package io.github.tweeter042.alarmclock.core.alarm.schedule

import io.github.tweeter042.alarmclock.core.model.entity.CalendarAlarmRule
import io.github.tweeter042.alarmclock.core.model.service.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The alarm a calendar rule generates for one event. */
internal data class CalendarOccurrence(
    val ruleId: Long,
    /** When the alarm rings (epoch millis). */
    val alarmAt: Long,
    val eventId: Long,
    val eventTitle: String,
    val eventBegin: Long,
)

/**
 * Pure selection of the alarm a [CalendarAlarmRule] generates.
 *
 * Semantics, per local day D (today first, at most [HORIZON_MILLIS] ahead):
 * - D's ISO day of week must be in [CalendarAlarmRule.days].
 * - Candidate events: begin on day D (local time; all-day events count for their own date and
 *   "begin" at local midnight), title contains [CalendarAlarmRule.keyword] (case-insensitive, blank
 *   = any), calendar in [CalendarAlarmRule.calendarIds] (empty = any), all-day events skipped when
 *   [CalendarAlarmRule.ignoreAllDayEvents].
 * - Only the FIRST candidate of the day counts. Its alarm is `begin - minutesBefore`, raised to
 *   [CalendarAlarmRule.earliest] on day D if earlier ("never ring earlier than"). If the alarm would
 *   be later than [CalendarAlarmRule.latest] on day D, the day has no alarm (later events would be
 *   even later).
 * - The alarm must be in the future, within the horizon, and not already handled
 *   (dismissed/missed) — otherwise the next day is tried.
 */
internal object CalendarAlarmPlanner {
    const val HORIZON_MILLIS: Long = 48L * 60 * 60 * 1000

    /** Range of events to request so [nextOccurrence] sees every day it may need. */
    fun queryWindow(now: Long, zone: ZoneId, horizonMillis: Long = HORIZON_MILLIS): LongRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(now + horizonMillis).atZone(zone).toLocalDate()
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return from until to
    }

    fun nextOccurrence(
        rule: CalendarAlarmRule,
        events: List<CalendarEvent>,
        now: Long,
        zone: ZoneId,
        horizonMillis: Long = HORIZON_MILLIS,
        isHandled: (alarmAt: Long) -> Boolean = { false },
    ): CalendarOccurrence? {
        if (!rule.enabled || rule.days.isEmpty()) return null
        val keyword = rule.keyword.trim()
        val horizon = now + horizonMillis
        val candidates = events.filter { event ->
            (!rule.ignoreAllDayEvents || !event.allDay) &&
                (keyword.isEmpty() || event.title.contains(keyword, ignoreCase = true)) &&
                (rule.calendarIds.isEmpty() || event.calendarId in rule.calendarIds)
        }
        val byDay: Map<LocalDate, List<Pair<CalendarEvent, ZonedDateTime>>> = candidates
            .map { it to localBegin(it, zone) }
            .groupBy { it.second.toLocalDate() }

        var day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(horizon).atZone(zone).toLocalDate()
        while (!day.isAfter(lastDay)) {
            if (day.dayOfWeek.value in rule.days) {
                val first = byDay[day]
                    ?.minWithOrNull(compareBy<Pair<CalendarEvent, ZonedDateTime>>({ it.second.toInstant() }, { it.first.id }))
                if (first != null) {
                    val alarmAt = alarmTimeFor(rule, day, first.second, zone)
                    if (alarmAt != null) {
                        if (alarmAt > horizon) return null
                        if (alarmAt > now && !isHandled(alarmAt)) {
                            val (event, _) = first
                            return CalendarOccurrence(rule.id, alarmAt, event.id, event.title, event.begin)
                        }
                    }
                }
            }
            day = day.plusDays(1)
        }
        return null
    }

    /** Alarm time for an event beginning at [begin] on [day], or null if it is later than `latest`. */
    private fun alarmTimeFor(rule: CalendarAlarmRule, day: LocalDate, begin: ZonedDateTime, zone: ZoneId): Long? {
        var alarm = begin.minusMinutes(rule.minutesBefore.coerceAtLeast(0).toLong())
        rule.earliest?.let { earliest ->
            val bound = ZonedDateTime.of(day, earliest.toLocalTime(), zone)
            if (alarm.isBefore(bound)) alarm = bound
        }
        rule.latest?.let { latest ->
            val bound = ZonedDateTime.of(day, latest.toLocalTime(), zone)
            if (alarm.isAfter(bound)) return null
        }
        return alarm.toInstant().toEpochMilli()
    }

    /**
     * Local begin of an event. CalendarContract stores all-day events at UTC midnight of their date;
     * they are treated as beginning at local midnight of that date.
     */
    private fun localBegin(event: CalendarEvent, zone: ZoneId): ZonedDateTime =
        if (event.allDay) {
            val date = Instant.ofEpochMilli(event.begin).atOffset(ZoneOffset.UTC).toLocalDate()
            ZonedDateTime.of(date, LocalTime.MIDNIGHT, zone)
        } else {
            Instant.ofEpochMilli(event.begin).atZone(zone)
        }
}
