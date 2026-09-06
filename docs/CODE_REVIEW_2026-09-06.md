# MyChineseCalendar 代码审查报告

审查日期：2026-09-06。审查基线：`09ec2be`，开始审查时工作区干净。

本轮覆盖仓库全部 29 个生产 Kotlin 文件（2,453 行）、6 个测试文件，以及四个模块的 Gradle 配置、Manifest、XML 资源、混淆配置和 Gradle 启动脚本。仓库共有 105 个受版本控制的文件；图片等二进制资源检查其引用和打包配置，不属于逐行代码审查范围。

**结论：发现 15 项需要处理的代码问题，其中 4 项 P1、11 项 P2，另有 6 组优化建议和 3 项待设备验证的风险。最优先的是 Release 无法编译，以及小组件更新和日期准确性问题。** 现有工程具备模块划分、网络超时、缓存校验、诗词 Token 重试和图片临时文件替换等基础，但这些机制之间仍有断点。

本轮仅生成报告，没有修改业务代码、依赖版本或项目配置，也没有安装或发布应用。下文修复方向均为建议，等待你决定处理范围。

**优先级与证据口径**

- P1：阻断发布，或影响日历核心准确性，建议优先处理。
- P2：特定操作、异常或并发条件下功能错误，建议纳入后续修复。
- 优化建议：主要改善性能、维护和验证能力，不等同于已经发生的故障。
- “实测”指本机构建、现有 JVM 测试或明确说明的独立验证；“静态确认”指从现有代码和框架行为可以推导出触发路径，不表示已在手机上复现。
- 改动规模：小＝局部修改；中＝涉及状态、调度或数据库结构。不是工时承诺。

**问题总览**

| 编号 | 优先级 | 问题 | 证据 | 改动规模 |
| --- | --- | --- | --- | --- |
| R01 | P1 | Release 缺少 Glance Preview 依赖，无法编译 | 构建实测 | 小 |
| R02 | P1 | Glance 会话中只使用一次性快照，刷新后内容可能不变 | 静态确认、1.1.1 源码核对 | 中 |
| R03 | P1 | 跨天刷新依赖受限广播和 6 小时任务，无法保证及时切日 | 代码及 Android 文档核对 | 中 |
| R04 | P1 | 小组件无标记地显示其他日期的缓存 | 静态确认 | 中 |
| R05 | P2 | 主页跨天返回前台后仍保留旧日期 | 静态确认 | 中 |
| R06 | P2 | 小组件“暂无数据”状态没有重试入口 | 静态确认 | 小 |
| R07 | P2 | 数据库单例缺少锁内二次检查 | 静态确认 | 小 |
| R08 | P2 | 主页 HKO 加载中的数据库异常会逃出协程 | 静态确认，异常条件触发 | 小 |
| R09 | P2 | 黄历解析绕过统一 JSON 配置，新增字段即失败 | 实际模型解析实测 | 小 |
| R10 | P2 | 残缺黄历响应可覆盖有效缓存 | 实际模型解析、校验条件实测 | 小 |
| R11 | P2 | 小组件更新失败仍可能返回“刷新成功” | 静态确认，异常条件触发 | 小 |
| R12 | P2 | 小组件布局只按高度放大，窄、高或矮尺寸不可用 | 布局公式验证 | 中 |
| R13 | P2 | 非方形图片从左上角截取，头像主体容易被裁掉 | 静态确认 | 小 |
| R14 | P2 | 并发更换头像共用临时文件，存在覆盖和损坏竞争 | 静态确认，并发条件触发 | 小 |
| R15 | P2 | 黄历缓存重复追加，且“最后写入”不等于“今天” | SQL 实测、静态确认 | 中 |

**R01 · P1：Release 构建失败**

位置：[Appwidget/build.gradle.kts:47](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/build.gradle.kts:47)、[MyAppWidget.kt:42](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:42)、[预览代码:560](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:560)。

`MyAppWidget.kt` 位于 `src/main`，直接引用 `androidx.glance.preview.Preview` 和 `ExperimentalGlancePreviewApi`，相关库却声明为 `debugImplementation`。Release 编译时这些类型不可见。

执行 `:app:assembleRelease` 后，实际失败任务为 `:Appwidget:compileReleaseKotlin`，出现 `Unresolved reference 'preview'`、`Unresolved reference 'Preview'` 等错误。Debug 能构建不能覆盖这个问题。

建议把仅用于预览的函数和 import 移到 `src/debug`，继续保留 Debug 依赖；也可评估让预览注解库对主源码编译可见。验收必须包含 Release 构建。

**R02 · P1：刷新请求没有让存活中的 Glance 会话重新读取数据**

位置：[MyAppWidget.kt:75](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:75)、[WidgetDataSyncHelper.kt:59](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/helper/WidgetDataSyncHelper.kt:59)。

`provideGlance` 在调用 `provideContent` 前读取数据库和图片，之后始终把同一个 `snapshot` 传给 UI。组合内部没有订阅数据库、图片版本或其他可观察状态。

Glance 1.1.1 的本地依赖源码确认：会话仍运行时，`update` 发送更新事件，不会重新执行 `provideGlance`。因此，新建小组件后立即联网成功、短时间内更换头像，或在活跃会话中手动刷新，都可能继续展示旧日期、旧图片甚至“暂无数据”。会话结束本身也不等于自动执行一次新的数据读取，可能要等下次外部更新。

建议通过 Room Flow / Glance 状态保存并观察日期、图片版本和加载状态，初始读取后继续响应变化；外部写入仍调用 `update` 唤起未运行的会话。头像使用可观察的版本号或文件标识触发重新解码。框架行为参见 [GlanceAppWidget 官方说明](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/GlanceAppWidget)。

**R03 · P1：跨天自动刷新链路不可靠**

位置：[AndroidManifest.xml:40](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/AndroidManifest.xml:40)、[DateChangeReceiver.kt:28](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/receiver/DateChangeReceiver.kt:28)、[App.kt:27](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/App.kt:27)、[my_app_widget_info.xml:8](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/res/xml/my_app_widget_info.xml:8)。

`DATE_CHANGED` 仅在 Manifest 静态注册，工程没有动态注册入口。它不在 Android 8.0+ 隐式广播豁免清单中，不能据此保证后台跨天收到通知；`TIMEZONE_CHANGED` 在豁免清单中，二者不能等同处理。应用实际支持 Android 12+。参见 [Android 隐式广播限制及豁免](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)。

同时，小组件自身更新周期为 0，WorkManager 每 6 小时执行且要求联网，没有对齐当天结束时间。由现有调度可推导：午夜后日期可能保持数小时，系统延迟和断网会进一步延长。WorkManager 周期任务本来就不保证精确执行时间，参见 [周期任务调度说明](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)。手动修改系统时间使用的 `TIME_SET` 也未处理。

建议先确定产品可接受的跨天延迟，再组合前台日期校验、时间/时区变化处理和后台兜底；有条件时提前缓存未来日期或使用经过验证的离线历法。不要只把周期改成 24 小时就认为解决了零点更新。真实切日行为需在 One UI 8 上验收。

**R04 · P1：其他日期的农历被当作当前日期展示**

位置：[MyAppWidget.kt:78](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:78)、[LunarDateEntity.kt:24](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/src/main/java/lins/libs/module_base/model/LunarDateEntity.kt:24)、[WidgetDataSyncHelper.kt:80](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/helper/WidgetDataSyncHelper.kt:80)。

当天没有缓存时，读取逻辑直接回退到 `getLast()`。实体转换为 `LunarDateResponse` 时丢弃公历日期，UI 只显示农历且没有过期提示。假设只有 9 月 5 日缓存，9 月 6 日断网时，小组件会继续显示 9 月 5 日的农历。用户回调系统日期时，按日期倒序取出的记录还可能来自“未来”。同步失败又不会执行 UI 更新，桌面上的旧内容可以一直保留。

建议展示模型保留查询日期、获取时间和是否过期。当天数据缺失时明确显示未更新状态，并允许重试；如果保留旧值，要同时显示它属于哪一天。缓存保留 7 条并不能证明缓存属于今天。

**R05 · P2：主页只在 onCreate 加载，跨天恢复不会刷新**

位置：[MainActivity.kt:40](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainActivity.kt:40)、[MainContent.kt:58](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/ui/content/MainContent.kt:58)。

黄历仅在 `onCreate` 请求。用户夜间将应用放到后台，第二天从最近任务返回，如果 Activity 未重建，就仍显示昨天的内容；一直停留在前台跨午夜也没有更新入口。后台 Worker 更新的是 HKO 表，主页实际订阅的是另一份 `lunarDate`，无法自动弥补。当前页面也没有手动刷新黄历入口，只有刷新诗词。

建议把当前日期作为状态，在恢复前台和跨天时校验并刷新；提供明确的黄历加载、失败和重试入口。配置变化时也应根据日期和请求状态去重，避免每次重建重新请求。

**R06 · P2：首次失败后，“暂无数据”小组件不能点击重试**

位置：[MyAppWidget.kt:211](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:211)。

`date == null` 的分支只画文本并提前返回；点击刷新绑定在后面的 Small/Medium 布局上。首次添加时无缓存或离线，用户看到的恰好是唯一不能点击刷新的状态。联网后没有这个空态专属的立即重试机制，只能等待后台任务或打开应用。

建议空态同样绑定刷新，并显示加载/失败状态；与 R02 一起验证“空态 → 请求成功 → 内容出现”的完整链路。

**R07 · P2：数据库单例在并发首次访问时会创建多个实例**

位置：[AppDataBase.kt:63](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/src/main/java/lins/libs/module_base/database/AppDataBase.kt:63)。

当前形式为 `INSTANCE ?: synchronized(this) { 创建实例 }`，锁内没有再次检查 `INSTANCE`。两个线程都在锁外读到 null 后，会依次进入锁并各创建一个 Room 实例；前一个调用者仍持有前一个对象。`@Volatile` 能改善可见性，不能消除这个执行顺序。

主页并行加载黄历、HKO，Application 又启动 Worker，存在实际并发入口。影响包括重复数据库连接、资源浪费，以及未来使用 Room Flow 时跨实例通知的一致性问题；没有证据表明这一定导致数据库损坏。

建议使用标准双重检查或其他线程安全的单例初始化方式，让锁内先返回已经初始化的对象。

**R08 · P2：数据库故障可导致主页加载协程未处理异常**

位置：[MainViewModel.kt:90](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt:90)。

`getTodayLunarInfo` 中数据库初始化、查询和插入没有异常处理。迁移不匹配、数据库损坏或磁盘写入失败时，异常会逃出 `viewModelScope.launch`，走未捕获异常处理路径，可能使进程崩溃。HKO 仓库中的网络 catch 不覆盖这些操作。其他加载方法已有 catch，因此这一条入口尤其容易遗漏。

建议明确区分网络、缓存读写和协程取消：可恢复的缓存故障转成 UI 状态，保存失败时仍可展示网络数据，取消异常继续向上传播。本轮没有注入手机数据库故障；这里确认的是异常处理路径缺口。

**R09 · P2：黄历接口增加字段就会解析失败**

位置：[ChineseCalenderRepository.kt:50](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/data/ChineseCalenderRepository.kt:50)、[KtorClient.kt:28](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/src/main/java/lins/libs/module_base/network/KtorClient.kt:28)。

黄历仓库使用 `bodyAsText()` 后再调用默认的 `Json.decodeFromString`，没有使用 Ktor 中配置的 `ignoreUnknownKeys = true`。因此统一客户端“兼容扩字段”的配置对该接口不生效。

本机使用项目编译出的 `CHNDate` serializer 和当前依赖，输入 `{"公历日期":"2026年9月6日","新增字段":"test"}`，实际得到 `JsonDecodingException: Encountered an unknown key '新增字段'`。生产仓库会将其吞为 `CHNDate()`，随后保留旧缓存或显示未知日期。

建议使用统一 Json 实例或内容协商反序列化，并对 HTTP 状态做显式检查；容忍额外字段不等于允许核心字段缺失，后者需由 R10 处理。默认解析行为参见 [Kotlin JSON 配置文档](https://kotlinlang.org/docs/serialization-json-configuration.html)。

**R10 · P2：一个非空字段就能把残缺响应写成有效黄历**

位置：[MainViewModel.kt:126](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt:126)、[MainViewModel.kt:159](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt:159)。

`CHNDate.isValid()` 只要求九个字段中任意一个非空。实测 `{"宜":"测试值"}` 能正常解析并满足该校验，尽管公历和农历都为空。这样的响应会写入数据库并覆盖当前显示，破坏已有有效缓存；没有检查返回日期是否匹配本次查询。

建议至少校验公历/查询日期及农历等真正必要的字段，保留业务上允许为空的宜忌；不符合要求的响应作为失败处理。为查询日期建立独立、可比较的字段，避免依赖接口的展示字符串作为日期身份。

**R11 · P2：更新失败被吞掉，上层仍报告成功**

位置：[WidgetDataSyncHelper.kt:59](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/helper/WidgetDataSyncHelper.kt:59)、[RefreshAction.kt:53](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/action/RefreshAction.kt:53)、[SyncDateWorker.kt:25](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/worker/SyncDateWorker.kt:25)。

`updateAllWidgets` 捕获异常后仅记录日志，返回 Unit。`syncAndUpdate` 只根据网络结果是否非空返回 Boolean，因此一次已知的小组件更新异常仍会得到“刷新成功”，Worker 也会返回 success。此外 catch 包围整个遍历，一个实例失败会中断后续实例的更新。

建议区分数据同步成功和更新请求失败，逐实例处理异常并汇总结果；可恢复的更新请求失败应进入重试。Glance 更新请求完成并不必然代表宿主已绘制新内容，提示语也应符合实际可确认的状态。

**R12 · P2：尺寸自适应缺少宽度和字号边界**

位置：[MyAppWidget.kt:228](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:228)、[MediumWidgetLayout:284](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:284)、[my_app_widget_info.xml:5](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/res/xml/my_app_widget_info.xml:5)。

宽度达到 100dp 就使用带头像的中等布局；头像尺寸只取 `height - 39dp`，字体只按高度计算，没有结合文字可用宽度，也没有合理最小/最大字号。Provider 同时允许横向、纵向调整。

按现有常量计算，文字剩余宽度约为 `width - 54dp - avatarSize`，得到下列结果（这是几何验证，不是手机截图）：

| 小组件尺寸 | 头像直径 | 文字剩余宽度 | 计算字号 |
| --- | --- | --- | --- |
| 100 × 100dp | 61dp | -15dp | 20.4sp |
| 150 × 100dp | 61dp | 35dp | 20.4sp |
| 200 × 200dp | 161dp | -15dp | 62.9sp |
| 300 × 60dp | 21dp | 225dp | 3.4sp |

负剩余宽度意味着布局约束无法同时满足；35dp 也无法容纳正常两行农历文字。3.4sp 则几乎不可读。系统大字体会进一步放大问题。

建议根据宽高共同选择布局，给文字保留最小宽度，限制头像和字号范围，空间不足时降级为无头像布局；Provider 声明合理最小尺寸。验收至少覆盖窄宽度、1 格低高度、纵向拉高和系统大字体。

**R13 · P2：圆形裁剪固定取左上角**

位置：[getCircleBitmap:148](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidget.kt:148)、[WidgetImageManager.kt:110](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/util/WidgetImageManager.kt:110)。

源矩形和目标矩形均为 `Rect(0, 0, min(width,height), min(width,height))`。对 512×256 横图，读取的是左半部分，不是居中正方形；对竖图则读取顶部。主体居中的长宽图可能丢失主体，后面的 `ContentScale.Crop` 无法恢复已经裁掉的像素。

建议使用居中源矩形，或提供用户选择裁剪区域的能力。先保持正确取景，再做圆形蒙版。

**R14 · P2：并发图片保存共用同一个 .tmp 文件**

位置：[WidgetImageManager.kt:60](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/util/WidgetImageManager.kt:60)、[WidgetImageManager.kt:113](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/util/WidgetImageManager.kt:113)。

每次选图都在 IO 作用域启动独立协程，没有互斥或任务替换机制，临时路径却固定为 `widget_custom_image.png.tmp`。前一次处理仍在进行时再次选图，两个任务可能同时打开或截断相同文件。一个任务 rename 后，另一个已打开的文件描述符仍可能继续写入该文件；finally 中的 delete 还可能删除另一个任务正在使用的临时路径。

现有 rename 能保护单次保存，不能保护多写者。风险包括图片内容交错、后一次保存失败，以及最终图片不是用户最后选择的那张。

建议串行化整条保存流程，并明确“最后选择生效”的规则；若采用唯一临时文件，也要控制最终提交顺序。此项为静态并发路径确认，未在设备上进行压力复现。

**R15 · P2：黄历表不断追加重复记录，读取依赖完成顺序**

位置：[CHNDateEntity.kt:9](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/src/main/java/lins/libs/module_base/model/CHNDateEntity.kt:9)、[CHNDateDao.kt:23](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/src/main/java/lins/libs/module_base/dao/CHNDateDao.kt:23)、[MainViewModel.kt:145](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt:145)。

每次成功请求都以 `uid = 0` 插入自增行，没有按日期唯一约束，也没有调用清理方法。同一天重复打开应用或触发配置重建都会产生新记录。根据当前 Room DDL，在 SQLite 中连续插入同一天数据可直接得到多条记录。

`getLast()` 按 uid 最大读取，表示最后完成写入的请求，不表示当前日期。跨午夜或修改系统日期时，先发但后完成的旧日期请求可能成为“最新”记录；下次离线启动就展示该值。当前主页会显示该缓存的公历日期，因此此处与 R04 的无日期提示不同，但仍未选择今天的数据。

建议给黄历数据增加独立查询日期键，同一天 upsert、按日期读取，并设置历史保留范围。该变更涉及 Room schema，需要迁移；请求结果写入 UI 前也要确认它仍对应当前日期。

**可提升和优化的地方**

| 编号 | 建议 | 现有证据与收益 |
| --- | --- | --- |
| O01 | 合并 HKO 获取与缓存入口，按日期缓存并合并并发请求 | `MainActivity` 同时调用 `getTodayLunarInfo` 和 `syncAndUpdate`，二者都请求 HKO；首次启动还可能遇到 Worker 请求。`lunarData` 在当前 UI 无消费者。去掉这条重复状态链或让页面订阅统一仓库，可减少请求、写库和不一致窗口。 |
| O02 | 调度与小组件生命周期配合 | `App.onCreate` 总是注册周期任务，移除最后一个小组件后仍持续运行。可在存在小组件时维持同步，恢复/新增时补齐调度；日期对应的农历通常不需要同一天反复请求。 |
| O03 | 统一取消和错误语义 | HKO、同步助手和部分 ViewModel 直接 `catch(Exception)`，黄历及 Glance 使用 `runCatching`，都可能把协程取消当成普通失败。参考诗词仓库现有的 `CancellationException` 单独 rethrow 处理；避免取消时产生误导日志或失败分支。无需据此宣称所有取消后的网络都会继续运行。 |
| O04 | 增加能保护核心行为的测试和持续检查 | 3 个 JVM 测试只检查 `2+2`；另 3 个仪器测试只检查包名，诗词模块没有测试源码。优先测试 R02/R04/R09/R10/R15 和迁移，CI 同时构建 Debug 与 Release、运行测试及 Lint。注入 Clock、HTTP client 和 DAO 可让这些测试不依赖真实网络与当天日期。 |
| O05 | 控制依赖、包体和无用代码 | Release 未启用代码压缩；主界面仅使用一个 Image 图标但依赖扩展图标库，小组件模块也声明了该依赖。`MaxWidgetLayout` 当前仅由 Preview 调用，与 Medium 有大量重复；`img_maodie.jpg` 没有代码引用。先明确保留范围，再评估 R8/资源裁剪及依赖移除。实测 Debug APK 约 77MiB，不能据此推断修复后的 Release 大小。 |
| O06 | 补齐状态呈现和工程说明 | 日期加载失败、缓存过期、诗词刷新失败缺少明确状态；宜忌缺失时显示“无”会混淆“没有数据”和“当天没有事项”。README 只有一句描述，宜补充数据源、最低系统、构建方式和同步机制。清理重复布局、失效注释及无用资源，并把构建生成的 `.kotlin/` 纳入忽略规则。 |

O01 的位置：[MainActivity.kt:42](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainActivity.kt:42)、[MainViewModel.kt:79](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/MainViewModel.kt:79)。O02：[App.kt:23](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/App.kt:23)、[MyAppWidgetReceiver.kt:6](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/MyAppWidgetReceiver.kt:6)。O03：[HkoRepository.kt:48](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/src/main/java/lins/applications/appwidget/data/HkoRepository.kt:48)。O05：[app/build.gradle.kts:20](/home/LinkingLin/CODE/MyChineseCalender/app/build.gradle.kts:20)。O06：[MainContent.kt:286](/home/LinkingLin/CODE/MyChineseCalender/app/src/main/java/lins/applications/mychinesecalender/ui/content/MainContent.kt:286)。

**待设备或接口契约验证的风险，不计入上述 15 项**

- **广播执行时间。** `DateChangeReceiver` 使用 `goAsync()` 后直接等待网络、数据库和所有 widget 更新；单次 HTTP 请求上限 15 秒，整条链路没有总时限。`goAsync()` 不移除广播执行限制，但前台和后台广播的时间预算不同，不能只凭“超过 10 秒”断言这里必然 ANR。建议将持久任务交给 WorkManager，并在弱网、多小组件下验证。参见 [BroadcastReceiver.goAsync 官方说明](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync())。
- **图片方向与极端尺寸。** 当前只调用 BitmapFactory，未见 EXIF 方向处理；应使用原始相机照片检查旋转/镜像，再确定处理方式。缩放后宽高直接 `toInt()`，极端长宽比下还可能得到 0，应验证并设置至少 1 像素。此处未用真实设备图片复现。
- **外部接口契约与历史安装。** 未对 HKO、黄历及今日诗词做线上可用性/返回契约测试，也未模拟 Token 的各种业务错误；不能断言服务当前异常或 Token 一定无法恢复。较早历史曾使用另一个数据库文件名，但当前已知 2→3 路径使用 `chn_calendar.db`；是否需要兼容更早安装，取决于实际分发过的版本，不宜直接将缺少 1→3 迁移认定为现有用户必然崩溃。

**已执行的验证**

| 检查 | 结果与边界 |
| --- | --- |
| Debug 构建 | `:app:assembleDebug` 通过，为现有工作区上的增量构建。 |
| Release 构建 | 失败，定位到 R01；未生成可交付的 Release 包。 |
| JVM 单元测试 | `app`、`Appwidget`、`Module_Base` 各 1 个，共 3 个通过；`Module_Poem` 为 NO-SOURCE。 |
| Debug Lint | 四个模块的 `lintDebug` 均完成：app 33 个 Warning，Appwidget 2 个 Warning，两个基础模块各 0；无 Error。模块报告按各自结果列出，不作跨报告去重后的唯一问题计数。 |
| 黄历 JSON | 使用本次项目实际编译模型及 serialization 1.11.0，确认新增字段失败、单字段残缺响应通过现有有效性谓词。验证程序仅保存在临时目录。 |
| Room 2→3 SQL | 从仓库提取 4 条实际迁移 SQL，使用历史 v2 字段重建 SQLite 内存库，执行成功、样本数据保留；两张表的字段信息与当前生成的 Room DDL 一致。这不替代 Android MigrationTestHelper 的完整身份校验和真实升级测试。 |
| 布局公式 | 核算 100×100、150×100、200×200、300×60dp 等尺寸，确认 R12 的空间冲突。 |
| XML | 所有受版本控制的 XML 均能正常解析。 |
| Android 运行验证 | 未安装应用，未执行仪器测试、桌面小组件操作、Doze/跨天/字体大小和真实照片测试。 |

实际执行过的 Gradle 命令：

```sh
./gradlew --offline :app:assembleDebug :app:assembleRelease testDebugUnitTest lintDebug
./gradlew :app:assembleRelease testDebugUnitTest lintDebug --continue
```

首次离线检查因缺少 `androidx.graphics:graphics-shapes-desktop:1.0.1` 的本地缓存而中断；继续联网检查后 Lint 完成。最终组合命令的失败由 Release 编译错误造成，不表示三个 JVM 测试或已完成的 Lint 失败。

Lint 的主要内容是依赖版本提示、未使用资源、可以简化的 API 判断及 KTX 风格建议，没有发现上述核心状态问题。版本提示仅作为维护线索，本报告不将“不是最新版”自动认定为漏洞，也不建议在修复逻辑问题时一次性升级全部依赖。

验证产物：[app Lint](/home/LinkingLin/CODE/MyChineseCalender/app/build/reports/lint-results-debug.html)、[Appwidget Lint](/home/LinkingLin/CODE/MyChineseCalender/Appwidget/build/reports/lint-results-debug.html)、[Module_Base Lint](/home/LinkingLin/CODE/MyChineseCalender/Module_Base/build/reports/lint-results-debug.html)、[Module_Poem Lint](/home/LinkingLin/CODE/MyChineseCalender/Module_Poem/build/reports/lint-results-debug.html)。这些位于构建目录，后续 clean 会删除。

**建议的处理顺序，供你选择**

1. **先解除发布阻断：R01。** 单独处理并验证 Release，避免与业务修改混在一起。
2. **保证日期可信：R02、R03、R04、R05、R06。** 统一日期状态、过期提示和刷新来源，验收冷启动无缓存、断网跨天、前台跨天和活跃会话更新。
3. **保证缓存和错误处理：R07、R08、R09、R10、R11、R15。** 同时纳入 O01；日期键变更必须配迁移验证。
4. **处理图片与布局：R12、R13、R14。** 在实际 One UI 8 桌面和多种照片上验收。
5. **再做性能与维护优化：O02—O06。** 根据你希望投入的范围拆分实施，依赖升级独立验证。

建议把下列场景作为后续验收基线：新安装离线后联网；已有昨日缓存的午夜切日；修改系统日期与时区；45 秒内连续更新数据和头像；某个小组件更新失败；黄历接口新增字段、缺失核心字段和返回错误日期；连续两次选图；窄/高/低小组件和大字体；同日反复打开应用；v2 数据库升级。每个场景都应明确预期展示和失败反馈。
