# mission:barcode

Barcode/QR mission: scan a registered code (e.g. a product in the bathroom), or any code if none is registered.
Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on `mission:api`,
`mission:camera` (preview, permission, torch) and ZXing core. Registered as type `"barcode"`
(`BarcodeMissionModule`).

Config (version 1, JSON): `{"code": "…", "format": "QR_CODE", "name": "Bathroom shelf"}`. No `code` = any code
(also what a broken config gives, so the mission stays solvable). `format` is a ZXing `BarcodeFormat` name;
`name` is optional, up to 40 characters.

Permissions: CAMERA (`mission:camera`'s manifest, `permissions`). Camera hardware is `required="false"`.
`isAvailable` = a camera; `isReady` = a camera and the permission (else the ringing screen offers a skip).

Public API (`com.orthohin.alarmist.mission.barcode`, pure Kotlin, tested): `Scan(text, format)`,
`BarcodeDecoder.decode(luma, w, h)` (common 1D/2D formats, also inverted; tries the frame turned for 1D codes),
`codesMatch` (UPC-A = EAN-13 with a leading 0), `ScanConfirmer` (same code twice in a row).

UI: `mission:camera`'s `CameraPermissionGate`, `CameraView` (decoding in `analyze`) and `CameraDialog`. The editor
registers a code (needs two agreeing reads) and names it; the runner shows the name, "That's not the registered
code" for others, and calls `MissionHost.onActivity()` while the image changes (the phone is carried).

Tests: `BarcodeTest` (codes generated with ZXing's writers, decoded from synthetic frames).
