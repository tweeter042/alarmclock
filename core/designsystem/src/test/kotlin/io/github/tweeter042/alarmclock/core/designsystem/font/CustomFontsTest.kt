package io.github.tweeter042.alarmclock.core.designsystem.font

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.tweeter042.alarmclock.core.designsystem.R
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CustomFontsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun cleanUp() {
        CustomFonts.directory(context).deleteRecursively()
    }

    private fun bundledFontFile(name: String): File {
        val file = File(context.cacheDir, name)
        context.resources.openRawResource(R.font.inter_400).use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    @Test
    fun namesAreSanitizedAndSafe() {
        assertEquals("my_font", CustomFonts.sanitize("My Font!"))
        assertEquals("font", CustomFonts.sanitize("!!!"))
        assertTrue(CustomFonts.isSafeName("my_font.ttf"))
        assertFalse(CustomFonts.isSafeName("../evil.ttf"))
        assertFalse(CustomFonts.isSafeName("a/b.ttf"))
        assertNull(CustomFonts.fileNameOf("custom:../evil.ttf"))
        assertNull(CustomFonts.fileNameOf("prompt"))
        assertEquals("my_font.ttf", CustomFonts.fileNameOf("custom:my_font.ttf"))
        assertEquals("My cool font", CustomFonts.displayName("my_cool-font_2.ttf"))
    }

    @Test
    fun sniffRecognisesFontHeaders() {
        val ttf = File(context.cacheDir, "a.bin").apply { writeBytes(byteArrayOf(0, 1, 0, 0, 9, 9)) }
        val otf = File(context.cacheDir, "b.bin").apply { writeBytes("OTTO1234".toByteArray()) }
        val text = File(context.cacheDir, "c.bin").apply { writeText("hello world") }
        assertEquals("ttf", CustomFonts.sniff(ttf))
        assertEquals("otf", CustomFonts.sniff(otf))
        assertNull(CustomFonts.sniff(text))
    }

    @Test
    fun importListResolveAndDelete() = runTest {
        val source = bundledFontFile("Brand New Font.ttf")
        val result = CustomFonts.import(context, Uri.fromFile(source))
        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val font = result.getOrThrow()
        assertEquals("custom:brand_new_font.ttf", font.id)
        assertEquals(listOf(font.id), CustomFonts.list(context).map { it.id })

        val family = FontFamilies.resolve(context, font.id, "prompt")
        assertNotSame(FontFamilies.bundled("prompt"), family)
        assertNotSame(FontFamily.Default, family)

        // A second import with the same name never overwrites the first.
        val second = CustomFonts.import(context, Uri.fromFile(source)).getOrThrow()
        assertEquals("custom:brand_new_font_2.ttf", second.id)

        assertTrue(CustomFonts.delete(context, font.id))
        assertFalse(CustomFonts.delete(context, font.id))
        assertEquals(listOf(second.id), CustomFonts.list(context).map { it.id })
    }

    @Test
    fun rejectsFilesThatAreNotFonts() = runTest {
        val notAFont = File(context.cacheDir, "notes.ttf").apply { writeText("definitely not a font") }
        val result = CustomFonts.import(context, Uri.fromFile(notAFont))
        assertTrue(result.exceptionOrNull() is CustomFonts.ImportException.NotAFont)
        assertTrue(CustomFonts.list(context).isEmpty())
    }
}
