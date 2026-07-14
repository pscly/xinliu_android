# Navigation 3 与设置中心重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在一个完整稳定版本中迁移到 Navigation 3，建立六个可恢复的顶层独立返回栈，并把 Settings 改造成只读首页与六个由深能力接口驱动的独立页面。

**Architecture:** `:app` 继续作为唯一组合根，只聚合各 Feature 的无状态 entry contributor、托管 Navigation 3 Host、解析平台输入并完成 Hilt 最终装配。`:core:navigation` 拥有类型化键和六栈状态，`:core:domain` 拥有设置首页与六页契约，`:core:settings` 编排六个新 Core 模块和既有基础设施；Feature 自己构造页面并取得 Hilt ViewModel，Navigator 只停留在 Compose entry 层。

**Tech Stack:** Kotlin、Jetpack Compose、Navigation 3、Kotlin Serialization、Coroutines Flow、Hilt、JUnit 4、Robolectric、Compose UI Test、Gradle Wrapper、Benchmark、Baseline Profile、Macrobenchmark、GitHub Actions

---

## 计划依据与授权边界

- 唯一批准规格是 `docs/superpowers/specs/2026-07-14-settings-navigation3-redesign-design.md`，实现和验收必须逐项满足该规格。
- 用户已批准规格和本实施计划的编写，但尚未授权执行。开始修改代码、Gradle、资源、Manifest、版本或发布配置前，仍须取得用户明确授权。
- 工具链版本已按规格 §9.1 锁定在下表，并由 Task 1 原子迁移；执行者不得改用另一套 Kotlin 模式、预发布版本或本地缓存中的近似版本。
- 本计划不新增 `:feature:archived`。归档根页面始终由 `:feature:home` 提供，并使用 `HomeScreenMode.ARCHIVED`。
- `QuickCaptureActivity`、`QuickCaptureOverlayEntryActivity`、`QuickCaptureOverlayPickImagesActivity` 与 `ScreenshotQuickCaptureActivity` 继续是 `:app` 拥有的独立 Activity。Quick Capture 不进入 Navigation 3 目的地集合。
- 所有命令都在仓库根目录执行。Windows 先执行 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8`；Linux 可在命令前使用 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`。本文只记录命令，不表示已执行。

## 已锁定工具链与官方兼容依据

锁定日为 2026-07-14。下表是本计划直接锁定的唯一工具链结论，不读取或引用任何未跟踪工具链裁决文档。Android 模块统一采用 AGP 内置 Kotlin，不保留“禁用内置 Kotlin”的第二方案；纯 Kotlin 的 `:core:model` 与 `:core:domain` 使用同版 Kotlin JVM plugin。最终版本如下：

| 项目 | 锁定值 | 实施约束 |
| --- | --- | --- |
| Android Gradle Plugin | `9.2.1` | 使用 AGP 9 默认内置 Kotlin，不再应用 `org.jetbrains.kotlin.android` |
| Gradle Wrapper | `9.4.1` | `distributionUrl` 精确指向 `gradle-9.4.1-bin.zip` |
| Gradle daemon / 脚本 / CI JDK | `21` | `gradle-daemon-jvm.properties`、Windows/Linux 环境脚本与 GitHub Actions 全部固定 JDK 21 |
| AGP 内置 Kotlin / Compose Compiler plugin | `2.3.10` | `org.jetbrains.kotlin.plugin.compose` 与内置编译器同版 |
| KSP | `2.3.10` | 全部 Hilt/AndroidX Hilt 处理器从 kapt 迁到 KSP，不启用 legacy kapt |
| Compose BOM | `2026.06.00` | Compose 库继续只由 BOM 对齐 |
| Navigation 3 | `1.1.4` | 使用 `navigation3-runtime` 与 `navigation3-ui`；Navigation Compose 2 仅作为迁移期旧依赖保留到 Task 31 |
| Activity | `1.13.0` | 精确版本 |
| Lifecycle | `2.11.0` | 精确版本 |
| Dagger / Hilt | `2.60.1` | 精确版本 |
| AndroidX Hilt | `1.4.0` | `hilt-navigation-compose`、`hilt-work` 与 compiler 同线 |
| kotlinx.serialization | `1.11.0` | `org.jetbrains.kotlin.plugin.serialization` 与 Kotlin `2.3.10` 配套应用 |
| Android SDK | `compileSdk 36`、`targetSdk 34`、`minSdk 33` | 只提升 compileSdk；targetSdk 与 minSdk 不漂移 |
| Java / Kotlin 字节码目标 | `JavaVersion.VERSION_21` / `JvmTarget.JVM_21` | 所有 Android 与 JVM 模块统一，不保留 17 目标 |
| SDK Build Tools | 精确 `36.0.0` | 所有 Android 模块声明该版本；本地、CI 与发布核验只使用该目录中的 `aapt2`、`apksigner` |

官方兼容依据固定为 [AGP 9.2 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)、[AGP built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)、[Navigation 3 releases](https://developer.android.com/jetpack/androidx/releases/navigation3)、[Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)、[KSP releases](https://github.com/google/ksp/releases/tag/2.3.10)、[Dagger releases](https://github.com/google/dagger/releases/tag/dagger-2.60.1)、[Activity releases](https://developer.android.com/jetpack/androidx/releases/activity)、[Lifecycle releases](https://developer.android.com/jetpack/androidx/releases/lifecycle) 与 [AndroidX Hilt releases](https://developer.android.com/jetpack/androidx/releases/hilt)。AGP 9.2.1 的**最低可运行 JDK 是 17**，这只是厂商下限；本项目更严格地把 daemon、脚本、CI、Java source/target 和 Kotlin bytecode 全部固定为 21。AGP 内置 KGP、Compose Compiler plugin 与 KSP 均精确为 `2.3.10`，Build Tools 精确为 `36.0.0`。

## 执行协议

### 行为任务的固定节奏

每个新增或改变行为的任务都按以下顺序执行，不能跳过失败证据：

1. 写一个只覆盖当前行为切片的 JUnit 4、Robolectric 或 Compose 测试。
2. 运行文中给出的 RED 命令，确认失败原因正是缺少当前行为，而不是环境、编译或旧测试故障。
3. 写让该测试通过的最小实现，不顺手重构相邻区域。
4. 运行同一聚焦命令，确认 PASS。
5. 运行任务所属模块的 `testDebugUnitTest` 与 `assembleDebug` 门禁。
6. 按 `AGENTS.md` 在根 `.ai_session.md` 追加本任务的中文目的、决策、文件和验证结果；不得改写历史记录。
7. 只暂存本任务列出的文件和 `.ai_session.md`，检查 diff 后创建中文原子提交。

PowerShell 7 通用模板：

```powershell
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
.\gradlew.bat :模块:testDebugUnitTest --tests 完整测试类名 --stacktrace
.\gradlew.bat :模块:testDebugUnitTest :模块:assembleDebug --stacktrace
git status --short
git diff --check
# 先追加本任务留痕，再暂存会话文件。
git add .ai_session.md
# 使用每个 Task 末尾列出的精确 git add 命令；移动任务可用 git add -A -- <精确路径>，禁止无 pathspec 的 git add -A 或 git add .。
git diff --cached --check
git commit -m "类型(范围): 中文说明"
```

Linux 等价模板：

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :模块:testDebugUnitTest --tests 完整测试类名 --stacktrace
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :模块:testDebugUnitTest :模块:assembleDebug --stacktrace
```

### 纯 Gradle、建模块与移动任务的固定节奏

这类任务不制造无意义的失败单测。先写或补齐现状特征测试，运行移动前构建并保存通过证据；完成最小移动后，再运行同一测试和构建门禁。Manifest、FQCN、authority、资源名、DataStore 名称、更新校验和系统回退都必须在移动前后保持一致。后续每个 Task 的提交命令块均以前述 `git add .ai_session.md` 已执行为前提。

### 并行分组与独占文件

只有依赖任务全部完成后才启动对应并行组。每个文件同时只允许一个执行者修改，组内通过以下独占范围避免冲突：

| 分组 | 可并行工作 | 独占文件范围 | 汇合门禁 |
| --- | --- | --- | --- |
| A | Task 2 的六模块空骨架 | Task 2 由集成执行者独占 `settings.gradle.kts`、`app/build.gradle.kts` 和六个新模块 build/Manifest | 六模块分别 `assembleDebug`，再执行 `:app:assembleDebug` |
| B | Task 3 至 Task 7 **严格串行** | 每项同时触及 app 组合根、模块依赖或迁移期旧入口；顺序固定为 update、calendar、quicktiles、externalactions、diagnostics。Task 4 与 Task 7 又共同修改旧 `SettingsScreen.kt`，不得并行或交换顺序 | 每项结束都运行对应 Core、原消费者与 `:app:assembleDebug` |
| C | Task 11 的非 Settings Feature contributor | 可分别独占 `feature/home/**`、`feature/collections/**`、`feature/todo/**`、`feature/profile/**`、`feature/editor/**`、`feature/sharecard/**`、`feature/auth/**` 或 `feature/welcome/**`；app 的临时 Settings bridge 和 registry 由集成执行者串行完成 | `:core:navigation:testDebugUnitTest`、全部 Feature `assembleDebug` 与 `:app:assembleDebug` |
| D | Task 14 至 Task 19 的六个 capability 实现 | 每位执行者只拥有 `core/settings/src/**/account/**`、`record/**`、`reminder/**`、`storage/**`、`appearance/**` 或 `about/**` 之一；所有 Hilt 绑定文件由 Task 21 在 app 独占 | `:core:settings:testDebugUnitTest` |
| E | Task 24 至 Task 29 的六个 Settings 能力页 | 每位执行者只拥有对应页面 Kotlin、测试和页面专属资源文件 `settings_account_strings.xml`、`settings_record_strings.xml`、`settings_reminder_strings.xml`、`settings_storage_strings.xml`、`settings_appearance_strings.xml` 或 `settings_about_strings.xml` | `:feature:settings:testDebugUnitTest` 与 `assembleDebug` |

以下文件始终串行、由集成执行者独占：`settings.gradle.kts`、`build.gradle.kts`、`gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、全部模块 `build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`、`app/src/main/java/cc/pscly/onememos/MainActivity.kt`、`app/src/main/java/cc/pscly/onememos/di/**`、`scripts/verify.sh`、`scripts/verify.ps1`、`.github/workflows/android-benchmark.yml`、`ARCHITECTURE.md`。

## 目标文件结构

计划完成后，各新职责落在以下位置。现有包名需要保留组件 FQCN 时，模块移动不改 `package` 声明。

```text
core/domain/src/main/java/cc/pscly/onememos/domain/settings/
  SettingsHubCapability.kt
  SettingsCapabilityError.kt
  AccountSyncSettingsCapability.kt
  RecordEditingSettingsCapability.kt
  ReminderCalendarSettingsCapability.kt
  StorageOfflineSettingsCapability.kt
  AppearanceInteractionSettingsCapability.kt
  AboutAdvancedSettingsCapability.kt

core/domain/src/main/java/cc/pscly/onememos/domain/repository/
  MemoBrowseScope.kt

core/data/src/main/java/cc/pscly/onememos/data/paging/
  MemoPagingDataSource.kt
  RoomMemoPagingDataSource.kt

feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/
  HomeMemoPagingSource.kt

app/src/main/java/cc/pscly/onememos/di/
  HomeMemoPagingSourceAdapter.kt
  HomePagingModule.kt

core/navigation/src/main/java/cc/pscly/onememos/navigation/
  NavKeys.kt
  NavigationState.kt
  OneMemosNavigator.kt
  ExternalNavigationContract.kt
  ExternalNavigationMapper.kt
  FeatureEntryContributor.kt

core/settings/src/main/java/cc/pscly/onememos/settings/
  SettingsHubCapabilityImpl.kt
  account/AccountSyncSettingsCapabilityImpl.kt
  record/RecordEditingSettingsCapabilityImpl.kt
  reminder/ReminderCalendarSettingsCapabilityImpl.kt
  storage/StorageOfflineSettingsCapabilityImpl.kt
  appearance/AppearanceInteractionSettingsCapabilityImpl.kt
  about/AboutAdvancedSettingsCapabilityImpl.kt

core/update/
core/calendar/
core/quicktiles/
core/externalactions/
core/diagnostics/

app/src/main/java/cc/pscly/onememos/di/
  AppUpdateModule.kt
  CalendarCapabilityModule.kt
  QuickTilesModule.kt
  ExternalActionsModule.kt
  DiagnosticsModule.kt
  SettingsCapabilityModule.kt

feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/
  SettingsEntryContributor.kt
  hub/SettingsHubScreen.kt
  hub/SettingsHubViewModel.kt
  account/AccountSyncScreen.kt
  account/AccountSyncViewModel.kt
  record/RecordEditingScreen.kt
  record/RecordEditingViewModel.kt
  reminder/ReminderCalendarScreen.kt
  reminder/ReminderCalendarViewModel.kt
  storage/StorageOfflineScreen.kt
  storage/StorageOfflineViewModel.kt
  appearance/AppearanceInteractionScreen.kt
  appearance/AppearanceInteractionViewModel.kt
  about/AboutAdvancedScreen.kt
  about/AboutAdvancedViewModel.kt
  common/SettingsUiEvent.kt
  common/SettingsPlatformActionDispatcher.kt
  common/SettingsUpdateDeliveryDispatcher.kt
```

## 统一类型契约

后续任务必须使用这些名称和字段。只有经书面设计复审后才能改变公共类型，不能在不同任务中创建近义替代类型。

### Navigation 3 键、分区、状态与命令

创建于 `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavKeys.kt`、`NavigationState.kt`、`OneMemosNavigator.kt`：

```kotlin
@Serializable
sealed interface OneMemosNavKey : NavKey

@Serializable data object HomeKey : OneMemosNavKey
@Serializable data object CollectionsKey : OneMemosNavKey
@Serializable data object TodoKey : OneMemosNavKey
@Serializable
data class TodoItemKey(
    val itemId: String,
    val expectedOwnerKey: String,
) : OneMemosNavKey {
    init {
        require(itemId.isNotBlank() && itemId == itemId.trim())
        require(expectedOwnerKey.isNotBlank() && expectedOwnerKey == expectedOwnerKey.trim())
    }
}
@Serializable data object ProfileKey : OneMemosNavKey
@Serializable data object ArchivedKey : OneMemosNavKey
@Serializable data object SettingsHubKey : OneMemosNavKey
@Serializable data object WelcomeKey : OneMemosNavKey
@Serializable data class EditorKey(val uuid: String? = null) : OneMemosNavKey
@Serializable data class ShareCardKey(val uuid: String) : OneMemosNavKey
@Serializable enum class AuthMode { CUSTOM_TOKEN }
@Serializable data class AuthKey(val mode: AuthMode? = null) : OneMemosNavKey
@Serializable data object AccountSyncSettingsKey : OneMemosNavKey
@Serializable data object AccountManagementSettingsKey : OneMemosNavKey
@Serializable data object AdvancedSyncSettingsKey : OneMemosNavKey
@Serializable data object RecordEditingSettingsKey : OneMemosNavKey
@Serializable data object ReminderCalendarSettingsKey : OneMemosNavKey
@Serializable data object StorageOfflineSettingsKey : OneMemosNavKey
@Serializable data object AppearanceInteractionSettingsKey : OneMemosNavKey
@Serializable data object AboutAdvancedSettingsKey : OneMemosNavKey

@Serializable
enum class TopLevelSection(
    val root: OneMemosNavKey,
) {
    HOME(HomeKey),
    COLLECTIONS(CollectionsKey),
    TODO(TodoKey),
    PROFILE(ProfileKey),
    ARCHIVED(ArchivedKey),
    SETTINGS(SettingsHubKey),
}

@Serializable
data class NavigationSnapshot(
    val activeSection: TopLevelSection = TopLevelSection.HOME,
    val stacks: Map<TopLevelSection, List<OneMemosNavKey>> =
        TopLevelSection.entries.associateWith { listOf(it.root) },
)

sealed interface NavigationCommand {
    data class Push(val key: OneMemosNavKey) : NavigationCommand
    data object Back : NavigationCommand
    data class SwitchSection(val section: TopLevelSection) : NavigationCommand
}

sealed interface BackResult {
    data object Consumed : BackResult
    data object ExitApplication : BackResult
}

interface OneMemosNavigator {
    fun push(key: OneMemosNavKey)
    fun back(): BackResult
    fun switchSection(section: TopLevelSection)
}
```

`ExternalNavigationContract.kt` 前置定义平台输入和映射结果，`ExternalNavigationMapper.kt` 只实现明确白名单，不接收可反序列化任意键的字符串：

```kotlin
sealed interface ExternalNavigationInput {
    data class SharedMemo(val uuid: String) : ExternalNavigationInput
    data class TodoNotification(
        val itemId: String,
        val expectedOwnerKey: String,
    ) : ExternalNavigationInput
    data object OpenTodoRoot : ExternalNavigationInput
    data class LegacyEditorExtra(val uuid: String) : ExternalNavigationInput
    data class LegacyRouteExtra(val value: String) : ExternalNavigationInput
}

enum class ExternalNavigationDuplicatePolicy {
    ALLOW,
    IGNORE_IF_TOP,
}

sealed interface ExternalStackMutation {
    data object ResetToRoot : ExternalStackMutation
    data class Push(
        val key: OneMemosNavKey,
        val duplicatePolicy: ExternalNavigationDuplicatePolicy =
            ExternalNavigationDuplicatePolicy.ALLOW,
    ) : ExternalStackMutation
}

sealed interface ExternalNavigationResult {
    data class Accepted(
        val section: TopLevelSection,
        val mutation: ExternalStackMutation,
    ) : ExternalNavigationResult

    data class Rejected(val reason: ExternalNavigationRejection) : ExternalNavigationResult
}

enum class ExternalNavigationRejection {
    EMPTY_VALUE,
    UNKNOWN_VALUE,
    INVALID_ARGUMENT,
}

object TodoNavigationIntentContract {
    const val ACTION_OPEN_TODO = "cc.pscly.onememos.action.OPEN_TODO"
    const val EXTRA_TODO_ITEM_ID = "cc.pscly.onememos.extra.TODO_ITEM_ID"
    const val EXTRA_TODO_OWNER_KEY = "cc.pscly.onememos.extra.TODO_OWNER_KEY"
}
```

分享和旧 editor extra 使用 `Push(EditorKey, ALLOW)`；带完整账号与条目标识的 Todo 通知使用 `Push(TodoItemKey, IGNORE_IF_TOP)`，保留其他五栈及 Todo 既有现场并打开对应条目。同一个外部 `TodoItemKey` 已位于 Todo 栈顶时不重复压入；同键只在栈内更深位置时仍可再次压入，普通 `navigator.push()` 不去重。无可信条目标识的 app 时钟回退和迁移期 `LegacyRouteExtra("todo")` 使用 `ResetToRoot`，只重置 Todo 栈。Task 12 起提醒生产者写入固定 action、item 与 owner extras；旧 `START_ROUTE=todo` 仅作为 parser 的迁移期读取，并在 Task 31 删除。

`FeatureEntryContributor.kt` 是无状态接口，Feature 只把自己拥有的键映射为 entry：

```kotlin
interface FeatureEntryContributor {
    fun owns(key: OneMemosNavKey): Boolean

    fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey>
}

interface FeatureEntryHost {
    fun openDrawer()
}
```

### Settings 领域接口、命令与结果

公共错误位于 `core/domain/src/main/java/cc/pscly/onememos/domain/settings/SettingsCapabilityError.kt`：

```kotlin
sealed interface SettingsCapabilityError {
    data object AuthenticationExpired : SettingsCapabilityError
    data object NetworkUnavailable : SettingsCapabilityError
    data object PermissionDenied : SettingsCapabilityError
    data object PlatformUnavailable : SettingsCapabilityError
    data object InvalidInput : SettingsCapabilityError
    data object AlreadyRunning : SettingsCapabilityError
    data object StorageFailure : SettingsCapabilityError
    data class Unknown(val diagnosticCode: String) : SettingsCapabilityError
}

```

只读首页接口和独立摘要位于 `SettingsHubCapability.kt`。它没有 `execute`：

```kotlin
interface SettingsHubCapability {
    fun observe(): Flow<SettingsHubSnapshot>
}

data class SettingsHubSnapshot(
    val accountSync: SectionSummaryState,
    val recordEditing: SectionSummaryState,
    val reminderCalendar: SectionSummaryState,
    val storageOffline: SectionSummaryState,
    val appearanceInteraction: SectionSummaryState,
    val aboutAdvanced: SectionSummaryState,
)

sealed interface SectionSummaryState {
    data object Loading : SectionSummaryState
    data class Ready(
        val primary: SummaryFact,
        val secondary: SummaryFact? = null,
        val issue: SummaryIssue? = null,
    ) : SectionSummaryState
    data class Error(val error: SettingsCapabilityError) : SectionSummaryState
}

data class SummaryFact(val value: String)
data class SummaryIssue(val kind: SummaryIssueKind)

enum class SummaryIssueKind {
    AUTHENTICATION_EXPIRED,
    LAST_SYNC_FAILED,
    FULL_RESYNC_FAILED,
    PERMISSION_REQUIRED,
    STORAGE_FAILURE,
    UPDATE_FAILURE,
    DIAGNOSTICS_FAILURE,
    PREFERENCE_READ_FAILURE,
}
```

六页统一采用 `observe` 加 `execute`，但每页使用自己的类型：

```kotlin
interface AccountSyncSettingsCapability {
    fun observe(): Flow<AccountSyncSettingsSnapshot>
    suspend fun execute(command: AccountSyncSettingsCommand): AccountSyncSettingsResult
}

interface RecordEditingSettingsCapability {
    fun observe(): Flow<RecordEditingSettingsSnapshot>
    suspend fun execute(command: RecordEditingSettingsCommand): RecordEditingSettingsResult
}

interface ReminderCalendarSettingsCapability {
    fun observe(): Flow<ReminderCalendarSettingsSnapshot>
    suspend fun execute(command: ReminderCalendarSettingsCommand): ReminderCalendarSettingsResult
}

interface StorageOfflineSettingsCapability {
    fun observe(): Flow<StorageOfflineSettingsSnapshot>
    suspend fun execute(command: StorageOfflineSettingsCommand): StorageOfflineSettingsResult
}

interface AppearanceInteractionSettingsCapability {
    fun observe(): Flow<AppearanceInteractionSettingsSnapshot>
    suspend fun execute(command: AppearanceInteractionSettingsCommand): AppearanceInteractionSettingsResult
}

interface AboutAdvancedSettingsCapability {
    fun observe(): Flow<AboutAdvancedSettingsSnapshot>
    suspend fun execute(command: AboutAdvancedSettingsCommand): AboutAdvancedSettingsResult
}
```

`AccountSyncSettingsCapability.kt` 固定十种互斥健康状态和命令：

```kotlin
sealed interface AccountSyncHealth {
    data object Unbound : AccountSyncHealth
    data object ConfiguredSignedOut : AccountSyncHealth
    data class Healthy(val lastSuccessAtEpochMs: Long?) : AccountSyncHealth
    data object Syncing : AccountSyncHealth
    data object Queued : AccountSyncHealth
    data class Failed(val error: SettingsCapabilityError) : AccountSyncHealth
    data object AuthenticationExpired : AccountSyncHealth
    data class FullResyncRunning(val progress: FullResyncProgress) : AccountSyncHealth
    data class FullResyncFailed(val error: SettingsCapabilityError) : AccountSyncHealth
    data class FullResyncCompleted(val completedAtEpochMs: Long) : AccountSyncHealth
}

data class FullResyncProgress(
    val stage: FullSyncStage,
    val pagesFetched: Int,
    val itemsFetched: Int,
)

data class AccountSyncSettingsSnapshot(
    val health: AccountSyncHealth,
    val accountLabel: String?,
    val lastSuccessAtEpochMs: Long?,
    val commandInFlight: AccountSyncSettingsCommand?,
)

sealed interface AccountSyncSettingsCommand {
    data object SyncNow : AccountSyncSettingsCommand
    data object Logout : AccountSyncSettingsCommand
    data class ChangePassword(
        val currentPassword: String,
        val newPassword: String,
        val repeatedPassword: String,
    ) : AccountSyncSettingsCommand
    data object FullResync : AccountSyncSettingsCommand
}

sealed interface AccountSyncSettingsResult {
    data object Success : AccountSyncSettingsResult
    data object IgnoredDuplicate : AccountSyncSettingsResult
    data class Failure(val error: SettingsCapabilityError) : AccountSyncSettingsResult
}
```

其余五页命令使用以下固定集合，现有字段全部归入且不新增产品能力：

```kotlin
sealed interface RecordEditingSettingsCommand {
    data class SetDefaultVisibility(val visibility: MemoVisibility) : RecordEditingSettingsCommand
    data class SetRegexSearchEnabled(val enabled: Boolean) : RecordEditingSettingsCommand
    data class SetShowTagCounts(val enabled: Boolean) : RecordEditingSettingsCommand
    data class SetQuickInsertTimeEnabled(val enabled: Boolean) : RecordEditingSettingsCommand
    data class SetQuickInsertTimeFormat(val format: QuickInsertTimeFormat) : RecordEditingSettingsCommand
}

sealed interface ReminderCalendarSettingsCommand {
    data class SetReminderMode(val mode: TodoReminderMode) : ReminderCalendarSettingsCommand
    data class SetCalendarEnabled(val enabled: Boolean) : ReminderCalendarSettingsCommand
    data class SetCalendar(val calendarId: Long?) : ReminderCalendarSettingsCommand
    data class SetCalendarReminderSync(val enabled: Boolean) : ReminderCalendarSettingsCommand
    data class ApplyPermissionResult(
        val granted: Set<SettingsPermission>,
    ) : ReminderCalendarSettingsCommand
    data object Reschedule : ReminderCalendarSettingsCommand
}

sealed interface StorageOfflineSettingsCommand {
    data class SetImagePrefetchEnabled(val enabled: Boolean) : StorageOfflineSettingsCommand
    data class SetPrefetchMemoLimit(val value: Int) : StorageOfflineSettingsCommand
    data class SetPrefetchImageLimit(val value: Int) : StorageOfflineSettingsCommand
    data class SetAttachmentCacheLimitMb(val value: Int) : StorageOfflineSettingsCommand
    data object RefreshStats : StorageOfflineSettingsCommand
    data object ClearImageCache : StorageOfflineSettingsCommand
    data object ClearAttachmentCache : StorageOfflineSettingsCommand
    data object ClearAllCache : StorageOfflineSettingsCommand
}

sealed interface AppearanceInteractionSettingsCommand {
    data class SetThemePalette(val palette: ThemePalette) : AppearanceInteractionSettingsCommand
    data class SetThemeMode(val mode: ThemeMode) : AppearanceInteractionSettingsCommand
    data class SetQuickCaptureOverlayEnabled(val enabled: Boolean) : AppearanceInteractionSettingsCommand
    data class SetSealStampDurationMs(val value: Int) : AppearanceInteractionSettingsCommand
}

sealed interface AboutAdvancedSettingsCommand {
    data object CheckForUpdates : AboutAdvancedSettingsCommand
    data object DownloadUpdate : AboutAdvancedSettingsCommand
    data object InstallUpdate : AboutAdvancedSettingsCommand
    data object ClearIgnoredUpdate : AboutAdvancedSettingsCommand
    data object ExportDiagnostics : AboutAdvancedSettingsCommand
    data object RequestQuickCaptureTile : AboutAdvancedSettingsCommand
    data object RequestScreenshotTile : AboutAdvancedSettingsCommand
    data object OpenQuickCapture : AboutAdvancedSettingsCommand
    data object OpenScreenshotCapture : AboutAdvancedSettingsCommand
    data object RebuildDerivedFields : AboutAdvancedSettingsCommand
    data class SetAttachmentUploadLimitMb(val value: Int) : AboutAdvancedSettingsCommand
    data class SetDeveloperOptions(val options: DeveloperOptions) : AboutAdvancedSettingsCommand
}
```

更新交付契约由 Task 3 创建在 `core/domain/src/main/java/cc/pscly/onememos/domain/update/UpdateDeliveryAction.kt`，供全局更新提示和 Settings 共用：

```kotlin
sealed interface UpdateDeliveryAction {
    data class OpenUnknownSourcesSettings(
        val packageName: String,
    ) : UpdateDeliveryAction

    data class InstallApk(
        val uri: String,
        val mimeType: String = "application/vnd.android.package-archive",
    ) : UpdateDeliveryAction
}

sealed interface UpdateDeliveryResult {
    data class UnknownSourcesPermissionChanged(
        val granted: Boolean,
    ) : UpdateDeliveryResult
    data object InstallerReturned : UpdateDeliveryResult
    data class Failed(val reason: UpdateDeliveryFailure) : UpdateDeliveryResult
}

enum class UpdateDeliveryFailure {
    ACTIVITY_NOT_FOUND,
    PERMISSION_DENIED,
    PLATFORM_FAILURE,
}
```

唯一 `@Singleton AppUpdateManager` 的 `requestDelivery()` 返回初始动作，`onDeliveryResult(result)` 消费 app launcher 回调并可返回后继动作。全局 prompt 和 Settings 都只观察同一个 manager/store，并调用同一个 app-owned launcher；不得在 Settings capability、ViewModel 或 dispatcher 中复制更新状态、下载校验或未知来源授权状态机。

`AboutAdvancedSettingsCapability.kt` 还固定包含现有更新与开发者选项投影：

```kotlin
enum class UpdateSettingsPhase {
    IDLE,
    CHECKING,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    UP_TO_DATE,
    ERROR,
}

data class UpdateSettingsSnapshot(
    val phase: UpdateSettingsPhase,
    val availableVersion: String? = null,
    val ignoredVersionTag: String? = null,
    val downloadProgressPercent: Int? = null,
    val error: SettingsCapabilityError? = null,
)

data class DeveloperOptions(
    val unlocked: Boolean,
    val showPublicWorkspaceMemos: Boolean,
    val autoTagLineKeywords: String,
    val showAutoTagLineInHome: Boolean,
    val showAutoTagLineInView: Boolean,
    val showAutoTagLineInEdit: Boolean,
    val homeRichPreviewStickyLimit: Int,
)
```

每页定义与命令同前缀的 `Snapshot` 和 `Result`。五个 `Result` 均包含 `Success`、`IgnoredDuplicate` 与 `Failure(SettingsCapabilityError)`；需要交给 Android launcher 或导航器的成功结果额外携带下列类型化动作，而不是 `Intent`、`Activity` 或字符串 Route：

```kotlin
sealed interface SettingsPlatformAction {
    data class RequestPermissions(val permissions: Set<SettingsPermission>) : SettingsPlatformAction
    data class OpenOverlayPermissionSettings(val packageName: String) : SettingsPlatformAction
    data class ShareFile(val uri: String, val mimeType: String) : SettingsPlatformAction
    data object OpenQuickCapture : SettingsPlatformAction
    data object StartQuickCaptureOverlay : SettingsPlatformAction
    data object OpenScreenshotCapture : SettingsPlatformAction
}

enum class SettingsPermission {
    READ_CALENDAR,
    WRITE_CALENDAR,
}
```

### 页面 ViewModel 与一次性事件

七个 ViewModel 都使用同一形态，但每页只注入本页唯一能力接口。以账号与同步页为准：

```kotlin
data class AccountSyncUiState(
    val loading: Boolean = true,
    val snapshot: AccountSyncSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

sealed interface AccountSyncUserIntent {
    data object SyncNow : AccountSyncUserIntent
    data object OpenLogin : AccountSyncUserIntent
    data object OpenAccountManagement : AccountSyncUserIntent
    data object OpenAdvancedSync : AccountSyncUserIntent
    data object ConfirmLogout : AccountSyncUserIntent
    data class ConfirmFullResync(val confirmed: Boolean) : AccountSyncUserIntent
}

sealed interface SettingsUiEvent {
    data class Navigate(val key: OneMemosNavKey) : SettingsUiEvent
    data class Toast(val message: SettingsMessage) : SettingsUiEvent
    data class Confirm(val request: SettingsConfirmation) : SettingsUiEvent
    data class Platform(val action: SettingsPlatformAction) : SettingsUiEvent
    data class UpdateDelivery(val action: cc.pscly.onememos.domain.update.UpdateDeliveryAction) : SettingsUiEvent
}

enum class SettingsMessage {
    COMMAND_SUCCEEDED,
    COMMAND_FAILED,
    PERMISSION_DENIED,
}

enum class SettingsConfirmation {
    LOGOUT,
    FULL_RESYNC,
    CLEAR_IMAGE_CACHE,
    CLEAR_ATTACHMENT_CACHE,
    CLEAR_ALL_CACHE,
    REBUILD_DERIVED_FIELDS,
}

@HiltViewModel
class AccountSyncViewModel @Inject constructor(
    private val capability: AccountSyncSettingsCapability,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountSyncUiState())
    val uiState: StateFlow<AccountSyncUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsUiEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    fun onIntent(intent: AccountSyncUserIntent) {
        when (intent) {
            AccountSyncUserIntent.SyncNow -> execute(AccountSyncSettingsCommand.SyncNow)
            AccountSyncUserIntent.OpenLogin -> send(SettingsUiEvent.Navigate(AuthKey()))
            AccountSyncUserIntent.OpenAccountManagement -> send(SettingsUiEvent.Navigate(AccountManagementSettingsKey))
            AccountSyncUserIntent.OpenAdvancedSync -> send(SettingsUiEvent.Navigate(AdvancedSyncSettingsKey))
            AccountSyncUserIntent.ConfirmLogout -> execute(AccountSyncSettingsCommand.Logout)
            is AccountSyncUserIntent.ConfirmFullResync -> {
                if (intent.confirmed) execute(AccountSyncSettingsCommand.FullResync)
            }
        }
    }
}
```

`MutableSharedFlow(replay = 0, extraBufferCapacity = 1)` 是一次性事件的唯一模式。Compose entry 使用 `repeatOnLifecycle(Lifecycle.State.STARTED)` 保持单一收集者，收到 `Navigate` 后调用 `OneMemosNavigator`；页面离开后发出的事件不会被新 collector 重放。ViewModel 不注入、不缓存也不查找 Navigator。

七个 ViewModel 的类名、唯一构造依赖和稳定状态名固定如下：

```kotlin
@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    private val capability: SettingsHubCapability,
) : ViewModel()

@HiltViewModel
class AccountSyncViewModel @Inject constructor(
    private val capability: AccountSyncSettingsCapability,
) : ViewModel()

@HiltViewModel
class RecordEditingViewModel @Inject constructor(
    private val capability: RecordEditingSettingsCapability,
) : ViewModel()

@HiltViewModel
class ReminderCalendarViewModel @Inject constructor(
    private val capability: ReminderCalendarSettingsCapability,
) : ViewModel()

@HiltViewModel
class StorageOfflineViewModel @Inject constructor(
    private val capability: StorageOfflineSettingsCapability,
) : ViewModel()

@HiltViewModel
class AppearanceInteractionViewModel @Inject constructor(
    private val capability: AppearanceInteractionSettingsCapability,
) : ViewModel()

@HiltViewModel
class AboutAdvancedViewModel @Inject constructor(
    private val capability: AboutAdvancedSettingsCapability,
) : ViewModel()
```

稳定状态只包本页快照和本页错误，不能引用其他页面状态：

```kotlin
data class SettingsHubUiState(
    val snapshot: SettingsHubSnapshot? = null,
)

data class RecordEditingUiState(
    val loading: Boolean = true,
    val snapshot: RecordEditingSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

data class ReminderCalendarUiState(
    val loading: Boolean = true,
    val snapshot: ReminderCalendarSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

data class StorageOfflineUiState(
    val loading: Boolean = true,
    val snapshot: StorageOfflineSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

data class AppearanceInteractionUiState(
    val loading: Boolean = true,
    val snapshot: AppearanceInteractionSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)

data class AboutAdvancedUiState(
    val loading: Boolean = true,
    val snapshot: AboutAdvancedSettingsSnapshot? = null,
    val persistentError: SettingsCapabilityError? = null,
)
```

平台动作由 app 的 Activity Result/Intent 适配器执行，并把结果回传当前 ViewModel：

```kotlin
sealed interface SettingsPlatformResult {
    data object Completed : SettingsPlatformResult
    data class Permissions(
        val granted: Set<SettingsPermission>,
        val denied: Set<SettingsPermission>,
    ) : SettingsPlatformResult
    data class OverlayPermissionChanged(val granted: Boolean) : SettingsPlatformResult
    data class Failed(val error: SettingsCapabilityError) : SettingsPlatformResult
}

interface SettingsPlatformActionDispatcher {
    fun dispatch(
        action: SettingsPlatformAction,
        onResult: (SettingsPlatformResult) -> Unit,
    )
}

val LocalSettingsPlatformActionDispatcher =
    staticCompositionLocalOf<SettingsPlatformActionDispatcher> {
        error("SettingsPlatformActionDispatcher 未由 app 提供")
    }
```

接口与 `CompositionLocal` 位于 `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsPlatformActionDispatcher.kt`，app 在 Host 外层提供实现。它只处理日历权限（返回 `Permissions`）、overlay 权限开关（返回 `OverlayPermissionChanged`）、分享 URI、启动 Quick Capture overlay，以及打开 `QuickCaptureActivity`/`ScreenshotQuickCaptureActivity`；tile 请求由 `:core:quicktiles` 执行。两个 overlay entry Activity 仍由既有 tile/service 流程启动，四个 Activity 的 app 所有权都不改变。dispatcher 不处理 Settings 业务规则或更新安装。

App 实现 `SettingsPlatformActionDispatcher` 时，`OpenOverlayPermissionSettings` 使用 `ActivityResultContracts.StartActivityForResult` 打开系统设置页，并在宿主恢复时检查权限状态后回传结果。更新动作统一交给 app-owned `AppUpdateDeliveryLauncher`：它执行未知来源设置或安装器 Intent，将 `UpdateDeliveryResult` 回送唯一 `AppUpdateManager`，并继续执行 manager 返回的后继动作。

更新交付使用独立窄接口，不经过 `SettingsPlatformActionDispatcher`：

```kotlin
interface SettingsUpdateDeliveryDispatcher {
    fun dispatch(
        action: UpdateDeliveryAction,
        onResult: (UpdateDeliveryResult) -> Unit,
    )
}

val LocalSettingsUpdateDeliveryDispatcher =
    staticCompositionLocalOf<SettingsUpdateDeliveryDispatcher> {
        error("SettingsUpdateDeliveryDispatcher 未由 app 提供")
    }
```

该接口与 `CompositionLocal` 位于 `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsUpdateDeliveryDispatcher.kt`。Feature 只提交 `UpdateDeliveryAction` 并消费 `UpdateDeliveryResult`；app 实现只转发给唯一 `AppUpdateDeliveryLauncher`，不保存第二份状态。

## Task 1: 锁定工具链并建立迁移前回归契约

**Files:**
- Create: `app/src/test/java/cc/pscly/onememos/architecture/ImmutableRegressionContractsTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/architecture/PlatformComponentContractsTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/gradle-daemon-jvm.properties`
- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `baselineprofile/build.gradle.kts`
- Modify: `macrobenchmark/build.gradle.kts`
- Modify: `core/model/build.gradle.kts`
- Delete: `core/model/src/main/AndroidManifest.xml`
- Modify: `core/domain/build.gradle.kts`
- Delete: `core/domain/src/main/AndroidManifest.xml`
- Modify: `core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoBrowseScope.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/network/build.gradle.kts`
- Modify: `core/data/build.gradle.kts`
- Modify: `core/data/src/main/java/cc/pscly/onememos/data/repository/MemoRepositoryImpl.kt`
- Create: `core/data/src/main/java/cc/pscly/onememos/data/paging/MemoPagingDataSource.kt`
- Create: `core/data/src/main/java/cc/pscly/onememos/data/paging/RoomMemoPagingDataSource.kt`
- Modify: `core/sync/build.gradle.kts`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `core/navigation/build.gradle.kts`
- Modify: `core/performance/build.gradle.kts`
- Modify: `feature/home/build.gradle.kts`
- Create: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeMemoPagingSource.kt`
- Modify: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt`
- Modify: `feature/collections/build.gradle.kts`
- Modify: `feature/editor/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/sharecard/build.gradle.kts`
- Modify: `feature/quickcapture/build.gradle.kts`
- Modify: `feature/profile/build.gradle.kts`
- Modify: `feature/auth/build.gradle.kts`
- Modify: `feature/welcome/build.gradle.kts`
- Modify: `feature/start/build.gradle.kts`
- Modify: `feature/todo/build.gradle.kts`
- Create: `app/src/main/java/cc/pscly/onememos/di/HomeMemoPagingSourceAdapter.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/HomePagingModule.kt`
- Create: `app/src/test/java/cc/pscly/onememos/di/HomeMemoPagingSourceAdapterTest.kt`
- Modify: `app/src/test/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureInsertTimeFormatTest.kt`
- Modify: `app/src/test/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureDraftViewModelTest.kt`
- Modify: `feature/collections/src/test/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModelNoteRefCacheTest.kt`
- Modify: `scripts/_env.sh`
- Create: `scripts/_env.ps1`
- Modify: `scripts/verify.sh`
- Modify: `scripts/verify.ps1`
- Modify: `scripts/build-benchmark-apk.sh`
- Modify: `scripts/baselineprofile.ps1`
- Modify: `scripts/perf-home-scroll.ps1`
- Modify: `scripts/perf-startup.ps1`
- Modify: `scripts/README.md`
- Modify: `.github/workflows/android-benchmark.yml`

- [ ] **Step 1: 为规格 §10.1 写字面量特征测试**

在 `ImmutableRegressionContractsTest.kt` 读取仓库源码与资源，逐项断言：`cc.pscly.onememos`、`one_memos_sync`、`force_full_sync`、`is_periodic`、`followup_sync`、`one_memos_periodic_sync`、`one_memos_rebuild_memo_derived_fields`、`one_memos_attachment_prefetch`、Room 版本 `9`、`one_memos.db`、`${applicationId}.fileprovider`、`share_cards/`、`screenshots/`、`shared/`、`cc.pscly.onememos.extra.START_EDITOR_UUID`，以及两个 benchmark 模块的 `targetProjectPath = ":app"`。同时断言 `OneMemosApplication` 仍实现 `Configuration.Provider` 和 `ImageLoaderFactory`，并持有 `HiltWorkerFactory`。

为测试任务传入仓库根路径：

```kotlin
tasks.withType<Test>().configureEach {
    systemProperty("oneMemos.projectDir", rootProject.projectDir.absolutePath)
}
```

- [ ] **Step 2: 为组件与平台行为写 Robolectric 特征测试**

`PlatformComponentContractsTest.kt` 断言合并前清单中的完整组件名和属性：

```kotlin
private val appOwnedActivities = setOf(
    "cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity",
    "cc.pscly.onememos.overlay.QuickCaptureOverlayEntryActivity",
    "cc.pscly.onememos.overlay.QuickCaptureOverlayPickImagesActivity",
    "cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity",
)

private val movableComponents = setOf(
    "cc.pscly.onememos.qs.QuickCaptureTileService",
    "cc.pscly.onememos.qs.QuickScreenshotTileService",
    "cc.pscly.onememos.worker.TodoExternalActionsActivity",
)
```

测试还锁定 tile 的 label、icon、`BIND_QUICK_SETTINGS_TILE`、更新下载目录 `Download/updates/`、独立 GitHub 客户端不复用 Memos Bearer Token、系统时钟无 Activity 时回退待办、日历 DataStore 名 `todo_calendar_event_state` 与事件映射键 `todo_calendar_event_entries`。

- [ ] **Step 3: 在旧工具链上运行特征与构建基线**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.ImmutableRegressionContractsTest --tests cc.pscly.onememos.architecture.PlatformComponentContractsTest --stacktrace
```

Expected: PASS，证明工具链迁移前的字面量、组件和现有行为有可比较基线。若此步失败，只修复测试夹具，不开始升级。

- [ ] **Step 4: 写 `:core:domain` 纯度与 Home 分页适配 RED 测试**

扩展 `ImmutableRegressionContractsTest`，断言 `core/model/src/main/AndroidManifest.xml` 与 `core/domain/src/main/AndroidManifest.xml` 均不存在，`core/domain/src` 不含 `android.` 或 `androidx.` import，`core/domain/build.gradle.kts` 不含 Android plugin、`android {}` 或任何 `androidx-*`/`paging-*` 依赖；同时断言 `MemoRepository.kt` 不出现 `PagingData`。新增 `HomeMemoPagingSourceAdapterTest`，先引用尚不存在的 Feature-owned `HomeMemoPagingSource`、data-owned `MemoPagingDataSource` 与 app adapter，分别向 fake 返回 active/archived 的唯一 `PagingData<Memo>`，断言 adapter 对三种 `MemoBrowseScope` 原样转发 scope、Flow 与 PagingData 实例，不复制分页策略。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.ImmutableRegressionContractsTest --tests cc.pscly.onememos.di.HomeMemoPagingSourceAdapterTest --stacktrace
```

Expected: RED，因为 `MemoRepository` 仍暴露 AndroidX `PagingData`，新纯领域 scope、分页窄接口和 app adapter 尚不存在。

- [ ] **Step 5: 原子迁移到已锁定工具链并把 Paging AndroidX 移出 `:core:domain`**

按本文“已锁定工具链与官方兼容依据”逐项执行，不重新选择版本：

1. `gradle/wrapper/gradle-wrapper.properties` 改为 `gradle-9.4.1-bin.zip`；`gradle/libs.versions.toml` 精确写入 AGP `9.2.1`、Kotlin/Compose Compiler `2.3.10`、KSP `2.3.10`、Compose BOM `2026.06.00`、Navigation 3 `1.1.4`、Activity `1.13.0`、Lifecycle `2.11.0`、Dagger/Hilt `2.60.1`、AndroidX Hilt `1.4.0`、kotlinx.serialization `1.11.0`、`compileSdk 36` 与 `buildTools 36.0.0`，保持 `targetSdk 34`、`minSdk 33`。
2. 在版本目录增加 `kotlin-jvm`、`android-library`、`compose-compiler` 和 `kotlin-serialization` plugin alias，以及 `androidx-navigation3-runtime`、`androidx-navigation3-ui` 与 `kotlinx-serialization-json` library alias。保留 `androidx-navigation-compose 2.7.7` 只供 Task 31 前的旧入口编译，不允许新增调用；不得新增 `paging-common` 给 `:core:domain`，现有 Paging 版本只供 data、Home 与 app Android 模块使用。
3. `settings.gradle.kts` 删除硬编码的 Android library 与 Kotlin Android plugin 版本；根 `build.gradle.kts` 删除 Kotlin Android plugin，加入 Kotlin JVM、Android library、Compose Compiler、Kotlin Serialization plugin 的 `apply false` alias。执行 `./gradlew updateDaemonJvm --jvm-version=21` 生成受版本控制的 `gradle/gradle-daemon-jvm.properties`，并确认 `toolchainVersion=21`。
4. `:core:model` 与 `:core:domain` 删除 Android library plugin、`android {}` 和空 Manifest，改用 `libs.plugins.kotlin.jvm`，设置 `jvmToolchain(21)` 与 `JvmTarget.JVM_21`。`:core:domain` 删除 Paging 依赖和 `PagingData` import：把原嵌套 `MemoRepository.BrowseScope` 提取为纯 Kotlin `MemoBrowseScope`，并从 `MemoRepository` 删除 `pagingMemos/pagingArchivedMemos`。其余 Android 模块删除 `org.jetbrains.kotlin.android` 或 `libs.plugins.kotlin.android`，使用 AGP 内置 Kotlin；所有 Android 模块精确声明 `buildToolsVersion = "36.0.0"`、`JavaVersion.VERSION_21` 与 `JvmTarget.JVM_21`。十二个 Compose 模块应用 `libs.plugins.compose.compiler` 并删除旧 `composeOptions.kotlinCompilerExtensionVersion`。
5. `app`、`:core:sync` 和十个 Hilt Feature 删除 `kotlin("kapt")`、`kapt {}` 与全部 `kapt(...)`，应用 KSP 并把 Dagger/AndroidX Hilt compiler 改为 `ksp(...)`；`:core:database` 现有 Room KSP 保持。禁止设置 `android.builtInKotlin=false`，禁止加入 `com.android.legacy-kapt`。
6. 新建 `scripts/_env.ps1`：首行设置 UTF-8，解析 `ONE_MEMOS_JAVA_HOME`/`JAVA_HOME`，执行 `java -version` 并在 major 不是 21 时立即失败；`verify.ps1`、`baselineprofile.ps1`、`perf-home-scroll.ps1`、`perf-startup.ps1` 在调用 Gradle 前 dot-source 它。`scripts/_env.sh` 同样在不是 JDK 21 时失败，不再静默回落到另一版本；所有 Linux 构建脚本继续 source 它。GitHub Actions 保持 Temurin 21。历史 `.ai_session.md` 中已完成版本的 JDK 证据不改写。
7. 在 `:core:data` 新建 `MemoPagingDataSource` 与 `RoomMemoPagingDataSource`，把 `MemoRepositoryImpl` 中现有两个 `Pager` 实现逐字迁入后者；`:core:data` 直接声明现有 Paging runtime 依赖。Feature Home 新建只含 `active(scope)`/`archived(scope)` 的 `HomeMemoPagingSource`，`HomeViewModel` 注入它并使用纯 `MemoBrowseScope`，其他 `MemoRepository` 行为不变。app-owned `HomePagingModule` 唯一提供 `MemoPagingDataSource` 与 `HomeMemoPagingSourceAdapter`；`:core:data` 和 Feature 不声明 Hilt module。三个非 Home 的 `MemoRepository` fake 删除无关 Paging override/import，Home 分页行为与筛选、cachedIn、归档切换不变。

固定接口如下，禁止把 `PagingData` 再放回 domain：

```kotlin
sealed interface MemoBrowseScope {
    data object All : MemoBrowseScope
    data object LocalOnly : MemoBrowseScope
    data class Creator(val creator: String) : MemoBrowseScope
}

interface MemoPagingDataSource {
    fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>>
    fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>>
}

interface HomeMemoPagingSource {
    fun active(scope: MemoBrowseScope): Flow<PagingData<Memo>>
    fun archived(scope: MemoBrowseScope): Flow<PagingData<Memo>>
}
```

迁移不得修改 applicationId、versionCode、versionName、targetSdk、minSdk、Manifest 组件或用户可见行为。生产源码改动只限上述等价 Paging 契约迁移：Pager 配置、DAO 选择、筛选、缓存和归档语义必须原样保留；除此之外的业务源码不得修改。AGP DSL 修正只限上述 build 文件，且 `baselineprofile`、`macrobenchmark` 的 `targetProjectPath = ":app"` 不变。

- [ ] **Step 6: 运行工具链与纯领域静态锁定检查**

```powershell
$configFiles = @(git ls-files -- '*.gradle.kts' 'gradle/*.toml' 'gradle/wrapper/*.properties' 'gradle.properties')
$forbidden = Select-String -Path $configFiles -Pattern 'org\.jetbrains\.kotlin\.android|kotlin\("kapt"\)|\bkapt\(|kotlinCompilerExtensionVersion|android\.builtInKotlin=false|com\.android\.legacy-kapt'
if ($forbidden) {
  $forbidden | ForEach-Object { Write-Error $_.ToString() }
  throw '仍存在旧 Kotlin/kapt 配置'
}

$catalog = Get-Content gradle/libs.versions.toml -Raw
@('9.2.1', '2.3.10', '2026.06.00', '1.1.4', '1.13.0', '2.11.0', '2.60.1', '1.4.0', '1.11.0') |
  ForEach-Object {
    if ($catalog -notmatch [regex]::Escape($_)) { throw "版本目录缺少锁定值：$_" }
  }
if ($catalog -notmatch 'compileSdk\s*=\s*"36"') { throw 'compileSdk 未锁定为 36' }
if ($catalog -notmatch 'targetSdk\s*=\s*"34"' -or $catalog -notmatch 'minSdk\s*=\s*"33"') {
  throw 'targetSdk 或 minSdk 漂移'
}
if ((Get-Content gradle/wrapper/gradle-wrapper.properties -Raw) -notmatch 'gradle-9\.4\.1-bin\.zip') {
  throw 'Gradle Wrapper 未锁定为 9.4.1'
}
if ((Get-Content gradle/gradle-daemon-jvm.properties -Raw) -notmatch 'toolchainVersion=21') {
  throw 'Gradle daemon JVM 未锁定为 21'
}
$androidBuilds = @(git ls-files -- '*build.gradle.kts') |
  Where-Object { (Get-Content $_ -Raw) -match 'com\.android\.(application|library|test)' }
foreach ($buildFile in $androidBuilds) {
  $text = Get-Content $buildFile -Raw
  if ($text -notmatch 'buildToolsVersion\s*=\s*"36\.0\.0"') { throw "$buildFile 未锁定 Build Tools 36.0.0" }
  if ($text -notmatch 'JavaVersion\.VERSION_21') { throw "$buildFile 未锁定 Java 21" }
  if ($text -notmatch 'JvmTarget\.JVM_21') { throw "$buildFile 未锁定 Kotlin JVM 21" }
}
foreach ($jvmBuild in @('core/model/build.gradle.kts', 'core/domain/build.gradle.kts')) {
  $text = Get-Content $jvmBuild -Raw
  if ($text -notmatch 'kotlin\.jvm' -or $text -notmatch 'jvmToolchain\(21\)' -or $text -notmatch 'JvmTarget\.JVM_21') {
    throw "$jvmBuild 不是纯 Kotlin/JVM 21 模块"
  }
}
$domainFiles = @(git ls-files -- 'core/domain/src/**/*.kt')
$domainForbidden = Select-String -Path $domainFiles -Pattern '^import\s+(android|androidx)\.'
if ($domainForbidden) {
  $domainForbidden | ForEach-Object { Write-Error $_.ToString() }
  throw ':core:domain 仍引用 Android 或 AndroidX'
}
$domainBuild = Get-Content core/domain/build.gradle.kts -Raw
if ($domainBuild -match 'androidx|paging|com\.android|\bandroid\s*\{') {
  throw ':core:domain 构建仍含 Android/AndroidX/Paging 边界'
}
if ((Get-Content core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt -Raw) -match 'PagingData|pagingMemos|pagingArchivedMemos') {
  throw 'MemoRepository 仍泄漏 Paging AndroidX 类型'
}
```

Expected: 无旧 Kotlin Android、kapt、旧 Compose compiler 配置或第二 Kotlin 方案；所有锁定值精确存在；`:core:domain` 的源码和 build 文件不含 Android、AndroidX 或 Paging。

- [ ] **Step 7: 运行升级后完整工具链与分页迁移门禁**

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.ImmutableRegressionContractsTest --tests cc.pscly.onememos.di.HomeMemoPagingSourceAdapterTest --stacktrace
.\gradlew.bat testDebugUnitTest lint :app:assembleDebug :app:assembleBenchmark :baselineprofile:assembleBenchmark :macrobenchmark:assembleBenchmark --stacktrace
```

Expected: Gradle `9.4.1`、daemon JVM `21`；全部测试、Lint、Debug、Benchmark、Baseline Profile 和 Macrobenchmark 构建 PASS，且 Benchmark 仍使用既有签名配置。输出中的 Java 与 Kotlin 编译目标均为 21。

- [ ] **Step 8: 创建中文原子提交**

```powershell
git add -A -- gradle/libs.versions.toml gradle/wrapper/gradle-wrapper.properties gradle/gradle-daemon-jvm.properties settings.gradle.kts build.gradle.kts `
  app/build.gradle.kts baselineprofile/build.gradle.kts macrobenchmark/build.gradle.kts `
  core/model/build.gradle.kts core/model/src/main/AndroidManifest.xml core/domain/build.gradle.kts core/domain/src/main/AndroidManifest.xml core/database/build.gradle.kts core/network/build.gradle.kts core/data/build.gradle.kts core/sync/build.gradle.kts core/designsystem/build.gradle.kts core/navigation/build.gradle.kts core/performance/build.gradle.kts `
  core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoRepository.kt core/domain/src/main/java/cc/pscly/onememos/domain/repository/MemoBrowseScope.kt `
  core/data/src/main/java/cc/pscly/onememos/data/repository/MemoRepositoryImpl.kt core/data/src/main/java/cc/pscly/onememos/data/paging/MemoPagingDataSource.kt core/data/src/main/java/cc/pscly/onememos/data/paging/RoomMemoPagingDataSource.kt `
  feature/home/build.gradle.kts feature/collections/build.gradle.kts feature/editor/build.gradle.kts feature/settings/build.gradle.kts feature/sharecard/build.gradle.kts feature/quickcapture/build.gradle.kts feature/profile/build.gradle.kts feature/auth/build.gradle.kts feature/welcome/build.gradle.kts feature/start/build.gradle.kts feature/todo/build.gradle.kts `
  feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeMemoPagingSource.kt feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeViewModel.kt `
  app/src/main/java/cc/pscly/onememos/di/HomeMemoPagingSourceAdapter.kt app/src/main/java/cc/pscly/onememos/di/HomePagingModule.kt app/src/test/java/cc/pscly/onememos/di/HomeMemoPagingSourceAdapterTest.kt `
  app/src/test/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureInsertTimeFormatTest.kt app/src/test/java/cc/pscly/onememos/ui/feature/quickcapture/QuickCaptureDraftViewModelTest.kt feature/collections/src/test/java/cc/pscly/onememos/ui/feature/collections/CollectionsViewModelNoteRefCacheTest.kt `
  scripts/_env.sh scripts/_env.ps1 scripts/verify.sh scripts/verify.ps1 scripts/build-benchmark-apk.sh scripts/baselineprofile.ps1 scripts/perf-home-scroll.ps1 scripts/perf-startup.ps1 scripts/README.md .github/workflows/android-benchmark.yml `
  app/src/test/java/cc/pscly/onememos/architecture/ImmutableRegressionContractsTest.kt app/src/test/java/cc/pscly/onememos/architecture/PlatformComponentContractsTest.kt
git commit -m "build(android): 锁定导航迁移工具链与回归契约"
```

## Task 2: 注册六个新 Core 模块骨架

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`
- Create: `core/settings/build.gradle.kts`
- Create: `core/settings/src/main/AndroidManifest.xml`
- Create: `core/update/build.gradle.kts`
- Create: `core/update/src/main/AndroidManifest.xml`
- Create: `core/calendar/build.gradle.kts`
- Create: `core/calendar/src/main/AndroidManifest.xml`
- Create: `core/quicktiles/build.gradle.kts`
- Create: `core/quicktiles/src/main/AndroidManifest.xml`
- Create: `core/externalactions/build.gradle.kts`
- Create: `core/externalactions/src/main/AndroidManifest.xml`
- Create: `core/diagnostics/build.gradle.kts`
- Create: `core/diagnostics/src/main/AndroidManifest.xml`

- [ ] **Step 1: 运行移动前门禁**

```powershell
.\gradlew.bat :app:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 2: 注册准确模块名并建立最小依赖边界**

在 `settings.gradle.kts` 加入 `:core:settings`、`:core:update`、`:core:calendar`、`:core:quicktiles`、`:core:externalactions`、`:core:diagnostics`。各 Android library 使用现有统一 SDK/JVM 配置；`:core:settings` 只按规格依赖 `:core:domain`、`:core:data`、`:core:network`、`:core:sync` 与五个平台模块，不依赖 `:app`、任何 Feature 或 Compose。`:app` 添加这六个模块依赖，供最终组合。

- [ ] **Step 3: 运行六模块构建与 app 门禁**

```powershell
.\gradlew.bat :core:settings:assembleDebug :core:update:assembleDebug :core:calendar:assembleDebug :core:quicktiles:assembleDebug :core:externalactions:assembleDebug :core:diagnostics:assembleDebug :app:assembleDebug --stacktrace
```

Expected: 全部 PASS，无循环依赖。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add settings.gradle.kts app/build.gradle.kts core/settings core/update core/calendar core/quicktiles core/externalactions core/diagnostics
git commit -m "build(core): 建立六个平台与设置能力模块"
```

## Task 3: 抽取更新能力到 `:core:update`

**Files:**
- Move: `app/src/main/java/cc/pscly/onememos/update/AppUpdateManager.kt` to `core/update/src/main/java/cc/pscly/onememos/update/AppUpdateManager.kt`
- Move: `app/src/main/java/cc/pscly/onememos/update/AppUpdateStore.kt` to `core/update/src/main/java/cc/pscly/onememos/update/AppUpdateStore.kt`
- Move: `app/src/main/java/cc/pscly/onememos/update/GitHubUpdateApi.kt` to `core/update/src/main/java/cc/pscly/onememos/update/GitHubUpdateApi.kt`
- Move: `app/src/main/java/cc/pscly/onememos/update/AppUpdatePolicy.kt` to `core/update/src/main/java/cc/pscly/onememos/update/AppUpdatePolicy.kt`
- Move: `app/src/test/java/cc/pscly/onememos/update/AppUpdatePolicyTest.kt` to `core/update/src/test/java/cc/pscly/onememos/update/AppUpdatePolicyTest.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/update/UpdateDeliveryAction.kt`
- Create: `core/update/src/main/java/cc/pscly/onememos/update/AppIdentityPort.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/AppIdentityAdapter.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/di/AppUpdateModule.kt`
- Create: `app/src/main/java/cc/pscly/onememos/update/AppUpdateDeliveryLauncher.kt`
- Create: `app/src/test/java/cc/pscly/onememos/update/AppUpdateDeliveryLauncherTest.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`
- Modify: `core/update/build.gradle.kts`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 扩展特征测试并运行移动前门禁**

在 `AppUpdatePolicyTest` 加入自动检查冷却、失败退避、忽略版本、下载元数据包名/版本/签名不匹配和重复下载抑制断言；运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.update.AppUpdatePolicyTest :app:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 2: 引入 app 身份窄端口并完成原子移动**

```kotlin
interface AppIdentityPort {
    val applicationId: String
    val versionName: String
    val versionCode: Long
    val fileProviderAuthority: String
}
```

`AppUpdateManager` 用 `AppIdentityPort` 替代 `BuildConfig`，保留 `DownloadManager`、DataStore `app_updates`、`Download/updates/`、SHA-256、包名、版本和签名证书集合核验。`AppUpdateManager` 与 `AppUpdateStore` 保留现有 `@Singleton` 和 `@Inject constructor`，全工程只能各有一个实例和一份 mutable state。更新 Retrofit 仍使用独立 `OkHttpClient`，不注入 Memos 客户端。

`AppUpdateModule.kt` **保留在 app**，更新 import 后继续唯一 `@Provides GitHubUpdateApi`，并在同一 app 组合根绑定 `AppIdentityPort` 到 `AppIdentityAdapter`；不得在 `:core:update` 创建 `di/` 或任何 `@Module/@Provides/@Binds/@InstallIn`。将 manager 原先直接打开未知来源设置/安装器的分支改为本计划统一契约中的 `requestDelivery(): UpdateDeliveryAction?` 与 `onDeliveryResult(UpdateDeliveryResult): UpdateDeliveryAction?`。

- [ ] **Step 3: 写 launcher RED 测试并接回全局提示**

`AppUpdateDeliveryLauncherTest` 使用同一个 fake manager 覆盖：打开未知来源设置、回到前台后检查权限、权限已授予则执行 manager 返回的 `InstallApk`、安装器返回后回送 `InstallerReturned`、Activity 不存在时回送 `Failed(ACTIVITY_NOT_FOUND)`；断言 launcher 不保存第二份下载或安装状态。`OneMemosApp` 的全局更新 prompt 改为调用这一 launcher，仍观察同一个 `AppUpdateManager.uiState`。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.update.AppUpdateDeliveryLauncherTest --stacktrace
```

Expected: 先 RED；最小接线后 PASS。

- [ ] **Step 4: 运行移动后门禁**

```powershell
.\gradlew.bat :core:update:testDebugUnitTest :core:update:assembleDebug :app:testDebugUnitTest --tests cc.pscly.onememos.update.AppUpdateDeliveryLauncherTest :app:assembleDebug --stacktrace
```

Expected: PASS；`ImmutableRegressionContractsTest` 与 `PlatformComponentContractsTest` 仍 PASS；全局 prompt 与后续 Settings 共享唯一 manager/store/launcher 路径。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/domain/src/main/java/cc/pscly/onememos/domain/update/UpdateDeliveryAction.kt `
  core/update/build.gradle.kts core/update/src/main core/update/src/test `
  app/build.gradle.kts `
  app/src/main/java/cc/pscly/onememos/di/AppIdentityAdapter.kt `
  app/src/main/java/cc/pscly/onememos/di/AppUpdateModule.kt `
  app/src/main/java/cc/pscly/onememos/update/AppUpdateDeliveryLauncher.kt `
  app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt `
  app/src/main/java/cc/pscly/onememos/update `
  app/src/test/java/cc/pscly/onememos/update
git commit -m "refactor(update): 下沉固定签名更新能力"
```

## Task 4: 抽取系统日历能力到 `:core:calendar`

**Files:**
- Create: `core/calendar/src/main/java/cc/pscly/onememos/calendar/SystemCalendarGateway.kt`
- Create: `core/calendar/src/main/java/cc/pscly/onememos/calendar/SystemCalendarGatewayImpl.kt`
- Create: `core/calendar/src/main/java/cc/pscly/onememos/calendar/CalendarModels.kt`
- Create: `core/calendar/src/test/java/cc/pscly/onememos/calendar/SystemCalendarGatewayTest.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/CalendarCapabilityModule.kt`
- Modify: `core/sync/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt`
- Modify: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`

- [ ] **Step 1: 运行抽取前特征与构建门禁**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.PlatformComponentContractsTest :core:sync:testDebugUnitTest :core:sync:assembleDebug :feature:settings:assembleDebug --stacktrace
```

Expected: PASS，日历 DataStore、权限和现有 Settings/Worker 路径有可比较基线。

- [ ] **Step 2: 写日历 gateway RED 测试**

测试覆盖可写日历筛选、名称回退、权限缺失、事件插入/更新/删除、提醒替换、默认 30 分钟时长、已保存 event 映射、DataStore 名 `todo_calendar_event_state` 与键 `todo_calendar_event_entries`：

```powershell
.\gradlew.bat :core:calendar:testDebugUnitTest --tests cc.pscly.onememos.calendar.SystemCalendarGatewayTest --stacktrace
```

Expected: RED，因为 gateway 尚不存在。

- [ ] **Step 3: 实现 gateway 并只替换 Android Calendar 细节**

```kotlin
interface SystemCalendarGateway {
    fun hasPermissions(): Boolean
    suspend fun writableCalendars(): Result<List<WritableCalendar>>
    suspend fun calendarLabel(calendarId: Long): Result<String?>
    suspend fun syncTodoEvents(request: CalendarSyncRequest): CalendarSyncResult
}
```

把 `SettingsScreen.kt` 中 `hasCalendarPermissions`、`queryWritableCalendars`、`resolveCalendarLabelOrNull` 和 `TodoReminderRescheduleWorker` 中的 `CalendarContract`、事件映射、`todo_calendar_event_state` DataStore 实现移到 `:core:calendar`。`SystemCalendarGatewayImpl` 使用 `@Inject constructor(@ApplicationContext context: Context)`，Core 内不创建 Hilt module。app-owned `CalendarCapabilityModule` 用唯一 `@Binds @Singleton` 将实现绑定到 gateway。

`core/sync` 增加对 `:core:calendar` 的依赖；Worker 仍位于 `:core:sync`，构造函数注入 gateway，并只负责读取 Todo/Settings、计算输入和调用 `syncTodoEvents`。迁移期旧 `SettingsViewModel` 临时注入同一 gateway，向旧 `SettingsScreen` 暴露 `hasCalendarPermissions()`、`writableCalendars()`、`calendarLabel(id)` 三个薄方法；Screen 删除全部 `CalendarContract`/`ContentResolver` 代码但保留现有 permission launcher、选择器与显示行为。该薄接线会随旧 Screen/ViewModel 在 Task 31 删除。权限、日历筛选、名称回退、重复提醒、默认 30 分钟时长、时区和已保存 event 映射均不变。

- [ ] **Step 4: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:calendar:testDebugUnitTest --tests cc.pscly.onememos.calendar.SystemCalendarGatewayTest --stacktrace
.\gradlew.bat :core:calendar:testDebugUnitTest :core:calendar:assembleDebug :core:sync:testDebugUnitTest :core:sync:assembleDebug :feature:settings:assembleDebug :app:assembleDebug --stacktrace
```

Expected: 全部 PASS；Core 没有 `@Module/@Binds/@Provides/@InstallIn`，旧 Settings 与 Worker 都通过同一个 gateway 保持等价行为。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/calendar `
  core/sync/build.gradle.kts `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt `
  feature/settings/build.gradle.kts `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt `
  app/src/main/java/cc/pscly/onememos/di/CalendarCapabilityModule.kt
git commit -m "refactor(calendar): 抽取系统日历读写能力"
```

## Task 5: 抽取快捷开关服务到 `:core:quicktiles`

**Files:**
- Move: `app/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt` to `core/quicktiles/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt`
- Move: `app/src/main/java/cc/pscly/onememos/qs/QuickScreenshotTileService.kt` to `core/quicktiles/src/main/java/cc/pscly/onememos/qs/QuickScreenshotTileService.kt`
- Move: `app/src/main/res/drawable/ic_qs_quick_capture.xml` to `core/quicktiles/src/main/res/drawable/ic_qs_quick_capture.xml`
- Move: `app/src/main/res/drawable/ic_qs_quick_screenshot.xml` to `core/quicktiles/src/main/res/drawable/ic_qs_quick_screenshot.xml`
- Move: quick tile strings from `app/src/main/res/values/strings.xml` to `core/quicktiles/src/main/res/values/strings.xml`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/QuickCaptureTargetPort.kt`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/ScreenshotEntryPort.kt`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/OverlayPermissionGateway.kt`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/AndroidOverlayPermissionGateway.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/QuickTilesModule.kt`
- Create: `app/src/test/java/cc/pscly/onememos/quicktiles/QuickTileTargetAdaptersTest.kt`
- Create: `core/quicktiles/src/test/java/cc/pscly/onememos/quicktiles/OverlayPermissionGatewayTest.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/QuickTileTargetAdapters.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 运行移动前组件与资源特征门禁**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.PlatformComponentContractsTest :app:processDebugMainManifest :app:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 2: 通过窄端口保持 app Activity 独立**

```kotlin
interface QuickCaptureTargetPort {
    fun activityIntent(context: Context): Intent
    fun overlayIntent(context: Context): Intent
}

interface ScreenshotEntryPort {
    fun activityIntent(context: Context): Intent
}

interface OverlayPermissionGateway {
    val packageName: String
    fun isGranted(): Boolean
}
```

Core 服务不 import app Activity 类型。app 适配器负责创建指向四个 app-owned Activity 的显式 Intent；它们仍保留当前 FQCN、taskAffinity、theme、exported、noHistory 与 excludeFromRecents。`AndroidOverlayPermissionGateway` 使用 `@Inject constructor(@ApplicationContext context: Context)`，只读取 `Context.packageName` 与 `Settings.canDrawOverlays(context)`。app-owned `QuickTilesModule` 唯一绑定 `QuickCaptureTargetPort`、`ScreenshotEntryPort` 与 `OverlayPermissionGateway`；`:core:quicktiles` 不含任何 `@Module/@Binds/@Provides/@InstallIn`。不得新增 Quick Capture `NavKey`。

- [ ] **Step 3: 原子移动服务、资源和 Manifest 声明**

`core/quicktiles/src/main/AndroidManifest.xml` 使用原 FQCN `cc.pscly.onememos.qs.QuickCaptureTileService` 与 `cc.pscly.onememos.qs.QuickScreenshotTileService`，保留 icon、label、permission 和 intent-filter。app Manifest 删除重复 service 声明，但保留四个 app-owned Activity 与 overlay service。

- [ ] **Step 4: 运行移动后门禁**

```powershell
.\gradlew.bat :core:quicktiles:testDebugUnitTest --tests cc.pscly.onememos.quicktiles.OverlayPermissionGatewayTest --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.quicktiles.QuickTileTargetAdaptersTest --stacktrace
.\gradlew.bat :core:quicktiles:testDebugUnitTest :core:quicktiles:assembleDebug :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.PlatformComponentContractsTest :app:processDebugMainManifest :app:assembleDebug --stacktrace
```

Expected: PASS，合并 Manifest 的组件 FQCN 与资源行为不变。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add -A -- app/src/main/AndroidManifest.xml `
  app/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt `
  app/src/main/java/cc/pscly/onememos/qs/QuickScreenshotTileService.kt `
  app/src/main/res/drawable/ic_qs_quick_capture.xml `
  app/src/main/res/drawable/ic_qs_quick_screenshot.xml `
  app/src/main/res/values/strings.xml `
  app/src/main/java/cc/pscly/onememos/di/QuickTileTargetAdapters.kt `
  app/src/main/java/cc/pscly/onememos/di/QuickTilesModule.kt `
  app/src/test/java/cc/pscly/onememos/architecture/PlatformComponentContractsTest.kt `
  app/src/test/java/cc/pscly/onememos/quicktiles/QuickTileTargetAdaptersTest.kt `
  core/quicktiles/src/main `
  core/quicktiles/src/test `
  core/quicktiles/build.gradle.kts
$moveScope = @(
  'app/src/main/AndroidManifest.xml',
  'app/src/main/java/cc/pscly/onememos/qs/QuickCaptureTileService.kt',
  'app/src/main/java/cc/pscly/onememos/qs/QuickScreenshotTileService.kt',
  'app/src/main/res/drawable/ic_qs_quick_capture.xml',
  'app/src/main/res/drawable/ic_qs_quick_screenshot.xml',
  'app/src/main/res/values/strings.xml',
  'core/quicktiles/src/main'
)
$unstaged = @(git diff --name-only -- $moveScope)
if ($unstaged) { throw "快捷开关迁移仍有未暂存变更：$($unstaged -join ',')" }
$stagedStatus = @(git diff --cached --name-status --no-renames -- $moveScope)
foreach ($oldPath in $moveScope[1..4]) {
  if ("D`t$oldPath" -notin $stagedStatus) { throw "旧路径删除未进入暂存区：$oldPath" }
}
if (-not ($stagedStatus | Where-Object { $_ -match "^[MA]`tapp/src/main/res/values/strings.xml$" })) {
  throw '旧 strings.xml 的资源移除未进入暂存区'
}
if (-not ($stagedStatus | Where-Object { $_ -match "^A`tcore/quicktiles/src/main/" })) {
  throw '新模块源码、Manifest 或资源未进入暂存区'
}
git commit -m "refactor(quicktiles): 下沉快捷开关服务与资源"
```

## Task 6: 抽取外部系统动作到 `:core:externalactions`

**Files:**
- Move: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt` to `core/externalactions/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt`
- Move: the `cc.pscly.onememos.worker.TodoExternalActionsActivity` declaration from `core/sync/src/main/AndroidManifest.xml` to `core/externalactions/src/main/AndroidManifest.xml`
- Create: `core/externalactions/src/main/java/cc/pscly/onememos/externalactions/InAppFallbackPort.kt`
- Create temporarily before move (never commit at this path): `core/sync/src/test/java/cc/pscly/onememos/worker/TodoExternalActionsActivityTest.kt`
- Create final by moving the temporary baseline test: `core/externalactions/src/test/java/cc/pscly/onememos/worker/TodoExternalActionsActivityTest.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/InAppFallbackAdapter.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/ExternalActionsModule.kt`
- Modify: `core/sync/build.gradle.kts`

- [ ] **Step 1: 写并运行抽取前时钟特征测试**

覆盖今天未来时间打开闹钟、24 小时内非当天打开计时器、过期/过长打开闹钟、解析失败打开闹钟列表、无系统时钟时调用应用内待办回退：

```powershell
.\gradlew.bat :core:sync:testDebugUnitTest --tests cc.pscly.onememos.worker.TodoExternalActionsActivityTest :core:sync:assembleDebug --stacktrace
```

Expected: PASS，锁定当前时钟决策、extras、消息截断和应用内待办回退行为。

- [ ] **Step 2: 用窄端口替代包启动字符串**

```kotlin
interface InAppFallbackPort {
    fun todoIntent(context: Context): Intent
}
```

把 Activity 与刚通过的特征测试一起移动。旧测试路径只是本任务 Step 1 的未跟踪 RED/PASS 临时位置，不形成独立提交；移动后的新测试路径作为新增文件进入本任务唯一提交，因此暂存区不应也不可能包含旧测试路径的 `D` 状态。`TodoExternalActionsActivity` 使用 `@AndroidEntryPoint` 注入 `InAppFallbackPort`；app-owned `ExternalActionsModule` 唯一绑定 `InAppFallbackAdapter`，Core 不声明 Hilt module。由于 Navigation 3 契约尚要到 Task 8 才存在，本任务的 app adapter 只等价接管旧 `START_ROUTE=todo` Intent 创建，不能提前引用未来类型；Task 12 会在 parser 与白名单就绪的同一提交中把该 adapter 原子切换到固定 action `cc.pscly.onememos.action.OPEN_TODO`。

`TodoExternalActionsActivity` FQCN、extras `cc.pscly.onememos.extra.TODO_TITLE` 和 `cc.pscly.onememos.extra.TODO_DUE_AT_LOCAL`、透明主题和 noHistory 行为保持不变。Receiver 与 Worker 继续用原 FQCN 创建 PendingIntent；`core/sync` 只增加对 `:core:externalactions` 的编译依赖，不在本任务改写通知导航负载。

- [ ] **Step 3: 运行聚焦 PASS 与 Manifest 门禁**

```powershell
.\gradlew.bat :core:externalactions:testDebugUnitTest --tests cc.pscly.onememos.worker.TodoExternalActionsActivityTest --stacktrace
.\gradlew.bat :core:externalactions:testDebugUnitTest :core:externalactions:assembleDebug :core:sync:testDebugUnitTest :core:sync:assembleDebug :app:processDebugMainManifest :app:assembleDebug --stacktrace
```

Expected: 全部 PASS，Manifest 只有一份同 FQCN Activity。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add -A -- core/externalactions/build.gradle.kts core/externalactions/src/main core/externalactions/src/test `
  core/sync/build.gradle.kts `
  core/sync/src/main/AndroidManifest.xml `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt `
  app/src/main/java/cc/pscly/onememos/di/InAppFallbackAdapter.kt `
  app/src/main/java/cc/pscly/onememos/di/ExternalActionsModule.kt
$moveScope = @(
  'core/sync/src/main/AndroidManifest.xml',
  'core/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt',
  'core/externalactions/src/main',
  'core/externalactions/src/test'
)
$unstaged = @(git diff --name-only -- $moveScope)
if ($unstaged) { throw "外部动作迁移仍有未暂存变更：$($unstaged -join ',')" }
$stagedStatus = @(git diff --cached --name-status --no-renames -- $moveScope)
if ("D`tcore/sync/src/main/java/cc/pscly/onememos/worker/TodoExternalActionsActivity.kt" -notin $stagedStatus) {
  throw '旧 Activity 源码删除未进入暂存区'
}
if (-not ($stagedStatus | Where-Object { $_ -match "^[MA]`tcore/sync/src/main/AndroidManifest.xml$" })) {
  throw '旧 Manifest 声明删除未进入暂存区'
}
if (-not ($stagedStatus | Where-Object { $_ -match "^A`tcore/externalactions/src/(main|test)/" })) {
  throw '新模块源码、测试或 Manifest 未进入暂存区'
}
if ("A`tcore/externalactions/src/test/java/cc/pscly/onememos/worker/TodoExternalActionsActivityTest.kt" -notin $stagedStatus) {
  throw '移动后的特征测试未以新增文件进入暂存区'
}
git commit -m "refactor(externalactions): 下沉系统时钟与回退动作"
```

## Task 7: 抽取诊断收集与导出到 `:core:diagnostics`

**Files:**
- Create: `core/diagnostics/src/main/java/cc/pscly/onememos/diagnostics/DiagnosticsExporter.kt`
- Create: `core/diagnostics/src/main/java/cc/pscly/onememos/diagnostics/DiagnosticsExporterImpl.kt`
- Create: `core/diagnostics/src/main/java/cc/pscly/onememos/diagnostics/DiagnosticsSnapshot.kt`
- Create: `core/diagnostics/src/main/java/cc/pscly/onememos/diagnostics/AppIdentityPort.kt`
- Create: `core/diagnostics/src/test/java/cc/pscly/onememos/diagnostics/DiagnosticsExporterTest.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/DiagnosticsIdentityAdapter.kt`
- Create: `app/src/main/java/cc/pscly/onememos/di/DiagnosticsModule.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/LegacyDiagnosticsAdapter.kt`
- Modify: `feature/settings/build.gradle.kts`
- Modify: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`
- Modify: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`

- [ ] **Step 1: 写诊断安全边界 RED 测试**

测试断言导出 JSON 包含版本、设备、权限和现有开关状态，不包含 token 或密码；输出位于 `filesDir/shared/diagnostics-YYYY-MM-DDTHH-mm-ss.json`，时间部分使用生成时刻。若同一秒再次导出，沿用当前行为覆盖同名文件，不追加序号、不改变路径；结果为可供 launcher 分享的 URI 字符串。运行：

```powershell
.\gradlew.bat :core:diagnostics:testDebugUnitTest --tests cc.pscly.onememos.diagnostics.DiagnosticsExporterTest --stacktrace
```

Expected: RED，因为 exporter 尚不存在。

- [ ] **Step 2: 实现 exporter 并移除 Feature 的文件与 JSON 副作用**

```kotlin
interface DiagnosticsExporter {
    suspend fun export(snapshot: DiagnosticsSnapshot): DiagnosticsExportResult
}

sealed interface DiagnosticsExportResult {
    data class Success(val fileUri: String) : DiagnosticsExportResult
    data class Failure(val error: DiagnosticsError) : DiagnosticsExportResult
}

data class DiagnosticsSnapshot(
    val generatedAtEpochMs: Long,
    val permissions: DiagnosticsPermissionSnapshot,
    val settings: DiagnosticsSettingsSnapshot,
    val sync: DiagnosticsSyncSnapshot,
    val fullSync: DiagnosticsFullSyncSnapshot,
)
```

`DiagnosticsSnapshot` 及其四个子 snapshot 是 `:core:diagnostics` 自己的纯数据契约，字段逐项承接现有诊断 JSON，绝不引用 `SettingsUiState`、Compose、Intent 或 Android View 类型。敏感边界固定为只记录 `tokenSet: Boolean`，不接收或保存 token/password 原文。

`:core:diagnostics` 声明自己的 `AppIdentityPort`；`DiagnosticsExporterImpl` 使用 `@Inject constructor(@ApplicationContext context, appIdentityPort)`。app 提供 `DiagnosticsIdentityAdapter`，并在 app-owned `DiagnosticsModule` 中唯一绑定 exporter 与该端口；Core 不创建 Hilt module。迁移期 `LegacyDiagnosticsAdapter` 只负责把旧 `SettingsUiState`、权限布尔值和同步状态复制为独立 `DiagnosticsSnapshot` 后调用 exporter，旧 `SettingsViewModel` 注入它并向 Screen 暴露单个 `exportDiagnostics(...)` 方法。`SettingsScreen.kt` 删除 `exportDiagnosticsFile`、JSON 和文件写入代码，暂时保留既有 share Intent 消费 `Success.fileUri`；Task 30 再把分享交给 app dispatcher，Task 31 删除这层 legacy adapter 与旧 Screen/ViewModel。

FileProvider 仍是 `${applicationId}.fileprovider`，`shared/` 路径与同秒覆盖语义不改。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:diagnostics:testDebugUnitTest --tests cc.pscly.onememos.diagnostics.DiagnosticsExporterTest --stacktrace
.\gradlew.bat :core:diagnostics:testDebugUnitTest :core:diagnostics:assembleDebug :feature:settings:assembleDebug :app:assembleDebug --stacktrace
```

Expected: 全部 PASS；旧 Screen 不再 import `java.io.File`、`org.json` 或 `FileProvider`，同秒测试确认第二次导出覆盖同一路径。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/diagnostics `
  app/src/main/java/cc/pscly/onememos/di/DiagnosticsIdentityAdapter.kt `
  app/src/main/java/cc/pscly/onememos/di/DiagnosticsModule.kt `
  feature/settings/build.gradle.kts `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/LegacyDiagnosticsAdapter.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt
git commit -m "refactor(diagnostics): 抽取安全诊断导出能力"
```

## Task 8: 建立 Navigation 3 类型化契约与序列化

**Files:**
- Retain unchanged until Task 31: `core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavKeys.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavKeyCodec.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/OneMemosNavigator.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationContract.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/FeatureEntryContributor.kt`
- Create: `core/navigation/src/test/java/cc/pscly/onememos/navigation/NavKeyCodecTest.kt`
- Modify: `core/navigation/build.gradle.kts`

- [ ] **Step 1: 写类型化键序列化 RED 测试**

`NavKeyCodecTest` 对统一类型契约中的每个具体 `OneMemosNavKey` 做 encode/decode round trip，特别覆盖包含 `/`、空格和非 ASCII 字符的 `EditorKey.uuid`、`ShareCardKey.uuid` 与 `TodoItemKey.itemId`、可空 `AuthKey.mode`、六个顶层根键和九个 Settings 键。`TodoItemKey` 的空白或未 trim `itemId/expectedOwnerKey` 必须返回 `INVALID_ARGUMENT`，构造校验异常不得逃逸到 Host。未知 discriminator、字段缺失和任意原始 Route 都必须返回拒绝结果，不能构造替代字符串。测试同时编译验证 `OneMemosNavigator`、`ExternalNavigationInput.TodoNotification/OpenTodoRoot`、`ExternalStackMutation`、去重策略、结果与拒绝类型可由 Task 9 直接引用，并断言 `TodoKey` 仍是 TODO 根。

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavKeyCodecTest --stacktrace
```

Expected: RED，错误指向键或 codec 尚不存在。

- [ ] **Step 2: 实现键、codec、导航端口、外部输入结果和无状态 contributor 契约**

`NavKeyCodec` 只接受 `OneMemosNavKey` 的封闭序列化模块：

```kotlin
sealed interface NavKeyDecodeResult {
    data class Success(val key: OneMemosNavKey) : NavKeyDecodeResult
    data class Rejected(val reason: NavKeyDecodeRejection) : NavKeyDecodeResult
}

enum class NavKeyDecodeRejection {
    UNKNOWN_TYPE,
    MALFORMED_PAYLOAD,
    INVALID_ARGUMENT,
}

interface NavKeyCodec {
    fun encode(key: OneMemosNavKey): String
    fun decode(value: String): NavKeyDecodeResult
}
```

Contributor 接口加入窄 Host chrome，只为顶层页面打开 app 抽屉，不暴露返回栈：

```kotlin
interface FeatureEntryHost {
    fun openDrawer()
}

interface FeatureEntryContributor {
    fun owns(key: OneMemosNavKey): Boolean

    fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey>
}
```

`OneMemosNavigator.kt` 完整实现“统一类型契约”中的 `BackResult` 与 `OneMemosNavigator` 接口。`ExternalNavigationContract.kt` 完整实现同节的 `ExternalNavigationInput`、`ExternalStackMutation`、`ExternalNavigationDuplicatePolicy`、结果、拒绝类型和 `TodoNavigationIntentContract`，不包含映射逻辑。这样 Task 8 的 contributor 和 Task 9 的状态机只依赖本任务已提交的类型；Task 10 仅新增 mapper 实现。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavKeyCodecTest --stacktrace
.\gradlew.bat :core:navigation:testDebugUnitTest :core:navigation:assembleDebug --stacktrace
```

Expected: PASS。此步不删除旧 `Routes.kt`，只建立等价键和测试；删除发生在 Task 31。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/navigation
git commit -m "feat(navigation): 建立类型化导航键与序列化契约"
```

## Task 9: 实现六个独立返回栈与进程恢复

**Files:**
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationState.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationStateMachine.kt`
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationStateStore.kt`
- Create: `core/navigation/src/test/java/cc/pscly/onememos/navigation/NavigationStateMachineTest.kt`
- Create: `core/navigation/src/test/java/cc/pscly/onememos/navigation/NavigationStateStoreTest.kt`

- [ ] **Step 1: 写六栈规则 RED 测试**

`NavigationStateMachineTest` 用参数化数据逐项断言：六个根栈存在且根键正确；首次激活 HOME；切换分区保留每个栈；重复选择当前分区不清栈、不回根；普通 Push 只写当前栈且允许重复；非根 Back 只弹当前栈；非 HOME 根 Back 激活并恢复 HOME；HOME 根 Back 返回 `ExitApplication`；详情键保留在发起它的栈；系统不维护顶层访问历史。外部操作另覆盖：`ResetToRoot` 只重置目标栈；`Push(ALLOW)` 总是追加；`Push(IGNORE_IF_TOP)` 仅在目标栈顶相同时忽略，同键位于更深处仍追加；即使当前激活其他分区，也必须检查 Todo 目标栈。

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavigationStateMachineTest --stacktrace
```

Expected: RED，因为状态机尚不存在。

- [ ] **Step 2: 实现纯 Kotlin 状态机**

```kotlin
class NavigationStateMachine(
    initial: NavigationSnapshot = NavigationSnapshot(),
) : OneMemosNavigator {
    private val _state = MutableStateFlow(initial.normalized())
    val state: StateFlow<NavigationSnapshot> = _state.asStateFlow()

    override fun push(key: OneMemosNavKey)
    override fun switchSection(section: TopLevelSection)
    override fun back(): BackResult
    fun applyExternal(result: ExternalNavigationResult)
}
```

`NavigationStateMachine` 是 `OneMemosNavigator` 的唯一 Core 实现；app Host 只持有这一个实例，不创建平行 adapter 或第二份状态。`normalized()` 拒绝空栈、根不匹配和不可识别键；恢复失败返回全新合法六根状态，不把旧字符串 Route 插入任何栈。`applyExternal(Accepted)` 按 `mutation` 修改目标分区栈后激活该分区：`ResetToRoot` 只替换目标栈为其根，`Push(ALLOW)` 追加，`Push(IGNORE_IF_TOP)` 仅在目标栈顶相等时 no-op；`Rejected` 是严格 no-op。普通 `OneMemosNavigator.push()` 保持 append-only，不套用外部去重策略。

- [ ] **Step 3: 写状态序列化与恢复 RED 测试**

`NavigationStateStoreTest` 构造六个不同深度的栈，序列化并恢复后逐项相等；覆盖配置重建、合法进程恢复、未知键、损坏 JSON、根错位和单栈损坏。单栈损坏时整体拒绝该快照并回到合法初始状态，不能部分拼接不可信状态。

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavigationStateStoreTest --stacktrace
```

Expected: RED，因为 store 尚不存在。

- [ ] **Step 4: 实现可保存状态 store 并运行 PASS**

```kotlin
interface NavigationStateStore {
    fun save(snapshot: NavigationSnapshot): String
    fun restore(encoded: String): NavigationRestoreResult
}

sealed interface NavigationRestoreResult {
    data class Restored(val snapshot: NavigationSnapshot) : NavigationRestoreResult
    data class Rejected(val reason: NavigationRestoreRejection) : NavigationRestoreResult
}
```

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavigationStateMachineTest --tests cc.pscly.onememos.navigation.NavigationStateStoreTest --stacktrace
.\gradlew.bat :core:navigation:testDebugUnitTest :core:navigation:assembleDebug --stacktrace
```

Expected: 全部 PASS。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/navigation
git commit -m "feat(navigation): 实现六分区独立返回栈与恢复"
```

## Task 10: 建立外部导航白名单映射

**Files:**
- Create: `core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationMapper.kt`
- Create: `core/navigation/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationMapperTest.kt`

- [ ] **Step 1: 写外部映射 RED 测试**

覆盖：分享和 `LegacyEditorExtra` 映射为 `Accepted(HOME, Push(EditorKey, ALLOW))`；完整 `TodoNotification(itemId, expectedOwnerKey)` 映射为 `Accepted(TODO, Push(TodoItemKey, IGNORE_IF_TOP))`；空白或缺失任一 Todo 字段返回 `INVALID_ARGUMENT`；`OpenTodoRoot` 与迁移期 `LegacyRouteExtra("todo")` 映射为 `Accepted(TODO, ResetToRoot)`；未知 route、损坏 editor 参数返回 `Rejected`。把映射结果交给 Task 9 状态机后，拒绝保持六栈完全不变，Todo 重复外部投递只在相同键已位于栈顶时幂等。

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.ExternalNavigationMapperTest --stacktrace
```

Expected: RED，因为 mapper 尚不存在。

- [ ] **Step 2: 实现唯一白名单**

```kotlin
class ExternalNavigationMapper {
    fun map(input: ExternalNavigationInput): ExternalNavigationResult =
        when (input) {
            is ExternalNavigationInput.SharedMemo -> input.uuid.editorResult()
            is ExternalNavigationInput.LegacyEditorExtra -> input.uuid.editorResult()
            is ExternalNavigationInput.TodoNotification -> input.todoResult()
            ExternalNavigationInput.OpenTodoRoot ->
                ExternalNavigationResult.Accepted(
                    section = TopLevelSection.TODO,
                    mutation = ExternalStackMutation.ResetToRoot,
                )
            is ExternalNavigationInput.LegacyRouteExtra ->
                if (input.value == "todo") {
                    ExternalNavigationResult.Accepted(
                        section = TopLevelSection.TODO,
                        mutation = ExternalStackMutation.ResetToRoot,
                    )
                } else {
                    ExternalNavigationResult.Rejected(ExternalNavigationRejection.UNKNOWN_VALUE)
                }
        }
}
```

`editorResult()` 对空值返回 `INVALID_ARGUMENT`，否则使用 `Push(EditorKey, ALLOW)`。`todoResult()` 先 trim 并拒绝空字段，再构造 `TodoItemKey` 和 `Push(..., IGNORE_IF_TOP)`；任何构造校验失败都映射为 `INVALID_ARGUMENT`，不做首页回落。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest --tests cc.pscly.onememos.navigation.ExternalNavigationMapperTest --stacktrace
.\gradlew.bat :core:navigation:testDebugUnitTest :core:navigation:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/navigation
git commit -m "feat(navigation): 建立外部输入白名单映射"
```

## Task 11: 迁移 Feature 自有 entries 并建立 Settings 临时 bridge

**Files:**
- Create: `feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeEntryContributor.kt`
- Create: `feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsEntryContributor.kt`
- Create: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoEntryContributor.kt`
- Create: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoItemTargetState.kt`
- Modify: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoViewModel.kt`
- Modify: `feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoScreen.kt`
- Create: `feature/todo/src/test/java/cc/pscly/onememos/ui/feature/todo/TodoViewModelTodoItemTargetTest.kt`
- Create: `feature/profile/src/main/java/cc/pscly/onememos/ui/feature/profile/ProfileEntryContributor.kt`
- Create: `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorEntryContributor.kt`
- Create: `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardEntryContributor.kt`
- Create: `feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthEntryContributor.kt`
- Create: `feature/welcome/src/main/java/cc/pscly/onememos/ui/feature/welcome/WelcomeEntryContributor.kt`
- Create: `app/src/main/java/cc/pscly/onememos/navigation/LegacySettingsEntryContributor.kt`
- Create: `app/src/test/java/cc/pscly/onememos/navigation/FeatureEntryRegistryTest.kt`
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/collections/build.gradle.kts`
- Modify: `feature/todo/build.gradle.kts`
- Modify: `feature/profile/build.gradle.kts`
- Modify: `feature/editor/build.gradle.kts`
- Modify: `feature/sharecard/build.gradle.kts`
- Modify: `feature/auth/build.gradle.kts`
- Modify: `feature/welcome/build.gradle.kts`
- Modify: `feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt`
- Modify: `feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardViewModel.kt`
- Modify: `feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthViewModel.kt`

- [ ] **Step 1: 写 entry 唯一性 RED 测试**

`FeatureEntryRegistryTest` 列举当前全部键，断言每个键恰有一个 contributor，任何重复或遗漏都失败；同时断言 `HomeEntryContributor` 同时拥有 `HomeKey` 和 `ArchivedKey`，归档 entry 构造 `HomeScreen(mode = HomeScreenMode.ARCHIVED)`；`TodoEntryContributor` 唯一拥有 `TodoKey` 与代表性的 `TodoItemKey`。测试明确断言没有 `feature/archived` 模块或 contributor。

迁移期由 app-owned `LegacySettingsEntryContributor` **唯一拥有全部九个 Settings 键**：`SettingsHubKey`、`AccountSyncSettingsKey`、`AccountManagementSettingsKey`、`AdvancedSyncSettingsKey`、`RecordEditingSettingsKey`、`ReminderCalendarSettingsKey`、`StorageOfflineSettingsKey`、`AppearanceInteractionSettingsKey`、`AboutAdvancedSettingsKey`。九个键暂时都构造现有 `SettingsScreen`，因此深链或恢复到未来键也有唯一可编译 owner；不得同时创建 Feature-owned `SettingsEntryContributor`。Task 30 会在一个提交内从聚合列表移除 legacy bridge，并加入最终 Feature contributor；Task 31 删除 bridge 文件。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.FeatureEntryRegistryTest --stacktrace
```

Expected: RED，因为 contributor 尚不存在。

- [ ] **Step 2: 各 Feature 在独占目录内实现 entry**

每个正式 Feature contributor 是 `object` 或无字段 class。它在自己的 `entry` 中调用本 Feature Screen、使用 `hiltViewModel()` 取得本 Feature ViewModel，并把 UI 回调转成 `navigator.push`、`navigator.back` 或 `navigator.switchSection`。顶层根页面从 `FeatureEntryHost.openDrawer()` 取得抽屉动作。app-owned `LegacySettingsEntryContributor` 是唯一迁移例外：它只桥接现有 Settings composable 与 app 已有的更新/快捷入口回调，不注入或复制任何 Settings 状态。

Editor、Share Card、Auth、Todo item 不再从 `Routes` 或字符串 `SavedStateHandle` 取参数。各 entry 将类型化键传给幂等初始化方法：

```kotlin
fun EditorViewModel.bind(key: EditorKey)
fun ShareCardViewModel.bind(key: ShareCardKey)
fun AuthViewModel.bind(key: AuthKey)
fun TodoViewModel.bind(key: TodoItemKey)
```

四个 `bind` 对同一键重复调用是 no-op，对进程恢复后的合法键重建页面状态。`TodoEntryContributor` 对 `TodoKey` 构造既有根页面，对 `TodoItemKey` 构造同一个 `TodoScreen` 并传入 typed target；关闭目标时只由 entry 调用 `navigator.back()`。所有 ViewModel 构造函数都不接收 `OneMemosNavigator`、`FeatureEntryHost` 或 contributor。

- [ ] **Step 3: 写并实现 Todo 条目目标加载**

`TodoViewModelTodoItemTargetTest` 覆盖：合法 owner 与存在条目从 Loading 到 Ready；Todo 未启用或 token 空时返回 DISABLED 且不查仓库；当前 owner 与 `expectedOwnerKey` 不一致时返回 `NOT_FOUND_OR_ACCOUNT_MISMATCH` 且不查仓库；仓库返回 null 使用相同不可用原因；软删除条目返回 DELETED；重复 bind 同键只查一次；切换键取消或覆盖旧查询；账号或设置变化使旧 Ready 失效；源码不含 `OneMemosNavigator`。

```kotlin
sealed interface TodoItemTargetState {
    data object Idle : TodoItemTargetState
    data class Loading(val key: TodoItemKey) : TodoItemTargetState
    data class Ready(val key: TodoItemKey, val item: TodoItem) : TodoItemTargetState
    data class Unavailable(
        val key: TodoItemKey,
        val reason: TodoItemUnavailableReason,
    ) : TodoItemTargetState
}

enum class TodoItemUnavailableReason {
    DISABLED,
    DELETED,
    NOT_FOUND_OR_ACCOUNT_MISMATCH,
}
```

`TodoViewModel` 额外注入现有 `OwnerKeyProvider`，以 key StateFlow 与当前 Settings 组合后使用 `flatMapLatest/mapLatest` 派生 `todoItemTargetState`。解析顺序固定为：非 BACKEND、token 空或无 current owner 时 DISABLED 且不查仓库；owner 不等于 `expectedOwnerKey` 时账号不匹配且不查仓库；随后调用现有 `TodoRepository.getItem(itemId)`；null 为找不到/账号不匹配；`deletedAt` 非空为 DELETED；其余为 Ready。不得新增仓库、DAO 或持久化 API。

`TodoScreen` 增加可选 `targetKey` 与 `onTargetDismiss`，entry 对 typed key 用 `LaunchedEffect(targetKey)` 调用 bind。Ready 直接复用现有 `TodoEditItemDialog`；保存、删除或关闭后调用 `onTargetDismiss` 弹出 `TodoItemKey`。Loading 显示不可交互加载状态，Unavailable 显示一次可关闭提示并在关闭后弹栈。根列表原有本地 `editingItem` 流程不变，不创建 Todo detail 模块、第二套编辑器或第二个 ViewModel。

```powershell
.\gradlew.bat :feature:todo:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.todo.TodoViewModelTodoItemTargetTest --stacktrace
```

Expected: PASS；恢复后的 `TodoItemKey` 只重载领域条目，不在保存状态中持有 `TodoItem`。

- [ ] **Step 4: 保持 Quick Capture 在 Navigation 3 之外**

不得为 `QuickCaptureActivity`、`QuickCaptureOverlayEntryActivity`、`QuickCaptureOverlayPickImagesActivity` 或 `ScreenshotQuickCaptureActivity` 创建 `NavKey` 或 contributor。它们继续由 app Manifest 和平台 Intent 独立启动。

- [ ] **Step 5: 运行聚焦 PASS 与全部 Feature 门禁**

```powershell
.\gradlew.bat :feature:todo:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.todo.TodoViewModelTodoItemTargetTest :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.FeatureEntryRegistryTest --stacktrace
.\gradlew.bat :feature:home:assembleDebug :feature:collections:assembleDebug :feature:todo:assembleDebug :feature:profile:assembleDebug :feature:editor:assembleDebug :feature:sharecard:assembleDebug :feature:auth:assembleDebug :feature:welcome:assembleDebug :feature:settings:assembleDebug :app:assembleDebug --stacktrace
```

Expected: PASS；Feature build 文件没有任何 `project(":feature:...")` 依赖。

- [ ] **Step 6: 创建中文原子提交**

```powershell
git add feature/home/build.gradle.kts feature/collections/build.gradle.kts feature/todo/build.gradle.kts feature/profile/build.gradle.kts feature/editor/build.gradle.kts feature/sharecard/build.gradle.kts feature/auth/build.gradle.kts feature/welcome/build.gradle.kts `
  feature/home/src/main/java/cc/pscly/onememos/ui/feature/home/HomeEntryContributor.kt `
  feature/collections/src/main/java/cc/pscly/onememos/ui/feature/collections/CollectionsEntryContributor.kt `
  feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoEntryContributor.kt `
  feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoItemTargetState.kt `
  feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoViewModel.kt `
  feature/todo/src/main/java/cc/pscly/onememos/ui/feature/todo/TodoScreen.kt `
  feature/todo/src/test/java/cc/pscly/onememos/ui/feature/todo/TodoViewModelTodoItemTargetTest.kt `
  feature/profile/src/main/java/cc/pscly/onememos/ui/feature/profile/ProfileEntryContributor.kt `
  feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorEntryContributor.kt `
  feature/editor/src/main/java/cc/pscly/onememos/ui/feature/editor/EditorViewModel.kt `
  feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardEntryContributor.kt `
  feature/sharecard/src/main/java/cc/pscly/onememos/ui/feature/sharecard/ShareCardViewModel.kt `
  feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthEntryContributor.kt `
  feature/auth/src/main/java/cc/pscly/onememos/ui/feature/auth/AuthViewModel.kt `
  feature/welcome/src/main/java/cc/pscly/onememos/ui/feature/welcome/WelcomeEntryContributor.kt `
  app/src/main/java/cc/pscly/onememos/navigation/LegacySettingsEntryContributor.kt `
  app/src/test/java/cc/pscly/onememos/navigation/FeatureEntryRegistryTest.kt
git commit -m "refactor(feature): 由功能模块拥有导航页面入口"
```

## Task 12: 在 app 集成 Navigation 3 Host 与平台输入

**Files:**
- Modify: `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/MainActivity.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/share/ShareToOneMemosActivity.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/di/InAppFallbackAdapter.kt`
- Modify: `core/sync/build.gradle.kts`
- Modify: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt`
- Modify: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderNotifyWorker.kt`
- Modify: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderAlarmReceiver.kt`
- Create: `core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderLaunchIntentFactory.kt`
- Create: `core/sync/src/test/java/cc/pscly/onememos/worker/TodoReminderNavigationIntentTest.kt`
- Create: `app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt`
- Create: `app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt`
- Create: `app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt`
- Create: `app/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParserTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/navigation/AppNavigationProcessRestorationTest.kt`

- [ ] **Step 1: 写 Intent 解析和进程恢复 RED 测试**

`ExternalNavigationIntentParserTest` 覆盖以下固定矩阵：`cc.pscly.onememos.extra.START_EDITOR_UUID`；`ACTION_OPEN_TODO` 同时携带非空 item/owner extras 时解析为 `TodoNotification(itemId, expectedOwnerKey)`；两个 extra 都不存在时解析为 `OpenTodoRoot`；只缺一个或显式空白时保留不完整 typed input 并由 mapper 返回 `INVALID_ARGUMENT`；兼容期 `START_ROUTE=todo` 解析为 `LegacyRouteExtra("todo")`；分享完成后的 editor 输入；未知值拒绝。`AppNavigationProcessRestorationTest` 先把六栈推进到不同页面，再覆盖提醒追加账号范围的 `TodoItemKey`、栈顶重复投递不追加、弹出后新的 `onNewIntent()` 可再次追加、根回退只重置 Todo 栈、未知输入不改栈，以及已消费初始 Intent 在旋转或进程恢复后不重放。

`TodoReminderNavigationIntentTest` 断言共享 factory 写入固定 action、item extra、owner extra 和基于 owner+item 的唯一 `Intent.data`；Worker 与 AlarmReceiver 传入各自已经校验的 item/owner；Work input 缺 owner 或当前 owner 不匹配时在查询/通知前安全结束；同 itemId 不同 owner 的 PendingIntent identity 不同；无 item 的 app 回退只写 action，不写两个 extra。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.ExternalNavigationIntentParserTest --tests cc.pscly.onememos.navigation.AppNavigationProcessRestorationTest --stacktrace
```

Expected: RED，因为 parser 与 Host 尚不存在。

- [ ] **Step 2: 显式聚合 contributor 并拒绝重复键**

`AppEntryContributors.kt` 只包含以下引用，不构造页面：

```kotlin
val appEntryContributors: List<FeatureEntryContributor> = listOf(
    HomeEntryContributor,
    CollectionsEntryContributor,
    TodoEntryContributor,
    ProfileEntryContributor,
    EditorEntryContributor,
    ShareCardEntryContributor,
    AuthEntryContributor,
    WelcomeEntryContributor,
    LegacySettingsEntryContributor,
)
```

Host 启动时先验证每个合法键恰有一个 owner；失败时立即抛出带键名的组合错误，不按列表顺序静默覆盖。此阶段九个 Settings 键均由 Task 11 的 app-owned legacy bridge 拥有，不能再聚合尚未创建的最终 Feature contributor。

- [ ] **Step 3: 用 Navigation 3 Host 替换 `NavHost/NavController`**

`OneMemosApp` 保留抽屉和全局更新提示，但目的地构造全部委托 contributor。抽屉固定六项，切换调用 `switchSection`；选中当前项只关闭抽屉。`AppNavigationHost` 将 active stack 交给 Navigation 3 `NavDisplay`，安装 saveable-state 与 ViewModel-store entry decorators，并按 `NavigationStateStore` 保存六栈；系统 Back 先关闭抽屉，再调用状态机，只有 `ExitApplication` 交还 Activity。保存状态只包含 `TodoItemKey`，不包含加载后的 `TodoItem`；恢复后由 Task 11 的 entry 重新 bind。

- [ ] **Step 4: 接入外部输入与欢迎页优先级**

`MainActivity` 在 `onCreate` 和 `onNewIntent` 把平台 Intent 解析为 `ExternalNavigationInput`。初始 `onCreate` 仅在 saved state 未标记 consumed 时解析；成功应用后标记 consumed 并清空 pending input。`onNewIntent()` 总是新投递，先 `setIntent(intent)`、重置 consumed，再解析。分享保留六栈现场并进入 HOME editor；完整 Todo 通知保留六栈现场、激活 TODO 并追加 `TodoItemKey`；栈顶相同外部投递不重复。未知值记录类型化诊断且不改变 UI。只有没有 pending 外部输入时，`AppStartViewModel.showWelcome` 才向 HOME 栈压入 `WelcomeKey`。

保持 `MainActivity.EXTRA_START_EDITOR_UUID = "cc.pscly.onememos.extra.START_EDITOR_UUID"`。`core/sync` 增加对 `:core:navigation` 的实现依赖，仅复用纯 action/extra 常量。`TodoReminderRescheduleWorker` 把 expected owner 写入 WorkManager data，并把 owner 纳入 unique work name、requestCode 与稳定通知身份。`TodoReminderNotifyWorker` 对缺 owner 的旧/损坏 work 或当前 owner 不匹配在查库前返回；`TodoReminderAlarmReceiver` 复用已验证的 `EXTRA_OWNER_KEY`。二者都通过 `TodoReminderLaunchIntentFactory` 写入固定 action、item/owner extras 和用 `Uri.Builder` 构造的 owner+item `Intent.data`。

`InAppFallbackAdapter` 在本任务停止生产 `START_ROUTE=todo`，只写 `ACTION_OPEN_TODO`，因为外部时钟回退没有可信 itemId/owner；parser 将其映射为 `OpenTodoRoot`。parser 对旧 `START_ROUTE=todo` 的读取仅用于迁移期兼容并在 Task 31 删除。Task 12 可分两个连续、各自通过门禁的提交：先提交 parser/Host/新旧输入双读，再原子切换 Worker、AlarmReceiver 与 fallback 生产者；不得先生产新负载而消费者尚不能读取。

- [ ] **Step 5: 运行聚焦 PASS、恢复与 app 门禁**

```powershell
.\gradlew.bat :core:sync:testDebugUnitTest --tests cc.pscly.onememos.worker.TodoReminderNavigationIntentTest :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.ExternalNavigationIntentParserTest --tests cc.pscly.onememos.navigation.AppNavigationProcessRestorationTest --tests cc.pscly.onememos.navigation.FeatureEntryRegistryTest --stacktrace
.\gradlew.bat :core:navigation:testDebugUnitTest :core:sync:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: 全部 PASS；旋转和进程重建不重复执行外部导航。

- [ ] **Step 6: 创建中文原子提交**

```powershell
git add app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt `
  app/src/main/java/cc/pscly/onememos/MainActivity.kt `
  app/src/main/java/cc/pscly/onememos/share/ShareToOneMemosActivity.kt `
  app/src/main/java/cc/pscly/onememos/di/InAppFallbackAdapter.kt `
  app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt `
  app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt `
  app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt `
  app/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParserTest.kt `
  app/src/test/java/cc/pscly/onememos/navigation/AppNavigationProcessRestorationTest.kt `
  core/sync/build.gradle.kts `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderRescheduleWorker.kt `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderNotifyWorker.kt `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderAlarmReceiver.kt `
  core/sync/src/main/java/cc/pscly/onememos/worker/TodoReminderLaunchIntentFactory.kt `
  core/sync/src/test/java/cc/pscly/onememos/worker/TodoReminderNavigationIntentTest.kt
git commit -m "feat(app): 集成六栈 Navigation 3 主机与外部输入"
```

## Task 13: 定义七个 Settings 领域接口与完整模型

**Files:**
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/SettingsCapabilityError.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/SettingsHubCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/AccountSyncSettingsCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/RecordEditingSettingsCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/ReminderCalendarSettingsCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/StorageOfflineSettingsCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/AppearanceInteractionSettingsCapability.kt`
- Create: `core/domain/src/main/java/cc/pscly/onememos/domain/settings/AboutAdvancedSettingsCapability.kt`
- Create: `core/domain/src/test/java/cc/pscly/onememos/domain/settings/SettingsContractsTest.kt`

- [ ] **Step 1: 写纯领域契约 RED 测试**

测试断言七个接口都存在；Hub 只有 `observe()`；六页各只有 `observe()` 和自己的 `execute(command)`；每个命令、快照与结果是封闭类型；领域包不引用 `android.*`、`androidx.*`、Retrofit、OkHttp、WorkManager、Compose、资源 ID 或用户文案。

```powershell
.\gradlew.bat :core:domain:test --tests cc.pscly.onememos.domain.settings.SettingsContractsTest --stacktrace
```

Expected: RED，因为契约尚不存在。

- [ ] **Step 2: 按统一类型契约实现 Hub、Account 和错误类型**

完整复制本计划“统一类型契约”中 `SettingsCapabilityError`、`SettingsHubCapability`、`SectionSummaryState`、`AccountSyncHealth`、`AccountSyncSettingsSnapshot`、命令和结果，不能增加万能 `Any` payload、字符串命令或共享巨型 `SettingsSnapshot`。

- [ ] **Step 3: 为其余五页定义精确 Snapshot 与 Result**

```kotlin
data class RecordEditingSettingsSnapshot(
    val defaultVisibility: MemoVisibility,
    val regexSearchEnabled: Boolean,
    val showTagCounts: Boolean,
    val quickInsertTimeEnabled: Boolean,
    val quickInsertTimeFormat: QuickInsertTimeFormat,
    val commandInFlight: RecordEditingSettingsCommand? = null,
)

data class ReminderCalendarSettingsSnapshot(
    val reminderMode: TodoReminderMode,
    val calendarEnabled: Boolean,
    val selectedCalendar: CalendarSummary?,
    val syncCalendarReminders: Boolean,
    val permission: CalendarPermissionState,
    val writableCalendars: List<CalendarSummary>,
    val commandInFlight: ReminderCalendarSettingsCommand? = null,
)

data class CalendarSummary(val id: Long, val label: String)
enum class CalendarPermissionState { GRANTED, DENIED, UNKNOWN }

data class StorageOfflineSettingsSnapshot(
    val imagePrefetchEnabled: Boolean,
    val prefetchMemoLimit: Int,
    val prefetchImageLimit: Int,
    val attachmentCacheLimitMb: Int,
    val cacheStats: CacheStats?,
    val commandInFlight: StorageOfflineSettingsCommand? = null,
)

data class AppearanceInteractionSettingsSnapshot(
    val themePalette: ThemePalette,
    val themeMode: ThemeMode,
    val quickCaptureOverlayEnabled: Boolean,
    val sealStampDurationMs: Int,
    val commandInFlight: AppearanceInteractionSettingsCommand? = null,
)

data class AboutAdvancedSettingsSnapshot(
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val update: UpdateSettingsSnapshot,
    val diagnosticsAvailable: Boolean,
    val attachmentUploadLimitMb: Int,
    val developerOptions: DeveloperOptions,
    val commandInFlight: AboutAdvancedSettingsCommand? = null,
)
```

每个文件定义自己的结果，不通过 typealias 合并：

```kotlin
sealed interface RecordEditingSettingsResult {
    data object Success : RecordEditingSettingsResult
    data object IgnoredDuplicate : RecordEditingSettingsResult
    data class Failure(val error: SettingsCapabilityError) : RecordEditingSettingsResult
}

sealed interface ReminderCalendarSettingsResult {
    data object Success : ReminderCalendarSettingsResult
    data object IgnoredDuplicate : ReminderCalendarSettingsResult
    data class Platform(val action: SettingsPlatformAction) : ReminderCalendarSettingsResult
    data class Failure(val error: SettingsCapabilityError) : ReminderCalendarSettingsResult
}

sealed interface StorageOfflineSettingsResult {
    data object Success : StorageOfflineSettingsResult
    data object IgnoredDuplicate : StorageOfflineSettingsResult
    data class Failure(val error: SettingsCapabilityError) : StorageOfflineSettingsResult
}

sealed interface AppearanceInteractionSettingsResult {
    data object Success : AppearanceInteractionSettingsResult
    data object IgnoredDuplicate : AppearanceInteractionSettingsResult
    data class Platform(val action: SettingsPlatformAction) : AppearanceInteractionSettingsResult
    data class Failure(val error: SettingsCapabilityError) : AppearanceInteractionSettingsResult
}

sealed interface AboutAdvancedSettingsResult {
    data object Success : AboutAdvancedSettingsResult
    data object IgnoredDuplicate : AboutAdvancedSettingsResult
    data class Platform(val action: SettingsPlatformAction) : AboutAdvancedSettingsResult
    data class UpdateDelivery(val action: cc.pscly.onememos.domain.update.UpdateDeliveryAction) : AboutAdvancedSettingsResult
    data class Failure(val error: SettingsCapabilityError) : AboutAdvancedSettingsResult
}
```

`SettingsPlatformAction`、`SettingsPermission`、`UpdateSettingsSnapshot`、`DeveloperOptions`、`UpdateDeliveryAction` 与 `UpdateDeliveryResult` 都是纯 Kotlin data/sealed/enum 类型，不含 Android 类型或资源 ID。

- [ ] **Step 4: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:domain:test --tests cc.pscly.onememos.domain.settings.SettingsContractsTest --stacktrace
.\gradlew.bat :core:domain:test :core:domain:classes --stacktrace
```

Expected: PASS。`:core:domain` 是纯 Kotlin/JVM 模块，因此使用 `test`、`classes`，不使用 Android variant 任务名。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/domain
git commit -m "feat(domain): 定义设置首页与六页深能力契约"
```

## Task 14: 实现账号与同步能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/account/AccountSyncSettingsCapabilityImpl.kt`
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/account/AccountSyncHealthMapper.kt`
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/SettingsCapabilityErrorMapper.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/account/AccountSyncSettingsCapabilityImplTest.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/SettingsCapabilityErrorMapperTest.kt`

- [ ] **Step 1: 写十状态、错误映射和重复抑制 RED 测试**

`AccountSyncSettingsCapabilityImplTest` 对规格 §6.5 的十种状态各写一个精确断言：`Unbound`、`ConfiguredSignedOut`、`Healthy`、`Syncing`、`Queued`、`Failed`、`AuthenticationExpired`、`FullResyncRunning`、`FullResyncFailed`、`FullResyncCompleted`。同时断言鉴权失效覆盖普通失败，全量重同步鉴权失败也只映射 `AuthenticationExpired`；并发两次 `SyncNow` 或 `FullResync` 时，第二次返回 `IgnoredDuplicate`，底层 scheduler 只调用一次。

`SettingsCapabilityErrorMapperTest` 覆盖 HTTP 401/403、其他 HTTP、`IOException`、`SecurityException`、平台 Activity 不存在、WorkManager 失败和未知异常，断言领域层不泄漏异常 message、HTTP response 或 WorkInfo。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.account.AccountSyncSettingsCapabilityImplTest --tests cc.pscly.onememos.settings.SettingsCapabilityErrorMapperTest --stacktrace
```

Expected: RED，因为实现和 mapper 尚不存在。

- [ ] **Step 2: 用单一优先级函数映射十种状态**

`AccountSyncHealthMapper.map` 固定按以下优先级返回一个状态：未绑定、已配置未登录、鉴权失效、全量重同步进行中、全量重同步失败、全量重同步完成、常规同步中、已排队、普通失败、健康。一次调用只返回一个 sealed subtype，不在 Snapshot 另存第二个“当前故障”。

```kotlin
internal fun mapAccountSyncHealth(input: AccountSyncHealthInput): AccountSyncHealth =
    when {
        !input.hasConfiguration -> AccountSyncHealth.Unbound
        !input.signedIn -> AccountSyncHealth.ConfiguredSignedOut
        input.authenticationExpired -> AccountSyncHealth.AuthenticationExpired
        input.fullResyncRunning -> AccountSyncHealth.FullResyncRunning(input.fullResyncProgress)
        input.fullResyncError != null -> AccountSyncHealth.FullResyncFailed(input.fullResyncError)
        input.fullResyncCompletedAt != null -> AccountSyncHealth.FullResyncCompleted(input.fullResyncCompletedAt)
        input.syncing -> AccountSyncHealth.Syncing
        input.queued -> AccountSyncHealth.Queued
        input.syncError != null -> AccountSyncHealth.Failed(input.syncError)
        else -> AccountSyncHealth.Healthy(input.lastSuccessAtEpochMs)
    }
```

- [ ] **Step 3: 实现观察、命令和按命令互斥**

能力组合 `SettingsRepository.settings`、`SyncStatusMonitor.globalState`、凭据存储与现有全量同步状态。`SyncNow` 只调用现有常规 `SyncScheduler.requestSync()`；`FullResync` 只调用现有 `requestFullResync()`；`Logout` 清 token、login mode、creator 与本机凭据并重排提醒；`ChangePassword` 保留现有长度、UTF-8 71 字节、两次输入和后端响应规则。

每种命令用 `Mutex.tryLock()` 做原子抑制，锁未取得时直接返回 `IgnoredDuplicate`；锁在 `finally` 释放，`commandInFlight` 在执行前后更新。不得清 WorkManager 队列、增加取消、冲突处理或自动修复。

- [ ] **Step 4: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.account.AccountSyncSettingsCapabilityImplTest --tests cc.pscly.onememos.settings.SettingsCapabilityErrorMapperTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS，十种状态和 duplicate suppression 全覆盖。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/account/AccountSyncSettingsCapabilityImpl.kt `
  core/settings/src/main/java/cc/pscly/onememos/settings/account/AccountSyncHealthMapper.kt `
  core/settings/src/main/java/cc/pscly/onememos/settings/SettingsCapabilityErrorMapper.kt `
  core/settings/src/test/java/cc/pscly/onememos/settings/account/AccountSyncSettingsCapabilityImplTest.kt `
  core/settings/src/test/java/cc/pscly/onememos/settings/SettingsCapabilityErrorMapperTest.kt
git commit -m "feat(settings): 实现账号同步深能力与错误映射"
```

## Task 15: 实现记录与编辑能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/record/RecordEditingSettingsCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/record/RecordEditingSettingsCapabilityImplTest.kt`

- [ ] **Step 1: 写观察、命令和重复抑制 RED 测试**

测试从 `SettingsRepository.settings` 映射默认可见性、正则搜索、标签数量、一键插入时间和格式；逐条执行五个命令并断言只调用对应 setter；底层写入暂停时重复相同命令返回 `IgnoredDuplicate` 且只写一次；写入异常映射为 `StorageFailure`。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.record.RecordEditingSettingsCapabilityImplTest --stacktrace
```

Expected: RED，因为实现尚不存在。

- [ ] **Step 2: 写最小实现**

`observe()` 只映射本页五项设置，`execute()` 是穷尽 `when`，不触发同步、网络、日历或诊断。命令互斥和错误映射复用 Task 14 的基础组件。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.record.RecordEditingSettingsCapabilityImplTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/record core/settings/src/test/java/cc/pscly/onememos/settings/record
git commit -m "feat(settings): 实现记录编辑深能力"
```

## Task 16: 实现提醒与日历能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/reminder/ReminderCalendarSettingsCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/reminder/ReminderCalendarSettingsCapabilityImplTest.kt`

- [ ] **Step 1: 写权限、日历和重复抑制 RED 测试**

测试覆盖提醒模式更新后重排、启用日历但权限缺失返回 `Platform(RequestPermissions(READ_CALENDAR, WRITE_CALENDAR))`、权限已授予时列出可写日历、选择/清除日历、同步日历提醒开关、立即重排、`SecurityException` 映射 `PermissionDenied`、没有可写日历映射 `PlatformUnavailable`，以及重复 `Reschedule` 只调度一次。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.reminder.ReminderCalendarSettingsCapabilityImplTest --stacktrace
```

Expected: RED，因为实现尚不存在。

- [ ] **Step 2: 实现只面向领域模型的编排**

能力只依赖 `SettingsRepository`、`TodoReminderScheduler` 和 Task 4 的 `SystemCalendarGateway`。权限请求只返回 `SettingsPlatformAction`，不持有 Activity 或 launcher；Compose 回传授权结果后重新提交明确命令。日历事件、提醒、时区、30 分钟默认时长和已保存映射继续由 `:core:calendar` 处理。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.reminder.ReminderCalendarSettingsCapabilityImplTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:calendar:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/reminder core/settings/src/test/java/cc/pscly/onememos/settings/reminder
git commit -m "feat(settings): 实现提醒日历深能力"
```

## Task 17: 实现存储与离线能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/storage/StorageOfflineSettingsCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/storage/StorageOfflineSettingsCapabilityImplTest.kt`

- [ ] **Step 1: 写缓存、离线和重复抑制 RED 测试**

测试覆盖四个持久化设置、显式 `RefreshStats`、三种清理命令、清理后刷新统计、单次失败不丢旧统计、无效负数映射 `InvalidInput`、I/O 失败映射 `StorageFailure`，以及重复清理只执行一次。`observe()` 不自行全盘扫描，只有已有统计或显式命令才访问 `CacheRepository.getCacheStats()`。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.storage.StorageOfflineSettingsCapabilityImplTest --stacktrace
```

Expected: RED，因为实现尚不存在。

- [ ] **Step 2: 实现最小编排**

用独立 `MutableStateFlow<CacheStats?>` 保存最近统计并与 Settings flow 合并。`ClearImageCache`、`ClearAttachmentCache`、`ClearAllCache` 依次调用现有 repository 方法并在成功后刷新；不删除数据库、设置或服务器数据。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.storage.StorageOfflineSettingsCapabilityImplTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/storage core/settings/src/test/java/cc/pscly/onememos/settings/storage
git commit -m "feat(settings): 实现存储离线深能力"
```

## Task 18: 实现外观与交互能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/appearance/AppearanceInteractionSettingsCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/appearance/AppearanceInteractionSettingsCapabilityImplTest.kt`

- [ ] **Step 1: 写主题、悬浮权限和重复抑制 RED 测试**

测试覆盖主题、明暗、悬浮记录和盖章时长观察；逐条执行四个命令；盖章时长范围沿用现有 `200..2000`；启用悬浮记录但没有 overlay 权限时返回类型化平台动作，授权后才持久化；重复写入只调用一次。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.appearance.AppearanceInteractionSettingsCapabilityImplTest --stacktrace
```

Expected: RED，因为实现尚不存在。

- [ ] **Step 2: 实现最小编排**

能力通过 `:core:quicktiles` 声明的 overlay 状态窄端口读取权限，不引用 app Activity。权限缺失返回 `SettingsPlatformAction.OpenOverlayPermissionSettings(packageName)`，页面 entry 交给 app dispatcher 打开该包的悬浮窗设置页，并把返回结果作为用户意图回传；其他三项只写 `SettingsRepository`。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.appearance.AppearanceInteractionSettingsCapabilityImplTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:quicktiles:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/appearance core/settings/src/test/java/cc/pscly/onememos/settings/appearance
git commit -m "feat(settings): 实现外观交互深能力"
```

## Task 19: 实现关于与高级能力

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/about/AboutAdvancedSettingsCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/about/AboutAdvancedSettingsCapabilityImplTest.kt`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/QuickTileRequester.kt`
- Create: `core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/AndroidQuickTileRequester.kt`
- Create: `core/quicktiles/src/test/java/cc/pscly/onememos/quicktiles/QuickTileRequesterTest.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/di/QuickTilesModule.kt`
- Modify: `core/update/src/main/java/cc/pscly/onememos/update/AppUpdateManager.kt`

- [ ] **Step 1: 写更新、快捷开关、诊断和重复抑制 RED 测试**

测试覆盖版本和已有更新状态观察、手动检查、下载、安装、取消忽略、诊断导出、两个 tile 请求、打开两个 app-owned capture Activity、派生字段重建、上传限制和开发者设置。重复检查、下载、导出、重建或 tile 请求只执行一次；更新网络错误、诊断 I/O 错误、tile 平台错误和 scheduler 错误映射为领域错误。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.about.AboutAdvancedSettingsCapabilityImplTest :core:quicktiles:testDebugUnitTest --tests cc.pscly.onememos.quicktiles.QuickTileRequesterTest --stacktrace
```

Expected: RED，因为能力和 requester 尚不存在。

- [ ] **Step 2: 实现 Core-owned tile requester 与更新平台动作**

`QuickTileRequester` 完全位于 `:core:quicktiles`，不通过 app dispatcher：

```kotlin
enum class QuickTileKind { QUICK_CAPTURE, SCREENSHOT_CAPTURE }

sealed interface QuickTileRequestResult {
    data class Completed(val statusCode: Int) : QuickTileRequestResult
    data object PlatformUnavailable : QuickTileRequestResult
}

interface QuickTileRequester {
    suspend fun request(kind: QuickTileKind): QuickTileRequestResult
}
```

`AppUpdateManager` 继续完成下载文件大小、SHA-256、包名、版本和签名证书集合校验；需要未知来源设置或启动安装器时返回统一契约中已有的 `OpenUnknownSourcesSettings` 或 `InstallApk`，不直接从 ViewModel 持有 Activity。`AndroidQuickTileRequester` 使用 application `Context` 封装 `StatusBarManager.requestAddTileService`、原 service FQCN、label、icon、executor 和 callback；系统服务缺失返回 `PlatformUnavailable`，callback 的状态码包装为 `Completed`。`QuickTilesModule` 在本任务增加 `QuickTileRequester` 的唯一绑定。`AboutAdvancedSettingsCapabilityImpl` 负责重复抑制和领域错误映射，不把 tile 请求转成 `SettingsPlatformAction`。

- [ ] **Step 3: 实现 About 能力编排**

`observe()` 只读取安装版本与已有缓存更新/诊断状态，不联网检查更新、不导出文件。每个命令调用一个现有能力。`InstallUpdate` 只调用唯一 `AppUpdateManager.requestDelivery()`，并把 `OpenUnknownSourcesSettings` 或 `InstallApk` 原样包装为 `AboutAdvancedSettingsResult.UpdateDelivery`；这两类动作不得转换成 `SettingsPlatformAction`。分享诊断和打开 app-owned capture 等其余 Android launcher 工作转换成 `AboutAdvancedSettingsResult.Platform`。两个 tile 命令直接调用 `QuickTileRequester` 并映射为本页成功或领域失败；打开 capture 只返回 `OpenQuickCapture` 或 `OpenScreenshotCapture`，最终 Intent 仍由 app adapter 生成。

- [ ] **Step 4: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.about.AboutAdvancedSettingsCapabilityImplTest :core:quicktiles:testDebugUnitTest --tests cc.pscly.onememos.quicktiles.QuickTileRequesterTest --stacktrace
.\gradlew.bat :core:update:testDebugUnitTest :core:quicktiles:testDebugUnitTest :core:diagnostics:testDebugUnitTest :core:settings:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/about/AboutAdvancedSettingsCapabilityImpl.kt `
  core/settings/src/test/java/cc/pscly/onememos/settings/about/AboutAdvancedSettingsCapabilityImplTest.kt `
  core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/QuickTileRequester.kt `
  core/quicktiles/src/main/java/cc/pscly/onememos/quicktiles/AndroidQuickTileRequester.kt `
  core/quicktiles/src/test/java/cc/pscly/onememos/quicktiles/QuickTileRequesterTest.kt `
  app/src/main/java/cc/pscly/onememos/di/QuickTilesModule.kt `
  core/update/src/main/java/cc/pscly/onememos/update/AppUpdateManager.kt
git commit -m "feat(settings): 实现关于高级深能力与平台结果"
```

## Task 20: 实现只读无副作用 Settings Hub

**Files:**
- Create: `core/settings/src/main/java/cc/pscly/onememos/settings/SettingsHubCapabilityImpl.kt`
- Create: `core/settings/src/test/java/cc/pscly/onememos/settings/SettingsHubCapabilityImplTest.kt`

- [ ] **Step 1: 写六项独立状态和零副作用 RED 测试**

测试建立带调用计数的 fake：网络更新检查、常规同步、全量重同步、全盘存储扫描、Calendar 写入、权限请求、诊断导出全部初始为 0。订阅 Hub 后推进六项本地/cached flow，断言每项独立从 `Loading` 到 `Ready` 或 `Error`；一项失败不阻塞其余五项；异常 `issue` 优先保留，普通摘要最多两个 `SummaryFact`；所有副作用计数仍为 0。Hub 类型不得出现 `execute` 或 refresh。

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.SettingsHubCapabilityImplTest --stacktrace
```

Expected: RED，因为 Hub 实现尚不存在。

- [ ] **Step 2: 只组合轻量本地与已缓存来源**

`SettingsHubCapabilityImpl.observe()` 组合 `SettingsRepository.settings`、`SyncStatusMonitor.globalState`、现有全量同步缓存、最后一次已知缓存统计、日历权限/连接缓存、`AppUpdateManager.uiState` 的当前值和最后一次诊断状态。它不调用任何方法名以 `request`、`check`、`refresh`、`scan`、`clear`、`export`、`download`、`install` 或 `execute` 开头的接口。

摘要顺序固定为账号与同步、记录与编辑、提醒与日历、存储与离线、外观与交互、关于与高级。每项错误用自己的 `catch` 转为该项 `Error`，不能让外层 combine 终止。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:settings:testDebugUnitTest --tests cc.pscly.onememos.settings.SettingsHubCapabilityImplTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :core:settings:assembleDebug --stacktrace
```

Expected: PASS，副作用计数全部为 0。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/settings/src/main/java/cc/pscly/onememos/settings/SettingsHubCapabilityImpl.kt core/settings/src/test/java/cc/pscly/onememos/settings/SettingsHubCapabilityImplTest.kt
git commit -m "feat(settings): 实现只读无副作用设置总览"
```

## Task 21: 完成七个能力的唯一 Hilt 装配

**Files:**
- Create: `app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt`
- Create: `app/src/test/java/cc/pscly/onememos/di/SettingsCapabilityInjectionTest.kt`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 写唯一绑定 RED 测试**

`SettingsCapabilityInjectionTest` 从 app Hilt 组件解析七个接口，断言每个接口可注入、同一 Singleton 解析稳定、没有重复默认绑定，并构造七个 `@HiltViewModel` 所需依赖。静态依赖测试断言 `feature/settings/src/main` 下没有 `@Module`、`@Provides`、`@Binds`、Provider 或基础设施实现。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.di.SettingsCapabilityInjectionTest --stacktrace
```

Expected: RED，因为绑定尚未完成。

- [ ] **Step 2: 在 app 组合根提供唯一默认绑定**

`SettingsCapabilityModule` 用七个 `@Binds @Singleton` 分别绑定 Hub 和六页实现。更新依赖复用 Task 3 已在 app 的 `AppUpdateModule`（唯一 `@Provides GitHubUpdateApi`），不在 `SettingsCapabilityModule` 重复提供更新依赖。app 继续提供 Core 模块消费的 `AppIdentityPort`、`QuickCaptureTargetPort`、`ScreenshotEntryPort`、`InAppFallbackPort` 适配器；组合根测试同时断言不存在重复默认绑定。

- [ ] **Step 3: 运行聚焦 PASS 与组合根门禁**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.di.SettingsCapabilityInjectionTest --stacktrace
.\gradlew.bat :core:settings:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS，Dagger 输出没有 duplicate binding 或 missing binding。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add app/src/main/java/cc/pscly/onememos/di/SettingsCapabilityModule.kt `
  app/build.gradle.kts `
  app/src/test/java/cc/pscly/onememos/di/SettingsCapabilityInjectionTest.kt
git commit -m "refactor(di): 唯一装配七个设置能力接口"
```

## Task 22: 补齐 Settings 所需共享可访问性原语

**Files:**
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/InkCard.kt`
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealButton.kt`
- Modify: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealIconButton.kt`
- Create: `core/designsystem/src/main/java/cc/pscly/onememos/ui/accessibility/ReducedMotion.kt`
- Create: `core/designsystem/src/test/java/cc/pscly/onememos/ui/component/SettingsPrimitivesAccessibilityTest.kt`
- Modify: `core/designsystem/build.gradle.kts`

- [ ] **Step 1: 写触控、焦点、语义和 reduced-motion RED 测试**

测试断言可点击 `InkCard`、`SealButton`、`SealIconButton` 的语义触控区域至少 `48dp × 48dp`；键盘/D-pad 焦点有可见纸墨边界；禁用状态有语义且不只改颜色；`contentDescription` 必填；系统减少动态效果启用时按压缩放和非必要旋转关闭，但结果文字仍立即显示。

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests cc.pscly.onememos.ui.component.SettingsPrimitivesAccessibilityTest --stacktrace
```

Expected: RED，现有 `SealIconButton` 可见尺寸为 `44dp` 且缺少统一焦点/reduced-motion 支持。

- [ ] **Step 2: 在共享原语做最小修正**

视觉尺寸可保持紧凑，但通过 `minimumInteractiveComponentSize()` 或外层交互包围盒达到 48dp；focus ring 使用主题 `primary` 和现有 `1dp` 纸面描边关系；reduced motion 由共享 `ReducedMotion.current` 读取系统偏好。不得在 Settings 页面复制焦点或动画分支。

- [ ] **Step 3: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest --tests cc.pscly.onememos.ui.component.SettingsPrimitivesAccessibilityTest --stacktrace
.\gradlew.bat :core:designsystem:testDebugUnitTest :core:designsystem:assembleDebug --stacktrace
```

Expected: PASS，现有调用方构建无回归。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add core/designsystem
git commit -m "fix(designsystem): 补齐设置原语可访问性状态"
```

## Task 23: 实现线册式只读 Settings Hub

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsUiEvent.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsPlatformActionDispatcher.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common/SettingsUpdateDeliveryDispatcher.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/common/SettingsUiContractsTest.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_hub_strings.xml`
- Modify: `feature/settings/build.gradle.kts`

- [ ] **Step 1: 配置既有测试栈所需的模块测试依赖**

`:feature:settings` 添加 JUnit 4、Robolectric、AndroidX Test Core、Coroutines Test，以及使用 Compose BOM 对齐、不写独立版本号的 `androidx.compose.ui:ui-test-junit4` 和 `ui-test-manifest`。开启 `unitTests.isIncludeAndroidResources = true`。不在此任务改任何生产依赖版本。

- [ ] **Step 2: 写共享事件契约与 Hub ViewModel RED 测试**

`SettingsUiContractsTest` 先引用尚不存在的 `SettingsUiEvent`、`SettingsPlatformActionDispatcher`、`LocalSettingsPlatformActionDispatcher`、`SettingsUpdateDeliveryDispatcher` 和 `LocalSettingsUpdateDeliveryDispatcher`，断言平台 dispatcher 只接收 `SettingsPlatformAction`，更新 dispatcher 只接收 `UpdateDeliveryAction`，两个接口均以 callback 回传各自结果且不持有 ViewModel、manager 或 Activity。`SettingsHubViewModelTest` 断言订阅 `SettingsHubCapability.observe()` 后六项状态按原样进入 `SettingsHubUiState`；单项 Error 不影响其他项；Hub ViewModel 没有任何写命令、刷新方法或平台事件；构造和订阅不会触发 fake 的网络、同步、扫描、权限、更新、下载、安装或诊断计数。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.common.SettingsUiContractsTest --tests cc.pscly.onememos.ui.feature.settings.hub.SettingsHubViewModelTest --stacktrace
```

Expected: RED，因为共享契约和 ViewModel 尚不存在。

- [ ] **Step 3: 写 Hub Compose RED 测试**

`SettingsHubScreenTest` 断言固定六行、序号 1 至 6、顺序与规格一致、每行整行可点击、最小高度 48dp、有“进入”语义；摘要最多两行；有 issue 时文字异常可见且排在普通摘要前；没有 Switch、Checkbox、Slider、内联操作或刷新语义。窗口分别设为 `360dp × 800dp`、`600dp × 960dp`、`840dp × 900dp`，断言始终单列，展开窗口内容宽度不超过 720dp。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.hub.SettingsHubScreenTest --stacktrace
```

Expected: RED，因为 Screen 尚不存在。

- [ ] **Step 4: 实现共享契约、最小 ViewModel 与线册式页面**

先按“统一类型契约”创建三个 common 文件：`SettingsUiEvent.kt` 定义事件、消息与确认；`SettingsPlatformActionDispatcher.kt` 定义 `SettingsPlatformResult`、平台 dispatcher 与对应 CompositionLocal；`SettingsUpdateDeliveryDispatcher.kt` 定义只接收 `UpdateDeliveryAction`、回传 `UpdateDeliveryResult` 的窄 dispatcher 与对应 CompositionLocal。三个文件都不引用 app、具体 launcher、`AppUpdateManager` 或 Activity。随后用 `ScrollPaperSurface` 或 `InkCard` 组合单列 `LazyColumn`。每行参数固定为 index、title、`SectionSummaryState`、目标 `OneMemosNavKey` 和 `onOpen(key)`；UI 只把领域状态映射为字符串资源。异常同时提供文字、图标和 `stateDescription`，颜色只辅助。整页不执行 LaunchedEffect 副作用，仅收集 StateFlow。

- [ ] **Step 5: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.common.SettingsUiContractsTest --tests cc.pscly.onememos.ui.feature.settings.hub.SettingsHubViewModelTest --tests cc.pscly.onememos.ui.feature.settings.hub.SettingsHubScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS，Hub 副作用计数仍为 0。

- [ ] **Step 6: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/common `
  feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/common `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub `
  feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/hub `
  feature/settings/src/main/res/values/settings_hub_strings.xml `
  feature/settings/build.gradle.kts
git commit -m "feat(settings-ui): 实现只读线册式设置首页"
```

## Task 24: 实现账号与同步页面和两个次级视图

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncScreen.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountManagementScreen.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AdvancedSyncScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_account_strings.xml`

- [ ] **Step 1: 写 ViewModel 状态、命令和事件非重放 RED 测试**

测试断言 ViewModel 只注入 `AccountSyncSettingsCapability`；十种 `AccountSyncHealth` 原样形成稳定 `UiState`；`SyncNow`、确认 logout、修改密码、确认 full resync 调用精确命令；`OpenLogin`、账号管理和高级同步发出类型化 `Navigate`。对 events 建立 collector A，消费一次后取消，再建立 collector B，断言已消费的 Navigate、Toast、Confirm 或 Platform 事件不会重放；重组期间保持同一个 STARTED collector 时事件也只消费一次。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.account.AccountSyncViewModelTest --stacktrace
```

Expected: RED，因为 ViewModel 尚不存在。

- [ ] **Step 2: 写全部十状态 Compose RED 测试**

`AccountSyncScreenTest` 参数化覆盖：

| 状态 | 唯一主动作 | 额外断言 |
| --- | --- | --- |
| `Unbound` | 登录 | 全量重同步隐藏 |
| `ConfiguredSignedOut` | 登录 | 全量重同步隐藏 |
| `Healthy` | 立即同步 | 低强调高级同步可见 |
| `Syncing` | 禁用“同步进行中” | 全量重同步禁用 |
| `Queued` | 禁用“等待同步” | 没有清队列入口 |
| `Failed` | 立即同步 | 显示领域错误文案 |
| `AuthenticationExpired` | 重新登录 | 不显示立即同步和全量重同步 |
| `FullResyncRunning` | 禁用“重同步进行中” | 不显示取消 |
| `FullResyncFailed` | 查看故障处理 | 次级视图重试前再次确认 |
| `FullResyncCompleted` | 无新增主动作 | 显示完成结果并禁用危险动作 |

测试同时断言普通失败和鉴权失效永不同时出现；首屏 TalkBack 顺序是标题、健康、主动作、最近成功、账号摘要、账号管理、高级同步；修改密码和退出只在 `AccountManagementScreen`，全量重同步只在 `AdvancedSyncScreen`；危险动作有影响说明和确认 Dialog，关闭后焦点回触发控件。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.account.AccountSyncScreenTest --stacktrace
```

Expected: RED，因为页面尚不存在。

- [ ] **Step 3: 实现诊断前置册页**

根页面顺序固定为返回与标题、同步健康、唯一主恢复动作、最近成功、账号摘要、账号管理、高级同步。状态文案只由资源映射真实领域值，不出现线框中的 `08:42`、`08:47`、`HTTP 400`、服务器地址或用户名样例。页面不提供队列清理、同步历史、自动冲突解决、自动修复或取消全量重同步。

- [ ] **Step 4: 实现次级视图与一次性确认**

账号管理页包含修改密码、退出登录及其确认；高级同步页包含影响说明、执行确认、进行中、失败重试和完成状态。Dialog 只发送用户确认结果，业务动作仍由 ViewModel 提交 capability。导航事件由 entry 转换成 Navigator 调用，ViewModel 不接收 navigator。

- [ ] **Step 5: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.account.AccountSyncViewModelTest --tests cc.pscly.onememos.ui.feature.settings.account.AccountSyncScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS，十种状态全部覆盖，一次性事件不重放。

- [ ] **Step 6: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/account feature/settings/src/main/res/values/settings_account_strings.xml
git commit -m "feat(settings-ui): 实现账号同步诊断前置册页"
```

## Task 25: 实现记录与编辑页面

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_record_strings.xml`

- [ ] **Step 1: 写 ViewModel RED 测试**

覆盖初始加载、稳定快照、五个命令、领域错误到持久错误/短暂确认的映射、命令执行中禁用重复提交，以及一次性确认不重放。测试 fake 只实现 `RecordEditingSettingsCapability`，不引入 Retrofit 或 WorkManager。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.record.RecordEditingViewModelTest --stacktrace
```

Expected: RED。

- [ ] **Step 2: 写 Compose RED 测试**

覆盖默认可见性、正则搜索、标签数量、一键插入时间、时间格式的当前值和交互；写操作执行中对应控件禁用；错误由文字和语义表达；返回目标 48dp；大字体不裁切标题、说明或选项。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.record.RecordEditingScreenTest --stacktrace
```

Expected: RED。

- [ ] **Step 3: 实现最小页面并运行 PASS**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.record.RecordEditingViewModelTest --tests cc.pscly.onememos.ui.feature.settings.record.RecordEditingScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/record feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/record feature/settings/src/main/res/values/settings_record_strings.xml
git commit -m "feat(settings-ui): 实现记录编辑能力页"
```

## Task 26: 实现提醒与日历页面

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_reminder_strings.xml`

- [ ] **Step 1: 写 ViewModel RED 测试**

覆盖提醒模式、日历启用、日历选择、同步日历提醒、立即重排；权限结果作为明确用户意图回传；`Platform(RequestPermissions)` 只发一次；授权 collector 重建不重放；错误稳定显示；重复提交禁用。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarViewModelTest --stacktrace
```

Expected: RED。

- [ ] **Step 2: 写 Compose RED 测试**

覆盖智能/准点提醒、日历开关、权限文字、可写日历列表、当前选择、清除选择、同步提醒和立即同步一次；未授权时不伪装已启用；Dialog 与 launcher 结果有动态播报；大字体和三个窗口保持单列。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarScreenTest --stacktrace
```

Expected: RED。

- [ ] **Step 3: 实现最小页面并运行 PASS**

Compose 只调用 ViewModel intent，权限 launcher 由 `LocalSettingsPlatformActionDispatcher` 执行。页面不 import `CalendarContract`、`Context.contentResolver` 或 WorkManager。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarViewModelTest --tests cc.pscly.onememos.ui.feature.settings.reminder.ReminderCalendarScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/reminder feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/reminder feature/settings/src/main/res/values/settings_reminder_strings.xml
git commit -m "feat(settings-ui): 实现提醒日历能力页"
```

## Task 27: 实现存储与离线页面

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_storage_strings.xml`

- [ ] **Step 1: 写 ViewModel RED 测试**

覆盖离线预取、两个上限、附件缓存上限、显式统计刷新、三个清理命令、确认事件、清理后统计、失败保留旧统计、重复清理禁用和事件不重放。fake 只实现 `StorageOfflineSettingsCapability`。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineViewModelTest --stacktrace
```

Expected: RED。

- [ ] **Step 2: 写 Compose RED 测试**

覆盖当前占用四项、缓存说明、离线开关、范围和上限、三个危险清理确认；清理按钮 48dp；加载/错误/禁用有文字与语义；页面进入不自动扫描全部文件；大字体不裁切数值单位或确认说明。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineScreenTest --stacktrace
```

Expected: RED。

- [ ] **Step 3: 实现最小页面并运行 PASS**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineViewModelTest --tests cc.pscly.onememos.ui.feature.settings.storage.StorageOfflineScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/storage feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/storage feature/settings/src/main/res/values/settings_storage_strings.xml
git commit -m "feat(settings-ui): 实现存储离线能力页"
```

## Task 28: 实现外观与交互页面

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_appearance_strings.xml`

- [ ] **Step 1: 写 ViewModel RED 测试**

覆盖主题色板、明暗、悬浮记录、盖章时长、overlay 平台权限结果、错误、重复提交和事件不重放。fake 只实现 `AppearanceInteractionSettingsCapability`。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionViewModelTest --stacktrace
```

Expected: RED。

- [ ] **Step 2: 写 Compose RED 测试**

覆盖三套主题、三种明暗模式、悬浮记录授权、盖章时长；状态有文字和选中语义，不只靠颜色；焦点边界可见；reduced motion 下没有非必要缩放或旋转；大字体和三个窗口无裁切。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionScreenTest --stacktrace
```

Expected: RED。

- [ ] **Step 3: 实现最小页面并运行 PASS**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionViewModelTest --tests cc.pscly.onememos.ui.feature.settings.appearance.AppearanceInteractionScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/appearance feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/appearance feature/settings/src/main/res/values/settings_appearance_strings.xml
git commit -m "feat(settings-ui): 实现外观交互能力页"
```

## Task 29: 实现关于与高级页面

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedViewModel.kt`
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreen.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedViewModelTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreenTest.kt`
- Create: `feature/settings/src/main/res/values/settings_about_strings.xml`

- [ ] **Step 1: 写 ViewModel RED 测试**

覆盖已有版本和更新状态、检查、下载、安装、取消忽略、诊断导出、两个 tile 请求、两个 capture 启动、派生字段重建、上传限制和开发者选项；`Platform` 与 `UpdateDelivery` 结果均单次发送且不重放；安装只发 `UpdateDelivery`，未知来源设置和 APK 安装绝不发 `Platform`；重复动作禁用；领域错误不泄漏 HTTP 或 Android 异常。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedViewModelTest --stacktrace
```

Expected: RED。

- [ ] **Step 2: 写 Compose RED 测试**

覆盖版本、更新进度与错误、忽略状态、快捷开关、立即打开、诊断导出、派生字段重建、附件上传和开发者选项；页面进入不联网检查更新、不导出诊断；Quick Capture 操作只触发独立 Activity 平台事件，没有导航键；动态结果有播报，错误不只靠颜色。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedScreenTest --stacktrace
```

Expected: RED。

- [ ] **Step 3: 实现最小页面并运行 PASS**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedViewModelTest --tests cc.pscly.onememos.ui.feature.settings.about.AboutAdvancedScreenTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/about feature/settings/src/main/res/values/settings_about_strings.xml
git commit -m "feat(settings-ui): 实现关于高级能力页"
```

## Task 30: 原子切换 Settings entry 与平台 launcher

**Files:**
- Create: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt`
- Create: `app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/update/AppUpdateDeliveryLauncher.kt`
- Create: `app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`
- Modify: `app/src/test/java/cc/pscly/onememos/navigation/FeatureEntryRegistryTest.kt`
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributorTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcherTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcherTest.kt`

- [ ] **Step 1: 写七表面 entry 和平台动作 RED 测试**

`SettingsEntryContributorTest` 断言 Settings contributor 唯一拥有 Hub、六能力页和账号两个次级键；每个 entry 在 Feature 内调用对应 screen 并通过 `hiltViewModel()` 取得对应 ViewModel；事件 collector 只在 STARTED 生命周期活动；Navigate 调用窄 Navigator，Platform 调用平台 dispatcher，UpdateDelivery 调用更新 dispatcher。`AppSettingsPlatformActionDispatcherTest` 只覆盖日历权限、overlay 设置、分享诊断、启动 Quick Capture overlay，以及打开 `QuickCaptureActivity` 与 `ScreenshotQuickCaptureActivity`；明确断言它不接受未知来源设置或 APK 安装。`AppSettingsUpdateDeliveryDispatcherTest` 覆盖未知来源设置、权限结果、APK 安装和安装器返回，断言始终转发给 Task 3 的同一个 `AppUpdateDeliveryLauncher` 与唯一 manager。tile 请求已由 Task 19 的 `QuickTileRequesterTest` 覆盖，不进入任何 app dispatcher。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsEntryContributorTest :app:testDebugUnitTest --tests cc.pscly.onememos.ui.settings.AppSettingsPlatformActionDispatcherTest --tests cc.pscly.onememos.ui.settings.AppSettingsUpdateDeliveryDispatcherTest --stacktrace
```

Expected: RED，因为最终 entry 和 dispatcher 尚未接线。

- [ ] **Step 2: 原子创建 Settings contributor 并在同一提交替换 legacy bridge**

`SettingsEntryContributor` 的 `owns` 穷尽以下键：`SettingsHubKey`、`AccountSyncSettingsKey`、`AccountManagementSettingsKey`、`AdvancedSyncSettingsKey`、`RecordEditingSettingsKey`、`ReminderCalendarSettingsKey`、`StorageOfflineSettingsKey`、`AppearanceInteractionSettingsKey`、`AboutAdvancedSettingsKey`。每个 entry 自己取得页面 ViewModel，不接收 repository、manager、scheduler 或 navigator 作为 ViewModel 参数。

`AppEntryContributors.kt` 从列表移除 `LegacySettingsEntryContributor` 并加入 `SettingsEntryContributor`，其余八个 contributor 不变。`FeatureEntryRegistryTest` 同步更新，断言九个 Settings 键现在归 Feature contributor 且只读旧页面的 bridge 已消失。此提交后仍存在旧的 `SettingsScreen`/`SettingsViewModel` 文件，它们不再被任何导航路径引用但暂不删除，确保整个提交可独立编译和测试。

- [ ] **Step 3: app 只执行平台 launcher，更新交付只依赖单一 dispatcher**

`OneMemosApp` 在 `NavDisplay` 外层同时提供 Task 23 定义的 `LocalSettingsPlatformActionDispatcher` 与 `LocalSettingsUpdateDeliveryDispatcher`。平台 dispatcher 只把日历权限、overlay 设置、FileProvider share、Quick Capture overlay service，以及 `QuickCaptureActivity`/`ScreenshotQuickCaptureActivity` 映射到 Activity Result API 或 Intent，回调转换成 `SettingsPlatformResult` 后交回发起页面 ViewModel；它不接受未知来源设置或 APK 安装。app 不调用 `StatusBarManager`，也不解释设置命令、状态或错误文案。

更新交付只通过一个 app-owned `AppUpdateDeliveryLauncher`（Task 3 已创建并绑定，本任务只做接线调整）：它执行未知来源设置或安装器 Intent，将 `UpdateDeliveryResult` 回送唯一 `AppUpdateManager`，并继续执行 manager 返回的后继动作。`AppSettingsUpdateDeliveryDispatcher` 实现 Task 23 定义的窄接口，把 ViewModel 发出的 `UpdateDelivery` 事件转发给上述 launcher并让 ViewModel 消费结果。页面 ViewModel 不直接持有 `AppUpdateManager`、Activity、launcher 或安装器 Intent，也不在 Settings capability 内复制更新状态机。两个 overlay entry Activity 继续由既有 tile/service 流程启动。

- [ ] **Step 4: 验证新表面源码边界并保留旧源码编译依赖**

本任务只对新建的 Hub、六页、common 契约和最终 contributor 做静态检查：它们只引用 `:core:domain`、`:core:navigation`、`:core:designsystem`、Compose、Lifecycle 和 Hilt ViewModel 构造集成，不出现 Hilt `@Module`、`@Provides`、`@Binds`、Provider、Retrofit、OkHttp、WorkManager 或 Android Calendar 实现。旧 `SettingsScreen.kt`、`SettingsViewModel.kt` 与 `LegacyDiagnosticsAdapter.kt` 仍参与源码编译，因此 `feature/settings/build.gradle.kts` 的旧基础设施依赖在本任务保持不变；Task 31 会在删除这些旧源码的同一个原子提交中移除禁止依赖。

- [ ] **Step 5: 运行聚焦 PASS、唯一 entry 和依赖门禁**

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsEntryContributorTest :app:testDebugUnitTest --tests cc.pscly.onememos.ui.settings.AppSettingsPlatformActionDispatcherTest --tests cc.pscly.onememos.ui.settings.AppSettingsUpdateDeliveryDispatcherTest --tests cc.pscly.onememos.navigation.FeatureEntryRegistryTest --stacktrace
.\gradlew.bat :feature:settings:testDebugUnitTest :feature:settings:assembleDebug :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS；每个键恰有一个 entry，新 Settings 表面没有基础设施引用或 Hilt provider/binding；旧源码和旧依赖仍保持可编译直到 Task 31。

- [ ] **Step 6: 创建中文原子提交**

```powershell
git add feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt `
  app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt `
  app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt `
  app/src/main/java/cc/pscly/onememos/update/AppUpdateDeliveryLauncher.kt `
  app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt `
  app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt `
  app/src/test/java/cc/pscly/onememos/navigation/FeatureEntryRegistryTest.kt `
  feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributorTest.kt `
  app/src/test/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcherTest.kt `
  app/src/test/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcherTest.kt
git commit -m "refactor(settings): 切换七表面入口与平台动作边界"
```

## Task 31: 删除旧导航与单体 Settings 结构

**Files:**
- Delete: `core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt`
- Delete: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt`
- Delete: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt`
- Delete: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/LegacyDiagnosticsAdapter.kt`
- Delete: `app/src/main/java/cc/pscly/onememos/navigation/LegacySettingsEntryContributor.kt`
- Modify: `core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationContract.kt`
- Modify: `core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationMapper.kt`
- Modify: `core/navigation/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationMapperTest.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/MainActivity.kt`
- Modify: `app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt`
- Modify: `app/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParserTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `feature/settings/build.gradle.kts`

- [ ] **Step 1: 运行删除前完整聚焦门禁**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest :core:settings:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS，类型化替代路径已完整覆盖生产调用。

- [ ] **Step 2: 删除旧结构并修正最后引用**

删除字符串 `Routes`、旧 `NavHost/NavController` 生产入口、旧聚合 `SettingsUiState`、巨型 `SettingsViewModel`、长 `SettingsScreen`、迁移期 `LegacyDiagnosticsAdapter` 和 app-owned `LegacySettingsEntryContributor`。在同一个提交中从 `feature/settings/build.gradle.kts` 删除 `:core:data`、`:core:network`、`:core:sync`、Retrofit、OkHttp 和 WorkManager，最终只保留 `:core:domain`、`:core:navigation`、`:core:designsystem`、Compose、Lifecycle 与 Hilt ViewModel 构造集成；这样依赖移除发生在最后一个旧源码消费者删除之后，不产生不可编译中间态。同时从 `ExternalNavigationContract.kt`、mapper、`MainActivity` 和 parser 删除 `LegacyRouteExtra`、`EXTRA_START_ROUTE` 与 `START_ROUTE=todo` 的兼容读取。三处生产者已在 Task 12 切换到固定 `ACTION_OPEN_TODO`：两类提醒携带 item/owner extras 并打开 `TodoItemKey`，`InAppFallbackAdapter` 因没有可信条目标识而继续以无 extras action 映射为 `OpenTodoRoot`；本任务不得删除该正式根回退或新增字符串兼容写入。`app/build.gradle.kts` 删除 Navigation Compose 2 的 `libs.androidx.navigation.compose`。

- [ ] **Step 3: 运行静态消失断言**

```powershell
$productionKotlin = Get-ChildItem app/src/main,core,feature -Recurse -File -Filter '*.kt' |
  Where-Object { $_.FullName -match '[\\/]src[\\/]main[\\/]' }
$legacyNavigation = @(
  'androidx.navigation.compose.NavHost',
  'rememberNavController',
  'NavController',
  'cc.pscly.onememos.ui.Routes',
  'Routes.',
  'LegacyRouteExtra',
  'cc.pscly.onememos.extra.START_ROUTE',
  'data class SettingsUiState',
  'class SettingsViewModel'
)
foreach ($needle in $legacyNavigation) {
  $matches = $productionKotlin | Select-String -SimpleMatch $needle
  if ($matches) { throw "仍存在旧生产导航或 Settings 结构：$needle" }
}

$forbiddenSettingsBuild = @(
  'project(":core:data")',
  'project(":core:network")',
  'project(":core:sync")',
  'libs.retrofit',
  'libs.okhttp',
  'libs.androidx.work.runtime.ktx'
)
foreach ($needle in $forbiddenSettingsBuild) {
  $matches = Select-String -Path feature/settings/build.gradle.kts -SimpleMatch $needle
  if ($matches) { throw "feature/settings 仍存在禁止依赖：$needle" }
}

$featureSettingsMain = Get-ChildItem feature/settings/src/main -Recurse -File -Filter '*.kt'
$forbiddenFeatureDi = @('@Module', '@Provides', '@Binds', 'javax.inject.Provider', 'dagger.Lazy')
foreach ($needle in $forbiddenFeatureDi) {
  $matches = $featureSettingsMain | Select-String -SimpleMatch $needle
  if ($matches) { throw "feature/settings 仍拥有基础设施绑定：$needle" }
}

if (Select-String -Path app/build.gradle.kts -SimpleMatch 'libs.androidx.navigation.compose') {
  throw 'app 仍依赖 Navigation Compose 2'
}
```

人工复核 `feature/settings/build.gradle.kts` 的项目依赖白名单只含 `:core:domain`、`:core:navigation`、`:core:designsystem`，其余生产库只含 Compose、Lifecycle 和 Hilt ViewModel 构造集成。静态检查只读 `src/main` 与生产 build 文件，不把测试 fake 误判为生产依赖。

- [ ] **Step 4: 运行删除后模块门禁**

```powershell
.\gradlew.bat :core:navigation:testDebugUnitTest :core:settings:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

Expected: PASS，无旧类型、字符串 route 或重复生产导航源。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add core/navigation/src/main/java/cc/pscly/onememos/ui/Routes.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsViewModel.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/LegacyDiagnosticsAdapter.kt `
  app/src/main/java/cc/pscly/onememos/navigation/LegacySettingsEntryContributor.kt `
  core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationContract.kt `
  core/navigation/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationMapper.kt `
  core/navigation/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationMapperTest.kt `
  app/src/main/java/cc/pscly/onememos/MainActivity.kt `
  app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt `
  app/src/test/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParserTest.kt `
  app/build.gradle.kts `
  feature/settings/build.gradle.kts
git commit -m "refactor(settings): 删除旧导航与单体设置结构"
```

## Task 32: 固化架构、依赖与 CI 门禁

**Files:**
- Create: `scripts/verify-architecture.sh`
- Create: `scripts/verify-architecture.ps1`
- Modify: `scripts/verify.sh`
- Modify: `scripts/verify.ps1`
- Modify: `scripts/README.md`
- Modify: `.github/workflows/android-benchmark.yml`
- Modify: `ARCHITECTURE.md`
- Create: `app/src/test/java/cc/pscly/onememos/architecture/FinalModuleBoundariesTest.kt`

- [ ] **Step 1: 写最终架构 RED 测试**

`FinalModuleBoundariesTest` 读取 `settings.gradle.kts` 和各 build 文件，断言六个新模块存在；Feature 之间没有依赖；Core 不依赖 app/Feature；`:feature:settings` 满足最终依赖白名单；`HomeEntryContributor` 拥有归档且没有 `:feature:archived`；ViewModel 源码不含 `OneMemosNavigator`；app 聚合 contributor 但不直接调用 Feature Screen；七个能力默认绑定唯一；`baselineprofile` 和 `macrobenchmark` 仍指向 `:app`。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.FinalModuleBoundariesTest --stacktrace
```

Expected: RED，因为脚本、最终文档和 CI 门禁尚未接线。

- [ ] **Step 2: 实现跨平台架构脚本**

两个脚本执行同一组确定性检查并在失败时列出文件和匹配项：模块清单、依赖方向、Feature Settings 白名单、Feature 间依赖、Core 反向依赖、Navigator 进入 ViewModel、重复 entry、旧 Routes/NavController、Hilt provider/binding 进入 Feature，以及 §10.1 不可变字面量。PowerShell 入口第一行保留 UTF-8 输出设置；Linux 脚本使用 `set -euo pipefail`。

`verify-architecture.ps1` 的失败接口固定为：

```powershell
function Assert-NoMatch {
  param([string]$Path, [string]$Pattern, [string]$Message)
  $matches = Get-ChildItem $Path -Recurse -File | Select-String -Pattern $Pattern
  if ($matches) {
    $matches | ForEach-Object { Write-Error $_.ToString() }
    throw $Message
  }
}
```

`verify-architecture.sh` 使用 `rg` 执行同一模式，成功时只输出 `verify-architecture.sh: OK`。

- [ ] **Step 3: 把架构和新模块测试接入本地门禁**

`verify.ps1` 与 `verify.sh` 先运行架构脚本，再运行 `testDebugUnitTest`、所有新增 Core/Settings Feature 测试、Lint、`:app:assembleBenchmark`、`:baselineprofile:assembleBenchmark` 和 `:macrobenchmark:assembleBenchmark`。Linux `--all` 继续负责设备相关扩展，但稳定发布必须显式使用完整模式。`scripts/README.md` 同步说明架构门禁、`main → 稳定 Tag → Tag Actions → Release` 的固定顺序，以及 Release 只在 Task 36 复核 Tag Actions Artifact 后创建，不再声称工作流自动发布。

- [ ] **Step 4: 调整 GitHub Actions 为先验证、后人工正式发布**

在 `.github/workflows/android-benchmark.yml` 执行以下确定修改：

1. **JDK 21**：`setup-java` step 保持 `java-version: '21'`，工作流 JDK 与 Toolchain 统一为 21；`compileOptions` 的 Java `VERSION_21` 不变。
2. **恢复稳定 Tag 验证触发器**：`push.branches` 保留 `main`，`push.tags` 只保留 `v*`，删除 `benchmark-*`；同时保留 PR 与无参数 `workflow_dispatch`。删除 `workflow_dispatch.inputs.publish_release/release_tag/release_title`，避免维护入口绕过 Task 36。`main` push run 是分支健康证据，稳定发布的最终远端证据必须来自随后推送的 `vMAJOR.MINOR.PATCH` Tag run。
3. **删除工作流内发布分支**：从元数据 step 删除 `INPUT_RELEASE_TAG`、`INPUT_RELEASE_TITLE`、`release_tag`、`release_title`、`stable_release` 及对应输出；Artifact 名固定为 `benchmark-apk-${version_name}-${version_code}-run${GITHUB_RUN_NUMBER}`。删除 `创建或更新 GitHub Release` step（现文件第 217–284 行）和 `追加 Release 摘要` step（现文件第 286–295 行），不得保留删除/覆盖 Release 资产的逻辑。
4. **保留纯验证产物**：main 与稳定 Tag workflow 都只执行固定签名门禁、构建时间戳 Benchmark APK、校验包名/版本/证书并上传唯一 Artifact，不创建或移动 Tag，也不创建 Release。Task 36 必须先推送 `main`，再创建并推送稳定 Tag，随后等待该 Tag 对应 run 成功并复核它的 Artifact，最后才创建非草稿、非预发布 latest Release。

工作流固定证书 SHA-256 继续是 `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`，main 与稳定 Tag run 缺任一签名 Secret 都必须失败。生成的步骤摘要改为“稳定版：完整门禁后推送 main 与 `vMAJOR.MINOR.PATCH` Tag；仅在该 Tag run 与 Artifact 复核成功后创建正式 Release”。Tag run 失败时工作流自身不得移动或删除 Tag；Task 36 按受控恢复规则完成同一版本闭环。

- [ ] **Step 5: 重写派生架构文档**

`ARCHITECTURE.md` 更新为最终模块、允许依赖、唯一组合根、DI 提供源、Navigation 3 entry 所有权、六个独立栈、七个 Settings 接口、平台窄端口和不可变回归约束。删除把 2026-02-02 三模块、字符串 Routes 或 Feature 直接依赖 data/network/sync 描述为当前事实的段落。

- [ ] **Step 6: 运行聚焦 PASS 与脚本门禁**

```powershell
.\scripts\verify-architecture.ps1
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.FinalModuleBoundariesTest --stacktrace
```

Linux 等价：

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./scripts/verify-architecture.sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:testDebugUnitTest --tests cc.pscly.onememos.architecture.FinalModuleBoundariesTest --stacktrace
```

Expected: PASS，脚本输出 `verify-architecture.*: OK`。

- [ ] **Step 7: 创建中文原子提交**

```powershell
git add scripts/verify-architecture.sh scripts/verify-architecture.ps1 scripts/verify.sh scripts/verify.ps1 scripts/README.md .github/workflows/android-benchmark.yml ARCHITECTURE.md app/src/test/java/cc/pscly/onememos/architecture/FinalModuleBoundariesTest.kt
git commit -m "build(ci): 固化导航设置架构与发布门禁"
```

## Task 33: 完成导航、Settings 与进程恢复集成回归

**Files:**
- Create: `app/src/test/java/cc/pscly/onememos/navigation/NavigationIntegrationTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/navigation/NavigationProcessDeathTest.kt`
- Create: `app/src/test/java/cc/pscly/onememos/settings/SettingsEndToEndStateTest.kt`
- Modify only if a RED integration test proves the corresponding gap: `core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationStateStore.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/MainActivity.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt`
- Modify only if a RED integration test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt`
- Modify only if a RED integration test proves the corresponding gap: `app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt`
- Modify: `baselineprofile/src/main/java/cc/pscly/onememos/baselineprofile/BaselineProfileGenerator.kt`
- Create: `macrobenchmark/src/main/java/cc/pscly/onememos/macrobenchmark/SettingsNavigationBenchmark.kt`

- [ ] **Step 1: 写 app 集成 RED 测试**

`NavigationIntegrationTest` 从 Host 层走六个分区：每个分区压入一个详情，切换后现场保留；当前分区重复点击不回根；各非 HOME 根返回 HOME；HOME 根返回退出；归档渲染 `HomeScreenMode.ARCHIVED`；分享进入 HOME editor；完整 Todo 通知保留其他五栈和 Todo 现场、激活 TODO 并压入精确的账号范围 `TodoItemKey`；同键已位于 Todo 栈顶时重复外部投递不追加，弹出后新的投递可再次打开；无 item 的正式回退只重置 Todo 栈；未知外部输入不改变任何栈；每个键只有一个 entry。

`NavigationProcessDeathTest` 用已保存 bundle/状态字符串销毁并重建 Host，断言六栈、当前分区、`TodoItemKey(itemId, expectedOwnerKey)` 等类型化参数和已消费外部输入恢复正确；恢复后只重新加载条目，不重放通知导航。`SettingsEndToEndStateTest` 走 Hub 六入口、账号管理、高级同步、Dialog、权限结果和返回栈，断言事件不因重组、旋转或重建重复执行。

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavigationIntegrationTest --tests cc.pscly.onememos.navigation.NavigationProcessDeathTest --tests cc.pscly.onememos.settings.SettingsEndToEndStateTest --stacktrace
```

Expected: RED，失败精确指出尚未接通的集成边界。

- [ ] **Step 2: 只修正集成接线**

修复必须限定在 Files 中列出的生产文件，并与具体 RED 断言一一对应：contributor 聚合只改 `AppEntryContributors.kt`；状态保存 key 只改 `NavigationStateStore.kt` 或 `AppNavigationHost.kt`；外部输入消费标记只改 parser、Host 或 `MainActivity.kt`；dispatcher 回调只改 `OneMemosApp.kt`、`SettingsEntryContributor.kt` 和两个 app dispatcher。不得顺手修改其他文件，不回到 Feature 内新增基础设施依赖，也不建立字符串 Route 兼容写路径。若失败根因超出该白名单，立即停止并先修订计划，不能把额外生产改动留到工作区。

- [ ] **Step 3: 更新性能路径选择器**

`BaselineProfileGenerator.generate()` 在既有启动、主页滚动和编辑返回路径之后，追加打开抽屉、进入设置、进入账号与同步、返回设置和返回主页。新增 `SettingsNavigationBenchmark`，使用 `MacrobenchmarkRule`、`FrameTimingMetric`、`StartupMode.COLD` 和 `CompilationMode.Partial(BaselineProfileMode.UseIfAvailable)` 重复执行同一 Settings 往返路径。两个文件都保持 `packageName = "cc.pscly.onememos"`，模块继续以 `:app` 为目标；选择器使用稳定中文文字或明确 test tag，不依赖旧 Navigation route。

- [ ] **Step 4: 运行聚焦 PASS 与模块门禁**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests cc.pscly.onememos.navigation.NavigationIntegrationTest --tests cc.pscly.onememos.navigation.NavigationProcessDeathTest --tests cc.pscly.onememos.settings.SettingsEndToEndStateTest --stacktrace
.\gradlew.bat :core:navigation:testDebugUnitTest :core:settings:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :baselineprofile:assembleBenchmark :macrobenchmark:assembleBenchmark --stacktrace
```

Expected: PASS，两个性能模块仍以 `:app` 为目标。

- [ ] **Step 5: 创建中文原子提交**

```powershell
git add -A -- core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationStateStore.kt `
  app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt `
  app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt `
  app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt `
  app/src/main/java/cc/pscly/onememos/MainActivity.kt `
  app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt `
  app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt `
  app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt `
  app/src/test/java/cc/pscly/onememos/navigation/NavigationIntegrationTest.kt `
  app/src/test/java/cc/pscly/onememos/navigation/NavigationProcessDeathTest.kt `
  app/src/test/java/cc/pscly/onememos/settings/SettingsEndToEndStateTest.kt `
  baselineprofile/src/main/java/cc/pscly/onememos/baselineprofile/BaselineProfileGenerator.kt `
  macrobenchmark/src/main/java/cc/pscly/onememos/macrobenchmark/SettingsNavigationBenchmark.kt
$allowed = @(
  '.ai_session.md',
  'core/navigation/src/main/java/cc/pscly/onememos/navigation/NavigationStateStore.kt',
  'app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt',
  'app/src/main/java/cc/pscly/onememos/navigation/AppNavigationHost.kt',
  'app/src/main/java/cc/pscly/onememos/navigation/ExternalNavigationIntentParser.kt',
  'app/src/main/java/cc/pscly/onememos/MainActivity.kt',
  'app/src/main/java/cc/pscly/onememos/ui/OneMemosApp.kt',
  'feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt',
  'app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsPlatformActionDispatcher.kt',
  'app/src/main/java/cc/pscly/onememos/ui/settings/AppSettingsUpdateDeliveryDispatcher.kt',
  'app/src/test/java/cc/pscly/onememos/navigation/NavigationIntegrationTest.kt',
  'app/src/test/java/cc/pscly/onememos/navigation/NavigationProcessDeathTest.kt',
  'app/src/test/java/cc/pscly/onememos/settings/SettingsEndToEndStateTest.kt',
  'baselineprofile/src/main/java/cc/pscly/onememos/baselineprofile/BaselineProfileGenerator.kt',
  'macrobenchmark/src/main/java/cc/pscly/onememos/macrobenchmark/SettingsNavigationBenchmark.kt'
)
$unstaged = @(git diff --name-only)
$untracked = @(git ls-files --others --exclude-standard)
if ($unstaged -or $untracked) {
  throw "Task 33 仍有未暂存或未跟踪变更：$(@($unstaged + $untracked) -join ',')"
}
$unexpected = @(git diff --cached --name-only | Where-Object { $_ -notin $allowed })
if ($unexpected) { throw "Task 33 暂存区包含非白名单文件：$($unexpected -join ',')" }
git commit -m "test(app): 覆盖六栈设置与进程恢复集成"
```

## Task 34: 完成视觉与可访问性 QA

**Files:**
- Create: `feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/SettingsAccessibilityMatrixTest.kt`
- Create: `app/src/androidTest/java/cc/pscly/onememos/settings/SettingsVisualMatrixTest.kt`
- Create: `docs/qa/settings-navigation3-visual-accessibility.md`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountManagementScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AdvancedSyncScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionScreen.kt`
- Modify only if a RED matrix test proves the corresponding gap: `feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreen.kt`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_hub_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_account_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_record_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_reminder_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_storage_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_appearance_strings.xml`
- Modify only if copy or semantics must change: `feature/settings/src/main/res/values/settings_about_strings.xml`
- Modify only if a shared primitive is the root cause: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/InkCard.kt`
- Modify only if a shared primitive is the root cause: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealButton.kt`
- Modify only if a shared primitive is the root cause: `core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealIconButton.kt`
- Modify only if reduced-motion semantics are the root cause: `core/designsystem/src/main/java/cc/pscly/onememos/ui/accessibility/ReducedMotion.kt`

- [ ] **Step 1: 写自动可访问性矩阵 RED 测试**

测试遍历 Hub 和六个能力页，断言所有可点击节点至少 `48dp × 48dp`；纯图标有动作型描述；状态有文字/图标/`stateDescription`；Hub 遍历顺序为 1 至 6；账号页顺序为标题、健康、主动作、摘要、账号管理、高级同步；动态错误、同步、权限和完成结果具备 live region；Dialog 关闭后焦点回触发节点；大字体下关键节点仍显示完整语义；reduced motion 分支不移除文字结果。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest --stacktrace
```

Expected: RED，列出不满足的语义节点。

- [ ] **Step 2: 写设备视觉矩阵测试**

`SettingsVisualMatrixTest` 为 Hub 与账号同步页捕获以下组合：`360dp × 800dp`、`600dp × 960dp`、`840dp × 900dp`；纸墨浅色、纸墨深色；系统大字体；TalkBack 顺序。额外抽查黛蓝与赛博的浅深模式。断言展开窗口内容最大 720dp、三尺寸仍单列、无横向滚动、标题/摘要/异常/按钮不裁切、无矩阵或双栏。

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.pscly.onememos.settings.SettingsVisualMatrixTest --stacktrace
```

Expected: RED，直到设备/模拟器矩阵和所有断言就位。

- [ ] **Step 3: 修复页面或共享原语问题并运行 PASS**

修复只可修改 Files 中列出的九个 Screen、七个 Settings 字符串资源和四个共享原语，并必须由具体 RED 断言驱动。视觉只可使用 `background/surface` 色阶、1dp 描边、纸面横线、主题竖线和印记。禁止阴影、渐变、玻璃、模糊、发光和大面积装饰色。普通文字对比度至少 `4.5:1`，大号/粗体和关键非文本状态至少 `3:1`；若共享原语不满足，在列出的 `:core:designsystem` 文件修复，不在页面复制样式。若根因需要白名单之外的生产文件，立即停止并先修订计划。

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests cc.pscly.onememos.ui.feature.settings.SettingsAccessibilityMatrixTest --stacktrace
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=cc.pscly.onememos.settings.SettingsVisualMatrixTest --stacktrace
```

Expected: PASS。

- [ ] **Step 4: 人工走查并记录证据**

在 `docs/qa/settings-navigation3-visual-accessibility.md` 用表格记录每个窗口、主题、明暗、大字体、TalkBack、键盘/D-pad/Switch Access、减少动态效果和对比度结果。逐页确认无裁切、焦点可见、状态不只靠颜色、动态播报正确、Dialog 焦点归还。记录设备型号、Android 版本、density、font scale、测试提交和截图路径，所有失败项修复并重跑后才勾选通过。

- [ ] **Step 5: 运行模块门禁并创建中文原子提交**

```powershell
.\gradlew.bat :core:designsystem:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest :app:assembleBenchmark --stacktrace
git add -A -- feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountManagementScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AdvancedSyncScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/record/RecordEditingScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/reminder/ReminderCalendarScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/storage/StorageOfflineScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/appearance/AppearanceInteractionScreen.kt `
  feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedScreen.kt `
  feature/settings/src/main/res/values/settings_hub_strings.xml `
  feature/settings/src/main/res/values/settings_account_strings.xml `
  feature/settings/src/main/res/values/settings_record_strings.xml `
  feature/settings/src/main/res/values/settings_reminder_strings.xml `
  feature/settings/src/main/res/values/settings_storage_strings.xml `
  feature/settings/src/main/res/values/settings_appearance_strings.xml `
  feature/settings/src/main/res/values/settings_about_strings.xml `
  core/designsystem/src/main/java/cc/pscly/onememos/ui/component/InkCard.kt `
  core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealButton.kt `
  core/designsystem/src/main/java/cc/pscly/onememos/ui/component/SealIconButton.kt `
  core/designsystem/src/main/java/cc/pscly/onememos/ui/accessibility/ReducedMotion.kt `
  feature/settings/src/test/java/cc/pscly/onememos/ui/feature/settings/SettingsAccessibilityMatrixTest.kt `
  app/src/androidTest/java/cc/pscly/onememos/settings/SettingsVisualMatrixTest.kt `
  docs/qa/settings-navigation3-visual-accessibility.md
$unstaged = @(git diff --name-only)
$untracked = @(git ls-files --others --exclude-standard)
if ($unstaged -or $untracked) {
  throw "Task 34 仍有未暂存或未跟踪变更：$(@($unstaged + $untracked) -join ',')"
}
git commit -m "test(settings-ui): 完成视觉与无障碍矩阵验收"
```

## Task 35: 通过版本控制的幂等脚本准备本地发布与核验

**Files:**
- Create: `scripts/release-prepare.ps1`
- Create: `scripts/release-state.ps1`
- Create: `scripts/release-verify-apk.ps1`
- Create: `scripts/release-publish.ps1`
- Create: `scripts/release-state.test.ps1`
- Modify: `scripts/README.md`
- Modify: `app/build.gradle.kts`
- Modify: `.ai_session.md`
- Create only during a same-version recovery: the non-versioned Git-path evidence returned by `git rev-parse --git-path "release-evidence/$targetTag-$candidateSha.json"`; do not commit it.

发行脚本均使用 UTF-8 无 BOM、`Set-StrictMode -Version Latest`、`$ErrorActionPreference = 'Stop'`。`release-state.ps1` 只包含纯推导函数；调用方负责收集工作树版本、HEAD、`origin/main`、本地/远端稳定 Tag、对应提交中的版本、Actions runs、Artifact、恢复门禁证据与 GitHub latest Release 后传入。纯函数输出唯一状态及 `targetVersion/targetVersionCode/targetTag/targetSha`，不读取文件、不执行 Git/Gradle/GitHub/APK 命令，也不以 `release-context.json` 决策。`targetSha` 在版本提交前为 `null`；正常路径在远端稳定 Tag 出现前跟随携带同一版本的本地发布提交，推送 Tag 后由其 peeled SHA 固定。恢复路径中，旧远端 Tag 仍 peel 到失败提交 A 时，`targetSha` 必须保持 A；同版本候选提交 B 只记录在 `recovery.candidateSha`。只有 B 已提交、绑定 B/A/旧 tag object 的完整本地门禁证据有效、B 已普通快进推送到 main，且用户随后给出精确书面授权时，`RecoverTag` 才可用 lease 更新 Tag；远端 Tag 成功 peel 到 B 后，`targetSha` 才切换为 B。

`release-prepare.ps1` 与 `release-publish.ps1` 都要求 `-OutputPath`，以同目录临时文件加原子 rename 写出 UTF-8 无 BOM JSON，并同步刷新忽略目录中的 `app/build/reports/release-context.json`。stdout 只供人阅读，不得解析。JSON 固定包含 `schemaVersion`、`ok`、`stage`、`state`、`reasonCode`、`message`、四个 target 字段、`baselineTag`、`cacheStatus`、`tag`、`run`、`artifact`、`release`、`recovery`、`transition` 与 `observedAt`；`tag` 同时记录远端 tag object id 与 peeled commit SHA。`recovery` 固定投影 `oldTagObjectId`、`oldTagPeeledSha`、`candidateSha`、`prePushOriginMainSha`、`gateEvidencePath`、`gateEvidenceSha256`、`gateEvidenceStatus`、`authorizationRequired` 与 `invalidatedSha`，其中 `gateEvidenceStatus` 只有 `Missing/Valid/Stale`。不得写授权文本、token、密码或 keystore 路径。`cacheStatus` 只有 `Missing/Consistent/Stale`，缓存缺失或陈旧只触发事实重收集和覆盖，不能改变纯状态。

同版本 Tag 恢复另使用不可由缓存替代的本地门禁证据，确定路径由 `git rev-parse --git-path "release-evidence/$targetTag-$candidateSha.json"` 解析，兼容普通仓库与 worktree。`PrepareRecovery` 以同目录临时文件加原子 rename 写入 UTF-8 无 BOM；自动门禁完成前只允许 `.pending` 文件，完整门禁与人工走查都通过后才生成最终 JSON。最终文件一经进入 `RecoveryPrepared` 不得覆盖；同一有效证据重复调用幂等返回。`release-context.json` 只能保存该文件路径、摘要和校验状态，不能重建已丢失的证据。

恢复门禁证据固定包含：`schemaVersion`、`kind="same-version-tag-recovery-local-gate"`、目标 Tag/版本/versionCode、完整 `candidateSha`、`oldTagObjectId`、`oldTagPeeledSha`、`prePushOriginMainSha`；门禁前后的 branch、HEAD、工作树/索引洁净状态、origin/main、远端 tag object 与 peeled SHA；失败 Tag run 的 workflow/event/headBranch/headSha/databaseId/attempt/conclusion/completedAt；`gate.profile/startedAt/completedAt/steps`；本地 APK 路径、文件名、包名、版本、SHA-256、签名证书与核验时间；人工走查的 checklistId、candidateSha、apkSha256、完成标记与确认时间；以及 `createdAt` 和排除自身后规范 JSON 的 `payloadSha256`。所有 Git 对象字段必须是小写完整 40 位 SHA。门禁步骤 ID 固定且各恰有一次成功记录：`verifyArchitecture`、`testDebugUnitTest`、`lint`、`assembleAppBenchmark`、`assembleBaselineProfileBenchmark`、`assembleMacrobenchmarkBenchmark`、`verifyApk`、`deviceReleaseExtensions`；单一 `passed=true` 不构成证据。

- [ ] **Step 1: 写 `release-state.ps1` 与 `release-state.test.ps1`**

`release-state.ps1` 包含全部可测试的纯逻辑。目标版本选择优先级固定为：远端稳定 Tag；携带唯一下一版本的 `origin/main`；作为 `origin/main` 快进后继且携带该版本的本地 HEAD；只在工作树出现的下一版本；最后才从最新完整 Release 推导新版本。`targetVersionCode` 只在 `NewRelease` 计算一次，之后始终复用工作树、HEAD、origin 或 Tag 提交中的值。远端 Tag 存在时默认以其 peeled SHA 作为 `targetSha`；若同版本 `origin/main` 或本地 HEAD 已快进到后继提交，只把该 SHA 暴露为 recovery candidate，并根据独立恢复门禁证据判定 `RecoveryCandidate/RecoveryPrepared/TagRecoveryRequired`，不能从分支位置推断门禁已经通过，未经书面授权也不得自动改变 `targetSha`。

状态判定固定如下：

1. `NewRelease`：HEAD 与工作树仍是最新完整 Release 的基线版本，没有下一版本 Tag/Release；按 `1.8.11 → 1.9.0` 特殊规则或 `vMAJOR.(MINOR+1).0` 通用规则生成唯一目标。
2. `PreparedWorktree`：HEAD 仍是基线版本，但工作树 `app/build.gradle.kts` 已是唯一下一版本，且除该文件与可选 `.ai_session.md` 外无脏文件；复用版本并重跑门禁，不再递增。
3. `Prepared`：干净的本地 `main` HEAD 已提交目标版本、无目标 Tag/Release，且是 `origin/main` 的快进后继；允许推送 main。
4. `MainPushed`：`HEAD == origin/main == targetSha`、无远端目标 Tag；允许立即创建并推送稳定 Tag，不等待 main push workflow。
5. `AwaitingTagActions`：远端目标 Tag 已 peel 到 `targetSha`，对应 Tag push run 尚未发现、queued 或 in progress。
6. `TagActionsFailed`：远端 annotated Tag object O peel 到目标 SHA A，A 的最新匹配 Tag push attempt 已结束且 conclusion 非 success，没有 Release，`origin/main == A`，且没有合格的已提交恢复候选；临时 CI、配额、Runner 或 Secrets 故障只允许重跑同一 Tag/A。
7. `RecoveryCandidate`：没有 Release，O 仍 peel 到 A，A 的最新匹配 Tag run 已失败；本地分支为 `main`，工作树与索引干净，完整 HEAD B 是 A 的严格后继和当前 `origin/main` 的快进后继，A/B 的 `versionName/versionCode` 完全相同，`origin/main` 尚未到 B，且没有绑定 B/O/A 的有效最终门禁证据。只允许 `PrepareRecovery`；正常 `Prepare` 返回 `USE_PREPARE_RECOVERY`。
8. `RecoveryPrepared`：所有 `RecoveryCandidate` 事实仍成立，且最终恢复门禁证据已校验有效，精确绑定 B、O、A、门禁前 `origin/main`、同版本、完整门禁、APK 与人工走查；`origin/main` 仍未到 B。只允许 `PushRecoveryMain`。
9. `TagRecoveryRequired`：有效恢复门禁证据仍存在，`origin/main == B`，O 仍 peel 到 A，B 仍是 A 的同版本严格后继，A 的匹配 Tag run 仍失败且没有 Release。必须停止并在此状态之后取得用户对 O、A、B 和证据摘要的明确书面授权，才能执行 `RecoverTag`。
10. `TagArtifactUnavailable`：目标 Tag Actions 成功，但该 Tag run 的唯一 Benchmark Artifact 缺失、过期、命名或版本不匹配；只允许重跑同一 Tag/SHA workflow，不得创建 Release。
11. `TagActionsSucceeded`：目标 Tag 最新 attempt 成功，存在唯一未过期且命名、版本匹配的 Benchmark Artifact，正式 Release 尚不存在；允许创建 Release。
12. `ReleaseIncomplete`：匹配的非草稿非预发布 Release 已存在且 Tag 正确，但尚无 APK 资产；允许重新核验 Tag run Artifact 后补传。
13. `ReleasePublished`：匹配 Release 恰有一个 APK，但本次调用尚未重新下载深度核验。
14. `Completed`：Tag、成功 Tag run、正式 latest Release 和唯一 APK 全部匹配，远端 APK 已重新下载并通过包名、版本、证书与 Tag run Artifact SHA-256 核验。
15. `Conflict`：版本/versionCode 不唯一、分支分叉、同名 Tag 指向其他 SHA、恢复证据在 main 已推后丢失或陈旧、未经授权出现 Tag 漂移、Release/Tag 不一致、多个 APK、远端 APK 字节不匹配或任何不可安全自动修复事实；立即停止，不自动移动/删除 Tag、不覆盖资产、不 force-push main。

`release-state.test.ps1` 用 Pester 或简单 task/throw 覆盖全部状态。对每个阶段分别删除 `release-context.json`，重新推导必须得到同一版本与正确阶段；额外覆盖 PreparedWorktree 不二次递增、main 已推但 Tag 未推、Tag 推送中断、main run 成功不得替代 Tag run、Tag run 只按 `targetTag + targetSha` 匹配、Tag Actions 失败后同 SHA 重跑，以及以下恢复矩阵：失败 O/A 与干净已提交同版本 B 推导 `RecoveryCandidate`；脏工作树、非完整 SHA、版本漂移或非快进 B 拒绝；正常 `Prepare` 拒绝恢复态；证据精确绑定 B/O/A 且全部固定步骤 exitCode=0、APK/证书/人工走查一致才进入 `RecoveryPrepared`；A 的证据不能证明 B，B 的证据不能证明后继 C；规范载荷摘要、HEAD、索引或 `.ai_session.md` 等受跟踪文件变化使证据陈旧；`PushRecoveryMain` 只从 `RecoveryPrepared` 捕获一次非 force 的 main push；main 已到 B 但缺少推送前证据进入 `Conflict`；授权早于 `TagRecoveryRequired`、确认串缺字段或旧 tag object lease 不匹配均拒绝；恢复后 `targetSha` 从 A 切到 B，A 的 run/Artifact、B 的 main run 都不能用于 B 的 Tag 门禁；只有 B 的最新 Tag push attempt 与其 Artifact 可发布。另覆盖 Artifact 校验失败不创建 Release、Tag 后 Release 创建中断、Release 缺资产恢复、Completed 深度复核和所有 Conflict。副作用测试注入命令 runner，只断言精确命令，不执行真实 Git。

```powershell
pwsh -NoProfile -NonInteractive -File scripts/release-state.test.ps1
```

Expected: PASS。测试覆盖十五态、稳定 Tag/Release 一致性、`1.8.11 → 1.9.0`、通用 minor 递增、工作树不二次递增、main 后立即 Tag、Tag run 门禁、绑定候选提交的受控同版本 Tag 恢复、同名 Tag 冲突、context 与恢复证据的不同丢失语义、部分 Release 恢复、Completed 幂等深度复核，以及 Hash 推导可复现。

- [ ] **Step 2: 提交脚本与文档更新**

```powershell
git add scripts/release-state.ps1 scripts/release-prepare.ps1 scripts/release-verify-apk.ps1 scripts/release-publish.ps1 scripts/release-state.test.ps1 scripts/README.md
git commit -m "build(release): 建立幂等版本推导与发布准备脚本"
```

- [ ] **Step 3: 运行 `release-prepare.ps1` 执行完整本地门禁**

先在当前 PowerShell 7 会话定义唯一 JSON 调用器：

```powershell
function Invoke-ReleaseJson {
    param(
        [Parameter(Mandatory)] [string] $Script,
        [string[]] $StageArgs = @()
    )

    $outputPath = Join-Path ([System.IO.Path]::GetTempPath()) "1memos-release-$([guid]::NewGuid()).json"
    try {
        & pwsh -NoProfile -NonInteractive -File $Script @StageArgs -OutputPath $outputPath
        $exitCode = $LASTEXITCODE
        if (-not (Test-Path -LiteralPath $outputPath)) {
            throw "发布脚本未生成机器输出：$outputPath"
        }
        $data = Get-Content -LiteralPath $outputPath -Raw -Encoding utf8 | ConvertFrom-Json -Depth 64
        if ($exitCode -ne 0 -or $data.ok -ne $true) {
            throw "发布阶段失败：exit=$exitCode state=$($data.state) reason=$($data.reasonCode) message=$($data.message)"
        }
        return $data
    } finally {
        Remove-Item -LiteralPath $outputPath -Force -ErrorAction SilentlyContinue
    }
}

$prepare = Invoke-ReleaseJson `
    -Script .\scripts\release-prepare.ps1 `
    -StageArgs @('-Stage', 'Prepare')

if ($prepare.state -notin @('PreparedWorktree', 'Prepared')) {
    throw "准备阶段状态错误：$($prepare.state)"
}

$targetVersion = [string] $prepare.targetVersion
$targetVersionCode = [int] $prepare.targetVersionCode
$targetTag = [string] $prepare.targetTag
$apkPath = [string] $prepare.artifact.localPath
$sha256 = [string] $prepare.artifact.sha256
$actualCert = [string] $prepare.artifact.signerSha256
```

脚本先调用纯状态判定：`NewRelease` 时只递增一次并进入 `PreparedWorktree`；`PreparedWorktree` 或 `Prepared` 时复用当前版本；其他发布阶段拒绝再次准备并给出下一阶段。允许状态都执行完整门禁（架构检查、`testDebugUnitTest`、`lint`、`:app:assembleBenchmark`、`:baselineprofile:assembleBenchmark`、`:macrobenchmark:assembleBenchmark`）、生成时间戳 Benchmark APK，并用精确 Build Tools `36.0.0` 目录中的 `aapt2` 与 `apksigner` 核验包名、版本、SHA-256 和固定证书。若会话中断或缓存被删，重新执行同一命令；状态推导保证不二次递增，必要时只重跑门禁。

`release-verify-apk.ps1` 读取 `$env:ANDROID_HOME/build-tools/36.0.0/aapt2` 和 `apksigner`，绑定固定证书 SHA-256 `58749c794f0c54af6b69bb6d80248a9fda0b75c687fde55b98d9575fc091633e`，断言包名 `cc.pscly.onememos`、版本匹配、证书集合唯一且不变。不再使用 `aapt dump badging`（Build Tools 36.0.0 为 `aapt2 dump badging`），也不再动态选择目录。

Linux 完整发布门禁等价使用 `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`，直接调用项目已有的 `scripts/verify.sh --all` 运行架构检查、`testDebugUnitTest`、`lint` 和全部 Benchmark 变体构建；本地 APK 时间戳复制与核验步骤由 `release-verify-apk.ps1` 完成。

- [ ] **Step 4: 安装并人工走查最终 APK**

```powershell
adb devices
adb -s $env:ADB_SERIAL install -r -d -g $apkPath
adb -s $env:ADB_SERIAL shell dumpsys package cc.pscly.onememos | Select-String 'versionName|versionCode'
```

Expected: 安装成功且设备版本匹配。人工走查六个栈、重复分区选择、根返回、分享、Todo 通知、进程恢复、Hub 六入口、账号十状态、更新、日历、tile、外部时钟回退、诊断导出及四个独立 capture Activity。不得用 Debug APK 替代。

- [ ] **Step 5: 记录证据并创建版本提交**

在 `.ai_session.md` 顶部追加本任务的 `$targetVersion`、`$targetVersionCode`、APK 路径、SHA-256、`$actualCert`、门禁与设备走查结果，不改写旧记录。随后只提交版本与会话留痕：

```powershell
git add app/build.gradle.kts .ai_session.md
$staged = @(git diff --cached --name-only | Sort-Object)
$expectedStaged = @('.ai_session.md', 'app/build.gradle.kts' | Sort-Object)
if (Compare-Object $staged $expectedStaged) { throw "发布提交包含非预期文件：$($staged -join ',')" }
git diff --cached --check
git commit -m "chore(release): 准备 $targetVersion 稳定版本"
$status = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'Status')

if ($status.state -ne 'Prepared') {
    throw "版本提交后预期 Prepared，实际为 $($status.state)"
}

$targetSha = [string] $status.targetSha
if ($targetSha -notmatch '^[0-9a-f]{40}$') {
    throw "targetSha 非完整 Git SHA：$targetSha"
}
if ((git status --porcelain)) {
    throw '版本提交后工作区不干净'
}
```

Expected: `$targetSha` 是干净发布提交，状态为 `Prepared`。脚本自动刷新已忽略的 `app/build/reports/release-context.json`；不得手工拼装缓存。删除缓存后，`Status` 从工作树、HEAD、origin、Tag、Actions 与 Release 重建同一版本和阶段，不回到 `NewRelease`，也不推导下一个版本。

- [ ] **Step 6: 仅为失败稳定 Tag 的已提交同版本候选执行 `PrepareRecovery`**

本步骤不是正常发布前置步骤，只在 Task 36 的 Tag Actions 因代码或配置缺陷失败、且另行批准的补充修复任务已经产出候选 B 后回到这里执行。补充修复任务必须先诊断根因，列出精确修改/测试文件、RED/PASS 命令和原子提交边界，更新本计划或新增同目录补充计划，并取得用户对该代码/配置修复和普通 main 推送的书面授权；不得在 Task 35/36 的开放式应急段直接修改未列出的生产文件。补充任务保持原 `versionName/versionCode`，在 `.ai_session.md` 记录失败 run、旧远端 tag object O、旧 peeled SHA A、根因、修复摘要和“恢复门禁待运行”，再把最小修复、测试与该留痕提交为同版本快进候选 B。候选 SHA 只能在提交后取得，因此 `.ai_session.md` 不自引用 B；B 的完整 SHA 由机器证据记录。提交后必须处于本地 `main`、工作树和索引干净，且不再修改任何受跟踪文件：

```powershell
$candidateSha = (git rev-parse HEAD).Trim()
if ($candidateSha -notmatch '^[0-9a-f]{40}$') { throw '恢复候选不是完整 Git SHA' }
if ((git branch --show-current) -ne 'main') { throw '恢复候选必须位于 main' }
if ((git status --porcelain)) { throw '恢复候选提交后工作区或索引不干净' }

$candidate = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'Status')
if ($candidate.state -ne 'RecoveryCandidate') {
    throw "预期 RecoveryCandidate，实际为 $($candidate.state)"
}

$oldTagObjectId = [string] $candidate.recovery.oldTagObjectId
$oldTagPeeledSha = [string] $candidate.recovery.oldTagPeeledSha
$targetTag = [string] $candidate.targetTag
$targetVersion = [string] $candidate.targetVersion
$targetVersionCode = [int] $candidate.targetVersionCode
$recoveryEvidencePath = (git rev-parse --path-format=absolute --git-path "release-evidence/$targetTag-$candidateSha.json").Trim()
```

首次调用 `PrepareRecovery` 只接受 `RecoveryCandidate`，重新 fetch 并断言 B 是 A 与当前 `origin/main` 的同版本快进后继、O 仍 peel 到 A、A 的最新匹配 Tag run 失败且没有 Release。它对干净提交 B 运行与正常 `Prepare` 相同的完整自动门禁、设备扩展和时间戳 Benchmark APK 核验，只写 `.pending` 证据，不推送 main、不操作 Tag：

```powershell
$gate = Invoke-ReleaseJson `
    -Script .\scripts\release-prepare.ps1 `
    -StageArgs @(
        '-Stage', 'PrepareRecovery',
        '-CandidateSha', $candidateSha,
        '-ExpectedOldTagObjectId', $oldTagObjectId,
        '-ExpectedOldTagPeeledSha', $oldTagPeeledSha,
        '-RecoveryEvidencePath', $recoveryEvidencePath
    )

if ($gate.ok -ne $true -or $gate.state -ne 'RecoveryCandidate' -or $gate.reasonCode -ne 'RECOVERY_WALKTHROUGH_REQUIRED') {
    throw "恢复门禁未进入人工走查阶段：$($gate.reasonCode)"
}
```

`RECOVERY_WALKTHROUGH_REQUIRED` 是 `ok=true`、`state=RecoveryCandidate` 的成功检查点，不以非零退出码表达；因此唯一 JSON 调用器会返回 `$gate`，但该检查点仍不允许推送。安装 `$gate.artifact.localPath`，按 Step 4 的完整清单人工走查，且不得用其他 APK。确认后用绑定 B 与该 APK SHA-256 的精确串完成同一阶段；脚本再次核验 HEAD、工作树、origin/main、O、A、版本、失败 run 和 `.pending` 内容，再原子生成最终证据：

```powershell
$preparedRecovery = Invoke-ReleaseJson `
    -Script .\scripts\release-prepare.ps1 `
    -StageArgs @(
        '-Stage', 'PrepareRecovery',
        '-CandidateSha', $candidateSha,
        '-ExpectedOldTagObjectId', $oldTagObjectId,
        '-ExpectedOldTagPeeledSha', $oldTagPeeledSha,
        '-RecoveryEvidencePath', $recoveryEvidencePath,
        '-FinalizeWalkthrough',
        '-ConfirmWalkthrough', "RECOVERY_WALKTHROUGH:$candidateSha`:$($gate.artifact.sha256)"
    )

if ($preparedRecovery.state -ne 'RecoveryPrepared') {
    throw "预期 RecoveryPrepared，实际为 $($preparedRecovery.state)"
}
```

最终证据的 `candidateSha/repository.headBefore/repository.headAfter` 必须全等于 B，`oldTagObjectId/tagObjectBefore/tagObjectAfter` 全等于 O，`oldTagPeeledSha/tagPeeledBefore/tagPeeledAfter` 全等于 A，门禁前后 `origin/main` 都等于 `prePushOriginMainSha` 且不等于 B；全部固定门禁步骤 exitCode 为 0，人工走查中的 B 与 APK SHA-256 必须匹配，时间顺序与规范载荷摘要必须有效。此后若 `.ai_session.md` 或任何受跟踪文件变化，证据立即陈旧；必须把变化提交为新候选 C，并从本步骤重新运行完整门禁，禁止给 B 补写“已通过”记录。

## Task 36: 推送 main 与稳定 Tag、等待 Tag Actions 并发布 GitHub latest Release

**Files:**
- 无源文件修改。`scripts/release-publish.ps1` 已在 Task 35 Step 2 与其余发布脚本一并提交。
- 执行时生成：`app/build/reports/release-notes.md`（已有 gitignore 覆盖）和下载的 CI Artifact。
- 不提交任何文件。

`release-publish.ps1` 是幂等副作用执行器：每次运行先重新收集事实并调用 `release-state.ps1`，`release-context.json` 只交叉核对和缓存。任何阶段收到 `Completed` 都重新深度复核后幂等退出；`NewRelease`/`PreparedWorktree` 提示返回 Task 35；`Conflict` 立即停止。正常阶段不移动或删除稳定 Tag、不重复递增版本、不 force-push main、不覆盖已有 APK，也不依赖上一次 PowerShell 会话。唯一例外是 Tag Actions 因代码或配置缺陷失败后的三阶段恢复：`PrepareRecovery` 对已提交候选 B 生成推送前完整门禁证据，`PushRecoveryMain` 只以普通快进推送 B，`RecoverTag` 只在随后进入 `TagRecoveryRequired` 并取得精确书面授权后以旧 tag object lease 更新 Tag。三个阶段不得合并、调序或用普通 `Prepare/PushMain/PushTag` 代替；任何参数、证据或远端事实漂移都停止。所有阶段都用 Task 35 定义的 `Invoke-ReleaseJson` 调用；若开启新会话，先重新定义该函数，不复用旧变量。

- [ ] **Step 1: 只推送 main**

```powershell
$push = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'PushMain')

if ($push.state -ne 'MainPushed') {
    throw "PushMain 后预期 MainPushed，实际为 $($push.state)"
}
```

`PushMain` 只允许 `Prepared` 状态并执行 `git push origin 'refs/heads/main:refs/heads/main'`；不得创建任何 Tag。推送后 fetch 并确认本地 HEAD、`origin/main` 与 `targetSha` 相等。已处于后续状态时只复核并返回。Expected: `MainPushed`，main 指向目标发布提交，稳定 Tag 仍不存在。

- [ ] **Step 2: 立即创建并推送稳定 Tag**

```powershell
$tagged = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'PushTag')

if ($tagged.state -ne 'AwaitingTagActions') {
    throw "PushTag 后预期 AwaitingTagActions，实际为 $($tagged.state)"
}
```

`PushTag` 只允许 `MainPushed`。脚本先 fetch 并断言本地 `main`、`origin/main` 与 `targetSha` 三者相等，目标版本提交中的 `versionName/versionCode` 匹配，远端目标 Tag 和 Release 均不存在；然后创建 annotated Tag，消息固定为 `1memos $targetVersion`，并执行 `git push origin "refs/tags/$targetTag`:refs/tags/$targetTag"`。推送后重新 fetch，记录远端 tag object id 与 peeled SHA，断言 peeled SHA 等于 `targetSha`。不得等待或依赖 main push run；`main` 推送完成后必须先完成本步骤，才进入 Actions 等待。

- [ ] **Step 3: 等待稳定 Tag 的 Actions 并核验 Artifact**

```powershell
$actions = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @(
        '-Stage', 'WaitTagActions',
        '-DiscoveryTimeoutMinutes', '5',
        '-CompletionTimeoutMinutes', '60',
        '-PollSeconds', '15'
    )

if ($actions.state -ne 'TagActionsSucceeded') {
    throw "Tag Actions 未进入成功状态：$($actions.state)"
}
```

run 必须同时满足 workflow=`android-benchmark.yml`、event=`push`、`headBranch == targetTag`、`headSha == targetSha`，并使用该 Tag 最新 run 的最新 attempt；任何 `headBranch=main` 的成功 run 都不能替代。正常路径的 `targetSha` 是首次 Tag 提交；受控恢复成功后只能是候选 B，失败提交 A 的任何旧 run 或 Artifact 即使后来成功也不得重新入选。5 分钟只用于发现 run，完成等待为 60 分钟，高于工作流 45 分钟 timeout。成功后要求唯一、未过期、命名和版本匹配的 Benchmark Artifact；下载到全新临时目录，压缩包内必须恰有一个 `YYYY-MM-DDTHH-mm-ss.apk`，再用 `release-verify-apk.ps1` 核验包名、versionName、versionCode、唯一固定证书并计算 SHA-256。只有全部通过才返回 `TagActionsSucceeded`。

- [ ] **Step 4: 仅在 Tag Actions 失败时完成受控同版本恢复**

`TagActionsFailed` 时立即停止发布。若是临时 GitHub、配额、Runner 或 Secrets 故障，只对同一 Tag/SHA 重跑 workflow，然后重新执行 `WaitTagActions`。若是代码或配置缺陷，Task 36 本身不修改任何文件：先报告失败事实并停止，诊断后为精确文件集、测试与原子提交补写决策完备的修复任务，取得用户对修复和普通 main 推送的书面授权。该补充任务必须保持同一 `versionName/versionCode`，先更新 `.ai_session.md`，再提交干净候选 B；随后按 Task 35 Step 6 对 B 运行完整 `PrepareRecovery` 与人工走查。不得先推 main，不得创建新版本或 Release。只有状态为 `RecoveryPrepared` 且最终恢复门禁证据有效时，才允许执行恢复专用 main 推送：

```powershell
$pushedRecovery = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @(
        '-Stage', 'PushRecoveryMain',
        '-RecoveryEvidencePath', $recoveryEvidencePath
    )

if ($pushedRecovery.state -ne 'TagRecoveryRequired') {
    throw "PushRecoveryMain 后预期 TagRecoveryRequired，实际为 $($pushedRecovery.state)"
}

$recoveryStatus = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'Status')
if ($recoveryStatus.state -ne 'TagRecoveryRequired') {
    throw "授权前状态不再是 TagRecoveryRequired：$($recoveryStatus.state)"
}

$targetTag = [string] $recoveryStatus.targetTag
$targetVersion = [string] $recoveryStatus.targetVersion
$candidateSha = [string] $recoveryStatus.recovery.candidateSha
$oldTagObjectId = [string] $recoveryStatus.recovery.oldTagObjectId
$oldTagPeeledSha = [string] $recoveryStatus.recovery.oldTagPeeledSha
$recoveryEvidencePath = [string] $recoveryStatus.recovery.gateEvidencePath
```

`PushRecoveryMain` 只接受 `RecoveryPrepared`。脚本重新校验最终证据及现场事实，要求当前 `origin/main == evidence.prePushOriginMainSha`、HEAD 等于证据中的 B、O 仍 peel 到 A、A 的最新匹配 Tag run 失败、A/B 版本完全相同且没有 Release；只执行普通快进 `git push origin "refs/heads/main:refs/heads/main"`，禁止 main force push。随后 fetch 并证明 `origin/main == B`、O 仍 peel 到 A，返回 `TagRecoveryRequired`，不操作 Tag。若推送响应中断但远端已到 B，下一次 `Status` 只能凭有效的推送前恢复证据和现场事实恢复 `TagRecoveryRequired`。

只有重新推导为 `TagRecoveryRequired` 后，主代理才向用户报告失败 Tag run ID/attempt、目标 Tag、O、A、B、恢复门禁证据 SHA-256、本地 APK SHA-256、固定证书摘要以及 `origin/main == B`，并请求一次新的书面授权。提前授权不生效且不得缓存；精确确认串区分大小写并固定为：

```text
RECOVER_TAG:<targetTag>:<oldTagObjectId>:<oldTagPeeledSha>:<candidateSha>:<gateEvidenceSha256>
```

取得授权后，以现场值构造完全相同的确认串并执行：

```powershell
$gateEvidenceSha256 = [string] $recoveryStatus.recovery.gateEvidenceSha256
$confirmation = "RECOVER_TAG:$targetTag`:$oldTagObjectId`:$oldTagPeeledSha`:$candidateSha`:$gateEvidenceSha256"

$recovered = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @(
        '-Stage', 'RecoverTag',
        '-ExpectedOldTagObjectId', $oldTagObjectId,
        '-ExpectedOldTagPeeledSha', $oldTagPeeledSha,
        '-NewTargetSha', $candidateSha,
        '-RecoveryEvidencePath', $recoveryEvidencePath,
        '-ExpectedRecoveryEvidenceSha256', $gateEvidenceSha256,
        '-ConfirmTagRecovery', $confirmation
    )

if ($recovered.state -ne 'AwaitingTagActions') {
    throw "RecoverTag 后预期 AwaitingTagActions，实际为 $($recovered.state)"
}
```

`RecoverTag` 必须在副作用前再次确认状态仍为 `TagRecoveryRequired`、没有 Release、`origin/main == B`、远端 tag object 精确等于 O 且 peel 到 A、A 的最新匹配 Tag attempt 仍失败、B 仍是 A 的同版本快进后继、证据有效且精确命名 B/O/A，授权串逐字匹配现场事实和证据摘要。随后只执行以下受控更新：

```powershell
git tag -f -a $targetTag $candidateSha -m "1memos $targetVersion"
git push --force-with-lease="refs/tags/$targetTag`:$oldTagObjectId" `
  origin "refs/tags/$targetTag`:refs/tags/$targetTag"
git fetch --force origin "refs/tags/$targetTag`:refs/tags/$targetTag"
if ((git rev-parse "$targetTag`^{}") -ne $candidateSha) {
    throw '恢复后的稳定 Tag 未指向授权的新提交'
}
```

lease 必须绑定 annotated tag object O，不能绑定 peeled A。禁止 plain `--force`、删除后重建远端 Tag、修改 main 历史、递增版本或在 lease 失败后降级为宽松推送；任一 O/A/B、证据或远端事实漂移都进入 `Conflict`。若远端移动成功但响应丢失，下一次 `Status` 应从“远端 Tag peel 到 B + 有效证据”恢复为 `AwaitingTagActions`，不得再次 force push。恢复成功后把 `targetSha` 从 A 切换为 B，清空当前 run/Artifact 选择，记录 `recovery.invalidatedSha=A` 并返回 Step 3；A 的全部 run/Artifact 和 B 的 main run 永久不能满足 B 的 Tag 门禁或 Release 来源。

- [ ] **Step 5: 创建或恢复正式 Release**

```powershell
$published = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'PublishRelease')

if ($published.state -notin @('ReleasePublished', 'Completed')) {
    throw "PublishRelease 后状态错误：$($published.state)"
}
```

Release 资产必须来自当前目标 Tag/`targetSha` 最新成功 Tag push run 重新下载并核验的 CI APK，不得使用 main run Artifact、Task 35 本地 APK，或受控恢复前 A 的任何 Artifact。Release 不存在时执行 `gh release create $targetTag "$artifactApk#$assetName" --verify-tag --title "1memos $targetVersion" --notes-file $releaseNotesPath --latest`，不传 draft/prerelease。匹配 Release 已存在但无 APK 时重新核验同一 Tag run Artifact 后用 `gh release upload` 补传，不用 `--clobber`；恰有一个 APK 时转入复核。多个 APK、错误 Tag、draft/prerelease 或已有 APK 内容不匹配一律 `Conflict`。

- [ ] **Step 6: 深度复核 latest Release**

```powershell
$completed = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'VerifyRelease')

if ($completed.state -ne 'Completed') {
    throw "正式发布未完成：$($completed.state)"
}
```

`VerifyRelease` 重新核验远端 Tag peeled SHA、对应当前 `targetSha` 的 Tag push run 成功、Release 非草稿非预发布、GitHub latest API 的 tag、唯一 APK、包名、版本和固定证书；Release APK SHA-256 必须等于该 Tag run Artifact SHA-256。受控恢复后还必须重新校验恢复门禁证据仍有效且当前 `targetSha == recovery.candidateSha`，不得接受 A 的 run/Artifact。本地 Task 35 APK 只要求包名、版本和证书一致，不强制与 CI APK 字节哈希相同。

- [ ] **Step 7: 清理与记录最终证据**

```powershell
$cleanup = Invoke-ReleaseJson `
    -Script .\scripts\release-publish.ps1 `
    -StageArgs @('-Stage', 'Cleanup')
git status --short
```

Expected: 状态仍为 `Completed`，工作区干净（`git status --porcelain` 无输出），`app/build/reports/release-context.json` 仅作为可删除缓存保留。分别在 main push 后、Tag push 后、Tag Actions 成功后、受控 Tag 恢复后和 Release 创建后删除缓存并重跑对应阶段，都从现场事实与独立恢复证据恢复同一版本与正确状态；正常 Tag 推送或经书面授权完成恢复后，`targetSha` 均由当前远端 Tag 固定。

缓存与证据丢失必须分开处理：`release-context.json` 缺失或陈旧时始终重收集事实并覆盖，不改变状态；最终恢复证据在 main 推送前缺失或陈旧时回到 `RecoveryCandidate`，必须重跑完整门禁与人工走查；HEAD 从 B 变成 C 时 B 的证据不能证明 C。若 `origin/main` 已到 B 才发现证据缺失/陈旧，进入 `Conflict(RECOVERY_PRE_PUSH_EVIDENCE_MISSING)`，禁止事后给 B 补证明；只能创建同版本快进候选 C，在推送 C 前完整门禁 C。若 Tag 已移到 B 后证据丢失/陈旧，进入 `Conflict(RECOVERY_EVIDENCE_LOST_AFTER_TAG_MOVE)` 并阻止 Actions 接受和 Release，计划不授权自动第二次移动 Tag。授权丢失或调用在远端 Tag 移动前中断时必须重新 fetch 并重新请求授权；远端已成功移到 B 时只恢复状态，不重复移动。

## 最终验收清单

- [ ] 已逐项对照 `docs/superpowers/specs/2026-07-14-settings-navigation3-redesign-design.md`，没有重新打开已批准设计分叉。
- [ ] Task 1 工具链锁定已执行：AGP 9.2.1、Gradle 9.4.1、daemon/脚本/CI JDK 21、JvmTarget.JVM_21、AGP 内置 Kotlin 2.3.10、Compose Compiler plugin 2.3.10、KSP 2.3.10、Build Tools 36.0.0、compileSdk 36/targetSdk 34/minSdk 33，所有依赖精确写死且无动态/预发布版本。
- [ ] 规格 §10.1 的全部不可变字面量测试通过。
- [ ] 六个新 Core 模块存在，依赖图没有 `core -> app/feature`。
- [ ] `QuickCaptureActivity`、两个 overlay entry Activity 与 `ScreenshotQuickCaptureActivity` 仍由 app 独立拥有，Quick Capture 不是 Nav3 destination。
- [ ] 六个顶层返回栈、序列化、配置重建、进程恢复、分享、Todo 通知和未知输入拒绝全部通过。
- [ ] 每个 `OneMemosNavKey` 恰有一个 Feature-owned entry，Archive 由 `HomeEntryContributor` 和 `HomeScreenMode.ARCHIVED` 提供。
- [ ] app 只聚合 contributor、Host、平台输入和最终 DI；Feature 构造自己的页面并取得 Hilt ViewModel；ViewModel 不持有 Navigator。
- [ ] 七个 Settings 接口、六个 capability 实现和只读 Hub 均通过错误映射、并发 duplicate suppression 与零副作用测试。
- [ ] `:feature:settings` 没有 data/network/sync、Retrofit、OkHttp、WorkManager 依赖，也没有 Hilt provider/binding。
- [ ] Hub 与六页共七个 UI 表面通过状态、事件非重放、可访问性和三窗口视觉矩阵；账号与同步十种状态全部覆盖。
- [ ] 旧 Routes、NavHost/NavController、巨型 Settings 状态/ViewModel/Screen 和直接基础设施依赖已删除。
- [ ] 完整单元测试、Lint、Benchmark、Baseline Profile、Macrobenchmark、安装与人工冒烟全部通过。
- [ ] 发布日版本从本地 Tag、origin 和 GitHub latest 解析，versionCode 高于稳定基线和 main。
- [ ] 时间戳 Benchmark APK 的包名、版本、SHA-256 和证书摘要已在本地、Actions Artifact 和 Release 资产三处复核。
- [ ] 若发生同版本 Tag 恢复，推送 main 前的最终门禁证据精确绑定完整候选 B、旧 tag object O 与 peeled A，包含全部固定门禁步骤、APK 和人工走查；`PushRecoveryMain`、书面授权、精确 lease 与 B 的 Tag run 严格按序完成，A 的 run/Artifact 未被复用。
- [ ] main、稳定 Tag、该 Tag 的成功 Actions、非草稿非预发布 latest Release 和唯一远端 Benchmark APK 形成完整闭环。

计划执行前仍须取得用户明确授权。规格批准、本计划完成或任何测试准备都不构成实施授权。
