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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.cornming.slowjog.spotify.SpotifyPlayer

/** 固定的 debug 簽章指紋（keystore/debug.keystore），填在 Spotify 開發者後台。 */
private const val SIGNING_SHA1 = "00:06:F8:50:F7:CA:FC:8B:9C:B0:11:34:52:D7:0C:AC:3B:89:36:D4"

/** 設定分頁：運動音樂（Spotify 歌單、開始運動時的行為、隨機播放、Client ID）。 */
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
        Text(text = stringResource(R.string.settings_target_section), style = MaterialTheme.typography.titleMedium)
        Column {
            Text(
                text = if (settings.targetMinutes > 0) {
                    stringResource(R.string.settings_target_value, settings.targetMinutes)
                } else {
                    stringResource(R.string.settings_target_off)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = settings.targetMinutes.toFloat(),
                onValueChange = { AppSettings.setTargetMinutes((it / 5f).toInt() * 5) },
                valueRange = 0f..90f,
                steps = 17,
            )
        }
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(R.string.settings_shuffle), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = settings.shuffle, onCheckedChange = { AppSettings.setShuffle(it) })
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.playlistUri != null,
            onClick = {
                testStatus = context.getString(R.string.music_connecting)
                SpotifyPlayer.play(context, settings, onProgress = { testStatus = it }) { outcome ->
                    val message = when (outcome) {
                        is SpotifyPlayer.Outcome.Playing -> context.getString(R.string.music_playing)
                        is SpotifyPlayer.Outcome.OpenedSpotify -> outcome.reason
                        is SpotifyPlayer.Outcome.Failed -> outcome.reason
                    }
                    testStatus = message
                    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
                }
            },
        ) { Text(stringResource(R.string.settings_test_play)) }
        testStatus?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = settings.spotifyClientId,
            onValueChange = { AppSettings.setSpotifyClientId(it) },
            label = { Text(stringResource(R.string.settings_client_id_label)) },
            singleLine = true,
        )
        SelectionContainer {
            Text(
                text = stringResource(
                    R.string.settings_client_id_help,
                    SpotifyPlayer.REDIRECT_URI,
                    context.packageName,
                    SIGNING_SHA1,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
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
