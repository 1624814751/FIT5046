# Member C Sensor Dataset Playback and Live Workout Design

Approved by the user on 14 October 2026. Continue on `member-c-dev`, preserve the existing Room workout work and the user's staged `.idea` changes, and stop before integrating Member A's authentication, recommendation store or context-decision implementation. Do not push or merge automatically.

## Goal and boundary

Replace the fixed Live Activity demonstration with a transparent playback of a small, attributed subset of a real public inertial-sensor dataset. The app will expose the existing `SensorRepository` contract, derive a non-medical movement band from one-second signal windows, show the latest values and safety prompt on Live Activity, operate a real monotonic session timer with pause/resume, and save the finished simulated session through the existing Room-backed `WorkoutRepository`.

This milestone does not connect Firebase authentication, A's `RecommendationStore`, A's `ContextDecision`, weather, or D's statistics implementation. Until A's branch is integrated, the existing `local-demo` user and the existing A2 recommendation route remain explicitly labelled adapters. The next integration step will require A's real `UserAccount.userId`, stored recommendation ID and recommendation payload; work stops before that step.

## Dataset and attribution

Use the UCI Machine Learning Repository dataset *Smartphone-Based Recognition of Human Activities and Postural Transitions* by Reyes-Ortiz, Anguita, Oneto and Parra (2015), DOI `10.24432/C54G7M`, licensed CC BY 4.0. The source records three-axis acceleration and angular velocity from a waist-mounted Samsung Galaxy S II at 50 Hz. Acceleration is expressed in `g`; gyroscope values are expressed in `rad/s`.

Commit only a small deterministic teaching subset under `app/src/main/assets/sensor/`, not the 75.9 MB archive. A provenance file will record the dataset title, DOI, licence, original raw filenames, subject/experiment identifiers, source sample ranges and the extraction procedure. The app asset will use a combined CSV schema:

```text
elapsed_ms,ax_g,ay_g,az_g,gx_rad_s,gy_rad_s,gz_rad_s,activity_label
```

Each row must be copied from paired raw accelerometer and gyroscope rows at the same source index; timestamps advance by 20 ms. Include labelled samples covering a static state, walking and a more vigorous dynamic state so the demonstration traverses more than one movement band. No random or invented sensor rows may be presented as UCI data.

## Components and data flow

Create a focused `sensor` package:

- `CsvSensorRepository` implements the unchanged shared `SensorRepository`. It opens a known asset for a known dataset ID, validates the header and every row, and returns a cold `Flow<MotionContext>`. Collection starts playback and cancellation stops it. Unknown IDs, malformed rows, non-finite numbers and misordered timestamps produce a clear error instead of silently emitting fake values.
- `MotionWindowClassifier` consumes up to 50 consecutive readings. It computes acceleration deviation from gravity, `abs(sqrt(ax² + ay² + az²) - 1.0)`, and gyroscope magnitude, `sqrt(gx² + gy² + gz²)`. The mean values over the current one-second window determine `LOW`, `MODERATE` or `HIGH`. Threshold constants are named, documented in the code and covered by boundary tests. They are demonstration rules, not a trained clinical or activity-recognition model.
- `LiveWorkoutViewModel` owns playback, pause/resume, elapsed session time, the latest `MotionContext`, the current safety prompt and terminal playback errors. It receives a `SensorRepository`, dataset ID, monotonic clock/ticker abstraction and saved-state inputs so host tests do not wait in real time. It does not access navigation, Compose or Room directly.
- `LiveActivityScreen` renders state and sends user actions through callbacks. It removes the fake heart-rate claim, shows dataset source, activity label, current movement band and the latest three-axis readings with units. It distinguishes loading, active, paused, completed and playback-error states.
- The navigation layer injects the sensor repository and observes `LiveWorkoutViewModel`. Finishing creates one Room `WorkoutRecord` only after the user confirms. It uses the measured session duration rounded up to at least one minute, the session start instant, and `DataSource.DEMO` because playback is a public dataset simulation rather than the phone user's live sensor.

The resulting flow is:

```text
UCI CSV asset
  -> CsvSensorRepository
  -> Flow<MotionContext>
  -> LiveWorkoutViewModel
  -> LiveActivityScreen
  -> confirmed finish
  -> WorkoutRecordsViewModel
  -> RoomWorkoutRepository
  -> History and Statistics
```

## Playback, timer and pause rules

The CSV is replayed at its documented 50 Hz cadence. Playback loops through the selected teaching subset while the workout remains active, but `sampleIndex` and session elapsed time remain monotonic across loops. The displayed source activity label comes from the current dataset segment. Pausing suspends both the dataset playback and the workout timer; resuming continues without counting paused time. Leaving the Live route cancels collection and the timer.

The planned duration remains contextual guidance, not a reason to fabricate elapsed minutes. The progress indicator clamps actual active elapsed time against the planned duration. The finish action is available after playback has produced valid data; repeated finish taps cannot create duplicate Room records. A sensor parsing/playback error leaves the Live page open with Retry and Finish without sensor data; the error never substitutes generated values.

## Movement and safety rules

Classification operates on sensor magnitude, not solely on the UCI activity label. The label is display/provenance information. Initial thresholds will be calibrated against the committed subset and locked by tests; the intended ordering is static data as `LOW`, ordinary walking as `MODERATE`, and the selected vigorous segment as `HIGH`.

Only `HIGH` emits this non-medical prompt:

> Your movement is high. Consider taking a short break.

`LOW`, `MODERATE` and `UNKNOWN` emit no warning. The UI must not infer heart rate, injury, diagnosis, fall risk or medical safety from the dataset. It will state that the readings are replayed research data and are not measurements from the current user.

## Error handling and ownership

Asset parsing reports dataset ID and row number without logging credentials or personal data. Coroutine cancellation is rethrown. Retry restarts a new cold-flow playback from the beginning. A malformed or unavailable dataset cannot crash navigation or create a workout automatically.

Member C owns the new sensor package, Live view model, Live screen changes and their tests. Changes to `MoveSmartApp.kt` are limited to dependency injection and the existing Live route. Shared models/contracts remain unchanged. A's auth/weather/recommendation files and D's reminder/statistics logic are not edited.

## Test and acceptance strategy

Use strict red-green-refactor development:

1. Repository/parser tests first: valid rows and units, cold-flow restart, 20 ms ordering, unknown dataset, malformed headers/rows, non-finite values and cancellation.
2. Classifier tests first: empty/short windows, static `LOW`, walking `MODERATE`, vigorous `HIGH`, exact threshold boundaries and reset between dataset segments.
3. Live view-model tests first: loading to active, timer progression, pause/resume, playback loop, high-band prompt, retry after error, cancellation and single-finish gating.
4. Compose tests first: no fake heart rate, source disclosure, values/units, paused state, high prompt, error/Retry and finish confirmation.
5. Integration regression: finishing once saves a `DEMO` record with start time and measured duration, then the same Room flow updates History and Statistics.

Before claiming completion, run all host tests, build the debug and Android-test APKs and run Android lint. If an emulator is available, run the relevant instrumentation tests and manually exercise pause/resume, high-band warning, finish/save and process recreation. If no device is connected, report that limitation explicitly rather than treating host tests as device evidence.

Create `docs/A4_MEMBER_C_SENSOR_HANDOFF.md` as the durable English handoff for Members A and D. It will list the files Member C owns, the dataset attribution, the exact public interfaces, how A supplies authenticated identity and a stored recommendation, how D consumes the same persisted workout stream, the tested commands and results, any emulator limitation, and the safest merge order. Commit and push this Markdown handoff with the implementation on `member-c-dev`; do not maintain a separate Word copy that can drift from the code.

## Stop condition requiring Member A

After this isolated sensor/Live milestone passes its tests, stop before replacing the demo route with A's production flow. The blocked integration requires all three of the following from A's merged code:

1. `AuthRepository.currentUser` to supply the real immutable `UserAccount.userId` used by Room.
2. `RecommendationStore.observe(id)` to retrieve the selected recommendation by navigation-safe ID.
3. A's recommendation/context orchestration to combine the current `MotionContext` with preferences, planning input and weather and to decide whether a changed movement band should trigger re-evaluation.

The handoff at that point must state the exact interfaces required and avoid copying A's implementation into Member C's branch.
