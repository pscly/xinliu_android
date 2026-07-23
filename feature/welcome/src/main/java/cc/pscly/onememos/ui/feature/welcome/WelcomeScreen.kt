@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar

@Composable
fun WelcomeScreen(
    onEnterLocal: () -> Unit,
    onGoBindServer: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    // 首次引导页禁止返回，避免“没点按钮就进入首页”导致引导失效。
    BackHandler(enabled = true) {}

    WelcomeScreenContent(
        onEnterLocal = {
            viewModel.completeWelcome()
            onEnterLocal()
        },
        onGoBindServer = {
            viewModel.completeWelcome()
            onGoBindServer()
        },
    )
}

/**
 * 欢迎页可测内容层：无 Hilt / ViewModel，便于 compact 视口与大字体布局回归。
 * 滚动状态在内部 [rememberScrollState]，不对外暴露参数。
 */
@Composable
internal fun WelcomeScreenContent(
    onEnterLocal: () -> Unit,
    onGoBindServer: () -> Unit,
) {
    Scaffold(
        topBar = {
            PaperInkTopAppBar(
                title = { Text("心流") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X12)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .testTag("welcome_scroll"),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X16),
        ) {
            InkCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = InkSpacing.X16, vertical = InkSpacing.X14),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
                ) {
                    Text(
                        text = "先离线记录，再登录同步",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "你可以不登录直接开始写随笔；之后可以登录账号（推荐），或填写访问令牌，应用会自动上传并合并到云端。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(InkSpacing.X4))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_enter_local"),
                onClick = onEnterLocal,
            ) {
                Text("立即体验（离线）")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGoBindServer,
            ) {
                Text("登录/同步")
            }
        }
    }
}
