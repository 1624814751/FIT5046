# Member D statistics milestone

The Statistics page now supports day, Monday-to-Sunday week, and calendar month views. Users can choose a date, move to the previous/next period, and return to today. The selected date and period survive activity recreation. Changing the user resets these selections.

Each view reports total minutes, session count, active days and a daily minutes chart. Empty periods have an explicit empty state. Weekly goal progress is shown only in week mode and handles unset or exceeded goals. The monthly chart scrolls horizontally so each date has a readable label and an accessibility description.

`statistics/ActivityStatistics.kt` calculates totals from the supplied shared `WorkoutRecord` model. It filters by user ID and inclusive calendar date bounds, counts only positive-duration records, fills missing days with zero, and uses Long totals. The recorded `date` controls grouping; `startedAt` is not reinterpreted in the device's current time zone.

## Integration boundary

`ActivityStatisticsScreen` accepts shared records and the current user ID. Member C's `WorkoutRepository.observe(userId)` can supply those records after the repository and authenticated session are connected. Loading and repository failure handling must be implemented at that integration boundary; the current screen receives an already available list.

The existing navigation still calls `StatisticsScreen`, an explicit adapter from the A2 in-memory History list to shared records for `local-demo`. Sample-data labeling remains visible. Updating/deleting History entries updates statistics, but this does not add Room persistence or real authentication.

## Validation on 11 October 2026

- Debug app and Android test APK compilation passed.
- All 31 JVM tests passed, including 9 new statistics tests: daily filtering/user isolation, Monday week boundaries across years, leap February, empty ordinary February, month navigation, nonpositive durations, overflow/goal handling, edits/deletes and recorded-date semantics.
- Android lint: zero errors, 14 warnings and 2 hints.
- Device UI tests and visual verification of the new date controls/chart have not run. No new device-run success is claimed.

Development is isolated on `member-d-statistics`, based on `4f1f056`. This branch contains the statistics milestone separately from the school-only README merge commit `bccd37b3`; integrate the reviewed main history before proposing the next merge. Publishing this separate branch does not update the existing school Draft MR !1. Android Studio may create local `.idea` changes when opening the project; those are separate from this feature.
