package com.quransanctuary.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransanctuary.app.data.model.AppLocale
import com.quransanctuary.app.data.model.DisplayMode
import com.quransanctuary.app.data.model.ThemeMode
import com.quransanctuary.app.di.AppContainer
import com.quransanctuary.app.ui.strings.AppStrings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(val container: AppContainer) : ViewModel() {

    val locale: StateFlow<AppLocale> = container.settings.locale
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLocale.AZ)

    val theme: StateFlow<ThemeMode> = container.settings.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val displayMode: StateFlow<DisplayMode> = container.settings.displayMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisplayMode.BOTH)

    val arabicFontScale: StateFlow<Float> = container.settings.arabicFontScale
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val strings: StateFlow<AppStrings> = locale.map { container.stringsLoader.load(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppStrings())

    val player = container.audioEngine.state

    fun setLocale(v: AppLocale) = viewModelScope.launch { container.settings.setLocale(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { container.settings.setTheme(v) }
    fun setDisplayMode(v: DisplayMode) = viewModelScope.launch { container.settings.setDisplayMode(v) }
    fun setArabicFontScale(v: Float) = viewModelScope.launch { container.settings.setArabicFontScale(v) }
}
