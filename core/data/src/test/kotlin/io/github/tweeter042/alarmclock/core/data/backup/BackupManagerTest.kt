package io.github.tweeter042.alarmclock.core.data.backup

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.data.FakeSettingsRepository
import io.github.tweeter042.alarmclock.core.data.InMemoryDatabaseRule
import io.github.tweeter042.alarmclock.core.data.TestClock
import io.github.tweeter042.alarmclock.core.data.alarm
import io.github.tweeter042.alarmclock.core.data.barcodeMission
import io.github.tweeter042.alarmclock.core.data.db.AlarmClockDatabase
import io.github.tweeter042.alarmclock.core.data.photoMission
import io.github.tweeter042.alarmclock.core.data.repository.RoomAlarmRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomCalendarRuleRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomHolidayRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomLocationAlarmRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomMissionAssetRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomRoutineRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomTimerRepository
import io.github.tweeter042.alarmclock.core.data.repository.RoomWorldClockRepository
import io.github.tweeter042.alarmclock.core.model.alarm.Alarm
import io.github.tweeter042.alarmclock.core.model.alarm.MissionConfig
import io.github.tweeter042.alarmclock.core.model.alarm.MissionType
import io.github.tweeter042.alarmclock.core.model.entity.AlarmGroup
import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.entity.Holiday
import io.github.tweeter042.alarmclock.core.model.entity.HolidaySource
import io.github.tweeter042.alarmclock.core.model.entity.LocationAlarm
import io.github.tweeter042.alarmclock.core.model.entity.MorningRoutineItem
import io.github.tweeter042.alarmclock.core.model.entity.PhotoTarget
import io.github.tweeter042.alarmclock.core.model.entity.RegisteredCode
import io.github.tweeter042.alarmclock.core.model.entity.TimerModel
import io.github.tweeter042.alarmclock.core.model.entity.TimerPreset
import io.github.tweeter042.alarmclock.core.model.entity.TimerState
import io.github.tweeter042.alarmclock.core.model.entity.WorldClock
import io.github.tweeter042.alarmclock.core.model.service.BackupSummary
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.AppSettings
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import io.github.tweeter042.alarmclock.core.model.util.ModelJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupManagerTest {

    @get:Rule
    val database = InMemoryDatabaseRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Bangkok")
    private val clock = TestClock(ZonedDateTime.of(2026, 11, 27, 6, 30, 0, 0, ZoneId.of("Asia/Bangkok")).toInstant().toEpochMilli())

    private val deviceSettings = AppSettings().let {
        it.copy(
            appearance = it.appearance.copy(themeId = ThemeId.RETRO_LCD),
            region = it.region.copy(language = AppLanguage.THAI),
            reliability = it.reliability.copy(manualChecks = mapOf("SAMSUNG_NEVER_SLEEPING" to true), lastTestAt = 5L, lastTestOk = true),
            backup = it.backup.copy(autoBackupEnabled = true, treeUri = "content://this-device/tree/x", lastBackupAt = 7L),
            onboardingCompleted = true,
            lastSeenVersionCode = 1,
        )
    }

    private val extraDatabases = mutableListOf<AlarmClockDatabase>()

    private lateinit var db: AlarmClockDatabase
    private lateinit var settings: FakeSettingsRepository
    private lateinit var manager: JsonBackupManager

    @Before
    fun setUp() {
        db = database.db
        settings = FakeSettingsRepository(deviceSettings)
        manager = managerFor(db, settings)
    }

    @After
    fun tearDown() {
        extraDatabases.forEach { it.close() }
    }

    private fun managerFor(db: AlarmClockDatabase, settings: FakeSettingsRepository) =
        JsonBackupManager(context, db, settings, versionName = "1.0.0", clock = clock, zone = { zone })

    private fun freshDatabase() = AlarmClockDatabase.inMemory(context).also { extraDatabases += it }

    /** Everything the backup covers, read back through the repositories. */
    private data class Snapshot(
        val alarms: List<Alarm>,
        val groups: List<AlarmGroup>,
        val holidays: List<Holiday>,
        val timers: List<TimerModel>,
        val presets: List<TimerPreset>,
        val worldClocks: List<WorldClock>,
        val routine: List<MorningRoutineItem>,
        val codes: List<RegisteredCode>,
        val photos: List<PhotoTarget>,
        val calendarRules: List<CalendarAlarmRule>,
        val locationAlarms: List<LocationAlarm>,
    )

    private suspend fun snapshot(db: AlarmClockDatabase) = Snapshot(
        alarms = RoomAlarmRepository(db).getAlarms(),
        groups = RoomAlarmRepository(db).getGroups(),
        holidays = RoomHolidayRepository(db).observeAll().first(),
        timers = RoomTimerRepository(db).observeTimers().first(),
        presets = RoomTimerRepository(db).observePresets().first(),
        worldClocks = RoomWorldClockRepository(db).observe().first(),
        routine = RoomRoutineRepository(db).observeItems().first(),
        codes = RoomMissionAssetRepository(db).observeCodes().first(),
        photos = RoomMissionAssetRepository(db).observePhotoTargets().first(),
        calendarRules = RoomCalendarRuleRepository(db).getAll(),
        locationAlarms = RoomLocationAlarmRepository(db).getAll(),
    )

    private class Ids(val work: Long, val weekend: Long, val qr: Long, val barcode: Long, val mug: Long, val workAlarm: Long)

    private suspend fun populate(db: AlarmClockDatabase = this.db): Ids {
        val alarms = RoomAlarmRepository(db, clock)
        val assets = RoomMissionAssetRepository(db, clock)
        val holidays = RoomHolidayRepository(db)
        val timers = RoomTimerRepository(db, clock)

        val work = alarms.upsertGroup(AlarmGroup(name = "Work", colorIndex = 2))
        val weekend = alarms.upsertGroup(AlarmGroup(name = "Weekend", enabled = false))
        val qr = assets.upsertCode(RegisteredCode(label = "Bathroom", value = "QR-BATH", format = 256))
        val barcode = assets.upsertCode(RegisteredCode(label = "Toothpaste", value = "8850000000001", format = 32))
        val mug = assets.upsertPhotoTarget(PhotoTarget(label = "Mug", imagePath = "/data/photos/mug.jpg", labels = mapOf("Cup" to 0.92f)))

        val workAlarm = alarms.upsert(
            alarm(6, 30, "Work", groupId = work, missions = listOf(barcodeMission(qr, barcode), photoMission(mug), MissionConfig(MissionType.MATH))),
        )
        alarms.upsert(alarm(9, 0, "Weekend", groupId = weekend))
        alarms.upsert(alarm(22, 0, "Loose").copy(skippedOccurrenceAt = 123L))

        holidays.replaceSource(
            HolidaySource.BUILT_IN,
            listOf(Holiday(date = LocalDate.parse("2026-12-05"), nameEn = "Father's Day", nameTh = "วันพ่อแห่งชาติ", source = HolidaySource.BUILT_IN)),
        )
        holidays.upsert(Holiday(date = LocalDate.parse("2026-12-24"), nameEn = "Christmas Eve off", source = HolidaySource.CUSTOM))
        holidays.replaceSource(
            HolidaySource.CALENDAR,
            listOf(Holiday(date = LocalDate.parse("2026-02-17"), nameEn = "Chinese New Year", nameTh = "ตรุษจีน", source = HolidaySource.CALENDAR, enabled = false)),
        )

        timers.upsert(TimerModel(label = "Tea", durationMs = 180_000, state = TimerState.RUNNING, endAt = clock.now + 60_000, remainingMs = 120_000))
        timers.upsert(TimerModel(label = "Pasta", durationMs = 600_000, state = TimerState.PAUSED, remainingMs = 300_000))
        timers.upsertPreset(TimerPreset(label = "Eggs", durationMs = 420_000))

        RoomWorldClockRepository(db).apply {
            upsert(WorldClock(zoneId = "Asia/Tokyo"))
            upsert(WorldClock(zoneId = "Europe/London", label = "London office"))
        }
        RoomRoutineRepository(db).apply {
            upsert(MorningRoutineItem(text = "Drink water"))
            upsert(MorningRoutineItem(text = "Stretch", enabled = false))
        }
        RoomCalendarRuleRepository(db).upsert(
            CalendarAlarmRule(name = "Meetings", template = Alarm(groupId = work, missions = listOf(barcodeMission(qr)))),
        )
        RoomLocationAlarmRepository(db).upsert(
            LocationAlarm(label = "Station", latitude = 13.75, longitude = 100.5, template = Alarm(missions = listOf(photoMission(mug)))),
        )
        return Ids(work, weekend, qr, barcode, mug, workAlarm)
    }

    private suspend fun exportBytes(manager: JsonBackupManager = this.manager): ByteArray {
        val out = ByteArrayOutputStream()
        manager.exportTo(out).getOrThrow()
        return out.toByteArray()
    }

    private suspend fun import(bytes: ByteArray, replace: Boolean, manager: JsonBackupManager = this.manager): Result<BackupSummary> =
        manager.importFrom(ByteArrayInputStream(bytes), replace)

    private suspend fun importText(text: String, replace: Boolean = true) = import(text.encodeToByteArray(), replace)

    // --- export --------------------------------------------------------------------------------

    @Test
    fun exportWritesAVersionedDocument() = runTest {
        populate()
        val out = ByteArrayOutputStream()

        val summary = manager.exportTo(out).getOrThrow()

        val json = ModelJson.parseToJsonElement(out.toString(Charsets.UTF_8.name())).jsonObject
        assertEquals("alarmclock-backup", json.getValue("format").jsonPrimitive.content)
        assertEquals(1, json.getValue("version").jsonPrimitive.int)
        assertEquals(clock.now.toString(), json.getValue("createdAt").jsonPrimitive.content)
        assertEquals("1.0.0", json.getValue("appVersion").jsonPrimitive.content)
        assertTrue(json.containsKey("settings"))
        listOf(
            "alarms", "groups", "holidays", "timers", "timerPresets", "worldClocks", "routineItems",
            "registeredCodes", "photoTargets", "calendarRules", "locationAlarms",
        ).forEach { assertTrue("section $it", json.getValue(it).jsonArray.isNotEmpty()) }

        assertEquals(BackupSummary(alarms = 3, groups = 2, timers = 2, worldClocks = 2, holidays = 2, other = 7 + 2 + 2 + 1 + 1 + 1, createdAt = clock.now, appVersion = "1.0.0"), summary)
    }

    @Test
    fun exportSkipsBuiltInHolidaysAndStoresTimersAsIdle() = runTest {
        populate()
        val document = BackupCodec.decode(exportBytes().decodeToString())

        assertEquals(setOf(HolidaySource.CUSTOM, HolidaySource.CALENDAR), document.holidays.map { it.source }.toSet())
        assertTrue(document.timers.all { it.state == TimerState.IDLE && it.endAt == null && it.remainingMs == it.durationMs })
        assertEquals(listOf("Tea", "Pasta"), document.timers.map { it.label })
        assertEquals("metadata only: the image path is kept, no image bytes", "/data/photos/mug.jpg", document.photoTargets.single().imagePath)
    }

    @Test
    fun exportNeverContainsSecrets() = runTest {
        populate()
        // Secrets live only in the SecretStore; even settings that relate to them carry no token.
        settings.update { it.copy(smartThings = it.smartThings.copy(enabled = true, oauthClientId = "client-id")) }

        val text = exportBytes().decodeToString()

        assertFalse(text.contains("access_token", ignoreCase = true))
        assertFalse(text.contains("refresh_token", ignoreCase = true))
        assertFalse(text.contains("client_secret", ignoreCase = true))
        assertTrue(text.contains("client-id"))
    }

    // --- replace -------------------------------------------------------------------------------

    @Test
    fun replaceRestoresEverythingWithOriginalIds() = runTest {
        val ids = populate()
        val bytes = exportBytes()
        val expected = snapshot(db).let { it.copy(timers = it.timers.map { timer -> timer.asIdle() }) }

        // Change the data after the backup in every way possible.
        val alarms = RoomAlarmRepository(db, clock)
        alarms.delete(ids.workAlarm)
        alarms.upsert(alarm(5, 0, "Added later"))
        alarms.upsertGroup(alarms.getGroups().first().copy(name = "Renamed"))
        RoomMissionAssetRepository(db).deleteCode(ids.qr)
        RoomWorldClockRepository(db).upsert(WorldClock(zoneId = "Asia/Seoul"))
        RoomHolidayRepository(db).upsert(Holiday(date = LocalDate.parse("2026-06-01"), nameEn = "New custom", source = HolidaySource.CUSTOM))
        settings.update { it.copy(appearance = it.appearance.copy(themeId = ThemeId.NEON)) }

        val summary = import(bytes, replace = true).getOrThrow()

        assertEquals(expected, snapshot(db))
        assertEquals(3, summary.alarms)
        assertEquals(2, summary.holidays)
        assertEquals(ThemeId.RETRO_LCD, settings.current().appearance.themeId)
    }

    @Test
    fun replaceKeepsThisDevicesOwnSettings() = runTest {
        populate()
        val bytes = exportBytes()
        val newPhone = AppSettings().let {
            it.copy(
                backup = it.backup.copy(treeUri = "content://new-phone/tree/y"),
                reliability = it.reliability.copy(manualChecks = emptyMap()),
                smartThings = it.smartThings.copy(accessTokenExpiresAt = 99L),
                onboardingCompleted = false,
            )
        }
        settings.update { newPhone }

        import(bytes, replace = true).getOrThrow()

        val restored = settings.current()
        assertEquals(ThemeId.RETRO_LCD, restored.appearance.themeId)
        assertEquals("content://new-phone/tree/y", restored.backup.treeUri)
        assertEquals(emptyMap<String, Boolean>(), restored.reliability.manualChecks)
        assertNull(restored.reliability.lastTestAt)
        assertEquals(99L, restored.smartThings.accessTokenExpiresAt)
        assertFalse(restored.onboardingCompleted)
        assertEquals(0, restored.lastSeenVersionCode)
        assertEquals("language follows the per-app locale of this device", AppLanguage.ENGLISH, restored.region.language)
    }

    @Test
    fun replaceIntoAFreshInstallKeepsBuiltInHolidaysAndResolvesIdClashes() = runTest {
        populate()
        val bytes = exportBytes()

        val fresh = freshDatabase()
        val freshSettings = FakeSettingsRepository()
        // The new install already generated built-in holidays, occupying low ids.
        RoomHolidayRepository(fresh).replaceSource(
            HolidaySource.BUILT_IN,
            (1..5).map { Holiday(date = LocalDate.of(2026, 1, it), nameEn = "Built-in $it", source = HolidaySource.BUILT_IN) },
        )

        import(bytes, replace = true, manager = managerFor(fresh, freshSettings)).getOrThrow()

        val source = snapshot(db)
        val restored = snapshot(fresh)
        assertEquals(source.alarms, restored.alarms)
        assertEquals(source.groups, restored.groups)
        assertEquals(source.codes, restored.codes)
        assertEquals("seeded presets are replaced by the backup's", source.presets, restored.presets)
        assertEquals(5, restored.holidays.count { it.source == HolidaySource.BUILT_IN })
        val restoredOwn = restored.holidays.filter { it.source != HolidaySource.BUILT_IN }
        assertEquals(source.holidays.filter { it.source != HolidaySource.BUILT_IN }.map { it.copy(id = 0) }, restoredOwn.map { it.copy(id = 0) })
        assertEquals(restored.holidays.size, restored.holidays.map { it.id }.toSet().size)
        assertEquals(ThemeId.RETRO_LCD, freshSettings.current().appearance.themeId)

        // New rows after a restore never collide with restored ids.
        val next = RoomAlarmRepository(fresh).upsert(alarm(4, 0, "after restore"))
        assertTrue(next > source.alarms.maxOf { it.id })
    }

    @Test
    fun replaceWithPartialOlderBackupWipesAndKeepsSettings() = runTest {
        populate()
        val result = importText(
            """{"format":"alarmclock-backup","version":1,"alarms":[{"id":40,"time":{"hour":6,"minute":0},"label":"From an old version","groupId":3}]}""",
        )

        assertEquals(1, result.getOrThrow().alarms)
        val restored = snapshot(db)
        assertEquals(listOf(40L), restored.alarms.map { it.id })
        assertNull("dangling group reference dropped", restored.alarms.single().groupId)
        assertTrue(restored.groups.isEmpty())
        assertTrue(restored.presets.isEmpty())
        assertEquals(listOf(HolidaySource.BUILT_IN), restored.holidays.map { it.source })
        assertEquals("no settings section: settings untouched", deviceSettings, settings.current())
    }

    // --- merge ---------------------------------------------------------------------------------

    @Test
    fun mergeAddsEverythingAsNewRowsWithRemappedReferences() = runTest {
        val ids = populate()
        val before = snapshot(db)
        val bytes = exportBytes()
        settings.update { it.copy(appearance = it.appearance.copy(themeId = ThemeId.PAPER)) }

        val summary = import(bytes, replace = false).getOrThrow()

        val after = snapshot(db)
        assertEquals(3, summary.alarms)
        assertEquals(before.alarms.size * 2, after.alarms.size)
        assertEquals(before.groups.size * 2, after.groups.size)
        assertEquals(before.codes.size * 2, after.codes.size)
        assertEquals(before.photos.size * 2, after.photos.size)
        assertEquals("built-in holidays are not duplicated", before.holidays.size + 2, after.holidays.size)
        assertEquals("settings are untouched by a merge", ThemeId.PAPER, settings.current().appearance.themeId)
        assertEquals(after.alarms.size, after.alarms.map { it.id }.toSet().size)

        // The copy of the "Work" alarm points at the copies of its group, codes and photo target.
        val newWorkGroup = after.groups.single { it.name == "Work" && it.id != ids.work }
        val workCopy = after.alarms.single { it.label == "Work" && it.id != ids.workAlarm }
        assertEquals(newWorkGroup.id, workCopy.groupId)
        val barcode = workCopy.missions.single { it.type == MissionType.BARCODE }
        val newCodes = after.codes.filter { it.id !in setOf(ids.qr, ids.barcode) }
        assertEquals(newCodes.map { it.id }.toSet(), barcode.targetIds.toSet())
        assertEquals(setOf("QR-BATH", "8850000000001"), newCodes.filter { it.id in barcode.targetIds }.map { it.value }.toSet())
        val photo = workCopy.missions.single { it.type == MissionType.PHOTO }
        val newMug = after.photos.single { it.id != ids.mug }
        assertEquals(listOf(newMug.id), photo.targetIds)
        assertEquals(MissionConfig(MissionType.MATH), workCopy.missions.single { it.type == MissionType.MATH })

        // Originals are untouched.
        assertEquals(before.alarms.single { it.id == ids.workAlarm }, after.alarms.single { it.id == ids.workAlarm })

        // Templates are remapped too.
        val ruleCopy = after.calendarRules.single { it.id != before.calendarRules.single().id }
        assertEquals(newWorkGroup.id, ruleCopy.template.groupId)
        assertNotEquals(ids.qr, ruleCopy.template.missions.single().targetIds.single())
        assertEquals("QR-BATH", after.codes.single { it.id == ruleCopy.template.missions.single().targetIds.single() }.value)
        val locationCopy = after.locationAlarms.single { it.id != before.locationAlarms.single().id }
        assertEquals(listOf(newMug.id), locationCopy.template.missions.single().targetIds)
    }

    @Test
    fun mergeAppendsOrderedItemsAfterExistingOnes() = runTest {
        populate()
        val bytes = exportBytes()

        import(bytes, replace = false).getOrThrow()

        val after = snapshot(db)
        assertEquals(listOf("Asia/Tokyo", "Europe/London", "Asia/Tokyo", "Europe/London"), after.worldClocks.map { it.zoneId })
        assertEquals(listOf(0, 1, 2, 3), after.worldClocks.map { it.sortOrder })
        assertEquals(listOf("Drink water", "Stretch", "Drink water", "Stretch"), after.routine.map { it.text })
        assertEquals(listOf("Work", "Weekend", "Work", "Weekend"), after.groups.map { it.name })
        val presetLabels = after.presets.map { it.label }
        assertEquals(14, presetLabels.size)
        assertEquals("Eggs", presetLabels[6])
        assertEquals("Eggs", presetLabels.last())
        assertTrue(after.timers.filter { it.createdAt == clock.now }.size >= 2)
        assertTrue(after.timers.drop(2).all { it.state == TimerState.IDLE })
    }

    @Test
    fun mergeDropsReferencesToItemsMissingFromTheBackup() = runTest {
        val result = importText(
            """{"format":"alarmclock-backup","version":1,
                "registeredCodes":[{"id":5,"label":"Fridge","value":"F"}],
                "alarms":[{"time":{"hour":7,"minute":0},"label":"x","groupId":999,
                  "missions":[{"type":"BARCODE","targetIds":[5,555]},{"type":"PHOTO","targetIds":[1]}]}]}""",
            replace = false,
        )

        assertEquals(1, result.getOrThrow().alarms)
        val alarm = RoomAlarmRepository(db).getAlarms().single()
        val code = RoomMissionAssetRepository(db).observeCodes().first().single()
        assertNull(alarm.groupId)
        assertEquals(listOf(code.id), alarm.missions[0].targetIds)
        assertEquals(emptyList<Long>(), alarm.missions[1].targetIds)
    }

    // --- rejection -----------------------------------------------------------------------------

    @Test
    fun garbageIsRejectedWithTypedErrorsAndChangesNothing() = runTest {
        populate()
        val before = snapshot(db)
        val garbage = listOf(
            "",
            "hello",
            "[1,2,3]",
            "{}",
            """{"format":"something-else","version":1}""",
            """{"format":"alarmclock-backup"}""",
            """{"format":"alarmclock-backup","version":"one"}""",
            """{"format":"alarmclock-backup","version":0}""",
            """{"format":"alarmclock-backup","version":1,"alarms":"not a list"}""",
            """{"format":"alarmclock-backup","version":1,"alarms":[{"time":{"hour":99,"minute":0}}]}""",
            """{"format":"alarmclock-backup","version":1,"holidays":[{"date":"2026-13-45","nameEn":"x","source":"CUSTOM"}]}""",
            """{"format":"alarmclock-backup","version":1,"alarms":[{"id":1},{"id":1}]}""",
            """{"format":"alarmclock-backup","version":1""",
        )

        garbage.forEach { text ->
            listOf(true, false).forEach { replace ->
                val error = importText(text, replace).exceptionOrNull()
                assertTrue("'$text' must be rejected as invalid, got $error", error is InvalidBackupException)
            }
        }
        assertEquals(before, snapshot(db))
        assertEquals(deviceSettings, settings.current())
    }

    @Test
    fun backupsFromANewerFormatAreRejected() = runTest {
        val error = importText("""{"format":"alarmclock-backup","version":2,"alarms":[]}""").exceptionOrNull()

        assertTrue(error is UnsupportedBackupVersionException)
        error as UnsupportedBackupVersionException
        assertEquals(2, error.version)
        assertEquals(1, error.supportedVersion)
    }

    @Test
    fun hugeInputIsRejectedWithoutReadingItAll() = runTest {
        val huge = object : InputStream() {
            var remaining = BackupCodec.MAX_BYTES.toLong() + 10
            override fun read(): Int = if (remaining-- > 0) ' '.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val n = minOf(len.toLong(), remaining).toInt()
                b.fill(' '.code.toByte(), off, off + n)
                remaining -= n
                return n
            }
        }
        assertTrue(manager.importFrom(huge, replace = true).exceptionOrNull() is InvalidBackupException)
    }

    @Test
    fun byteOrderMarkIsAccepted() = runTest {
        populate()
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + exportBytes()
        assertTrue(import(withBom, replace = true).isSuccess)
    }

    @Test
    fun readFailuresAreReportedAsIoErrors() = runTest {
        val broken = object : InputStream() {
            override fun read(): Int = throw java.io.IOException("disk on fire")
        }
        assertTrue(manager.importFrom(broken, replace = true).exceptionOrNull() is BackupIoException)
    }

    @Test
    fun errorMessagesAreLocalized() {
        val errors = listOf(
            InvalidBackupException("x"),
            UnsupportedBackupVersionException(2, 1),
            BackupFolderUnavailableException("x"),
            BackupIoException("x"),
        )
        val thai = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("th")) })
        errors.forEach { error ->
            val english = context.getString(error.messageRes)
            val translated = thai.getString(error.messageRes)
            assertTrue(english.isNotBlank())
            assertNotEquals(english, translated)
            assertTrue(translated.any { it in '฀'..'๿' })
        }
    }

    // --- automatic backups -------------------------------------------------------------------

    private fun backupFolder() = folder.newFolder("drive")

    private fun backupNames(dir: java.io.File) = dir.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun autoBackupWritesADatedFileAndRecordsTheTime() = runTest {
        populate()
        val dir = backupFolder()
        val tree = FakeDocumentsProvider.install(context, dir)
        settings.update { it.copy(backup = it.backup.copy(treeUri = tree.toString(), lastBackupAt = null)) }

        val uri = manager.autoBackupNow().getOrThrow()

        assertEquals(listOf("alarmclock-backup-20261127-0630.json"), backupNames(dir))
        assertTrue(uri.startsWith("content://${FakeDocumentsProvider.AUTHORITY}/"))
        assertTrue(uri.endsWith("alarmclock-backup-20261127-0630.json"))
        assertEquals(clock.now, settings.current().backup.lastBackupAt)

        // The file is a complete backup that restores into a fresh install.
        val fresh = freshDatabase()
        val restored = managerFor(fresh, FakeSettingsRepository()).importFrom(java.io.File(dir, backupNames(dir).single()).inputStream(), replace = true)
        assertEquals(3, restored.getOrThrow().alarms)
        assertEquals(snapshot(db).alarms, snapshot(fresh).alarms)
    }

    @Test
    fun autoBackupKeepsTheNewestTen() = runTest {
        val dir = backupFolder()
        val tree = FakeDocumentsProvider.install(context, dir)
        settings.update { it.copy(backup = it.backup.copy(treeUri = tree.toString())) }
        (1..12).forEach { day -> java.io.File(dir, "alarmclock-backup-202611${"%02d".format(day)}-0630.json").writeText("{}") }
        java.io.File(dir, "alarmclock-backup-manual.json").writeText("{}")
        java.io.File(dir, "shopping-list.txt").writeText("milk")

        manager.autoBackupNow().getOrThrow()
        clock.advance(60_000)
        manager.autoBackupNow().getOrThrow()

        val names = backupNames(dir)
        val automatic = names.filter { BackupFileNames.stampOf(it) != null }
        assertEquals(10, automatic.size)
        assertTrue("alarmclock-backup-20261127-0630.json" in automatic)
        assertTrue("alarmclock-backup-20261127-0631.json" in automatic)
        assertEquals((5..12).map { "alarmclock-backup-202611${"%02d".format(it)}-0630.json" }.toSet(), automatic.filterNot { "20261127-" in it }.toSet())
        assertTrue("files that are not ours stay", "alarmclock-backup-manual.json" in names && "shopping-list.txt" in names)
    }

    @Test
    fun twoBackupsInTheSameMinuteBothSucceed() = runTest {
        val dir = backupFolder()
        val tree = FakeDocumentsProvider.install(context, dir)
        settings.update { it.copy(backup = it.backup.copy(treeUri = tree.toString())) }

        val first = manager.autoBackupNow().getOrThrow()
        val second = manager.autoBackupNow().getOrThrow()

        assertNotEquals(first, second)
        assertEquals(listOf("alarmclock-backup-20261127-0630 (1).json", "alarmclock-backup-20261127-0630.json"), backupNames(dir))
    }

    @Test
    fun autoBackupWithoutAFolderFails() = runTest {
        settings.update { it.copy(backup = it.backup.copy(treeUri = null, lastBackupAt = null)) }
        assertTrue(manager.autoBackupNow().exceptionOrNull() is BackupFolderUnavailableException)

        settings.update { it.copy(backup = it.backup.copy(treeUri = "  ")) }
        assertTrue(manager.autoBackupNow().exceptionOrNull() is BackupFolderUnavailableException)

        settings.update { it.copy(backup = it.backup.copy(treeUri = "content://not.a.tree/document/1")) }
        assertTrue(manager.autoBackupNow().exceptionOrNull() is BackupFolderUnavailableException)
        assertNull(settings.current().backup.lastBackupAt)
    }

    @Test
    fun autoBackupWithARevokedFolderFails() = runTest {
        val dir = backupFolder()
        val tree = FakeDocumentsProvider.install(context, dir)
        settings.update { it.copy(backup = it.backup.copy(treeUri = tree.toString(), lastBackupAt = null)) }
        FakeDocumentsProvider.denyCreate = true

        assertTrue(manager.autoBackupNow().exceptionOrNull() is BackupFolderUnavailableException)
        assertTrue(backupNames(dir).isEmpty())
        assertNull(settings.current().backup.lastBackupAt)
    }

    @Test
    fun aBackupThatCannotBeWrittenIsRemoved() = runTest {
        val dir = backupFolder()
        val tree = FakeDocumentsProvider.install(context, dir)
        settings.update { it.copy(backup = it.backup.copy(treeUri = tree.toString(), lastBackupAt = null)) }
        FakeDocumentsProvider.failOpen = true

        val error = manager.autoBackupNow().exceptionOrNull()

        assertTrue("got $error", error is BackupException)
        assertTrue("the empty file is cleaned up", backupNames(dir).isEmpty())
        assertNull(settings.current().backup.lastBackupAt)
    }
}
