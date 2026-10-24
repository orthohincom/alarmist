# Contributing

Thanks for taking a look. Any help is welcome: testing on your phone, bug reports, ideas, translations, code.
Nothing here is set in stone; if something seems wrong or clumsy, say so.

## Reporting a bug
Open an issue with:
- phone maker, model and Android version, and whether battery optimisation is on for Alarmist;
- what you did, what you expected, what happened;
- if an alarm didn't ring or rang late: the event log at the bottom of the reliability check (shield icon on
  the alarm list) and the phone maker's battery settings;
- logs if you can: `adb logcat -s Alarmist` (debug builds log more, e.g. every counted step).

## Building and checking
JDK 17 and an Android SDK. Then:

```bash
scripts/check.sh            # build, unit tests, lint and format check; prints errors only, "OK" when green
scripts/check.sh :core:model  # one module
scripts/check.sh --fix      # apply ktlint formatting first
./gradlew :app:installFdroidDebug
```

Debug builds install next to a release (`com.orthohin.alarmist.debug`) and add "Test alarm" buttons to the
alarm list. Please keep `scripts/check.sh` green in pull requests; CI runs it too.

## Ground rules
- **Alarm reliability comes first**, then app quality. When unsure, an extra ring beats a missed one, and the
  user must never be trapped in ringing (a mission that can't run can be skipped).
- **F-Droid compatible:** no Google Play Services, no proprietary libraries, no tracking or ads, and no INTERNET
  permission. Features Google Play restricts (like the accessibility service) go in the `fdroid` flavor only.
- **Logic in pure Kotlin** (`core/model`, `core/scheduling`, or a mission's own classes), with unit tests.
  Android code stays thin.
- **Time comes from the injected `Clock`**, never `System.currentTimeMillis()`, so tests control it.
- **All user-visible text in string resources**, so it can be translated.
- **Each module has a `README.md`** (purpose, public API). Update it when the API changes.
- Stored formats are schema: the Room encoding (`AlarmMapper`), the backup JSON and mission configs. Change them
  only with a migration or a version bump.

## Adding a mission
Missions are plugins; a new one never changes core modules. Look at `mission/math` or `mission/shake`.
1. Create `mission/<name>` with `alarmist.android.library`, `alarmist.android.compose` and `alarmist.hilt`, and
   depend on `:mission:api` only (camera missions also on `:mission:camera`). Add it to `settings.gradle.kts`
   and to `app`'s dependencies.
2. Implement `Mission` (`mission/api`): a stable `type` (stored with alarms, never change it), name, icon,
   one-line description, category, `defaultConfig()` as JSON, `ConfigEditor` (the options) and `Runner` (the
   mission itself). Call `MissionHost.onActivity()` for non-touch progress (keeps the alarm muted) and
   `onComplete()` when solved.
3. Make the config parser forgiving: a broken config falls back to defaults, so an alarm always has a solvable
   mission. Declare runtime permissions in `permissions` and report hardware in `isAvailable` / `isReady`.
4. Register it: `@Binds @IntoMap @StringKey("<type>") abstract fun bind(m: YourMission): Mission` in a Hilt module.
5. Put the counting or checking logic in a plain Kotlin class with unit tests. Use "Try it" in the editor and a
   Preview alarm to try it for real.

## Translations
All text is in `res/values/strings.xml` of each module. A translation is a `values-<language>/strings.xml` next to
it. Strings marked `translatable="false"` stay as they are.

## Step and sensor data
The steps mission is tuned on real recordings. Debug builds save every run of the mission to
`/data/user_de/0/com.orthohin.alarmist.debug/files/steps-<n>.csv`; pull one with
`adb exec-out run-as com.orthohin.alarmist.debug cat <that path> > walk.csv`. Recordings from other phones
(walking in the hand, in a pocket, shaking) help a lot: gzip them into `mission/steps/src/test/resources/gait/`
as `walk-*.csv.gz` or `shake-*.csv.gz` and `GaitReplayTest` replays them.

## Pull requests
Small and focused is easiest to review. Describe what you tested and on which phone. By contributing you agree
that your work is licensed under GPL-3.0-or-later.
