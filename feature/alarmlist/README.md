# feature:alarmlist

The home screen: every alarm with an on/off switch. Android library with Compose (`alarmist.android.library`,
`.compose`, `alarmist.hilt`). Depends on `core:ringing` (`OverlayPermission`), `core:scheduling` (brings data, model) and `core:designsystem`.

Public API (`com.orthohin.alarmist.feature.alarmlist`):
- `AlarmListScreen(onAdd, onEdit: (Long) -> Unit, header, actions)`: `header` is shown above the list (debug tools),
  `actions` in the top bar (the reliability check).

Behaviour:
- Rows: label, time (system 12/24 h), days ("Once", "Every day" or short names in locale order), switch.
  Header: "Next alarm in …" (updated each minute) or "No alarms on". Tap a row to edit.
- "Let alarms come to the front" card (`OverlayHint`) while "display over other apps" is off: Allow (system
  setting, re-read on resume) or "Don't show again" (`AppSettings.overlayHintDismissed`, this phone only).
- Turning a repeating alarm off shows a snackbar "Skip once instead": it turns it back on with
  `skippingNext`. A skip that still applies shows "Skipping <day>" with Undo.
- Turning an alarm on shows "Rings in …"; a one-off alarm whose date has passed gets its date dropped.
- Writes go through `AlarmEditor`; reads from `AlarmRepository.observeAlarms()`.

Tests: `AlarmListUiStateTest` (pure mapping: next ring, active skip, day order, turning on).
