package com.orthohin.alarmist.feature.ringing

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.orthohin.alarmist.core.ringing.RingingController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The ringing screen, shown over the lock screen. It follows `RingingController.sessions.current` (or, when
 * nothing rings, the wake-up check: its countdown, then "Still awake?") and closes itself when there is neither. It doesn't unlock the phone: like any alarm clock, it can be handled while locked.
 */
@AndroidEntryPoint
class RingingActivity : ComponentActivity() {
    private val viewModel: RingingViewModel by viewModels()

    @Inject lateinit var controller: RingingController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        // Back must not walk away from a ringing alarm or the "Still awake?" question.
        val blockBack = onBackPressedDispatcher.addCallback(this) {}
        // Not tied to the started state: it also closes if the alarm ends while the screen is off.
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                if (state == RingingUiState.Idle) finish()
                val waiting = state is RingingUiState.CheckWaiting
                blockBack.isEnabled = !waiting
                // The "Stay awake" countdown lets the screen time out like any app; ringing and asking keep it on.
                if (waiting) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            MaterialTheme(colorScheme = darkColorScheme()) {
                RingingScreen(state, viewModel, onClose = ::finish)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.screenVisible(true)
    }

    override fun onStop() {
        super.onStop()
        // Anti-cheat (relaunchIfLeft) brings it back; a rotation isn't leaving.
        if (!isChangingConfigurations) controller.screenVisible(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        blocksVolumeKey(keyCode) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        blocksVolumeKey(keyCode) || super.onKeyUp(keyCode, event)

    /** Anti-cheat (lockVolume): the volume keys do nothing here; the service restores the volume anyway. */
    private fun blocksVolumeKey(keyCode: Int): Boolean = keyCode in VOLUME_KEYS &&
        (viewModel.state.value as? RingingUiState.Ringing)?.lockVolume == true

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private companion object {
        val VOLUME_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
    }
}
