package com.cornming.slowjog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cornming.slowjog.R
import com.cornming.slowjog.metronome.MetronomeController
import com.cornming.slowjog.metronome.MetronomeEngine
import com.cornming.slowjog.metronome.TapTempo
import kotlin.math.roundToInt

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by MetronomeController.state.collectAsStateWithLifecycle()
    val tapTempo = remember { TapTempo() }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    // Android 13 以上要先取得通知權限；不論使用者同意與否都照樣開始，
    // 沒有權限時只是看不到通知，節拍器仍會運作。
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { MetronomeController.start(context) }

    fun startMetronome() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            MetronomeController.start(context)
        }
    }

    // 運動中保持螢幕恆亮，離開畫面或停止後恢復系統設定。
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
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 節拍視覺閃動：圓形在每一拍亮起再淡出，環境吵或音量小時也能對拍。
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(baseColor)
                    .drawBehind { drawRect(flashColor.copy(alpha = flash.value * 0.6f)) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.bpm.toString(),
                        fontSize = 64.sp,
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Text(
                        text = stringResource(R.string.bpm_unit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
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
                    .padding(top = 24.dp),
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
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = {
                    if (state.running) MetronomeController.stop(context) else startMetronome()
                },
            ) {
                Text(
                    text = stringResource(if (state.running) R.string.stop else R.string.start),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            UpdateSection(versionName = versionName, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
