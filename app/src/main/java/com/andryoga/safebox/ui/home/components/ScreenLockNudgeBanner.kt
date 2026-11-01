package com.andryoga.safebox.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.previewHelper.LightDarkModePreview
import com.andryoga.safebox.ui.theme.SafeBoxTheme

/**
 * Home banner shown while the device has no lock screen: without one, a forgotten master password
 * cannot be recovered. *Set up* opens the system security settings; *I accept the risk* hides it
 * for this vault.
 *
 * @param onShow Invoked once each time the banner enters the composition, for analytics.
 */
@Composable
internal fun ScreenLockNudgeBanner(
    onSetUpClick: () -> Unit,
    onAcceptRiskClick: () -> Unit,
    onShow: () -> Unit,
) {
    LaunchedEffect(Unit) { onShow() }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Text(
                text = stringResource(R.string.home_screen_lock_nudge_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onAcceptRiskClick) {
                    Text(text = stringResource(R.string.home_screen_lock_nudge_accept_risk))
                }
                TextButton(onClick = onSetUpClick) {
                    Text(text = stringResource(R.string.home_screen_lock_nudge_set_up))
                }
            }
        }
    }
}

@LightDarkModePreview
@Composable
private fun ScreenLockNudgeBannerPreview() {
    SafeBoxTheme {
        ScreenLockNudgeBanner(onSetUpClick = {}, onAcceptRiskClick = {}, onShow = {})
    }
}
