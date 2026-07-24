package cc.pscly.onememos.ui.feature.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.settings.AccountSyncHealth
import cc.pscly.onememos.domain.settings.AccountSyncSettingsCommand
import cc.pscly.onememos.domain.settings.AccountSyncSettingsSnapshot
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import cc.pscly.onememos.feature.settings.R
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.feature.settings.common.SettingsConfirmation
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.theme.PaperInkAlertDialog

@Composable
fun AccountManagementScreen(
    onBack: () -> Unit,
    confirmation: SettingsConfirmation?,
    onDismissConfirmation: () -> Unit,
    viewModel: AccountSyncViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshot = uiState.snapshot
    if (snapshot == null) {
        AccountPaperPage {
            AccountHeader(
                title = stringResource(R.string.settings_account_management_page_title),
                onBack = onBack,
            )
            SummaryBlock(
                title = stringResource(R.string.settings_account_management_status_title),
                value = stringResource(R.string.settings_account_loading),
            )
        }
        return
    }
    AccountManagementContent(
        snapshot = snapshot,
        showLogoutConfirmation = confirmation == SettingsConfirmation.LOGOUT,
        onBack = onBack,
        onChangePassword = viewModel::changePassword,
        onRequestLogout = viewModel::requestLogout,
        onDismissLogout = onDismissConfirmation,
        onConfirmLogout = {
            onDismissConfirmation()
            viewModel.confirmLogout()
        },
        passwordError = uiState.passwordError,
        logoutError = uiState.logoutError,
        passwordSuccessGeneration = uiState.passwordSuccessGeneration,
    )
}

@Composable
fun AccountManagementContent(
    snapshot: AccountSyncSettingsSnapshot,
    onBack: () -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
    showLogoutConfirmation: Boolean = false,
    onRequestLogout: () -> Unit = {},
    onDismissLogout: () -> Unit = {},
    passwordError: SettingsCapabilityError? = null,
    logoutError: SettingsCapabilityError? = null,
    passwordSuccessGeneration: Int = 0,
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var repeatedPassword by remember { mutableStateOf("") }
    val currentPasswordFocusRequester = remember { FocusRequester() }
    val logoutFocusRequester = remember { FocusRequester() }
    var restoreLogoutFocus by remember { mutableStateOf(false) }
    LaunchedEffect(showLogoutConfirmation, restoreLogoutFocus) {
        if (!showLogoutConfirmation && restoreLogoutFocus) {
            logoutFocusRequester.requestFocus()
            restoreLogoutFocus = false
        }
    }
    LaunchedEffect(passwordError) {
        if (passwordError != null) {
            currentPasswordFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(passwordSuccessGeneration) {
        if (passwordSuccessGeneration > 0) {
            currentPassword = ""
            newPassword = ""
            repeatedPassword = ""
        }
    }

    val passwordInFlight = snapshot.commandInFlight is AccountSyncSettingsCommand.ChangePassword
    val canChangePassword = snapshot.health.canChangePassword() && snapshot.commandInFlight == null
    val inputReady =
        currentPassword.isNotBlank() &&
            newPassword.length >= 6 &&
            newPassword.toByteArray(Charsets.UTF_8).size <= 71 &&
            newPassword == repeatedPassword
    val passwordActionEnabled = canChangePassword && inputReady
    val passwordActionState =
        when {
            passwordInFlight -> stringResource(R.string.settings_account_password_saving)
            passwordActionEnabled -> stringResource(R.string.settings_account_password_save)
            else -> stringResource(R.string.settings_account_password_description)
        }
    val canLogout = snapshot.health.canLogout() && snapshot.commandInFlight == null
    val logoutEnabledState = stringResource(R.string.settings_account_logout_action)
    val logoutDisabledState = stringResource(R.string.settings_account_logout_description)

    AccountPaperPage(modifier = modifier) {
        AccountHeader(
            title = stringResource(R.string.settings_account_management_page_title),
            onBack = onBack,
        )
        SummaryBlock(
            title = stringResource(R.string.settings_account_management_status_title),
            value = accountSummary(snapshot),
        )
        InkCard {
            Text(
                text = stringResource(R.string.settings_account_password_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_account_password_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = InkSpacing.X8),
            )
            Column(
                modifier = Modifier.padding(top = InkSpacing.X10),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
            ) {
                PasswordField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = stringResource(R.string.settings_account_password_current),
                    enabled = canChangePassword,
                    modifier =
                        Modifier
                            .focusRequester(currentPasswordFocusRequester)
                            .testTag("settings_account_password_current"),
                )
                PasswordField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.settings_account_password_new),
                    enabled = canChangePassword,
                    modifier = Modifier.testTag("settings_account_password_new"),
                )
                PasswordField(
                    value = repeatedPassword,
                    onValueChange = { repeatedPassword = it },
                    label = stringResource(R.string.settings_account_password_repeat),
                    enabled = canChangePassword,
                    modifier = Modifier.testTag("settings_account_password_repeat"),
                )
                if (passwordError != null) {
                    Text(
                        text = errorText(passwordError),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("settings_account_password_error"),
                    )
                }
                Button(
                    onClick = {
                        onChangePassword(currentPassword, newPassword, repeatedPassword)
                    },
                    enabled = passwordActionEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = InkSpacing.TouchTargetMin)
                            .testTag("settings_account_password_save")
                            .semantics { stateDescription = passwordActionState },
                ) {
                    Text(
                        text =
                            if (passwordInFlight) {
                                stringResource(R.string.settings_account_password_saving)
                            } else {
                                stringResource(R.string.settings_account_password_save)
                            },
                    )
                }
            }
        }
        InkCard {
            Text(
                text = stringResource(R.string.settings_account_logout_action),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.settings_account_logout_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = InkSpacing.X8),
            )
            if (logoutError != null) {
                Text(
                    text = errorText(logoutError),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier
                            .padding(top = InkSpacing.X8)
                            .testTag("settings_account_logout_error"),
                )
            }
            OutlinedButton(
                onClick = {
                    onRequestLogout()
                },
                enabled = canLogout,
                modifier =
                    Modifier
                        .padding(top = InkSpacing.X10)
                        .fillMaxWidth()
                        .heightIn(min = InkSpacing.TouchTargetMin)
                        .focusRequester(logoutFocusRequester)
                        .testTag("settings_account_logout")
                        .semantics {
                            stateDescription =
                                if (canLogout) {
                                    logoutEnabledState
                                } else {
                                    logoutDisabledState
                                }
                        },
            ) {
                Text(text = stringResource(R.string.settings_account_logout_action))
            }
        }
    }

    if (showLogoutConfirmation) {
        PaperInkAlertDialog(
            onDismissRequest = {
                restoreLogoutFocus = true
                onDismissLogout()
            },
            title = { Text(stringResource(R.string.settings_account_logout_dialog_title)) },
            text = { Text(stringResource(R.string.settings_account_logout_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoreLogoutFocus = true
                        onConfirmLogout()
                    },
                ) {
                    Text(stringResource(R.string.settings_account_logout_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restoreLogoutFocus = true
                        onDismissLogout()
                    },
                ) {
                    Text(stringResource(R.string.settings_account_cancel))
                }
            },
            modifier = Modifier.testTag("settings_account_logout_dialog"),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun AccountSyncHealth.canChangePassword(): Boolean =
    this !is AccountSyncHealth.Unbound &&
        this !is AccountSyncHealth.ConfiguredSignedOut &&
        this !is AccountSyncHealth.AuthenticationExpired

private fun AccountSyncHealth.canLogout(): Boolean =
    this !is AccountSyncHealth.Unbound && this !is AccountSyncHealth.ConfiguredSignedOut
