package dev.ai.elements.demo.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ai.elements.core.config.ProviderProfile
import dev.ai.elements.demo.ChatViewModel
import dev.ai.elements.demo.R
import dev.ai.elements.demo.auth.SignInController.State

/**
 * Sign-in status for an OAuth profile, in place of the API key field:
 * who is signed in, Sign in / Sign out, progress while the browser is open,
 * and the device-code dialog.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@Composable
fun AccountCard(viewModel: ChatViewModel, profile: ProviderProfile, modifier: Modifier = Modifier) {
    val provider = profile.oauth ?: return
    val activity = LocalActivity.current ?: return
    val store = viewModel.providers
    val version by store.tokenVersion.collectAsStateWithLifecycle()
    val state by viewModel.signIn.state.collectAsStateWithLifecycle()
    val tokens = remember(profile.id, version, state) { if (profile.usesTokens) store.tokenStore(profile.id).load() else null }
    val signedIn = if (profile.usesTokens) tokens != null else remember(profile.id, state) { store.apiKey(profile.id).isNotBlank() }
    val mine = when (val s = state) {
        is State.InBrowser -> s.profileId == profile.id
        is State.Device -> s.profileId == profile.id
        is State.Failed -> s.profileId == profile.id
        State.Idle -> false
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(DemoIcons.AccountCircle, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(provider.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            !signedIn -> stringResource(R.string.not_signed_in)
                            !profile.usesTokens -> stringResource(R.string.openrouter_signed_in)
                            tokens?.email != null -> stringResource(R.string.signed_in_as, tokens.email!!)
                            else -> stringResource(R.string.signed_in)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("account-status"),
                    )
                }
            }
            if (provider.experimental) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(DemoIcons.WarningAmber, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.subscription_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                mine && state is State.InBrowser -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.finish_in_browser), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel.signIn::cancel) { Text(stringResource(R.string.cancel)) }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.signIn.signIn(activity, profile) }, modifier = Modifier.testTag("sign-in")) {
                        Icon(DemoIcons.Login, null, Modifier.size(ButtonDefaults.IconSize))
                        Text(
                            stringResource(if (signedIn) R.string.sign_in_again else R.string.sign_in),
                            Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                    if (signedIn) OutlinedButton(onClick = { viewModel.signIn.signOut(profile) }, modifier = Modifier.testTag("sign-out")) {
                        Icon(DemoIcons.Logout, null, Modifier.size(ButtonDefaults.IconSize))
                        Text(stringResource(R.string.sign_out), Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
            (state as? State.Failed)?.takeIf { mine }?.let {
                Text(
                    stringResource(R.string.sign_in_failed, it.message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("sign-in-error"),
                )
            }
        }
    }

    (state as? State.Device)?.takeIf { mine }?.let { device ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(DemoIcons.Login, null) },
            title = { Text(stringResource(R.string.device_title, provider.label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.device_desc), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        device.authorization.userCode,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("device-code"),
                    )
                    TextButton(onClick = { clipboard.setText(AnnotatedString(device.authorization.userCode)) }) {
                        Text(stringResource(R.string.copy_code))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LoadingIndicator(Modifier.size(28.dp))
                        Text(stringResource(R.string.waiting_approval), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.signIn.open(activity, device.authorization.openUrl) }) { Text(stringResource(R.string.open_page)) }
            },
            dismissButton = { TextButton(onClick = viewModel.signIn::cancel) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
