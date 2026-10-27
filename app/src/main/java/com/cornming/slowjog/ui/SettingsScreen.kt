package com.cornming.slowjog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.R
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.settings.MusicMode
import com.cornming.slowjog.spotify.SpotifyLink
import com.cornming.slowjog.reminder.ReminderScheduler
import com.cornming.slowjog.sound.CompletionChime
import com.cornming.slowjog.spotify.SpotifyPlayer
import com.cornming.slowjog.workout.Distance
import kotlin.math.roundToInt

/** 設定分頁：到達目標時的行為與步幅、運動音樂（Spotify 歌單與開始運動時的行為）。目標本身在首頁設定。 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    var linkText by remember {
        mutableStateOf(settings.playlistUri?.let { SpotifyLink.webUrl(it) }.orEmpty())
    }
    val parsed = SpotifyLink.parsePlaylist(linkText)
    val linkBad = linkText.isNotBlank() && parsed == null
    var testStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Text(text = stringResource(R.string.settings_reminder_section), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.settings_reminder_on),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = settings.reminderEnabled,
                onCheckedChange = {
                    AppSettings.setReminderEnabled(it)
                    ReminderScheduler.schedule(context)
                },
            )
        }
        if (settings.reminderEnabled) {
            Text(
                text = stringResource(R.string.settings_reminder_time, settings.reminderHour, settings.reminderMinute),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.reminderHour.toFloat(),
                onValueChange = { AppSettings.setReminderTime(it.roundToInt(), settings.reminderMinute) },
                onValueChangeFinished = { ReminderScheduler.schedule(context) },
                valueRange = 0f..23f,
                steps = 22,
            )
            Slider(
                value = settings.reminderMinute.toFloat(),
                onValueChange = { AppSettings.setReminderTime(settings.reminderHour, (it / 5f).roundToInt() * 5) },
                onValueChangeFinished = { ReminderScheduler.schedule(context) },
                valueRange = 0f..55f,
                steps = 10,
            )
        }
        Text(
            text = stringResource(R.string.settings_reminder_help, 3),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(text = stringResource(R.string.settings_target_section), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(R.string.settings_target_where), style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.settings_auto_stop),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(checked = settings.autoStopAtTarget, onCheckedChange = { AppSettings.setAutoStopAtTarget(it) })
        }
        Text(text = stringResource(R.string.settings_auto_stop_help), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { CompletionChime.play() },
        ) { Text(stringResource(R.string.settings_chime_test)) }

        Column {
            Text(
                text = stringResource(R.string.settings_stride_value, settings.strideCm),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.strideCm.toFloat(),
                onValueChange = { AppSettings.setStrideCm((it / 5f).roundToInt() * 5) },
                valueRange = Distance.MIN_STRIDE_CM.toFloat()..Distance.MAX_STRIDE_CM.toFloat(),
                steps = (Distance.MAX_STRIDE_CM - Distance.MIN_STRIDE_CM) / 5 - 1,
            )
        }
        Text(text = stringResource(R.string.settings_stride_help), style = MaterialTheme.typography.bodySmall)

        Text(text = stringResource(R.string.settings_music_section), style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = linkText,
            onValueChange = {
                linkText = it
                when {
                    it.isBlank() -> AppSettings.setPlaylistUri(null)
                    else -> SpotifyLink.parsePlaylist(it)?.let { uri -> AppSettings.setPlaylistUri(uri) }
                }
            },
            label = { Text(stringResource(R.string.settings_playlist_label)) },
            singleLine = true,
            isError = linkBad,
            supportingText = {
                Text(
                    stringResource(
                        when {
                            linkText.isBlank() -> R.string.settings_playlist_empty
                            linkBad -> R.string.settings_playlist_bad
                            else -> R.string.settings_playlist_ok
                        },
                    ),
                )
            },
        )
        Text(text = stringResource(R.string.settings_playlist_help), style = MaterialTheme.typography.bodySmall)

        Column {
            Text(text = stringResource(R.string.settings_mode_title), style = MaterialTheme.typography.titleSmall)
            ModeOption(R.string.settings_mode_ask, settings.musicMode == MusicMode.ASK) {
                AppSettings.setMusicMode(MusicMode.ASK)
            }
            ModeOption(R.string.settings_mode_always, settings.musicMode == MusicMode.ALWAYS) {
                AppSettings.setMusicMode(MusicMode.ALWAYS)
            }
            ModeOption(R.string.settings_mode_never, settings.musicMode == MusicMode.NEVER) {
                AppSettings.setMusicMode(MusicMode.NEVER)
            }
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.playlistUri != null,
            onClick = {
                testStatus = when (val outcome = SpotifyPlayer.open(context, settings.playlistUri)) {
                    is SpotifyPlayer.Outcome.Opened -> context.getString(R.string.music_opened)
                    is SpotifyPlayer.Outcome.OpenedInBrowser -> context.getString(R.string.music_opened_browser)
                    is SpotifyPlayer.Outcome.Failed -> outcome.reason
                }
            },
        ) { Text(stringResource(R.string.settings_test_play)) }
        testStatus?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ModeOption(labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
