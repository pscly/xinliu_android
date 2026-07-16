package cc.pscly.onememos.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.R
import cc.pscly.onememos.navigation.AppNavigationHost
import cc.pscly.onememos.navigation.ExternalNavigationInput
import cc.pscly.onememos.navigation.TopLevelSection
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsPlatformActionDispatcher
import cc.pscly.onememos.ui.feature.settings.common.LocalSettingsUpdateDeliveryDispatcher
import cc.pscly.onememos.ui.feature.start.AppStartViewModel
import cc.pscly.onememos.ui.settings.AppSettingsPlatformActionDispatcher
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.update.AppUpdateUiState
import kotlinx.coroutines.launch

@Composable
fun OneMemosApp(
    appViewModel: AppViewModel = hiltViewModel(),
    pendingExternalInput: ExternalNavigationInput? = null,
    onExternalInputConsumed: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val updateUiState by appViewModel.updateUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activityProvider = rememberUpdatedState(context as? Activity)
    val lifecycleOwner = LocalLifecycleOwner.current

    val platformDispatcher =
        remember(context.applicationContext) {
            AppSettingsPlatformActionDispatcher(
                context = context.applicationContext,
                activityProvider = { activityProvider.value },
            )
        }
    val updateDispatcher =
        remember(appViewModel) {
            appViewModel.settingsUpdateDeliveryDispatcher { activityProvider.value }
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { grantMap ->
            platformDispatcher.onPermissionResult(grantMap)
        }
    LaunchedEffect(platformDispatcher, permissionLauncher) {
        platformDispatcher.bindPermissionLauncher { permissions ->
            permissionLauncher.launch(permissions)
        }
    }
    DisposableEffect(lifecycleOwner, platformDispatcher) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    platformDispatcher.onHostResumed()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val startViewModel: AppStartViewModel = hiltViewModel()
    val startUiState by startViewModel.uiState.collectAsStateWithLifecycle()

    val shellViewModel: AppShellViewModel = hiltViewModel()
    val shellUiState by shellViewModel.uiState.collectAsStateWithLifecycle()

    var showCollectionsDisabledDialog by rememberSaveable { mutableStateOf(false) }
    var activeSection by rememberSaveable { mutableStateOf(TopLevelSection.HOME.name) }
    var requestedSection by rememberSaveable { mutableStateOf<String?>(null) }

    val drawerOpenOrOpening =
        drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    BackHandler(enabled = drawerOpenOrOpening) {
        scope.launch { drawerState.close() }
    }

    val toggleDrawer: () -> Unit = {
        scope.launch {
            val openOrOpening =
                drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
            if (openOrOpening) drawerState.close() else drawerState.open()
        }
    }

    val current =
        runCatching { TopLevelSection.valueOf(activeSection) }.getOrDefault(TopLevelSection.HOME)

    fun requestSection(section: TopLevelSection) {
        scope.launch { drawerState.close() }
        if (current == section) return
        requestedSection = section.name
    }

    CompositionLocalProvider(
        LocalSettingsPlatformActionDispatcher provides platformDispatcher,
        LocalSettingsUpdateDeliveryDispatcher provides updateDispatcher,
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { drawerState.close() } }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭菜单")
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        NavigationDrawerItem(
                            label = { Text("随笔") },
                            selected = current == TopLevelSection.HOME,
                            onClick = { requestSection(TopLevelSection.HOME) },
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    NavigationDrawerItem(
                        label = { Text("锦囊") },
                        selected = current == TopLevelSection.COLLECTIONS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (shellUiState.showCollections) {
                                if (current != TopLevelSection.COLLECTIONS) {
                                    requestedSection = TopLevelSection.COLLECTIONS.name
                                }
                            } else {
                                showCollectionsDisabledDialog = true
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                    if (shellUiState.showTodo) {
                        NavigationDrawerItem(
                            label = { Text("待办") },
                            selected = current == TopLevelSection.TODO,
                            onClick = { requestSection(TopLevelSection.TODO) },
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("个人中心") },
                        selected = current == TopLevelSection.PROFILE,
                        onClick = { requestSection(TopLevelSection.PROFILE) },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                    NavigationDrawerItem(
                        label = { Text("已归档") },
                        selected = current == TopLevelSection.ARCHIVED,
                        onClick = { requestSection(TopLevelSection.ARCHIVED) },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                        NavigationDrawerItem(
                            label = { Text("设置") },
                            selected = current == TopLevelSection.SETTINGS,
                            onClick = { requestSection(TopLevelSection.SETTINGS) },
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    }
                }
            },
        ) {
            AppNavigationHost(
                pendingExternalInput = pendingExternalInput,
                onExternalInputConsumed = onExternalInputConsumed,
                showWelcome = startUiState.showWelcome,
                onOpenDrawer = toggleDrawer,
                requestedSection =
                    requestedSection?.let { name ->
                        runCatching { TopLevelSection.valueOf(name) }.getOrNull()
                    },
                onRequestedSectionHandled = { requestedSection = null },
                onActiveSectionChanged = { activeSection = it.name },
            )

            if (showCollectionsDisabledDialog) {
                AlertDialog(
                    onDismissRequest = { showCollectionsDisabledDialog = false },
                    title = { Text("锦囊不可用") },
                    text = { Text("锦囊目前仅支持 Flow Backend 登录模式（自定义服务器模式暂不支持）。") },
                    confirmButton = {
                        TextButton(onClick = { showCollectionsDisabledDialog = false }) {
                            Text("知道了")
                        }
                    },
                )
            }

            val updateDialogContext = LocalContext.current
            AppUpdateDialog(
                state = updateUiState,
                onDownload = appViewModel::startUpdateDownload,
                onInstall = {
                    appViewModel.installDownloadedUpdate(updateDialogContext as? Activity)
                },
                onLater = appViewModel::remindUpdateLater,
                onIgnore = appViewModel::ignoreCurrentUpdate,
                onDismiss = appViewModel::dismissUpdatePrompt,
            )
        }
    }
}

@Composable
private fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.promptVisible) return
    val release = state.release ?: return
    when (state.phase) {
        AppUpdatePhase.AVAILABLE ->
            AlertDialog(
                onDismissRequest = onLater,
                title = { Text("发现新版本 ${release.versionName}") },
                text = {
                    Column(
                        modifier =
                            Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                    ) {
                        Text(release.notes.ifBlank { "本次为完整稳定版更新。" })
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "安装包约 ${"%.1f".format(release.apkSizeBytes / 1024.0 / 1024.0)} MB，下载后会校验哈希、包名、版本和签名。",
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDownload) { Text("立即更新") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = onIgnore) { Text("忽略此版本") }
                        TextButton(onClick = onLater) { Text("稍后") }
                    }
                },
            )
        AppUpdatePhase.DOWNLOADING ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("正在下载 ${release.versionName}") },
                text = {
                    Column {
                        Text(state.statusMessage)
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { (state.downloadProgressPercent ?: 0).coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("后台下载") }
                },
            )
        AppUpdatePhase.READY_TO_INSTALL ->
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("更新已准备好") },
                text = { Text("${release.versionName} 已下载并通过安全校验，可以交给系统安装。") },
                confirmButton = {
                    TextButton(onClick = onInstall) { Text("安装更新") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                },
            )
        else -> Unit
    }
}
