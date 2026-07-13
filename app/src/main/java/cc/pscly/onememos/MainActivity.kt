package cc.pscly.onememos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.pscly.onememos.core.performance.requestMaxRefreshRate
import cc.pscly.onememos.ui.AppViewModel
import cc.pscly.onememos.ui.OneMemosApp
import cc.pscly.onememos.ui.theme.OneMemosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private var startEditorUuid: String? by mutableStateOf(null)
    private var startRoute: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        enableEdgeToEdge()
        startEditorUuid = intent.getStringExtra(EXTRA_START_EDITOR_UUID)
        startRoute = intent.getStringExtra(EXTRA_START_ROUTE)
        val activity = this
        setContent {
            // 某些 ROM/依赖组合下，lifecycle-compose 的 LocalLifecycleOwner 可能未被自动注入，显式提供以避免启动崩溃。
            CompositionLocalProvider(LocalLifecycleOwner provides activity) {
                val themeConfig = appViewModel.themeConfig.collectAsStateWithLifecycle(lifecycleOwner = activity).value
                OneMemosTheme(config = themeConfig) {
                    OneMemosApp(
                        appViewModel = appViewModel,
                        startEditorUuid = startEditorUuid,
                        onStartEditorHandled = { startEditorUuid = null },
                        startRoute = startRoute,
                        onStartRouteHandled = { startRoute = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.checkForUpdatesAutomatically()
    }

    override fun onResume() {
        super.onResume()
        appViewModel.onHostResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startEditorUuid = intent.getStringExtra(EXTRA_START_EDITOR_UUID)
        startRoute = intent.getStringExtra(EXTRA_START_ROUTE)
    }

    companion object {
        const val EXTRA_START_EDITOR_UUID = "cc.pscly.onememos.extra.START_EDITOR_UUID"
        const val EXTRA_START_ROUTE = "cc.pscly.onememos.extra.START_ROUTE"
    }
}
