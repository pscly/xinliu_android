package cc.pscly.onememos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.core.performance.requestMaxRefreshRate
import cc.pscly.onememos.navigation.ExternalNavigationInput
import cc.pscly.onememos.navigation.ExternalNavigationIntentParser
import cc.pscly.onememos.ui.AppViewModel
import cc.pscly.onememos.ui.OneMemosApp
import cc.pscly.onememos.ui.theme.OneMemosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()
    private var pendingExternalInput: ExternalNavigationInput? by mutableStateOf(null)
    private var initialIntentConsumed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMaxRefreshRate()
        enableEdgeToEdge()

        initialIntentConsumed =
            savedInstanceState?.getBoolean(STATE_INITIAL_INTENT_CONSUMED, false) == true
        if (!initialIntentConsumed) {
            pendingExternalInput = ExternalNavigationIntentParser.parse(intent)
            if (pendingExternalInput != null) {
                Log.d(TAG, "onCreate 解析外部输入：$pendingExternalInput")
            }
        }

        val activity = this
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides activity) {
                val themeConfig =
                    appViewModel.themeConfig.collectAsStateWithLifecycle(lifecycleOwner = activity).value
                OneMemosTheme(config = themeConfig) {
                    OneMemosApp(
                        appViewModel = appViewModel,
                        pendingExternalInput = pendingExternalInput,
                        onExternalInputConsumed = {
                            pendingExternalInput = null
                            initialIntentConsumed = true
                        },
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
        appViewModel.onHostResumed(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // onNewIntent 总是新投递：重置消费标记后再解析。
        initialIntentConsumed = false
        pendingExternalInput = ExternalNavigationIntentParser.parse(intent)
        Log.d(TAG, "onNewIntent 解析外部输入：$pendingExternalInput")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INITIAL_INTENT_CONSUMED, initialIntentConsumed)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val STATE_INITIAL_INTENT_CONSUMED = "one_memos_initial_intent_consumed"

        const val EXTRA_START_EDITOR_UUID = "cc.pscly.onememos.extra.START_EDITOR_UUID"
    }
}
