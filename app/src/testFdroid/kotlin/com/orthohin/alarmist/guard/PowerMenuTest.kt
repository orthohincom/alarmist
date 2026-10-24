package com.orthohin.alarmist.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMenuTest {
    @Test
    fun `recognises AOSP and OEM power menus`() {
        assertTrue(
            isPowerMenu(
                "com.android.systemui",
                "com.android.systemui.globalactions.GlobalActionsDialogLite",
                emptyList(),
                null
            )
        )
        assertTrue(
            isPowerMenu(
                "com.android.systemui",
                "com.android.systemui.globalactions.GlobalActionsDialog\$ActionsDialog",
                emptyList(),
                null
            )
        )
        val dialog = "com.android.systemui.statusbar.phone.SystemUIDialog"
        assertTrue(isPowerMenu("com.android.systemui", dialog, listOf("Phone options"), "Phone options"))
        assertTrue(isPowerMenu("com.samsung.android.globalactions", "android.app.Dialog", emptyList(), null))
        assertTrue(isPowerMenu("com.android.systemui", "com.oplus.systemui.shutdown.ShutdownDialog", emptyList(), null))
    }

    @Test
    fun `ignores other windows`() {
        assertFalse(
            isPowerMenu(
                "com.android.systemui",
                "com.android.systemui.shade.NotificationShadeWindowView",
                emptyList(),
                null
            )
        )
        assertFalse(isPowerMenu("com.example.app", "com.example.ShutdownActivity", emptyList(), null))
        assertFalse(isPowerMenu("", "", emptyList(), null))
        val dialog = "com.android.systemui.statusbar.phone.SystemUIDialog"
        assertFalse(isPowerMenu("com.android.systemui", dialog, listOf("Internet"), "Phone options"))
        assertFalse(isPowerMenu("com.example.app", "Main", listOf("Phone options"), "Phone options"))
    }
}
