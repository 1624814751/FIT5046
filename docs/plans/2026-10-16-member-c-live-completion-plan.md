# Member C Live Workout Completion Plan

1. Add failing tests for authenticated Live Activity access and saving under the current Firebase UID.
2. Add a failing test that repeated finish actions cannot create duplicate records.
3. Replace the authenticated Live placeholder with the shared Live Activity flow while retaining dataset-disclosure text and `DataSource.DEMO`.
4. Add a failing classifier test for a provisional, non-UNKNOWN band before a complete measurement window.
5. Implement a transparent dataset-label fallback that is replaced by magnitude-based window classification.
6. Run existing Search and Room CRUD tests and repair only Member C regressions.
7. Run the complete JVM, debug APK, Android-test APK, and lint verification suite.
8. Commit the implementation on `member-c-dev`, push it to GitLab, and prepare a concise review hand-off for Members A and D.
