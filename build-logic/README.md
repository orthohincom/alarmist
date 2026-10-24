# build-logic

Convention plugins, so each module's build file stays about 5 lines. Versions and SDK levels come from
`gradle/libs.versions.toml`. AGP 9 has Kotlin built in, so Android modules don't apply a Kotlin plugin.

| Plugin id | Use for |
|---|---|
| `alarmist.android.application` | `app`: SDK levels, `fdroid`/`play` flavors, release shrinking, F-Droid-safe packaging |
| `alarmist.android.library` | Android library modules (no flavors) |
| `alarmist.android.compose` | Adds Compose (BOM, material3, tooling). Apply after one of the two above |
| `alarmist.jvm.library` | Pure Kotlin modules (`core:model`, domain logic), plus Android lint |
| `alarmist.hilt` | Hilt with KSP |
| `alarmist.room` | Room with KSP, schemas exported to `<module>/schemas` |

Every module gets JUnit 4 for tests and a `quickCheck` task (one variant: build, unit tests, lint), which is
what `scripts/check.sh` runs.
