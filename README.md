# Quran Sanctuary — Android

A premium native Android app for listening to the Holy Quran: reciter-guided playback, verse-synchronized reading, ambient atmospheres with looping background videos, and full offline support.

Built with **Kotlin · Jetpack Compose (Material 3) · Media3 · Room · DataStore · Coroutines/Flow · Coil · kotlinx.serialization**.

> Companion to the web version: [nazarlielmaddin/quran](https://github.com/nazarlielmaddin/quran)

## ✨ Features

- **Guided flow** — Hero → reciter pyramid (4-3-2-1) → searchable 114-surah browser → synchronized reader
- **Background audio that survives the app** — foreground `MediaSessionService` with instant promotion (no `ForegroundServiceDidNotStartInTime` crashes), rich notification + lock-screen controls (prev/play/next, artwork), headset & Bluetooth transport via a `ForwardingPlayer` routed into the surah engine
- **Verse-synced reading** — real ayah-start timings with binary-search active-verse tracking, tap-to-jump, auto-scroll, bookmarks, Arabic + correct Azerbaijani reading + translation
- **Correct transliteration engine** — exact 1:1 port of the website's `toAzerbaijani()` (125 ordered word fixes + clusters, ASCII-boundary semantics preserved)
- **Ambient atmospheres** — 15 sounds, each with a muted looping background video (single shared player across screens, disk-cached, lifecycle-aware)
- **Offline first** — fully in-app HTTPS downloader (no `DownloadManager` permission/path pitfalls) with atomic finalize, progress, and a Room ledger; played/streamed audio also rides a 300 MB ExoPlayer cache
- **4 languages** — Azerbaijani, English, Turkish, Arabic (all 84 strings translated, runtime switch across the whole app)
- **Hardened parsing** — lenient serializers + per-element tolerant decoding: one corrupt JSON object can never crash startup
- **Glassmorphism UI** — frosted cards, breathing gold aura, edge-to-edge, dark-first theme

## 🧱 Architecture

```
app/src/main/java/com/quransanctuary/app/
├── MainActivity.kt            # NavHost (Home → Reader → Player → Library → Settings)
├── QuranApp.kt                # Application + Coil ImageLoader
├── di/AppContainer.kt         # Manual DI graph (no codegen, hermetic builds)
├── data/
│   ├── audio/                 # AudioEngine (dual Quran+ambient layers), MediaSessionService
│   ├── download/              # In-app offline downloader (atomic, resumable UX)
│   └── local/                 # AssetLoader (tolerant JSON), Room DB, DataStore settings
├── domain/                    # Transliteration engine, verse timing, formatting
└── ui/                        # Compose screens + glass design system
```

Audio state is a single `StateFlow<PlayerUiState>`; the reader follows playback (prev/next/auto-next navigate to the playing surah, stale-scroll guarded).

## 🚀 Build

Requirements: Android Studio (or JDK 17 + Gradle 8.9), Android SDK 34.

```bash
# debug
./gradlew :app:assembleDebug

# signed release (needs your own key — see below)
./gradlew :app:assembleRelease
```

### Release signing

Create `app/keystore.properties` (never committed):

```properties
storeFile=../keystore/release.keystore
storePassword=****
keyAlias=****
keyPassword=****
```

## 🔗 Data sources

- Recitation audio: `audio-cdn.tarteel.ai` (Quranic Universal Library, 8 reciters) and `cdn.mp3quran.net` (Badr Al-Turki, Haitham Al-Dakhin)
- Verse timings: QUL per-ayah segments (8 reciters) and QDC forced alignment (Badr Al-Turki); Haitham has no timings
- Runtime fallback: `cdn.mp3quran.net` mirrors for QUL reciters, `server8.mp3quran.net` for Badr, `server16.mp3quran.net` for Haitham
- Transliteration: `api.alquran.cloud` (`en.transliteration`)
- Ambient audio/video: Pixabay CDN · Portraits: Wikimedia Commons

## 📄 License

Code: MIT. Media assets belong to their respective owners (see above).
