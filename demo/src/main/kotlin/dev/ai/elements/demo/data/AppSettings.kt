package dev.ai.elements.demo.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Appearance(val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = true)

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai_elements_demo", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(
        Appearance(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = prefs.getBoolean("dynamic_color", true),
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun update(appearance: Appearance) {
        _appearance.value = appearance
        prefs.edit()
            .putString("theme", appearance.themeMode.name)
            .putBoolean("dynamic_color", appearance.dynamicColor)
            .apply()
    }
}
