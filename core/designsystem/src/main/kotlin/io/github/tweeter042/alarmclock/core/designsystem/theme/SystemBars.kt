package io.github.tweeter042.alarmclock.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Makes the (transparent, edge-to-edge) status and navigation bar icons readable on the current
 * theme: dark icons on light themes, light icons on dark ones. Call it once at the root of each
 * activity, inside [AlarmClockTheme] (not inside nested previews).
 */
@Composable
fun ThemedSystemBars() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val lightBars = !AppTheme.isDark
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
