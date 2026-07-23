package cc.pscly.onememos.ui.feature.auth

import android.app.Application
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthScreenSemanticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun renderBackendPane() {
        composeRule.setContent {
            OneMemosTheme {
                AuthScreenContent(
                    uiState =
                        AuthUiState(
                            error = "测试错误",
                            tab = AuthTab.BACKEND,
                            username = "",
                            password = "",
                            confirmPassword = "",
                            customServerUrl = "",
                            customToken = "",
                        ),
                    onBack = {},
                    onTabSelected = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onConfirmPasswordChanged = {},
                    onPasswordVisibleToggle = {},
                    onBackendFormSwitch = {},
                    onBackendSubmit = {},
                    onTokenChanged = {},
                    onTokenVisibleToggle = {},
                    onCustomServerUrlChanged = {},
                    onCustomSave = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("auth_error_backend", useUnmergedTree = true)
            .assert(errorSemantics("测试错误"))
        composeRule
            .onAllNodesWithTag("auth_error_custom", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun renderCustomPane() {
        composeRule.setContent {
            OneMemosTheme {
                AuthScreenContent(
                    uiState =
                        AuthUiState(
                            error = "测试错误",
                            tab = AuthTab.CUSTOM,
                            username = "",
                            password = "",
                            confirmPassword = "",
                            customServerUrl = "",
                            customToken = "",
                        ),
                    onBack = {},
                    onTabSelected = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onConfirmPasswordChanged = {},
                    onPasswordVisibleToggle = {},
                    onBackendFormSwitch = {},
                    onBackendSubmit = {},
                    onTokenChanged = {},
                    onTokenVisibleToggle = {},
                    onCustomServerUrlChanged = {},
                    onCustomSave = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("auth_error_custom", useUnmergedTree = true)
            .assert(errorSemantics("测试错误"))
        composeRule
            .onAllNodesWithTag("auth_error_backend", useUnmergedTree = true)
            .assertCountEquals(0)
    }

    private fun errorSemantics(message: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Error, message) and
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive,
            )
}
