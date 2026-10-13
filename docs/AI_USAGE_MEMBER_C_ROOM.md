# Generative AI usage — Member C Room milestone

This is a usage-record fragment for this development milestone, not a claim that the team's complete A4 AI acknowledgement or final submission is ready. Retain the original conversation and combine it with the other members' records in the format required by the assessment.

## Tool and scope

OpenAI Codex was used to inspect the existing MoveSmart Android project, propose a Room persistence design, generate and revise Kotlin code and tests, diagnose build/test failures, prepare integration notes, and conduct an AI-assisted read-only code review. The working branch was `member-c-dev` in the school's GitLab clone. No claims are made that these changes were wholly unaided student work.

## Student prompts used in this milestone

English translations are labelled as translations, not original English prompts:

1. Original: “开始在本地`member-c-dev` 开发，从 **Room 数据库和运动记录增删改查** 开始”
   Translation: “Start local development on member-c-dev, beginning with the Room database and workout-record CRUD.”
2. Original: “全按你推荐的走，一直做到该push到gitlab上为止”
   Translation: “Follow your recommendations and continue until the point when the work is ready to push to GitLab.”

The conversation also contains the design discussion and approval. The assistant interpreted the second prompt as completing local implementation, verification and commits, stopping before the remote push or main-branch merge.

## AI output and use

- Proposed an empty initial database, user-scoped queries, durable dates/IDs and a single observed record flow for History, Search and existing Statistics.
- Produced design and implementation-plan Markdown documents.
- Generated Room entity, DAO, database, validation and repository code implementing the team's existing `WorkoutRepository` contract; generated the records ViewModel and screen wiring.
- Generated host database/ViewModel/Compose tests and a device database integration test. Added a debug-only, isolated UI-test Activity.
- Investigated KSP compatibility using official Android/KSP sources, then pinned KSP 2.3.10 without replacing the team's Android Gradle Plugin.
- Diagnosed a small-host-screen snackbar obstructing a test click. An independent AI review identified return-handler ordering and the need to isolate legacy UI tests from persistent application data; the implementation and tests were revised.
- Prepared the Member C handoff/learning map and this AI-usage fragment. The acknowledgement text itself is therefore also AI-assisted.

This summary does not replace the original prompts, responses, code diffs and test logs where a complete usage record is required. Do not describe summarized outputs as verbatim transcripts or fabricated screenshots as original conversations.

## Adaptation, review and limitations

The implementation was adapted to the team's existing shared models and callback-based navigation rather than replacing the whole project. Simulated Live sessions remain marked `DEMO`, and `local-demo` is explicitly not real authentication. The existing D-owned weekly chart remains in place; sensor playback and actual timing are not implemented in this milestone.

Automated host verification does not establish that device instrumentation tests or the complete A4 rubric have been satisfied. Use the actual verification section of `A4_MEMBER_C_ROOM.md` for outcomes. Member C must read and understand the code, review the proposed rules and evidence, complete any device checks, and explain their contribution. This document does not assert that student review, manual adaptation or final acceptance has already happened.

## Primary technical references consulted

- Android Room releases: https://developer.android.com/jetpack/androidx/releases/room
- Built-in Kotlin migration: https://developer.android.com/build/migrate-to-built-in-kotlin
- KSP releases: https://github.com/google/ksp/releases
- Robolectric supported Android/JDK versions: https://robolectric.org/compatibility_table/

Local design/plan commit: `9c44c93`. Functional implementation commit: `8ddd8af`. Verification: 47 host tests passed; debug and instrumentation APKs built; lint had zero errors (14 warnings and 2 hints). No device was connected, so instrumentation execution is not verified. Record these results, the final handoff-document commit and the original conversation when preparing the team's complete acknowledgement.
