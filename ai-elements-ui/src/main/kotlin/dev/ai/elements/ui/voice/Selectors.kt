package dev.ai.elements.ui.voice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ai.elements.ui.R

// ---------------------------------------------------------------- MicSelector

/**
 * Pick the microphone (AI Elements `<MicSelector>`): lists the system's input
 * devices — built-in, wired, Bluetooth, USB — and updates as they are plugged
 * in. `null` means the system default. Apply the choice with e.g.
 * `AudioRecord.setPreferredDevice`.
 */
@Composable
fun MicSelector(selected: AudioDeviceInfo?, onSelect: (AudioDeviceInfo?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val devices = rememberInputDevices(context)
    var open by remember { mutableStateOf(false) }
    val default = stringResource(R.string.ai_default_mic)
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text(selected?.let { deviceName(it) } ?: default, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(selected?.let(::deviceIcon) ?: Icons.Outlined.Mic, null, Modifier.size(AssistChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.ai_microphone), Modifier.size(AssistChipDefaults.IconSize)) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.testTag("mic-selector"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(default) },
                leadingIcon = { Icon(Icons.Outlined.Mic, null) },
                trailingIcon = if (selected == null) ({ Icon(Icons.Outlined.Check, null) }) else null,
                onClick = { onSelect(null); open = false },
            )
            devices.forEach { d ->
                DropdownMenuItem(
                    text = { Text(deviceName(d)) },
                    leadingIcon = { Icon(deviceIcon(d), null) },
                    trailingIcon = if (selected?.id == d.id) ({ Icon(Icons.Outlined.Check, null) }) else null,
                    onClick = { onSelect(d); open = false },
                )
            }
        }
    }
}

@Composable
private fun rememberInputDevices(context: Context): List<AudioDeviceInfo> {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var devices by remember { mutableStateOf(audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { devices = audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList() }
        }
        audio.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    // Telephony and loopback inputs aren't microphones a user would pick.
    return devices.filter { it.type !in setOf(AudioDeviceInfo.TYPE_TELEPHONY, AudioDeviceInfo.TYPE_REMOTE_SUBMIX, AudioDeviceInfo.TYPE_FM_TUNER) }
}

@Composable
private fun deviceName(d: AudioDeviceInfo): String {
    val name = d.productName?.toString().orEmpty()
    val kind = stringResource(
        when (d.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> R.string.ai_mic_builtin
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> R.string.ai_mic_headset
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> R.string.ai_mic_bluetooth
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.ai_mic_usb
            else -> if (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) R.string.ai_mic_bluetooth else R.string.ai_microphone
        },
    )
    // Several built-in mics (top / bottom / back) are told apart by address (API 28+).
    val address = if (Build.VERSION.SDK_INT >= 28 && d.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && d.address.isNotBlank()) " · ${d.address}" else ""
    return if (name.isBlank()) kind + address else "$name ($kind)$address"
}

private fun deviceIcon(d: AudioDeviceInfo): ImageVector = when (d.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> Icons.Outlined.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> Icons.Outlined.Headset
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> Icons.Outlined.Usb
    else -> Icons.Outlined.Mic
}

// ---------------------------------------------------------------- VoiceSelector

/** A text-to-speech voice in [VoiceSelector]. */
@Immutable
data class VoiceOption(
    val id: String,
    val name: String,
    val description: String? = null,
    /** e.g. "en-US", "zh-CN". */
    val language: String? = null,
    /** A sample to play (URL or content URI). */
    val previewUrl: String? = null,
)

/**
 * Pick a text-to-speech voice (AI Elements `<VoiceSelector>`): a chip opens a
 * searchable sheet; voices with a [VoiceOption.previewUrl] can be heard
 * before choosing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(voices: List<VoiceOption>, selectedId: String?, onSelect: (VoiceOption) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = voices.firstOrNull { it.id == selectedId }
    AssistChip(
        onClick = { open = true },
        label = { Text(selected?.name ?: stringResource(R.string.ai_voice), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.RecordVoiceOver, null, Modifier.size(AssistChipDefaults.IconSize)) },
        trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(AssistChipDefaults.IconSize)) },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.testTag("voice-selector"),
    )
    if (!open) return
    var previewing by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val query = rememberTextFieldState()
        val q = query.text.toString().trim()
        val shown = voices.filter { q.isEmpty() || it.name.contains(q, true) || it.language.orEmpty().contains(q, true) || it.description.orEmpty().contains(q, true) }
        Column(Modifier.navigationBarsPadding()) {
            SearchBarDefaults.InputField(
                state = query, onSearch = {}, expanded = false, onExpandedChange = {},
                placeholder = { Text(stringResource(R.string.ai_search_voices)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (shown.isEmpty()) Text(stringResource(R.string.ai_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            LazyColumn {
                items(shown, key = { it.id }) { v ->
                    ListItem(
                        onClick = { onSelect(v); open = false },
                        selected = v.id == selectedId,
                        supportingContent = listOfNotNull(v.language, v.description).joinToString(" · ").let { sub ->
                            if (sub.isNotEmpty()) ({ Text(sub) }) else null
                        },
                        leadingContent = v.previewUrl?.let { url ->
                            {
                                val playing = previewing == v.id
                                IconButton(onClick = { previewing = if (playing) null else v.id }) {
                                    Icon(if (playing) Icons.Outlined.StopCircle else Icons.Outlined.PlayCircle, stringResource(R.string.ai_preview_voice))
                                }
                                if (playing) VoicePreview(url) { previewing = null }
                            }
                        },
                        trailingContent = if (v.id == selectedId) ({ Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    ) { Text(v.name) }
                }
            }
        }
    }
}

/** Plays [url] once while composed, then calls [onDone]. */
@Composable
private fun VoicePreview(url: String, onDone: () -> Unit) {
    val state = rememberAudioPlayerState(url)
    var started by remember(url) { mutableStateOf(false) }
    LaunchedEffect(state.isReady) {
        if (state.isReady && !started) { state.play(); started = true }
    }
    LaunchedEffect(started, state.isPlaying, state.error) {
        if ((started && !state.isPlaying) || state.error != null) onDone()
    }
}
