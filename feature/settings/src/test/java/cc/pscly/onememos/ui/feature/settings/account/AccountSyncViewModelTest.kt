package cc.pscly.onememos.ui.feature.settings.account

import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCapability
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsResult
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.FullResyncProgress
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.navigation.AccountManagementSettingsKey
import cc.pscly.onememos.navigation.AdvancedSyncSettingsKey
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observe_preservesAllTenMutuallyExclusiveHealthVariants() =
        runBlocking {
            val snapshots = MutableSharedFlow<AccountSyncSettingsSnapshot>(replay = 1)
            val viewModel = AccountSyncViewModel(FakeAccountSyncCapability(snapshots))
            val collector = launch { viewModel.uiState.collect {} }
            try {
                val healthVariants =
                    listOf(
                        AccountSyncHealth.Unbound,
                        AccountSyncHealth.ConfiguredSignedOut,
                        AccountSyncHealth.Healthy(lastSuccessAtEpochMs = 100L),
                        AccountSyncHealth.Syncing,
                        AccountSyncHealth.Queued,
                        AccountSyncHealth.Failed(SettingsCapabilityError.NetworkUnavailable),
                        AccountSyncHealth.AuthenticationExpired,
                        AccountSyncHealth.FullResyncRunning(
                            FullResyncProgress(
                                stage = FullSyncStage.ARCHIVED,
                                pagesFetched = 3,
                                itemsFetched = 42,
                            ),
                        ),
                        AccountSyncHealth.FullResyncFailed(SettingsCapabilityError.StorageFailure),
                        AccountSyncHealth.FullResyncCompleted(
                            completionId = "run-200",
                            completedAtEpochMs = 200L,
                        ),
                    )

                healthVariants.forEachIndexed { index, health ->
                    val snapshot =
                        AccountSyncSettingsSnapshot(
                            health = health,
                            accountLabel = "account-$index",
                            lastSuccessAtEpochMs = index.toLong(),
                            commandInFlight = null,
                        )
                    snapshots.emit(snapshot)
                    await { viewModel.uiState.value.snapshot == snapshot }
                    assertEquals(health, viewModel.uiState.value.snapshot?.health)
                }
            } finally {
                collector.cancel()
            }
        }

    @Test
    fun userIntents_executeExactCapabilityCommands() =
        runBlocking {
            val fake = FakeAccountSyncCapability(MutableSharedFlow())
            val viewModel = AccountSyncViewModel(fake)

            viewModel.syncNow()
            viewModel.confirmLogout()
            viewModel.changePassword("current", "new-password", "new-password")
            viewModel.confirmFullResync()

            await { fake.commands.size == 4 }
            assertEquals(
                listOf(
                    AccountSyncSettingsCommand.SyncNow,
                    AccountSyncSettingsCommand.Logout,
                    AccountSyncSettingsCommand.ChangePassword(
                        currentPassword = "current",
                        newPassword = "new-password",
                        repeatedPassword = "new-password",
                    ),
                    AccountSyncSettingsCommand.FullResync,
                ),
                fake.commands,
            )
        }

    @Test
    fun openAndDangerousIntents_emitTypedNavigateAndConfirmEvents() =
        runBlocking {
            val viewModel = AccountSyncViewModel(FakeAccountSyncCapability(MutableSharedFlow()))

            assertEquals(
                SettingsUiEvent.Navigate(AuthKey()),
                nextEvent(viewModel) { viewModel.openLogin() },
            )
            assertEquals(
                SettingsUiEvent.Navigate(AccountManagementSettingsKey),
                nextEvent(viewModel) { viewModel.openAccountManagement() },
            )
            assertEquals(
                SettingsUiEvent.Navigate(AdvancedSyncSettingsKey),
                nextEvent(viewModel) { viewModel.openAdvancedSync() },
            )
            assertEquals(
                SettingsUiEvent.Confirm(SettingsConfirmation.LOGOUT),
                nextEvent(viewModel) { viewModel.requestLogout() },
            )
            assertEquals(
                SettingsUiEvent.Confirm(SettingsConfirmation.FULL_RESYNC),
                nextEvent(viewModel) { viewModel.requestFullResync() },
            )
        }

    @Test
    fun acknowledgeFullResyncCompletion_executesCapabilityWithCompletionId() =
        runBlocking {
            val fake = FakeAccountSyncCapability(MutableSharedFlow())
            val viewModel = AccountSyncViewModel(fake)

            viewModel.acknowledgeFullResyncCompletion("run-200")

            await { fake.commands.size == 1 }
            assertEquals(
                listOf(
                    AccountSyncSettingsCommand.AcknowledgeFullResyncCompletion(
                        completionId = "run-200",
                    ),
                ),
                fake.commands,
            )
        }

    @Test
    fun passwordFailure_keepsPartitionedError_andSyncSuccessDoesNotClearIt() =
        runBlocking {
            val fake =
                FakeAccountSyncCapability(
                    snapshots = MutableSharedFlow(),
                    resultProvider = { command ->
                        when (command) {
                            is AccountSyncSettingsCommand.ChangePassword ->
                                AccountSyncSettingsResult.Failure(
                                    SettingsCapabilityError.InvalidInput,
                                )
                            AccountSyncSettingsCommand.SyncNow ->
                                AccountSyncSettingsResult.Success
                            else -> AccountSyncSettingsResult.Success
                        }
                    },
                )
            val viewModel = AccountSyncViewModel(fake)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                viewModel.changePassword("current", "new-password", "new-password")
                await {
                    viewModel.uiState.value.passwordError == SettingsCapabilityError.InvalidInput
                }
                assertEquals(SettingsCapabilityError.InvalidInput, viewModel.uiState.value.passwordError)
                assertEquals(null, viewModel.uiState.value.syncError)

                viewModel.syncNow()
                await { fake.commands.size == 2 }
                delay(50L)
                assertEquals(SettingsCapabilityError.InvalidInput, viewModel.uiState.value.passwordError)
                assertEquals(null, viewModel.uiState.value.syncError)
            } finally {
                collector.cancel()
            }
        }

    @Test
    fun fullResyncBusyOrFailure_mapsOnlyToFullResyncErrorSlot() =
        runBlocking {
            val fake =
                FakeAccountSyncCapability(
                    snapshots = MutableSharedFlow(),
                    resultProvider = { command ->
                        when (command) {
                            is AccountSyncSettingsCommand.ChangePassword ->
                                AccountSyncSettingsResult.Failure(
                                    SettingsCapabilityError.NetworkUnavailable,
                                )
                            AccountSyncSettingsCommand.FullResync ->
                                AccountSyncSettingsResult.Failure(
                                    SettingsCapabilityError.AlreadyRunning,
                                )
                            else -> AccountSyncSettingsResult.Success
                        }
                    },
                )
            val viewModel = AccountSyncViewModel(fake)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                viewModel.changePassword("current", "new-password", "new-password")
                await {
                    viewModel.uiState.value.passwordError ==
                        SettingsCapabilityError.NetworkUnavailable
                }

                viewModel.confirmFullResync()
                await {
                    viewModel.uiState.value.fullResyncError ==
                        SettingsCapabilityError.AlreadyRunning
                }

                assertEquals(
                    SettingsCapabilityError.NetworkUnavailable,
                    viewModel.uiState.value.passwordError,
                )
                assertEquals(
                    SettingsCapabilityError.AlreadyRunning,
                    viewModel.uiState.value.fullResyncError,
                )
                assertEquals(null, viewModel.uiState.value.syncError)
                assertEquals(null, viewModel.uiState.value.logoutError)
            } finally {
                collector.cancel()
            }
        }

    @Test
    fun events_haveReplayZero_andConsumedEventIsNotReplayed() =
        runBlocking {
            val viewModel = AccountSyncViewModel(FakeAccountSyncCapability(MutableSharedFlow()))
            assertTrue(viewModel.events.replayCache.isEmpty())

            val collectorA = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
            viewModel.openAccountManagement()
            assertEquals(
                SettingsUiEvent.Navigate(AccountManagementSettingsKey),
                withTimeout(2_000L) { collectorA.await() },
            )

            val receivedByCollectorB = mutableListOf<SettingsUiEvent>()
            val collectorB = launch { viewModel.events.collect { receivedByCollectorB += it } }
            delay(50L)
            assertTrue(receivedByCollectorB.isEmpty())
            collectorB.cancel()
        }

    @Test
    fun viewModel_injectsOnlyCapability_andHasNoNavigatorOrPlatformOwner() {
        val constructors = AccountSyncViewModel::class.java.declaredConstructors
        assertEquals(1, constructors.size)
        assertEquals(
            listOf(AccountSyncSettingsCapability::class.java),
            constructors.single().parameterTypes.toList(),
        )

        val projectDir = System.getProperty("oneMemos.projectDir") ?: error("oneMemos.projectDir 未设置")
        val source =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/account/AccountSyncViewModel.kt",
            ).readText()
        assertFalse(source.contains("Navigator"))
        assertFalse(source.contains("android.app.Activity"))
        assertFalse(source.contains("AppUpdateManager"))
        assertFalse(source.contains("AccountManager"))
        assertFalse(source.contains("SettingsPlatformAction"))
        assertTrue(source.contains("MutableSharedFlow<SettingsUiEvent>(replay = 0"))
    }

    private suspend fun nextEvent(
        viewModel: AccountSyncViewModel,
        action: () -> Unit,
    ): SettingsUiEvent =
        coroutineScope {
            val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
            action()
            withTimeout(2_000L) { event.await() }
        }

    private suspend fun await(
        timeoutMs: Long = 2_000L,
        condition: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) {
                delay(10L)
            }
        }
    }

    private class FakeAccountSyncCapability(
        private val snapshots: Flow<AccountSyncSettingsSnapshot>,
        private val resultProvider: (AccountSyncSettingsCommand) -> AccountSyncSettingsResult = {
            AccountSyncSettingsResult.Success
        },
    ) : AccountSyncSettingsCapability {
        val commands = mutableListOf<AccountSyncSettingsCommand>()

        override fun observe(): Flow<AccountSyncSettingsSnapshot> = snapshots

        override suspend fun execute(command: AccountSyncSettingsCommand): AccountSyncSettingsResult {
            commands += command
            return resultProvider(command)
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
