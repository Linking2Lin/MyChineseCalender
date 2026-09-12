# MyChineseCalendar

面向 Android 12+ 的黄历、诗词应用和 Glance 桌面农历小组件，主要适配三星 One UI。

## 功能与数据来源

- 主页黄历：`api.tiax.cn/almanac/`；公历和农历必须完整且与请求日期一致，宜忌等信息允许缺省。
- 小组件农历：香港天文台开放数据。按设备当前时区确定公历日期，不把其他日期的缓存当作今天。
- 今日诗词：今日诗词 API，Token 存储在 DataStore。
- 自定义头像：系统照片选择器 → 方向处理和降采样 → 居中圆形裁剪 → 内部文件原子替换。

## 刷新与离线行为

主页恢复前台时检查日期，停留前台跨天时也会切换；“刷新黄历”可以主动重试。网络失败时仅保留当天有效缓存，没有当天缓存就显示失败/空态。

小组件在 Glance 会话中持续订阅日期、数据库和头像变化，空态也能点击刷新。外观沿用原有精调布局：胶囊边距、头像比例、字号计算、两行文字与尺寸分支保持原样，不额外拼接公历日期，也不自动隐藏头像或年份。

小组件点击刷新或重建会话时先读取当天缓存，已有内容在刷新期间继续展示。网络失败时，有当天有效缓存就继续展示，没有则使用空态。空态在所有尺寸下均复用中等布局的样式和头像，仅将第一行固定为“常能遣其欲而心自静，”，第二行固定为“澄其心而神自清。”。

存在小组件时，应用维持 30 分钟周期任务和下一次本地午夜的非精确闹钟，并处理时间、时区、开机和应用更新事件。任务即使离线也会请求刷新展示；当天同步成功后尝试预取次日农历。同一天已有有效缓存时，后台不会重复拉取该日数据。删除最后一个小组件会停止这些任务。

**后台更新不是精确零点承诺。** Android 省电/Doze、强行停止应用和桌面宿主行为仍可能延迟执行；本项目不要求“闹钟和提醒”特殊权限。午夜调度使用 `setAndAllowWhileIdle`，相关限制见 [Android 闹钟调度说明](https://developer.android.com/develop/background-work/services/alarms)。需要精确零点更新时，应另外确定权限与产品策略。

## 模块

| 模块 | 职责 |
| --- | --- |
| `app` | 页面、ViewModel、应用入口、选图流程 |
| `Appwidget` | Glance 展示、后台调度、接口适配和仓库装配 |
| `Module_Base` | 共享 HTTP/JSON、按日缓存仓库、日期工具、Room 模型和迁移 |
| `Module_Poem` | 诗词模型、Token 与接口处理 |

Room 当前版本为 4，保留 2→3→4 升级路径。3→4 为完整黄历增加 ISO 日期主键，保留每一天最后一条有效旧记录，丢弃无法确定日期或缺失农历的缓存；HKO 表保持不变。所有缓存只保留近期日期，图片文件和诗词 Token 不参与该迁移。

## 构建与检查

需要 Android SDK 37、Java 21 和网络可用的首次依赖下载环境。Gradle Wrapper 及 JBR 21 工具链要求已在仓库配置；本地 SDK 路径通过 Android Studio、环境变量或不提交的 `local.properties` 设置。

```sh
./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest lintDebug
./gradlew :Module_Base:assembleDebugAndroidTest
```

连接 Android 12+ 测试设备/模拟器后运行数据库真实升级测试：

```sh
./gradlew :Module_Base:connectedDebugAndroidTest
```

Debug APK 位于 `app/build/outputs/apk/debug/`，Release 位于 `app/build/outputs/apk/release/`。Release 开启 R8 和资源裁剪，未配置发布签名；Glance/Worker 的持久化类名保留规则位于应用的 ProGuard 文件。

GitHub Actions 会检查 Debug/Release、JVM 回归测试、Lint 及升级测试 APK 的编译。它不运行设备仪器测试。

## 验收建议

设备上重点检查：无缓存离线首次添加、联网后点击重试、已有缓存跨午夜、修改系统时间和时区、45 秒内更换头像、多小组件同步、系统大字体、窄/高/低尺寸，以及横竖相机照片。受后台调度影响的场景尤其需要在目标 One UI 版本实测。

维护入口、核心流程与按修改需求选择的测试见 [代码维护导航](docs/MAINTENANCE.md)。源码中的中文注释说明了日期身份、缓存错误、组件生命周期、图片提交顺序及持久化兼容约定。

历史审查见 [审查报告](docs/CODE_REVIEW_2026-09-06.md)，本轮处理情况见 [修复记录](docs/FIXES_2026-09-06.md)。
