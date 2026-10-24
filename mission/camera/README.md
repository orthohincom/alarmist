# mission:camera

Shared camera UI for camera missions (barcode, photo), so missions without a camera don't carry CameraX.
Android library with Compose (`alarmist.android.library`, `.compose`). Depends on CameraX (`camera-camera2`,
`-lifecycle`, `-view`).

Manifest: CAMERA permission; camera hardware `required="false"` (else stores hide the app on phones without one).

Public API (`com.orthohin.alarmist.mission.camera`):
- `hasCamera(context)`, `hasCameraPermission(context)` (for `isAvailable` / `isReady`).
- `CameraPermissionGate(rationale) { … }`: asks once, then a button, then app settings.
- `CameraView(analyze, onResult, modifier, onMotion, analysisSize = 1280×720, overlay)`: back camera (else front),
  `analyze(CameraFrame)` on one background thread (latest frame only), non-null results to `onResult` on the main
  thread; `onMotion` at most once a second while the image changes; torch button if there's a flash.
- `CameraFrame`: `luma` (Y plane, tight), `width`, `height`, `rotationDegrees`, `toBitmap()` (colour, upright);
  valid only during `analyze`.
- `CameraDialog(title, onDismiss) { … }`: full-screen dialog with a close button, for registering.
- Pure Kotlin, tested (`LumaTest`): `lumaFromPlane`, `rotate90`, `MotionDetector`.
