@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkSpacing
import kotlinx.coroutines.flow.collectLatest
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar

@Composable
fun AuthScreen(
    onBack: () -> Unit,
    onAuthed: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { e ->
            when (e) {
                AuthEvent.Authed -> {
                    focusManager.clearFocus(force = true)
                    onAuthed()
                }
            }
        }
    }

    AuthScreenContent(
        uiState = uiState,
        onBack = onBack,
        onTabSelected = viewModel::switchTab,
        onUsernameChanged = viewModel::updateUsername,
        onPasswordChanged = viewModel::updatePassword,
        onConfirmPasswordChanged = viewModel::updateConfirmPassword,
        onPasswordVisibleToggle = viewModel::togglePasswordVisible,
        onBackendFormSwitch = viewModel::switchBackendForm,
        onBackendSubmit = viewModel::submitBackend,
        onTokenChanged = viewModel::updateCustomToken,
        onTokenVisibleToggle = viewModel::toggleTokenVisible,
        onCustomServerUrlChanged = viewModel::updateCustomServerUrl,
        onCustomSave = viewModel::saveCustom,
    )
}

@Composable
internal fun AuthScreenContent(
    uiState: AuthUiState,
    onBack: () -> Unit,
    onTabSelected: (AuthTab) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onPasswordVisibleToggle: () -> Unit,
    onBackendFormSwitch: (BackendForm) -> Unit,
    onBackendSubmit: () -> Unit,
    onTokenChanged: (String) -> Unit,
    onTokenVisibleToggle: () -> Unit,
    onCustomServerUrlChanged: (String) -> Unit,
    onCustomSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            PaperInkTopAppBar(
                title = { Text("登录") },
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
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(padding)
                .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X14),
        ) {
            TabRow(selectedTabIndex = if (uiState.tab == AuthTab.BACKEND) 0 else 1) {
                Tab(
                    selected = uiState.tab == AuthTab.BACKEND,
                    onClick = { onTabSelected(AuthTab.BACKEND) },
                    text = { Text("账号登录") },
                )
                Tab(
                    selected = uiState.tab == AuthTab.CUSTOM,
                    onClick = { onTabSelected(AuthTab.CUSTOM) },
                    text = { Text("访问令牌") },
                )
            }

            when (uiState.tab) {
                AuthTab.BACKEND ->
                    BackendPane(
                        uiState = uiState,
                        onTabSelected = onTabSelected,
                        onUsernameChanged = onUsernameChanged,
                        onPasswordChanged = onPasswordChanged,
                        onConfirmPasswordChanged = onConfirmPasswordChanged,
                        onPasswordVisibleToggle = onPasswordVisibleToggle,
                        onBackendFormSwitch = onBackendFormSwitch,
                        onBackendSubmit = onBackendSubmit,
                    )

                AuthTab.CUSTOM ->
                    CustomPane(
                        uiState = uiState,
                        onTokenChanged = onTokenChanged,
                        onTokenVisibleToggle = onTokenVisibleToggle,
                        onCustomServerUrlChanged = onCustomServerUrlChanged,
                        onCustomSave = onCustomSave,
                    )
            }
        }
    }
}

@Composable
private fun BackendPane(
    uiState: AuthUiState,
    onTabSelected: (AuthTab) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onPasswordVisibleToggle: () -> Unit,
    onBackendFormSwitch: (BackendForm) -> Unit,
    onBackendSubmit: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
            Text(
                text = if (uiState.backendForm == BackendForm.LOGIN) "登录账号" else "注册账号",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "用户名仅支持字母与数字；密码至少 6 位（建议不超过 72 字节）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = onUsernameChanged,
                label = { Text("用户名") },
                placeholder = { Text("abc123") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = onPasswordChanged,
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
                visualTransformation =
                    if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        enabled = !uiState.loading,
                        onClick = onPasswordVisibleToggle,
                    ) {
                        Icon(
                            imageVector = if (uiState.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (uiState.passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )

            if (uiState.backendForm == BackendForm.REGISTER) {
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = onConfirmPasswordChanged,
                    label = { Text("确认密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.loading,
                    visualTransformation =
                        if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }

            if (!uiState.error.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .semantics {
                                error(uiState.error.orEmpty())
                                liveRegion = LiveRegionMode.Assertive
                            }.testTag("auth_error_backend"),
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.loading,
                    onClick = onBackendSubmit,
                ) {
                    if (uiState.loading) {
                        // 按钮内加载态：豁免状态原语；尺寸为结构常量
                        CircularProgressIndicator(
                            modifier = Modifier.size(InkSpacing.X18),
                            strokeWidth = InkBorder.SpinnerStroke,
                        )
                        Spacer(modifier = Modifier.size(InkSpacing.X10))
                    }
                    Text(if (uiState.backendForm == BackendForm.LOGIN) "登录" else "注册并登录")
                }
                OutlinedButton(
                    enabled = !uiState.loading,
                    onClick = { onTabSelected(AuthTab.CUSTOM) },
                ) {
                    Text("其他方式")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (uiState.backendForm == BackendForm.LOGIN) {
                    TextButton(enabled = !uiState.loading, onClick = { onBackendFormSwitch(BackendForm.REGISTER) }) {
                        Text("没有账号？去注册")
                    }
                } else {
                    TextButton(enabled = !uiState.loading, onClick = { onBackendFormSwitch(BackendForm.LOGIN) }) {
                        Text("已有账号？去登录")
                    }
                }
                TextButton(enabled = !uiState.loading, onClick = { onTabSelected(AuthTab.CUSTOM) }) {
                    Text("填写 Token")
                }
            }
        }
    }
}

@Composable
private fun CustomPane(
    uiState: AuthUiState,
    onTokenChanged: (String) -> Unit,
    onTokenVisibleToggle: () -> Unit,
    onCustomServerUrlChanged: (String) -> Unit,
    onCustomSave: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
            Text(
                text = "访问令牌（Token）",
                style = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            if (uiState.dev2Unlocked) {
                OutlinedTextField(
                    value = uiState.customServerUrl,
                    onValueChange = onCustomServerUrlChanged,
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.loading,
                )
            }

            OutlinedTextField(
                value = uiState.customToken,
                onValueChange = onTokenChanged,
                label = { Text("访问令牌（Token）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
                visualTransformation =
                    if (uiState.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        enabled = !uiState.loading,
                        onClick = onTokenVisibleToggle,
                    ) {
                        Icon(
                            imageVector = if (uiState.tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (uiState.tokenVisible) "隐藏 Token" else "显示 Token",
                        )
                    }
                },
            )

            if (!uiState.error.isNullOrBlank()) {
                Box(
                    modifier =
                        Modifier
                            .semantics {
                                error(uiState.error.orEmpty())
                                liveRegion = LiveRegionMode.Assertive
                            }.testTag("auth_error_custom"),
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.loading,
                onClick = onCustomSave,
            ) {
                if (uiState.loading) {
                    // 按钮内加载态：豁免状态原语；尺寸为结构常量
                    CircularProgressIndicator(
                        modifier = Modifier.size(InkSpacing.X18),
                        strokeWidth = InkBorder.SpinnerStroke,
                    )
                    Spacer(modifier = Modifier.size(InkSpacing.X10))
                }
                Text("保存并进入")
            }
        }
    }
}
