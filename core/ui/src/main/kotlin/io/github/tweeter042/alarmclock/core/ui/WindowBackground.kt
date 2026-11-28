package io.github.tweeter042.alarmclock.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.drawable.toDrawable
import io.github.tweeter042.alarmclock.core.designsystem.theme.AppTheme

/**
 * Paints the activity window in the current theme's background colour. The window background
 * shows during activity transitions, predictive back and while the keyboard resizes the window;
 * matching it to the theme avoids flashes of the static manifest colour (e.g. light grey behind
 * the AMOLED theme). Called by [AlarmClockAppRoot].
 */
@Composable
fun ThemedWindowBackground() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val color = AppTheme.colors.background.toArgb()
    DisposableEffect(view, color) {
        view.context.findActivity()?.window?.setBackgroundDrawable(color.toDrawable())
        onDispose { }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
