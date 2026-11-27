package io.github.tweeter042.alarmclock.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.preview.ThemePreviewCard
import io.github.tweeter042.alarmclock.core.designsystem.theme.AlarmClockTheme
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme
import io.github.tweeter042.alarmclock.core.model.settings.AccessibilitySettings
import io.github.tweeter042.alarmclock.core.model.settings.AppearanceSettings
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

internal const val OUT = "build/screenshots-out"

/** Renders [content] inside [AlarmClockTheme] and writes `build/screenshots-out/<name>.png`. */
internal fun captureThemed(
    name: String,
    themeId: ThemeId,
    dark: Boolean,
    paletteId: String? = null,
    // Static frames: infinite animations (blinking colon, cursor) would keep the test clock busy.
    // Reduce motion only changes animations, so the captured frame equals the steady state.
    accessibility: AccessibilitySettings = AccessibilitySettings(reduceMotion = true),
    content: @Composable () -> Unit,
) {
    captureRoboImage("$OUT/$name.png") {
        AlarmClockTheme(
            appearance = AppearanceSettings(themeId = themeId),
            accessibility = accessibility,
            forceDark = dark,
            paletteOverride = paletteId,
            content = content,
        )
    }
}

internal fun ThemeId.slug() = name.lowercase(Locale.ROOT)

/**
 * Kitchen-sink screens for every theme (light and dark; dark-only themes once), a second sheet
 * with the remaining components, every palette as live previews, and a Thai variant.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ThemeScreenshotTest(private val themeId: ThemeId) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun themes(): List<Array<Any>> = ThemeId.entries.map { arrayOf(it) }
    }

    private val info = ThemeCatalog.theme(themeId)
    private val modes = if (info.supportsLight) listOf(false, true) else listOf(true)

    private fun mode(dark: Boolean) = if (!info.supportsLight) "dark" else if (dark) "dark" else "light"

    @Test
    fun kitchenSink() {
        modes.forEach { dark ->
            captureThemed("kitchen_${themeId.slug()}_${mode(dark)}", themeId, dark) { KitchenSink(Sample.English) }
        }
    }

    @Test
    fun components() {
        modes.forEach { dark ->
            captureThemed("components_${themeId.slug()}_${mode(dark)}", themeId, dark) { ComponentsSheet(Sample.English) }
        }
    }

    @Test
    fun overlays() {
        modes.forEach { dark ->
            captureThemed("overlays_${themeId.slug()}_${mode(dark)}", themeId, dark) { OverlaysSheet(Sample.English) }
        }
    }

    @Test
    fun thai() {
        RuntimeEnvironment.setQualifiers("+th")
        val dark = !info.supportsLight
        captureThemed("thai_kitchen_${themeId.slug()}", themeId, dark) { KitchenSink(Sample.Thai) }
        captureThemed("thai_components_${themeId.slug()}", themeId, dark) { ComponentsSheet(Sample.Thai) }
        captureThemed("thai_overlays_${themeId.slug()}", themeId, dark) { OverlaysSheet(Sample.Thai) }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun palettes() {
        // Every palette of the theme as a live miniature, light then dark.
        captureThemed("palettes_${themeId.slug()}", ThemeId.MINIMAL, dark = false) {
            ThemedBackground(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(12.dp)) {
                    Text(info.nameEn, style = AppTheme.typography.titleLarge, color = AppTheme.colors.onBackground)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        modes.forEach { dark ->
                            info.palettes.forEach { palette ->
                                ThemePreviewCard(
                                    themeId = themeId,
                                    paletteId = palette.id,
                                    dark = dark,
                                    selected = palette.id == info.defaultPaletteId && dark == modes.first(),
                                    onClick = {},
                                    width = 90.dp,
                                    showName = true,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
