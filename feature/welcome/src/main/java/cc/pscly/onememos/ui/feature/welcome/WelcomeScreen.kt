@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cc.pscly.onememos.ui.component.InkCard

@Composable
fun WelcomeScreen(
    onEnterLocal: () -> Unit,
    onGoBindServer: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    // 首次引导页禁止返回，避免“没点按钮就进入首页”导致引导失效。
    BackHandler(enabled = true) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("心流") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InkCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
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

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.completeWelcome()
                    onEnterLocal()
                },
            ) {
                Text("立即体验（离线）")
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.completeWelcome()
                    onGoBindServer()
                },
            ) {
                Text("登录/同步")
            }
        }
    }
}
