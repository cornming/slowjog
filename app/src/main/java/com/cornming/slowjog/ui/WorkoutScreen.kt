package com.cornming.slowjog.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.R
import com.cornming.slowjog.mascot.MoveSchedule
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.plan.Announcements
import com.cornming.slowjog.plan.StageTransition
import com.cornming.slowjog.scenery.SceneryLogic
import com.cornming.slowjog.scenery.SceneryView
import com.cornming.slowjog.settings.AppSettings
import com.cornming.slowjog.workout.TargetTime
import com.cornming.slowjog.workout.TargetType
import com.cornming.slowjog.workout.WorkoutController
import java.time.LocalDate

/**
 * 運動中畫面：目前階段、整段時間、風景與小人物（跑步時風景往後捲）、
 * 換階段前的倒數大字、暖身與伸展的動作說明、節拍調整與控制按鈕。運動中螢幕恆亮。
 */
@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = LocalPalette.current
    val workout by WorkoutController.state.collectAsStateWithLifecycle()
    val cheer by WorkoutController.cheer.collectAsStateWithLifecycle()
    val metronome by MetronomeController.state.collectAsStateWithLifecycle()
    val settings by AppSettings.settings.collectAsStateWithLifecycle()
    val now = rememberNow()
    val elapsed = WorkoutController.elapsedSec(now)
    val jog = WorkoutController.jogSec(now)
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val target = WorkoutController.target()
    val meters = WorkoutController.estimatedMeters(now)
    // 這次運動的風景在開始時決定（每天輪換時同一天固定同一種）。
    val scene = remember { SceneryLogic.sceneFor(settings.scenePref, LocalDate.now().toEpochDay()) }

    // 暖身與伸展階段、而且沒在超慢跑：小人物示範動作。
    val elapsedMs = WorkoutController.elapsedMs(now)
    val plan = WorkoutController.plan()
    val planPosition = plan?.position(elapsedMs)
    val planStage = planPosition?.stage
    val moveStage = if (planStage != null && planPosition != null && !workout.jogging && MoveSchedule.movesFor(planStage.kind).isNotEmpty()) {
        val stageMs = planStage.minutes * 60_000L
        MoveStage(planStage.kind, stageMs, stageMs - planPosition.remainingMs)
    } else {
        null
    }
    val moveSlot = moveStage?.let { MoveSchedule.at(it.kind, it.stageMs, it.elapsedMs) }
    val nextStage = planPosition?.let { plan?.next(it.index) }
    val countdown = if (planPosition != null && WorkoutController.autoAdvance()) {
        StageTransition.countdown(planPosition.remainingMs, nextStage != null)
    } else {
        null
    }

    KeepScreenOn(enabled = true)

    val startJog = rememberWithNotificationPermission { WorkoutController.startJog(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 目前階段與下一段
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Pill(color = if (workout.running) p.accent else p.surface) {
                val text = when {
                    !workout.running -> stringResource(R.string.workout_paused)
                    planStage != null && planPosition != null -> stringResource(
                        R.string.workout_stage,
                        Announcements.stageName(planStage.kind),
                        formatClock(((planPosition.remainingMs + 999) / 1000).toInt()),
                    )
                    plan != null -> stringResource(R.string.workout_plan_done)
                    workout.jogging -> stringResource(R.string.workout_jogging)
                    else -> stringResource(R.string.workout_status_free)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (workout.running) p.onAccent else p.ink,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (nextStage != null) {
                Text(
                    text = stringResource(R.string.workout_stage_next, Announcements.stageName(nextStage.kind), nextStage.minutes),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.inkSoft,
                )
            }
        }

        // 整段時間與目標
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.workout_total_time), style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
            Text(
                text = formatClock(elapsed),
                fontSize = 72.sp,
                lineHeight = 72.sp,
                fontWeight = FontWeight.Black,
                fontFamily = p.numbers,
                color = p.ink,
            )
            if (target.enabled) {
                val line = when (target.type) {
                    TargetType.TIME -> {
                        val remaining = TargetTime.remainingSec(elapsed * 1000L, target.minutes) ?: 0
                        if (remaining > 0) {
                            stringResource(R.string.workout_target_remaining, target.minutes, formatClock(remaining))
                        } else {
                            stringResource(R.string.workout_target_done, target.minutes)
                        }
                    }
                    TargetType.DISTANCE -> {
                        val left = target.remainingMeters(meters) ?: 0
                        val goal = formatDistance(target.distanceM.toDouble())
                        if (left > 0) {
                            stringResource(R.string.workout_target_distance_remaining, goal, formatDistance(left.toDouble()))
                        } else {
                            stringResource(R.string.workout_target_distance_done, goal)
                        }
                    }
                }
                Text(text = line, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = p.accentText)
            }
        }

        // 風景、小人物與換階段倒數
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            SceneryView(
                scene = scene,
                meters = { WorkoutController.estimatedMeters(SystemClock.elapsedRealtime()) },
                moving = settings.sceneryMoving && workout.jogging && workout.running,
                modifier = Modifier.fillMaxSize(),
            )
            MascotView(
                style = settings.mascotStyle,
                runPose = workout.jogging,
                running = workout.running,
                jogSec = jog,
                bpm = metronome.bpm,
                beat = metronome.beat,
                moveStage = moveStage,
                cheer = cheer,
                onTap = { WorkoutController.cheerNow(context) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.workout_scene_chip, scene.nameZh, formatDistance(meters)),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3A2A1F),
                )
            }
            if (countdown != null && nextStage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = stringResource(R.string.workout_countdown_title), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        text = stringResource(R.string.workout_stage_next_short, Announcements.stageName(nextStage.kind), nextStage.minutes),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    )
                    Text(
                        text = countdown.toString(),
                        fontSize = 140.sp,
                        lineHeight = 140.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = p.numbers,
                        color = if (p.dark) p.countdown else Color.White,
                    )
                }
            }
        }

        // 暖身與伸展：目前動作與要領
        if (moveSlot != null) {
            Panel {
                Text(
                    text = stringResource(R.string.workout_move, moveSlot.index + 1, moveSlot.count, moveSlot.move.nameZh),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = p.ink,
                )
                Text(text = moveSlot.move.tip, style = MaterialTheme.typography.bodyMedium, color = p.ink)
                moveSlot.next?.let {
                    Text(text = stringResource(R.string.workout_move_next, it.nameZh), style = MaterialTheme.typography.bodySmall, color = p.inkSoft)
                }
            }
        }

        // 數字
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f), stringResource(R.string.workout_jog_time), formatClock(jog), if (workout.jogging) p.accentText else null)
            Tile(Modifier.weight(1f), stringResource(R.string.stat_distance), formatDistance(meters))
            Tile(Modifier.weight(1f), stringResource(R.string.bpm_unit), metronome.bpm.toString())
        }

        // 節拍與超慢跑
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                onClick = { MetronomeController.setBpm(metronome.bpm - 1) },
            ) { Text(stringResource(R.string.bpm_minus), fontSize = 22.sp) }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                enabled = workout.running,
                onClick = { if (workout.jogging) WorkoutController.endJog(context) else startJog() },
            ) {
                Text(
                    text = stringResource(if (workout.jogging) R.string.jog_end else R.string.jog_start),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                onClick = { MetronomeController.setBpm(metronome.bpm + 1) },
            ) { Text(stringResource(R.string.bpm_plus), fontSize = 22.sp) }
        }

        // 暫停與結束
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.ink, contentColor = p.bg),
                onClick = { if (workout.running) WorkoutController.pause(context) else WorkoutController.resume(context) },
            ) {
                Icon(if (workout.running) AppIcons.Pause else AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(if (workout.running) R.string.workout_pause else R.string.workout_resume),
                    fontWeight = FontWeight.Bold,
                )
            }
            TextButton(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                onClick = { confirmFinish = true },
            ) {
                Text(
                    text = stringResource(R.string.workout_finish),
                    fontWeight = FontWeight.Bold,
                    color = p.accentText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (workout.targetPrompt) {
        AlertDialog(
            onDismissRequest = { WorkoutController.dismissTargetPrompt() },
            title = { Text(stringResource(if (plan != null) R.string.plan_done_title else R.string.target_title)) },
            text = {
                Text(
                    stringResource(
                        if (WorkoutController.stoppedAtTarget()) R.string.target_text_stopped else R.string.target_text,
                        formatClock(elapsed),
                        formatClock(jog),
                        formatDistance(meters),
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { WorkoutController.finish(context, save = true) }) {
                    Text(stringResource(R.string.finish_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { WorkoutController.dismissTargetPrompt() }) {
                    Text(stringResource(R.string.finish_continue))
                }
            },
        )
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.finish_summary, formatClock(elapsed), formatClock(jog)))
                    TextButton(onClick = {
                        confirmFinish = false
                        WorkoutController.finish(context, save = false)
                    }) { Text(stringResource(R.string.finish_discard)) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmFinish = false
                    WorkoutController.finish(context, save = true)
                }) { Text(stringResource(R.string.finish_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.finish_continue)) }
            },
        )
    }
}
