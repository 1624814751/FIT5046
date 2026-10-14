# Member A Phase 2 testing

## Branch and Phase 1 check

Work started on `main` at `28a3d0f`. The school GitLab remote is `origin`:
`https://git.infotech.monash.edu/fit5046/fit5046-s2-2026/114.git`.
`origin/Shiya_dev` was fetched and checked at `cb92241` before deleting Phase 2 files.
The local branch now tracks `origin/Shiya_dev`; the fast-forward pull was already up to date.

Only the verified old weather files/tests/docs and three weather configuration changes were
discarded. No broad clean command, commit or push was used. Phase 1 source, local SDK settings
and `.idea/codeStyles` were kept. The temporary IDE backup did not survive the sandbox session,
so the exact earlier local edits to `.idea/gradle.xml`, `.idea/misc.xml` and the untracked
`.idea/deviceManager.xml` could not be restored; those files now use the branch versions.

Firebase configuration was missing at first. The normal build reported the missing
`app/google-services.json`. The skipped-task build was rejected and did not run. Once the team
file appeared at the correct path, all normal Phase 1 checks passed with Firebase resource
generation enabled. There were 42 unit tests with no failures or errors. No Phase 1 Kotlin
source was changed. The team Firebase file remains local and must not be staged without approval.

## Normal build checks

Use JDK 21. In this workspace, set it only for the terminal session:

```powershell
$env:JAVA_HOME = 'C:\workspace\JDK\jdk-21.0.2'
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

Do not exclude `processDebugGoogleServices`. Keep the real team file at
`app/google-services.json`. Never create a sample Firebase file to make the build pass.
Ordinary unit tests use fake API responses and local Robolectric Android storage/WorkManager.
They need no live weather connection; the first build may download test runtimes.

## Real weather and UI checks

Member D must connect the ViewModel or use a local debug caller described in the
[interface guide](MEMBER_A_INTERFACE_GUIDE.md). Home still shows demo weather in this phase.

1. Start a connected emulator. Load Melbourne coordinates -37.8136, 144.9631 with today's
   Melbourne date, then tomorrow. Check Loading followed by Success and `source == LIVE`.
2. Inspect the Retrofit response with Network Inspector or a breakpoint in `toWeather`.
   Find the selected date at `T12:00` and compare temperature, probability and wind arrays.
   Do not compare a noon forecast with a current weather reading.
3. You can also open the [same Melbourne API request](https://api.open-meteo.com/v1/forecast?latitude=-37.8136&longitude=144.9631&hourly=temperature_2m,precipitation_probability,wind_speed_10m&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=16)
   in a browser. Check units `°C`, `%` and `km/h` and the returned forecast range.
4. Load latitude 91 or `Double.NaN`. Expect a location error and no network request.
   Load a date one month ahead. Expect a date error, never today's forecast.
5. Load one valid query online, disable Wi-Fi/mobile data, and retry that query. Expect
   `DataSource.CACHE`. Use different coordinates/date offline and expect an error.
6. Restore the network and press Retry. Expect a new LIVE result and fetch time.
7. After a success, close and reopen the app process without clearing app data. Retry the same
   query offline. Check the original saved values and fetch time return.
8. After at least one hour, retry offline and check `isStale`. Unit tests check the exact boundary
   with a fixed clock. A new online success should replace the old data.

## Background checks

1. After a valid load, call `WeatherWork(context).startWeatherWork(query)` once.
2. Open Android Studio > App Inspection > Background Task Inspector. Find
   `WeatherRefreshWorker` tagged `weather-refresh`, with connected-network constraint and
   60-minute interval. The first run has a 60-minute delay.
3. Start again for another valid date. There should be one unfinished unique job.
4. Leave the app and wait for Android to run it. A newer saved `fetchedAt` plus actual successful
   work state is evidence of an update. A queued job is not proof of successful execution.
5. Disable the network before work is due. Work should wait for its constraint. A failure during
   a request should retry with WorkManager backoff; cache fallback also means retry.
6. Close the app process and check that the stored query/schedule remain. Do not force-stop
   from Settings: that blocks normal background work until the app is reopened.
7. Call `stopWeatherWork()` and verify cancellation. Stop work on logout or when a plan ends.

## Device tests

The new weather device tests restore the previous cache after each test. They check Android
cache storage and missing worker input. A real weather test is skipped unless explicitly enabled.
It checks Retrofit, saved weather and a real one-time WorkManager run. That short test uses the
same worker as production, but does not prove the delayed hourly periodic run.

Run focused Phase 1 Profile logout and weather checks with the real API enabled:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.movesmart.ProfileLogoutUiTest,com.example.movesmart.WeatherDeviceTest' '-Pandroid.testInstrumentationRunnerArguments.weatherLiveTest=true'
```

The existing A2 onboarding test uses fixed signup credentials. It is not a reliable real Firebase
test and can create an account. It is left unchanged. Real email/Google login, account switching
and Firebase session restoration still require the team's manual Phase 1 test steps.

## Member D input

Provide real coordinates and the selected calendar date. The current labels do not include
coordinates, and there is no geocoding service. Convert DatePicker milliseconds using UTC, as
the current form does. Observe shared `UiState`, show saved/old-weather labels, and start/stop
work after a valid query is available. Keep the sample rain switch until integration is agreed.

## Phase 2 verification record

Verified on 11 October 2026, on `Shiya_dev`, with JDK 21.0.2 and normal Firebase generation:

| Command/check | Actual result |
|---|---|
| `:app:compileDebugKotlin` | Passed |
| `:app:testDebugUnitTest` | Passed: 79 tests, 0 failures, 0 errors, 0 skipped |
| `:app:assembleDebug` | Passed; debug APK built |
| `:app:lintDebug` | Passed: 0 errors, 24 warnings, 2 hints |
| Focused `:app:connectedDebugAndroidTest` command above | Passed: 4 tests, 0 failures/errors/skipped, Pixel_10_Pro emulator on Android 17 |
| `git diff --check` | Passed |

The unit count includes all 42 existing tests plus 37 new tests: 20 mapping/repository tests,
5 ViewModel tests and 12 cache/worker/scheduling tests. Ordinary unit tests used no live API.
Lint has dependency update suggestions and existing source/resource cleanup findings; none
target the new weather Kotlin files. Existing library versions were not upgraded.

The four device tests actually checked:

- Profile logout using the existing fake auth/provider test while preferences are loading.
- Saving and restoring weather through Android SharedPreferences with a new store instance.
- The real worker's safe failure for missing input.
- Real Retrofit/Open-Meteo fetching and mapping on Android, saving the matching forecast,
  and a real one-time WorkManager refresh that reached `SUCCEEDED` and advanced `fetchedAt`.

The live test's one-time job was cancelled/cleaned up after the check, and the earlier weather
cache was restored. No production periodic schedule was left running. This proves the worker
can run through WorkManager on the emulator; it does not prove the delayed hourly schedule.

Still manual: the 60-minute periodic run, cache after a real app process restart, visible weather
loading/error/retry UI after Member D connects it, and real Firebase email/Google login and
session tests. The full historical A2 UI suite was not run; the fixed-credential onboarding
test is left unchanged. The focused Profile logout test is not a real Firebase logout test.

Final Git check: `Shiya_dev` tracks `origin/Shiya_dev`, no staged files, no commits or pushes.
Firebase config remained unchanged and untracked. Phase 1 Kotlin, shared contracts, Member C/D
source and the manifest have no local diff. See the IDE preservation caveat near the top.
