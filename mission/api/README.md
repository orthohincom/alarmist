# mission:api

The mission plugin contract. Android library with Compose (`alarmist.android.library`, `.compose`,
`alarmist.hilt`). Depends on `core:model` (api). A mission module depends on this, `core:model` and
`core:designsystem` only, and never changes core modules.

Public API (`com.orthohin.alarmist.mission.api`):
- `Mission`: `type` (stored in `MissionSpec.type`, never change), `name`, `icon`, `description` (one line for the
  picker), `category` (`MissionCategory`: Brain, Body, GetUp, Other; the picker's groups), `configVersion`,
  `defaultConfig()` (JSON), `summary(spec, resources)`, `permissions`, `isAvailable(context)` (hardware; the
  editor greys it out), `isReady(context)` (can run now, e.g. permission granted; else the ringing screen offers a skip),
  `@Composable ConfigEditor(spec, onChange)`, `@Composable Runner(spec, host)`.
- `MissionHost`: `onActivity()` (non-touch input keeps the alarm muted; touches count by themselves),
  `onComplete()`.
- `MissionRegistry`: `all`, `get(type)`. Register: `@Binds @IntoMap @StringKey("<type>") abstract fun bind(m: X): Mission`
  in a Hilt module of the mission's module, and add the module to `app`'s dependencies.
- `Mission.newSpec()`: a spec with the default config.
- Editor building blocks (`MissionOptions.kt`), so all missions' options look alike: `RadioOptions(title, options,
  selected, label, onSelect, detail)` (radio list with an optional second line) and `IntSlider(title, value, range, step, onChange)`.
- Counting missions (`Counting.kt`): `hasSensor(context, type)`, `SensorEffect(type, key) { event -> }` (listens while
  shown, main thread, `SENSOR_DELAY_GAME`), `CountingProgress(prompt, icon, count, total, left, hint)` (ring layout).
- Board missions (`Board.kt`): `BoardLayout(status, board)`: status above a square board in portrait, beside it in
  landscape; the board gets the sizing modifier (memory, tap in order, Simon).

Rules on the ringing screen (feature:ringing): missions run in the alarm's order; opening one and
every input mute for 30 s; leaving the screen rings at once; progress is kept until a snooze; an unknown or
unavailable mission can be skipped (never trap the user). Config JSON: Android's `org.json` is enough.
