# feature:alarmedit

Creates and edits one alarm. Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`).
Depends on `core:ringing` (preview; brings scheduling, data, model), `core:sounds`, `mission:api` and `core:designsystem`.

Public API (`com.orthohin.alarmist.feature.alarmedit`):
- `AlarmEditScreen(onClose)`: reads the navigation argument `ALARM_ID_ARG` (Long; `Alarm.NEW_ID` = new alarm).
  `onClose` runs after Save, Delete or the close button.

Behaviour:
- Layout: time wheels or, with the clock-face setting (`AppSettings.timeInput`), Material's `TimePicker` (`TimeDial`). Wheels (`TimeWheel`: hour, minute, AM/PM in 12-hour format; snap, loop, one TalkBack-adjustable
  control each), "Rings in …", days with "Daily" (all seven; off = once; week starts per locale), label, then cards
  (`EditRows.kt`: `SettingsCard`, `ValueRow` = value + chevron, `SwitchRow`, `ChoiceRow` = menu, `HintButton` = (?)
  dialog, `OptionsSheet` = bottom sheet): Missions + wake-up check, Sound (sound, volume with vibrate toggle,
  gradual volume), Snooze and stop (snooze sheet: on/off, length, count; auto-silence), Anti-cheat
  (`AntiCheatCard`: keep screen in front, asking for "display over other apps"; lock volume; power-menu guard only
  if `Optional<PowerMenuGuard>` is present, with a disclosure dialog first; states refresh on resume). Explanations
  sit behind (?) buttons; warnings show under the switch. "Rings in …" updates each minute.
- Rules in `AlarmDraft.kt` (pure): a new alarm starts at the next whole hour; volume 0 only with vibration
 ; Save turns the alarm on, trims the label, drops a one-off date, and keeps a skip only if
  time and days are unchanged.
- Writes go through `AlarmEditor`. Save shows a "Rings in …" toast.
- Preview: `RingingController.preview(draft, now + 10 s)`; nothing is saved.
- Sound picker (`SoundPicker`, full-screen dialog, `SoundPickerViewModel`): category chips jump to sections
  (bundled categories, My sounds, Device sounds); tapping a row selects and previews it, tapping the playing
  selection stops it. Preview stops when the picker closes or the screen stops. Import via the system file
  picker (`audio/*`); deleting the selected import selects the backup. A bundled choice also becomes the backup
  (`withSound`).
- Missions (`MissionsCard`): five slots in run order (icon, name, category colour); an empty slot opens the picker
  sheet (grouped by `MissionCategory`, sorted by name, with descriptions; unavailable ones greyed) → full-screen
  config editor with "Try it" (runs the `Runner` with nothing ringing) and remove. Saving asks for the mission's
  permissions (added either way). Slots hidden when no mission is installed; the wake-up check row stays.

Tests: `AlarmDraftTest` (incl. Daily).
