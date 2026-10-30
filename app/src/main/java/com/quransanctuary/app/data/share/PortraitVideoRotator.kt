package com.quransanctuary.app.data.share

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Media3 Transformer intentionally encodes portrait videos into a landscape
 * encoder surface and stores the 90° rotation in the MP4 metadata. Most players
 * honour that metadata, but some gallery apps and the emulator's software
 * decoder do not, so the finished clip appears sideways.
 *
 * This second pass decodes the exported MP4, rotates every frame 90° clockwise
 * in OpenGL and re-encodes it as a real 1080x1920 file with no rotation
 * metadata. Audio is copied sample-for-sample, so A/V sync is preserved.
 *
 * If anything is unsupported by the device (for example an encoder that refuses
 * portrait dimensions) the method returns false and the caller keeps the
 * original metadata-based export.
 */
internal object PortraitVideoRotator {
    private const val TAG = "PortraitRotator"
    private const val FRAME_RATE = 30
    private const val BIT_RATE = 8_000_000
    private const val TIMEOUT_US = 10_000L
    private const val FRAME_WAIT_SECONDS = 10L
    private const val EGL_RECORDABLE_ANDROID = 0x3142

    suspend fun rotate(input: File, output: File, width: Int, height: Int): Boolean =
        withContext(Dispatchers.IO) {
            val temp = File(output.parentFile, "${output.nameWithoutExtension}.portrait.tmp.mp4")
            if (temp.exists()) temp.delete()
            try {
                rotateBlocking(input, temp, width, height)
                if (!temp.exists() || temp.length() < 20_000L) {
                    error("Rotated video is too small")
                }
                if (output.exists()) output.delete()
                if (!temp.renameTo(output)) {
                    temp.copyTo(output, overwrite = true)
                    temp.delete()
                }
                Log.i(TAG, "Rotated ${input.name} -> true ${width}x$height portrait")
                true
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.w(TAG, "Could not rotate video to true portrait", t)
                if (temp.exists()) temp.delete()
                false
            }
        }

    private suspend fun rotateBlocking(input: File, output: File, width: Int, height: Int) {
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var decoderSurface: Surface? = null
        var surfaceTexture: SurfaceTexture? = null
        var eglCore: EglCore? = null
        val muxState = MuxState()
        try {
            videoExtractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            val videoTrack = findTrack(videoExtractor, "video/")
            require(videoTrack >= 0) { "Input has no video track" }
            videoExtractor.selectTrack(videoTrack)
            val inputFormat = videoExtractor.getTrackFormat(videoTrack)
            val inputWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH)
            val inputHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            // We rotate in GL ourselves. Tell the decoder not to apply the
            // container rotation, otherwise some devices rotate twice.
            runCatching { inputFormat.setInteger(MediaFormat.KEY_ROTATION, 0) }

            // Prepare the audio track first so the muxer knows about both tracks
            // before its first video sample arrives.
            audioExtractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            val audioTrack = findTrack(audioExtractor, "audio/")
            var audioMuxTrack = -1
            if (audioTrack >= 0) {
                audioExtractor.selectTrack(audioTrack)
                val audioFormat = audioExtractor.getTrackFormat(audioTrack)
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                audioMuxTrack = muxer.addTrack(audioFormat)
            } else {
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val outputFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, width, height
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                runCatching {
                    setInteger(
                        MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                    )
                }
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val encoderSurface = encoder.createInputSurface()
            encoder.start()

            eglCore = EglCore(encoderSurface).apply { makeCurrent() }
            val textureId = createExternalTexture()
            surfaceTexture = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(inputWidth, inputHeight)
            }
            val frameAvailable = Semaphore(0)
            surfaceTexture.setOnFrameAvailableListener { frameAvailable.release() }
            decoderSurface = Surface(surfaceTexture)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Input video has no MIME type")
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, decoderSurface, null, 0)
            decoder.start()

            val program = GlProgram()
            val textureMatrix = FloatArray(16)
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)
                        if (buffer == null) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val size = videoExtractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    size,
                                    videoExtractor.sampleTime,
                                    videoExtractor.sampleFlags
                                )
                                videoExtractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (isEos) {
                            decoder.releaseOutputBuffer(outputIndex, false)
                            outputDone = true
                        } else {
                            decoder.releaseOutputBuffer(outputIndex, true)
                            if (!frameAvailable.tryAcquire(FRAME_WAIT_SECONDS, TimeUnit.SECONDS)) {
                                error("Timed out waiting for a decoded frame")
                            }
                            surfaceTexture.updateTexImage()
                            surfaceTexture.getTransformMatrix(textureMatrix)
                            program.draw(
                                textureId = textureId,
                                textureMatrix = textureMatrix,
                                width = width,
                                height = height
                            )
                            EGLExt.eglPresentationTimeANDROID(
                                eglCore.display,
                                eglCore.surface,
                                bufferInfo.presentationTimeUs * 1000L
                            )
                            eglCore.swapBuffers()
                            drainEncoder(
                                encoder = encoder,
                                muxer = muxer,
                                audioExtractor = audioExtractor.takeIf { audioMuxTrack >= 0 },
                                audioMuxTrack = audioMuxTrack,
                                state = muxState,
                                endOfStream = false
                            )
                        }
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format change is handled by the encoder drain below.
                    }
                    outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }

            decoder.stop()
            decoder.release()
            decoder = null

            encoder.signalEndOfInputStream()
            drainEncoder(
                encoder = encoder,
                muxer = muxer,
                audioExtractor = audioExtractor.takeIf { audioMuxTrack >= 0 },
                audioMuxTrack = audioMuxTrack,
                state = muxState,
                endOfStream = true
            )

            if (!muxState.started) error("Encoder produced no muxable video")
            muxer.stop()
            muxState.started = false
            muxer.release()
            muxer = null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { decoderSurface?.release() }
            runCatching { surfaceTexture?.release() }
            runCatching { eglCore?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxState.started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { audioExtractor?.release() }
            runCatching { videoExtractor?.release() }
        }
    }

    private class MuxState {
        var videoTrack: Int = -1
        var started: Boolean = false
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        audioExtractor: MediaExtractor?,
        audioMuxTrack: Int,
        state: MuxState,
        endOfStream: Boolean
    ) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(
                info,
                if (endOfStream) TIMEOUT_US else 0L
            )
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (state.videoTrack < 0) {
                        state.videoTrack = muxer.addTrack(encoder.outputFormat)
                    }
                    if (!state.started) {
                        muxer.start()
                        state.started = true
                        if (audioExtractor != null && audioMuxTrack >= 0) {
                            copyAudio(audioExtractor, muxer, audioMuxTrack)
                        }
                    }
                }
                outputIndex >= 0 -> {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0
                    }
                    if (info.size > 0) {
                        if (state.videoTrack < 0) error("Video track missing")
                        val buffer = encoder.getOutputBuffer(outputIndex)
                            ?: error("Encoder output buffer is null")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(state.videoTrack, buffer, info)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
            }
        }
    }

    private fun copyAudio(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        track: Int
    ) {
        val maxSize = runCatching {
            extractor.getTrackFormat(track).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        }.getOrDefault(256 * 1024).coerceAtLeast(64 * 1024)
        val buffer = ByteBuffer.allocateDirect(maxSize)
        val info = MediaCodec.BufferInfo()
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            val pts = extractor.sampleTime
            buffer.position(0)
            buffer.limit(size)
            info.set(0, size, pts, 0)
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(prefix) == true) return i
        }
        return -1
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return id
    }

    private class GlProgram {
        private val program: Int
        private val positionHandle: Int
        private val textureHandle: Int
        private val textureMatrixHandle: Int
        private val samplerHandle: Int
        private val positionBuffer: FloatBuffer
        private val textureBuffer: FloatBuffer

        init {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
            val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
            program = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vertex)
                GLES20.glAttachShader(it, fragment)
                GLES20.glLinkProgram(it)
                val status = IntArray(1)
                GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, status, 0)
                if (status[0] != GLES20.GL_TRUE) {
                    val log = GLES20.glGetProgramInfoLog(it)
                    GLES20.glDeleteProgram(it)
                    error("Could not link GL program: $log")
                }
                GLES20.glDeleteShader(vertex)
                GLES20.glDeleteShader(fragment)
            }
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            textureHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
            samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
            positionBuffer = floatBuffer(POSITIONS)
            textureBuffer = floatBuffer(TEXTURE_COORDS)
        }

        fun draw(
            textureId: Int,
            textureMatrix: FloatArray,
            width: Int,
            height: Int
        ) {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(samplerHandle, 0)
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)

            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle, 2, GLES20.GL_FLOAT, false, 0, positionBuffer
            )
            GLES20.glEnableVertexAttribArray(textureHandle)
            GLES20.glVertexAttribPointer(
                textureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer
            )
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(textureHandle)
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] != GLES20.GL_TRUE) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                error("Could not compile GL shader: $log")
            }
            return shader
        }

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(values)
                    position(0)
                }
    }

    private class EglCore(private val outputSurface: Surface) {
        var display: android.opengl.EGLDisplay
            private set
        var surface: android.opengl.EGLSurface
            private set
        private var context: android.opengl.EGLContext
        private var config: android.opengl.EGLConfig

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "No EGL display" }
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) {
                "Could not initialise EGL"
            }
            val baseAttributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT
            )
            val recordableAttributes = baseAttributes + intArrayOf(
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            val hasRecordable = EGL14.eglChooseConfig(
                display, recordableAttributes, 0, configs, 0, 1, count, 0
            ) && count[0] > 0
            if (!hasRecordable) {
                check(
                    EGL14.eglChooseConfig(
                        display, baseAttributes + intArrayOf(EGL14.EGL_NONE),
                        0, configs, 0, 1, count, 0
                    ) && count[0] > 0
                ) { "No EGL config" }
            }
            config = configs[0] ?: error("No EGL config")
            val contextAttributes = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "Could not create EGL context" }
            surface = EGL14.eglCreateWindowSurface(
                display, config, outputSurface, intArrayOf(EGL14.EGL_NONE), 0
            )
            check(surface != EGL14.EGL_NO_SURFACE) { "Could not create EGL surface" }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                "Could not make EGL context current"
            }
        }

        fun swapBuffers() {
            if (!EGL14.eglSwapBuffers(display, surface)) {
                error("eglSwapBuffers failed")
            }
        }

        fun release() {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private val POSITIONS = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    private val TEXTURE_COORDS = floatArrayOf(
        0f, 0f,
        1f, 0f,
        0f, 1f,
        1f, 1f
    )

    private const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vec2 tex = (uTexMatrix * aTexCoord).xy;
            vTexCoord = vec2(tex.y, 1.0 - tex.x);
        }
    """

    private const val FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTexture;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """
}
