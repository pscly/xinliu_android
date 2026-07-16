package cc.pscly.onememos.navigation

import cc.pscly.onememos.ui.feature.auth.AuthEntryContributor
import cc.pscly.onememos.ui.feature.collections.CollectionsEntryContributor
import cc.pscly.onememos.ui.feature.editor.EditorEntryContributor
import cc.pscly.onememos.ui.feature.home.HomeEntryContributor
import cc.pscly.onememos.ui.feature.profile.ProfileEntryContributor
import cc.pscly.onememos.ui.feature.settings.SettingsEntryContributor
import cc.pscly.onememos.ui.feature.sharecard.ShareCardEntryContributor
import cc.pscly.onememos.ui.feature.todo.TodoEntryContributor
import cc.pscly.onememos.ui.feature.welcome.WelcomeEntryContributor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class FeatureEntryRegistryTest {
    companion object {
        private lateinit var projectDir: File
        private lateinit var contributors: List<FeatureEntryContributor>

        @BeforeClass
        @JvmStatic
        fun setup() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) { "oneMemos.projectDir missing" }
            projectDir = File(path)
            contributors = appEntryContributors
        }
    }

    private val allKeys: List<OneMemosNavKey> =
        listOf(
            HomeKey,
            CollectionsKey,
            TodoKey,
            TodoItemKey(itemId = "item-1", expectedOwnerKey = "owner-1"),
            ProfileKey,
            ArchivedKey,
            SettingsHubKey,
            WelcomeKey,
            EditorKey(uuid = null),
            EditorKey(uuid = "memos/123"),
            ShareCardKey(uuid = "share/1"),
            AuthKey(mode = null),
            AuthKey(mode = AuthMode.CUSTOM_TOKEN),
            AccountSyncSettingsKey,
            AccountManagementSettingsKey,
            AdvancedSyncSettingsKey,
            RecordEditingSettingsKey,
            ReminderCalendarSettingsKey,
            StorageOfflineSettingsKey,
            AppearanceInteractionSettingsKey,
            AboutAdvancedSettingsKey,
        )

    @Test
    fun everyKey_hasExactlyOneOwner() {
        allKeys.forEach { key ->
            val owners = contributors.filter { it.owns(key) }
            assertEquals("key $key owners=$owners", 1, owners.size)
        }
    }

    @Test
    fun homeOwnsActiveAndArchived_todoOwnsRootAndItem() {
        assertTrue(HomeEntryContributor.owns(HomeKey))
        assertTrue(HomeEntryContributor.owns(ArchivedKey))
        assertTrue(TodoEntryContributor.owns(TodoKey))
        assertTrue(TodoEntryContributor.owns(TodoItemKey("i", "o")))
        assertFalse(HomeEntryContributor.owns(TodoKey))
    }

    @Test
    fun settingsFeatureOwnsAllNineSettingsKeys() {
        val settingsKeys =
            listOf(
                SettingsHubKey,
                AccountSyncSettingsKey,
                AccountManagementSettingsKey,
                AdvancedSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            )
        settingsKeys.forEach { key ->
            assertTrue(SettingsEntryContributor.owns(key))
            val owners = contributors.filter { it.owns(key) }
            assertEquals("settings key $key owners=$owners", 1, owners.size)
            assertEquals(SettingsEntryContributor, owners.single())
        }
        val registrySource =
            projectDir.resolve("app/src/main/java/cc/pscly/onememos/navigation/AppEntryContributors.kt")
                .readText()
        assertTrue(registrySource.contains("SettingsEntryContributor"))
        assertFalse(registrySource.contains("LegacySettingsEntryContributor"))
    }

    @Test
    fun noFeatureArchivedModuleExists() {
        val archivedModule = projectDir.resolve("feature/archived")
        assertFalse(archivedModule.exists())
        val contributorFiles =
            projectDir
                .resolve("feature")
                .walkTopDown()
                .filter { it.isFile && it.name.contains("ArchivedEntryContributor") }
                .toList()
        assertTrue("unexpected archived contributors: $contributorFiles", contributorFiles.isEmpty())
    }
}
