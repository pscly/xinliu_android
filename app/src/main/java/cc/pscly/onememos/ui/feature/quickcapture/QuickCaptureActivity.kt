package cc.pscly.onememos.ui.feature.quickcapture

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.pscly.onememos.core.performance.requestMaxRefreshRate
import cc.pscly.onememos.ui.AppViewModel
import cc.pscly.onememos.ui.theme.OneMemosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickCaptureActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        enableEdgeToEdge()
        val activity = this
        setContent {
            // 某些 ROM/依赖组合下，lifecycle-compose 的 LocalLifecycleOwner 可能未被自动注入，显式提供以避免启动崩溃。
            CompositionLocalProvider(LocalLifecycleOwner provides activity) {
                val themeConfig = appViewModel.themeConfig.collectAsStateWithLifecycle(lifecycleOwner = activity).value
                OneMemosTheme(config = themeConfig) {
                    QuickCaptureRoute(onClose = { finish() })
                }
            }
        }
    }
}
