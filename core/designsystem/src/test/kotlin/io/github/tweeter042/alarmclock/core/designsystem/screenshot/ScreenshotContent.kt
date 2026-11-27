package io.github.tweeter042.alarmclock.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.SolidColor
import io.github.tweeter042.alarmclock.core.designsystem.component.AppListItem
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSnackbar
import io.github.tweeter042.alarmclock.core.designsystem.component.DialogContainer
import io.github.tweeter042.alarmclock.core.designsystem.component.SheetBody
import io.github.tweeter042.alarmclock.core.designsystem.component.SheetDragHandle
import io.github.tweeter042.alarmclock.core.designsystem.component.themedContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockDisplay
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockSize
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockText
import io.github.tweeter042.alarmclock.core.designsystem.component.AppBadge
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonSize
import io.github.tweeter042.alarmclock.core.designsystem.component.AppButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCard
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCardVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCheckbox
import io.github.tweeter042.alarmclock.core.designsystem.component.AppChip
import io.github.tweeter042.alarmclock.core.designsystem.component.AppDivider
import io.github.tweeter042.alarmclock.core.designsystem.component.AppFab
import io.github.tweeter042.alarmclock.core.designsystem.component.AppFilterChip
import io.github.tweeter042.alarmclock.core.designsystem.component.AppIconButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppIconButtonVariant
import io.github.tweeter042.alarmclock.core.designsystem.component.AppLinearProgress
import io.github.tweeter042.alarmclock.core.designsystem.component.AppNavigationBar
import io.github.tweeter042.alarmclock.core.designsystem.component.AppNavigationItem
import io.github.tweeter042.alarmclock.core.designsystem.component.AppRadioButton
import io.github.tweeter042.alarmclock.core.designsystem.component.AppScaffold
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSegmentedButtons
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSlider
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSwitch
import io.github.tweeter042.alarmclock.core.designsystem.component.AppTextField
import io.github.tweeter042.alarmclock.core.designsystem.component.DayOfWeekSelector
import io.github.tweeter042.alarmclock.core.designsystem.component.EmptyState
import io.github.tweeter042.alarmclock.core.designsystem.component.ProgressRing
import io.github.tweeter042.alarmclock.core.designsystem.component.SectionHeader
import io.github.tweeter042.alarmclock.core.designsystem.component.SettingsNavRow
import io.github.tweeter042.alarmclock.core.designsystem.component.SettingsSwitchRow
import io.github.tweeter042.alarmclock.core.designsystem.component.SettingsValueRow
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.icon.AppIcons
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import java.time.LocalTime

/** Sample texts in English and Thai (test content only; production strings live in resources). */
internal data class Sample(
    val title: String,
    val subtitle: String,
    val today: String,
    val wakeUp: String,
    val weekdays: String,
    val snooze: String,
    val save: String,
    val cancel: String,
    val volume: String,
    val gentle: String,
    val vibrate: String,
    val missions: String,
    val tabs: List<String>,
    val delete: String,
    val more: String,
    val sound: String,
    val soundValue: String,
    val label: String,
    val labelHint: String,
    val emptyTitle: String,
    val emptyBody: String,
    val add: String,
    val segments: List<String>,
    val fadeIn: String,
    val dialogTitle: String,
    val dialogBody: String,
    val snackbar: String,
    val undo: String,
) {
    companion object {
        val English = Sample(
            title = "Alarms", subtitle = "Next alarm in 7 h 30 min", today = "Upcoming", wakeUp = "Wake up",
            weekdays = "Weekdays", snooze = "Snooze", save = "Save", cancel = "Cancel", volume = "Volume",
            gentle = "Gentle wake", vibrate = "Vibrate", missions = "Missions",
            tabs = listOf("Alarm", "World", "Timer", "Watch", "Sleep"), delete = "Delete", more = "More",
            sound = "Sound", soundValue = "Sunrise chime", label = "Label", labelHint = "Morning run",
            emptyTitle = "No timers yet", emptyBody = "Tap + to start a timer. Presets appear here.",
            add = "Add timer", segments = listOf("Easy", "Medium", "Hard"), fadeIn = "Fade in",
            dialogTitle = "Delete alarm?", dialogBody = "The 06:45 weekday alarm will be removed.",
            snackbar = "Alarm set for 7 h 30 min from now", undo = "Undo",
        )
        val Thai = Sample(
            title = "นาฬิกาปลุก", subtitle = "ปลุกครั้งถัดไปในอีก 7 ชม. 30 นาที", today = "เร็ว ๆ นี้", wakeUp = "ตื่นนอน",
            weekdays = "วันทำงาน", snooze = "เลื่อนปลุก", save = "บันทึก", cancel = "ยกเลิก", volume = "ระดับเสียง",
            gentle = "ปลุกแบบนุ่มนวล", vibrate = "สั่น", missions = "ภารกิจ",
            tabs = listOf("ปลุก", "เวลาโลก", "ตัวจับเวลา", "นาฬิกาจับเวลา", "การนอน"), delete = "ลบ", more = "เพิ่มเติม",
            sound = "เสียงปลุก", soundValue = "ระฆังรุ่งอรุณ", label = "ชื่อปลุก", labelHint = "วิ่งตอนเช้า",
            emptyTitle = "ยังไม่มีตัวจับเวลา", emptyBody = "แตะ + เพื่อเริ่มจับเวลา ค่าที่ตั้งไว้จะแสดงที่นี่",
            add = "เพิ่มตัวจับเวลา", segments = listOf("ง่าย", "ปานกลาง", "ยาก"), fadeIn = "ค่อย ๆ ดังขึ้น",
            dialogTitle = "ลบการปลุกนี้ไหม", dialogBody = "การปลุก 06:45 ในวันทำงานจะถูกลบ",
            snackbar = "ตั้งปลุกแล้ว อีก 7 ชม. 30 นาที", undo = "เลิกทำ",
        )
    }
}

/** The main kitchen sink: One UI scaffold, hero clock, alarm card, buttons, switch, slider, chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KitchenSink(s: Sample) {
    val tabIcons = listOf(AppIcons.Alarm, AppIcons.WorldClock, AppIcons.Hourglass, AppIcons.Stopwatch, AppIcons.Bedtime)
    AppScaffold(
        title = s.title,
        subtitle = s.subtitle,
        actions = {
            AppIconButton(AppIcons.Add, s.add, onClick = {})
            AppIconButton(AppIcons.More, s.more, onClick = {})
        },
        bottomBar = {
            AppNavigationBar {
                s.tabs.forEachIndexed { i, tab ->
                    AppNavigationItem(selected = i == 0, onClick = {}, icon = tabIcons[i], label = tab, badgeCount = if (i == 2) 2 else null)
                }
            }
        },
        floatingActionButton = { AppFab(AppIcons.Add, s.add, onClick = {}) },
    ) { padding ->
        val spacing = AppTheme.spacing
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 96.dp,
                start = spacing.screen,
                end = spacing.screen,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.gutter),
        ) {
            item {
                ClockDisplay(time = LocalTime.of(7, 30, 15), use24h = false, size = ClockSize.Large, modifier = Modifier.fillMaxWidth())
            }
            item {
                AppCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            ClockDisplay(time = LocalTime.of(6, 45), use24h = true, size = ClockSize.Compact)
                            Text(
                                "${s.wakeUp} · ${s.weekdays}",
                                style = AppTheme.typography.bodyMedium,
                                color = AppTheme.colors.onSurfaceVariant,
                            )
                        }
                        AppSwitch(checked = true, onCheckedChange = {})
                    }
                    Spacer(Modifier.height(12.dp))
                    DayOfWeekSelector(selectedDays = setOf(1, 2, 3, 4, 5), onSelectedDaysChange = {}, firstDayOfWeek = 7)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppButton(s.save, onClick = {}, leadingIcon = AppIcons.Check)
                    AppButton(s.cancel, onClick = {}, variant = AppButtonVariant.Secondary)
                    AppButton(s.snooze, onClick = {}, variant = AppButtonVariant.Tonal)
                }
            }
            item {
                AppSlider(value = 0.7f, onValueChange = {}, label = s.volume, valueLabel = { "${(it * 100).toInt()}%" })
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppFilterChip(s.gentle, selected = true, onClick = {})
                    AppFilterChip(s.vibrate, selected = false, onClick = {})
                    AppChip(s.missions, onClick = {}, leadingIcon = AppIcons.Mission)
                }
            }
        }
    }
}

/** Second sheet: the remaining components on the themed page background. */
@Composable
internal fun ComponentsSheet(s: Sample) {
    val spacing = AppTheme.spacing
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(variant = AppCardVariant.Emphasis, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClockText("05:00", size = ClockSize.Medium, modifier = Modifier.weight(1f))
                    ProgressRing(progress = 0.62f, size = 64.dp, strokeWidth = 6.dp)
                }
                Spacer(Modifier.height(8.dp))
                AppLinearProgress(progress = 0.62f)
            }
            AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                SectionHeader(s.today)
                SettingsSwitchRow(title = s.vibrate, checked = true, onCheckedChange = {}, summary = s.gentle, icon = AppIcons.Vibration)
                SettingsValueRow(title = s.sound, value = s.soundValue, onClick = {}, icon = AppIcons.Music)
                SettingsNavRow(title = s.missions, onClick = {}, summary = s.segments.joinToString(" · "), icon = AppIcons.Mission)
            }
            AppSegmentedButtons(options = s.segments, selectedIndex = 1, onSelect = {})
            AppTextField(value = "", onValueChange = {}, label = s.label, placeholder = s.labelHint, leadingIcon = AppIcons.Label)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppCheckbox(checked = true, onCheckedChange = {})
                AppCheckbox(checked = false, onCheckedChange = {})
                AppRadioButton(selected = true, onClick = {})
                AppRadioButton(selected = false, onClick = {})
                AppSwitch(checked = false, onCheckedChange = {})
                Spacer(Modifier.weight(1f))
                AppBadge(count = 3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppButton(s.delete, onClick = {}, variant = AppButtonVariant.Danger, size = AppButtonSize.Small)
                AppButton(s.more, onClick = {}, variant = AppButtonVariant.Text, size = AppButtonSize.Small)
                AppButton(s.save, onClick = {}, enabled = false, size = AppButtonSize.Small)
                Spacer(Modifier.weight(1f))
                AppIconButton(AppIcons.Delete, s.delete, onClick = {}, variant = AppIconButtonVariant.Outlined)
                AppIconButton(AppIcons.Edit, s.more, onClick = {}, variant = AppIconButtonVariant.Filled)
            }
            AppDivider()
            AppButton(s.snooze, onClick = {}, size = AppButtonSize.ExtraLarge, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Third sheet: dialog and bottom-sheet surfaces, a snackbar and an empty state. */
@Composable
internal fun OverlaysSheet(s: Sample) {
    val spacing = AppTheme.spacing
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = spacing.screen, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DialogContainer(
                title = s.dialogTitle,
                text = s.dialogBody,
                confirmButton = { AppButton(s.delete, onClick = {}, variant = AppButtonVariant.Danger, size = AppButtonSize.Small) },
                dismissButton = { AppButton(s.cancel, onClick = {}, variant = AppButtonVariant.Text, size = AppButtonSize.Small) },
                modifier = Modifier.fillMaxWidth(),
            )
            AppSnackbar(FakeSnackbar(s.snackbar, s.undo))
            EmptyState(icon = AppIcons.Hourglass, title = s.emptyTitle, body = s.emptyBody, actionLabel = s.add, onAction = {})
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .themedContainer(AppTheme.shapes.sheet, SolidColor(AppTheme.colors.surface), null, AppTheme.shadow),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SheetDragHandle()
                SheetBody(title = s.sound) {
                    AppListItem(headline = s.soundValue, leadingIcon = AppIcons.Music, trailing = { AppRadioButton(selected = true, onClick = null) }, onClick = {})
                    AppListItem(headline = s.gentle, leadingIcon = AppIcons.Sunrise, trailing = { AppRadioButton(selected = false, onClick = null) }, onClick = {})
                    AppLinearProgress(progress = null, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
            }
        }
    }
}

private class FakeSnackbar(message: String, action: String) : SnackbarData {
    override val visuals: SnackbarVisuals = object : SnackbarVisuals {
        override val message = message
        override val actionLabel = action
        override val withDismissAction = false
        override val duration = SnackbarDuration.Short
    }
    override fun performAction() = Unit
    override fun dismiss() = Unit
}
