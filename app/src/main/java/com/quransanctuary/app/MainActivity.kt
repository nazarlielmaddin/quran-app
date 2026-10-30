package com.quransanctuary.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.quransanctuary.app.di.AppContainer
import com.quransanctuary.app.ui.AppViewModel
import com.quransanctuary.app.ui.navigation.Route
import com.quransanctuary.app.ui.screens.HomeScreen
import com.quransanctuary.app.ui.screens.LibraryScreen
import com.quransanctuary.app.ui.screens.PlayerScreen
import com.quransanctuary.app.ui.screens.ReaderScreen
import com.quransanctuary.app.ui.screens.ReciterDetailScreen
import com.quransanctuary.app.ui.screens.RecitersScreen
import com.quransanctuary.app.ui.screens.SettingsScreen
import com.quransanctuary.app.ui.screens.SurahPickerScreen
import com.quransanctuary.app.ui.strings.LocalStrings
import com.quransanctuary.app.ui.theme.QuranSanctuaryTheme

class MainActivity : ComponentActivity() {

    // Android 13+: without POST_NOTIFICATIONS the playback notification
    // (shade + lock-screen controls) is silently suppressed — ask once.
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        val container: AppContainer = (application as QuranApp).container
        // Audio init touches disk (player cache); never let it crash app start.
        runCatching { container.audioEngine.initialize() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF07100C.toInt())
        )
        setContent {
            val vm: AppViewModel = viewModel { AppViewModel(container) }
            val theme by vm.theme.collectAsState()
            val strings by vm.strings.collectAsState()
            QuranSanctuaryTheme(mode = theme) {
                CompositionLocalProvider(LocalStrings provides strings) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val nav = rememberNavController()
                        NavHost(navController = nav, startDestination = Route.Home) {
                            composable<Route.Home> {
                                HomeScreen(vm = vm, nav = nav)
                            }
                            composable<Route.Reader> { backStack ->
                                val args = backStack.toRoute<Route.Reader>()
                                ReaderScreen(vm = vm, nav = nav, surahId = args.surah)
                            }
                            composable<Route.Player> {
                                PlayerScreen(vm = vm, nav = nav)
                            }
                            composable<Route.Library> {
                                LibraryScreen(vm = vm, nav = nav)
                            }
                            composable<Route.Settings> {
                                SettingsScreen(vm = vm, nav = nav)
                            }
                            composable<Route.Reciters> {
                                RecitersScreen(vm = vm, nav = nav)
                            }
                            composable<Route.Surahs> {
                                SurahPickerScreen(vm = vm, nav = nav)
                            }
                            composable<Route.ReciterDetail> { backStack ->
                                val args = backStack.toRoute<Route.ReciterDetail>()
                                ReciterDetailScreen(vm = vm, nav = nav, reciterId = args.id)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Keep audio playing when the UI is gone only if something is actually playing.
        val engine = (application as QuranApp).container.audioEngine
        val s = runCatching { engine.state.value }.getOrNull()
        if (s == null || (!s.quranPlaying && !s.ambientPlaying)) {
            runCatching { engine.release() }
        }
        super.onDestroy()
    }
}
