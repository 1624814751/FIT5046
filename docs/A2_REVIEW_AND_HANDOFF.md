> Historical audit from 9 September. The current integrated code and branch sources are documented in [TEAM_INTEGRATION.md](TEAM_INTEGRATION.md). Statements below about missing A/B code predate the 11 September integration.

# MoveSmart A2 审阅与 D 交接

审阅日期：2026-09-09。项目：Context-aware 智能健身推荐 App。本文是实施审阅及交接资料，不是可直接替代整组 Proposal PDF 的成品报告。

## 已核对的证据与分支

- 原始 brief：FIT5046-Assessment2-AndroidAppDesignProposal-2026-S2-v2.pdf，共 10 页。
- 原始 rubric：FIT5046-Assessment2-Rubric-Marking Guide-2026-S2-v2.pdf，共 2 页。
- 原始 FIT5046-Task Allocation Form.docx：一页，任务栏和成员栏都为空。该附件是模板，不能作为已经签定的 A/B/C/D 分工证据。
- 本轮按用户明确给出的 D 分工及所引用对话中的 A/B/C 规划核对。真实成员姓名、学号和同组确认仍需补入正式表。
- GitHub 仓库：https://github.com/1624814751/FIT5046 。读取时只有 `origin/main` 与 `origin/member-c-dev`，都在 `6534891`。GitHub 默认分支是 `member-c-dev`，但本轮改动前已从 `origin/main` 建立独立 `member-d-dev`。
- 本轮独立开发于 `member-d-dev`，推送目标为同名远端分支 `origin/member-d-dev`；后续通过 PR 将团队确认的改动合入 `main`。

## A2 硬性要求

| 要求 | 原文位置 | 本项目的落实或待办 |
|---|---|---|
| 四人同一 lab；9 月 11 日 2026 11:55 PM 截止，占 20% | brief p.1 | 核对组号、lab、成员；由一人提交 |
| 一个 A2-Lab0X-GNo.zip，包含 Proposal PDF、Task Allocation Form、完整 Android project ZIP | brief pp.2,7 | 代码交付不等于总提交完成；保留 Gradle wrapper 与项目配置 |
| 正文上限 1200 words；References 和 AI Acknowledgement 不计入 | brief p.2 | 合并各成员段落后统计总字数 |
| Appendix A 的 Section 1–7 和全部子标题齐全 | brief p.9 | D 草稿只覆盖 Section 4/5/6 |
| 有价值的应用领域、SDG、impact/value、段落式 user group，不能用 persona | brief pp.2–3 | 建议明确健康领域及 SDG 3，避免笼统“适合所有人” |
| 公开 sensory dataset、连续模拟 sensor 的 class 设计、多种 sensory/non-sensory context、动态调整或个性化 | brief pp.3–4；rubric p.1 | A2 报告必须说明；当前代码是手动 mock scenario，没有真实 dataset simulator |
| 六个指定组件 + 四个额外组件，逐项说明用途 | brief p.4 | 下方列出对照；Room、Retrofit 为 A4 接入计划 |
| 六个基本功能 + 两个有创意和复杂性的 app-specific 功能 | brief pp.4–5 | Screens 对齐功能；推荐和动态调整需要具体、可区分的解释 |
| 所有主要 UI 用 Compose，navigation 可运行；A2 不要求全功能 | brief pp.2,5 | 本轮补全整合原型；无需为 A2 加完整 Room/API/调度器 |
| 一张包含所有主要 emulator screenshots 和跳转箭头的总图 | brief pp.5–6,9 | 必须使用运行中的 Android emulator 截图，普通流程图不能替代 |
| 总图后的每个 screen 单独 screenshot + 一句用途；每页最多两张 | brief pp.5,9 | 对长表单可附滚动后的补充状态；每页仍遵守上限 |
| 2 条 password + 3 条 form guideline，基于 Week 6，标明 screen 和具体元素 | brief p.6；rubric p.2 | 下方草稿对应现有 UI；Week 6 seminar 未上传，须逐条核对课程措辞 |
| 从指定列表选 2 advanced features，每项至少 3 sources | brief pp.6–7 | WorkManager（B）、AlarmManager（D），详见 Section 5 草稿 |
| APA 文内引用及 References | brief p.9；rubric p.2 | brief 写至少 5 条，HD rubric 要求至少 6 条，按 6 条以上准备 |
| AI 使用声明、真实 prompts/responses、如何采用修改；最低预期 10 页 | brief pp.8–9；rubric p.2 | 本轮只是记录的一部分，不能编造旧 prompt 或人工修改经历 |

## HD rubric 对照

| 评分项 | 分值 | HD 所需证据 |
|---|---:|---|
| 1.1 Domain/SDG、1.2 Impact/value、1.3 User group | 各 5 | 清楚、合理、有论证、有细节，符合 brief，体现思考与创意 |
| 1.4 Context-awareness | 5 | 选定并研究公共 dataset；清楚解释 simulator、多种 context 和具体动态行为 |
| 2.1 Components、2.2 Functionalities、2.3 Screens | 各 5 | 基本和额外项目完整、合理、可实施；三者互相对应 |
| 3.1 Diagram/screenshots | 20 | 所有 Compose screens、全图箭头、清晰的逐屏截图与逻辑导航 |
| 3.2 UI/UX | 20 | 视觉吸引力、对齐、一致性、易用性、对状态与细节的考虑 |
| 4 Design Guidelines | 10 | 全部五条符合 Week 6；能指出具体 screen / element |
| 5 Advanced Features | 10 | 两项有价值、可实现，研究充分，六个相关来源 |
| 6 References | 5 | 至少六个高质量来源、正确文内引用、APA list |

“能运行”只满足基础，不自动获得 HD。40 分集中在界面、导航图和截图。Rubric 还写有：迟交每日扣 5%；缺文件/章节可能扣 5%–50%，缺 Android 项目至少可能扣 50%；AI 记录不充分可能扣 25%–50%，完全不声明可能为零分。Rubric 中 AI 条款使用了 “slides” 一词，brief 的实际提交物仍是 Proposal PDF，应将完整声明放入 Section 7。

## A/B/C/D 核对

| 成员 | 计划职责 | 仓库原有实现 | 本轮整合后的状态 |
|---|---|---|---|
| A | Login、Sign Up、Profile、Preferences；账户与偏好 | 未入库 | AccountScreens.kt 提供可替换 Compose mock；无真实认证/云端账户 |
| B | Home/context、天气 API、推荐、动态调整、WorkManager（A4） | 未入库 | RecommendationScreens.kt 提供天气场景开关及推荐原因；无 Retrofit/真实传感器循环 |
| C | Live Workout、Search、History、Room（A4） | MainActivity 内有 Live/History，按钮无行为，临时切页遮挡 | 抽成 WorkoutScreens.kt，修正布局，补 callback、Search 和 Add/Edit 原型；成员 C 仍负责后续真实记录层 |
| D | Reminder Settings、Statistics、Navigation integration；AlarmManager/Notification（A4） | 无 | 完成表单、周统计图、目标进度、全局 NavHost、统一 scaffold/theme、保存状态和集成验证 |

A2 报告责任：D 为 Section 4 Design Guidelines、5 Advanced Features、6 References。本轮补 A/B/C mock 是为导航完整性的整合工作，不表示重新分配队友的 A4 职责。正式 Task Allocation Form 要写 A4 开发任务，不要只写 A2 谁写哪段报告。

## Screens 与 functionality

| Screen / route | 对应功能与可演示路径 | 建议负责人 |
|---|---|---|
| Login / login | mock email/password 校验；Explore demo 进入 Home | A |
| Sign up / signup | display name、email、password 表单；进入 Home 后打开 Preferences | A |
| Home / home | 当前 context 与推荐摘要；进入 Plan、Search、Reminders | B |
| Preferences / preferences | 目标、强度、每周分钟数；更新推荐和 Statistics 目标 | A |
| Workout plan / plan | mock 推荐 + weather change 后的动态 indoor/outdoor 调整 | B |
| Live workout / live | sample timer、暂停状态、结束确认、写入 mock record 后跳 History | C |
| Search / search | Activities 与 My history 两类查询、无结果反馈；开始活动或编辑记录 | C |
| History / history | 展示、添加、编辑、确认删除记录；统计同步更新 | C |
| Add/Edit workout / record/{id} | 活动下拉、分钟数校验、DatePicker；新增与更新表单状态 | C |
| Reminder settings / reminders | 开关、标题、时间、repeat days、声音、校验、保存反馈 | D |
| Statistics / statistics | This week/Last week、每日分钟数柱状图、目标进度、active days/sessions | D |
| Profile / profile | 偏好与提醒入口、统计入口、确认退出并清空 demo session | A |

使用 Bottom Navigation（Home / History / Statistics / Profile），不再叠加 Navigation Drawer。二级页面有标题与返回键；登录页隐藏底栏。所有 screens 通过 callback 连接，队友换掉 mock implementation 时保留路由及函数接口。

两个独特功能应分别写清楚：

1. 初始 context-aware recommendation：输入天气、模拟活动信号、时间/目标/强度等，输出推荐活动和解释。
2. context 改变后的 adaptive adjustment：比较当前和新 context，在保留目标前提下调整既有计划，并说明改变原因。

当前 prototype 展示了“晴天户外 / 雨天室内”的变化；A4 的多个 context 输入、判定规则、公共数据集和异常数据处理还需要 B 明确设计。

## Components 对照

| 类型 | Component | A2 界面证据 / A4 用途 |
|---|---|---|
| 必选 | Room | A4 保存 workout records；Statistics 从 Room 聚合，A2 共享同一组 mock records |
| 必选 | Navigation | navigation-compose NavHost + Bottom Navigation |
| 必选 | Retrofit | A4 请求所选 weather API；报告需写 API 名称、数据字段和失败行为 |
| 必选 | DatePicker | Add/Edit workout 日期选择，阻止未来记录日期 |
| 必选 | Expanded Dropdown Menu | Material3 ExposedDropdownMenu，用于 Preferences/Activity |
| 必选 | LazyColumn | History 和 Search |
| 额外 | Time picker input | Reminder Settings 使用 Material3 TimeInput + rememberTimePickerState |
| 额外 | Switch | 提醒、声音、演示天气改变 |
| 额外 | Progress indicator | Statistics 每周目标，Live session 示例进度 |
| 额外 | FilterChip | Reminder repeat days、Statistics week、Search category |

这张表是 proposal component mapping，不能把 Room/Retrofit 写成已实现。

## UI/UX 审阅

本轮已处理：

- 移除截图用临时切页按钮；统一 Scaffold 的 system insets，避免内容与系统栏或底栏重叠。
- 保留 C 的 violet/lime/warm-neutral 风格，改为同一 Material3 主题，含独立深色配色。
- 表单可滚动、考虑键盘；长记录标题不会与横排图标争抢空间。
- 日期与时间使用可观察的 locale；时间显示遵守设备 12/24 小时格式。
- 提醒的 on/off 状态、时间取消、重复日期缺失、过长标题及保存反馈都有 UI。
- 统计柱有数值、单位、日期范围和读屏描述，不能仅靠颜色理解；进度限制在 0–100%。
- History 增删改和 Live 保存反映在 Statistics，含空数据反馈；退出重置 demo state。
- 明确标注 Sample/Preview，避免把静态心率解释成实时测量或安全诊断。

需要团队在最终合并后继续核对：每个可见按钮的行为、实际 emulator viewport、较大字号、dark theme、键盘展开、系统 Back、横屏、tab 返回、表单状态。当前 app 的 minSdk/targetSdk 延续原仓库 36，compileSdk 37.1；若老师环境不同，应由团队统一调整并重新测试，而非每人单独升级。

本轮验证：编译成功，Lint 0 errors（13 项继承的配置/模板 warnings），7 项新增逻辑测试通过，5 项新增 Pixel 9 / API 37 UI 测试通过。已提供 16 张真实 emulator 截图，包括深色与 1.3 倍文字的补充状态，详细记录见 VERIFICATION.md。

## 仍未满足或待确认的 A2 事项

1. **整组报告未提供**：Section 1/2 的实际正文、1200 words 总字数、全部标题不能宣称已完成。
2. **公共 dataset 尚未明确**：补具体来源、字段/单位、采样间隔、文件读取/循环方式、与天气和偏好的组合、调整示例。没有 dataset 的随机数示例不能替代该设计要求。
3. **后端与 API 方案尚未明确**：brief 概述要求 public web APIs 与 cloud-based services；团队需说明 Retrofit 的 API 和 Firebase/Firestore 等 backend 的实际用途。
4. **Task Allocation Form 为空**：补真实姓名、学号/组信息和 A4 责任；A/B/C 的已有代码是否在其他未共享位置需成员确认。
5. **Week 6 原始资料未提供**：Section 4 的五条草稿虽对应代码，仍要核对课堂给定 guideline；12 字符是原型选择，不是 brief 强制长度。
6. **Section 3 最终报告排版仍需整组完成**：使用最终合并版本的真实 emulator screenshots，包含总导航图和逐页两图以内的一句说明。后续 UI 改动后须重截。
7. **AI Acknowledgement**：收集至少预期 10 页的真实 AI 使用过程；只写“used ChatGPT/Codex”不够。团队需要说明自己审阅、修改、测试和学到了什么。
8. **References 与文内引用合并**：至少六个高质量来源；下面给出 D 的六个官方来源，但全组的 SDG/dataset/API/其他论断也需相应来源。
9. **最终提交包装**：一个人提交命名正确的总 ZIP，实际解压测试，不能只交 APK 或 GitHub 链接。

完整 Room、真实登录、Retrofit、AlarmManager/Notification、WorkManager、真实 sensor simulator 的代码不属于这次 skeleton prototype 必须补齐的运行功能；其设计说明仍是 A2 报告要求。

## 修改文件清单

- `gradlew`：修正 macOS/Linux 的 executable bit。
- `README.md`、`docs/VERIFICATION.md`：运行入口、验证结果和复现方法。
- `.gitignore`：排除本机构建产生的 `.kotlin/`。
- `app/build.gradle.kts`、`gradle/libs.versions.toml`：Navigation Compose 依赖、图标版本跟随 Compose BOM。
- `MainActivity.kt`：应用入口、edge-to-edge、统一 theme 与导航。
- `ui/theme/Color.kt`、`ui/theme/Theme.kt`：统一颜色及深色方案。
- `model/PrototypeModels.kt`：record/reminder/preferences 数据模型、week 聚合、校验和 fixtures。
- `model/PrototypeState.kt`：跨 screen 的 mock state 与 recreation saver，不保存登录密码。
- `navigation/MoveSmartApp.kt`：所有 routes、底栏、标题、Back、callback 和退出清理。
- `ui/components/Components.kt`：统一卡片、标题、按钮、滚动表单和 exposed dropdown。
- `ui/screens/ReminderSettingsScreen.kt`：D 的提醒表单。
- `ui/screens/StatisticsScreen.kt`：D 的周柱状图与进度。
- `ui/screens/AccountScreens.kt`：A 的 Login/Sign up/Preferences/Profile integration skeleton。
- `ui/screens/RecommendationScreens.kt`：B 的 Home/Plan integration skeleton。
- `ui/screens/WorkoutScreens.kt`：C 的 Live/History 提取和整合，Search/Add/Edit mock。
- `app/src/test/.../PrototypeModelsTest.kt`：周界线、聚合、进度、提醒边界等逻辑测试。
- `app/src/androidTest/.../PrototypeUiTest.kt`：导航、保存恢复、错误状态、退出和截图。
- `docs/A2_REVIEW_AND_HANDOFF.md`、`docs/REPORT_SECTIONS_4_5_6.md`、`docs/AI_USAGE_THIS_SESSION.md`：审阅、报告草稿与本轮真实使用记录。

## 后续提交与合并步骤

在本项目文件夹打开终端。先与 C 确认对 MainActivity 的提取，不要再把旧临时导航覆盖回来。

```sh
git switch member-d-dev
git status
git fetch origin
git log --oneline --left-right member-d-dev...origin/main
```

后续修改仍在 D 分支上检查并提交（无需创建或修改 member-c-dev）：

```sh
git add .gitignore gradlew README.md app/build.gradle.kts gradle/libs.versions.toml app/src/main/java app/src/test app/src/androidTest docs
git diff --cached --check
git diff --cached --stat
git commit -m "Complete member D A2 screens and prototype navigation"
```

如 main 在这期间有新提交，提交本地改动后，在 `member-d-dev` 执行 `git merge origin/main` 并处理冲突；不要 reset 或 force push 队友代码。特别检查 `MainActivity.kt`、theme、Gradle 和 navigation。冲突解决后运行：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二行要求已经启动 emulator。CLI 若缺省 Java 不可用，在 Android Studio Terminal 中使用内置 JDK；本轮采用 Android Studio bundled JBR 21。

```sh
git push -u origin member-d-dev
```

在 GitHub 建 PR：**base = main，compare = member-d-dev**，注意仓库默认分支是 C。让 A/B/C 核对 callbacks、命名、记录字段和截图后再合并。本次发布目标是 D 分支；合入 main 是团队确认后的下一步。

A2 交卷应从已整合确认的 main 导出完整源代码项目 ZIP，不含 `.git`、构建缓存、`local.properties` 或本机签名文件；保留 wrapper JAR、gradlew、gradlew.bat、Gradle 文件、manifest、所有源码与资源。最终总 ZIP 再包含 Proposal PDF 和填好的正式分工表。
