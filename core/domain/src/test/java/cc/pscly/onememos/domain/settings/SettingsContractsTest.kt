package cc.pscly.onememos.domain.settings

import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class SettingsContractsTest {
    companion object {
        private lateinit var projectDir: File

        @BeforeClass
        @JvmStatic
        fun setup() {
            val path = System.getProperty("oneMemos.projectDir")
            projectDir =
                if (!path.isNullOrBlank()) {
                    File(path)
                } else {
                    var cur = File("").absoluteFile
                    while (cur.parentFile != null && !File(cur, "settings.gradle.kts").exists()) {
                        cur = cur.parentFile
                    }
                    cur
                }
        }
    }

    @Test
    fun sevenCapabilityInterfaces_existWithExpectedMethods() {
        assertTrue(SettingsHubCapability::class.java.isInterface)
        assertEquals(listOf("observe"), publicMethodNames(SettingsHubCapability::class.java))

        val pages =
            listOf(
                AccountSyncSettingsCapability::class.java,
                RecordEditingSettingsCapability::class.java,
                ReminderCalendarSettingsCapability::class.java,
                StorageOfflineSettingsCapability::class.java,
                AppearanceInteractionSettingsCapability::class.java,
                AboutAdvancedSettingsCapability::class.java,
            )
        pages.forEach { iface ->
            assertTrue(iface.isInterface)
            assertEquals("$iface methods", listOf("execute", "observe"), publicMethodNames(iface))
        }
    }

    @Test
    fun snapshotsCommandsResults_areSealedOrDataTypes() {
        assertTrue(SettingsCapabilityError::class.java.isSealed)
        assertTrue(AccountSyncHealth::class.java.isSealed)
        assertTrue(AccountSyncSettingsCommand::class.java.isSealed)
        assertTrue(AccountSyncSettingsResult::class.java.isSealed)
        assertTrue(RecordEditingSettingsCommand::class.java.isSealed)
        assertTrue(RecordEditingSettingsResult::class.java.isSealed)
        assertTrue(ReminderCalendarSettingsCommand::class.java.isSealed)
        assertTrue(ReminderCalendarSettingsResult::class.java.isSealed)
        assertTrue(StorageOfflineSettingsCommand::class.java.isSealed)
        assertTrue(StorageOfflineSettingsResult::class.java.isSealed)
        assertTrue(AppearanceInteractionSettingsCommand::class.java.isSealed)
        assertTrue(AppearanceInteractionSettingsResult::class.java.isSealed)
        assertTrue(AboutAdvancedSettingsCommand::class.java.isSealed)
        assertTrue(AboutAdvancedSettingsResult::class.java.isSealed)
        assertTrue(SettingsPlatformAction::class.java.isSealed)
        assertTrue(SectionSummaryState::class.java.isSealed)

        assertTrue(isKotlinData(AccountSyncSettingsSnapshot::class.java))
        assertTrue(isKotlinData(RecordEditingSettingsSnapshot::class.java))
        assertTrue(isKotlinData(ReminderCalendarSettingsSnapshot::class.java))
        assertTrue(isKotlinData(StorageOfflineSettingsSnapshot::class.java))
        assertTrue(isKotlinData(AppearanceInteractionSettingsSnapshot::class.java))
        assertTrue(isKotlinData(AboutAdvancedSettingsSnapshot::class.java))
        assertTrue(isKotlinData(SettingsHubSnapshot::class.java))
    }

    @Test
    fun domainSettingsPackage_hasNoAndroidOrFrameworkImports() {
        val settingsDir =
            projectDir.resolve("core/domain/src/main/java/cc/pscly/onememos/domain/settings")
        assertTrue("settings package missing: $settingsDir", settingsDir.isDirectory)
        val forbidden =
            listOf(
                "import android.",
                "import androidx.",
                "import retrofit2.",
                "import okhttp3.",
                "import androidx.work.",
                "import androidx.compose.",
                "R.string.",
                "R.drawable.",
            )
        settingsDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val body = file.readText()
            forbidden.forEach { token ->
                assertFalse("${file.name} contains forbidden token $token", body.contains(token))
            }
            val chineseLiterals = Regex("\"[^\"]*[\\u4e00-\\u9fff][^\"]*\"").findAll(body).toList()
            assertTrue(
                "${file.name} should not embed user-facing Chinese copy: $chineseLiterals",
                chineseLiterals.isEmpty(),
            )
        }
    }

    @Test
    fun hubHasNoExecute_andAccountHasTenHealthStates() {
        assertFalse(publicMethodNames(SettingsHubCapability::class.java).contains("execute"))

        val healthSubtypes =
            AccountSyncHealth::class.java.permittedSubclasses
                .map { it.simpleName }
                .toSet()
        assertEquals(
            setOf(
                "Unbound",
                "ConfiguredSignedOut",
                "Healthy",
                "Syncing",
                "Queued",
                "Failed",
                "AuthenticationExpired",
                "FullResyncRunning",
                "FullResyncFailed",
                "FullResyncCompleted",
            ),
            healthSubtypes,
        )
    }

    private fun publicMethodNames(type: Class<*>): List<String> =
        type.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .filter { it.declaringClass == type || type.isInterface }
            .filter { it.name !in setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll", "getClass") }
            .map { it.name }
            .distinct()
            .sorted()

    private fun isKotlinData(type: Class<*>): Boolean {
        val names = type.declaredMethods.map(Method::getName).toSet()
        return names.any { it.startsWith("component") } && names.contains("copy")
    }
}
