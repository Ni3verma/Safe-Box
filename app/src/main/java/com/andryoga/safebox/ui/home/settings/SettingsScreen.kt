package com.andryoga.safebox.ui.home.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants.APP_BUY_ME_A_COFFEE_URL
import com.andryoga.safebox.common.CommonConstants.APP_GITHUB_URL
import com.andryoga.safebox.common.CommonConstants.APP_PLAYSTORE_LINK
import com.andryoga.safebox.ui.core.LocalDeviceSecurityAuthProvider
import com.andryoga.safebox.ui.core.MyAppTopAppBar
import com.andryoga.safebox.ui.core.TestTags
import com.andryoga.safebox.ui.core.canAuthenticateUsingDeviceSecurity
import com.andryoga.safebox.ui.home.settings.components.BiometricUnlockPreference
import com.andryoga.safebox.ui.home.settings.components.ChangePasswordFlow
import com.andryoga.safebox.ui.home.settings.components.SliderPreference
import com.andryoga.safebox.ui.home.settings.components.SwitchPreference
import com.andryoga.safebox.ui.home.settings.components.TextPreference
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.openExternalUrl
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenRoot() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Enrolment can change while the user is in the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenAction(SettingsScreenAction.OnScreenResumed)
    }

    Scaffold(
        topBar = {
            MyAppTopAppBar(title = { Text(stringResource(R.string.bottom_nav_settings)) })
        },
    ) { innerPadding ->
        SettingsScreen(
            uiState = uiState,
            onScreenAction = {
                // intercept some of the events in UI
                Timber.i("on screen action: ${it::class.simpleName}")
                when (it) {
                    SettingsScreenAction.OpenGithubProject -> openGitHub(context)
                    SettingsScreenAction.OpenBuyMeACoffee -> openBuyMeACoffee(context)
                    SettingsScreenAction.ReviewApp -> launchReview(context)
                    SettingsScreenAction.SendFeedback -> sendFeedback(context)
                    else -> Unit
                }

                // forward events to VM
                viewModel.onScreenAction(it)
            },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onScreenAction: (SettingsScreenAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val biometricAuthProvider = LocalDeviceSecurityAuthProvider.current
    val context = LocalContext.current
    val settings = uiState.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_security_and_privacy),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SwitchPreference(

            title = stringResource(R.string.settings_privacy_enabled_title),
            checked = settings.isPrivacyEnabled,
            onCheckedChange = { onScreenAction(SettingsScreenAction.UpdatePrivacy(it)) },
            body = stringResource(R.string.settings_privacy_enabled_body),
            controlModifier = Modifier.testTag(TestTags.SETTINGS_PRIVACY_SWITCH),
        )

        SwitchPreference(
            title = stringResource(R.string.settings_auto_backup_title),
            checked = settings.autoBackupAfterPasswordLogin,
            onCheckedChange = { onScreenAction(SettingsScreenAction.UpdateAutoBackupAfterLogin(it)) },
            body = stringResource(R.string.settings_auto_backup_body),
            controlModifier = Modifier.testTag(TestTags.SETTINGS_AUTO_BACKUP_SWITCH),
        )

        BiometricUnlockPreference(
            switch = uiState.biometricUnlock,
            onCheckedChange = { onScreenAction(SettingsScreenAction.UpdateBiometricUnlock(it)) },
        )

        SliderPreference(
            value = settings.passwordAfterXBiometricLogins,
            title = stringResource(R.string.settings_ask_for_pswrd_after_biometric_title),
            valueRange = 5f..15f,
            onValueChanged = { onScreenAction(SettingsScreenAction.UpdatePasswordAfterXBiometric(it)) },
            body = stringResource(R.string.settings_ask_for_pswrd_after_biometric_body),
            controlModifier = Modifier.testTag(TestTags.SETTINGS_PASSWORD_AFTER_BIOMETRIC_SLIDER),
        )

        SliderPreference(
            value = settings.awayTimeoutSec,
            title = stringResource(R.string.settings_away_timeout_title),
            valueRange = 5f..20f,
            onValueChanged = { onScreenAction(SettingsScreenAction.UpdateAwayTimeout(it)) },
            body = stringResource(R.string.settings_away_timeout_body),
            controlModifier = Modifier.testTag(TestTags.SETTINGS_AWAY_TIMEOUT_SLIDER),
        )

        TextPreference(
            title = stringResource(R.string.change_master_password),
            body = if (uiState.isLegacySession) {
                stringResource(R.string.settings_change_password_legacy_body)
            } else {
                stringResource(R.string.change_master_password_body)
            },
            onTap = {
                Timber.i("change master password clicked on settings screen, checking device security auth availability")
                onScreenAction(
                    SettingsScreenAction.ChangePasswordClicked(
                        canUseDeviceAuth = canAuthenticateUsingDeviceSecurity(
                            context,
                            biometricAuthProvider,
                            allowDeviceCredential = true,
                        ),
                    ),
                )
            },
            enabled = !uiState.isLegacySession,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_section_support_and_community),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        TextPreference(
            title = stringResource(R.string.settings_feedback_title),
            body = stringResource(R.string.settings_feedback_body),
            onTap = { onScreenAction(SettingsScreenAction.SendFeedback) },
            icon = Icons.Outlined.Email,
        )

        TextPreference(
            title = stringResource(R.string.settings_review_title),
            body = stringResource(R.string.settings_review_body),
            onTap = { onScreenAction(SettingsScreenAction.ReviewApp) },
            icon = Icons.Outlined.RateReview,
        )

        TextPreference(
            title = stringResource(R.string.settings_github_title),
            body = stringResource(R.string.settings_github_body),
            onTap = { onScreenAction(SettingsScreenAction.OpenGithubProject) },
        )

        TextPreference(
            title = stringResource(R.string.settings_support_title),
            body = stringResource(R.string.settings_support_body),
            onTap = { onScreenAction(SettingsScreenAction.OpenBuyMeACoffee) },
            icon = Icons.Outlined.Coffee,
        )
    }

    uiState.changePasswordStep?.let { step ->
        ChangePasswordFlow(step = step, onScreenAction = onScreenAction)
    }

    val passwordChangeResult = uiState.passwordChangeResult
    val successMessage = stringResource(R.string.password_updated_success)
    val failureMessage = stringResource(R.string.password_update_failed)
    LaunchedEffect(passwordChangeResult) {
        if (passwordChangeResult == null) return@LaunchedEffect
        val message = when (passwordChangeResult) {
            PasswordChangeResult.SUCCESS -> successMessage
            PasswordChangeResult.FAILURE -> failureMessage
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onScreenAction(SettingsScreenAction.OnPasswordChangeResultShown)
    }
}

fun sendFeedback(context: Context) {
    val deviceModel = android.os.Build.MODEL
    val androidVersion = android.os.Build.VERSION.RELEASE
    val appVersion = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
        "Unknown"
    }

    val body = """
        
        --- Device Info ---
        Model: $deviceModel
        Android: $androidVersion
        App Version: $appVersion
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_EMAIL, arrayOf("canvas.nv@gmail.com"))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.safe_box_feedback))
        putExtra(Intent.EXTRA_TEXT, body)
    }

    try {
        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.send_feedback_email)
            )
        )
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.no_email_app_found), Toast.LENGTH_SHORT).show()
    }
}

fun launchReview(context: Context) {
    openExternalLinkOrToast(context, APP_PLAYSTORE_LINK)
}

fun openGitHub(context: Context) {
    openExternalLinkOrToast(context, APP_GITHUB_URL)
}

fun openBuyMeACoffee(context: Context) {
    openExternalLinkOrToast(context, APP_BUY_ME_A_COFFEE_URL)
}

private fun openExternalLinkOrToast(context: Context, url: String) {
    if (!context.openExternalUrl(url)) {
        Toast.makeText(
            context,
            context.getString(R.string.no_app_to_open_link),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@LightDarkModePreview
@Composable
private fun SettingsScreenPreview() {
    SafeBoxTheme {
        SettingsScreen(
            uiState = SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Available(isOn = true)),
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun SettingsScreenFingerprintUnavailablePreview() {
    SafeBoxTheme {
        SettingsScreen(
            uiState = SettingsUiState(biometricUnlock = BiometricUnlockSwitch.Unavailable),
            onScreenAction = {},
        )
    }
}

@LightDarkModePreview
@Composable
private fun SettingsScreenLegacySessionPreview() {
    SafeBoxTheme {
        SettingsScreen(
            uiState = SettingsUiState(isLegacySession = true),
            onScreenAction = {},
        )
    }
}
