package io.github.tweeter042.alarmclock.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockDisplay
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockSize
import io.github.tweeter042.alarmclock.core.designsystem.clock.ClockText
import io.github.tweeter042.alarmclock.core.designsystem.component.AppCard
import io.github.tweeter042.alarmclock.core.designsystem.component.AppDivider
import io.github.tweeter042.alarmclock.core.designsystem.component.AppSwitch
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.settings.NumeralStyle
import io.github.tweeter042.alarmclock.core.model.theme.ClockFace
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime
import java.util.Locale

/**
 * Every clock face at hero size in the theme it belongs to (12 h, 24 h with seconds, a list row
 * and a timer readout), the faces as overrides inside another theme, and Thai numerals/AM-PM.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ClockFaceScreenshotTest(private val face: ClockFace) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun faces(): List<Array<Any>> = ClockFace.entries.map { arrayOf(it) }

        /** The theme whose native face this is (PLAIN is shown in Minimal). */
        fun nativeTheme(face: ClockFace): ThemeId = when (face) {
            ClockFace.PLAIN -> ThemeId.MINIMAL
            ClockFace.SEVEN_SEGMENT -> ThemeId.RETRO_LCD
            ClockFace.FLIP -> ThemeId.FLIP_CLOCK
            ClockFace.ANALOG -> ThemeId.ANALOG
            ClockFace.PIXEL -> ThemeId.PIXEL
            ClockFace.TERMINAL -> ThemeId.TERMINAL
            ClockFace.NEON_GLOW -> ThemeId.NEON
            ClockFace.SERIF -> ThemeId.PAPER
        }
    }

    private val slug = face.name.lowercase(Locale.ROOT)

    @Test
    fun heroInNativeTheme() {
        val theme = nativeTheme(face)
        for (dark in listOf(false, true)) {
            captureThemed("face_${slug}_${if (dark) "dark" else "light"}", theme, dark) { FaceSheet(face, NumeralStyle.ARABIC) }
        }
    }

    @Test
    fun thaiNumeralsAndLabels() {
        RuntimeEnvironment.setQualifiers("+th")
        captureThemed("face_${slug}_thai", nativeTheme(face), dark = true) { FaceSheet(face, NumeralStyle.THAI) }
    }

    @Test
    fun asOverrideInMinimal() {
        captureThemed("face_${slug}_in_minimal", ThemeId.MINIMAL, dark = false) {
            ThemedBackground(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ClockDisplay(time = LocalTime.of(21, 45, 30), use24h = true, faceOverride = face, size = ClockSize.Hero)
                    ClockDisplay(time = LocalTime.of(9, 5), use24h = false, faceOverride = face, size = ClockSize.Medium)
                    ClockDisplay(time = LocalTime.of(6, 45), use24h = true, faceOverride = face, size = ClockSize.Compact)
                }
            }
        }
    }
}

@Composable
private fun FaceSheet(face: ClockFace, numerals: NumeralStyle) {
    ThemedBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = AppTheme.spacing.screen, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClockDisplay(time = LocalTime.of(7, 30, 15), use24h = false, numerals = numerals, size = ClockSize.Hero, faceOverride = face)
            ClockDisplay(time = LocalTime.of(23, 59, 42), use24h = true, showSeconds = true, numerals = numerals, size = ClockSize.Large, faceOverride = face)
            AppDivider()
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ClockDisplay(
                        time = LocalTime.of(6, 45),
                        use24h = false,
                        numerals = numerals,
                        size = ClockSize.Compact,
                        faceOverride = face,
                        modifier = Modifier.weight(1f),
                    )
                    AppSwitch(checked = true, onCheckedChange = {})
                }
            }
            Text(face.name, style = AppTheme.typography.labelLarge, color = AppTheme.colors.onSurfaceVariant)
            ClockText("12:34.56", size = ClockSize.Medium, numerals = numerals, faceOverride = face)
        }
    }
}
