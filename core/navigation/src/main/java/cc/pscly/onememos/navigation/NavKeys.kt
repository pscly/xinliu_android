package cc.pscly.onememos.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface OneMemosNavKey : NavKey

@Serializable
data object HomeKey : OneMemosNavKey

@Serializable
data object CollectionsKey : OneMemosNavKey

@Serializable
data object TodoKey : OneMemosNavKey

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

@Serializable
data object ProfileKey : OneMemosNavKey

@Serializable
data object ArchivedKey : OneMemosNavKey

@Serializable
data object SettingsHubKey : OneMemosNavKey

@Serializable
data object WelcomeKey : OneMemosNavKey

@Serializable
data class EditorKey(val uuid: String? = null) : OneMemosNavKey

@Serializable
data class ShareCardKey(val uuid: String) : OneMemosNavKey

@Serializable
enum class AuthMode {
    CUSTOM_TOKEN,
}

@Serializable
data class AuthKey(val mode: AuthMode? = null) : OneMemosNavKey

@Serializable
data object AccountSyncSettingsKey : OneMemosNavKey

@Serializable
data object AccountManagementSettingsKey : OneMemosNavKey

@Serializable
data object AdvancedSyncSettingsKey : OneMemosNavKey

@Serializable
data object RecordEditingSettingsKey : OneMemosNavKey

@Serializable
data object ReminderCalendarSettingsKey : OneMemosNavKey

@Serializable
data object StorageOfflineSettingsKey : OneMemosNavKey

@Serializable
data object AppearanceInteractionSettingsKey : OneMemosNavKey

@Serializable
data object AboutAdvancedSettingsKey : OneMemosNavKey

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
