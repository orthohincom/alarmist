# mission:typing

Typing mission: type out a phrase to dismiss. Android library with Compose (`alarmist.android.library`,
`.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as type `"typing"` (`TypingMissionModule`).

Config (version 1, JSON): `{"phrases": 1, "custom": []}`. `phrases` is 1–5; `custom` holds the user's own
phrases (empty = the built-in `R.array.typing_phrases`, 20 short wake-up sentences, translatable).
A broken value falls back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.typing`, pure Kotlin, tested): `Typing.clean`, `matched`, `hasError`,
`isDone`, `pick(pool, count, random)`. Case, curly vs straight quotes, leading and repeated spaces don't count;
spelling and punctuation do.

UI: the editor has a phrase-count slider and a "Your own phrases" field (one per line). The runner shows the phrase,
colours the correct part and marks the first wrong character, and opens the system keyboard (autocorrect off).
A phrase is accepted as soon as it matches; there is no submit button. Every text change calls
`MissionHost.onActivity()`: keyboard input lands in the keyboard's window, so it never counts as a touch.
The seed, progress and input are saved with the screen.

Tests: `TypingTest`.
