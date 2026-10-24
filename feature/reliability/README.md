# feature:reliability

The reliability check and first-run setup. Android library with Compose (`alarmist.android.library`, `.compose`,
`alarmist.hilt`). Depends on `core:ringing` (brings scheduling, data, model) and `core:designsystem`.

Public API (`com.orthohin.alarmist.feature.reliability`):
- `ReliabilityScreen(setup, onDone)`: settings that can stop alarms, each with a fix button; "when the phone is
  off" note; phone-maker tips (`oemTip`, links to dontkillmyapp.com in the browser); the event log (not in setup).
  `setup = true` is the first-run screen with a Done button (marks setup done, then `onDone`).
- `ReliabilityAction(onClick)`: top-bar icon for the alarm list, with a badge while a required check fails.
- `ReliabilitySetup.isDone()`: the app shows the setup screen at start until it's done.

Checks (`ReliabilityChecks`, re-read on every resume): notifications (in-app request on 13+, settings after two
denials), full-screen intent (34+), exact alarms (31–32 only), background restriction (28+): required;
battery optimisation: recommended; display over other apps (`OverlayPermission`): optional. Battery fix asks directly when the flavor declares
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (fdroid only), else opens the system list.

Event log lines name the alarm by label (else "7:00 alarm"), "Preview" or "A deleted alarm"; missed and late
rings and fallbacks show in the error colour.

Tests: `ReliabilityTextTest` (phone-maker tips, lateness).
