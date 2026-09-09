# MoveSmart Section 4 5 6 draft

以下英文草稿对应本轮 prototype。Section 4/5 要与全组 Section 1–3 合并计入 1200 words；Section 6 不计入。Week 6 seminar 未提供，请在采用 Section 4 前逐条核对课堂原始 guideline。不要把下列 A4 计划描述为已经实现。

## 4 Design Guidelines

**Password guideline 1 — Keep passwords private but checkable.** Login and Sign up mask the Password field by default. A labelled eye button lets users show or hide their entry to check typing errors.

**Password guideline 2 — Explain requirements before submission.** Sign up places the passphrase requirement beneath Password and marks an insufficient entry when Create demo account is selected. The prototype uses a minimum of 12 characters; the final policy will be agreed by the team.

**Form guideline 1 — Use persistent, specific labels.** Reminder settings labels Reminder name, Time and Repeat on. Add/Edit workout labels Duration (minutes), making the expected unit explicit.

**Form guideline 2 — Match the control to the input.** Reminder settings uses a time input and selectable weekday chips. Preferences uses exposed dropdowns, while Add/Edit workout uses a date picker, reducing avoidable input errors.

**Form guideline 3 — Make errors actionable and preserve entries.** Reminder settings explains missing names or repeat days when users save; Add/Edit workout specifies the valid duration range. Entries remain editable, and successful saves provide feedback.

## 5 Advanced Features

**WorkManager — background context maintenance (member B, A4).** WorkManager will refresh weather and summarise stored activity data when the app is not visible, supporting recommendations when users return. We will learn worker creation, scheduling constraints and unique-work management from Google's implementation guides (Google, n.d.-b, n.d.-c, n.d.-d). Network constraints and retry backoff will handle unavailable weather services. Periodic work is deferrable and has a minimum 15-minute interval; the short-interval dataset simulator will therefore run separately while the app is active. This separates interactive context changes from persistent background maintenance.

**AlarmManager — user-selected workout reminders (member D, A4).** Reminder settings will supply the local time and repeat days for an AlarmManager schedule. A receiver will post a notification that opens the relevant app destination. We will use the scheduling, notification and permission guides to implement cancellation/rescheduling, notification channels and denial handling (Google, n.d.-a, n.d.-e, n.d.-f). Inexact alarms will be preferred for flexible fitness reminders. Any justified exact scheduling will require the applicable permission checks. Reboot and time-zone changes must reschedule the next occurrence. These reminders support routine building without claiming that the A2 preview sends notifications.

## 6 References

Google. (n.d.-a). *Create a notification*. Android Developers. https://developer.android.com/develop/ui/compose/notifications/create-notification

Google. (n.d.-b). *Define work requests*. Android Developers. https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work

Google. (n.d.-c). *Getting started with WorkManager*. Android Developers. https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started

Google. (n.d.-d). *Managing work*. Android Developers. https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work

Google. (n.d.-e). *Notification runtime permission*. Android Developers. https://developer.android.com/develop/ui/compose/notifications/notification-permission

Google. (n.d.-f). *Schedule alarms*. Android Developers. https://developer.android.com/develop/background-work/services/alarms

### 中文采用说明

- 三个 WorkManager sources 是 b/c/d；三个 AlarmManager/Notification sources 是 a/e/f。每条都实际用于说明，不只把链接堆在参考文献中。
- 已在 2026-09-09 打开核对以上六个官方页面。这里用 n.d.，因为没有用页面页脚的更新日期冒充原始发表日期；若团队采用可核实的发布日期，请同步调整文内和 list 的年份/字母。
- a–f 按同一作者、同一年份的标题字母顺序排列。全组增加其他 Google 来源后，须重新统一字母后缀。
- 这六条技术来源不能替代 SDG、公共数据集、用户需求和课程 guideline 的引用。
- Section 4 的五条是可讨论、可定位的 draft；不声称这些措辞已经核对过未提供的 Week 6 课件。
- Reminder Settings 的 TimeInput 是 Material3 的 time-picker 输入形式，不是 AlarmManager；Statistics 的数据为共享 mock records，不是 Room 查询。
