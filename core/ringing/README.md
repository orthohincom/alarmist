# core:ringing

Makes a fired alarm audible until it is snoozed or dismissed, including after process death and reboots.
Android library (`alarmist.android.library`, `alarmist.hilt`). Depends on `core:scheduling` (api) and `core:sounds`.

Public API (`com.orthohin.alarmist.core.ringing`):
- `RingingController`: `sessions: StateFlow<RingSessions>` (`current` is the one to show), `snooze(id): Boolean`,
  `dismiss(id)` (starts the wake-up check if on), `awake(id)`, `screenVisible(visible)` (anti-cheat). For the ringing screen (feature:ringing), which must enforce missions before `dismiss`.
  `openMissions`, `missionActivity`, `missionCompleted(id, index)` (dismisses after the last), `leaveMissions`.
  `alarm(id)`: the settings an alarm rings with (covers the preview). `preview(alarm, at)`: rings unsaved
  settings as `PREVIEW_ID` via a snooze alarm, kept in memory only.
- Anti-cheat: `OverlayPermission` (`isGranted`, `requestIntent`; "display over other apps",
  needed to bring the screen back), `PowerMenuGuard` (`isEnabled`, `settingsIntent`), injected as
  `Optional<PowerMenuGuard>`: bound only by the app's fdroid flavor.
- `RingingController.ACTION_SHOW_RINGING`: the full-screen intent's action, handled by feature:ringing's
  `RingingActivity` (falls back to the launcher activity if nothing handles it).
- Binds `AlarmRinger` for core:scheduling (`ring`, `snoozeEnded`, `restore`).

Behaviour:
- State: pure `RingSessions` (core:model), persisted on every change to device-protected SharedPreferences
  (`RingSessionCodec`, versioned). One alarm rings at a time; others wait in fire order.
- `RingingService`: sticky FGS (`systemExempted`, directBootAware) that plays `current`. Ticks every 250 ms:
  silent while a mission mutes, full volume (no ramp) when it comes back; auto-silence counts audible time only. Sound on the alarm stream
  at the alarm's volume (stream restored afterwards), -40 dB to full ramp, falls back custom → backup → default.
  Vibration tagged as alarm. Partial wake lock from the broadcast until ringing stops.
- Anti-cheat per alarm: `lockVolume` restores a lowered alarm stream every tick; `relaunchIfLeft` restarts the
  ringing screen (every 2 s at most) while it isn't visible and the screen is on.
- If the FGS can't start: an insistent notification on the fallback channel plays the bundled sound.
- Auto-silence (`AutoSilenceConfig`): the service times each ring; `timedOut` goes quiet via a snooze alarm or,
  after the last ring, ends the session with a "Missed" notification. Vibrate only: `volumePercent` 0.
- Snooze: a separate system alarm via `SnoozeAlarms`, armed before ringing stops; "Snoozed until" notification.
- Wake-up check: timed by the snooze system alarm; a quiet "Wake-up check at …" notification
  (tap: the countdown screen), then "Still awake?" (own channel, alarm audio attributes, countdown, "I'm awake",
  full-screen intent to the ringing screen's card; with "display over other apps" it also comes to the front while
  the phone is in use); unanswered rings again.
- Missed ring (core:scheduling found it): "Didn't ring at …" notification on the missed channel; tapping opens
  `RingingController.ACTION_SHOW_RELIABILITY` (the app's main activity).
- Event log: Snoozed, Dismissed (only if something was ringing or snoozed), AutoSilenced, Unanswered,
  RingingFallback, SoundFallback, Awake, NotAwake.
- Notification actions: Snooze (if allowed), Dismiss (only for alarms without missions, also on the snoozed
  notification; the receiver re-checks).
- Sounds are opened through `core:sounds` (`SoundSources`). The fallback channel plays the default bundled sound;
  its id ends in that sound's resource id, because the channel's sound URI holds it and ids can change between builds.

Tests: `DefaultRingingControllerTest` (fakes), `RingSessionCodecTest` (format has an optional mission field), `VolumeTest`. Device checklist: docs/TESTING.md.
