# mission:photo

Photo mission: find the view of a registered photo (e.g. the bathroom sink), or take any photo if none is
registered. Android library with Compose (`alarmist.android.library`, `.compose`, `alarmist.hilt`). Depends on
`mission:api` and `mission:camera`; no image library (own ORB + RANSAC). Registered as type `"photo"`
(`PhotoMissionModule`).

Config (version 1, JSON): `{"photo": "<Base64 JPEG, 480 px long side>", "name": "…", "level": "normal"}`.
No `photo` (or one that won't decode) = any photo. `level`: easy / normal / strict (scores 15 / 25 / 45).

Permissions: CAMERA (from `mission:camera`). `isAvailable` = a camera; `isReady` = a camera and the permission.

Public API (`com.orthohin.alarmist.mission.photo`, pure Kotlin, tested): `GrayImage` (`fitWithin`, `scaled`,
`rotated`, `turned`, `blurred`), `Orb.detect(image): Features`, `PhotoMatcher.score(reference, query)` (inliers
of one homography), `PhotoMatcher.distinctiveness(photo)` (registering needs ≥ `MIN_DISTINCTIVENESS`),
`PhotoMatcher.Level`, `WORKING_SIZE` (480).

UI: the editor registers a photo (shutter; rejected with "Too little detail" for bare walls and tiles), shows it,
names it and picks the level. The runner shows the photo as a thumbnail over the preview and a match bar; two frames
in a row at the level's score solve it. "Too dark" below mean brightness 35. Moving the phone keeps the alarm muted.

Tests: `PhotoMatcherTest` (synthetic scenes from `Scenes`; set `PHOTO_SAMPLES=<dir of photos>` to also run on real
images; stats with `-i | grep PHOTOSTATS`).
