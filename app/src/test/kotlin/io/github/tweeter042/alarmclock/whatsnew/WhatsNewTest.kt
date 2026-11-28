package io.github.tweeter042.alarmclock.whatsnew

import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.model.service.LocalizedText
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Truth table of [shouldShowWhatsNew] / [whatsNewAction] (plain JVM). */
class WhatsNewTest {

    private fun settings(onboarded: Boolean, lastSeen: Int, autoShow: Boolean = true) =
        AppSettings(onboardingCompleted = onboarded, lastSeenVersionCode = lastSeen, showWhatsNewOnUpdate = autoShow)

    @Test
    fun truthTable() {
        data class Row(val onboarded: Boolean, val lastSeen: Int, val autoShow: Boolean, val version: Int, val show: Boolean, val action: WhatsNewAction)
        val rows = listOf(
            // Updated after onboarding: show once.
            Row(onboarded = true, lastSeen = 1, autoShow = true, version = 2, show = true, action = WhatsNewAction.Show),
            Row(onboarded = true, lastSeen = 3, autoShow = true, version = 7, show = true, action = WhatsNewAction.Show),
            // Already seen this version (or a newer one).
            Row(onboarded = true, lastSeen = 2, autoShow = true, version = 2, show = false, action = WhatsNewAction.None),
            Row(onboarded = true, lastSeen = 5, autoShow = true, version = 2, show = false, action = WhatsNewAction.None),
            // First install: nothing to show, just remember the version.
            Row(onboarded = true, lastSeen = 0, autoShow = true, version = 1, show = false, action = WhatsNewAction.MarkSeen),
            Row(onboarded = true, lastSeen = 0, autoShow = true, version = 4, show = false, action = WhatsNewAction.MarkSeen),
            // Turned off in Settings: never shown, version still recorded.
            Row(onboarded = true, lastSeen = 1, autoShow = false, version = 2, show = false, action = WhatsNewAction.MarkSeen),
            Row(onboarded = true, lastSeen = 2, autoShow = false, version = 2, show = false, action = WhatsNewAction.None),
            // Onboarding not finished: wait.
            Row(onboarded = false, lastSeen = 0, autoShow = true, version = 1, show = false, action = WhatsNewAction.None),
            Row(onboarded = false, lastSeen = 1, autoShow = true, version = 2, show = false, action = WhatsNewAction.None),
            Row(onboarded = false, lastSeen = 1, autoShow = false, version = 2, show = false, action = WhatsNewAction.None),
        )
        rows.forEach { row ->
            val s = settings(row.onboarded, row.lastSeen, row.autoShow)
            assertEquals("shouldShow for $row", row.show, shouldShowWhatsNew(s, row.version))
            assertEquals("action for $row", row.action, whatsNewAction(s, row.version))
        }
    }

    @Test
    fun linesPreferHighlightsAndAreCapped() {
        val text = { i: Int -> LocalizedText("en $i", "th $i") }
        val withHighlights = ChangelogEntry("1.1.0", 2, "2026-12-15", highlights = (1..7).map(text), new = listOf(text(99)))
        assertEquals((1..5).map(text), withHighlights.whatsNewLines())
        val withoutHighlights = ChangelogEntry("1.1.0", 2, "2026-12-15", new = listOf(text(1)), improved = listOf(text(2)), fixed = listOf(text(3)))
        assertEquals(listOf(text(1), text(2), text(3)), withoutHighlights.whatsNewLines())
        assertEquals(emptyList<LocalizedText>(), ChangelogEntry("1.1.0", 2, "2026-12-15").whatsNewLines())
    }
}
