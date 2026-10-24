# app

Entry point: `AlarmistApp` (`@HiltAndroidApp`) and `MainActivity`: `AlarmistTheme` plus a navigation-compose
`NavHost` with string routes `setup` (first run, until `ReliabilitySetup.isDone()`), `list` (feature:alarmlist),
`reliability`, `settings` (feature:settings) and `edit/{alarmId}` (feature:alarmedit, `ALARM_ID_ARG`, `Alarm.NEW_ID` for new). Opens
`reliability` for `RingingController.ACTION_SHOW_RELIABILITY` (missed-ring notification).
- Theme: `MainActivity` applies `SettingsRepository`'s theme and Material You setting (blank until read, a few ms)
  and matches the system bar icons. `values-night` gives a dark starting window.
- Languages: `generateLocaleConfig` lists the translated locales for Android 13+'s per-app language setting
  (`res/resources.properties`: default `en-US`). How to add a language: CONTRIBUTING.md.
- `src/fdroid/AndroidManifest.xml`: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play restricts it) and
  `guard/PowerMenuGuardService` (accessibility service, binds `PowerMenuGuard`). Its matcher
  `isPowerMenu` is tested in `src/testFdroid`.

- Flavors (dimension `distribution`): `fdroid` and `play`. Code that only one flavor may contain goes in
  `src/fdroid/` or `src/play/`; everything else goes in `src/main/`.
- `DebugTapMission` (src/debug): a "tap N times" mission, so the mission flow can be tried before real missions.
- `DebugTools` (src/debug; an empty one in src/release): "Test alarm" buttons shown above the alarm list, for
  device tests (docs/TESTING.md). Not for users; they have Preview in the edit screen.
- Debug builds use the application ID `com.orthohin.alarmist.debug`, so they can be installed next to a release build.
- Release builds: R8 with resource shrinking, and no dependency-info block (required by F-Droid).

Public API: none. Nothing depends on `app`.
