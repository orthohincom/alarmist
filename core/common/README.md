# core:common

Small shared building blocks, pure Kotlin (`alarmist.jvm.library`). Exposes `kotlinx-coroutines-core` as `api`.

Public API (`com.orthohin.alarmist.core.common`):
- `Clock` (`now(): Instant`, `zone(): ZoneId`) and `DeviceClock`. Inject `Clock`; never read system time directly.
  Call `zone()` each time, because the time zone can change while the app runs.
- `Clock.minuteTicks()`: a flow of `now()` at once and at every whole minute, for countdowns in the UI.
- `AppDispatchers` (`io`, `default`) and `DefaultAppDispatchers`.

Test fixtures: `FakeClock` (settable `instant`/`zoneId`, `advanceBy`). Use it with
`testImplementation(testFixtures(project(":core:common")))`.

Result types: none yet. Add one when a module first needs it (`kotlin.Result` covers simple cases).
