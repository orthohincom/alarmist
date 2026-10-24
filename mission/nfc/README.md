# mission:nfc

NFC tag mission: scan a registered tag (e.g. a sticker on the bathroom mirror), or any tag if none is registered.
Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api`.
Registered as type `"nfc"` (`NfcMissionModule`).

Config (version 1, JSON): `{"tag": "04A1B2C3D4E5F6", "name": "Bathroom mirror"}`. No `tag` = any tag (also what a
broken config gives). `tag` is the tag's ID (UID) in upper-case hex; `name` is optional, up to 40 characters.

Permissions: NFC (normal, granted at install). NFC hardware is `required="false"`. `isAvailable` = NFC hardware;
`isReady` = hardware and NFC switched on (else the ringing screen offers a skip).

Public API (`com.orthohin.alarmist.mission.nfc`, pure Kotlin, tested): `tagId(bytes)`, `isRandomId(bytes)` (empty,
or an NFC-A random ID: 4 bytes starting 0x08), `TagConfirmer` (two separate taps with the same ID), `MotionGate`
(accelerometer: the phone is carried, at most once a second).

UI: reader mode (`NfcReaderEffect`, all tag types, NDEF skipped, follows the activity's lifecycle) while the editor's
register dialog or the runner is shown, so other apps don't react to the tag. The editor warns when NFC is off (NFC
settings button) and registers with two taps, refusing tags whose ID changes. The runner: "That's not the registered
tag" for others; carrying the phone keeps the alarm muted; on the lock screen, an Unlock button
(`requestDismissKeyguard`), since some phones read tags only when unlocked.
