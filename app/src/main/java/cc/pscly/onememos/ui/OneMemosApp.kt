package cc.pscly.onememos.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.BuildConfig
import cc.pscly.onememos.R
import cc.pscly.onememos.overlay.QuickCaptureOverlayService
import cc.pscly.onememos.qs.QuickCaptureTileService
import cc.pscly.onememos.qs.QuickScreenshotTileService
import cc.pscly.onememos.screenshot.ScreenshotQuickCaptureActivity
import cc.pscly.onememos.ui.feature.editor.EditorScreen
import cc.pscly.onememos.ui.feature.auth.AuthScreen
import cc.pscly.onememos.ui.feature.collections.CollectionsScreen
import cc.pscly.onememos.ui.feature.home.HomeScreen
import cc.pscly.onememos.ui.feature.home.HomeScreenMode
import cc.pscly.onememos.ui.feature.profile.ProfileScreen
import cc.pscly.onememos.ui.feature.quickcapture.QuickCaptureActivity
import cc.pscly.onememos.ui.feature.sharecard.ShareCardScreen
import cc.pscly.onememos.ui.feature.settings.SettingsAppInfo
import cc.pscly.onememos.ui.feature.settings.SettingsUpdateInfo
import cc.pscly.onememos.ui.feature.settings.SettingsScreen
import cc.pscly.onememos.ui.feature.start.AppStartViewModel
import cc.pscly.onememos.ui.feature.todo.TodoScreen
import cc.pscly.onememos.ui.feature.welcome.WelcomeScreen
import cc.pscly.onememos.update.AppUpdatePhase
import cc.pscly.onememos.update.AppUpdateUiState

@Composable
fun OneMemosApp(
    appViewModel: AppViewModel = hiltViewModel(),
    startEditorUuid: String? = null,
    onStartEditorHandled: (() -> Unit)? = null,
    startRoute: String? = null,
    onStartRouteHandled: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var startHandled by rememberSaveable(startEditorUuid) { mutableStateOf(false) }
    var startRouteHandled by rememberSaveable(startRoute) { mutableStateOf(false) }
    var welcomeHandled by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val updateUiState by appViewModel.updateUiState.collectAsStateWithLifecycle()

    val startViewModel: AppStartViewModel = hiltViewModel()
    val startUiState by startViewModel.uiState.collectAsStateWithLifecycle()

    val shellViewModel: AppShellViewModel = hiltViewModel()
    val shellUiState by shellViewModel.uiState.collectAsStateWithLifecycle()

    var showCollectionsDisabledDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(startEditorUuid) {
        if (!startHandled && !startEditorUuid.isNullOrBlank()) {
            navController.navigate(Routes.editor(startEditorUuid))
            startHandled = true
            onStartEditorHandled?.invoke()
        }
    }

    LaunchedEffect(startRoute, startEditorUuid) {
        // 有“外部启动意图”（例如通知）时：如果不需要进编辑器，就按 startRoute 导航。
        if (!startRouteHandled && startEditorUuid.isNullOrBlank() && !startRoute.isNullOrBlank()) {
            navController.navigate(startRoute) {
                launchSingleTop = true
            }
            startRouteHandled = true
            onStartRouteHandled?.invoke()
        }
    }

    LaunchedEffect(startUiState.showWelcome, startEditorUuid, startRoute) {
        // 有“外部启动意图”（分享/快捷记录/通知）时优先处理启动意图，不打断用户。
        if (startEditorUuid.isNullOrBlank() && startRoute.isNullOrBlank() && startUiState.showWelcome && !welcomeHandled) {
            navController.navigate(Routes.WELCOME) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            welcomeHandled = true
        }
    }

    // 抽屉打开/动画中，系统返回键优先关闭抽屉，避免误触导致退出到桌面或出现空栈观感。
    val drawerOpenOrOpening = drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
    BackHandler(enabled = drawerOpenOrOpening) {
        scope.launch { drawerState.close() }
    }

    val toggleDrawer: () -> Unit = {
        scope.launch {
            val openOrOpening = drawerState.currentValue == DrawerValue.Open || drawerState.targetValue == DrawerValue.Open
            if (openOrOpening) drawerState.close() else drawerState.open()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 仅允许点击左上角标题打开抽屉，禁用右滑手势（避免“误触滑出”与观感问题）。
        gesturesEnabled = false,
        drawerContent = {
            // 使用 DrawerSheet，避免抽屉内容在某些机型/分辨率下“露出/穿透”到主界面。
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
                        IconButton(
                            onClick = { scope.launch { drawerState.close() } },
                        ) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭菜单")
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val current = navBackStackEntry?.destination?.route
                    NavigationDrawerItem(
                        label = { Text("随笔") },
                        selected = current == Routes.HOME,
                        onClick = {
                            scope.launch { drawerState.close() }
                            // 已在随笔页时只需要关抽屉，避免 nav 误操作导致空栈。
                            if (current != Routes.HOME) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )

                    NavigationDrawerItem(
                        label = { Text("锦囊") },
                        selected = current == Routes.COLLECTIONS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (shellUiState.showCollections) {
                                if (current != Routes.COLLECTIONS) {
                                    navController.navigate(Routes.COLLECTIONS) {
                                        launchSingleTop = true
                                    }
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
                            selected = current == Routes.TODO,
                            onClick = {
                                scope.launch { drawerState.close() }
                                if (current != Routes.TODO) {
                                    navController.navigate(Routes.TODO) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(),
                        )
                    }
                    NavigationDrawerItem(
                        label = { Text("个人中心") },
                        selected = current == Routes.PROFILE,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (current != Routes.PROFILE) {
                                navController.navigate(Routes.PROFILE) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                    NavigationDrawerItem(
                        label = { Text("已归档") },
                        selected = current == Routes.ARCHIVED,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (current != Routes.ARCHIVED) {
                                navController.navigate(Routes.ARCHIVED) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                    NavigationDrawerItem(
                        label = { Text("设置") },
                        selected = current == Routes.SETTINGS,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (current != Routes.SETTINGS) {
                                navController.navigate(Routes.SETTINGS) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        colors = NavigationDrawerItemDefaults.colors(),
                    )
                }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onEnterLocal = {
                        navController.popBackStack()
                    },
                    onGoBindServer = {
                        navController.navigate(Routes.auth()) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    title = "随笔",
                    mode = HomeScreenMode.ACTIVE,
                    onOpenDrawer = toggleDrawer,
                    onOpenAuth = {
                        navController.navigate(Routes.auth()) {
                            launchSingleTop = true
                        }
                    },
                    onCreateMemo = { navController.navigate(Routes.editor()) },
                    onOpenMemo = { uuid -> navController.navigate(Routes.editor(uuid)) },
                    onOpenShareCard = { uuid -> navController.navigate(Routes.shareCard(uuid)) },
                )
            }

            composable(Routes.COLLECTIONS) {
                CollectionsScreen(
                    onOpenDrawer = toggleDrawer,
                    onOpenMemo = { uuid -> navController.navigate(Routes.editor(uuid)) },
                )
            }

            composable(Routes.TODO) {
                TodoScreen(
                    onOpenDrawer = toggleDrawer,
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenDrawer = toggleDrawer,
                    onOpenMemo = { uuid -> navController.navigate(Routes.editor(uuid)) },
                )
            }

            composable(Routes.ARCHIVED) {
                HomeScreen(
                    title = "已归档",
                    mode = HomeScreenMode.ARCHIVED,
                    onOpenDrawer = toggleDrawer,
                    onOpenAuth = {
                        navController.navigate(Routes.auth()) {
                            launchSingleTop = true
                        }
                    },
                    onCreateMemo = { navController.navigate(Routes.editor()) },
                    onOpenMemo = { uuid -> navController.navigate(Routes.editor(uuid)) },
                    onOpenShareCard = { uuid -> navController.navigate(Routes.shareCard(uuid)) },
                )
            }

            composable(
                route = "${Routes.EDITOR}?${Routes.ARG_UUID}={${Routes.ARG_UUID}}",
                arguments = listOf(
                    navArgument(Routes.ARG_UUID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                EditorScreen(
                    onBack = { navController.popBackStack() },
                    onOpenShareCard = { uuid -> navController.navigate(Routes.shareCard(uuid)) },
                )
            }

            composable(
                route = "${Routes.SHARE_CARD}?${Routes.ARG_UUID}={${Routes.ARG_UUID}}",
                arguments = listOf(
                    navArgument(Routes.ARG_UUID) {
                        type = NavType.StringType
                        nullable = false
                    },
                ),
            ) {
                ShareCardScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                val context = LocalContext.current
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAuth = { mode ->
                        navController.navigate(Routes.auth(mode)) {
                            launchSingleTop = true
                        }
                    },
                    appInfo =
                        SettingsAppInfo(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE,
                            buildType = BuildConfig.BUILD_TYPE,
                            flowBackendBaseUrl = BuildConfig.FLOW_BACKEND_BASE_URL,
                        ),
                    updateInfo = updateUiState.toSettingsUpdateInfo(),
                    onCheckForUpdates = appViewModel::checkForUpdatesManually,
                    onRunUpdateAction = {
                        when (updateUiState.phase) {
                            AppUpdatePhase.AVAILABLE -> appViewModel.startUpdateDownload()
                            AppUpdatePhase.READY_TO_INSTALL ->
                                appViewModel.installDownloadedUpdate(context as? Activity)
                            else -> Unit
                        }
                    },
                    onClearIgnoredVersion = appViewModel::clearIgnoredUpdate,
                    onRequestAddQuickCaptureTile = {
                        val act = context as? Activity
                        if (act == null) {
                            Toast.makeText(context, "当前页面无法发起系统添加请求", Toast.LENGTH_SHORT).show()
                            return@SettingsScreen
                        }

                        // minSdk=33：这里不需要再判断 SDK 版本。
                        val sbm = act.getSystemService(StatusBarManager::class.java)
                        if (sbm == null) {
                            Toast.makeText(context, "系统服务不可用，无法一键添加", Toast.LENGTH_SHORT).show()
                            return@SettingsScreen
                        }
                        sbm.requestAddTileService(
                            ComponentName(act, QuickCaptureTileService::class.java),
                            context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_capture),
                            AndroidIcon.createWithResource(
                                act,
                                cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_capture,
                            ),
                            act.mainExecutor,
                        ) { result ->
                            // 不强依赖具体常量值：不同 ROM 可能返回额外状态码；数值足够用户判断。
                            Toast.makeText(context, "系统返回：$result", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStartQuickCaptureOverlay = {
                        context.startService(Intent(context, QuickCaptureOverlayService::class.java))
                    },
                    onOpenQuickCaptureActivity = {
                        context.startActivity(
                            Intent(context, QuickCaptureActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                    },
                    onRequestAddQuickScreenshotTile = {
                        val act = context as? Activity
                        if (act == null) {
                            Toast.makeText(context, "当前页面无法发起系统添加请求", Toast.LENGTH_SHORT).show()
                            return@SettingsScreen
                        }

                        // minSdk=33：这里不需要再判断 SDK 版本。
                        val sbm = act.getSystemService(StatusBarManager::class.java)
                        if (sbm == null) {
                            Toast.makeText(context, "系统服务不可用，无法一键添加", Toast.LENGTH_SHORT).show()
                            return@SettingsScreen
                        }
                        sbm.requestAddTileService(
                            ComponentName(act, QuickScreenshotTileService::class.java),
                            context.getString(cc.pscly.onememos.core.quicktiles.R.string.qs_quick_screenshot),
                            AndroidIcon.createWithResource(
                                act,
                                cc.pscly.onememos.core.quicktiles.R.drawable.ic_qs_quick_screenshot,
                            ),
                            act.mainExecutor,
                        ) { result ->
                            Toast.makeText(context, "系统返回：$result", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenScreenshotQuickCapture = {
                        context.startActivity(
                            Intent(context, ScreenshotQuickCaptureActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        )
                    },
                )
            }

            composable(
                route = "${Routes.AUTH}?${Routes.ARG_AUTH_MODE}={${Routes.ARG_AUTH_MODE}}",
                arguments = listOf(
                    navArgument(Routes.ARG_AUTH_MODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                AuthScreen(
                    onBack = { navController.popBackStack() },
                    onAuthed = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

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

private fun AppUpdateUiState.toSettingsUpdateInfo(): SettingsUpdateInfo =
    SettingsUpdateInfo(
        statusText = statusMessage,
        checking = phase == AppUpdatePhase.CHECKING,
        actionLabel =
            when (phase) {
                AppUpdatePhase.AVAILABLE -> "下载更新"
                AppUpdatePhase.READY_TO_INSTALL -> "安装更新"
                else -> null
            },
        actionEnabled = phase == AppUpdatePhase.AVAILABLE || phase == AppUpdatePhase.READY_TO_INSTALL,
        downloadProgressPercent = downloadProgressPercent.takeIf { phase == AppUpdatePhase.DOWNLOADING },
        ignoredVersionTag = ignoredVersionTag,
    )

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
