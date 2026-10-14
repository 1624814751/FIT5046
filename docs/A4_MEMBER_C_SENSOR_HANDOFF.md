# Member C — Sensor Playback and Live Workout Handoff

## 1. Status and deliberate stop point

Member C's isolated sensor milestone is implemented on `member-c-dev` and is ready for team review. It includes an attributed UCI HAPT dataset subset, strict CSV playback, motion-band classification, a real active-time session timer, pause/resume, retry/error state, a non-medical high-movement prompt, and a Compose Live Activity component for the new state.

The new component is **not yet connected to the production `Routes.LIVE` destination**. `MoveSmartApp.kt` still calls the legacy A2 Live Activity overload and still scopes workout records to `local-demo`. This is intentional: that file is the current integration hotspot for Member A's authentication/recommendation flow, Member C's workout/sensor flow, and Member D's navigation/statistics flow. It should be changed once by the team rather than independently on three branches.

Do not claim that public-dataset playback is the phone user's live sensor measurement. Every emitted sample and saved playback session must remain `DataSource.DEMO`.

## 2. Member C deliverables

### Dataset and reproducibility

- `app/src/main/assets/sensor/uci_hapt_exp01_user01_subset.csv`
- `app/src/main/assets/sensor/README.md`
- `tools/extract_uci_hapt_subset.ps1`

The committed asset contains 150 paired accelerometer/gyroscope rows: 50 standing, 50 walking, and 50 walking-downstairs rows from Experiment 1, User 1. Samples are spaced at 20 ms (50 Hz). Acceleration is expressed in `g`; angular velocity is expressed in `rad/s`.

Dataset: *Human Activity Recognition Using Smartphones* / UCI HAPT data, DOI `10.24432/C54G7M`, licensed CC BY 4.0. The asset README records the original files, exact source ranges, attribution and extraction command. The extraction script reproduces the checked-in subset.

### Sensor implementation

- `sensor/SensorDataset.kt`
- `sensor/CsvSensorRepository.kt`
- `sensor/MotionWindowClassifier.kt`
- `sensor/LiveWorkoutViewModel.kt`

### Live UI and tests

- `ui/screens/WorkoutScreens.kt` — new state-driven `LiveActivityScreen` overload
- `CsvSensorRepositoryTest.kt`
- `MotionWindowClassifierTest.kt`
- `LiveWorkoutViewModelTest.kt`
- `LiveActivityScreenTest.kt`

The older `LiveActivityScreen(activity, intensity, plannedMinutes, ...)` overload remains temporarily so the shared navigation branch still compiles before integration. D should remove it after the production route uses the state-driven overload and the older navigation/UI tests have been updated.

## 3. Public contracts for integration

### Start sensor playback

```kotlin
val sensorRepository: SensorRepository = CsvSensorRepository(
    AndroidSensorAssetReader(applicationContext)
)

val datasetId = SensorDatasets.UCI_HAPT_SUBSET_ID
val motionFlow: Flow<MotionContext> = sensorRepository.playback(datasetId)
```

`SensorRepository.playback` is a cold flow. Collection starts playback; cancellation stops it. Each collection restarts from the first source row. The 150-row asset loops while the collector is active, while `sampleIndex` and `elapsedMilliseconds` remain globally monotonic for that collection.

The parser rejects unknown dataset IDs, a wrong header, empty data, blank/malformed rows, non-finite values and timestamps that do not advance in 20 ms steps. It never substitutes generated sensor values after an error.

### Create the Live state owner

```kotlin
val liveWorkout = LiveWorkoutViewModel(
    repository = sensorRepository,
    datasetId = SensorDatasets.UCI_HAPT_SUBSET_ID
)
```

Observe `liveWorkout.state` with `collectAsStateWithLifecycle()`. Use:

- `togglePause()` to pause/resume both active-time accumulation and upstream playback processing;
- `retryPlayback()` after a terminal CSV/playback error;
- `summary()` only when the user confirms Finish.

`summary()` returns active elapsed milliseconds, an original session start `Instant`, and duration rounded up to a minimum of one minute. The ViewModel does not write Room data or navigate.

### Render the new Live screen

```kotlin
LiveActivityScreen(
    activity = recommendation.activityName,
    plannedMinutes = recommendation.minutes,
    state = liveState,
    isSaving = saving,
    saveError = saveError,
    onTogglePause = liveWorkout::togglePause,
    onRetryPlayback = liveWorkout::retryPlayback,
    onFinish = { /* save once, then navigate after success */ }
)
```

The screen shows the measured active time, UCI activity label, movement band, acceleration, angular velocity, dataset disclosure, playback error/retry state and a non-medical safety disclaimer. It does not interpret the values as heart rate.

### Save through Member C's existing Room flow

After confirmation, create exactly one shared `WorkoutRecord` from `summary()`:

```kotlin
WorkoutRecord(
    id = 0,
    userId = resolvedUserId,
    title = recommendation.activityName,
    minutes = summary.minutes,
    date = summary.startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
    startedAt = summary.startedAt,
    source = DataSource.DEMO
)
```

Call the existing `WorkoutRecordsViewModel.save(record)` and navigate to History only after `Result.success`. Disable repeated Finish/Save actions while the write is pending so one session cannot create duplicate rows.

## 4. Required Member A decisions and changes

Member A should confirm the production identity and recommendation inputs before D edits the route:

1. Use `authViewModel.currentUser.value?.userId` for authenticated workout ownership. Do not silently write authenticated records under `local-demo`.
2. Pass the selected stored `Recommendation.id` into the details/Live navigation flow, then load it through `RecommendationViewModel.loadRecommendation(id)` or provide the already validated `Recommendation` state. Do not reconstruct a production recommendation from the old `PrototypeState` strings.
3. Decide whether UCI playback is available only in explicit demo mode or may be demonstrated by a signed-in user. In either case, its saved source remains `DataSource.DEMO`.
4. If sensor context should influence a later recommendation refresh, collect `MotionContext` in A's lifecycle-aware recommendation layer and pass it to `generateRecommendation(..., motion)`. Do not copy or reimplement C's CSV player.

## 5. Required Member D navigation/statistics changes

Member D owns the single integration edit in `MoveSmartApp.kt`:

1. Add an injectable `SensorRepository` with the Android asset implementation as the production default.
2. Create/obtain one `LiveWorkoutViewModel` for the active Live destination and collect its state lifecycle-safely.
3. Call the new state-driven `LiveActivityScreen` overload.
4. On confirmed Finish, use `LiveWorkoutViewModel.summary()` and save once through the existing `WorkoutRecordsViewModel`.
5. Scope `WorkoutRecordsViewModel` to the resolved account/demo user ID and ensure an account change creates the correct user-scoped model rather than exposing the previous user's flow.
6. Keep Statistics reading the same `WorkoutRepository.observe(userId)` stream. No separate statistics database or copied list is required.
7. After integration, remove the legacy Live overload and replace old screenshot/navigation assertions that expect the A2 fake timer or `112 bpm`.

## 6. Verification completed on 14 October 2026

Environment: Windows PowerShell, Android Studio JBR, Gradle wrapper 9.5.0.

```text
:app:testDebugUnitTest
BUILD SUCCESSFUL
24 test suites; 209 tests; 0 failures; 0 errors; 0 skipped

:app:lintDebug :app:assembleDebug
BUILD SUCCESSFUL
Lint: 0 errors, 24 existing warnings, 2 hints
Debug APK: app/build/outputs/apk/debug/app-debug.apk
```

The first offline lint attempt stopped before analysis because `androidx.concurrent:concurrent-futures-ktx:1.1.0` was not cached. Re-running online downloaded the dependency and completed successfully. The lint XML contains no finding located in the new Member C sensor/ViewModel/Live-screen files.

These are host/build checks. Emulator/device navigation, real Firebase sign-in, complete recommendation-to-Live navigation, session saving, account switching, notification interaction and final Statistics display still require manual team acceptance after the integration edit.

`app/google-services.json` is present only in the developer's local checkout and is excluded through `.git/info/exclude`; it is not part of Member C's commits.

## 7. Safe review and merge order

1. A and D review the public contracts and the production identity/recommendation decisions above.
2. Review Member C commits on `member-c-dev`; preserve commit authorship and do not squash unless the teaching team explicitly requires it.
3. D makes the single navigation integration change on the agreed integration branch, with A and C reviewing the relevant identity/recommendation/save lines.
4. Run the complete host tests, lint and debug build again.
5. Run the end-to-end emulator path: sign in or enter explicit demo mode → create/select recommendation → start Live → observe changing UCI readings → pause/resume → trigger a HIGH prompt → finish once → verify History → verify Statistics → switch/logout account and confirm isolation.
6. Merge to `main` only after the team accepts that path. Do not merge this branch based only on the isolated tests above.

## 8. Feature commits

- `ebb72b7` — attributed UCI sensor playback subset
- `ed649b5` — motion-window classifier
- `954e604` — CSV repository implementing `SensorRepository`
- `70773d6` — Live workout state, timer, pause and retry
- `4bd33c8` — state-driven Live Activity UI

The branch also contains the earlier Member C Room/CRUD work and the merge from current `main` (`6eee53e`) used for compatibility verification.
