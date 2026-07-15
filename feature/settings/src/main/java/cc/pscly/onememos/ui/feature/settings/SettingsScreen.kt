@file:OptIn(ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.settings

import cc.pscly.onememos.calendar.WritableCalendar

import android.app.AlarmManager
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.pscly.onememos.domain.model.FullSyncStage
import cc.pscly.onememos.domain.model.FullSyncStatus
import cc.pscly.onememos.domain.model.LoginMode
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.model.QuickInsertTimeFormat
import cc.pscly.onememos.domain.model.ThemeMode
import cc.pscly.onememos.domain.model.ThemePalette
import cc.pscly.onememos.domain.model.TodoReminderMode
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.util.ByteSizeFormatter
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.worker.MemoDerivedFieldsRebuildScheduler
import androidx.work.ExistingWorkPolicy
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsAppInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val flowBackendBaseUrl: String,
)

data class SettingsUpdateInfo(
    val statusText: String,
    val checking: Boolean = false,
    val actionLabel: String? = null,
    val actionEnabled: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val ignoredVersionTag: String = "",
)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAuth: (startMode: String?) -> Unit,
    appInfo: SettingsAppInfo,
    updateInfo: SettingsUpdateInfo,
    onCheckForUpdates: () -> Unit,
    onRunUpdateAction: () -> Unit,
    onClearIgnoredVersion: () -> Unit,
    onRequestAddQuickCaptureTile: () -> Unit,
    onStartQuickCaptureOverlay: () -> Unit,
    onOpenQuickCaptureActivity: () -> Unit,
    onRequestAddQuickScreenshotTile: () -> Unit,
    onOpenScreenshotQuickCapture: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val changePasswordState by viewModel.changePasswordUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canScheduleExactAlarms by remember { mutableStateOf(alarmManager?.canScheduleExactAlarms() == true) }
    var lastExactAlarmAllowed by remember { mutableStateOf(canScheduleExactAlarms) }
    var calendarPermissionGranted by remember { mutableStateOf(viewModel.hasCalendarPermissions()) }
    var calendarIntegrationPendingEnable by remember { mutableStateOf(false) }
    var selectedCalendarLabel by remember { mutableStateOf<String?>(null) }
    var availableCalendars by remember { mutableStateOf<List<WritableCalendar>>(emptyList()) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var showClearImagesConfirm by remember { mutableStateOf(false) }
    var showClearAttachmentsConfirm by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showFullResyncConfirm by remember { mutableStateOf(false) }
    var showDev2PasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canDrawOverlays = Settings.canDrawOverlays(context)
                    canScheduleExactAlarms = alarmManager?.canScheduleExactAlarms() == true
                    calendarPermissionGranted = viewModel.hasCalendarPermissions()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var dev2Password by remember { mutableStateOf("") }
    var dev2PasswordError by remember { mutableStateOf<String?>(null) }
    var versionTapCount by remember { mutableStateOf(0) }
    var versionFirstTapAtMs by remember { mutableStateOf(0L) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPassword2 by remember { mutableStateOf("") }

    val requestCalendarPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted =
                (result[Manifest.permission.READ_CALENDAR] == true) &&
                    (result[Manifest.permission.WRITE_CALENDAR] == true)
            calendarPermissionGranted = granted

            if (calendarIntegrationPendingEnable) {
                calendarIntegrationPendingEnable = false
                if (granted) {
                    viewModel.updateCalendarIntegrationEnabled(true)
                } else {
                    Toast.makeText(context, "未授予日历权限，无法开启自动写入日历", Toast.LENGTH_SHORT).show()
                }
            } else if (granted && uiState.calendarIntegrationEnabled) {
                // 授权成功后立即触发一次同步，减少“开关开了但没反应”的错觉。
                viewModel.requestTodoReminderReschedule()
            }
        }

    LaunchedEffect(changePasswordState.successAt) {
        if (changePasswordState.successAt <= 0L) return@LaunchedEffect
        Toast.makeText(context, "密码已更新", Toast.LENGTH_SHORT).show()
        showChangePasswordDialog = false
        currentPassword = ""
        newPassword = ""
        newPassword2 = ""
        viewModel.resetChangePasswordState()
    }

    LaunchedEffect(canScheduleExactAlarms, uiState.todoReminderMode) {
        val justGranted = canScheduleExactAlarms && !lastExactAlarmAllowed
        lastExactAlarmAllowed = canScheduleExactAlarms
        if (justGranted && uiState.todoReminderMode == TodoReminderMode.EXACT) {
            viewModel.requestTodoReminderReschedule()
        }
    }

    LaunchedEffect(calendarPermissionGranted, uiState.calendarIntegrationCalendarId) {
        val calendarId = uiState.calendarIntegrationCalendarId
        if (!calendarPermissionGranted || calendarId == null || calendarId <= 0L) {
            selectedCalendarLabel = null
            return@LaunchedEffect
        }

        selectedCalendarLabel =
            withContext(Dispatchers.IO) {
                viewModel.calendarLabel(calendarId)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val bound = uiState.serverUrl.isNotBlank() && uiState.token.isNotBlank()
            InkCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "账号与同步",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (bound) {
                        Text(
                            text = "已登录：联网后会自动同步（含离线期间创建的记录）。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "全量同步",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            val stageText =
                                when (uiState.fullSyncStage) {
                                    FullSyncStage.NORMAL -> "普通"
                                    FullSyncStage.ARCHIVED -> "归档"
                                }
                            val pages = uiState.fullSyncPagesFetched
                            val items = uiState.fullSyncItemsFetched
                            val lastSuccessAtText =
                                if (uiState.fullSyncLastSuccessAt <= 0L) {
                                    "从未"
                                } else {
                                    DateTimeFormatter.formatYmdHm(uiState.fullSyncLastSuccessAt)
                                }

                            @Suppress("REDUNDANT_ELSE_IN_WHEN")
                            when (uiState.fullSyncStatus) {
                                FullSyncStatus.RUNNING -> {
                                    Text(
                                        text = "进行中：$stageText（页 $pages，条 $items）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }

                                FullSyncStatus.SUCCESS -> {
                                    Text(
                                        text = "已完成：$lastSuccessAtText（页 $pages，条 $items）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }

                                FullSyncStatus.FAILED -> {
                                    val err = uiState.fullSyncLastError.ifBlank { "未知错误" }
                                    Text(
                                        text = "失败：$err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "进度：$stageText（页 $pages，条 $items）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }

                                FullSyncStatus.CANCELLED -> {
                                    Text(
                                        text = "已取消：$stageText（页 $pages，条 $items）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }

                                FullSyncStatus.IDLE -> {
                                    Text(
                                        text = "尚未完成全量同步",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                    )
                                }

                                else -> {
                                    Text(
                                        text = "未知状态：$stageText（页 $pages，条 $items）",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "同步状态",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )

                            val g = uiState.globalSync
                            val workText =
                                when {
                                    g.isSyncing -> "同步中"
                                    g.isEnqueued -> "等待执行"
                                    else -> "空闲"
                                }
                            val netText = if (g.networkOnline) "在线" else "离线"
                            val pendingText = "待同步 ${g.pendingCount.coerceAtLeast(0)} 条"

                            Text(
                                text = "状态：$workText · 网络：$netText · $pendingText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            val lastSuccessText =
                                if (g.lastSuccessAt <= 0L) {
                                    "从未"
                                } else {
                                    DateTimeFormatter.formatYmdHm(g.lastSuccessAt)
                                }
                            Text(
                                text = "最近成功：$lastSuccessText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (g.hasError) {
                                val err = g.lastError.ifBlank { "未知错误" }
                                val lastErrText =
                                    if (g.lastErrorAt <= 0L) {
                                        ""
                                    } else {
                                        "（${DateTimeFormatter.formatYmdHm(g.lastErrorAt)}）"
                                    }
                                Text(
                                    text = "最近失败：$err$lastErrText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                if (g.authInvalid) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        OutlinedButton(onClick = { onOpenAuth(null) }) {
                                            Text("重新登录")
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = viewModel::requestSyncNow) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "立即同步",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("立即同步")
                            }
                            OutlinedButton(onClick = { showFullResyncConfirm = true }) {
                                Text("重新同步所有笔记")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(
                                onClick = {
                                    currentPassword = ""
                                    newPassword = ""
                                    newPassword2 = ""
                                    viewModel.resetChangePasswordState()
                                    showChangePasswordDialog = true
                                },
                            ) {
                                Text("修改密码")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            OutlinedButton(onClick = { showLogoutConfirm = true }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = "退出登录",
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("退出")
                            }
                        }
                    } else {
                        Text(
                            text = "当前为离线模式，数据仅保存在本机。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "你可以先离线记笔记；也可以现在登录账号，或填写访问令牌后自动同步。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenAuth(null) },
                            ) {
                                Text("账号登录/注册")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenAuth("custom") },
                            ) {
                                Text("填写 Token")
                            }
                        }
                    }
                }
            }

            if (uiState.dev2Unlocked) {
                InkCard {
                    Text(
                        text = "新建默认可见性",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    MemoVisibilityRow(
                        visibility = uiState.defaultVisibility,
                        onSelect = viewModel::updateDefaultVisibility,
                    )
                }
            }

            InkCard {
                Text(
                    text = "快捷开关",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "极速记录（下拉快捷开关）",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "把“极速记录”添加到系统下拉快捷开关后，一点就能快速记录；可选“悬浮窗模式”（不跳转当前应用）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(12.dp))
                FloatingQuickCaptureRow(
                    enabled = uiState.quickCaptureOverlayEnabled,
                    canDrawOverlays = canDrawOverlays,
                    onToggle = { enabled ->
                        viewModel.updateQuickCaptureOverlayEnabled(enabled)
                        if (enabled && !canDrawOverlays) {
                            Toast.makeText(context, "悬浮窗模式需要先授予“在其他应用上层显示”权限", Toast.LENGTH_LONG).show()
                        }
                    },
                    onGrantPermission = {
                        val uri = Uri.parse("package:${context.packageName}")
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
                QuickInsertTimeRow(
                    enabled = uiState.quickInsertTimeEnabled,
                    format = uiState.quickInsertTimeFormat,
                    onToggle = viewModel::updateQuickInsertTimeEnabled,
                    onSelectFormat = viewModel::updateQuickInsertTimeFormat,
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onRequestAddQuickCaptureTile,
                    ) {
                        Text("添加到快捷开关")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (uiState.quickCaptureOverlayEnabled) {
                                if (canDrawOverlays) {
                                    onStartQuickCaptureOverlay()
                                } else {
                                    val uri = Uri.parse("package:${context.packageName}")
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            } else {
                                onOpenQuickCaptureActivity()
                            }
                        },
                    ) {
                        Text("立即打开")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "截图记录（下拉快捷开关）",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "点一下先截图，再弹出悬浮记录窗（附件*1），可补充文字再“盖”。首次会弹出系统“允许捕获屏幕”确认框。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onRequestAddQuickScreenshotTile,
                    ) {
                        Text("添加到快捷开关")
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onOpenScreenshotQuickCapture,
                    ) {
                        Text("立即打开")
                    }
                }
            }

            InkCard {
                Text(
                    text = "搜索与筛选",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                RegexSearchRow(
                    enabled = uiState.regexSearchEnabled,
                    onToggle = viewModel::updateRegexSearchEnabled,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ShowTagCountsRow(
                    enabled = uiState.showTagCountsInFilter,
                    onToggle = viewModel::updateShowTagCountsInFilter,
                )
            }

            InkCard {
                Text(
                    text = "待办提醒",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                TodoReminderModeRow(
                    mode = uiState.todoReminderMode,
                    canScheduleExactAlarms = canScheduleExactAlarms,
                    onSelect = viewModel::updateTodoReminderMode,
                    onRequestExactAlarmPermission = {
                        val uri = Uri.parse("package:${context.packageName}")
                        val intent =
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                    },
                )
            }

            InkCard {
                Text(
                    text = "日历联动",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "把“已设置提醒”的待办自动写入系统日历（需要授权并选择一个可写日历）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(modifier = Modifier.height(10.dp))

                val openCalendarPicker = {
                    if (!calendarPermissionGranted) {
                        Toast.makeText(context, "请先授权日历权限", Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            val calendars =
                                withContext(Dispatchers.IO) {
                                    viewModel.writableCalendars()
                                }
                            if (calendars.isEmpty()) {
                                Toast
                                    .makeText(context, "未找到可写日历，请先在系统日历添加账号或开启可见", Toast.LENGTH_SHORT)
                                    .show()
                                return@launch
                            }
                            availableCalendars = calendars
                            showCalendarPicker = true
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "自动写入系统日历", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.calendarIntegrationEnabled,
                        onCheckedChange = { enabled ->
                            if (!enabled) {
                                viewModel.updateCalendarIntegrationEnabled(false)
                                return@Switch
                            }

                            if (!calendarPermissionGranted) {
                                calendarIntegrationPendingEnable = true
                                requestCalendarPermissions.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR,
                                    ),
                                )
                                return@Switch
                            }

                            viewModel.updateCalendarIntegrationEnabled(true)
                        },
                    )
                }

                if (uiState.calendarIntegrationEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))

                    if (!calendarPermissionGranted) {
                        Text(
                            text = "未授予日历权限（READ/WRITE_CALENDAR），无法自动写入。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                requestCalendarPermissions.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALENDAR,
                                        Manifest.permission.WRITE_CALENDAR,
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("授权日历权限")
                        }
                    } else {
                        val calendarId = uiState.calendarIntegrationCalendarId
                        val calendarReady = calendarId != null && calendarId > 0L

                        Text(
                            text =
                                if (calendarReady) {
                                    "当前日历：${selectedCalendarLabel ?: calendarId}"
                                } else {
                                    "未选择要写入的日历。"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (calendarReady) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { openCalendarPicker() },
                            ) {
                                Text(if (calendarReady) "更换日历" else "选择日历")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = calendarReady,
                                onClick = { viewModel.updateCalendarIntegrationCalendarId(null) },
                            ) {
                                Text("清除选择")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "同步日历提醒", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "把“提前 X 分钟”也写进日历提醒（可能与应用通知重复）。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Switch(
                                checked = uiState.calendarIntegrationSyncReminders,
                                onCheckedChange = viewModel::updateCalendarIntegrationSyncReminders,
                                enabled = calendarReady,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.requestTodoReminderReschedule()
                                Toast.makeText(context, "已触发后台同步", Toast.LENGTH_SHORT).show()
                            },
                            enabled = calendarReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("立即同步一次")
                        }
                    }
                }
            }

            InkCard {
                Text(
                    text = "动效",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                SealStampDurationRow(
                    durationMs = uiState.sealStampDurationMs,
                    onChange = viewModel::updateSealStampDurationMs,
                )
            }

            InkCard {
                Text(
                    text = "缓存与存储",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "当前占用",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (uiState.cacheLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = viewModel::refreshCacheStats) {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    }
                }

                val stats = uiState.cacheStats
                Text(
                    text = "本地内容（数据库）：${ByteSizeFormatter.format(stats?.databaseBytes ?: 0)}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "图片缓存：${ByteSizeFormatter.format(stats?.imageCacheBytes ?: 0)}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "附件本地缓存：${ByteSizeFormatter.format(stats?.attachmentCacheBytes ?: 0)}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "其它缓存：${ByteSizeFormatter.format(stats?.otherCacheBytes ?: 0)}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "提示：缓存只有浏览过的内容才会逐步写入；清理缓存不会删除你的服务器数据。",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )

                if (!uiState.cacheError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.cacheError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.cacheClearing,
                        onClick = { showClearImagesConfirm = true },
                    ) {
                        Text("清理图片缓存")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.cacheClearing,
                        onClick = { showClearAttachmentsConfirm = true },
                    ) {
                        Text("清理附件缓存")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.cacheClearing,
                    onClick = { showClearAllConfirm = true },
                ) {
                    Icon(imageVector = Icons.Filled.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("清理全部缓存")
                }
            }

            Text(
                text = "离线",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OfflinePrefetchRow(
                enabled = uiState.offlineImagePrefetchEnabled,
                maxMemos = uiState.offlineImagePrefetchMaxMemos,
                maxImages = uiState.offlineImagePrefetchMaxImages,
                onToggle = viewModel::updateOfflineImagePrefetchEnabled,
                onChangeMemos = viewModel::updateOfflineImagePrefetchMaxMemos,
                onChangeImages = viewModel::updateOfflineImagePrefetchMaxImages,
            )

            AttachmentCacheLimitRow(
                maxMb = uiState.attachmentCacheMaxMb,
                onChange = viewModel::updateAttachmentCacheMaxMb,
            )

            InkCard {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                ThemeModeRow(
                    title = "模式",
                    mode = uiState.themeMode,
                    onSelect = viewModel::updateThemeMode,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThemePaletteRow(
                    title = "配色",
                    palette = uiState.themePalette,
                    onSelect = viewModel::updateThemePalette,
                )
            }

            InkCard {
                Text(
                    text = "开发者选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "自动标签元数据行（例如 __Atags）",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "用于隐藏由自动化流程写入的“标签行”。判断规则：行尾匹配关键字（忽略行尾空白）。支持多个关键字（逗号/空格/换行分隔）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.devAutoTagLineKeywords,
                    onValueChange = viewModel::updateDevAutoTagLineKeywords,
                    label = { Text("关键字（默认 __Atags）") },
                    placeholder = { Text("__Atags, __BTAGS") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "主页显示该行", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.devShowAutoTagLineInHome,
                        onCheckedChange = viewModel::updateDevShowAutoTagLineInHome,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "查看页显示该行", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.devShowAutoTagLineInView,
                        onCheckedChange = viewModel::updateDevShowAutoTagLineInView,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "编辑页显示该行", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = uiState.devShowAutoTagLineInEdit,
                        onCheckedChange = viewModel::updateDevShowAutoTagLineInEdit,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "提示：若编辑页隐藏该行，保存时会自动保留它（不会丢失）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "主页富预览粘住",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "0=关闭；默认 500；仅 benchmark/release 生效（当前：${appInfo.buildType}）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))
                var richPreviewStickyLimitText by
                    remember(uiState.devHomeRichPreviewStickyLimit) {
                        mutableStateOf(uiState.devHomeRichPreviewStickyLimit.toString())
                    }
                OutlinedTextField(
                    value = richPreviewStickyLimitText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }
                        richPreviewStickyLimitText = digits
                        viewModel.updateDevHomeRichPreviewStickyLimit(digits.toIntOrNull() ?: 0)
                    },
                    label = { Text("富预览粘住上限（0=关闭）") },
                    placeholder = { Text("500") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "性能维护",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "用于重建主页列表的预览/标签索引（派生字段）。一般升级后或改了内容计算策略时使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        MemoDerivedFieldsRebuildScheduler.enqueue(
                            context = context,
                            initialDelaySeconds = 0,
                            existingWorkPolicy = ExistingWorkPolicy.REPLACE,
                        )
                        Toast.makeText(context, "已开始后台重建主页预览索引", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "重建主页预览索引")
                }
            }

            InkCard {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                val versionText = "${appInfo.versionName} (${appInfo.versionCode})"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (versionFirstTapAtMs == 0L || now - versionFirstTapAtMs > 10_000L) {
                                versionFirstTapAtMs = now
                                versionTapCount = 1
                            } else {
                                versionTapCount += 1
                            }
                            if (versionTapCount >= 6) {
                                versionTapCount = 0
                                versionFirstTapAtMs = 0L
                                dev2Password = ""
                                dev2PasswordError = null
                                showDev2PasswordDialog = true
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = "版本号", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(text = "应用更新", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = updateInfo.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp),
                )
                updateInfo.downloadProgressPercent?.let { progress ->
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = !updateInfo.checking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("检查更新")
                    }
                    updateInfo.actionLabel?.let { label ->
                        Button(
                            onClick = onRunUpdateAction,
                            enabled = updateInfo.actionEnabled,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(label)
                        }
                    }
                }
                if (updateInfo.ignoredVersionTag.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onClearIgnoredVersion) {
                        Text("取消忽略 ${updateInfo.ignoredVersionTag}")
                    }
                }

                if (uiState.dev2Unlocked) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "开发者模式2（已解锁）", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "默认只看自己创建的历史（不混入他人的工作区/公开内容）。开启后可查看公开/工作区内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "查看公开/工作区笔记", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.dev2ShowPublicWorkspaceMemos,
                            onCheckedChange = viewModel::updateDev2ShowPublicWorkspaceMemos,
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "附件上传",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "提示：上传会用 base64 编码，体积会膨胀；上限设得越大越耗时、耗流量。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    var attachmentUploadMaxMbText by
                        remember(uiState.attachmentUploadMaxMb) {
                            mutableStateOf(uiState.attachmentUploadMaxMb.toString())
                        }
                    OutlinedTextField(
                        value = attachmentUploadMaxMbText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            val parsed = digits.toIntOrNull()
                            val clamped =
                                when {
                                    parsed == null -> 50
                                    parsed < 1 -> 1
                                    parsed > 1024 -> 1024
                                    else -> parsed
                                }
                            attachmentUploadMaxMbText = if (digits.isBlank()) "" else clamped.toString()
                            viewModel.updateAttachmentUploadMaxMb(clamped)
                        },
                        label = { Text("附件上传大小上限（MB）") },
                        placeholder = { Text("50") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateDev2ShowPublicWorkspaceMemos(false)
                                viewModel.updateDev2Unlocked(false)
                                Toast.makeText(context, "已退出隐藏模式", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Text("退出隐藏模式")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Flow Backend：${appInfo.flowBackendBaseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            InkCard {
                Text(
                    text = "备份与诊断",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "用于排障与问题定位。诊断文件不包含 Token，仅包含必要的版本/设备信息与开关状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val uriString =
                                withContext(Dispatchers.IO) {
                                    viewModel.exportDiagnostics(
                                        appInfo = appInfo,
                                        canDrawOverlays = canDrawOverlays,
                                        canScheduleExactAlarms = canScheduleExactAlarms,
                                    )
                                }
                            if (uriString == null) {
                                Toast.makeText(context, "导出失败，请稍后重试", Toast.LENGTH_SHORT).show()
                                return@launch
                            }

                            val shareIntent =
                                Intent(Intent.ACTION_SEND)
                                    .setType("application/json")
                                    .putExtra(Intent.EXTRA_SUBJECT, "1memos 诊断文件")
                                    .putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val chooser = Intent.createChooser(shareIntent, "分享诊断文件")
                            context.startActivity(chooser)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "导出诊断文件")
                }
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录？") },
            text = { Text("将清空本机保存的访问令牌（Token）。本地离线记录会保留，之后仍可再次登录并同步。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout(clearServerUrl = false)
                        Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showFullResyncConfirm) {
        AlertDialog(
            onDismissRequest = { showFullResyncConfirm = false },
            title = { Text("重新同步所有笔记？") },
            text = { Text("将从服务器重新拉取所有笔记（含归档）。过程在后台静默进行，可能耗时。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFullResyncConfirm = false
                        viewModel.requestFullResync()
                        Toast.makeText(context, "已开始后台重同步", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullResyncConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("修改密码") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "新密码至少 6 位，UTF-8 不超过 71 字节。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            if (!changePasswordState.error.isNullOrBlank()) {
                                viewModel.resetChangePasswordState()
                            }
                        },
                        label = { Text("当前密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            if (!changePasswordState.error.isNullOrBlank()) {
                                viewModel.resetChangePasswordState()
                            }
                        },
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = newPassword2,
                        onValueChange = {
                            newPassword2 = it
                            if (!changePasswordState.error.isNullOrBlank()) {
                                viewModel.resetChangePasswordState()
                            }
                        },
                        label = { Text("再次确认") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (!changePasswordState.error.isNullOrBlank()) {
                        Text(
                            text = changePasswordState.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.changePassword(currentPassword, newPassword, newPassword2) },
                    enabled = !changePasswordState.loading,
                ) {
                    if (changePasswordState.loading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("保存")
                        }
                    } else {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDev2PasswordDialog) {
        AlertDialog(
            onDismissRequest = { showDev2PasswordDialog = false },
            title = { Text("进入隐藏模式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入加载密码以解锁开发者模式2。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = dev2Password,
                        onValueChange = {
                            dev2Password = it
                            dev2PasswordError = null
                        },
                        label = { Text("加载密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (!dev2PasswordError.isNullOrBlank()) {
                        Text(
                            text = dev2PasswordError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (dev2Password.trim() != "pscly") {
                            dev2PasswordError = "密码错误"
                            return@TextButton
                        }
                        viewModel.updateDev2Unlocked(true)
                        Toast.makeText(context, "隐藏模式已解锁", Toast.LENGTH_SHORT).show()
                        showDev2PasswordDialog = false
                    },
                ) {
                    Text("解锁")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDev2PasswordDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showCalendarPicker) {
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            title = { Text("选择要写入的日历") },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (availableCalendars.isEmpty()) {
                        Text(
                            text = "未找到可写日历。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    } else {
                        availableCalendars.forEach { c ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateCalendarIntegrationCalendarId(c.id)
                                            showCalendarPicker = false
                                            Toast.makeText(context, "已选择日历：${c.displayName}", Toast.LENGTH_SHORT).show()
                                        },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = c.id == uiState.calendarIntegrationCalendarId,
                                    onClick = null,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = c.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCalendarPicker = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showClearImagesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearImagesConfirm = false },
            title = { Text("清理图片缓存？") },
            text = { Text("这会删除本地图片缓存文件；下次查看同一图片可能需要重新下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearImagesConfirm = false
                        viewModel.clearImageCache()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearImagesConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showClearAttachmentsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAttachmentsConfirm = false },
            title = { Text("清理附件缓存？") },
            text = { Text("这会删除“附件本地缓存”（用于离线秒开）；不会删除服务器数据。下次查看同一附件可能需要重新下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAttachmentsConfirm = false
                        viewModel.clearAttachmentCache()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAttachmentsConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清理全部缓存？") },
            text = { Text("会清理应用缓存目录（包含图片缓存）与附件本地缓存目录。不会删除数据库内容与设置，也不会删除服务器数据。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearAllConfirm = false
                        viewModel.clearAllCache()
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

// WritableCalendar 与日历查询已下沉到 :core:calendar，经 SettingsViewModel 薄转发。


@Composable
private fun MemoVisibilityRow(
    visibility: MemoVisibility,
    onSelect: (MemoVisibility) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = visibility == MemoVisibility.PRIVATE,
                onClick = { onSelect(MemoVisibility.PRIVATE) },
            )
            Text(text = "私密（仅自己）")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = visibility == MemoVisibility.PROTECTED,
                onClick = { onSelect(MemoVisibility.PROTECTED) },
            )
            Text(text = "受保护（登录可见）")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = visibility == MemoVisibility.PUBLIC,
                onClick = { onSelect(MemoVisibility.PUBLIC) },
            )
            Text(text = "公开（所有人）")
        }
    }
}

@Composable
private fun TodoReminderModeRow(
    mode: TodoReminderMode,
    canScheduleExactAlarms: Boolean,
    onSelect: (TodoReminderMode) -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == TodoReminderMode.SMART,
                onClick = { onSelect(TodoReminderMode.SMART) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "智能（推荐）", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "基于 WorkManager，省心；但可能被省电/待机策略延迟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == TodoReminderMode.EXACT,
                onClick = { onSelect(TodoReminderMode.EXACT) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "准点（实验）", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "基于 AlarmManager 精确闹钟；更准点，但需要系统允许“精确闹钟”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        if (mode == TodoReminderMode.EXACT) {
            if (canScheduleExactAlarms) {
                Text(
                    text = "系统已允许精确闹钟：准点提醒将优先生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            } else {
                Text(
                    text = "系统未允许精确闹钟：当前会自动降级为“智能”调度（避免无效闹钟）。可在系统设置中开启。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRequestExactAlarmPermission,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("去开启精确闹钟")
                }
            }
        }
    }
}

@Composable
private fun OfflinePrefetchRow(
    enabled: Boolean,
    maxMemos: Int,
    maxImages: Int,
    onToggle: (Boolean) -> Unit,
    onChangeMemos: (Int) -> Unit,
    onChangeImages: (Int) -> Unit,
) {
    InkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "自动预取图片（提升离线浏览）", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "联网时后台缓存最近随笔的图片附件；断网打开不会空白",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "范围：${if (maxMemos <= 0) "无限" else "最近 $maxMemos 条随笔"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Slider(
            value = maxMemos.toFloat(),
            onValueChange = { onChangeMemos(it.roundToInt()) },
            valueRange = 0f..100f,
            steps = 19,
            enabled = enabled,
        )

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "上限：${if (maxImages <= 0) "无限" else "最多 $maxImages 张图片"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Slider(
            value = maxImages.toFloat(),
            onValueChange = { onChangeImages(it.roundToInt()) },
            valueRange = 0f..200f,
            steps = 39,
            enabled = enabled,
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "提示：开关负责启停；数值为 0 表示“无限”。建议保持开启以获得更稳定的离线体验。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun AttachmentCacheLimitRow(
    maxMb: Int,
    onChange: (Int) -> Unit,
) {
    InkCard {
        Text(text = "附件缓存上限", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (maxMb <= 0) "当前：无限" else "当前：$maxMb MB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Slider(
            value = maxMb.coerceIn(0, 2048).toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..2048f,
            steps = 40,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "超过上限会自动淘汰最旧的附件缓存文件；不会影响服务器数据。设置为 0 表示无限（不推荐）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ThemeModeRow(
    title: String,
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = MaterialTheme.colorScheme.outline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == ThemeMode.FOLLOW_SYSTEM,
                onClick = { onSelect(ThemeMode.FOLLOW_SYSTEM) },
            )
            Text(text = "跟随系统")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == ThemeMode.LIGHT,
                onClick = { onSelect(ThemeMode.LIGHT) },
            )
            Text(text = "白天")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = mode == ThemeMode.DARK,
                onClick = { onSelect(ThemeMode.DARK) },
            )
            Text(text = "夜晚")
        }
    }
}

@Composable
private fun ThemePaletteRow(
    title: String,
    palette: ThemePalette,
    onSelect: (ThemePalette) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, color = MaterialTheme.colorScheme.outline)

        PaletteRadio(text = "宣纸 · 朱砂", selected = palette == ThemePalette.PAPER_INK) {
            onSelect(ThemePalette.PAPER_INK)
        }
        PaletteRadio(text = "宣纸 · 黛蓝", selected = palette == ThemePalette.INDIGO) {
            onSelect(ThemePalette.INDIGO)
        }
        PaletteRadio(text = "玄青 · 荧光青", selected = palette == ThemePalette.CYBER) {
            onSelect(ThemePalette.CYBER)
        }
    }
}

@Composable
private fun PaletteRadio(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(text = text)
    }
}

@Composable
private fun RegexSearchRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "正则表达式搜索")
            Text(
                text = "开启后：搜索文本按正则匹配（#标签仍按标签筛选）",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ShowTagCountsRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "标签数量提示")
            Text(
                text = "标签筛选面板里显示每个标签的记录数（例如：#读书 (12)）",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun FloatingQuickCaptureRow(
    enabled: Boolean,
    canDrawOverlays: Boolean,
    onToggle: (Boolean) -> Unit,
    onGrantPermission: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "悬浮记录（不跳转当前应用）")
            Text(
                text =
                    if (enabled) {
                        if (canDrawOverlays) {
                            "已开启：从快捷入口弹出悬浮窗记录；保存后不切回 1memos 主界面"
                        } else {
                            "已开启：但尚未授权“在其他应用上层显示”，请先去系统设置授予权限"
                        }
                    } else {
                        "关闭：使用原有弹窗模式（会打开 1memos 的极速记录界面）"
                    },
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.bodySmall,
            )
            if (enabled && !canDrawOverlays) {
                TextButton(onClick = onGrantPermission) {
                    Text("去授权")
                }
            }
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun QuickInsertTimeRow(
    enabled: Boolean,
    format: QuickInsertTimeFormat,
    onToggle: (Boolean) -> Unit,
    onSelectFormat: (QuickInsertTimeFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "一键插入时间")
                Text(
                    text = "开启后：编辑页、极速记录页与悬浮极速记录都会显示「时」按钮；点击后插入带 `>` 的时间戳并自动补空行。",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }

        Text(
            text = "时间格式",
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
        )
        QuickInsertTimeFormatRow(
            format = format,
            onSelect = onSelectFormat,
        )
    }
}

@Composable
private fun QuickInsertTimeFormatRow(
    format: QuickInsertTimeFormat,
    onSelect: (QuickInsertTimeFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = format == QuickInsertTimeFormat.FULL_DATETIME,
                onClick = { onSelect(QuickInsertTimeFormat.FULL_DATETIME) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "完整日期时间", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "插入为：> yyyy-MM-dd HH:mm:ss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = format == QuickInsertTimeFormat.TIME_ONLY,
                onClick = { onSelect(QuickInsertTimeFormat.TIME_ONLY) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "仅时间", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "插入为：> HH:mm:ss",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SealStampDurationRow(
    durationMs: Int,
    onChange: (Int) -> Unit,
) {
    val minSec = 0.3f
    val maxSec = 1.2f
    val stepSec = 0.1f
    val steps = (((maxSec - minSec) / stepSec).roundToInt() - 1).coerceAtLeast(0)

    val currentSec = (durationMs.coerceIn((minSec * 1000).roundToInt(), (maxSec * 1000).roundToInt()) / 1000f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "盖章动效速度")
                Text(
                    text = "保存/归档成功后的“已记/已封”浮层停留时长（越大越慢）",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = String.format(Locale.getDefault(), "%.1fs", currentSec),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Slider(
            value = currentSec,
            onValueChange = { sec ->
                val snapped = (sec / stepSec).roundToInt() * stepSec
                onChange((snapped * 1000).roundToInt())
            },
            valueRange = minSec..maxSec,
            steps = steps,
        )
    }
}
