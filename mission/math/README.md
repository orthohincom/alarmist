# mission:math

Math mission: solve arithmetic problems to dismiss. Android library with Compose (`alarmist.android.library`,
`.compose`, `alarmist.hilt`). Depends on `mission:api` only. Registered as type `"math"` (`MathMissionModule`).

Config (version 1, JSON): `{"difficulty": "normal", "problems": 3}`. `difficulty` is a `MathDifficulty.key`
(`very_easy` 3 + 5, `easy` 23 + 48, `normal` 23 + 48 + 17, `hard` (23 × 7) + 48, `very_hard` (234 × 7) + 486);
`problems` is 1–10. A broken or unknown value falls back to the defaults, never an error.

Public API (`com.orthohin.alarmist.mission.math`, pure Kotlin, tested): `MathDifficulty` (`problem(random)`,
`fromKey`), `MathProblem(text, answer)`, `mathProblems(difficulty, count, seed)`. Answers are never negative.

UI: the editor lists the difficulties as radio rows with an example problem, and a problem-count slider. The runner
shows "Problem n of N", the problem, the answer and its own keypad (no system keyboard over the lock screen; at most
6 digits). In landscape the problem and keypad sit side by side. A wrong answer clears the input and the same problem stays. The seed, progress and input are saved with
the screen, so rotation keeps them.

Tests: `MathProblemsTest`.
