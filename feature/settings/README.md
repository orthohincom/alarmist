# feature:settings

App settings and backups. Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`).
Depends on `core:scheduling` (brings data, model) and `core:designsystem`.

Public API (`com.orthohin.alarmist.feature.settings`):
- `SettingsScreen(onBack)`; `SettingsAction(onClick)`: the gear icon for the alarm list's top bar.

Behaviour:
- Appearance: theme (System / Light / Dark), Material You colours (Android 12+ only) and how the editor sets the
  time (scroll wheels or clock face), stored in `SettingsRepository` (core:data). The app's `MainActivity` applies them; the ringing screen stays dark.
- Back up: `CreateDocument` (`alarmist-backup-<date>.json`) with every alarm and the settings (`BackupCodec`).
- Restore: `OpenDocument` (JSON, octet-stream or plain text; at most 32 MB). Applies the backup's settings and adds
  its alarms through `AlarmEditor` (`alarmsToRestore`: never changes or deletes an alarm, skips ones already here,
  restores a one-off that can't ring again off). One backup or restore at a time; it finishes off-screen.
- Results and errors show in a snackbar (not a backup, newer version, damaged, too large, no file app).

Tests: `RestorePlanTest` (pure).
