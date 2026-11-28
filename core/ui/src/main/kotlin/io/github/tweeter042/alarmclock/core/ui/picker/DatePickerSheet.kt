package io.github.tweeter042.alarmclock.core.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CalendarLocale
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerFormatter
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.tweeter042.alarmclock.core.designsystem.component.AppBottomSheet
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.settings.AppLanguage
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.ui.R
import io.github.tweeter042.alarmclock.core.ui.format.TimeFormatter
import io.github.tweeter042.alarmclock.core.ui.format.UiFormatters
import io.github.tweeter042.alarmclock.core.ui.format.rememberUiFormatters
import io.github.tweeter042.alarmclock.core.ui.time.rememberZone
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * A bottom sheet with the Material 3 calendar in the current theme's colours and type. Dates are
 * written the user's way: the headline ("Wednesday, 25 November 2569") and the month title
 * ("November 2569") use the Buddhist year when chosen, Thai numerals and the user's first day of
 * the week apply to the grid.
 *
 * Exactly one of [onConfirm] / [onDismiss] is called, after the sheet has closed.
 *
 * @param minDate / [maxDate] limit the selectable days (e.g. today for a one-time alarm).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initialDate: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ui_date_picker_title),
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    confirmLabel: String = stringResource(R.string.ui_done),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = rememberAppDatePickerState(initialDate, minDate, maxDate)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier, sheetState = sheetState) {
        DatePickerSheetContent(
            state = state,
            title = title,
            confirmLabel = confirmLabel,
            onConfirm = { state.selectedDate()?.let { date -> close { onConfirm(date) } } },
            onCancel = { close(onDismiss) },
        )
    }
}

/** The body of [DatePickerSheet] (also used directly in screenshot tests). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerSheetContent(
    state: DatePickerState,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatters = rememberUiFormatters()
    val dateFormatter = remember(formatters) { AppDatePickerFormatter(formatters.time) }
    val selected = state.selectedDate()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(horizontal = AppTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.colors.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = selected?.let { formatters.time.dateLong(it) } ?: stringResource(R.string.ui_date_picker_no_selection),
                style = AppTheme.typography.headlineSmall,
                color = AppTheme.colors.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        DatePicker(
            state = state,
            dateFormatter = dateFormatter,
            colors = themedDatePickerColors(),
            title = null,
            headline = null,
            showModeToggle = false,
        )
        Spacer(Modifier.height(AppTheme.spacing.sm))
        Column(Modifier.padding(horizontal = AppTheme.spacing.xl)) {
            SheetButtons(confirmLabel = confirmLabel, confirmEnabled = selected != null, onConfirm = onConfirm, onCancel = onCancel)
        }
    }
}

/**
 * A [DatePickerState] in the user's language, numerals and first day of the week, limited to
 * [minDate]..[maxDate]. The selection survives configuration changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppDatePickerState(initialDate: LocalDate?, minDate: LocalDate? = null, maxDate: LocalDate? = null): DatePickerState {
    val formatters = rememberUiFormatters()
    val zone = rememberZone()
    val today = remember(zone) { LocalDate.now(zone) }
    val locale = remember(formatters) { calendarLocale(formatters) }
    val bounds = remember(minDate, maxDate, initialDate, today) { DateBounds.of(minDate, maxDate, initialDate, today) }
    val selectable = remember(bounds) { BoundedSelectableDates(bounds) }
    val saver = remember(locale, bounds, selectable) {
        Saver<DatePickerState, List<Long?>>(
            save = { listOf(it.selectedDateMillis, it.displayedMonthMillis) },
            restore = { saved ->
                DatePickerState(
                    locale = locale,
                    initialSelectedDateMillis = saved[0],
                    initialDisplayedMonthMillis = saved[1],
                    yearRange = bounds.years,
                    initialDisplayMode = DisplayMode.Picker,
                    selectableDates = selectable,
                )
            },
        )
    }
    return rememberSaveable(locale, bounds, saver = saver) {
        val initialMillis = initialDate?.toUtcMillis()
        DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initialMillis,
            initialDisplayedMonthMillis = initialMillis ?: (bounds.clamp(today)).toUtcMillis(),
            yearRange = bounds.years,
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectable,
        )
    }
}

/** The selected day as a [LocalDate] (the state works in UTC midnight millis). */
@OptIn(ExperimentalMaterial3Api::class)
fun DatePickerState.selectedDate(): LocalDate? = selectedDateMillis?.let(::utcMillisToDate)

/** Allowed range of a date picker. Pure and unit tested. */
@Immutable
internal data class DateBounds(val min: LocalDate?, val max: LocalDate?, val years: IntRange) {
    fun contains(date: LocalDate): Boolean = (min == null || !date.isBefore(min)) && (max == null || !date.isAfter(max))

    fun clamp(date: LocalDate): LocalDate = when {
        min != null && date.isBefore(min) -> min
        max != null && date.isAfter(max) -> max
        else -> date
    }

    companion object {
        /** Same as Material's `DatePickerDefaults.YearRange`. */
        private val DefaultYears = 1900..2100

        /** The year range always contains the initial date and today, so the picker can open on them. */
        fun of(min: LocalDate?, max: LocalDate?, initial: LocalDate?, today: LocalDate): DateBounds {
            val first = listOfNotNull(min?.year ?: DefaultYears.first, initial?.year, today.year).min()
            val last = listOfNotNull(max?.year ?: DefaultYears.last, initial?.year, today.year).max()
            return DateBounds(min, max, first..last)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class BoundedSelectableDates(private val bounds: DateBounds) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = bounds.contains(utcMillisToDate(utcTimeMillis))

    override fun isSelectableYear(year: Int): Boolean = year in bounds.years &&
        (bounds.min == null || year >= bounds.min.year) && (bounds.max == null || year <= bounds.max.year)
}

/** Month titles and day descriptions in the user's format (Buddhist year, Thai numerals). */
@OptIn(ExperimentalMaterial3Api::class)
private class AppDatePickerFormatter(private val time: TimeFormatter) : DatePickerFormatter {
    override fun formatMonthYear(monthMillis: Long?, locale: CalendarLocale): String? =
        monthMillis?.let { time.monthYear(utcMillisToDate(it)) }

    override fun formatDate(dateMillis: Long?, locale: CalendarLocale, forContentDescription: Boolean): String? =
        dateMillis?.let { millis ->
            val date = utcMillisToDate(millis)
            if (forContentDescription) time.dateLong(date) else time.dateShort(date)
        }

    override fun equals(other: Any?): Boolean = other is AppDatePickerFormatter && other.time === time

    override fun hashCode(): Int = System.identityHashCode(time)
}

/**
 * The picker's calendar locale: the app language, plus Unicode extensions for Thai digits
 * (`nu-thai`) and the first day of the week (`fw`), which the grid's numbers and week layout follow.
 */
internal fun calendarLocale(formatters: UiFormatters): Locale =
    calendarLocale(formatters.strings.language, formatters.options.numerals, formatters.options.firstDayOfWeek)

internal fun calendarLocale(language: AppLanguage, numerals: NumeralStyle, firstDayOfWeek: DayOfWeek): Locale {
    val builder = Locale.Builder().setLanguage(language.tag)
    if (language == AppLanguage.THAI) builder.setRegion("TH")
    runCatching { builder.setUnicodeLocaleKeyword("fw", firstDayOfWeek.name.take(3).lowercase(Locale.ROOT)) }
    if (numerals == NumeralStyle.THAI) runCatching { builder.setUnicodeLocaleKeyword("nu", "thai") }
    return builder.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun themedDatePickerColors(): DatePickerColors {
    val c = AppTheme.colors
    val selectedContainer = if (AppTheme.traits.outlinedPrimary) c.surfaceVariant else c.primary
    val selectedContent = if (AppTheme.traits.outlinedPrimary) c.primary else c.onPrimary
    return DatePickerDefaults.colors(
        containerColor = Color.Transparent,
        titleContentColor = c.onSurfaceVariant,
        headlineContentColor = c.onSurface,
        weekdayContentColor = c.onSurfaceVariant,
        subheadContentColor = c.onSurfaceVariant,
        navigationContentColor = c.onSurface,
        yearContentColor = c.onSurface,
        disabledYearContentColor = c.disabledContent,
        currentYearContentColor = c.primary,
        selectedYearContentColor = selectedContent,
        selectedYearContainerColor = selectedContainer,
        dayContentColor = c.onSurface,
        disabledDayContentColor = c.disabledContent,
        selectedDayContentColor = selectedContent,
        selectedDayContainerColor = selectedContainer,
        todayContentColor = c.primary,
        todayDateBorderColor = c.primary,
        dividerColor = c.outlineVariant,
    )
}

internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun utcMillisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
