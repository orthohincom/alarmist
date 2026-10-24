# core:sounds

Every sound an alarm can play, and how to open it. Android library (`alarmist.android.library`, `alarmist.hilt`).
Depends on `core:model` (api) and `core:common`.

Public API (`com.orthohin.alarmist.core.sounds`):
- `BundledSounds` (`all`, `default`, `find(id)`): `BundledSound(id, name, category, resource)` in `SoundCategory`
  order. Alarms store only the id (`AlarmSound.Bundled`): never rename or reuse one. `AlarmAudio.ATTRIBUTES`: alarm stream.
- `ImportedSounds` (singleton): "My sounds". `sounds` (call `refresh()`), `import(uri): ImportResult`, `delete`.
  Copies go to device-protected `files/sounds/` (play before the first unlock, survive the original's deletion);
  name kept from the original (made safe and unique, `SoundFileNames`), max 30 MB, must have an audio track.
- `DeviceRingtones.load()`: system alarm sounds and ringtones (`AlarmSound.Custom` URIs); no permission needed.
  They may not open before the first unlock, so ringing falls back to the backup.
- `SoundSources`: `setDataSource(player, sound)` (throws if it can't open) and `title(sound)` (null if gone).
- `SoundPreviewer`: loops one sound on the alarm stream for the picker; `playing`, `play`, `stop`.

Sounds: 14 Ogg files in `res/raw/sound_<id>.ogg`, all synthesised by `scripts/gen-sounds.py` (own work,
GPL-3.0-or-later like the repo; no third-party audio). Regenerate one: `scripts/gen-sounds.py core/sounds/src/main/res/raw <id>`.
A new sound: a generator in the script, the Ogg, a `sounds_<id>` string and a `BundledSounds` entry.

Tests: `BundledSoundsTest`, `SoundFileNamesTest`.
