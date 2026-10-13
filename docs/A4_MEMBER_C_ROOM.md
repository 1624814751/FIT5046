# Member C — Room 与运动记录 CRUD 交接

## 本阶段范围

在 `member-c-dev` 实现本地运动记录的新增、查看、编辑、删除。首次运行数据库为空，不导入 A2 的虚构历史。History、Search 的历史搜索、D 的现有每周 Statistics 读取同一份 Room 记录流。退出演示账户不删除已保存记录。

本阶段不等于 Member C 所有 A4 工作完成：CSV 传感器播放、运动强度解释、安全提示和真实运动计时仍待后续开发。A 的真实认证、天气，以及 D 的日/月统计仍由对应成员完成。不要把目前 Live 页的固定计时和心率当作真实测量。

## 数据流与逐文件学习地图

`Screen → WorkoutRecordsViewModel → WorkoutRepository → Room DAO → SQLite`

`SQLite 改变 → DAO Flow → ViewModel state → History / Search / Statistics`

| 文件 | 给小白的解释 |
| --- | --- |
| `shared/SharedModels.kt`、`ModuleContracts.kt` | 三个人共同约定的记录格式和接口，本次没有改它们。 |
| `data/local/WorkoutEntity.kt` | 一条记录在数据库里的样子。日期保存为 epoch day，开始时间为可空毫秒值，来源为枚举名称。 |
| `data/local/WorkoutDao.kt` | 真正执行 SQL 的入口：按用户查询、插入、修改、删除。Room 自动生成实现。 |
| `data/local/MoveSmartDatabase.kt` | 打开应用数据库；单例使用 applicationContext，不持有 Activity。 |
| `data/local/WorkoutValidation.kt` | 拒绝空活动名、无用户 ID、负记录 ID、非法时长、未来日期。 |
| `data/local/RoomWorkoutRepository.kt` | 把共享记录转换成数据库行，分配 ID，返回成功或失败，保留协程取消行为。 |
| `workout/WorkoutRecordsViewModel.kt` | 订阅记录流，暴露加载/错误/记录列表，提供重试和防重复写入。 |
| `navigation/MoveSmartApp.kt` | 将数据库数据和保存回调接进现有页面，只在成功后退出编辑页。 |
| `ui/screens/WorkoutScreens.kt` | 表单、历史列表、搜索与删除确认；保存期间禁用写入按钮，错误保留输入。 |
| `model/PrototypeState.kt` | 只保留暂时的个人资料/偏好等 UI 状态，不再保存第二份运动记录。 |
| `app/schemas/.../1.json` | Room 导出的 schema v1，后续数据库升级时用于编写和检查迁移。 |

DAO 中的 `userId` 条件是数据隔离，不是真实身份认证。只有 A 接入认证后，界面中的用户 ID 才有真实账户含义。

## 业务规则

- 新记录 `id=0`，由 Room 生成正整数 ID；编辑必须使用已有 ID，不会偷偷插入另一条。
- 标题去掉首尾空格；时长为整数 `1..1440`；日期不得晚于设备当地今天。
- 查询按日期降序、再按 ID 降序。修改、删除不存在或其他用户的记录返回失败。
- 保存失败留在编辑页；删除失败不从界面伪移除记录；读取失败显示 Retry，不显示假记录。
- 写入互斥，重复保存/删除被拒绝；写入过程中阻止返回退出造成二次导航。
- `source=DEMO`：Live 页的模拟会话。手工输入使用 `LIVE` 表示用户实际输入，**并非传感器已验证**；编辑保留原来源与开始时间。
- 不使用破坏性迁移，不在退出时清空数据；卸载/清除应用数据会删除本地数据库。A2 暂存历史不作真实记录导入。

## A 与 D 如何接入

### Member A

目前入口 `MoveSmartApp(workoutUserId = "local-demo")` 是显式演示适配器。接入真实账户后，传入 `UserAccount.userId`，不能使用姓名或可编辑 email 冒充身份。真实账号不要查询 `local-demo` 的记录，不要自动把模拟记录转移到真实账户。

`RoomWorkoutRepository(MoveSmartDatabase.get(applicationContext))` 实现已约定的 `WorkoutRepository`。可用 `observe(userId)`、`get(userId,id)`、`upsert(record)`、`delete(userId,id)`；`Result` 失败需要处理。

提醒账户仍沿用 D 的现有适配器；A/D 完成认证接线时应统一提醒与运动记录的真实 userId。不要把本阶段当作这项接线已经完成。

### Member D

当前 weekly Statistics 接收从共享记录转换的旧 `model.WorkoutRecord` 列表。转换只用于现有图表 UI，不是第二个数据仓库。接入你新的统计分支时，继续订阅同一 `WorkoutRepository.observe(userId)`，按日期筛选，不再调用 `sampleRecords()` 或维护独立记录列表。

本次未改你的图表算法，只改数据输入与说明文案。导航文件有必要的 C 模块接线，合并时请保留双方变化，不整份覆盖。

## 自动化验证与设备检查

验证结果以本文末尾的验证记录为准。数据库测试使用真实 Room 生成的 SQL，运行于 Robolectric host Android 环境；ViewModel 使用可控 repository 检查状态和失败分支；Compose CRUD 测试使用独立内存数据库进行页面操作，返回键测试专门用门控写入验证导航。它们不是实体设备测试。

`WorkoutDatabaseIntegrationTest` 使用唯一命名文件验证 reopen、CRUD 和用户隔离。旧 `PrototypeUiTest` 改用 debug-only `PrototypeTestActivity`，其独立测试数据库通过 retained ViewModel 跨 Activity 重建保留，测试结束只清理自己创建的文件，不触碰生产数据库。

在本项目目录运行（首次需要网络下载依赖）：

```powershell
$env:JAVA_HOME = 'D:\Android Studio\jbr'
.\gradlew.bat --no-daemon --no-configuration-cache '-Dorg.gradle.java.installations.paths=D:\Android Studio\jbr' '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

连接正常的 API 36+ 设备或模拟器后，执行 `:app:connectedDebugAndroidTest`。不要通过 Wipe Data 删除现有演示记录来“让测试通过”。

手工验收：Explore demo → History 首用为空 → Add 30 min → Statistics 本周增加 30 → Edit 为 45 → 搜索历史能找到 → 强制停止并重启，45 min 仍在 → Delete 确认，History 和统计一起减少 → 退出再进入，剩余记录仍在。再检查 0/1441 时长和未来日期不能保存。

## Git 交接

代码在本地 `member-c-dev`，保留原有 `.idea` 改动，不把它们混入功能提交。不直接修改/合并 main，不自动推送。本地验证与提交结束后再运行 `git push origin member-c-dev`，随后请 A/D 审核 MR。保持原提交作者，避免 squash 淹没贡献证据。

设计/计划提交：`9c44c93`；功能提交：`8ddd8af`（22 个文件）。本文件与 AI-use fragment 在独立文档提交中交接。推送前确认当前目录是 `MoveSmart-GitLab`，不是旧 GitHub 项目 `MoveSmart`。

## 本次验证记录

2026-10-11，本地 JBR 21 / Gradle 9.5.0：

- `testDebugUnitTest`：47 tests，0 failures/errors（原有 22，新增 25）。其中真实 Room SQL/持久化 9 项、校验 2 项、ViewModel 8 项、Compose 页面回归 6 项。
- `assembleDebug`、`assembleDebugAndroidTest`：APK 成功生成；设备测试源码已编译。
- `lintDebug`：0 errors、14 warnings、2 hints。剩余提示在原有目标 SDK、页面/资源和提醒代码；没有为本阶段关闭 lint 或加入错误 baseline。
- 默认开启 configuration cache 的 `assembleDebug` 成功并保存缓存条目。
- 独立只读审核发现的返回键顺序、设备 UI 测试数据库隔离和异步等待问题已修复；返回键回归先失败，再通过。
- `adb devices -l` 没有连接设备；**未执行设备 instrumentation 测试，也未宣称实际模拟器/实体设备操作通过**。这是 main 合并前的后续验收项，不妨碍先 push 功能分支供队友审核。

TDD 记录：数据库/校验 stub 首次 11 tests 中 8 failed；实现后该 11 项通过。ViewModel stub 8 项中 7 failed；实现后 8 项通过。最初 Compose 5 项全失败；接线后逐步修复界面/测试等待，最终加入返回键回归后 6 项通过。原测试记录存在保存提示遮挡小屏点击的情况，测试通过 Dismiss 语义关闭提示后继续，不用固定 sleep 假装数据库已保存。

本地 SDK 路径的冒号转义已修正，`local.properties` 仍被 Git 忽略。lint 对它保留旧缓存时，通过仅重新运行 lint 分析/报告验证修正结果。一次尝试 StandardTestDispatcher 的 host UI 测试出现滚动死循环，已停止该测试 worker 并撤销该实验配置；没有修改或删除用户模拟器数据。

报告位置：`app/build/reports/tests/testDebugUnitTest/index.html`、`app/build/reports/lint-results-debug.html`。构建报告/APK 是本地产物，不提交进 Git。
