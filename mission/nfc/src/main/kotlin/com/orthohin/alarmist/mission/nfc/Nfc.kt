package com.orthohin.alarmist.mission.nfc

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

internal fun hasNfc(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

internal fun nfcEnabled(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

/** Whether NFC is on, kept up to date while shown (quick settings or the NFC settings screen). */
@Composable
internal fun rememberNfcEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(nfcEnabled(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                enabled = nfcEnabled(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        enabled = nfcEnabled(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

/**
 * Reads tags while the activity is resumed, in reader mode: [onTag] gets each tag's ID on the main thread, once per
 * tap. Other apps don't see the tag meanwhile (a tag holding a link doesn't open the browser). Follows the activity's
 * lifecycle, not a dialog's.
 */
@Composable
internal fun NfcReaderEffect(onTag: (ByteArray) -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val currentOnTag by rememberUpdatedState(onTag)
    // Turning NFC on while shown starts reading again.
    val enabled = rememberNfcEnabled()
    LifecycleResumeEffect(activity, enabled, lifecycleOwner = activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        val main = ContextCompat.getMainExecutor(activity)
        adapter?.enableReaderMode(
            activity,
            { tag -> tag.id.let { id -> main.execute { currentOnTag(id) } } },
            READER_FLAGS,
            null
        )
        onPauseOrDispose { adapter?.disableReaderMode(activity) }
    }
}

// Every tag type; the ID is all we need, so skip reading NDEF content.
private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_NFC_BARCODE or
    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

internal fun isLocked(context: Context): Boolean =
    context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

/** Asks Android to unlock (PIN, fingerprint or a swipe); [onUnlocked] when it's done. */
internal fun requestUnlock(activity: Activity, onUnlocked: () -> Unit) {
    val keyguard = activity.getSystemService(KeyguardManager::class.java) ?: return
    keyguard.requestDismissKeyguard(
        activity,
        object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = onUnlocked()
        }
    )
}
