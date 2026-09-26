package dev.ai.elements.demo.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Languages the app ships. [autonym] is shown untranslated, as language pickers do. */
enum class AppLanguage(val tag: String?, val autonym: String?) {
    SYSTEM(null, null),
    ENGLISH("en", "English"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    JAPANESE("ja", "日本語"),
}

/**
 * Per-app language. On Android 13+ this is the platform setting (also shown in
 * system Settings › Apps › Language, via the generated `localeConfig`), which
 * restarts the activity itself. Older versions keep the choice in preferences
 * and apply it in [wrap] from `attachBaseContext`.
 */
object AppLocale {
    private const val PREFS = "ai_elements_demo"
    private const val KEY = "app_language"

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        } ?: return AppLanguage.SYSTEM
        return match(tag)
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).commit()
            activity.recreate()
        }
    }

    /** Apply the stored language before Android 13; a no-op on 13+, where the platform does it. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** "zh-Hans-CN", "zh-CN" → Simplified; "zh-Hant-HK", "zh-TW" → Traditional. */
    private fun match(tag: String): AppLanguage {
        val locale = Locale.forLanguageTag(tag)
        return when (locale.language) {
            "en" -> AppLanguage.ENGLISH
            "ja" -> AppLanguage.JAPANESE
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) AppLanguage.CHINESE_TRADITIONAL
            else AppLanguage.CHINESE_SIMPLIFIED
            else -> AppLanguage.SYSTEM
        }
    }
}
