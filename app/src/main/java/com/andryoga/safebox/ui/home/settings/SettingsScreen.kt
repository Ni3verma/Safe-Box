package com.andryoga.safebox.ui.home.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andryoga.safebox.R
import com.andryoga.safebox.common.CommonConstants.APP_GITHUB_URL
import com.andryoga.safebox.common.CommonConstants.APP_PLAYSTORE_LINK
import com.andryoga.safebox.data.dataStore.Settings
import com.andryoga.safebox.ui.MainViewModel
import com.andryoga.safebox.ui.core.TopAppBarConfig
import com.andryoga.safebox.ui.home.settings.components.SliderPreference
import com.andryoga.safebox.ui.home.settings.components.SwitchPreference
import com.andryoga.safebox.ui.home.settings.components.TextPreference
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme
import com.andryoga.safebox.ui.utils.OnStart
import timber.log.Timber

@Composable
fun SettingsScreenRoot(mainViewModel: MainViewModel) {
    OnStart {
        val config = TopAppBarConfig(
            title = { Text(stringResource(R.string.bottom_nav_settings)) },
        )
        mainViewModel.updateTopBar(config)
    }

    val viewModel = hiltViewModel<SettingsViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    SettingsScreen(uiState = uiState, onScreenAction = {
        // intercept some of the events in UI
        Timber.i("on screen action: ${it::class.simpleName}")
        when (it) {
            SettingsScreenAction.OpenGithubProject -> openGitHub(context)
            SettingsScreenAction.ReviewApp -> launchReview(context)
            SettingsScreenAction.SendFeedback -> sendFeedback(context)
            else -> Unit
        }

        // forward events to VM
        viewModel.onScreenAction(it)
    })
}

@Composable
fun SettingsScreen(uiState: Settings, onScreenAction: (SettingsScreenAction) -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_section_security_and_privacy),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SwitchPreference(
            title = stringResource(R.string.settings_privacy_enabled_title),
            checked = uiState.isPrivacyEnabled,
            onCheckedChange = { onScreenAction(SettingsScreenAction.UpdatePrivacy(it)) },
            body = stringResource(R.string.settings_privacy_enabled_body),
        )

        SwitchPreference(
            title = stringResource(R.string.settings_auto_backup_title),
            checked = uiState.autoBackupAfterPasswordLogin,
            onCheckedChange = { onScreenAction(SettingsScreenAction.UpdateAutoBackupAfterLogin(it)) },
            body = stringResource(R.string.settings_auto_backup_body),
        )

        SliderPreference(
            value = uiState.passwordAfterXBiometricLogins,
            title = stringResource(R.string.settings_ask_for_pswrd_after_biometric_title),
            valueRange = 5f..15f,
            onValueChanged = { onScreenAction(SettingsScreenAction.UpdatePasswordAfterXBiometric(it)) },
            body = stringResource(R.string.settings_ask_for_pswrd_after_biometric_body),
        )

        SliderPreference(
            value = uiState.awayTimeoutSec,
            title = stringResource(R.string.settings_away_timeout_title),
            valueRange = 5f..20f,
            onValueChanged = { onScreenAction(SettingsScreenAction.UpdateAwayTimeout(it)) },
            body = stringResource(R.string.settings_away_timeout_body),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_section_support_and_community),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
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
        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
    }
}

fun launchReview(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, APP_PLAYSTORE_LINK.toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

fun openGitHub(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, APP_GITHUB_URL.toUri())
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

@LightDarkModePreview
@Composable
private fun SettingsScreenPreview() {
    SafeBoxTheme {
        SettingsScreen(Settings(), {})
    }
}
