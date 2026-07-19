@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.theme.InkSpacing
import kotlinx.coroutines.flow.collectLatest

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

    Scaffold(
        topBar = {
            TopAppBar(
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
                .padding(padding)
                .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X14),
        ) {
            TabRow(selectedTabIndex = if (uiState.tab == AuthTab.BACKEND) 0 else 1) {
                Tab(
                    selected = uiState.tab == AuthTab.BACKEND,
                    onClick = { viewModel.switchTab(AuthTab.BACKEND) },
                    text = { Text("账号登录") },
                )
                Tab(
                    selected = uiState.tab == AuthTab.CUSTOM,
                    onClick = { viewModel.switchTab(AuthTab.CUSTOM) },
                    text = { Text("访问令牌") },
                )
            }

            when (uiState.tab) {
                AuthTab.BACKEND -> BackendPane(uiState = uiState, viewModel = viewModel)
                AuthTab.CUSTOM -> CustomPane(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun BackendPane(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
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
                onValueChange = viewModel::updateUsername,
                label = { Text("用户名") },
                placeholder = { Text("abc123") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
            )

            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
                visualTransformation =
                    if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        enabled = !uiState.loading,
                        onClick = viewModel::togglePasswordVisible,
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
                    onValueChange = viewModel::updateConfirmPassword,
                    label = { Text("确认密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.loading,
                    visualTransformation =
                        if (uiState.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                )
            }

            if (!uiState.error.isNullOrBlank()) {
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
                    onClick = viewModel::submitBackend,
                ) {
                    if (uiState.loading) {
                        // 按钮内加载态：豁免状态原语；尺寸为结构常量
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.size(InkSpacing.X10))
                    }
                    Text(if (uiState.backendForm == BackendForm.LOGIN) "登录" else "注册并登录")
                }
                OutlinedButton(
                    enabled = !uiState.loading,
                    onClick = { viewModel.switchTab(AuthTab.CUSTOM) },
                ) {
                    Text("其他方式")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (uiState.backendForm == BackendForm.LOGIN) {
                    TextButton(enabled = !uiState.loading, onClick = { viewModel.switchBackendForm(BackendForm.REGISTER) }) {
                        Text("没有账号？去注册")
                    }
                } else {
                    TextButton(enabled = !uiState.loading, onClick = { viewModel.switchBackendForm(BackendForm.LOGIN) }) {
                        Text("已有账号？去登录")
                    }
                }
                TextButton(enabled = !uiState.loading, onClick = { viewModel.switchTab(AuthTab.CUSTOM) }) {
                    Text("填写 Token")
                }
            }
        }
    }
}

@Composable
private fun CustomPane(
    uiState: AuthUiState,
    viewModel: AuthViewModel,
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
                    onValueChange = viewModel::updateCustomServerUrl,
                    label = { Text("服务器地址") },
                    placeholder = { Text("https://example.com/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.loading,
                )
            }

            OutlinedTextField(
                value = uiState.customToken,
                onValueChange = viewModel::updateCustomToken,
                label = { Text("访问令牌（Token）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.loading,
                visualTransformation =
                    if (uiState.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        enabled = !uiState.loading,
                        onClick = viewModel::toggleTokenVisible,
                    ) {
                        Icon(
                            imageVector = if (uiState.tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (uiState.tokenVisible) "隐藏 Token" else "显示 Token",
                        )
                    }
                },
            )

            if (!uiState.error.isNullOrBlank()) {
                Text(
                    text = uiState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.loading,
                onClick = viewModel::saveCustom,
            ) {
                if (uiState.loading) {
                    // 按钮内加载态：豁免状态原语；尺寸为结构常量
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(InkSpacing.X10))
                }
                Text("保存并进入")
            }
        }
    }
}
