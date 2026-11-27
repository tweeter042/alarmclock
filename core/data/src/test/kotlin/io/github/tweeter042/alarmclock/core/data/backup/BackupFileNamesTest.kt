package io.github.tweeter042.alarmclock.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class BackupFileNamesTest {

    private val bangkok = ZoneId.of("Asia/Bangkok")

    @Test
    fun nameUsesLocalTimeWithAsciiDigits() {
        val at = ZonedDateTime.of(2026, 11, 27, 6, 5, 59, 0, bangkok).toInstant().toEpochMilli()
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("th-TH-u-ca-buddhist-nu-thai"))
            assertEquals("alarmclock-backup-20261127-0605.json", BackupFileNames.forTime(at, bangkok))
        } finally {
            Locale.setDefault(previous)
        }
        assertEquals("alarmclock-backup-20261126-2305.json", BackupFileNames.forTime(at, ZoneId.of("UTC")))
    }

    @Test
    fun onlyOurOwnNamesHaveAStamp() {
        assertEquals("20261127-0605", BackupFileNames.stampOf("alarmclock-backup-20261127-0605.json"))
        assertEquals("20261127-0605", BackupFileNames.stampOf("alarmclock-backup-20261127-0605 (2).json"))
        assertEquals("20261127-0605", BackupFileNames.stampOf("alarmclock-backup-20261127-0605(1).json"))
        assertNull(BackupFileNames.stampOf("alarmclock-backup-manual.json"))
        assertNull(BackupFileNames.stampOf("alarmclock-backup-20261127-0605-keep.json"))
        assertNull(BackupFileNames.stampOf("alarmclock-backup-20261127-0605.json.txt"))
        assertNull(BackupFileNames.stampOf("my-alarmclock-backup-20261127-0605.json"))
        assertNull(BackupFileNames.stampOf("notes.txt"))
    }

    @Test
    fun keepsTheNewestTenIncludingTheOneJustWritten() {
        val old = (1..14).map { day ->
            FolderEntry("doc$day", "alarmclock-backup-202611${"%02d".format(day)}-0300.json", lastModified = day.toLong())
        }
        val unrelated = listOf(FolderEntry("x", "holiday-photos.json", 1L), FolderEntry("y", "alarmclock-backup-manual.json", 1L))
        val fresh = FolderEntry("new", "alarmclock-backup-20261127-0300.json", 99L)

        val deleted = BackupFileNames.toDelete(old + unrelated + fresh, keepDocumentId = "new")

        assertEquals((1..5).map { "doc$it" }.toSet(), deleted.map { it.documentId }.toSet())
    }

    @Test
    fun theJustWrittenBackupIsKeptEvenIfOlderNamesLookNewer() {
        // Older files named while the clock was wrong (year 2030) must not push out the new backup.
        val future = (1..12).map { FolderEntry("f$it", "alarmclock-backup-2030010${it % 10}-0${it % 10}00.json", it.toLong()) }
        val fresh = FolderEntry("new", "alarmclock-backup-20261127-0300.json", 1L)

        val deleted = BackupFileNames.toDelete(future + fresh, keepDocumentId = "new")

        assertTrue(deleted.none { it.documentId == "new" })
        assertEquals(12 - 9, deleted.size)
    }

    @Test
    fun sameMinuteBackupsAreOrderedByModificationTime() {
        val entries = listOf(
            FolderEntry("a", "alarmclock-backup-20261127-0300.json", 10L),
            FolderEntry("b", "alarmclock-backup-20261127-0300 (1).json", 20L),
            FolderEntry("c", "alarmclock-backup-20261127-0300 (2).json", 30L),
        )
        assertEquals(listOf("a"), BackupFileNames.toDelete(entries, keepDocumentId = null, keep = 2).map { it.documentId })
    }

    @Test
    fun nothingToDeleteWhenFewerThanTheLimit() {
        val entries = (1..3).map { FolderEntry("d$it", "alarmclock-backup-2026112$it-0100.json", null) }
        assertTrue(BackupFileNames.toDelete(entries, keepDocumentId = "d3").isEmpty())
    }
}
