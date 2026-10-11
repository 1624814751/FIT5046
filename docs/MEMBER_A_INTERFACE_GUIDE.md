# Member A interface guide

Audit basis: repository state on 11 October 2026. Kotlin source is treated as authoritative. Historical A2 documents still refer to a fourth member (Member B); no repository file records how that work was reassigned after the team changed to A, C and D.

## 1. Project structure

The project contains one Android application module, `:app`.

| Path | Current purpose |
|---|---|
| `settings.gradle.kts` | Declares the single `:app` module and Google/Maven Central repositories. |
| `app/build.gradle.kts` | Android/Compose build configuration. It has Compose, Navigation, Lifecycle, JUnit and Android UI-test dependencies. |
| `app/src/main/AndroidManifest.xml` | App entry point and Member D's reminder receivers/permissions. |
| `app/src/main/java/com/example/movesmart/shared/SharedModels.kt` | Shared A4 enums and data models. These are contracts, not implementations. |
| `app/src/main/java/com/example/movesmart/shared/ModuleContracts.kt` | Repository and service interfaces owned by A, C and D. |
| `app/src/main/java/com/example/movesmart/navigation/MoveSmartApp.kt` | The only navigation graph and current integration shell. It uses demo state rather than Member A repositories. |
| `app/src/main/java/com/example/movesmart/ui/auth/LoginScreen.kt` | Member A login form UI. |
| `app/src/main/java/com/example/movesmart/ui/auth/SignUpScreen.kt` | Member A sign-up form UI. |
| `app/src/main/java/com/example/movesmart/ui/preferences/PreferencesScreen.kt` | Member A preferences UI. |
| `app/src/main/java/com/example/movesmart/ui/profile/ProfileScreen.kt` | Member A profile UI and links to D's reminders/statistics. |
| `app/src/main/java/com/example/movesmart/model/PrototypeState.kt` | A2-only in-memory/saveable UI state. It is not account or database storage. |
| `app/src/main/java/com/example/movesmart/model/PrototypeModels.kt` | A2-only models, including names that duplicate shared `WorkoutRecord` and `ReminderSettings`. |
| `app/src/main/java/com/example/movesmart/model/ContextRecommendation.kt` | A2-only deterministic recommendation and its differently named `ContextRecommendation` model. |
| `app/src/main/java/com/example/movesmart/ui/context/WorkoutViewModel.kt` | A2 planning form state and sample weather/movement values. It does not use the shared contracts. |
| `app/src/main/java/com/example/movesmart/ui/context/ContextScreens.kt` | Home, planning form and recommendation-detail UI. Source comments and team history attribute these screens to former Member B. |
| `app/src/main/java/com/example/movesmart/ui/screens/WorkoutScreens.kt` | Member C live/history/editor/search UI, still backed by prototype records. |
| `app/src/main/java/com/example/movesmart/reminder/*` | Member D's implemented reminder repository, scheduler and Android integration. |
| `README.md`, `docs/TEAM_INTEGRATION.md`, `docs/A2_REVIEW_AND_HANDOFF.md`, `docs/A4_MEMBER_D_REMINDERS.md` | Available project history and handoff notes. Some A2 responsibility statements are historical. |

### Ownership and dependencies

Confirmed by `ModuleContracts.kt`:

- Member A owns implementations of `AuthRepository`, `PreferencesRepository`, `WeatherRepository`, `RecommendationEngine` and `RecommendationStore`.
- Member C owns `WorkoutRepository` and `SensorRepository`.
- Member D owns `ReminderRepository` and `ReminderScheduler`; implementations already exist.

Member A depends on Member C for a `Flow<MotionContext>` and, when recommendations become workouts/history, C's user-scoped workout persistence. Member A depends on the planning UI owner for `PlanningInput`, and the recommendation display owner consumes the resulting `Recommendation`. The user's current allocation describes that planning/display integration as Member D, but the checked-in source and historical documents still label it Member B. A/C/D should confirm the reassignment before editing those files.

Reusable work already present:

- Validated Compose forms for login, sign-up and preferences; profile presentation; password visibility controls; and callback-based navigation.
- Shared data models and Member A interface definitions.
- A single navigation graph with stable route constants.
- `WorkoutViewModel` state retention through `SavedStateHandle`, a planning DatePicker/dropdowns, and a deterministic recommendation demo useful as a rule/test fixture.
- Member D's real, user-scoped reminder persistence/scheduling, ready to receive an authenticated `UserAccount.userId` instead of `"local-demo"`.

## 2. Shared data models

All types below exist in `shared/SharedModels.kt`. A type without `?` is non-null.

### Supporting enums

```kotlin
enum class Intensity { LOW, MEDIUM, HIGH }
enum class Environment { INDOOR, OUTDOOR, ANY }
enum class MovementBand { UNKNOWN, LOW, MODERATE, HIGH }
enum class DataSource { DEMO, LIVE, CACHE }
```

### Member A and integration models

```kotlin
data class UserAccount(
    val userId: String,
    val displayName: String,
    val email: String
)
```

Represents the authenticated account. `userId` is the cross-module user key; no password or provider token is stored in this model.

```kotlin
data class UserPreferences(
    val userId: String,
    val goal: String,
    val preferredIntensity: Intensity,
    val weeklyGoalMinutes: Int,
    val activityNames: List<String>,
    val environment: Environment
)
```

Stores a user's recommendation/statistics preferences. All properties are required. The existing prototype `Preferences` is a different model: it lacks `userId` and `environment`, and uses a `String` intensity.

```kotlin
data class GeoLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double
)
```

Carries both display text and coordinates for planning/weather lookup. The current form only selects a label, so its coordinate mapping is missing.

```kotlin
data class PlanningInput(
    val date: LocalDate,
    val availableMinutes: Int,
    val intensity: Intensity,
    val environment: Environment,
    val location: GeoLocation
)
```

The complete activity-planning input expected by `ContextInput`. The existing form holds equivalent values as strings/milliseconds but does not construct this model.

```kotlin
data class WeatherQuery(
    val location: GeoLocation,
    val date: LocalDate
)
```

Keys weather observation/refresh requests by location and date.

```kotlin
data class WeatherContext(
    val query: WeatherQuery,
    val temperatureC: Double,
    val rainProbabilityPercent: Int?,
    val windSpeedKmh: Double?,
    val fetchedAt: Instant,
    val source: DataSource,
    val isStale: Boolean
)
```

Weather used during evaluation. Rain probability and wind speed may be absent; the query, temperature, timestamp, source and staleness flag may not.

```kotlin
data class MotionContext(
    val datasetId: String,
    val sampleIndex: Long,
    val elapsedMilliseconds: Long,
    val accelerometer: List<Double>,
    val gyroscope: List<Double>,
    val accelerometerUnit: String,
    val gyroscopeUnit: String,
    val activityLabel: String?,
    val movementBand: MovementBand,
    val source: DataSource
)
```

Represents one dataset/sensor playback sample. Only `activityLabel` is nullable. Units come from the selected dataset and must not be assumed to be SI units; these values are not heart-rate data.

```kotlin
data class Recommendation(
    val id: String,
    val activityName: String,
    val minutes: Int,
    val intensity: Intensity,
    val environment: Environment,
    val plannedDate: LocalDate,
    val location: GeoLocation,
    val reasons: List<String>
)
```

The shared recommendation passed between evaluation, storage and display. `id` is the lookup/navigation identity. This is not the prototype `ContextRecommendation`, whose fields are only `title`, `minutes`, `weatherReason` and `preferenceReason`.

```kotlin
data class ContextInput(
    val preferences: UserPreferences,
    val plan: PlanningInput,
    val weather: WeatherContext?,
    val motion: MotionContext?,
    val evaluatedAt: Instant
)
```

Bundles all evaluation inputs. Weather and motion are explicitly nullable; preferences, plan and evaluation time are required.

```kotlin
data class ContextDecision(
    val recommendation: Recommendation,
    val safetyPrompt: String?
)
```

Returns the recommendation plus an optional safety message.

```kotlin
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val retryable: Boolean) : UiState<Nothing>
}
```

Generic presentation state. It exists but is not used by current Member A screens/ViewModels.

### Other cross-module shared models

`WorkoutRecord` and `ReminderSettings` also exist in `shared/SharedModels.kt`. `WorkoutRecord` is C-owned persistence data with `id`, `userId`, `title`, `minutes`, `date`, nullable `startedAt`, and `source`. `ReminderSettings` is D-owned, user-scoped reminder data. Same-named A2 prototype models under `com.example.movesmart.model` are different types and should not cross the A4 interfaces.

## 3. Member A interfaces

All signatures are copied from `shared/ModuleContracts.kt`.

### `AuthRepository`

```kotlin
interface AuthRepository {
    val currentUser: StateFlow<UserAccount?>
    suspend fun signIn(email: String, password: String): Result<UserAccount>
    suspend fun signUp(displayName: String, email: String, password: String): Result<UserAccount>
    suspend fun signInWithGoogle(idToken: String): Result<UserAccount>
    suspend fun signOut(): Result<Unit>
}
```

- `currentUser` is a hot `StateFlow` with an immediately readable latest value; `null` means no authenticated account. Initial loading/restoration semantics are not specified.
- The implementation must authenticate, map the provider account to `UserAccount`, update `currentUser`, and clear it on sign-out. Passwords and ID tokens must not be persisted or logged.
- Each command reports failure through `Result`; callers must handle validation, provider/network failures and sign-out failure. The contract does not define exception classes or user-facing messages.

### `PreferencesRepository`

```kotlin
interface PreferencesRepository {
    fun observe(userId: String): Flow<UserPreferences?>
    suspend fun save(preferences: UserPreferences): Result<Unit>
}
```

- `observe` returns updates for one user; `null` means no saved preferences. The contract does not say whether the `Flow` is hot/cold or whether it emits immediately.
- The implementation must isolate data by `userId`, persist all fields, and emit saved changes.
- `save` failures use `Result`. Collection failures are not modelled and may still be thrown by a concrete `Flow`; the UI needs a catch/retry policy.

### `WeatherRepository`

```kotlin
interface WeatherRepository {
    fun observe(query: WeatherQuery): Flow<WeatherContext?>
    suspend fun refresh(query: WeatherQuery): Result<WeatherContext>
}
```

- `observe` supplies cached/current weather for exactly one query; `null` means unavailable. Hot/cold and initial-emission behaviour are unspecified.
- `refresh` must obtain and return weather for the same location/date, set `fetchedAt`, `source` and `isStale`, and make refreshed data observable.
- `Result` must represent refresh failure. The implementation also needs policies for unsupported dates, network/HTTP/parsing errors, missing optional fields, cache fallback and staleness; none are fixed by the interface.

### `RecommendationEngine`

```kotlin
interface RecommendationEngine {
    fun evaluate(input: ContextInput): ContextDecision
}
```

- This is synchronous and has no `Flow`, `suspend` or `Result` wrapper.
- It must produce a complete `Recommendation` and optional safety prompt from preferences, plan and nullable context. Its ID-generation rules and decision rules are not defined.
- Because failure is not represented, implementations should validate predictable input and use deterministic fallback rules for missing weather/motion. Whether invalid input throws is a team decision that must be tested.

### `RecommendationStore`

```kotlin
interface RecommendationStore {
    fun observe(id: String): Flow<Recommendation?>
    suspend fun save(recommendation: Recommendation): Result<Unit>
}
```

- `observe` tracks one recommendation ID; `null` means it is not present. Hot/cold and initial-emission behaviour are unspecified.
- The source comment permits an in-memory cache for this phase. The implementation must keep `Recommendation.id` stable and emit saved changes.
- `save` failures use `Result`; collection failures and duplicate-ID/overwrite behaviour are unspecified.

## 4. Integration with Member D

### Confirmed current behaviour

The planning UI stores date, duration, intensity, location, environment preference and a rain toggle in `WorkoutViewModel`. It generates the A2-only `ContextRecommendation` directly. Navigation from `discover` to `plan` passes no argument; both screens read the same navigation-graph-scoped `WorkoutViewModel`. Starting a workout copies `title`, `minutes` and a string intensity into `PrototypeState`. No shared `PlanningInput`, `ContextInput`, `Recommendation` or `RecommendationStore` is used.

### Intended use of the existing contracts

1. D's planning module supplies a complete `PlanningInput`. A mapping layer is required for UI strings, date milliseconds and location labels.
2. A creates `WeatherQuery(plan.location, plan.date)`, observes cached weather and calls `WeatherRepository.refresh(query)` when fresh data is required.
3. A combines the current user's `UserPreferences`, the plan, nullable weather and the latest nullable C-owned motion sample into `ContextInput`, then calls `RecommendationEngine.evaluate`.
4. A saves `decision.recommendation` through `RecommendationStore.save`.
5. D observes/displays the recommendation by `Recommendation.id` and renders `reasons` plus `ContextDecision.safetyPrompt` where the decision is still available.

No navigation contract currently carries a recommendation. For an ID-based integration, add a recommendation detail route only after team agreement and pass `Recommendation.id`, not the whole object. Authentication-dependent destinations should obtain `UserAccount.userId` from session state. The existing workout start callback may continue passing display fields temporarily, but C's eventual persisted `WorkoutRecord` also requires the authenticated `userId`. Route shape, ID generation, safety-prompt storage, and who owns the former B planning files require confirmation.

Member D's reminder integration already exposes one concrete requirement: replace `reminderUserId = "local-demo"` with the authenticated `UserAccount.userId`, activate reminders for that ID after sign-in, and retain the existing `ReminderService.logout()` call during sign-out.

## 5. Integration with Member C

The only motion provider contract is:

```kotlin
interface SensorRepository {
    // Cold Flow: collection starts playback; cancellation stops it.
    fun playback(datasetId: String): Flow<MotionContext>
}
```

Member C owns dataset selection/loading, sample timing, unit/label interpretation and the cold-flow lifecycle. Each emitted `MotionContext` is non-null. Member A owns collection in an appropriate lifecycle scope, retaining the latest sample while collected, and placing it into `ContextInput.motion` for evaluation.

`ContextInput.motion` is nullable, so Member A must support the state before the first sample, after collection is stopped, or when sensing is deliberately unavailable. The engine can use `movementBand`, `activityLabel`, elapsed/sample metadata and raw vectors to adjust a recommendation or safety prompt, but the exact rule set is not present in the repository. It must not interpret accelerometer/gyroscope values as heart rate.

Member A should not implement the dataset player or C's `WorkoutRepository`. Member C should not implement authentication, weather lookup, recommendation rules or recommendation caching. Dataset IDs, reevaluation frequency, and whether the last sample remains valid after playback ends need team confirmation.

## 6. Integration examples

These examples use only existing contracts and properties. They illustrate coordination; dependency construction and lifecycle ownership are not yet present in the project.

### Authenticate and observe the session

```kotlin
val result: Result<UserAccount> = authRepository.signIn(email, password)
result.onFailure { error ->
    // Map error to UiState.Error without logging the password.
}

authRepository.currentUser.collect { account: UserAccount? ->
    // null means signed out; account.userId scopes preferences/reminders/workouts.
}
```

### Fetch weather and evaluate a plan

```kotlin
val query = WeatherQuery(location = plan.location, date = plan.date)
val weather: WeatherContext? = weatherRepository.refresh(query).getOrNull()

val input = ContextInput(
    preferences = preferences,
    plan = plan,
    weather = weather,
    motion = latestMotion,
    evaluatedAt = Instant.now()
)
val decision: ContextDecision = recommendationEngine.evaluate(input)
recommendationStore.save(decision.recommendation).getOrThrow()
```

Here `plan: PlanningInput`, `preferences: UserPreferences`, and `latestMotion: MotionContext?` are existing types. Production UI should not use `getOrThrow()` without converting failure to a user-visible state.

### Consume C's cold motion flow

```kotlin
sensorRepository.playback(datasetId).collect { motion: MotionContext ->
    latestMotion = motion
    // Re-evaluate only according to an agreed throttling/change policy.
}
```

Cancelling this collection stops playback as specified by the source comment.

### Display a stored recommendation

```kotlin
recommendationStore.observe(recommendationId).collect { recommendation: Recommendation? ->
    when (recommendation) {
        null -> Unit // Show missing/loading according to the screen's state policy.
        else -> showName = recommendation.activityName
    }
}
```

## 7. Implementation readiness

| Status | Item | Evidence / next action |
|---|---|---|
| Already implemented | Shared Member A models and five interfaces | Present in `shared/SharedModels.kt` and `shared/ModuleContracts.kt`. |
| Already implemented | Member A Compose form/profile layouts | Four files under `ui/auth`, `ui/preferences` and `ui/profile`; currently callback/demo driven. |
| Already implemented | Shared navigation shell and A→planning→C demo flow | `MoveSmartApp.kt`; no production repositories. |
| Already implemented | D reminder persistence and scheduling | Implements the shared D contracts and uses `local-demo` pending A integration. |
| Partially implemented | Authentication UX | Validation and callbacks exist; callbacks discard passwords and no `AuthRepository` implementation/view model exists. |
| Partially implemented | Preferences UX | Form/state propagation exists using prototype strings; no shared model mapping or `PreferencesRepository`. |
| Partially implemented | Recommendation UX/rules | Deterministic A2 function and tests exist, but it uses `ContextRecommendation`, not the shared engine/model. |
| Partially implemented | Weather/motion presentation | Sample strings/toggle exist; there is no repository or sensor-flow integration. |
| Missing | Implementations of all five Member A interfaces | Repository search found none. |
| Missing | Member A presentation/state layer using `UiState` | Current screens connect directly to `PrototypeState`/callbacks. |
| Missing | Production dependencies/configuration | No authentication provider/Credential Manager, Retrofit/HTTP/serialization, Room, DI, or WorkManager dependency is declared. The manifest also lacks `android.permission.INTERNET`, required by a network weather implementation. |
| Missing | Model mappings | String-to-enum mapping, `GeoLocation` coordinates, shared preferences mapping, and prototype/shared recommendation replacement are absent. |
| Missing | Recommendation navigation/storage wiring | No route takes `Recommendation.id`; `RecommendationStore` is unused. |
| Missing | Member A repository/engine tests | Existing recommendation tests cover only the A2 helper. |
| Blocked by C | Real motion samples | Requires `SensorRepository`, dataset and playback policy from Member C. |
| Blocked by C | Persisted workout handoff | Requires C's `WorkoutRepository`; existing history uses prototype records. |
| Blocked by team confirmation | Former Member B planning ownership | Source/history says B; the current three-person allocation and exact D ownership are not checked in. |
| Blocked by team confirmation | Auth/weather providers and storage policy | No provider/API, credential flow, cache technology or error taxonomy is selected in source. |
| Blocked by team confirmation | Recommendation identity/rules/lifetime | ID generation, overwrite semantics, safety-prompt persistence, reevaluation triggers and cache lifetime are unspecified. |

## Validation findings and conflicts

- Every model property and interface signature in this guide was checked against the two shared Kotlin source files.
- The requested models all exist with the exact requested names. A differently named prototype `ContextRecommendation` also exists but is not the shared `Recommendation`.
- No implementations of `AuthRepository`, `PreferencesRepository`, `WeatherRepository`, `RecommendationEngine`, `RecommendationStore`, `WorkoutRepository`, or `SensorRepository` were found. D's `ReminderStore` and `AndroidReminderScheduler` are the only shared repository/service implementations.
- UI vocabulary conflicts with shared enums: `"Easy"` has no same-named `Intensity` value (`LOW` is available), and `"No preference"` must map to `Environment.ANY`.
- The planning UI's `"Chadstone"`, `"Home"` and `"Campus"` labels do not provide the latitude/longitude required by `GeoLocation`.
- Login and sign-up validate passwords locally but expose callbacks without the password, so they cannot call the current `AuthRepository` signatures without changing screen/view-model wiring.
- The current recommendation route shares a `WorkoutViewModel` rather than a stable recommendation ID. Process restoration and direct navigation semantics are therefore not defined for stored recommendations.
- Flow error, initial-emission and dispatcher behaviour is unspecified for A's repositories. Engine invalid-input behaviour is also unspecified.
- The README and integration notes are useful history but predate the three-member allocation. The repository does not contain the referenced three-member responsibility document itself.

## Recommended Member A implementation order

1. Confirm the A/C/D responsibility reassignment, authentication provider, weather API, persistence/cache choice, recommendation ID policy and motion reevaluation policy.
2. Add only the approved dependencies/configuration, including network permission if weather is remote.
3. Implement `AuthRepository` and an auth/session state holder; refactor screen callbacks so credentials reach the repository without being saved.
4. Replace prototype account/preferences state with `UserAccount`, `UserPreferences`, `PreferencesRepository` and explicit UI-to-enum/location mappings.
5. Implement/test `WeatherRepository`, including cache, stale data and failure behaviour.
6. Implement/test the deterministic `RecommendationEngine` for nullable weather/motion, then the in-memory `RecommendationStore`.
7. Connect D's `PlanningInput` to A's evaluation pipeline and agree an ID-based display route.
8. Connect C's cold motion flow and user-scoped workout handoff, then run integrated unit and device tests.

## Phase 1 Implementation Status

Updated on 11 October 2026.

### Implemented

- `FirebaseAuthRepository` implements the existing `AuthRepository` contract for email/password signup, email/password login, Google ID token login, current-user observation, session restoration through Firebase, and sign-out.
- `MissingFirebaseAuthRepository` keeps the app usable when Firebase configuration is absent. Real login shows a clear setup error; Explore demo still works.
- `GoogleCredentialLoginProvider` uses Android Credential Manager to request a Google ID token. It handles cancellation, missing credentials, missing client ID, and other provider errors.
- `DataStorePreferencesRepository` implements `PreferencesRepository`. It stores every `UserPreferences` field, uses the stable Firebase UID as the user key, keeps accounts separate, and emits saved changes through `Flow`.
- `AuthViewModel` and `PreferencesViewModel` keep network/storage work out of Compose rendering. They use `StateFlow`, coroutines, loading guards, and simple error states.
- Existing Login, Sign Up, Preferences and Profile screens now use the Member A repositories and ViewModels. Explore demo still uses the A2 callback/state path.
- Preference mapping is kept in one file. `Easy` maps to `Intensity.LOW`, and `No preference` maps to `Environment.ANY`.
- The Preferences screen now supports the shared model's indoor/outdoor/any environment field.
- Profile shows the authenticated account and saved preferences. Sign-out uses `AuthRepository.signOut()` before returning to the existing logout callback.

### Authentication approach

Firebase Authentication is the real account provider. `UserAccount.userId` is the Firebase UID, never the email. Passwords and ID tokens are passed only to the authentication request and are not placed in shared models, saved state, DataStore or logs.

The app uses Credential Manager for Google login. `GoogleCredentialLoginProvider` reads the generated `default_web_client_id` resource. It does not contain a hard-coded client ID.

The project now has `app/google-services.json`, and Google Services Gradle plugin 4.5.0 is applied through the version catalog. The Firebase Android package matches `com.example.movesmart`. The refreshed configuration contains Android and Web OAuth clients, and the plugin generates both `google_app_id` and `default_web_client_id`. `GoogleCredentialLoginProvider` reads that generated Web client ID. Email/password and Google authentication are configured but not yet device-verified.

Required team setup:

1. Confirm the debug SHA fingerprint used by the emulator matches an Android OAuth client in Firebase Project settings.
2. Run the manual email/password and Google login checks on a Google Play-enabled emulator or device.
3. Confirm provider cancellation, sign-out and session restoration on the device.
4. Do not invent or commit private credentials. Use only the team's Firebase configuration and follow the subject's repository rules.

### Preference storage approach

Preferences use one process-wide Preferences DataStore named `member_a_preferences`. Each stored key contains the Firebase UID, so one account cannot overwrite another account's values. Activity list order is preserved. DataStore storage and observation errors are returned to or caught by the ViewModel and shown by the screen.

Demo preferences continue to use `PrototypeState`; they are not saved under a Firebase user ID. This keeps demo data separate from real account data.

### Compatibility with Members C and D

- No C or D Kotlin source file was changed.
- The public shared models and repository signatures were not changed.
- Member D's reminders still use `"local-demo"`, as required for Phase 1. `MemberAService.get(context).authRepository.currentUser` is the simple future source of `UserAccount.userId` for D's navigation integration.
- The existing Member D navigation callbacks remain unchanged. After a real preference save, the screen also calls the existing callback so Home and Statistics keep receiving the A2-compatible intensity, activities, goal and weekly minutes.
- Member C's workout/history/sensor code remains unchanged.

### New files

- `data/remote/FirebaseAuthRepository.kt`
- `data/remote/MissingFirebaseAuthRepository.kt`
- `data/remote/GoogleLoginProvider.kt`
- `ui/auth/AuthChecks.kt`
- `ui/auth/AuthViewModel.kt`
- `ui/auth/MemberAService.kt`
- `ui/preferences/DataStorePreferencesRepository.kt`
- `ui/preferences/PreferenceMappings.kt`
- `ui/preferences/PreferencesViewModel.kt`
- Member A unit tests under `app/src/test/java/com/example/movesmart/`
- `docs/MEMBER_A_PHASE1_TESTING.md`

### Changed files

- Member A's Login, Sign Up, Preferences and Profile screen files.
- `app/build.gradle.kts` and `gradle/libs.versions.toml` for Firebase Auth, Credential Manager, Google ID, DataStore, lifecycle Compose and test dependencies.
- `app/src/main/AndroidManifest.xml` adds the Internet permission required by Firebase.

### Remaining Phase 1 work

- Device-test Firebase Email/Password and Google authentication.
- Confirm the emulator build's SHA fingerprint matches Firebase if Google login returns a developer configuration error.
- Run the manual and device checks in `MEMBER_A_PHASE1_TESTING.md` with a Google Play-enabled emulator/device.
- Member D can later replace `"local-demo"` with the observed Firebase UID after the team agrees on navigation/session ownership.
- The existing navigation callback for email login only forwards the email to `PrototypeState`; therefore Home may keep its demo display name after an email login. Profile correctly uses `UserAccount`. Fixing Home requires a small D-owned navigation interface change and is left for team confirmation.

Phase 2 weather/WorkManager and Phase 3 recommendation/motion work have not started.
