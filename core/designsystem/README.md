# core:designsystem

Theme and small shared Compose helpers for the feature modules. Android library with Compose
(`alarmist.android.library`, `.compose`). No dependencies on other modules.

Public API (`com.orthohin.alarmist.core.designsystem`):
- `AlarmistTheme(darkTheme, dynamicColor)`: Material 3; Material You colours on Android 12+ if `dynamicColor`
  (`supportsDynamicColor`). The app passes the user's settings; the ringing screen is always dark.
- `rememberTimeFormatter()`: a `DateTimeFormatter` for times of day in the system's 12/24-hour setting;
  `bestFormatter(locale, skeleton)` for others (e.g. "EEEMMMd"). `weekOrder(locale)`: days from the locale's first.
- `Countdown.between(from, to)`: days/hours/minutes left, rounded up to the minute (pure, tested).
- On `Resources` (usable outside composition, e.g. for a toast): `countdownText` ("7 hours 12 minutes"),
  `ringsInText(from, to)` ("Rings in …"), `secondsText` ("30 seconds", "2 minutes").

String resources use the `ds_` prefix.

Tests: `CountdownTest`.
