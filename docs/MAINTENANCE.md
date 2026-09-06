# 代码维护导航

本文从“要改什么”定位源码。类和方法旁的中文注释描述当前实现的职责、调用顺序和边界；历史问题与本轮修复验证分别见 [审查报告](CODE_REVIEW_2026-09-06.md) 和 [修复记录](FIXES_2026-09-06.md)。修改行为时应同步更新相邻注释及相关测试，避免注释继续描述旧行为。

## 1. 从这些入口阅读

| 入口 | 职责与后续调用 |
| --- | --- |
| [App](../app/src/main/java/lins/applications/mychinesecalender/App.kt) | 进程初始化 → 基础日志 → 日期广播 → 已有组件调度恢复；后台启动进程也会经过这里 |
| [MainActivity](../app/src/main/java/lins/applications/mychinesecalender/MainActivity.kt) | 装配页面与 ViewModel，STARTED 时检查日期并尝试组件同步 |
| [MainViewModel](../app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt) | 选择今天、管理加载/订阅、取消旧日期请求，独立维护诗词状态 |
| [MainContent](../app/src/main/java/lins/applications/mychinesecalender/ui/content/MainContent.kt) | 页面状态订阅、照片选择器与纯展示组件，业务请求通过回调进入 ViewModel |
| [MyAppWidgetReceiver](../Appwidget/src/main/java/lins/applications/appwidget/MyAppWidgetReceiver.kt) | 桌面组件添加、更新、删除生命周期，协调后台任务并保留 Glance 父类处理 |
| [MyAppWidget](../Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt) | 订阅日期、仓库和头像版本，组合实际尺寸的组件内容 |

依赖方向是 `app → Appwidget / Module_Poem / Module_Base`，两个功能库再依赖 `Module_Base`。基础库不反向依赖页面或小组件；新增共享逻辑应通过接口或参数注入使用者需要的能力。

## 2. 日期、缓存与失败状态

主页和小组件的数据来源不同，分别是完整黄历与 HKO 轻量农历，由 [CalendarRepositories](../Appwidget/src/main/java/lins/applications/appwidget/data/CalendarRepositories.kt) 装配成两份共享的 [DailyRepository](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。它们共用加载流程，但各有互斥锁与状态表。

普通加载路径为：精确读取指定日缓存 → 尝试复用同日内存结果 → 必要时联网 → 校验 → 写库/清理 → 发布状态。强制刷新会尝试联网；请求失败时仍保留原先有效的同日数据。

维护这条链路时保留以下约定：

- “今天”由 [CalendarDates](../Module_Base/src/main/java/lins/libs/module_base/time/CalendarDates.kt) 使用设备当前时区计算。下一天从当地日历日的起点计算，不是固定增加 24 小时。
- `DateLoadResult.date` 是请求身份。不要以响应完成时间重新标注日期，也不要用最后一条记录兜底今天。
- `data` 与 `error` 可以同时存在，表示刷新失败但已有同日数据。`cacheError` 表示缓存操作失败，有效网络数据仍可展示。取消异常继续传播，不作为普通错误提示。
- `observe` 只订阅，不主动联网。内存加载状态存在时优先于数据库观察值，因此新业务写入应经过仓库，不绕过仓库直接写 DAO。
- 同一仓库的加载全程串行，普通并发请求在前一次成功后复用缓存；多个 `force` 请求并不会自动合并。页面和组件点击入口还各有重复操作保护。
- 缓存清理发生在写入路径，保留当前日期前 7 天到后 2 天的闭区间，不是独立定时清理。改变预取范围时同步调整清理窗口。

完整黄历要求公历字段能解析且等于请求日、农历非空，宜忌可缺省。HKO 没有可对照的公历字段，日期身份来自请求参数，只能检查农历核心字段完整。新增接口字段或更换数据源时，检查 [模型校验](../Module_Base/src/main/java/lins/libs/module_base/model/CHNDate.kt)、[统一 JSON 配置](../Module_Base/src/main/java/lins/libs/module_base/network/NetworkJson.kt) 和两个网络适配器，不能把“JSON 解析成功”当成业务成功。

## 3. 小组件从触发到显示

[WidgetScheduler](../Appwidget/src/main/java/lins/applications/appwidget/helper/WidgetScheduler.kt) 统一安排三类任务：30 分钟周期兜底、下一本地午夜的非精确闹钟，以及合并密集事件的即时任务。只有存在实际桌面实例才维持调度，最后一个实例删除后取消。

触发后由 [SyncDateWorker](../Appwidget/src/main/java/lins/applications/appwidget/worker/SyncDateWorker.kt) 调用 [WidgetDataSyncHelper](../Appwidget/src/main/java/lins/applications/appwidget/helper/WidgetDataSyncHelper.kt)：先请求展示本地状态 → 加载今天 → 检查请求期间是否换日 → 再请求所有组件更新 → 成功后尽力预取明天。广播只排队，不在接收窗口里等待网络。

Glance 活跃会话不能依靠重复调用 `update` 一定重进 `provideGlance`。日期与头像必须在 `provideContent` 内持续收集；换日使用 `flatMapLatest` 切换日期订阅。新增状态也应进入这条可观察链路，而不是只在入口读取一次。

`WidgetUpdateResult` 表示应用侧更新请求的结果，不代表宿主已经绘制完成。单实例失败继续处理其他实例，枚举失败单独记整体失败。系统省电、强行停止和宿主行为仍可能延迟显示，计算正确不等于零点准时刷新。

小组件外观采用用户在 `09ec2be` 精调的原有布局，入口与布局常量在 [MyAppWidget](../Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt)，另见 [Provider 配置](../Appwidget/src/main/res/xml/my_app_widget_info.xml) 和 [Debug 预览](../Appwidget/src/debug/java/lins/applications/appwidget/WidgetPreviews.kt)。胶囊边距、头像比例、字号公式、文字顺序、对齐和尺寸分支均属于已确认的视觉设计，逻辑修复与注释维护不得顺带调整。只有用户明确要求改外观时才修改这些参数。预览源码留在 `src/debug`，其依赖不进入 Release。

## 4. 头像与诗词

头像流程位于 [WidgetImageManager](../app/src/main/java/lins/applications/mychinesecalender/util/WidgetImageManager.kt)：选择器返回 URI 后立即取请求序号 → 在 IO 解码并限制最长边 → 居中裁剪圆形 → [LatestImageWriter](../Appwidget/src/main/java/lins/applications/appwidget/helper/LatestImageWriter.kt) 提交 → 发布头像版本变化 → 请求组件更新。

请求序号必须在启动后台工作前取得，代表用户真实选择顺序。编码互斥限制内存峰值，短临界区保护序号检查与最终替换。过时请求不能提交；最新请求失败保留原目标文件，不自动回退到已经淘汰的旧请求。输入位图与圆形结果由保存流程释放，正在被 Glance 使用的位图不能提前回收。这是进程内写入协调，不是后台持久任务或跨进程锁。

诗词由 [PoemRepository](../Module_Poem/src/main/java/lins/libs/module_poem/repository/PoemRepository.kt) 独立处理：优先复用 DataStore Token，HTTP 401/403 时清除并最多重试一次。其他失败返回 null，由页面保留旧诗词并显示错误。响应模型中的 `token` 当前不会写回 DataStore，持久 Token 来自专门的 token 接口。新增诗词调用入口时应考虑仓库本身没有请求互斥。

## 5. 修改需求对应哪些文件与测试

| 修改需求 | 重点文件 | 需要保持的验证 |
| --- | --- | --- |
| 更换黄历/HKO 接口或字段 | `ChineseCalenderRepository`、`HkoRepository`、模型、`NetworkJson` | `RemoteRepositoryTest`、`CalendarValidationTest`：请求参数、额外字段、错误状态、错日与残缺响应 |
| 修改缓存优先级、保留范围 | `DailyRepository`、`CalendarRepositories`、两个 DAO | `DailyRepositoryTest`：并发、离线、跨日、落盘恢复与活跃订阅 |
| 修改前台加载或手动刷新 | `MainActivity`、`MainViewModel`、`MainContent` | `MainViewModelTest`：换日取消、前台恢复、错误保留与数据库故障 |
| 修改日期或后台时机 | `CalendarDates`、`WidgetScheduler`、`DateChangeReceiver`、`SyncDateWorker`、Manifest | `CalendarDatesTest`，另在设备验证时区、午夜、重启、Doze、最后一个组件删除 |
| 修改图片处理；用户明确要求时修改布局 | `WidgetContent`、原有 Small/Medium/Max 布局、`ImageGeometry`、`WidgetImageManager`、`LatestImageWriter` | `WidgetBehaviorTest`、`LatestImageWriterTest`；图片逻辑修复应保留原有组件外观 |
| 修改数据库结构 | `AppDataBase`、Entity、DAO、schemas | `DatabaseMigrationTest` 在设备上实际运行，验证 v2/v3 升级与重复数据保留策略 |
| 修改主题 | `ui/theme`、页面语义配色、GlanceTheme | 页面与组件分别验证日/夜模式、大字体；二者主题入口不同 |
| 修改发布或工具链 | 根/模块构建脚本、版本目录、ProGuard、CI | Debug/Release 均构建，核对反射入口及已有任务的持久化类名 |

Room 结构升级必须增加版本号并追加迁移，现有安装通过 `2→3→4` 升级。不要只修改导出的 JSON，也不要随意修改 `chn_calendar.db` 文件名。Glance/WorkManager 类名、唯一任务名、头像文件名和 DataStore 名称/键名同样影响已有安装，重命名需要单独考虑兼容路径。

## 6. 日常验证与排查

完整项目检查：

```sh
./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest lintDebug :Module_Base:assembleDebugAndroidTest
```

连接 Android 12+ 设备/模拟器后，实际执行数据库升级测试：

```sh
./gradlew :Module_Base:connectedDebugAndroidTest
```

本地 JVM 测试通过可注入日期、虚拟协程时间、内存缓存与 MockEngine 重现边界，不访问线上接口。`DatabaseMigrationTest` 使用测试专属数据库文件并在结束后清理。当前 CI 编译仪器测试 APK，但不运行设备测试，也不能证明目标 One UI 已正确绘制。

排查时先区分故障阶段：没有数据看接口/日期校验；有数据但 `cacheError` 看数据库与磁盘；仓库数据正确但组件不变化看订阅、更新请求和宿主；重启后头像/Token/任务丢失看持久化身份是否被改名。业务日志经 `Logger` 输出，日志初始化见 `MyApplication`；Debug HTTP 日志可能包含 Token，不应原样外传，Release 保持关闭请求体日志。

只修改注释时，可以先对照去除注释后的源码并编译；修改行为时再选择上表对应测试，避免把“编译通过”当成业务行为已经验证。
