package com.cornming.slowjog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.R
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.workout.WorkoutController

/** 運動中畫面：整段時間與超慢跑時間、節拍閃動、暫停與結束。運動中螢幕恆亮。 */
@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val workout by WorkoutController.state.collectAsStateWithLifecycle()
    val metronome by MetronomeController.state.collectAsStateWithLifecycle()
    val now = rememberNow()
    val elapsed = WorkoutController.elapsedSec(now)
    val jog = WorkoutController.jogSec(now)
    var confirmFinish by rememberSaveable { mutableStateOf(false) }

    KeepScreenOn(enabled = true)

    val startJog = rememberWithNotificationPermission { WorkoutController.startJog(context) }

    val flash = remember { Animatable(0f) }
    LaunchedEffect(metronome.beat) {
        if (metronome.running && metronome.beat > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, animationSpec = tween(durationMillis = 180))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(
                when {
                    !workout.running -> R.string.workout_paused
                    workout.jogging -> R.string.workout_jogging
                    else -> R.string.workout_ongoing
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.workout_total_time), style = MaterialTheme.typography.bodySmall)
            Text(text = formatClock(elapsed), fontSize = 56.sp, style = MaterialTheme.typography.displayMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(R.string.workout_jog_time), style = MaterialTheme.typography.bodySmall)
            Text(text = formatClock(jog), style = MaterialTheme.typography.headlineMedium)
        }

        MascotView(
            jogging = workout.jogging && workout.running,
            jogSec = jog,
            bpm = metronome.bpm,
            beat = metronome.beat,
            flashAlpha = flash.value,
        )
        Text(
            text = stringResource(R.string.mascot_hint),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = { MetronomeController.setBpm(metronome.bpm - 1) }) {
                Text(stringResource(R.string.bpm_minus))
            }
            Text(
                text = "${metronome.bpm} ${stringResource(R.string.bpm_unit)}",
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = { MetronomeController.setBpm(metronome.bpm + 1) }) {
                Text(stringResource(R.string.bpm_plus))
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (workout.jogging) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = workout.running,
                    onClick = { WorkoutController.endJog(context) },
                ) { Text(stringResource(R.string.jog_end), style = MaterialTheme.typography.titleMedium) }
            } else {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = workout.running,
                    onClick = { startJog() },
                ) { Text(stringResource(R.string.jog_start), style = MaterialTheme.typography.titleMedium) }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (workout.running) WorkoutController.pause(context) else WorkoutController.resume(context)
                },
            ) { Text(stringResource(if (workout.running) R.string.workout_pause else R.string.workout_resume)) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { confirmFinish = true },
            ) { Text(stringResource(R.string.workout_finish)) }
        }
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
