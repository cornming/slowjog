package com.cornming.slowjog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import com.cornming.slowjog.R
import com.cornming.slowjog.data.Stats
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.metronome.MetronomeEngine
import com.cornming.slowjog.metronome.TapTempo
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.settings.MusicMode
import com.cornming.slowjog.spotify.SpotifyPlayer
import com.cornming.slowjog.workout.WorkoutController
import java.time.LocalDate
import kotlin.math.roundToInt

/** 首頁：今天的狀態與累計、BPM 設定、試聽節拍、開始運動。 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by MetronomeController.state.collectAsStateWithLifecycle()
    val records by rememberRecords()
    val stats = remember(records) { Stats.compute(records, LocalDate.now()) }
    val tapTempo = remember { TapTempo() }
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    var askMusic by remember { mutableStateOf(false) }
    val versionName = remember { com.cornming.slowjog.update.UpdateController.currentVersion(context) }

    val startPreview = rememberWithNotificationPermission { MetronomeController.start(context) }

    // 開始運動；需要時請 Spotify 播放歌單，結果以提示訊息告知。
    fun startWorkout(playMusic: Boolean) {
        WorkoutController.start(context)
        if (playMusic) {
            android.widget.Toast.makeText(context.applicationContext, R.string.music_connecting, android.widget.Toast.LENGTH_SHORT).show()
            SpotifyPlayer.play(context, settings) { outcome ->
                val message = when (outcome) {
                    is SpotifyPlayer.Outcome.Playing -> context.getString(R.string.music_playing)
                    is SpotifyPlayer.Outcome.OpenedSpotify -> outcome.reason
                    is SpotifyPlayer.Outcome.Failed -> outcome.reason
                }
                android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // 試聽節拍時保持螢幕恆亮，停止後恢復系統設定。
    KeepScreenOn(enabled = state.running)

    // 每一拍讓圓形閃一下。
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.beat) {
        if (state.running && state.beat > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 180))
        }
    }

    val flashColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
        )

        SummaryCard(stats)

        // 節拍視覺閃動：圓形在每一拍亮起再淡出，環境吵或音量小時也能對拍。
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .background(baseColor)
                .drawBehind { drawRect(flashColor.copy(alpha = flash.value * 0.6f)) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.bpm.toString(),
                    fontSize = 56.sp,
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = stringResource(R.string.bpm_unit),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { MetronomeController.setBpm(state.bpm - 1) }) {
                    Text(stringResource(R.string.bpm_minus))
                }
                Slider(
                    modifier = Modifier.weight(1f),
                    value = state.bpm.toFloat(),
                    onValueChange = { MetronomeController.setBpm(it.roundToInt()) },
                    valueRange = MetronomeEngine.MIN_BPM.toFloat()..MetronomeEngine.MAX_BPM.toFloat(),
                )
                OutlinedButton(onClick = { MetronomeController.setBpm(state.bpm + 1) }) {
                    Text(stringResource(R.string.bpm_plus))
                }
            }

            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = {
                    tapTempo.tap(SystemClock.elapsedRealtime())?.let { MetronomeController.setBpm(it) }
                },
            ) {
                Text(stringResource(R.string.tap_tempo))
            }
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = stringResource(R.string.tap_tempo_hint),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                text = stringResource(R.string.volume),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.volume,
                onValueChange = { MetronomeController.setVolume(it) },
                valueRange = 0f..1f,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (state.running) MetronomeController.stop(context) else startPreview()
                },
            ) {
                Text(stringResource(if (state.running) R.string.preview_stop else R.string.preview_start))
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(56.dp),
                onClick = {
                    when {
                        settings.playlistUri == null || settings.musicMode == MusicMode.NEVER -> startWorkout(false)
                        settings.musicMode == MusicMode.ALWAYS -> startWorkout(true)
                        else -> askMusic = true
                    }
                },
            ) {
                Text(
                    text = stringResource(R.string.workout_start),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            UpdateSection(versionName = versionName, modifier = Modifier.padding(top = 8.dp))
        }
    }

    if (askMusic) {
        AlertDialog(
            onDismissRequest = { askMusic = false },
            title = { Text(stringResource(R.string.music_ask_title)) },
            text = {
                Text(
                    stringResource(
                        if (settings.shuffle) R.string.music_ask_text_shuffle else R.string.music_ask_text,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    askMusic = false
                    startWorkout(true)
                }) { Text(stringResource(R.string.music_play)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askMusic = false
                    startWorkout(false)
                }) { Text(stringResource(R.string.music_skip)) }
            },
        )
    }
}

@Composable
private fun SummaryCard(stats: Stats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(if (stats.doneToday) R.string.today_done else R.string.today_not_yet),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(stringResource(R.string.stat_streak), stringResource(R.string.days_format, stats.streakDays))
                StatItem(stringResource(R.string.stat_total_jog), formatTotal(stats.totalJogSec))
                StatItem(stringResource(R.string.stat_count), stringResource(R.string.times_format, stats.count))
            }
        }
    }
}

@Composable
internal fun StatItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

