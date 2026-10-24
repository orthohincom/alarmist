# feature:ringing

The ringing screen. Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`).
Depends on `core:ringing` and `mission:api`.

Public API: none. `RingingActivity` is reached only through `RingingController.ACTION_SHOW_RINGING` (the
ringing notification's full-screen intent), so `app` doesn't reference it.

Behaviour:
- Shows over the lock screen and turns the screen on (directBootAware, own task, out of recents, keeps the
  screen on while ringing or asking). It doesn't unlock the phone. Back does nothing (except while waiting for the check).
- Shows `RingingController.sessions.current` (settings from `RingingController.alarm`, so previews work): time it went off, label, Snooze (with snoozes left) and Dismiss.
  Closes itself when nothing rings (snoozed, dismissed, auto-silenced). Switches if another alarm takes over.
- Wake-up check (when nothing rings, `RingSessions.checking`): after dismissing, "Stay awake and get ready" with a
  progress bar and "Check in 04:12" (no skip; Close leaves, the screen may time out); then "Still awake?", a card
  with the seconds left and "Yes, I'm up!" (`RingingController.awake`). Opened by the check notifications.
- Alarms with missions: Dismiss opens the mission screen ("Mission 1 of 3", progress bar, "Back to the alarm"),
  which runs each mission's `Runner` in order (from `MissionRegistry`). Opening it mutes; leaving it (Back, home,
  screen off) rings at once; any touch keeps it muted. Unknown missions and ones not `isReady` (no hardware or permission) offer "Skip this mission".
- Reports `RingingController.screenVisible` (onStart/onStop, not rotations) for the anti-cheat relaunch; with
  `lockVolume` the volume keys do nothing.
- Actions run `NonCancellable`, because the screen closes as soon as the state changes.

Tests: `RingingUiStateTest` (pure mapping from session + alarm to UI state, mission progress).
