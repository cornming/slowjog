package com.cornming.slowjog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.cornming.slowjog.data.WeekDots
import com.cornming.slowjog.plan.Announcements
import com.cornming.slowjog.scenery.SceneryLogic
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
import com.cornming.slowjog.data.Habit
import com.cornming.slowjog.data.Stats
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.metronome.MetronomeEngine
import com.cornming.slowjog.metronome.TapTempo
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.settings.MusicMode
import com.cornming.slowjog.spotify.SpotifyLink
import com.cornming.slowjog.spotify.SpotifyPlayer
import com.cornming.slowjog.workout.Distance
import com.cornming.slowjog.workout.TargetType
import com.cornming.slowjog.workout.WorkoutController
import java.time.LocalDate
import kotlin.math.roundToInt

/** 首頁：今天的課表與陪跑夥伴、開始運動、本週進度與累計、目標與節拍器。顏色跟著介面風格。 */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val state by MetronomeController.state.collectAsStateWithLifecycle()
    val records by rememberRecords()
    val today = remember { LocalDate.now() }
    val stats = remember(records) { Stats.compute(records, today) }
    val tapTempo = remember { TapTempo() }
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    var askMusic by remember { mutableStateOf(false) }
    val versionName = remember { com.cornming.slowjog.update.UpdateController.currentVersion(context) }

    val startRequested by StartRequest.pending.collectAsStateWithLifecycle()

    val startPreview = rememberWithNotificationPermission { MetronomeController.start(context) }

    // 開始運動；需要音樂時開啟 Spotify 的歌單頁（立刻開啟，沒有等待）。
    fun startWorkout(playMusic: Boolean) {
        WorkoutController.start(context, musicRequested = playMusic)
        if (playMusic) {
            val playlist = SpotifyLink.pick(settings.playlistUris, settings.lastPlaylistUri, kotlin.random.Random.nextDouble())
            AppSettings.setLastPlaylist(playlist)
            val message = when (val outcome = SpotifyPlayer.open(context, playlist)) {
                is SpotifyPlayer.Outcome.Opened -> context.getString(R.string.music_opened)
                is SpotifyPlayer.Outcome.OpenedInBrowser -> context.getString(R.string.music_opened_browser)
                is SpotifyPlayer.Outcome.Failed -> outcome.reason
            }
            android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 按下「開始運動」，依音樂設定決定直接開始或先詢問。
    fun onStartClicked() {
        when {
            settings.playlistUris.isEmpty() || settings.musicMode == MusicMode.NEVER -> startWorkout(false)
            settings.musicMode == MusicMode.ALWAYS -> startWorkout(true)
            else -> askMusic = true
        }
    }

    // 來自提醒通知的「開始運動」。
    LaunchedEffect(startRequested) {
        if (startRequested) {
            StartRequest.clear()
            onStartClicked()
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

    val plan = if (settings.planEnabled) settings.plan().takeIf { it.stages.isNotEmpty() } else null
    val doneDays = remember(records) { records.map { it.dateEpochDay }.toSet() }
    val weekCount = Habit.weekCount(doneDays, today.toEpochDay())
    val score = (Habit.score(doneDays, today.toEpochDay(), settings.weeklyTargetDays) * 100).roundToInt()
    val totalMeters = remember(records, settings.strideCm) { WeekDots.totalMeters(records, settings.strideCm) }
    val scene = SceneryLogic.sceneFor(settings.scenePref, today.toEpochDay())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 標題列：日期、問候、連續天數
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.home_date,
                        today.monthValue,
                        today.dayOfMonth,
                        today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.TAIWAN),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkSoft,
                )
                Text(
                    text = stringResource(if (stats.doneToday) R.string.today_done else R.string.home_greeting),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = p.ink,
                )
            }
            Pill {
                Icon(AppIcons.Flame, contentDescription = null, tint = p.accentText, modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.streak_pill, stats.streakDays),
                    style = MaterialTheme.typography.labelLarge,
                    color = p.ink,
                )
            }
        }

        // 主卡：今天的課表與陪跑夥伴
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(p.accentSoft),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 24.dp)
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(p.sun),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .fillMaxWidth(0.55f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(if (plan != null) R.string.home_plan_label else R.string.home_free_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = p.accentText,
                )
                val minutes = plan?.let { (it.totalMs / 60_000).toInt() } ?: settings.targetMinutes
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (minutes > 0) minutes.toString() else "∞",
                        fontSize = 60.sp,
                        lineHeight = 60.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = p.numbers,
                        color = p.ink,
                    )
                    Text(
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        text = stringResource(R.string.minutes_unit),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = p.ink,
                    )
                }
                Text(
                    text = stringResource(if (plan != null) R.string.home_plan_flow else R.string.home_free_flow),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkSoft,
                )
            }
            MascotIdleImage(
                style = settings.mascotStyle,
                contentDescription = settings.mascotStyle.nameZh,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp)
                    .height(196.dp),
            )
        }

        // 課表比例條
        if (plan != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (stage in plan.stages) {
                        Box(
                            modifier = Modifier
                                .weight(stage.minutes.toFloat())
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(p.stages[stage.kind.ordinal]),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (stage in plan.stages) {
                        Text(
                            modifier = Modifier.weight(stage.minutes.toFloat()),
                            text = "${Announcements.stageName(stage.kind)} ${stage.minutes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.inkSoft,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 開始運動
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                onClick = { onStartClicked() },
            ) {
                Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = stringResource(R.string.workout_start),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
            }
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(
                    R.string.home_caption,
                    state.bpm,
                    if (settings.playlistUris.isEmpty()) {
                        stringResource(R.string.home_music_none)
                    } else {
                        stringResource(R.string.home_music_count, settings.playlistUris.size)
                    },
                    scene.nameZh,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = p.inkSoft,
                textAlign = TextAlign.Center,
            )
        }

        // 本週
        Panel {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.stat_week),
                    style = MaterialTheme.typography.titleSmall,
                    color = p.ink,
                )
                Text(
                    text = stringResource(R.string.week_format, weekCount, settings.weeklyTargetDays),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = p.numbers,
                    color = p.ink,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (dot in WeekDots.of(doneDays, today)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (dot.done) p.accent else p.line.copy(alpha = if (p.dark) 1f else 0.6f))
                                .then(if (dot.today && !dot.done) Modifier.border(2.dp, p.accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (dot.done) Icon(AppIcons.Check, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = dot.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (dot.today) FontWeight.Bold else FontWeight.Normal,
                            color = if (dot.today) p.ink else p.inkSoft,
                        )
                    }
                }
            }
        }

        // 累計
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), stringResource(R.string.stat_total_jog), formatTotal(stats.totalJogSec))
            Tile(Modifier.weight(1f), stringResource(R.string.stat_distance), formatDistance(totalMeters))
            Tile(Modifier.weight(1f), stringResource(R.string.stat_score), stringResource(R.string.score_format, score))
        }

        GoalCard(bpm = state.bpm)

        // 節拍器：調整 BPM、跟著拍、音量、試聽
        Panel {
            Text(text = stringResource(R.string.metronome_title), style = MaterialTheme.typography.titleSmall, color = p.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(p.accentSoft)
                        .drawBehind { drawRect(p.accent.copy(alpha = flash.value * 0.6f)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = state.bpm.toString(), fontSize = 36.sp, fontWeight = FontWeight.Black, fontFamily = p.numbers, color = p.ink)
                        Text(text = stringResource(R.string.bpm_unit), style = MaterialTheme.typography.labelSmall, color = p.inkSoft)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { MetronomeController.setBpm(state.bpm - 1) }) { Text(stringResource(R.string.bpm_minus)) }
                        OutlinedButton(onClick = { MetronomeController.setBpm(state.bpm + 1) }) { Text(stringResource(R.string.bpm_plus)) }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { tapTempo.tap(SystemClock.elapsedRealtime())?.let { MetronomeController.setBpm(it) } },
                    ) { Text(stringResource(R.string.tap_tempo)) }
                }
            }
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { MetronomeController.setBpm(it.roundToInt()) },
                valueRange = MetronomeEngine.MIN_BPM.toFloat()..MetronomeEngine.MAX_BPM.toFloat(),
            )
            Text(text = stringResource(R.string.tap_tempo_hint), style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
            Text(text = stringResource(R.string.volume), style = MaterialTheme.typography.labelLarge, color = p.ink)
            Slider(value = state.volume, onValueChange = { MetronomeController.setVolume(it) }, valueRange = 0f..1f)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (state.running) MetronomeController.stop(context) else startPreview() },
            ) { Text(stringResource(if (state.running) R.string.preview_stop else R.string.preview_start)) }
        }

        UpdateSection(versionName = versionName)
    }

    if (askMusic) {
        AlertDialog(
            onDismissRequest = { askMusic = false },
            title = { Text(stringResource(R.string.music_ask_title)) },
            text = {
                Text(stringResource(R.string.music_ask_text))
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

/** 圓角白底（深色風格為深灰）區塊。 */
@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(p.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** 小膠囊（連續天數、階段標籤）。 */
@Composable
internal fun Pill(color: Color? = null, content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color ?: p.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

/** 數字小卡：上面標籤、下面大數字。 */
@Composable
internal fun Tile(modifier: Modifier, label: String, value: String, valueColor: Color? = null) {
    val p = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = p.inkSoft, maxLines = 1)
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = p.numbers, color = valueColor ?: p.ink, maxLines = 1)
    }
}

/** 今日目標：在首頁直接選「時間」或「距離」並用拉桿設定，馬上生效、下次開始運動時採用。 */
@Composable
private fun GoalCard(bpm: Int) {
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    run {
        Panel {
            Text(text = stringResource(R.string.goal_title), style = MaterialTheme.typography.titleSmall, color = LocalPalette.current.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeButton(R.string.goal_type_time, settings.targetType == TargetType.TIME) {
                    AppSettings.setTargetType(TargetType.TIME)
                }
                TypeButton(R.string.goal_type_distance, settings.targetType == TargetType.DISTANCE) {
                    AppSettings.setTargetType(TargetType.DISTANCE)
                }
            }
            when (settings.targetType) {
                TargetType.TIME -> {
                    Text(
                        text = if (settings.targetMinutes > 0) {
                            stringResource(R.string.goal_time_value, settings.targetMinutes)
                        } else {
                            stringResource(R.string.goal_off)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = settings.targetMinutes.toFloat(),
                        onValueChange = { AppSettings.setTargetMinutes((it / 5f).roundToInt() * 5) },
                        valueRange = 0f..90f,
                        steps = 17,
                    )
                    Text(text = stringResource(R.string.goal_time_help), style = MaterialTheme.typography.bodySmall)
                }
                TargetType.DISTANCE -> {
                    Text(
                        text = if (settings.targetDistanceM > 0) {
                            stringResource(R.string.goal_distance_value, formatDistance(settings.targetDistanceM.toDouble()))
                        } else {
                            stringResource(R.string.goal_off)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Slider(
                        value = settings.targetDistanceM.toFloat(),
                        onValueChange = { AppSettings.setTargetDistanceM((it / 250f).roundToInt() * 250) },
                        valueRange = 0f..10_000f,
                        steps = 39,
                    )
                    val minutes = Distance.minutesFor(settings.targetDistanceM, bpm, settings.strideCm)
                    Text(
                        text = stringResource(
                            R.string.goal_distance_help,
                            bpm,
                            settings.strideCm,
                            Distance.metersPerMinute(bpm, settings.strideCm).roundToInt(),
                        ) + (minutes?.let { stringResource(R.string.goal_distance_minutes, it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeButton(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(stringResource(labelRes)) }
    } else {
        OutlinedButton(onClick = onClick) { Text(stringResource(labelRes)) }
    }
}

@Composable
internal fun StatItem(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

