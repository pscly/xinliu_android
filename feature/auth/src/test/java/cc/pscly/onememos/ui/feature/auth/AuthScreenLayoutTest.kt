package cc.pscly.onememos.ui.feature.auth

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import cc.pscly.onememos.ui.theme.OneMemosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AuthScreenLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun backendFormFields_reachableAtCompactHeightAndLargeFont() {
        render(
            AuthUiState(
                tab = AuthTab.BACKEND,
                backendForm = BackendForm.REGISTER,
                username = "backend-user",
                password = "backend-password",
                confirmPassword = "backend-confirm",
                passwordVisible = true,
            ),
        )

        listOf("backend-user", "backend-password", "backend-confirm").forEach { value ->
            composeRule
                .onNode(hasSetTextAction() and hasText(value))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h250dp-xxhdpi")
    fun customFormFields_reachableAtCompactHeightAndLargeFont() {
        render(
            AuthUiState(
                tab = AuthTab.CUSTOM,
                dev2Unlocked = true,
                customServerUrl = "https://example.test/",
                customToken = "custom-token",
                tokenVisible = true,
            ),
        )

        listOf("https://example.test/", "custom-token").forEach { value ->
            composeRule
                .onNode(hasSetTextAction() and hasText(value))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    private fun render(uiState: AuthUiState) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                OneMemosTheme {
                    AuthScreenContent(
                        uiState = uiState,
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
        }
    }
}
