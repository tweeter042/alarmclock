package io.github.tweeter042.alarmclock.whatsnew

import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.model.service.LocalizedText
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings

/**
 * True when the "What's new" sheet should appear for [versionCode]: onboarding is done, the app
 * was updated since the user last saw the notes (the first install does not count:
 * `lastSeenVersionCode` is still 0) and the user did not turn the sheet off in Settings.
 */
fun shouldShowWhatsNew(settings: AppSettings, versionCode: Int): Boolean =
    settings.onboardingCompleted &&
        settings.showWhatsNewOnUpdate &&
        settings.lastSeenVersionCode != 0 &&
        settings.lastSeenVersionCode < versionCode

/** What the app shell does about "What's new" at start. */
enum class WhatsNewAction {
    /** Nothing to do (onboarding not finished, or this version was already seen). */
    None,

    /** Show the sheet, then store the version as seen. */
    Show,

    /** Store the version as seen without showing anything (first install, or the sheet is turned off). */
    MarkSeen,
}

fun whatsNewAction(settings: AppSettings, versionCode: Int): WhatsNewAction = when {
    !settings.onboardingCompleted -> WhatsNewAction.None
    shouldShowWhatsNew(settings, versionCode) -> WhatsNewAction.Show
    settings.lastSeenVersionCode < versionCode -> WhatsNewAction.MarkSeen
    else -> WhatsNewAction.None
}

/** The lines shown in the sheet: the highlights, or the first notes when an entry has none. */
fun ChangelogEntry.whatsNewLines(max: Int = MAX_WHATS_NEW_LINES): List<LocalizedText> =
    highlights.ifEmpty { new + improved + fixed }.take(max)

/** The sheet summarizes; "See all changes" shows the rest. */
const val MAX_WHATS_NEW_LINES = 5
