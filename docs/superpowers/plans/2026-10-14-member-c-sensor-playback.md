# Member C Sensor Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replay an attributed UCI inertial-data subset through the shared `SensorRepository`, derive transparent movement bands, drive a pausable Live workout with a monotonic timer and safety prompt, and save one completed simulation to the existing Room workout stream.

**Architecture:** A reproducible extraction script creates a small combined CSV asset from paired UCI accelerometer/gyroscope rows. `CsvSensorRepository` parses and paces a cold flow, `MotionWindowClassifier` adds a band from one-second windows, and `LiveWorkoutViewModel` owns playback/timer state. Compose renders immutable state; the existing navigation layer alone connects the finished session to `WorkoutRecordsViewModel` and Room. A/D modules and shared interfaces stay unchanged.

**Tech Stack:** Kotlin 2.2.10, coroutines `Flow`/`StateFlow`, Android `AssetManager`, Lifecycle ViewModel, Jetpack Compose, Room, JUnit 4, kotlinx-coroutines-test, Robolectric/Compose host tests, PowerShell extraction tooling.

---

## File map

- Create `tools/extract_uci_hapt_subset.ps1`: reproducibly pair exact UCI experiment 01 accelerometer/gyroscope ranges and create the committed CSV.
- Create `app/src/main/assets/sensor/uci_hapt_exp01_user01_subset.csv`: 150 authentic rows, 50 each from standing, walking and walking downstairs.
- Create `app/src/main/assets/sensor/README.md`: DOI, CC BY 4.0 attribution, original files/ranges, units and extraction command.
- Create `app/src/main/java/com/example/movesmart/sensor/SensorDataset.kt`: supported dataset metadata and asset-reader boundary.
- Create `app/src/main/java/com/example/movesmart/sensor/MotionWindowClassifier.kt`: deterministic signal-magnitude classifier.
- Create `app/src/main/java/com/example/movesmart/sensor/CsvSensorRepository.kt`: strict CSV parser and paced looping cold flow.
- Create `app/src/main/java/com/example/movesmart/sensor/LiveWorkoutViewModel.kt`: timer, pause gate, retry and exposed UI state.
- Modify `app/src/main/java/com/example/movesmart/ui/screens/WorkoutScreens.kt`: replace fake timer/heart rate with dataset state.
- Modify `app/src/main/java/com/example/movesmart/navigation/MoveSmartApp.kt`: inject Member C sensor dependencies and save measured demo sessions.
- Create/modify focused JVM and Compose tests under `app/src/test/java/com/example/movesmart/`.
- Modify the relevant Android UI test only if its Live-screen assertions require the new truthful copy.
- Create `docs/A4_MEMBER_C_SENSOR_HANDOFF.md`: English A/D integration handoff.

### Task 1: Reproducible UCI teaching subset

**Files:**
- Create: `tools/extract_uci_hapt_subset.ps1`
- Create: `app/src/main/assets/sensor/uci_hapt_exp01_user01_subset.csv`
- Create: `app/src/main/assets/sensor/README.md`

- [ ] **Step 1: Write the extraction tool**

The script accepts an extracted UCI HAPT directory and output path, verifies paired row counts, then selects these one-based inclusive ranges from `RawData/acc_exp01_user01.txt` and `RawData/gyro_exp01_user01.txt`:

```powershell
$segments = @(
    @{ Start = 500; End = 549; Label = 'STANDING' },
    @{ Start = 7650; End = 7699; Label = 'WALKING' },
    @{ Start = 13300; End = 13349; Label = 'WALKING_DOWNSTAIRS' }
)
```

Write the exact header `elapsed_ms,ax_g,ay_g,az_g,gx_rad_s,gy_rad_s,gz_rad_s,activity_label`, use invariant-culture numeric text from the source rows, and advance elapsed time by 20 ms across the combined file. Fail when source files/ranges are missing or axis counts differ.

- [ ] **Step 2: Generate and independently verify the asset**

Run:

```powershell
.\tools\extract_uci_hapt_subset.ps1 `
  -DatasetRoot "$env:TEMP\uci-hapt-341-extracted" `
  -OutputPath ".\app\src\main\assets\sensor\uci_hapt_exp01_user01_subset.csv"
```

Expected: 151 lines including the header, elapsed values `0..2980`, 50 rows per label, and each axis value exactly equals its paired UCI raw source row.

- [ ] **Step 3: Document provenance**

Record dataset title, authors, year, DOI `10.24432/C54G7M`, CC BY 4.0, 50 Hz cadence, acceleration `g`, gyroscope `rad/s`, original filenames, exact ranges and extraction command. State that the subset is research-data playback, not the current user's sensor measurement.

- [ ] **Step 4: Commit the isolated data/provenance change**

```powershell
git add -- tools/extract_uci_hapt_subset.ps1 app/src/main/assets/sensor
git commit -m "data: add attributed UCI sensor playback subset"
```

Do not stage `.idea`, the downloaded archive or extracted full dataset.

### Task 2: Classifier tests first

**Files:**
- Create: `app/src/test/java/com/example/movesmart/MotionWindowClassifierTest.kt`
- Create: `app/src/main/java/com/example/movesmart/sensor/MotionWindowClassifier.kt`

- [ ] **Step 1: Write failing classifier tests**

Test the wished-for API:

```kotlin
val classifier = MotionWindowClassifier(windowSize = 50)
assertEquals(MovementBand.UNKNOWN, classifier.add(sample(ax = 0.0, ay = 0.0, az = 1.0)))
repeat(49) { classifier.add(sample(ax = 0.0, ay = 0.0, az = 1.0)) }
assertEquals(MovementBand.LOW, classifier.currentBand)
```

Add separate cases for moderate signal, high signal, exact threshold values, non-finite input rejection and reset when `activityLabel` changes. Expected constants:

```kotlin
const val MODERATE_ACCELERATION_DEVIATION_G = 0.08
const val MODERATE_GYROSCOPE_RAD_S = 0.30
const val HIGH_ACCELERATION_DEVIATION_G = 0.30
const val HIGH_GYROSCOPE_RAD_S = 1.50
```

Classification is `HIGH` when either high threshold is met, `MODERATE` when either moderate threshold is met, otherwise `LOW`; fewer than 50 same-label samples are `UNKNOWN`.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat --offline --no-daemon --no-configuration-cache `
  '-Dorg.gradle.java.installations.paths=D:\Android Studio\jbr' `
  '-Pkotlin.compiler.execution.strategy=in-process' `
  :app:testDebugUnitTest --tests '*MotionWindowClassifierTest'
```

Expected: compilation/test failure because `MotionWindowClassifier` is absent.

- [ ] **Step 3: Implement the minimal classifier**

Use a bounded 50-sample accumulator containing the sum of acceleration deviation and gyroscope magnitude. Clear the accumulator when the label changes. Reject axis lists that do not contain exactly three finite values. Expose `currentBand` and return the band from `add(MotionContext)`.

- [ ] **Step 4: Run GREEN and commit**

Run the same focused command, expecting all classifier tests to pass, then:

```powershell
git add -- app/src/main/java/com/example/movesmart/sensor/MotionWindowClassifier.kt app/src/test/java/com/example/movesmart/MotionWindowClassifierTest.kt
git commit -m "feat: classify replayed motion windows"
```

### Task 3: CSV repository tests first

**Files:**
- Create: `app/src/test/java/com/example/movesmart/CsvSensorRepositoryTest.kt`
- Create: `app/src/main/java/com/example/movesmart/sensor/SensorDataset.kt`
- Create: `app/src/main/java/com/example/movesmart/sensor/CsvSensorRepository.kt`

- [ ] **Step 1: Write failing parser/playback tests**

Define an injectable reader and pacing boundary:

```kotlin
fun interface SensorAssetReader { fun open(assetPath: String): Reader }
fun interface SamplePacer { suspend fun wait(milliseconds: Long) }
```

Test a `StringReader` fixture for exact numeric values/units/source, monotonically increasing global `sampleIndex` and elapsed time, 20 ms pacing, label propagation, band propagation after 50 rows and cold-flow restart. Add separate tests for unknown dataset ID, wrong header, wrong column count, non-numeric/non-finite values, timestamps not increasing by 20 ms, empty data and cancellation. Use `take(...)` for the looping flow.

- [ ] **Step 2: Run RED**

Run the focused repository test and verify failure because repository/catalog classes do not exist.

- [ ] **Step 3: Implement catalog, Android reader and repository**

Use dataset ID `uci-hapt-exp01-user01-subset-v1` mapped only to `sensor/uci_hapt_exp01_user01_subset.csv`. Parse the entire small asset once per collection, require at least one row, and loop the validated rows while the collector remains active. On each loop, preserve monotonic global sample/elapsed values, pace 20 ms between emissions, feed a classifier and emit `DataSource.DEMO`. Rethrow `CancellationException`; wrap parsing failures with dataset ID and CSV row number.

Provide an `AndroidSensorAssetReader` using application-context `assets.open(path).bufferedReader()` without retaining an Activity.

- [ ] **Step 4: Run GREEN and real-asset regression**

Add a Robolectric-backed test that opens the committed asset through the Android reader and asserts the first complete windows classify as `LOW`, `MODERATE` and `HIGH` for the three selected segments. Run repository and classifier suites.

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/com/example/movesmart/sensor app/src/test/java/com/example/movesmart/CsvSensorRepositoryTest.kt
git commit -m "feat: replay UCI sensor data through shared contract"
```

### Task 4: Live view-model tests first

**Files:**
- Create: `app/src/test/java/com/example/movesmart/LiveWorkoutViewModelTest.kt`
- Create: `app/src/main/java/com/example/movesmart/sensor/LiveWorkoutViewModel.kt`

- [ ] **Step 1: Write failing state/timer tests**

Use a controlled `SensorRepository`, test dispatcher and monotonic clock. Define immutable state containing:

```kotlin
data class LiveWorkoutState(
    val loading: Boolean = true,
    val paused: Boolean = false,
    val elapsedMilliseconds: Long = 0,
    val latestMotion: MotionContext? = null,
    val safetyPrompt: String? = null,
    val playbackError: String? = null
)
```

Test loading-to-active, elapsed progression from monotonic time, no paused-time accumulation, upstream back-pressure while paused, resume, high-only safety prompt, error display, retry restart, cancellation in `onCleared`, and `summary()` rounding `1..1440` minutes with the original start `Instant`.

- [ ] **Step 2: Run RED**

Run the focused test and verify the missing class causes failure.

- [ ] **Step 3: Implement minimal ViewModel**

Collect one repository flow in `viewModelScope`. Before accepting each emission, wait until the paused state is false so collection suspends upstream. Run a 250 ms timer tick using an injected monotonic clock and account for pause boundaries. Exact safety text for `HIGH`:

```text
Your movement is high. Consider taking a short break.
```

Retry cancels the old playback job, clears only playback error/latest motion and starts a new collection. It must not reset the workout timer. `summary()` contains active elapsed minutes rounded up to at least one and the original wall-clock start time.

- [ ] **Step 4: Run GREEN and commit**

Run the focused tests, then the sensor package test group, and commit production plus test files.

### Task 5: Compose Live screen tests first

**Files:**
- Modify: `app/src/test/java/com/example/movesmart/WorkoutRoomUiTest.kt` or create `LiveActivityScreenTest.kt`
- Modify: `app/src/main/java/com/example/movesmart/ui/screens/WorkoutScreens.kt`

- [ ] **Step 1: Write failing UI tests**

Render `LiveActivityScreen` from immutable `LiveWorkoutState` and assert:

- fake `112 bpm` and `Sample heart rate` text do not exist;
- loading identifies the UCI simulation without pretending to read the device;
- active state shows activity, elapsed `mm:ss`, planned progress, source label, movement band, activity label, three acceleration values in `g` and three gyroscope values in `rad/s`;
- high prompt appears only when supplied;
- Pause/Resume callbacks and labels are correct;
- playback error exposes Retry and still allows finish after confirmation;
- finish dialog states that the measured simulated duration will be saved.

- [ ] **Step 2: Run RED**

Run the focused Compose test. Expected: failure against the old fake timer/heart-rate screen.

- [ ] **Step 3: Refactor screen to state/callback rendering**

Use a signature equivalent to:

```kotlin
fun LiveActivityScreen(
    activity: String,
    intensity: String,
    plannedMinutes: Int,
    state: LiveWorkoutState,
    isSaving: Boolean,
    saveError: String?,
    onPauseToggle: () -> Unit,
    onRetryPlayback: () -> Unit,
    onFinish: () -> Unit
)
```

Remove all heart-rate UI and fixed `sampleSessionMinutes` elapsed output. Keep the general project theme/components. Format numbers compactly without changing their documented units.

- [ ] **Step 4: Run GREEN and commit**

Run the focused Compose tests and existing `WorkoutRoomUiTest`, then commit the screen and tests.

### Task 6: Navigation/Room integration tests first

**Files:**
- Modify: `app/src/test/java/com/example/movesmart/WorkoutRoomUiTest.kt`
- Modify: `app/src/main/java/com/example/movesmart/navigation/MoveSmartApp.kt`
- Modify if required: `app/src/androidTest/java/com/example/movesmart/PrototypeUiTest.kt`

- [ ] **Step 1: Write failing integration regression**

Inject a deterministic `SensorRepository` into `MoveSmartApp`, enter Live, advance virtual data/time, finish once, and assert the Room record has:

```kotlin
source == DataSource.DEMO
startedAt != null
minutes == maxOf(1, ceil(activeElapsedMilliseconds / 60_000.0).toInt())
```

Assert History and Statistics observe the same single row and repeated finish actions cannot create a duplicate.

- [ ] **Step 2: Run RED**

Verify the old navigation saves a fixed sample duration with `startedAt == null`.

- [ ] **Step 3: Inject the sensor implementation and measured summary**

Add optional `SensorRepository` injection to `MoveSmartApp` for tests, with production default `CsvSensorRepository(AndroidSensorAssetReader(applicationContext))`. Build `LiveWorkoutViewModel` only for the Live destination. Pass its state/actions to the screen. On confirmed finish, call the existing `WorkoutRecordsViewModel.save` once with measured summary and `DataSource.DEMO`; navigate only after Room success. Preserve the existing explicit `workoutUserId = "local-demo"` adapter and add no A implementation.

- [ ] **Step 4: Run GREEN and wider regressions**

Run the integration test, all Compose host tests and all JVM tests. Update only assertions made obsolete by truthful Live copy; do not weaken unrelated tests.

- [ ] **Step 5: Commit**

```powershell
git add -- app/src/main/java/com/example/movesmart/navigation/MoveSmartApp.kt app/src/test app/src/androidTest
git commit -m "feat: connect sensor playback to live workout records"
```

### Task 7: Handoff, review and verification

**Files:**
- Create: `docs/A4_MEMBER_C_SENSOR_HANDOFF.md`
- Modify: `docs/A4_MEMBER_C_ROOM.md` only to link the completed next milestone and correct obsolete statements.

- [ ] **Step 1: Write the English A/D handoff**

Document Member C-owned files, dataset citation/licence, the exact `SensorRepository`/`MotionContext` behavior, demo-source semantics, how A must supply `UserAccount.userId` and recommendation ID, how D continues consuming the same Room `WorkoutRepository`, merge hotspots in navigation/Gradle, and the required manual acceptance path. Include actual command outputs only after verification.

- [ ] **Step 2: Run full fresh verification**

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
.\gradlew.bat --offline --no-daemon --no-configuration-cache `
  '-Dorg.gradle.java.installations.paths=D:\Android Studio\jbr' `
  '-Pkotlin.compiler.execution.strategy=in-process' `
  :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Count test XML failures/errors/skips, inspect lint errors, verify APK outputs, run `git diff --check`, and confirm no `.idea`, archive or extracted full dataset is part of the new commits.

- [ ] **Step 3: Device check if available**

Run `adb devices -l`. If a compatible device is online, run `:app:connectedDebugAndroidTest` and manually verify pause/resume, changing bands, high prompt, retry behavior and one saved record. If unavailable/offline, record the exact limitation without claiming device success.

- [ ] **Step 4: Commit documentation and stop at A dependency**

Commit the handoff and corrected Room handoff only. Then stop before connecting real auth/recommendations. Report the exact A interfaces required and whether D needs to resolve any navigation/statistics merge issue.

- [ ] **Step 5: Push only after all verification succeeds**

Push `member-c-dev` to `origin/member-c-dev` because the user has requested the Markdown handoff on GitLab. Do not merge to `main`, do not delete the source branch, and do not include `.idea` or unrelated files.
