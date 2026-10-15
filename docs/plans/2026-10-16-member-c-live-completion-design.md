# Member C Live Workout Completion Design

## Scope

Complete the remaining Member C workout flow without changing account copy or recommendation ownership assigned to Members A and D.

## Behaviour

- Both demo users and authenticated users can open the existing Live Activity screen.
- The timer is driven by a monotonic clock and remains independent of CSV playback availability.
- Pause and resume affect active elapsed time and dataset playback together.
- Finishing saves exactly one record for the current workout owner. Authenticated sessions use the Firebase UID; demo sessions use `local-demo`.
- UCI HAPT playback remains clearly labelled as dataset simulation and all resulting records keep `DataSource.DEMO`.
- The movement card should not remain `UNKNOWN` for a full one-second window when the dataset label changes. It may show a provisional band derived from the public dataset label until the measured window result is available.
- History account wording remains unchanged because Member A owns that adjustment.

## Boundaries

- Do not alter Firebase authentication, weather, WorkManager, recommendations, reminder scheduling, statistics ownership, or account labels.
- Do not claim that UCI HAPT playback is live phone-sensor capture.
- Preserve the existing Room schema and repository interface unless a failing test proves a change is necessary.

## Verification

- Unit tests cover timer independence, pause/resume, provisional movement classification, and summary generation.
- Compose/Room regression tests cover authenticated UID isolation, one-save-only behaviour, Search, and workout CRUD.
- Run JVM tests, debug build, Android-test APK build, and lint before hand-off.
