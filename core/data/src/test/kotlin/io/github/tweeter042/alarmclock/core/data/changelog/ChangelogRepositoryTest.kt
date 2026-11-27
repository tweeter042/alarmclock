package io.github.tweeter042.alarmclock.core.data.changelog

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.model.service.ChangelogEntry
import io.github.tweeter042.alarmclock.core.model.service.LocalizedText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ChangelogRepositoryTest {

    /** The real `app/src/main/assets/changelog.json` (path passed in by core/data/build.gradle.kts). */
    private val assetFile: File = File(
        System.getProperty("alarmclock.changelogAsset") ?: "../../app/src/main/assets/changelog.json",
    )

    private fun repositoryOf(json: String) = AssetChangelogRepository({ ByteArrayInputStream(json.encodeToByteArray()) })

    // --- the shipped asset -------------------------------------------------------------------

    @Test
    fun shippedChangelogParses() = runTest {
        assertTrue("missing asset at ${assetFile.absolutePath}", assetFile.isFile)
        val entries = AssetChangelogRepository({ assetFile.inputStream() }).entries()

        assertTrue(entries.isNotEmpty())
        assertEquals("newest first", entries.sortedByDescending { it.versionCode }, entries)
        assertEquals(entries.size, entries.map { it.versionCode }.toSet().size)
        entries.forEach { LocalDate.parse(it.date) }
    }

    @Test
    fun firstReleaseEntryIsComplete() = runTest {
        val first = AssetChangelogRepository({ assetFile.inputStream() }).entryFor(1)

        assertNotNull(first)
        first!!
        assertEquals("1.0.0", first.versionName)
        assertEquals("2026-11-27", first.date)
        assertTrue("6-8 highlights, was ${first.highlights.size}", first.highlights.size in 6..8)
        assertTrue("comprehensive feature list, was ${first.new.size}", first.new.size >= 40)
        (first.highlights + first.new + first.improved + first.fixed).forEach { text ->
            assertTrue("blank English text", text.en.isNotBlank())
            assertTrue("blank Thai text for '${text.en}'", text.th.isNotBlank())
            assertTrue("Thai text must be Thai: '${text.th}'", text.th.any { it in '฀'..'๿' })
            assertTrue("untranslated item '${text.en}'", text.en != text.th)
        }
    }

    @Test
    fun firstReleaseCoversEveryFeatureArea() = runTest {
        val first = AssetChangelogRepository({ assetFile.inputStream() }).entryFor(1)!!
        val english = (first.highlights + first.new).joinToString("\n") { it.en }.lowercase()

        val areas = mapOf(
            "alarms & repeat rules" to listOf("repeat", "rotation", "skip next", "holiday"),
            "sound & vibration" to listOf("ringtone", "fade-in", "vibration"),
            "snooze" to listOf("snooze"),
            "missions" to listOf("mission", "barcode", "s pen"),
            "ring screen" to listOf("ring screen"),
            "clock tools" to listOf("world clock", "timer", "stopwatch", "bedside"),
            "sleep" to listOf("bedtime", "sleep cycles", "sleep sounds", "light sleep"),
            "widgets, tiles, shortcuts" to listOf("widget", "tiles", "shortcuts"),
            "settings, backup, accessibility" to listOf("default settings", "back up", "accessibility"),
            "reliability & Samsung" to listOf("restart", "watchdog", "never sleeping apps"),
            "Galaxy Watch" to listOf("galaxy watch", "complication"),
            "SmartThings" to listOf("smartthings"),
            "Health Connect" to listOf("health connect"),
            "themes, palettes, fonts" to listOf("themes", "palette", "fonts"),
            "English & Thai" to listOf("english and thai", "thai numerals", "buddhist"),
            "changelog, about, AI" to listOf("changelog", "about screen", "ai assistance"),
        )
        areas.forEach { (area, keywords) ->
            keywords.forEach { keyword -> assertTrue("$area: '$keyword' not mentioned", keyword in english) }
        }
    }

    // --- parsing behaviour -------------------------------------------------------------------

    @Test
    fun entriesAreSortedNewestFirstAndCached() = runTest {
        var opened = 0
        val json = """[
            {"versionName":"1.0.0","versionCode":1,"date":"2026-11-27"},
            {"versionName":"1.2.0","versionCode":3,"date":"2027-02-01","fixed":[{"en":"Fix","th":"แก้ไข"}]},
            {"versionName":"1.1.0","versionCode":2,"date":"2027-01-10"}
        ]"""
        val repository = AssetChangelogRepository({ opened++; ByteArrayInputStream(json.encodeToByteArray()) })

        val entries = repository.entries()
        assertEquals(listOf(3, 2, 1), entries.map { it.versionCode })
        assertEquals(listOf(LocalizedText("Fix", "แก้ไข")), entries.first().fixed)
        assertSame(entries, repository.entries())
        assertEquals("1.1.0", repository.entryFor(2)?.versionName)
        assertNull(repository.entryFor(99))
        assertEquals("parsed once", 1, opened)
    }

    @Test
    fun malformedEntriesAreSkipped() = runTest {
        val entries = repositoryOf(
            """[
                {"versionName":"1.1.0","versionCode":2,"date":"2027-01-10","new":[{"en":"only english"}]},
                {"versionName":"1.0.0","versionCode":1,"date":"2026-11-27"},
                42
            ]""",
        ).entries()
        assertEquals(listOf(1), entries.map { it.versionCode })
    }

    @Test
    fun duplicateVersionCodesKeepOneEntry() = runTest {
        val entries = repositoryOf(
            """[{"versionName":"a","versionCode":1,"date":"2026-11-27"},{"versionName":"b","versionCode":1,"date":"2026-11-28"}]""",
        ).entries()
        assertEquals(1, entries.size)
    }

    @Test
    fun invalidFilesYieldAnEmptyChangelog() = runTest {
        assertTrue(repositoryOf("").entries().isEmpty())
        assertTrue(repositoryOf("not json").entries().isEmpty())
        assertTrue(repositoryOf("""{"versionName":"1.0.0"}""").entries().isEmpty())
        assertTrue(AssetChangelogRepository({ throw FileNotFoundException("changelog.json") }).entries().isEmpty())
    }

    @Test
    fun missingAssetThroughTheContextYieldsAnEmptyChangelog() = runTest {
        // This library module ships no assets; in the app the asset comes from :app.
        assertTrue(AssetChangelogRepository(ApplicationProvider.getApplicationContext<android.content.Context>()).entries().isEmpty())
    }

    @Test
    fun localizedTextFollowsLanguageTag() {
        val text = ChangelogEntry("1", 1, "2026-11-27", highlights = listOf(LocalizedText("Hello", "สวัสดี"))).highlights.single()
        assertEquals("สวัสดี", text.get("th-TH"))
        assertEquals("Hello", text.get("en"))
    }
}
