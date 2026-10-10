# 全仓库审查及修复报告 · 2026-09-13

## 结论与范围

本轮检查了 `app`、`Appwidget`、`Module_Base`、`Module_Poem` 四个模块的生产代码、Debug 预览、测试、数据库迁移与 schema、Manifest、资源、构建和 CI 配置。修复及优化集中在以下 8 组内部问题；没有调整页面或小组件的展示布局，也没有升级依赖版本。

已通过 Debug/Release 构建、43 项 JVM 单元测试、四模块 Lint 和数据库仪器测试 APK 编译。此次新增 12 项回归测试；数据库仪器测试尚未在设备上执行。静态检查和模拟测试不能证明所有潜在问题都已消除，设备验证边界见文末。

## 布局保护与注释要求

本轮以开始工作时的 `f6e4fac3ca65ce5a32662e578ef82b29cd81675e` 及工作目录快照为基准，保留该提交已有的空态行为与防闪烁处理，没有退回更早的布局版本。

- 对页面、主题、Activity、小组件入口及 Debug 预览共 7 个 Kotlin 文件进行了去注释比对：可执行源码一致，字符串内容也一致。
- 四模块 `src/main/res` 下 38 个资源文件逐字节一致，资源 XML 解析通过。
- 间距、内边距、尺寸、字号、头像比例、颜色、文字、排列、对齐及尺寸分支均保持原值。小组件空态仍沿用中等布局、头像和已有两行文字。
- 生产及 Debug 源码中的 112 个具名函数均有返回结果或副作用说明，有形参的函数均有对应 `@param`。复杂路径补充了执行顺序、取消、并发、缓存优先级与失败恢复的中文注释；简单表达式不逐行重复语法。
- 工作区原先存在的 `WidgetLayoutSpec.kt` 暂存新增、工作目录缺失状态保持原状；本轮未操作暂存区。

注释之外的既有生产源码逻辑变更仅位于 `DailyRepository`、`PoemRepository` 和 `KtorClient`，另新增 `PoemTokenStore`、`NetworkLogging` 两个内部辅助文件。其余生产/预览源码修改为注释。函数说明及后续维护入口见 [代码维护导航](MAINTENANCE.md)。

## 发现、触发方式与修复

### A01：写盘失败后，新数据可能被旧磁盘值覆盖

位置：[DailyRepository.load](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。

磁盘已有当天旧数据，强制刷新取得新数据但保存失败时，内存已展示新值。原逻辑下次普通加载优先选磁盘值，会退回旧内容，且无法补写真正的新值。

现在优先使用通过校验的同日内存数据；磁盘与内存不一致时补写内存值。仍保留精确日期校验，不以其他日期的数据兜底。回归测试 `newerMemorySurvivesFailedWriteAndRepairsOldDisk` 验证新值保留、存储恢复和不重复联网。

### A02：缓存清理失败后，后续命中缓存不会重试

位置：[DailyRepository.persist](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。

写库成功但清理过期记录失败时，下次读取能命中同日缓存，原逻辑会跳过整个保存流程，使清理故障失去重试机会。

现在把写库和清理视为一个可重试步骤，只有两个步骤都完成才移除待处理标记。普通加载命中缓存时也检查此标记。标记仅在进程内保存，并随日期范围清理，避免无限增长。回归测试 `cachedLoadRetriesFailedCleanup` 验证清理恢复后无需再次联网。

### A03：新数据写入后取消，内存状态可能回退

位置：[DailyRepository.load / persist](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。

强制刷新已取得有效新值并写盘，但在后续清理过程中被取消，原取消分支会重新发布刷新前的数据，导致同日内存与磁盘不一致。

现在单独保留本次已校验的最新数据；持久化阶段取消时发布该值并保留补写/清理标记，取消异常仍向调用方传播。网络返回后也检查协程是否已取消，避免不配合取消的适配器返回后继续写入。回归测试 `cancellationAfterWriteKeepsNewestDataAndRetriesCleanup` 验证取消传播、数据不回退及后续恢复。

### A04：已有内存结果的订阅仍被慢磁盘阻塞

位置：[DailyRepository.observe](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。

仓库已有当天真实加载状态，但新订阅仍需等待磁盘观察流的首值才能完成合并，这会推迟已有内容的使用。

现在已有内存状态时立即参与合并；没有内存状态时仍等待真实缓存查询，不先发空值，保留此前修复的无闪烁行为。同时过滤重复状态，避免其他日期更新引起本日的无效通知。回归测试 `knownMemoryIsEmittedBeforeSlowDiskObservation` 与已有首值、防闪烁测试共同验证这两个分支。

### A05：明日预取占用整个仓库锁，阻塞今日加载

位置：[DailyRepository.load / publish](../Module_Base/src/main/java/lins/libs/module_base/data/DailyRepository.kt)。

原仓库使用一个锁包围读盘、联网、写盘全流程。明日预取遇到慢网络时，即使今天已有缓存，今日加载也需要排队。

现在使用固定 32 个日期分组锁：同日请求保持串行，相邻日期可以独立执行；哈希碰撞只造成排队，锁数量不随日期数增加。共享状态改为原子合并，防止不同日期同时完成时互相覆盖。回归测试 `stalledTomorrowPrefetchDoesNotBlockToday` 验证预取未完成时今日仍可返回，已有同日并发测试继续保证不重复请求。

### A06：诗词 Token 存储故障阻断有效网络请求

位置：[PoemRepository](../Module_Poem/src/main/java/lins/libs/module_poem/repository/PoemRepository.kt)、[PoemTokenStore](../Module_Poem/src/main/java/lins/libs/module_poem/repository/PoemTokenStore.kt)。

原流程把磁盘读取与网络获取耦合；Token 读盘失败会直接中断诗词请求，新 Token 写盘失败也会丢失当前已经取得的可用值。

现在将存储接口与网络流程分开，读盘失败仍尝试申请 Token，写盘失败先保留内存 Token，之后复用时重试保存。沿用 `jinrishici_datastore` 文件名和 `user_token` 键，不破坏已有安装。回归测试 `storageFailureDoesNotDiscardTokenAndCanRecover` 同时覆盖读写故障及恢复。

### A07：认证失效后的恢复依赖磁盘删除成功

位置：[PoemRepository.rejectToken / fetchPoem](../Module_Poem/src/main/java/lins/libs/module_poem/repository/PoemRepository.kt)。

诗句接口拒绝旧 Token 后，如果删除持久值失败，原流程无法继续正常恢复；并发入口还可能重复获取 Token，或让旧请求的认证失败影响新 Token。

现在先使内存 Token 失效，再尽力删除磁盘值；删除失败也不会重新读取已拒绝的 Token。本次诗句请求最多尝试两次，第二次仍拒绝也清理无效值。整次操作由同一仓库实例的互斥锁保护，取消继续传播。

回归测试覆盖删除失败、新 Token 重试、持续拒绝时的请求上限、并发复用、正文为空和取消：`failedCacheDeletionDoesNotReuseRejectedToken`、`persistentAuthenticationFailureIsBounded`、`concurrentRequestsReuseToken`、`emptyPoemIsRejectedWithoutTokenReset`、`cancellationIsNotSwallowedOrRetried`。

### A08：Debug HTTP 正文日志会输出 Token

位置：[KtorClient](../Module_Base/src/main/java/lins/libs/module_base/network/KtorClient.kt)、[NetworkLogging](../Module_Base/src/main/java/lins/libs/module_base/network/NetworkLogging.kt)。

原 Debug 日志级别为 BODY，获取 Token 的响应正文会进入日志。新增头部日志时也需要处理认证头，单独隐藏头部不足以保护正文里的 Token。

现在 Debug 使用 HEADERS，隐藏 `X-User-Token`、`Authorization`、`Cookie`、`Set-Cookie`，不记录请求和响应正文；Release 保持 NONE。日志级别和头部过滤使用 [Ktor 官方日志配置接口](https://ktor.io/docs/client-logging.html)。这项修复针对共享客户端的 HTTP 日志，业务异常日志仍需在新增调用处避免输出敏感内容。

`NetworkLoggingTest.credentialsAreHiddenInDebugAndRelease` 使用模拟请求捕获真实 Logging 插件输出，验证认证头与正文中的测试凭据不出现、Debug 脱敏占位符存在、Release 无 HTTP 日志。

## 验证证据

先新增缓存回归用例运行于原实现，14 项缓存测试中有 5 项失败，分别对应 A01—A05；完成修复后全部通过。诗词新增 6 项、HTTP 日志新增 1 项，共新增 12 项测试。

最终运行：

```sh
./gradlew --offline :app:assembleDebug :app:assembleRelease testDebugUnitTest lintDebug :Module_Base:assembleDebugAndroidTest --continue --console=plain
```

结果为 `BUILD SUCCESSFUL`，381 项任务；Release 的 R8 与资源裁剪也通过。

| 模块 | JVM 测试数 | 失败 / 错误 / 跳过 | Lint 错误 | Lint 警告 |
| --- | ---: | --- | ---: | ---: |
| app | 4 | 0 / 0 / 0 | 0 | 23 |
| Appwidget | 11 | 0 / 0 / 0 | 0 | 2 |
| Module_Base | 22 | 0 / 0 / 0 | 0 | 1 |
| Module_Poem | 6 | 0 / 0 / 0 | 0 | 0 |
| 合计 | 43 | 0 / 0 / 0 | 0 | 26 |

Lint 警告仍有依赖更新、资源及代码风格建议；本轮未为消除提示而调整视觉资源或批量升级依赖。测试总数包含仓库原有基础用例，不代表 43 种独立端到端设备场景。构建、测试和 Lint 的详细产物位于各模块 `build` 目录。

## 尚需设备验证的边界

- 数据库迁移测试 APK 已编译，尚未运行 `:Module_Base:connectedDebugAndroidTest`，不能代替真实 SQLite 升级验证。
- 未在目标 One UI 桌面实测午夜、Doze、系统时间/时区变化、多组件、组件缩放和照片选择；布局一致性结论来自源码与资源比对，不是设备截图验收。
- 网络测试使用 MockEngine，不访问线上黄历、HKO 或今日诗词服务，不能证明服务当前可用性或未来接口不变。
- 缓存补写标记与 Token 内存兜底不跨进程保存；磁盘持续失败期间进程退出，仍会丢失未持久化数据。此次实现保证进程内继续使用和后续调用可重试，没有引入独立持久后台队列。

后续修改优先按 [维护导航中的需求—测试对应表](MAINTENANCE.md#5-修改需求对应哪些文件与测试) 选择验证。涉及间距、尺寸、字号、头像或空态内容时，需作为独立的外观需求处理，不能混入内部逻辑维护。
