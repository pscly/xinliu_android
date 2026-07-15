package cc.pscly.onememos.ui.feature.settings.about

import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCapability
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsCommand
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsResult
import cc.pscly.onememos.domain.settings.AboutAdvancedSettingsSnapshot
import cc.pscly.onememos.domain.settings.DeveloperOptions
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.domain.settings.SettingsPlatformAction
import cc.pscly.onememos.domain.settings.UpdateSettingsPhase
import cc.pscly.onememos.domain.settings.UpdateSettingsSnapshot
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.feature.settings.common.SettingsMessage
import cc.pscly.onememos.ui.feature.settings.common.SettingsUiEvent
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * AboutAdvancedViewModel 行为契约：
 * 只注入 AboutAdvancedSettingsCapability；更新/诊断/磁贴/捕获/重建/上传/开发者选项；
 * Platform 与 UpdateDelivery 事件 replay=0；安装绝不走 Platform。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutAdvancedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun observe_mapsVersionAndUpdatePhases_withoutEntrySideEffects() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                assertEquals(0, fake.checkCalls.get())
                assertEquals(0, fake.downloadCalls.get())
                assertEquals(0, fake.installCalls.get())
                assertEquals(0, fake.exportCalls.get())
                assertEquals(1, fake.observeCalls.get())

                fake.emit(
                    baseSnapshot(
                        versionName = "2.1.0",
                        versionCode = 210L,
                        update =
                            UpdateSettingsSnapshot(
                                phase = UpdateSettingsPhase.AVAILABLE,
                                availableVersion = "2.2.0",
                                ignoredVersionTag = "v2.0.0",
                            ),
                    ),
                )
                await { vm.uiState.value.snapshot?.versionName == "2.1.0" }
                val state = vm.uiState.value
                assertFalse(state.loading)
                assertEquals("2.1.0", state.snapshot!!.versionName)
                assertEquals(210L, state.snapshot!!.versionCode)
                assertEquals(UpdateSettingsPhase.AVAILABLE, state.snapshot!!.update.phase)
                assertEquals("2.2.0", state.snapshot!!.update.availableVersion)
                assertEquals("v2.0.0", state.snapshot!!.update.ignoredVersionTag)
                assertEquals(0, fake.checkCalls.get())
            } finally {
                job.cancel()
            }
        }

    @Test
    fun checkDownloadInstallClear_emitSuccessOrUpdateDelivery_neverPlatformForInstall() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            val events = CopyOnWriteArrayList<SettingsUiEvent>()
            val eventJob = launch { vm.events.collect { events.add(it) } }
            yield()
            try {
                fake.emit(baseSnapshot(update = UpdateSettingsSnapshot(UpdateSettingsPhase.IDLE)))
                await { vm.uiState.value.snapshot != null }

                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onCheckForUpdates()
                await {
                    fake.checkCalls.get() == 1 &&
                        events.any {
                            it is SettingsUiEvent.Toast &&
                                it.message == SettingsMessage.COMMAND_SUCCEEDED
                        }
                }
                assertTrue(events.any { it is SettingsUiEvent.Toast && it.message == SettingsMessage.COMMAND_SUCCEEDED })

                fake.nextResult = AboutAdvancedSettingsResult.Success
                events.clear()
                vm.onDownloadUpdate()
                await {
                    fake.downloadCalls.get() == 1 &&
                        events.any {
                            it is SettingsUiEvent.Toast &&
                                it.message == SettingsMessage.COMMAND_SUCCEEDED
                        }
                }

                val installAction =
                    UpdateDeliveryAction.InstallApk(uri = "content://apk/1.apk")
                fake.nextResult = AboutAdvancedSettingsResult.UpdateDelivery(installAction)
                events.clear()
                vm.onInstallUpdate()
                await {
                    fake.installCalls.get() == 1 &&
                        events.any { it is SettingsUiEvent.UpdateDelivery }
                }
                val delivery = events.filterIsInstance<SettingsUiEvent.UpdateDelivery>().single()
                assertEquals(installAction, delivery.action)
                assertTrue(events.none { it is SettingsUiEvent.Platform })

                val unknown =
                    UpdateDeliveryAction.OpenUnknownSourcesSettings("cc.pscly.onememos")
                fake.nextResult = AboutAdvancedSettingsResult.UpdateDelivery(unknown)
                events.clear()
                vm.onInstallUpdate()
                await {
                    fake.installCalls.get() == 2 &&
                        events.any { it is SettingsUiEvent.UpdateDelivery }
                }
                assertEquals(unknown, events.filterIsInstance<SettingsUiEvent.UpdateDelivery>().single().action)
                assertTrue(events.none { it is SettingsUiEvent.Platform })

                fake.nextResult = AboutAdvancedSettingsResult.Success
                events.clear()
                vm.onClearIgnoredUpdate()
                await {
                    fake.clearIgnoredCalls.get() == 1 &&
                        events.any {
                            it is SettingsUiEvent.Toast &&
                                it.message == SettingsMessage.COMMAND_SUCCEEDED
                        }
                }
            } finally {
                eventJob.cancel()
                job.cancel()
            }
        }

    @Test
    fun exportAndTilesAndCapture_mapPlatformOrToast_andSanitizeFailures() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            val events = CopyOnWriteArrayList<SettingsUiEvent>()
            val eventJob = launch { vm.events.collect { events.add(it) } }
            yield()
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }

                fake.nextResult =
                    AboutAdvancedSettingsResult.Platform(
                        SettingsPlatformAction.ShareFile(
                            uri = "content://diag/a.json",
                            mimeType = "application/json",
                        ),
                    )
                vm.onExportDiagnostics()
                await {
                    fake.exportCalls.get() == 1 &&
                        events.any { it is SettingsUiEvent.Platform }
                }
                val share = events.filterIsInstance<SettingsUiEvent.Platform>().single()
                assertTrue(share.action is SettingsPlatformAction.ShareFile)

                fake.nextResult = AboutAdvancedSettingsResult.Success
                events.clear()
                vm.onRequestQuickCaptureTile()
                await {
                    fake.quickTileCalls.get() == 1 &&
                        events.any { it is SettingsUiEvent.Toast }
                }
                assertTrue(events.any { it is SettingsUiEvent.Toast })

                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.PlatformUnavailable)
                events.clear()
                vm.onRequestScreenshotTile()
                await {
                    fake.screenshotTileCalls.get() == 1 &&
                        vm.uiState.value.persistentError ==
                        SettingsCapabilityError.PlatformUnavailable &&
                        events.any {
                            it is SettingsUiEvent.Toast &&
                                it.message == SettingsMessage.COMMAND_FAILED
                        }
                }
                assertEquals(
                    SettingsCapabilityError.PlatformUnavailable,
                    vm.uiState.value.persistentError,
                )
                assertTrue(events.any { it is SettingsUiEvent.Toast && it.message == SettingsMessage.COMMAND_FAILED })

                fake.nextResult =
                    AboutAdvancedSettingsResult.Platform(SettingsPlatformAction.OpenQuickCapture)
                events.clear()
                vm.onOpenQuickCapture()
                await {
                    fake.openQuickCalls.get() == 1 &&
                        events.any { it is SettingsUiEvent.Platform }
                }
                assertEquals(
                    SettingsPlatformAction.OpenQuickCapture,
                    events.filterIsInstance<SettingsUiEvent.Platform>().single().action,
                )
                assertTrue(events.none { it is SettingsUiEvent.Navigate })

                fake.nextResult =
                    AboutAdvancedSettingsResult.Platform(SettingsPlatformAction.OpenScreenshotCapture)
                events.clear()
                vm.onOpenScreenshotCapture()
                await {
                    fake.openScreenshotCalls.get() == 1 &&
                        events.any { it is SettingsUiEvent.Platform }
                }
                assertEquals(
                    SettingsPlatformAction.OpenScreenshotCapture,
                    events.filterIsInstance<SettingsUiEvent.Platform>().single().action,
                )

                // 领域错误不得泄漏 HTTP / Android 异常文本
                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(
                        SettingsCapabilityError.Unknown("HTTP_500_BODY_SECRET"),
                    )
                events.clear()
                vm.onExportDiagnostics()
                await {
                    fake.exportCalls.get() == 2 &&
                        vm.uiState.value.persistentError is SettingsCapabilityError.Unknown
                }
                assertEquals(
                    SettingsCapabilityError.Unknown("HTTP_500_BODY_SECRET"),
                    vm.uiState.value.persistentError,
                )
                val projectDir =
                    System.getProperty("oneMemos.projectDir")
                        ?: error("oneMemos.projectDir 未设置")
                val vmBody =
                    File(
                        projectDir,
                        "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedViewModel.kt",
                    ).readText()
                assertFalse(vmBody.contains("HttpException"))
                assertFalse(vmBody.contains("response.body"))
                assertFalse(vmBody.contains("Throwable.message"))
                assertFalse(vmBody.contains("e.message"))
            } finally {
                eventJob.cancel()
                job.cancel()
            }
        }

    @Test
    fun rebuildConfirm_uploadLimit_developerOptions_andDuplicateDisabled() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            val events = CopyOnWriteArrayList<SettingsUiEvent>()
            val eventJob = launch { vm.events.collect { events.add(it) } }
            yield()
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }

                events.clear()
                vm.onRequestRebuildDerivedFields()
                await { events.any { it is SettingsUiEvent.Confirm } }
                assertEquals(
                    SettingsConfirmation.REBUILD_DERIVED_FIELDS,
                    (events.single() as SettingsUiEvent.Confirm).request,
                )
                assertEquals(0, fake.rebuildCalls.get())

                fake.nextResult = AboutAdvancedSettingsResult.Success
                events.clear()
                vm.onConfirmRebuildDerivedFields()
                await { fake.rebuildCalls.get() == 1 }

                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onSetAttachmentUploadLimitMb(128)
                await { fake.uploadLimitCalls.get() == 1 }
                assertEquals(128, fake.lastUploadLimit.get())

                val options =
                    DeveloperOptions(
                        unlocked = true,
                        showPublicWorkspaceMemos = true,
                        autoTagLineKeywords = "kw",
                        showAutoTagLineInHome = true,
                        showAutoTagLineInView = false,
                        showAutoTagLineInEdit = true,
                        homeRichPreviewStickyLimit = 40,
                    )
                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onSetDeveloperOptions(options)
                await { fake.developerCalls.get() == 1 }
                assertEquals(options, fake.lastDeveloperOptions.get())

                // 进行中禁用重复提交：commandInFlight 时不再调用 execute
                fake.emit(
                    baseSnapshot(
                        commandInFlight = AboutAdvancedSettingsCommand.ExportDiagnostics,
                    ),
                )
                await {
                    vm.uiState.value.snapshot?.commandInFlight ==
                        AboutAdvancedSettingsCommand.ExportDiagnostics
                }
                val before = fake.exportCalls.get()
                vm.onExportDiagnostics()
                delay(50)
                assertEquals(before, fake.exportCalls.get())
                assertTrue(vm.uiState.value.actionsDisabled)

                // IgnoredDuplicate 不覆盖持久错误、不额外失败 toast
                fake.emit(baseSnapshot(commandInFlight = null))
                await { vm.uiState.value.snapshot?.commandInFlight == null }
                fake.nextResult = AboutAdvancedSettingsResult.IgnoredDuplicate
                events.clear()
                vm.onCheckForUpdates()
                await { fake.checkCalls.get() >= 1 }
                assertTrue(events.none { it is SettingsUiEvent.Toast && it.message == SettingsMessage.COMMAND_FAILED })
            } finally {
                eventJob.cancel()
                job.cancel()
            }
        }

    @Test
    fun events_platformAndUpdateDelivery_areOneShotNoReplay() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }

                val first = CopyOnWriteArrayList<SettingsUiEvent>()
                val collectorA = launch { vm.events.collect { first.add(it) } }
                yield()
                fake.nextResult =
                    AboutAdvancedSettingsResult.Platform(SettingsPlatformAction.OpenQuickCapture)
                vm.onOpenQuickCapture()
                await { first.isNotEmpty() }
                collectorA.cancel()

                val second = CopyOnWriteArrayList<SettingsUiEvent>()
                val collectorB = launch { vm.events.collect { second.add(it) } }
                yield()
                delay(80)
                assertTrue("已消费 Platform 事件不得重放", second.isEmpty())
                collectorB.cancel()

                val third = CopyOnWriteArrayList<SettingsUiEvent>()
                val collectorC = launch { vm.events.collect { third.add(it) } }
                yield()
                fake.nextResult =
                    AboutAdvancedSettingsResult.UpdateDelivery(
                        UpdateDeliveryAction.InstallApk("content://apk"),
                    )
                vm.onInstallUpdate()
                await { third.isNotEmpty() }
                collectorC.cancel()
                val fourth = CopyOnWriteArrayList<SettingsUiEvent>()
                val collectorD = launch { vm.events.collect { fourth.add(it) } }
                yield()
                delay(80)
                assertTrue("已消费 UpdateDelivery 事件不得重放", fourth.isEmpty())
                collectorD.cancel()
            } finally {
                job.cancel()
            }
        }

    @Test
    fun sectionFailure_staysBesideOrigin_andUnrelatedSuccessDoesNotClear() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }

                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
                vm.onExportDiagnostics()
                await {
                    vm.uiState.value.errorSection == AboutErrorSection.DIAGNOSTICS &&
                        vm.uiState.value.sectionError == SettingsCapabilityError.StorageFailure
                }
                assertEquals(AboutErrorSection.DIAGNOSTICS, vm.uiState.value.errorSection)
                // 不得误挂到更新分区
                assertTrue(vm.uiState.value.errorSection != AboutErrorSection.UPDATE)

                // 无关分区成功不得清除诊断错误
                fake.nextResult = AboutAdvancedSettingsResult.Success
                val tokenBefore = vm.uiState.value.announcementToken
                vm.onCheckForUpdates()
                await { vm.uiState.value.announcementToken > tokenBefore }
                assertEquals(AboutErrorSection.DIAGNOSTICS, vm.uiState.value.errorSection)
                assertEquals(SettingsCapabilityError.StorageFailure, vm.uiState.value.sectionError)

                // 本区成功可清除
                fake.nextResult =
                    AboutAdvancedSettingsResult.Platform(
                        SettingsPlatformAction.ShareFile("content://d", "application/json"),
                    )
                val token2 = vm.uiState.value.announcementToken
                vm.onExportDiagnostics()
                await {
                    vm.uiState.value.announcementToken > token2 &&
                        vm.uiState.value.sectionError == null
                }
                assertNull(vm.uiState.value.errorSection)

                // 上传失败挂到 UPLOAD
                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.InvalidInput)
                vm.onSetAttachmentUploadLimitMb(-1)
                await { vm.uiState.value.errorSection == AboutErrorSection.UPLOAD }
                assertEquals(SettingsCapabilityError.InvalidInput, vm.uiState.value.sectionError)

                // 开发者失败挂到 DEVELOPER
                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.StorageFailure)
                vm.onSetDeveloperOptions(
                    DeveloperOptions(
                        unlocked = true,
                        showPublicWorkspaceMemos = false,
                        autoTagLineKeywords = "kw",
                        showAutoTagLineInHome = false,
                        showAutoTagLineInView = false,
                        showAutoTagLineInEdit = false,
                        homeRichPreviewStickyLimit = 10,
                    ),
                )
                await { vm.uiState.value.errorSection == AboutErrorSection.DEVELOPER }

                // 重建失败挂到 REBUILD
                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.AlreadyRunning)
                vm.onConfirmRebuildDerivedFields()
                await { vm.uiState.value.errorSection == AboutErrorSection.REBUILD }
            } finally {
                job.cancel()
            }
        }

    @Test
    fun consecutiveIdenticalResults_bumpAnnouncementToken() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }
                assertEquals(0L, vm.uiState.value.announcementToken)

                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onCheckForUpdates()
                await { vm.uiState.value.announcementToken == 1L }
                assertEquals(AboutAnnouncementKind.SUCCESS, vm.uiState.value.announcementKind)

                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onCheckForUpdates()
                await { vm.uiState.value.announcementToken == 2L }
                assertEquals(AboutAnnouncementKind.SUCCESS, vm.uiState.value.announcementKind)

                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.NetworkUnavailable)
                vm.onCheckForUpdates()
                await { vm.uiState.value.announcementToken == 3L }
                assertEquals(AboutAnnouncementKind.FAILED, vm.uiState.value.announcementKind)

                fake.nextResult =
                    AboutAdvancedSettingsResult.Failure(SettingsCapabilityError.NetworkUnavailable)
                vm.onCheckForUpdates()
                await { vm.uiState.value.announcementToken == 4L }
                assertEquals(AboutAnnouncementKind.FAILED, vm.uiState.value.announcementKind)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun developerUnlock_viaSetDeveloperOptions_sendsFullOptionsIncludingKeywordsAndSticky() =
        runBlocking {
            val fake = FakeAboutCapability()
            val vm = AboutAdvancedViewModel(fake)
            val job = launch { vm.uiState.collect {} }
            try {
                fake.emit(baseSnapshot())
                await { vm.uiState.value.snapshot != null }
                val unlocked =
                    DeveloperOptions(
                        unlocked = true,
                        showPublicWorkspaceMemos = true,
                        autoTagLineKeywords = "__Atags",
                        showAutoTagLineInHome = true,
                        showAutoTagLineInView = false,
                        showAutoTagLineInEdit = true,
                        homeRichPreviewStickyLimit = 500,
                    )
                fake.nextResult = AboutAdvancedSettingsResult.Success
                vm.onSetDeveloperOptions(unlocked)
                await { fake.developerCalls.get() == 1 }
                assertEquals(unlocked, fake.lastDeveloperOptions.get())
                assertEquals("__Atags", fake.lastDeveloperOptions.get()!!.autoTagLineKeywords)
                assertEquals(500, fake.lastDeveloperOptions.get()!!.homeRichPreviewStickyLimit)
                assertTrue(fake.lastDeveloperOptions.get()!!.unlocked)
            } finally {
                job.cancel()
            }
        }

    @Test
    fun viewModel_injectsOnlyAboutCapability_andStaticSeparation() {
        val projectDir =
            System.getProperty("oneMemos.projectDir")
                ?: error("oneMemos.projectDir 未设置")
        val path =
            File(
                projectDir,
                "feature/settings/src/main/java/cc/pscly/onememos/ui/feature/settings/about/AboutAdvancedViewModel.kt",
            )
        assertTrue(path.exists())
        val body = path.readText()
        assertTrue(body.contains("AboutAdvancedSettingsCapability"))
        assertTrue(body.contains("@HiltViewModel"))
        assertFalse(body.contains("AppUpdateManager"))
        assertFalse(body.contains("Activity"))
        assertFalse(body.contains("StatusBarManager"))
        assertFalse(body.contains("startActivity"))
        // 安装/未知来源只允许经 UpdateDelivery，不得伪造 Platform
        assertFalse(body.contains("OpenUnknownSourcesSettings") && body.contains("SettingsPlatformAction"))
        assertTrue(body.contains("SettingsUiEvent.UpdateDelivery") || body.contains("UpdateDelivery("))
        assertTrue(body.contains("MutableSharedFlow"))
        assertTrue(body.contains("replay = 0") || body.contains("replay=0"))
        // 固定契约：extraBufferCapacity 必须为 1
        assertTrue(
            body.contains("extraBufferCapacity = 1") ||
                body.contains("extraBufferCapacity=1"),
        )
        assertFalse(body.contains("extraBufferCapacity = 16"))
        assertFalse(body.contains("DROP_OLDEST"))
        assertTrue(body.contains("AboutErrorSection"))
        assertTrue(body.contains("announcementToken"))
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

    private fun baseSnapshot(
        versionName: String = "1.0.0",
        versionCode: Long = 1L,
        update: UpdateSettingsSnapshot = UpdateSettingsSnapshot(UpdateSettingsPhase.IDLE),
        commandInFlight: AboutAdvancedSettingsCommand? = null,
    ): AboutAdvancedSettingsSnapshot =
        AboutAdvancedSettingsSnapshot(
            versionName = versionName,
            versionCode = versionCode,
            buildType = "debug",
            update = update,
            diagnosticsAvailable = false,
            attachmentUploadLimitMb = 50,
            developerOptions =
                DeveloperOptions(
                    unlocked = false,
                    showPublicWorkspaceMemos = false,
                    autoTagLineKeywords = "",
                    showAutoTagLineInHome = false,
                    showAutoTagLineInView = false,
                    showAutoTagLineInEdit = false,
                    homeRichPreviewStickyLimit = 0,
                ),
            commandInFlight = commandInFlight,
        )

    private class FakeAboutCapability : AboutAdvancedSettingsCapability {
        private val snapshots = MutableSharedFlow<AboutAdvancedSettingsSnapshot>(replay = 1)
        val observeCalls = AtomicInteger(0)
        val checkCalls = AtomicInteger(0)
        val downloadCalls = AtomicInteger(0)
        val installCalls = AtomicInteger(0)
        val clearIgnoredCalls = AtomicInteger(0)
        val exportCalls = AtomicInteger(0)
        val quickTileCalls = AtomicInteger(0)
        val screenshotTileCalls = AtomicInteger(0)
        val openQuickCalls = AtomicInteger(0)
        val openScreenshotCalls = AtomicInteger(0)
        val rebuildCalls = AtomicInteger(0)
        val uploadLimitCalls = AtomicInteger(0)
        val developerCalls = AtomicInteger(0)
        val lastUploadLimit = AtomicInteger(-1)
        val lastDeveloperOptions = AtomicReference<DeveloperOptions?>(null)
        val executed = CopyOnWriteArrayList<AboutAdvancedSettingsCommand>()
        var nextResult: AboutAdvancedSettingsResult = AboutAdvancedSettingsResult.Success

        suspend fun emit(snapshot: AboutAdvancedSettingsSnapshot) {
            snapshots.emit(snapshot)
        }

        override fun observe(): Flow<AboutAdvancedSettingsSnapshot> {
            observeCalls.incrementAndGet()
            return snapshots
        }

        override suspend fun execute(command: AboutAdvancedSettingsCommand): AboutAdvancedSettingsResult {
            executed.add(command)
            when (command) {
                AboutAdvancedSettingsCommand.CheckForUpdates -> checkCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.DownloadUpdate -> downloadCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.InstallUpdate -> installCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.ClearIgnoredUpdate -> clearIgnoredCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.ExportDiagnostics -> exportCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.RequestQuickCaptureTile -> quickTileCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.RequestScreenshotTile -> screenshotTileCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.OpenQuickCapture -> openQuickCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.OpenScreenshotCapture -> openScreenshotCalls.incrementAndGet()
                AboutAdvancedSettingsCommand.RebuildDerivedFields -> rebuildCalls.incrementAndGet()
                is AboutAdvancedSettingsCommand.SetAttachmentUploadLimitMb -> {
                    uploadLimitCalls.incrementAndGet()
                    lastUploadLimit.set(command.value)
                }
                is AboutAdvancedSettingsCommand.SetDeveloperOptions -> {
                    developerCalls.incrementAndGet()
                    lastDeveloperOptions.set(command.options)
                }
            }
            return nextResult
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
