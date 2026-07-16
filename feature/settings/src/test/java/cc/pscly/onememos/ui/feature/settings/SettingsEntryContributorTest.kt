package cc.pscly.onememos.ui.feature.settings

import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.navigation.AboutAdvancedSettingsKey
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AccountSyncSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AppearanceInteractionSettingsKey
import cc.pscly.onememos.navigation.HomeKey
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.RecordEditingSettingsKey
import cc.pscly.onememos.navigation.ReminderCalendarSettingsKey
import cc.pscly.onememos.navigation.SettingsHubKey
import cc.pscly.onememos.navigation.StorageOfflineSettingsKey
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SettingsEntryContributorTest {
    companion object {
        private lateinit var projectDir: File

        @BeforeClass
        @JvmStatic
        fun setup() {
            val path = System.getProperty("oneMemos.projectDir")
            require(!path.isNullOrBlank()) { "oneMemos.projectDir missing" }
            projectDir = File(path)
        }
    }

    private val settingsKeys: List<OneMemosNavKey> =
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

    @Test
    fun ownsExactlyNineSettingsKeys() {
        assertEquals(9, settingsKeys.size)
        settingsKeys.forEach { key -> assertTrue("missing owner for $key", SettingsEntryContributor.owns(key)) }
        assertFalse(SettingsEntryContributor.owns(HomeKey))
    }

    @Test
    fun eventRouter_forwardsEachVariantToItsSingleBoundary() {
        val calls = mutableListOf<String>()
        val callbacks =
            SettingsEventCallbacks(
                onNavigate = { calls += "navigate:$it" },
                onToast = { calls += "toast:$it" },
                onConfirm = { calls += "confirm:$it" },
                onPlatform = { calls += "platform:$it" },
                onUpdateDelivery = { calls += "update:$it" },
            )

        dispatchSettingsEvent(SettingsUiEvent.Navigate(AccountSyncSettingsKey), callbacks)
        dispatchSettingsEvent(SettingsUiEvent.Toast(SettingsMessage.COMMAND_SUCCEEDED), callbacks)
        dispatchSettingsEvent(SettingsUiEvent.Confirm(SettingsConfirmation.LOGOUT), callbacks)
        dispatchSettingsEvent(
            SettingsUiEvent.Platform(SettingsPlatformAction.StartQuickCaptureOverlay),
            callbacks,
        )
        dispatchSettingsEvent(
            SettingsUiEvent.UpdateDelivery(
                UpdateDeliveryAction.InstallApk(uri = "content://updates/one-memos.apk"),
            ),
            callbacks,
        )

        assertEquals(
            listOf(
                "navigate:AccountSyncSettingsKey",
                "toast:COMMAND_SUCCEEDED",
                "confirm:LOGOUT",
                "platform:StartQuickCaptureOverlay",
                "update:InstallApk(uri=content://updates/one-memos.apk, mimeType=application/vnd.android.package-archive)",
            ),
            calls,
        )
    }

    @Test
    fun source_usesNineHiltViewModelsAndOneStartedCollector() {
        val source = contributorSource()

        assertEquals(9, source.windowed("hiltViewModel<".length).count { it == "hiltViewModel<" })
        assertEquals(
            3,
            source.windowed("hiltViewModel<AccountSyncViewModel>()".length)
                .count { it == "hiltViewModel<AccountSyncViewModel>()" },
        )
        listOf(
            "SettingsHubScreen(",
            "AccountSyncScreen(",
            "AccountManagementScreen(",
            "AdvancedSyncScreen(",
            "RecordEditingScreen(",
            "ReminderCalendarScreen(",
            "StorageOfflineScreen(",
            "AppearanceInteractionScreen(",
            "AboutAdvancedScreen(",
        ).forEach { call -> assertTrue("missing $call", source.contains(call)) }
        assertTrue(source.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(source.contains("navigator.push(key)"))
        assertTrue(source.contains("Toast.makeText"))
        assertTrue(source.contains("LocalSettingsPlatformActionDispatcher.current"))
        assertTrue(source.contains("LocalSettingsUpdateDeliveryDispatcher.current"))
    }

    @Test
    fun screens_doNotCollectOneShotEventsOutsideEntry() {
        val screens =
            listOf(
                "account/AccountSyncScreen.kt",
                "account/AccountManagementScreen.kt",
                "account/AdvancedSyncScreen.kt",
                "record/RecordEditingScreen.kt",
                "reminder/ReminderCalendarScreen.kt",
                "storage/StorageOfflineScreen.kt",
                "appearance/AppearanceInteractionScreen.kt",
                "about/AboutAdvancedScreen.kt",
            )
        screens.forEach { relativePath ->
            val source =
                projectDir
                    .resolve(
                        "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/$relativePath",
                    ).readText()
            assertFalse("page-level event collector remains in $relativePath", source.contains("events.collect"))
        }
    }

    private fun contributorSource(): String =
        projectDir
            .resolve(
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/SettingsEntryContributor.kt",
            ).readText()
}
