package com.cornming.slowjog.ui

import androidx.compose.foundation.background
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
import com.cornming.slowjog.data.CsvExport
import com.cornming.slowjog.data.Habit
import com.cornming.slowjog.data.RecordRepository
import com.cornming.slowjog.plan.Announcements
import com.cornming.slowjog.plan.StageKind
import com.cornming.slowjog.plan.WorkoutPlan
import com.cornming.slowjog.reminder.ReminderScheduler
import com.cornming.slowjog.sound.ClickSound
import com.cornming.slowjog.sound.ClickSounds
import com.cornming.slowjog.sound.Tones
import com.cornming.slowjog.voice.VoiceAnnouncer
import com.cornming.slowjog.sound.CompletionChime
import com.cornming.slowjog.spotify.SpotifyPlayer
import com.cornming.slowjog.workout.Distance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 設定分頁：到達目標時的行為與步幅、運動音樂（Spotify 歌單與開始運動時的行為）。目標本身在首頁設定。 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    var linkText by remember {
        mutableStateOf(settings.playlistUris.joinToString("\n") { SpotifyLink.webUrl(it) })
    }
    val (parsedPlaylists, badCount) = SpotifyLink.parsePlaylists(linkText)
    val linkBad = badCount > 0
    var testStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LocalPalette.current.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        LookSection(settings)
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

        Text(text = stringResource(R.string.settings_plan_section), style = MaterialTheme.typography.titleMedium)
        SwitchRow(R.string.settings_plan_on, settings.planEnabled) { AppSettings.setPlanEnabled(it) }
        if (settings.planEnabled) {
            val minutes = listOf(settings.planWarmupMin, settings.planJogMin, settings.planStrengthMin, settings.planCooldownMin)
            StageKind.values().forEachIndexed { i, kind ->
                Text(
                    text = stringResource(R.string.settings_plan_stage, Announcements.stageName(kind), minutes[i]),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Slider(
                    value = minutes[i].toFloat(),
                    onValueChange = {
                        val m = minutes.toMutableList().also { list -> list[i] = it.roundToInt() }
                        AppSettings.setPlanMinutes(m[0], m[1], m[2], m[3])
                    },
                    valueRange = 0f..30f,
                    steps = 29,
                )
            }
            Text(
                text = stringResource(R.string.settings_plan_total, minutes.sum()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (settings.planEnabled) SwitchRow(R.string.settings_auto_on, settings.planAutoEnabled) { AppSettings.setPlanAutoEnabled(it) }
        SwitchRow(R.string.settings_cues_on, settings.cuesEnabled) { AppSettings.setCuesEnabled(it) }
        SwitchRow(R.string.settings_voice_on, settings.voiceEnabled) { AppSettings.setVoiceEnabled(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val kind = StageKind.JOG
                    com.cornming.slowjog.plan.StageCue.play(context, kind, sound = true, vibrate = true)
                },
            ) { Text(stringResource(R.string.settings_cue_test)) }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    VoiceAnnouncer.speak(context, Announcements.stageStart(com.cornming.slowjog.plan.Stage(StageKind.JOG, WorkoutPlan.DEFAULT_JOG)))
                },
            ) { Text(stringResource(R.string.settings_voice_test)) }
        }
        Text(text = stringResource(R.string.settings_plan_help), style = MaterialTheme.typography.bodySmall)

        Text(text = stringResource(R.string.settings_sound_section), style = MaterialTheme.typography.titleMedium)
        Column {
            ModeOption(R.string.settings_sound_click, settings.metronomeSound == ClickSound.CLICK) {
                AppSettings.setMetronomeSound(ClickSound.CLICK)
                Tones.play(ClickSounds.samples(ClickSound.CLICK), ClickSounds.SAMPLE_RATE)
            }
            ModeOption(R.string.settings_sound_clap, settings.metronomeSound == ClickSound.CLAP) {
                AppSettings.setMetronomeSound(ClickSound.CLAP)
                Tones.play(ClickSounds.samples(ClickSound.CLAP), ClickSounds.SAMPLE_RATE)
            }
            ModeOption(R.string.settings_sound_snap, settings.metronomeSound == ClickSound.SNAP) {
                AppSettings.setMetronomeSound(ClickSound.SNAP)
                Tones.play(ClickSounds.samples(ClickSound.SNAP), ClickSounds.SAMPLE_RATE)
            }
        }
        Text(text = stringResource(R.string.settings_sound_help), style = MaterialTheme.typography.bodySmall)

        Text(text = stringResource(R.string.settings_habit_section), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.settings_weekly_target, settings.weeklyTargetDays),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = settings.weeklyTargetDays.toFloat(),
            onValueChange = { AppSettings.setWeeklyTargetDays(it.roundToInt()) },
            valueRange = Habit.MIN_WEEKLY_TARGET.toFloat()..Habit.MAX_WEEKLY_TARGET.toFloat(),
            steps = Habit.MAX_WEEKLY_TARGET - Habit.MIN_WEEKLY_TARGET - 1,
        )
        Text(text = stringResource(R.string.settings_habit_help), style = MaterialTheme.typography.bodySmall)

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
                AppSettings.setPlaylistUris(SpotifyLink.parsePlaylists(it).first)
            },
            label = { Text(stringResource(R.string.settings_playlist_label)) },
            minLines = 3,
            isError = linkBad,
            supportingText = {
                Text(
                    when {
                        linkBad -> stringResource(R.string.settings_playlist_bad, badCount)
                        parsedPlaylists.isEmpty() -> stringResource(R.string.settings_playlist_empty)
                        parsedPlaylists.size == 1 -> stringResource(R.string.settings_playlist_one)
                        else -> stringResource(R.string.settings_playlist_many, parsedPlaylists.size)
                    },
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
            enabled = settings.playlistUris.isNotEmpty(),
            onClick = {
                val playlist = SpotifyLink.pick(settings.playlistUris, settings.lastPlaylistUri, kotlin.random.Random.nextDouble())
                AppSettings.setLastPlaylist(playlist)
                testStatus = when (val outcome = SpotifyPlayer.open(context, playlist)) {
                    is SpotifyPlayer.Outcome.Opened -> context.getString(R.string.music_opened)
                    is SpotifyPlayer.Outcome.OpenedInBrowser -> context.getString(R.string.music_opened_browser)
                    is SpotifyPlayer.Outcome.Failed -> outcome.reason
                }
            },
        ) { Text(stringResource(R.string.settings_test_play)) }
        testStatus?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Text(text = stringResource(R.string.settings_data_section), style = MaterialTheme.typography.titleMedium)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val message = try {
                        withContext(Dispatchers.IO) {
                            val csv = CsvExport.build(RecordRepository.observeAll().first())
                            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                                ?: error("無法開啟檔案")
                        }
                        context.getString(R.string.settings_export_done)
                    } catch (e: Exception) {
                        context.getString(R.string.settings_export_failed, e.message ?: e.javaClass.simpleName)
                    }
                    android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { exportLauncher.launch("slowjog-records-${java.time.LocalDate.now()}.csv") },
        ) { Text(stringResource(R.string.settings_export)) }
        Text(text = stringResource(R.string.settings_export_help), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = checked, onCheckedChange = onChange)
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
