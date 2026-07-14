# Navigation 3 与设置中心重构设计规格

## 1. 文档状态与使用规则

| 项目 | 结论 |
| --- | --- |
| 文档日期 | 2026-07-14 |
| 当前状态 | 已获用户书面批准；implementation plan 已完成并通过 Momus 阻塞审查，等待执行授权 |
| 适用范围 | Navigation 3 迁移、Settings 架构重构、模块边界、平台能力拆分、Settings 首页与“账号与同步”代表能力页 |
| 计划用途 | 本文档是后续 implementation plan 的单一设计输入，计划编写者不需要重新访谈，也不得重新打开本文已经关闭的设计分叉 |
| 当前授权 | 已批准编写、自审和提交 implementation plan；不授权修改代码、Gradle、资源、Manifest、版本、路由或发布配置 |
| 下一道关口 | implementation plan 本地提交后，仍须由用户明确授权执行；设计批准、计划审查通过和计划提交均不构成实施授权 |

本文综合 `DESIGN.md`、`CONTEXT.md`、现有 `ARCHITECTURE.md`、ADR 0001 至 0007、当前 Settings Gradle 依赖、版本目录和两份视觉比较稿。ADR 继续记录决策原因，本文负责把这些决策消歧为一套可执行设计。实施计划以本文为准，不复制 ADR，也不在计划阶段重新选择架构或视觉方向。

## 2. 目标、成功定义与范围

### 2.1 目标

1. 在一次稳定版内把应用导航迁移到 Navigation 3，并为六个顶层分区建立相互独立、可恢复的返回栈。
2. 把 Settings 从聚合基础设施与大量字段的单体页面，改成只负责导航的设置首页和六个能力页。
3. 让 `:feature:settings` 只面向领域能力与 UI 契约，所有数据、网络、同步、更新、日历和 Android 平台副作用都隐藏在明确的模块后面。
4. 把更新、系统日历、快捷开关、外部系统动作和诊断导出拆到各自的 Core 模块，阻止 `core -> app/feature` 反向依赖。
5. 保持现有用户数据、后台任务、分享 URI、外部 Intent、包身份、benchmark 和 profile 行为不变。
6. 用已批准的“线册式总览”和“诊断前置册页”形成可实现、可测试、可访问的 Settings 视觉与交互方案。

### 2.2 成功定义

迁移完成时，用户能在六个顶层分区之间切换并恢复各自现场；Settings 首页只显示六个入口的轻量只读摘要；每个能力页拥有独立状态与测试面；`账号与同步` 能用一个互斥状态清楚表达同步健康、恢复动作和非常规全量重同步；Gradle 与源码依赖符合本文边界；完整门禁、签名 Benchmark APK 和稳定发布闭环全部通过。

### 2.3 范围外

本设计不增加以下产品能力：

1. 自动冲突解决或冲突合并。
2. 同步历史管理、事件流水管理或历史记录清理。
3. WorkManager 队列清理、手动取消队列或新的重试策略。
4. 自动修复账号、服务器或同步错误。
5. 新的登录模式、账号体系或服务器连接模式。
6. Settings 首页写操作、快捷开关或跨页批量编辑。
7. 与本迁移无关的数据库结构、文件分享范围、编辑器行为或视觉系统重做。

## 3. 当前事实

### 3.1 导航现状

1. 当前 `OneMemosApp` 使用 Navigation Compose 2.7.7 的 `NavHost`、`NavController` 和字符串 Routes。
2. 当前路由由应用层集中管理，页面注册和导航编排集中在 app，尚未形成 Navigation 3 的可序列化 `NavKey`、Feature 自有 entry 或六个独立顶层返回栈。
3. 现有 `ARCHITECTURE.md` 的“当前工程概览”停留在 2026-02-02 的 Hybrid 多模块迁移前后语境，其中仍把旧 package 结构和字符串 Routes 写成当前事实。它对 WorkManager、数据库、FileProvider、外部 Intent、包名以及 benchmark/profile 的字面量约束仍有价值，但不能继续作为最终模块结构的现状说明。

### 3.2 Settings 现状

1. `feature/settings/build.gradle.kts` 显示 `:feature:settings` 当前直接依赖 `:core:data`、`:core:network` 和 `:core:sync`。
2. 该模块还直接引入 Retrofit 与 WorkManager，Presentation 层因此能看到基础设施类型。
3. 当前 `SettingsUiState` 聚合大量跨能力字段，账号、同步、编辑、提醒、存储、外观、更新和高级操作共享同一宽状态面，页面状态、依赖和测试相互牵连。
4. Settings 尚未以“只导航的首页 + 六个独立能力页”形成模块级接口和独立 ViewModel 测试面。

### 3.3 工具链现状与 Navigation 3 前置条件

以下版本均来自当前 `gradle/libs.versions.toml`。

| 项目 | 当前版本或值 |
| --- | --- |
| Android Gradle Plugin | 8.4.2 |
| Kotlin | 1.9.24 |
| Compose BOM | 2024.06.00 |
| Navigation Compose | 2.7.7 |
| `compileSdk` | 34 |
| `targetSdk` | 34 |
| `minSdk` | 33 |

Navigation 3 稳定线要求 `compileSdk` 至少为 36。实施不能只替换导航依赖，必须先锁定一组相互兼容的稳定工具链与库版本，并把 `compileSdk` 提升到所选 Navigation 3 稳定版要求的值，且不得低于 36。本设计不因 Navigation 3 改变 `minSdk 33` 或 `targetSdk 34`。

## 4. 目标架构与依赖方向

### 4.1 总体规则

1. `:app` 保持唯一组合根，负责 Android 入口、Hilt 最终装配、Navigation 3 Host、Feature entry 聚合和平台导航输入，不承载 Settings 业务规则。
2. Feature 之间不得互相依赖。跨 Feature 导航只提交 `:core:navigation` 中的类型化 `NavKey`。
3. UI 和 ViewModel 不直接调用 Retrofit API、持久化实现、WorkManager 实现或 Android 平台组件。
4. `:core:domain` 保持纯 Kotlin，声明 Settings 能力接口、领域快照、命令、结果和用户可理解的错误类型。
5. 每个默认 Hilt 绑定只能有一个提供源。迁移时采用“新提供源生效后删除旧提供源”的原子替换，不允许 app 与 Core 同时保留同一默认绑定。

### 4.2 最终模块所有权

| 模块 | 最终所有权 | 允许依赖 | 禁止依赖 |
| --- | --- | --- | --- |
| `:core:navigation` | 可序列化 `NavKey`、六个顶层分区、独立返回栈机制、外部输入到受控键的映射结果、窄类型化 Navigator | Navigation 3 所需稳定库、纯 Kotlin 支撑 | 任何 `:feature:*`、`:app`、业务数据实现 |
| `:feature:*` | 本 Feature 的 Navigation 3 entry、页面构造、ViewModel 获取和模块内 UI | `:core:domain`、`:core:navigation`、`:core:designsystem` 与该 Feature 已有能力所需的窄契约和 UI 依赖 | 其他 Feature、`:app`、基础设施具体实现 |
| `:app` | 组合根、Host、entry 聚合、平台 Intent/通知输入、Hilt 最终装配、Application 级 WorkManager 与 ImageLoader 入口 | Feature 与各 Core 模块 | Settings 业务规则、页面状态推导、Feature 页面实现注册细节 |
| `:core:domain` | 六个 Settings 能力接口、设置首页只读摘要接口及其领域模型 | 纯 Kotlin | AndroidX、Retrofit、WorkManager、Feature、app |
| `:core:settings` | 实现六个 Settings 能力接口和首页摘要接口，组合 data、network、sync、update、calendar 等能力，统一副作用编排与错误映射 | `:core:domain`、`:core:data`、`:core:network`、`:core:sync`、`:core:update`、`:core:calendar`、`:core:quicktiles`、`:core:externalactions`、`:core:diagnostics` | `:app`、任何 `:feature:*`、Compose |
| `:feature:settings` | Settings 首页、六个能力页、页面级 ViewModel、`UiState`、用户意图和一次性 UI 结果 | `:core:domain`、`:core:navigation`、`:core:designsystem`、Compose、Lifecycle，以及仅用于 ViewModel 构造的 Hilt 集成 | `:core:data`、`:core:network`、`:core:sync`、Retrofit、OkHttp、WorkManager、Hilt Provider/Binding、其他 Feature、app |
| `:core:update` | 更新检查与交付相关 Android 能力和实现 | 自身需要的 Core 契约与 Android 库 | app、Feature |
| `:core:calendar` | 系统日历读写、权限相关能力和实现 | 自身需要的 Core 契约与 Android 库 | app、Feature |
| `:core:quicktiles` | 快捷开关及其 Android 组件和实现 | 自身需要的 Core 契约与 Android 库 | app、Feature |
| `:core:externalactions` | 分享、打开外部目标等外部系统动作及实现 | 自身需要的 Core 契约与 Android 库 | app、Feature |
| `:core:diagnostics` | 诊断信息收集与导出相关 Android 能力和实现 | 自身需要的 Core 契约与 Android 库 | app、Feature |

五个平台能力模块需要包身份、快捷记录目标、截图入口或应用内回退目标时，只调用由 `:app` 注入的窄端口。设计级端口可命名为 `AppIdentityPort`、`QuickCaptureTargetPort`、`ScreenshotEntryPort` 和 `InAppFallbackPort`。端口声明位于消费它的 Core 模块，app 只提供适配器。最终符号名可在 implementation plan 中按项目命名风格调整，但这些端口的语义和依赖方向不能被替换成 Core 对 app 或 Feature 的直接依赖。

### 4.3 Feature entry 与依赖装配

1. 每个 Feature 暴露一个无状态的 entry contributor。它把 `:core:navigation` 中属于本 Feature 的 `NavKey` 映射到本 Feature 的 Compose entry；页面构造和 ViewModel 获取都保留在 Feature 内。
2. `:app` 显式聚合各 Feature 的 contributor 引用，不逐页构造 Feature 页面，不维护所有页面实现的巨型注册表，也不向 contributor 传递 data、network、sync、update 或 calendar 的具体类型。
3. `:feature:settings` 继续使用 `@HiltViewModel` 和 Hilt 的 Compose ViewModel 获取集成。Settings contributor 在本模块 entry 内取得对应 ViewModel，不新增 `SettingsEntryDependencies` 或其他平行依赖容器。
4. Hilt 最终装配仍由 app 组合根负责。`:feature:settings` 不声明手写 Provider、Binding 或任何基础设施实现，注入到 ViewModel 的只能是 `:core:domain` 能力接口。
5. Navigator 只传到 Compose/entry 层。ViewModel 不接收、不缓存也不间接查找 Navigator。

### 4.4 Settings 深接口

接口粒度固定为“一个设置首页只读接口 + 一页一个能力接口”。推荐的设计级名称如下：

| 页面或入口 | 设计级接口名 | 职责 |
| --- | --- | --- |
| 设置首页 | `SettingsHubCapability` | 观察六项独立、轻量、只读的摘要投影，不执行写操作 |
| 账号与同步 | `AccountSyncSettingsCapability` | 聚合账号状态、同步健康、常规同步、账号管理与全量重同步，隐藏网络和 WorkManager |
| 记录与编辑 | `RecordEditingSettingsCapability` | 聚合现有记录默认值、编辑偏好及其持久化副作用 |
| 提醒与日历 | `ReminderCalendarSettingsCapability` | 聚合提醒设置、系统日历连接和权限相关领域结果，隐藏 Android Calendar 实现 |
| 存储与离线 | `StorageOfflineSettingsCapability` | 聚合现有缓存、离线内容与存储相关设置及操作 |
| 外观与交互 | `AppearanceInteractionSettingsCapability` | 聚合主题、明暗、交互和动效偏好 |
| 关于与高级 | `AboutAdvancedSettingsCapability` | 聚合版本、更新、诊断和现有高级设置，隐藏更新交付与诊断导出实现 |

页面能力接口采用小而深的统一形态：

```kotlin
interface AccountSyncSettingsCapability {
    fun observe(): Flow<AccountSyncSettingsSnapshot>
    suspend fun execute(
        command: AccountSyncSettingsCommand,
    ): AccountSyncSettingsResult
}
```

其余五页使用各自的 `Snapshot`、`Command` 和 `Result`，不共用一个无类型的万能命令。`SettingsHubCapability` 只有观察只读摘要的接口，不提供 `execute`。领域快照不包含 Compose 文案或资源 ID；错误和结果使用领域类型，由 Presentation 映射为资源。最终符号名可在 implementation plan 中按项目既有命名风格确定，但以下规则不可改变：

1. 六个能力页各有且只有一个主能力接口。
2. 不得退化为“一项操作一个 UseCase”的浅接口集合。
3. 页面 ViewModel 只依赖本页的一个能力接口。
4. 跨 data、network、sync、update、calendar 的编排只在 `:core:settings` 内发生。
5. `:core:settings` 把 Retrofit、HTTP、WorkManager 和平台异常映射为 `:core:domain` 的用户可理解错误类型。

## 5. Navigation 3 设计

### 5.1 导航契约

1. `:core:navigation` 集中定义所有可序列化 `NavKey`，确保类型、参数编码和恢复规则只有一个来源。
2. `TopLevelSection` 固定为六项：随笔、锦囊、待办、个人中心、归档、设置。每项有唯一根 `NavKey` 和独立返回栈。
3. 窄 Navigator 只暴露三类语义动作：向当前分区压入受控 `NavKey`、执行返回、切换顶层分区。Feature 不得直接拿到或修改全局 `NavBackStack`。
4. entry 必须由拥有页面的 Feature 提供。`core:navigation` 不引用 Feature，app 只聚合 entry 与平台导航输入。
5. `NavKey` 参数保持类型化。外部字符串不得直接反序列化或写入返回栈，必须先经过白名单映射。

### 5.2 六个独立返回栈

| 顶层分区 | 初始根 | 抽屉切换 | 系统返回 |
| --- | --- | --- | --- |
| 随笔 | 随笔根 | 恢复上次随笔位置 | 栈内非根先弹出；位于随笔根时退出应用 |
| 锦囊 | 锦囊根 | 恢复上次锦囊位置 | 栈内非根先弹出；位于根时切回并恢复随笔栈 |
| 待办 | 待办根 | 恢复上次待办位置 | 栈内非根先弹出；位于根时切回并恢复随笔栈 |
| 个人中心 | 个人中心根 | 恢复上次个人中心位置 | 栈内非根先弹出；位于根时切回并恢复随笔栈 |
| 归档 | 归档根 | 恢复上次归档位置 | 栈内非根先弹出；位于根时切回并恢复随笔栈 |
| 设置 | 设置根 | 恢复上次 Settings 能力页位置 | 栈内非根先弹出；位于根时切回并恢复随笔栈 |

应用首次启动默认激活随笔栈。通过抽屉选择分区时恢复该分区原栈，选择当前分区不清栈、不回根，只保持现场。详情页归属发起它的分区。系统不维护额外的“顶层分区访问历史”，因此非随笔根返回的结果始终是随笔，而不是上一个访问过的分区。

六个栈及当前激活分区使用 Navigation 3 的可保存状态机制。配置变更和系统可恢复的进程重建后，合法且可反序列化的 `NavKey` 按原分区恢复；无法映射的旧值或未知值被拒绝，不注入替代字符串 Route。

### 5.3 平台入口映射

1. 分享入口保留六个分区的现有现场，激活随笔栈，并把符合现有分享语义的类型化目标压入随笔栈。
2. Todo 通知保留六个分区的现有现场，激活待办栈，并把对应类型化 Todo 目标压入待办栈。
3. 其他现有外部入口由 app 解析平台 Intent 或通知负载，再调用 `:core:navigation` 的白名单映射器。
4. 未知外部字符串返回明确的 `Rejected` 映射结果，当前分区与全部返回栈保持不变。未知值不回落到任意首页，也不作为原始 Route 继续导航。
5. 外部输入解析失败可记录为类型化诊断，但本设计不新增用户可见的外部入口错误页。

## 6. Settings 信息架构

### 6.1 固定页面集合

Settings 首页只负责导航，不承载开关、写操作、立即同步、退出登录、清理缓存或权限请求。首页之后固定为六个能力页，顺序如下：

1. 账号与同步。
2. 记录与编辑。
3. 提醒与日历。
4. 存储与离线。
5. 外观与交互。
6. 关于与高级。

设置首页拥有独立的轻量摘要 ViewModel。每个能力页拥有独立 ViewModel、`UiState`、用户意图、能力接口和测试面。六页之间不共享聚合全部设置的 ViewModel，也不重新建立跨页巨型 `SettingsUiState`。

“账号管理”和“高级同步/故障处理”是“账号与同步”能力页内的次级视图，继续使用 `AccountSyncSettingsCapability`，不形成第七个能力页。“修改密码”和“退出登录”只出现在账号管理次级视图；“全量重同步”只出现在高级同步/故障处理次级视图。

### 6.2 设置首页摘要的数据边界

`SettingsHubCapability` 输出包含六个独立 `SectionSummaryState` 的只读投影。每项可分别处于加载、就绪或错误状态，一项失败不能阻塞其余五项。摘要必须来自本地设置、已有轻量观察流或已经缓存的状态，不能为了首页摘要启动网络同步、更新检查、存储全盘扫描、日历写入、诊断导出或其他重任务。

| 首页项 | 允许读取的摘要边界 | 异常优先内容 | 明确禁止 |
| --- | --- | --- | --- |
| 账号与同步 | 账号是否可用、最近同步结果、已有最近成功时间 | 鉴权失效、最近同步失败、全量重同步失败 | 发起登录、常规同步或全量重同步 |
| 记录与编辑 | 已持久化的现有记录和编辑偏好 | 偏好读取失败 | 修改默认值、触发保存或同步 |
| 提醒与日历 | 已有提醒设置、已知的日历连接或权限状态 | 权限或日历连接的已有异常状态 | 请求权限、写系统日历、重建提醒 |
| 存储与离线 | 已缓存的空间统计、离线状态与既有配置 | 已知存储或离线异常 | 扫描全部文件、预取附件、清理缓存 |
| 外观与交互 | 当前主题、明暗和已有交互偏好 | 偏好读取失败 | 切换主题、写入动效设置 |
| 关于与高级 | 已安装版本和已有缓存的更新或诊断状态 | 已知更新或诊断异常 | 联网检查更新、下载、安装或导出诊断 |

每行最多展示两行普通摘要。存在需处理的异常时，异常行优先于普通摘要，普通摘要可缩减但不能把异常挤出可见区。首页摘要不提供手动刷新动作，也不因页面进入执行副作用。

### 6.3 Settings 首页视觉方案

已选方案为 A“线册式总览”。页面采用单列纵向册页，固定显示六行，结构与能力页顺序一致。每行包含：

1. 一个清楚可读的序号印记，依次为 1 至 6。
2. 能力名称。
3. 最多两行轻量摘要。
4. 必要时一行文字异常提示。
5. 明确的“进入” affordance。

整行是一个最小 `48dp` 高的导航触控目标。首页不显示 Switch、Checkbox、滑杆、内联按钮、危险动作或任何写操作。异常必须有文字和语义，颜色只作辅助。六行在紧凑、中等和展开窗口中都保持同一阅读顺序，不改成两列矩阵；较宽窗口使用居中的单列内容区域，最大内容宽度为 `720dp`。

### 6.4 “账号与同步”代表能力页视觉方案

已选方案为 A“诊断前置册页”，并固定采用“同步健康优先”。首屏从上到下的顺序是：

1. 页面返回操作与“账号与同步”标题。
2. 同步健康状态、用户可理解的说明和当前唯一主恢复动作。
3. 最近成功摘要与当前账号摘要。
4. 账号管理入口。
5. 低强调度的高级同步/故障处理入口。

普通同步失败时，主动作是“立即同步”。鉴权失效是与普通失败互斥的状态，首要文案改为登录失效，主动作改为“重新登录”，同时不展示“立即同步”。页面任何时刻只展示一个当前故障，不同时把普通失败和鉴权失效都描述为当前问题。

修改密码与退出登录下沉到账号管理，不在同步健康卡片中出现。全量重同步下沉到高级同步/故障处理，必须先展示影响说明和确认，不与“立即同步”并列为日常主操作。页面不提供队列清理、同步历史管理、自动冲突解决或自动修复。

视觉比较稿中的 `08:42`、`08:47`、`HTTP 400`、服务器地址和用户名只是线框示例，不是产品常量、默认值或验收数据。实现必须显示真实领域状态，并把底层错误映射为用户能理解的文案。

### 6.5 “账号与同步”状态矩阵

页面使用单一互斥状态模型。鉴权类失败总是映射为“鉴权失效”，不会再同时保留“普通失败”；全量重同步因鉴权失败而终止时也映射为“鉴权失效”。下表的主动作是同步健康区域中唯一高强调度动作。

| 状态 | 首要文案 | 主动作 | 全量重同步可见性 |
| --- | --- | --- | --- |
| 未绑定同步账号 | “尚未连接同步账号” | “登录”，进入现有登录流程，不创建新模式 | 隐藏 |
| 已有配置但未登录 | “尚未登录” | “登录”，进入现有登录流程 | 隐藏 |
| 健康 | “同步正常”并显示真实最近成功摘要 | “立即同步” | 根页面只显示低强调度“高级同步”入口；实际危险动作在次级视图并需确认 |
| 同步中 | “正在同步” | 禁用态“同步进行中” | 高级入口可见，实际危险动作禁用，不能并发启动 |
| 已排队 | “同步已排队” | 禁用态“等待同步” | 高级入口可见，实际危险动作禁用，不能清理或重排队列 |
| 普通失败 | “最近一次同步失败”并显示用户可理解原因 | “立即同步” | 高级入口可见；实际危险动作仍在次级视图并需确认 |
| 鉴权失效 | “登录已失效” | “重新登录” | 隐藏；不显示“立即同步”或全量重同步 |
| 全量重同步进行中 | “正在全量重同步”并显示已有进度状态 | 禁用态“重同步进行中” | 高级入口可见，危险动作显示进行中并禁用；不新增取消能力 |
| 全量重同步失败 | “全量重同步未完成”并显示用户可理解原因 | “查看故障处理”，进入同一能力页的高级同步次级视图 | 次级视图显示“重试全量重同步”，执行前再次确认 |
| 全量重同步完成 | “全量重同步已完成” | 无新增主动作；按现有完成状态流回到最新稳定同步健康状态 | 完成结果显示期间危险动作禁用；回到稳定状态后按该状态规则展示 |

“立即同步”只请求现有常规同步能力。“重新登录”只进入现有认证流程。“全量重同步”只使用现有强制全量同步语义。本设计不承诺自动修复、冲突合并、同步历史、队列清理或新增取消能力。

## 7. 状态、用户意图、错误与一次性结果

### 7.1 页面状态

1. 每个 ViewModel 的 `UiState` 只描述本页可稳定重建的内容，包括加载、数据、当前互斥状态、稳定错误和动作可用性。
2. 用户可见错误先由 `:core:settings` 映射为领域错误，再由 `:feature:settings` 映射为资源文案。ViewModel 不接收 Retrofit exception、HTTP response、WorkInfo 原始失败对象或 WorkManager exception。
3. 同一动作进行中必须在 `UiState` 中禁用重复提交。并发规则由能力接口保证，Compose 不自行推断后台任务状态。
4. Settings 首页每项摘要独立加载。能力页只加载本页所需数据，不读取其他五页状态。

### 7.2 一次性 UI 结果

导航、Toast、Dialog、权限请求和需要交给 Android launcher 的一次性动作通过页面专属的一次性 UI 结果流发送，由处于已启动生命周期的 Compose entry 单次消费，结果流不重放已消费事件。

1. 导航结果由 Compose 转换为窄 Navigator 调用，ViewModel 不持有 Navigator。
2. Toast 只用于短暂确认，不代替 `UiState` 中的持久成功或错误说明。
3. Dialog 结果用于确认退出登录、全量重同步等已有危险动作。确认后再向 ViewModel 提交明确用户意图。
4. 权限请求由 Compose 使用平台 launcher 执行，结果再作为用户意图回传 ViewModel；能力层只接收领域化权限结果。
5. 页面离开后未消费的一次性结果不在新页面重放。仍需展示的状态必须保留在 `UiState`。

## 8. 视觉与可访问性规则

### 8.1 纸墨视觉

1. 严格继承 `DESIGN.md` 的纸、墨、线、印。浅色使用草白背景和米宣纸表面，深色使用墨灰背景和玄青纸面，标题保持系统衬线，正文保持系统无衬线。
2. 页面层次依靠 `background/surface` 色阶、细描边、纸面横线和低透明度主题竖线，不使用阴影、渐变、玻璃、模糊、发光或通用悬浮卡片。
3. 主色只用于印记、强调、焦点和必要状态，不铺设大面积装饰色。
4. 新页面优先组合现有 `InkCard`、`ScrollPaper`、`ScrollPaperSurface` 和印章原语。若现有原语缺少焦点、触控或 reduced-motion 支持，应在本次迁移触及的共享原语中补齐，不在业务页面复制另一套样式。

### 8.2 可访问性

1. 所有可点击区域至少 `48dp × 48dp`，包括返回、整行入口、次级入口和危险动作。
2. 状态、选择、成功、警告、错误、同步中和禁用不能只靠颜色，必须同时提供文字、图标或语义状态。
3. 自定义无 ripple 控件必须有清晰可见的键盘、D-pad、Switch Access 和无障碍焦点边界。
4. 字体缩放不得关闭。标题、两行摘要、异常行、按钮和状态说明允许换行或扩展，不能裁切关键内容。
5. TalkBack 顺序严格跟随视觉与任务顺序。Settings 首页按 1 至 6 遍历；账号与同步按标题、健康状态、主动作、摘要、账号管理、高级同步遍历。
6. 动态错误、同步状态变化、权限结果和完成结果需要适当的可访问性播报。Dialog 关闭后焦点回到触发控件。
7. 尊重系统减少动态效果设置。启用时移除非必要的缩放、旋转和遮罩过渡，同时保留立即可见的文字结果。
8. 普通文字目标对比度至少 `4.5:1`，大号或粗体文字和关键非文本状态至少 `3:1`，三套主题的浅色与深色都要验证。

### 8.3 三窗口行为

视觉 QA 固定使用以下三个窗口，不用单一手机截图代替：

| 窗口 | 验证尺寸 | 结构要求 |
| --- | --- | --- |
| 紧凑 | `360dp × 800dp` | 单列、无横向滚动、标题与摘要不裁切 |
| 中等 | `600dp × 960dp` | 单列居中，阅读顺序不变，触控和焦点不漂移 |
| 展开 | `840dp × 900dp` | 单列内容不超过 `720dp`，不变成未批准的矩阵或双栏 |

三个窗口均验证默认纸墨主题的浅色与深色、系统大字体和 TalkBack 顺序，并抽查黛蓝与赛博主题没有对比度或状态表达回退。

## 9. 一次稳定版内的内部迁移阶段

Navigation 3 和 Settings 重构必须在同一个稳定版本完成。内部阶段可分别提交和验证，但任何阶段都不是可正式发布的长期过渡架构。只有全部阶段完成并通过完整门禁后才发布。

| 阶段 | 工作内容 | 本阶段独立验证 |
| --- | --- | --- |
| 1. 基线与依赖锁定 | 记录当前行为与测试基线；按下述规则精确锁定稳定 Navigation 3、SDK、AGP、Kotlin、Compose、Lifecycle、Activity 和 Hilt 组合；把 `compileSdk` 提升到至少 36 | 版本目录无动态或预发布版本；现有测试、Lint 和 Benchmark 变体在升级后可构建；`targetSdk 34` 与 `minSdk 33` 未漂移 |
| 2. 平台模块拆分 | 创建 `:core:update`、`:core:calendar`、`:core:quicktiles`、`:core:externalactions`、`:core:diagnostics`，移动各自 Android 能力，通过 app 窄端口接收包身份和应用内目标 | 每个模块可独立编译；依赖图不存在 `core -> app/feature`；Manifest 合并后的组件、authority 和 Intent 行为不变 |
| 3. Navigation 3 契约与返回栈 | 在 `:core:navigation` 建立可序列化 `NavKey`、六分区模型、独立返回栈、窄 Navigator 和外部输入映射 | 纯 Kotlin 状态机、序列化、返回规则、分享、Todo 通知和未知映射测试通过 |
| 4. Feature entries 迁移 | 各 Feature 提供自己的 entry；app 改为聚合 contributor 和处理平台输入；页面导航改用类型化键 | 每个键恰有一个 entry；Feature 无相互依赖；ViewModel 无 Navigator；六分区手动导航冒烟通过 |
| 5. Settings 能力层与 Gradle 边界 | 在 `:core:domain` 定义首页与六页契约，在 `:core:settings` 实现编排和错误映射；移除 Settings 对 data/network/sync/Retrofit/WorkManager 的直接依赖，并把 Hilt 限定为 ViewModel 构造集成 | 六个能力接口测试、首页投影测试、Hilt 组合根测试和 Gradle 边界断言通过 |
| 6. Settings UI 六页 | 实现线册式首页、六个独立能力页、页面 ViewModel、一次性结果和账号与同步全部状态 | Compose 关键状态、用户动作、错误、权限、字体、TalkBack 和三窗口视觉 QA 通过 |
| 7. 清理旧结构 | 删除旧字符串 Routes、旧 `NavHost/NavController` 入口、旧聚合 `SettingsUiState`、巨型 Settings ViewModel 和直接基础设施依赖；同步更新派生架构文档 | 搜索和依赖检查确认旧入口与依赖已消失；`ARCHITECTURE.md` 不再把旧结构描述为当前事实 |
| 8. 完整验证与发布 | 执行完整单元、Compose、Lint、Benchmark、profile、安装冒烟、签名和发布门禁 | 全部门禁通过后才生成稳定签名 Benchmark APK、推送 main、创建稳定 Tag、等待 Actions 并发布 GitHub latest Release |

### 9.1 实施日依赖锁定规则

实施计划必须按以下固定顺序选版本，不留人工偏好分叉：

1. 以第一天实施日期为锁定日，选择该日期前已经正式发布的最高 Navigation 3 稳定版。
2. 使用该版本官方要求的 `compileSdk`，且结果不得低于 36；本轮 `targetSdk` 固定为 34，`minSdk` 固定为 33。
3. 选择官方支持该 `compileSdk` 的最高 AGP 稳定版。
4. 选择该 AGP 官方支持、且与 Compose 编译链兼容的最高 Kotlin 稳定版。
5. 选择与已选 Kotlin 和 Compose 编译链兼容的最高 Compose BOM 稳定版，再依次选择兼容的 Activity、Lifecycle 和 Hilt 稳定版。
6. 如果后选项与已锁定项不兼容，只下调当前正在选择的项到最近兼容稳定版，不反向改用预发布版本。
7. 所有版本在 `libs.versions.toml` 精确写定。禁止动态版本、Alpha、Beta、RC、Canary、EAP 和 Snapshot。
8. implementation plan 必须记录最终版本表和官方兼容依据，执行者不得凭本地缓存猜测版本。

## 10. 测试与验收矩阵

| 领域 | 必须覆盖 | 通过条件 |
| --- | --- | --- |
| 纯 Kotlin 导航 | 六个根栈、分区切换保留现场、当前分区重复选择、栈内返回、非随笔根返回随笔、随笔根退出、序列化恢复 | 状态机测试逐项断言，所有栈变化符合第 5 节 |
| 外部导航映射 | 分享进入随笔栈、Todo 通知进入待办栈、原有现场保留、未知字符串拒绝 | 未知值不改变任何栈；合法输入只写入目标分区 |
| Entry 所有权 | 每个 `NavKey` 唯一 entry、Feature entry 自有、app 只聚合 | 无重复或缺失 entry，Feature 之间无依赖 |
| Settings 首页 | 六项独立加载、摘要最多两行、异常优先、单项失败隔离、无写操作 | 首页进入不触发网络、同步、扫描、权限、更新或诊断副作用 |
| 六页 ViewModel | 初始加载、稳定状态、每个现有用户意图、重复提交禁用、领域错误映射、一次性结果 | 每个 ViewModel 只使用本页一个能力接口，测试不需要 Retrofit 或 WorkManager fake |
| 六个能力接口 | 观察流、命令编排、成功、可恢复错误、鉴权错误、平台错误映射 | `:core:settings` 测试覆盖跨模块编排，领域层不泄漏基础设施异常 |
| 账号与同步 | 第 6.5 节全部状态、唯一主动作、鉴权互斥、全量重同步确认和动作可见性 | 每个状态有 Compose 截面测试；普通失败与鉴权失效不能同时出现 |
| Hilt 与依赖边界 | 六页接口绑定、平台窄端口、默认单例唯一、Feature Settings 依赖 | Hilt 编译与注入测试通过；`:feature:settings` 只保留 ViewModel 构造集成，不含 Hilt Provider/Binding 或 data/network/sync/Retrofit/WorkManager 依赖 |
| Compose 交互 | 抽屉切换、首页六入口、返回、账号管理、高级同步、Dialog、Toast、权限结果 | Compose 单次消费一次性结果；旋转或重组不重复执行动作 |
| 可访问性 | `48dp` 触控、焦点可见、文字加图标状态、大字体、TalkBack 顺序、动态播报、减少动态效果 | 紧凑、中等、展开三个窗口均无裁切、顺序错误或只靠颜色的状态 |
| 视觉 QA | 首页 A、账号与同步 A、纸墨浅深色、黛蓝和赛博抽查 | 无阴影、渐变、玻璃；线、描边、印记和字体关系符合 `DESIGN.md` |
| 平台回归 | WorkManager、Room、FileProvider、外部 Intent、更新、日历、快捷开关、外部动作、诊断 | 字面量和现有行为不变，模块移动不改变系统注册与调用结果 |
| 性能与发布 | app Benchmark、baseline profile、macrobenchmark、签名与安装冒烟 | 目标仍为 `:app`，稳定签名 Benchmark APK 可安装并完成关键路径 |

### 10.1 不可变回归约束

以下项目在迁移中保持字面量和行为不变：

1. 包名仍为 `cc.pscly.onememos`。
2. `MemosSyncWorker.UNIQUE_WORK_NAME` 仍为 `one_memos_sync`。
3. 同步输入键仍为 `force_full_sync`、`is_periodic` 和 `followup_sync`。
4. 周期同步 unique name 与 tag 仍为 `one_memos_periodic_sync`。
5. 派生字段重建任务名仍为 `one_memos_rebuild_memo_derived_fields`。
6. 附件预取任务名仍为 `one_memos_attachment_prefetch`。
7. Room 数据库版本仍为 9，数据库文件名仍为 `one_memos.db`。
8. FileProvider authority 仍为 `${applicationId}.fileprovider`；路径仍为 `share_cards/`、`screenshots/` 和 `shared/`。
9. 外部编辑入口 extra 仍为 `cc.pscly.onememos.extra.START_EDITOR_UUID`。
10. `baselineprofile` 与 `macrobenchmark` 的 `targetProjectPath` 仍为 `:app`。
11. `OneMemosApplication` 继续持有 `Configuration.Provider`、`HiltWorkerFactory` 和 `ImageLoaderFactory` 的 Application 级初始化责任。

旧字符串 Routes 是本次被替换的内部导航机制，不作为最终结构继续保留。迁移必须先建立等价类型化 `NavKey` 和参数测试，再删除旧 Routes；不得留下两套可写导航源。

### 10.2 完整工程门禁

实施完成后至少执行并通过以下门禁，命令在项目根目录使用 Windows PowerShell 7 和 Gradle Wrapper：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
.\gradlew.bat testDebugUnitTest --stacktrace
.\gradlew.bat lint --stacktrace
.\gradlew.bat :app:assembleBenchmark --stacktrace
.\gradlew.bat :baselineprofile:assembleBenchmark --stacktrace
.\gradlew.bat :macrobenchmark:assembleBenchmark --stacktrace
```

此外必须安装固定发布签名的 Benchmark APK，人工走查六分区返回栈、分享入口、Todo 通知入口、Settings 六入口、账号与同步全部关键状态，以及现有更新、日历、快捷开关、外部动作和诊断路径。需要设备的 benchmark/profile 运行按正式发布门禁执行，不能用 Debug APK 代替最终验收。

## 11. 派生文档与旧结构清理

`ARCHITECTURE.md` 是实施期间必须更新的派生文档。阶段 7 完成时，它必须准确反映最终 Gradle 模块、依赖方向、DI 所有权、Navigation 3 entry 所有权、六个独立返回栈和 Settings 能力接口，不能继续声称旧 package 结构、字符串 Routes 或过渡依赖是当前事实。

同步更新 `ARCHITECTURE.md` 不会改变本文的设计权威。若实施发现代码与本文冲突，应停止实施并回到书面设计审阅，不能在代码中静默建立第二套架构。

## 12. 稳定发布约束

当前稳定基线为 `v1.8.11`。实施版本按以下确定规则取值：

1. 如果实施开始时 `v1.8.11` 仍是仓库和 GitHub 的最新稳定版，目标版本固定为 `v1.9.0`。
2. 如果已经存在更高稳定版 `vMAJOR.MINOR.PATCH`，目标版本固定为同一 `MAJOR` 下的下一次 minor，即 `vMAJOR.(MINOR+1).0`。
3. `versionCode` 取最新稳定版对应 `versionCode + 1`，并确保高于 main 上已有值。
4. Navigation 3 与 Settings 迁移只发布一个完整稳定版，不发布长期过渡版，也不以预发布或仅 Artifact 形式交付。

完整发布必须遵守仓库 `AGENTS.md`：

1. 使用固定发布签名构建 Benchmark APK，不使用临时或漂移签名。
2. Benchmark APK 文件名包含本地生成日期时间，遵守仓库脚本与 `AGENTS.md` 的 `YYYY-MM-DDTHH-mm-ss.apk` 时间戳规则。
3. 发布前后核验 applicationId、版本号、SHA-256 和签名证书摘要。
4. 完整门禁通过后提交并推送 `main`。
5. 创建并推送对应稳定 Tag `vMAJOR.MINOR.PATCH`。
6. 等待 GitHub Actions 成功。
7. 创建非草稿、非预发布的 GitHub latest Release，并只上传固定签名 Benchmark APK。
8. 任一发布环节失败时修复并完成同一版本闭环，不能把本地 APK 或流水线 Artifact 视为完成。

本文档阶段不修改版本，不运行 Gradle，也不生成 APK。只允许提交本规格及必要的会话留痕，不推送 main，不创建 Tag 或 Release。

## 13. 设计完成与实施授权关口

本文关闭了 Navigation 3、Settings 架构、平台模块、首页视觉和代表能力页的设计分叉。用户已于 2026-07-14 书面批准本规格；implementation plan 已完成编写、自审和 Momus 阻塞审查。该批准与审查结论都没有授权实施；计划本地提交后仍须由用户明确授权执行，届时才可修改代码、Gradle、资源、Manifest、版本或发布配置。

## 14. 设计批准记录

| 已选择项目 | 已批准选择 | 选择含义 | 授权边界 |
| --- | --- | --- | --- |
| Settings 首页 | A“线册式总览” | 六行纵向册页，只显示摘要、异常和进入提示 | 选择不等于实现授权 |
| 代表能力页 | “账号与同步” | 用该页验证六页架构、深接口、状态与视觉规则 | 选择不等于实现授权 |
| 首屏信息优先级 | “同步健康优先” | 首屏先给当前健康、唯一恢复动作、最近成功与账号摘要 | 选择不等于实现授权 |
| 账号与同步详细页 | A“诊断前置册页” | 异常和恢复动作前置，账号管理与全量重同步下沉 | 选择不等于实现授权 |
| 完整设计规格 | 用户于 2026-07-14 书面批准 | 允许据此编写、自审和提交 implementation plan | 不授权执行计划或修改 APK 行为 |
