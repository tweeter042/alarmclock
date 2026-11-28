package io.github.tweeter042.alarmclock.core.ui.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionRulesTest {

    private fun state(granted: Boolean = false, rationale: Boolean = false, asked: Boolean = false, name: String = "p") =
        PermissionState(permission = name, granted = granted, shouldShowRationale = rationale, requestedBefore = asked)

    @Test
    fun grantedWhenEveryPermissionIs() {
        assertEquals(PermissionStatus.Granted, PermissionRules.status(listOf(state(granted = true), state(granted = true, name = "q"))))
        assertEquals(PermissionStatus.Granted, PermissionRules.status(emptyList()))
    }

    @Test
    fun neverAskedShowsTheSystemDialog() {
        assertEquals(PermissionStatus.Denied, PermissionRules.status(listOf(state())))
    }

    @Test
    fun deniedOnceNeedsARationale() {
        assertEquals(PermissionStatus.RationaleNeeded, PermissionRules.status(listOf(state(rationale = true, asked = true))))
    }

    @Test
    fun askedBeforeWithoutRationaleIsPermanentlyDenied() {
        assertEquals(PermissionStatus.PermanentlyDenied, PermissionRules.status(listOf(state(asked = true))))
    }

    @Test
    fun mixedStates() {
        // One permission can still be asked for: ask.
        assertEquals(PermissionStatus.Denied, PermissionRules.status(listOf(state(asked = true), state(name = "q"))))
        // A rationale for any of them comes first.
        assertEquals(PermissionStatus.RationaleNeeded, PermissionRules.status(listOf(state(asked = true), state(rationale = true, name = "q"))))
        // Granted ones do not count.
        assertEquals(PermissionStatus.PermanentlyDenied, PermissionRules.status(listOf(state(granted = true), state(asked = true, name = "q"))))
    }
}
