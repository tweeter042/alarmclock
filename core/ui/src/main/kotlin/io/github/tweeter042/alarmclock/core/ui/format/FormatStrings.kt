package io.github.tweeter042.alarmclock.core.ui.format

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.ui.R
import java.util.Locale

/**
 * Where the formatters get their localized words and patterns from. In the app this is the
 * Android [Resources] of the current (per-app) locale; tests use the same resources through
 * Robolectric, so the real English and Thai strings are what gets verified.
 */
interface FormatStrings {
    /** Language of these strings; decides month and weekday names of formatted dates. */
    val language: AppLanguage

    fun string(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/** [FormatStrings] backed by Android resources. */
class ResourceFormatStrings(private val resources: Resources) : FormatStrings {

    override val language: AppLanguage = AppLanguages.fromTag(resources.getString(R.string.ui_language_tag))

    override fun string(id: Int, vararg args: Any): String =
        if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        resources.getQuantityString(id, count, *args)
}

/** [FormatStrings] for [context]'s current configuration. */
fun Context.formatStrings(): FormatStrings = ResourceFormatStrings(resources)

/** Helpers to map between [AppLanguage], language tags and [Locale]s. */
object AppLanguages {
    /** "th", "th-TH", … → THAI; anything else → ENGLISH (the app's default and fallback language). */
    fun fromTag(tag: String?): AppLanguage =
        if (tag != null && tag.substringBefore('-').substringBefore('_').equals("th", ignoreCase = true)) {
            AppLanguage.THAI
        } else {
            AppLanguage.ENGLISH
        }

    fun fromLocale(locale: Locale?): AppLanguage = fromTag(locale?.language)
}

/**
 * The locale used for month and weekday names. Only the language matters (the app's own
 * patterns fix the order of the fields), so regional variants such as en-GB "Sept" never leak in.
 */
fun AppLanguage.formattingLocale(): Locale = Locale.forLanguageTag(tag)
