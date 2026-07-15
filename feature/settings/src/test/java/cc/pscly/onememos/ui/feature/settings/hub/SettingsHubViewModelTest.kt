package cc.pscly.onememos.ui.feature.settings.hub

import cc.pscly.onememos.domain.settings.SectionSummaryState
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsHubCapability
import cc.pscly.onememos.domain.settings.SettingsHubSnapshot
import cc.pscly.onememos.domain.settings.SummaryFact
import cc.pscly.onememos.domain.settings.SummaryIssue
import cc.pscly.onememos.domain.settings.SummaryIssueKind
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHubViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observe_mapsSixSectionsAsIs_andSingleErrorDoesNotBlockOthers() =
        runBlocking {
            val flow = MutableSharedFlow<SettingsHubSnapshot>(replay = 1)
            val fake = FakeHubCapability(flow)
            val vm = SettingsHubViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                val snap =
                    SettingsHubSnapshot(
                        accountSync =
                            SectionSummaryState.Ready(
                                primary = SummaryFact("ACCOUNT_HEALTHY"),
                                issue = SummaryIssue(SummaryIssueKind.AUTHENTICATION_EXPIRED),
                            ),
                        recordEditing = SectionSummaryState.Loading,
                        reminderCalendar =
                            SectionSummaryState.Error(
                                SettingsCapabilityError.Unknown("REMINDER"),
                            ),
                        storageOffline =
                            SectionSummaryState.Ready(
                                primary = SummaryFact("PREFETCH_OFF"),
                            ),
                        appearanceInteraction =
                            SectionSummaryState.Ready(
                                primary = SummaryFact("THEME_PAPER_INK"),
                                secondary = SummaryFact("MODE_FOLLOW_SYSTEM"),
                            ),
                        aboutAdvanced =
                            SectionSummaryState.Ready(
                                primary = SummaryFact("VERSION_1.0.0"),
                            ),
                    )
                flow.emit(snap)
                await { vm.uiState.value.snapshot != null }
                val state = vm.uiState.value.snapshot!!
                assertEquals(snap.accountSync, state.accountSync)
                assertEquals(snap.recordEditing, state.recordEditing)
                assertEquals(snap.reminderCalendar, state.reminderCalendar)
                assertEquals(snap.storageOffline, state.storageOffline)
                assertEquals(snap.appearanceInteraction, state.appearanceInteraction)
                assertEquals(snap.aboutAdvanced, state.aboutAdvanced)
                assertTrue(state.reminderCalendar is SectionSummaryState.Error)
                assertTrue(state.storageOffline is SectionSummaryState.Ready)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun constructAndSubscribe_doNotTriggerSideEffectCounters() =
        runBlocking {
            val flow = MutableSharedFlow<SettingsHubSnapshot>(replay = 1)
            val fake = FakeHubCapability(flow)
            val vm = SettingsHubViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                flow.emit(
                    SettingsHubSnapshot(
                        accountSync = SectionSummaryState.Loading,
                        recordEditing = SectionSummaryState.Loading,
                        reminderCalendar = SectionSummaryState.Loading,
                        storageOffline = SectionSummaryState.Loading,
                        appearanceInteraction = SectionSummaryState.Loading,
                        aboutAdvanced = SectionSummaryState.Loading,
                    ),
                )
                await { vm.uiState.value.snapshot != null }
                assertEquals(0, fake.networkCalls.get())
                assertEquals(0, fake.syncCalls.get())
                assertEquals(0, fake.scanCalls.get())
                assertEquals(0, fake.permissionCalls.get())
                assertEquals(0, fake.updateCalls.get())
                assertEquals(0, fake.downloadCalls.get())
                assertEquals(0, fake.installCalls.get())
                assertEquals(0, fake.diagnosticsCalls.get())
                assertEquals(1, fake.observeCalls.get())
            } finally {
                job.cancel()
            }
        }

    @Test
    fun hubViewModel_hasNoWriteCommandsRefreshOrPlatformEvents() {
        val projectDir =
            System.getProperty("oneMemos.projectDir")
                ?: error("oneMemos.projectDir 未设置")
        val path =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/hub/SettingsHubViewModel.kt",
            )
        assertTrue(path.exists())
        val body = path.readText()
        assertFalse(body.contains("fun execute"))
        assertFalse(body.contains("fun refresh"))
        assertFalse(body.contains("SettingsUiEvent"))
        assertFalse(body.contains("SettingsPlatformAction"))
        assertFalse(body.contains("MutableSharedFlow"))
        assertTrue(body.contains("SettingsHubCapability"))
        assertTrue(body.contains("observe()"))
        // 初始状态 snapshot 为 null
        val flow = MutableSharedFlow<SettingsHubSnapshot>(replay = 0)
        val vm = SettingsHubViewModel(FakeHubCapability(flow))
        assertNull(vm.uiState.value.snapshot)
    }

    private suspend fun await(
        timeoutMs: Long = 2_000L,
        condition: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10)
            }
        }
    }

    private class FakeHubCapability(
        private val snapshots: Flow<SettingsHubSnapshot>,
    ) : SettingsHubCapability {
        val observeCalls = AtomicInteger(0)
        val networkCalls = AtomicInteger(0)
        val syncCalls = AtomicInteger(0)
        val scanCalls = AtomicInteger(0)
        val permissionCalls = AtomicInteger(0)
        val updateCalls = AtomicInteger(0)
        val downloadCalls = AtomicInteger(0)
        val installCalls = AtomicInteger(0)
        val diagnosticsCalls = AtomicInteger(0)

        override fun observe(): Flow<SettingsHubSnapshot> {
            observeCalls.incrementAndGet()
            return snapshots
        }
    }

    class MainDispatcherRule : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
