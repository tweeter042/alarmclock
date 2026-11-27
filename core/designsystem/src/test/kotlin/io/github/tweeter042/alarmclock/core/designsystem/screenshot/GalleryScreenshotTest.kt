package io.github.tweeter042.alarmclock.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tweeter042.alarmclock.core.designsystem.component.SectionHeader
import io.github.tweeter042.alarmclock.core.designsystem.component.ThemedBackground
import io.github.tweeter042.alarmclock.core.designsystem.preview.PaletteSwatches
import io.github.tweeter042.alarmclock.core.designsystem.preview.ThemePreviewCard
import io.github.tweeter042.alarmclock.core.model.theme.ThemeCatalog
import io.github.tweeter042.alarmclock.core.model.theme.ThemeId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/** The appearance gallery: all 12 themes as live previews plus palette swatches. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GalleryScreenshotTest {

    @OptIn(ExperimentalLayoutApi::class)
    private fun gallery(name: String, dark: Boolean) {
        captureThemed(name, ThemeId.MINIMAL, dark = dark) {
            ThemedBackground(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding().padding(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeCatalog.themes.forEach { theme ->
                            ThemePreviewCard(
                                themeId = theme.id,
                                paletteId = theme.defaultPaletteId,
                                dark = dark,
                                selected = theme.id == ThemeId.GLASS,
                                onClick = {},
                                width = 88.dp,
                            )
                        }
                    }
                    SectionHeader("Palettes")
                    PaletteSwatches(themeId = ThemeId.BRUTAL, selectedPaletteId = "pink_black", dark = dark, onSelect = {})
                }
            }
        }
    }

    @Test
    fun galleryLight() = gallery("gallery_light", dark = false)

    @Test
    fun galleryDark() = gallery("gallery_dark", dark = true)

    @Test
    fun galleryThai() {
        RuntimeEnvironment.setQualifiers("+th")
        gallery("gallery_thai", dark = false)
    }
}
