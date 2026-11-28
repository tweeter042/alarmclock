package io.github.tweeter042.alarmclock.core.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import android.util.Log
import io.github.tweeter042.alarmclock.core.model.repository.SettingsRepository
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.AppLanguages
import kotlinx.coroutines.CancellationException

/**
 * The app language (English or Thai), stored by Android as the per-app language
 * (`LocaleManager.applicationLocales`, also editable in system Settings › Apps › Alarm Clock ›
 * Language). Android is the source of truth; `settings.region.language` only mirrors it for code
 * that runs without a UI context (TTS, briefing, watch).
 *
 * Changing the language recreates the visible activities automatically.
 */
object AppLocales {
    private const val TAG = "AlarmClock/Locale"

    /** Switches the app to [language]. Activities are recreated by the system. */
    fun setLanguage(context: Context, language: AppLanguage) {
        val manager = context.localeManager() ?: return
        manager.applicationLocales = LocaleList.forLanguageTags(language.tag)
    }

    /** The language chosen for the app, or null if it follows the system language. */
    fun selectedLanguage(context: Context): AppLanguage? {
        val locales = context.localeManager()?.applicationLocales ?: return null
        return if (locales.isEmpty) null else AppLanguages.fromLocale(locales[0])
    }

    /** True when no per-app language is set (the app follows the system language). */
    fun followsSystem(context: Context): Boolean = context.localeManager()?.applicationLocales?.isEmpty ?: true

    /**
     * The language the UI is shown in right now: the per-app language if set, otherwise the one
     * Android picked from the system languages (Thai if available, else English).
     */
    fun currentLanguage(context: Context): AppLanguage =
        selectedLanguage(context) ?: AppLanguages.fromTag(context.getString(R.string.ui_language_tag))

    /**
     * App start bookkeeping, call once per activity start:
     * 1. English is the default language: on first run (onboarding not completed and no per-app
     *    language set) English is set explicitly, so a Thai phone still starts in English.
     * 2. Mirrors the current language into `settings.region.language`.
     *
     * Returns true if the language was changed (the activity is about to be recreated).
     */
    suspend fun syncWithSettings(context: Context, settings: SettingsRepository): Boolean {
        return try {
            val current = settings.current()
            val applyDefault = !current.onboardingCompleted && followsSystem(context)
            if (applyDefault) setLanguage(context, AppLanguage.ENGLISH)
            val language = if (applyDefault) AppLanguage.ENGLISH else currentLanguage(context)
            if (current.region.language != language) {
                settings.update { it.copy(region = it.region.copy(language = language)) }
            }
            applyDefault
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not sync the app language (${e.javaClass.simpleName})")
            false
        }
    }

    /**
     * Synchronous first-run check for `Application.onCreate`, before any activity exists, so the
     * first screen already opens in English: sets English when nothing has been stored yet
     * ([hasStoredSettings] false) and no per-app language is set.
     */
    fun applyFirstRunDefault(context: Context, hasStoredSettings: Boolean): Boolean {
        if (hasStoredSettings || !followsSystem(context)) return false
        setLanguage(context, AppLanguage.ENGLISH)
        return true
    }

    private fun Context.localeManager(): LocaleManager? = getSystemService(LocaleManager::class.java)
}
