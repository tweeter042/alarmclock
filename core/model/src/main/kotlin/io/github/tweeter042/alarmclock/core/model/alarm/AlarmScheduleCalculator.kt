package io.github.tweeter042.alarmclock.core.model.alarm

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Pure, deterministic schedule computation shared by the ring engine, the UI ("rings in 7 h 3 min")
 * and the Wear OS app. All functions are side-effect free and unit tested.
 *
 * Semantics:
 * - A disabled alarm, or an alarm whose group is disabled, has no occurrence.
 * - The result is always strictly after `after`.
 * - DST gaps resolve forward (02:30 in a skipped hour rings at 03:30); overlaps use the earlier offset.
 * - `skipHolidays` skips any day for which `isHoliday(date)` is true (not applied to Once with a date
 *   or SpecificDates, where the user picked the exact day).
 * - `skippedOccurrenceAt` skips exactly that one occurrence (unless `ignoreSkip`).
 */
object AlarmScheduleCalculator {

    /** How far ahead to search (covers long rotation patterns and sparse weekly rules). */
    const val SEARCH_DAYS: Long = 800

    fun nextOccurrence(
        alarm: Alarm,
        after: ZonedDateTime,
        isHoliday: (LocalDate) -> Boolean = { false },
        groupEnabled: Boolean = true,
        ignoreSkip: Boolean = false,
    ): ZonedDateTime? {
        if (!alarm.enabled || !groupEnabled) return null
        return occurrencesSequence(alarm, after, isHoliday, ignoreSkip).firstOrNull()
    }

    /** Up to [limit] upcoming occurrences (useful for previews such as "next 5 rings"). */
    fun upcoming(
        alarm: Alarm,
        after: ZonedDateTime,
        limit: Int,
        isHoliday: (LocalDate) -> Boolean = { false },
        groupEnabled: Boolean = true,
    ): List<ZonedDateTime> {
        if (!alarm.enabled || !groupEnabled || limit <= 0) return emptyList()
        return occurrencesSequence(alarm, after, isHoliday, ignoreSkip = false).take(limit).toList()
    }

    /** True if [date] is a day on which the alarm's repeat rule (ignoring holidays/skip) fires. */
    fun matchesRule(rule: RepeatRule, date: LocalDate): Boolean = when (rule) {
        is RepeatRule.Once -> rule.date == null || rule.date == date
        is RepeatRule.Weekly -> rule.days.isEmpty() || date.dayOfWeek.value in rule.days
        is RepeatRule.Rotation -> {
            if (rule.pattern.isEmpty()) {
                false
            } else {
                val offset = ChronoUnit.DAYS.between(rule.anchorDate, date)
                rule.pattern[Math.floorMod(offset, rule.pattern.size.toLong()).toInt()]
            }
        }
        is RepeatRule.SpecificDates -> date in rule.dates
    }

    private fun occurrencesSequence(
        alarm: Alarm,
        after: ZonedDateTime,
        isHoliday: (LocalDate) -> Boolean,
        ignoreSkip: Boolean,
    ): Sequence<ZonedDateTime> {
        val zone = after.zone
        val time = alarm.time.toLocalTime()
        val startDate = after.toLocalDate()
        val skipped = if (ignoreSkip) null else alarm.skippedOccurrenceAt

        fun at(date: LocalDate): ZonedDateTime = ZonedDateTime.of(date, time, zone)
        fun notSkipped(z: ZonedDateTime) = skipped == null || z.toInstant().toEpochMilli() != skipped

        return when (val rule = alarm.repeat) {
            is RepeatRule.Once -> {
                val date = rule.date
                if (date != null) {
                    sequenceOf(at(date)).filter { it.isAfter(after) && notSkipped(it) }.take(1)
                } else {
                    dateSequence(startDate)
                        .filter { !(alarm.skipHolidays && isHoliday(it)) }
                        .map(::at)
                        .filter { it.isAfter(after) && notSkipped(it) }
                        .take(1)
                }
            }
            is RepeatRule.Weekly -> {
                val days = rule.days
                dateSequence(startDate)
                    .filter { days.isEmpty() || it.dayOfWeek.value in days }
                    .filter { !(alarm.skipHolidays && isHoliday(it)) }
                    .map(::at)
                    .filter { it.isAfter(after) && notSkipped(it) }
                    .let { if (days.isEmpty()) it.take(1) else it }
            }
            is RepeatRule.Rotation -> {
                if (rule.pattern.none { it }) {
                    emptySequence()
                } else {
                    dateSequence(startDate)
                        .filter { matchesRule(rule, it) }
                        .filter { !(alarm.skipHolidays && isHoliday(it)) }
                        .map(::at)
                        .filter { it.isAfter(after) && notSkipped(it) }
                }
            }
            is RepeatRule.SpecificDates -> rule.dates
                .asSequence()
                .distinct()
                .sorted()
                .filter { !it.isBefore(startDate) }
                .map(::at)
                .filter { it.isAfter(after) && notSkipped(it) }
        }
    }

    private fun dateSequence(start: LocalDate): Sequence<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.take(SEARCH_DAYS.toInt())
}

/** Snooze rules shared by the engine and the ring UI. */
object SnoozePolicy {

    /** Whether another snooze is allowed after [snoozeCount] snoozes so far. */
    fun canSnooze(config: SnoozeConfig, snoozeCount: Int): Boolean =
        config.enabled && (config.maxCount <= 0 || snoozeCount < config.maxCount)

    /** Duration of the next snooze, given how many snoozes already happened. */
    fun nextDurationMinutes(config: SnoozeConfig, snoozeCount: Int): Int {
        val base = config.durationMinutes.coerceAtLeast(1)
        return when (config.mode) {
            SnoozeMode.DECREASING ->
                (base - config.decreaseStepMinutes.coerceAtLeast(0) * snoozeCount)
                    .coerceAtLeast(config.minDurationMinutes.coerceAtLeast(1))
            else -> base
        }
    }

    /** True if missions must be completed before snoozing. */
    fun missionsRequiredToSnooze(alarm: Alarm): Boolean =
        alarm.snooze.mode == SnoozeMode.MISSION_TO_SNOOZE && alarm.missions.isNotEmpty()

    /** Mission difficulty to use for a ring that has been snoozed [snoozeCount] times. */
    fun effectiveDifficulty(alarm: Alarm, mission: MissionConfig, snoozeCount: Int): MissionDifficulty =
        if (alarm.snooze.mode == SnoozeMode.HARDER_EACH_TIME) {
            mission.difficulty.raisedBy(snoozeCount)
        } else {
            mission.difficulty
        }
}
