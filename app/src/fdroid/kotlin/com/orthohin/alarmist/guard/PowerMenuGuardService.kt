package com.orthohin.alarmist.guard

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.orthohin.alarmist.core.ringing.RingingController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Closes the power menu while an alarm with `AntiCheatConfig.powerMenuGuard` rings (fdroid flavor only: Play
 * restricts accessibility services). It listens only to window changes, can't read window content, and does
 * nothing while no such alarm rings.
 */
@AndroidEntryPoint
class PowerMenuGuardService : AccessibilityService() {
    @Inject lateinit var controller: RingingController

    private val scope = MainScope()

    @Volatile private var guarding = false
    private var lastClosed = 0L

    override fun onServiceConnected() {
        scope.launch {
            controller.sessions.map { it.current?.alarmId }.distinctUntilChanged().collectLatest { id ->
                guarding = id != null && controller.alarm(id)?.antiCheat?.powerMenuGuard == true
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!guarding || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        // Which window it was, so device testing can tell an OEM's power menu apart.
        val text = event.text.map { it.toString() }
        Log.d(TAG, "Window while ringing: $pkg/$cls $text")
        if (!isPowerMenu(pkg, cls, text, powerMenuTitle())) return
        // One opening sends several events: a second Back would reach the window below.
        val now = SystemClock.elapsedRealtime()
        if (now - lastClosed < DEBOUNCE_MS) return
        lastClosed = now
        Log.i(TAG, "Power menu opened while ringing: closing it")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /** AOSP's power menu window title ("Phone options"), in the phone's language. */
    @SuppressLint("DiscouragedApi") // A framework resource without a public id.
    private fun powerMenuTitle(): String? {
        val res = Resources.getSystem()
        val id = res.getIdentifier("global_actions", "string", "android")
        return if (id == 0) null else res.getString(id)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Alarmist"
        const val DEBOUNCE_MS = 1_000L
    }
}

/**
 * The power menu's window, from System UI: titled like AOSP's ([title], "Phone options"; newer System UI shows
 * it as a plain `SystemUIDialog`), or a class or package named like a power menu (`GlobalActionsDialog`, OEM
 * variants). A heuristic, so real devices decide: unknown OEM menus show up in the debug log while ringing.
 */
internal fun isPowerMenu(pkg: String, cls: String, text: List<String>, title: String?): Boolean {
    val p = pkg.lowercase()
    val c = cls.lowercase()
    val systemUi = "systemui" in p || "globalactions" in p
    return systemUi && (POWER_MENU_HINTS.any { it in c || it in p } || (title != null && title in text))
}

private val POWER_MENU_HINTS = listOf("globalactions", "powermenu", "shutdown", "poweroff")
