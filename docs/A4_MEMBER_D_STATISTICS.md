# Member D statistics milestone

The Statistics page now supports day, Monday-to-Sunday week, and calendar month views. Users can choose a date, move to the previous/next period, and return to today. The selected date and period survive activity recreation. Changing the user resets these selections.

Each view reports total minutes, session count, active days and a daily minutes chart. Empty periods have an explicit empty state. Weekly goal progress is shown only in week mode and handles unset or exceeded goals. The monthly chart scrolls horizontally so each date has a readable label and an accessibility description.

`statistics/ActivityStatistics.kt` calculates totals from the supplied shared `WorkoutRecord` model. It filters by user ID and inclusive calendar date bounds, counts only positive-duration records, fills missing days with zero, and uses Long totals. The recorded `date` controls grouping; `startedAt` is not reinterpreted in the device's current time zone.

## Integration boundary

`MoveSmartApp` now passes Member C's shared Room records directly from `WorkoutRecordsViewModel.state` into `ActivityStatisticsScreen`, together with `workoutUserId`. The legacy in-memory adapter has been removed. `WorkoutDataContent` supplies loading, failure and retry UI; observation uses lifecycle-aware collection. History edits/deletes and completed workouts update the same repository stream and therefore update all statistics periods.

These are saved local records, not preloaded samples. The local integration now consumes A's Firebase UID and preferences, while Explore demo uses a separate `local-demo` scope. Account changes rebuild the navigation state, cancel old record observation and reset pending planning work. Real Firebase sign-in still requires the team configuration and device verification.

## Validation on 11 October 2026

- Debug app and Android test APK compilation passed.
- All 31 JVM tests passed, including 9 new statistics tests: daily filtering/user isolation, Monday week boundaries across years, leap February, empty ordinary February, month navigation, nonpositive durations, overflow/goal handling, edits/deletes and recorded-date semantics.
- Android lint: zero errors, 14 warnings and 2 hints.
- Device UI tests and visual verification of the new date controls/chart have not run. No new device-run success is claimed.

## Integration on 14 October 2026

Development continues on `member-d-integration` in a separate checkout, merging school main `c429794` into the statistics history. This includes reviewed reminder fixes (MR !2) and Member C Room persistence (MR !3). The only merge conflict was the Statistics screen; daily/weekly/monthly controls are retained and the real Room stream supplies the records. No remote branches have been changed by this local integration.

A new host Compose/Room test checks daily and previous-month totals with stored records and a second user's records. Existing Room UI tests cover edit/delete updates to weekly statistics. Validation of the integrated branch: debug APK and Android test APK built; all 57 host tests passed (zero failures/errors/skips), including the new Compose/Room period test and existing CRUD statistics test; lint completed successfully. Robolectric ran with a workspace-local user.home to allow dependency cache writes. No physical-device or emulator run is claimed. The 11 October results above are historical.

After integrating A branch `cde9ba3`, all 176 host tests passed and both APKs built; lint reported zero errors, 25 warnings and 2 hints. A new account-switch test verifies separate Room totals for two Firebase-style UIDs, reminder scope changes, and return to Login on logout. The test injects authentication; real Firebase credentials and device checks remain pending. A stale login-success navigation bug and an asynchronous preference-save account race were corrected during integration.
