package com.cornming.slowjog.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cornming.slowjog.mascot.MascotLogic
import com.cornming.slowjog.mascot.MascotSheets
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 運動中畫面的小人物：超慢跑時腳步對齊節拍，每 5 分鐘（超慢跑時間）轉換視角，
 * 不定時冒出鼓勵氣泡。點一下小人物可以立刻換視角。
 */
@Composable
fun MascotView(
    jogging: Boolean,
    jogSec: Int,
    bpm: Int,
    beat: Long,
    flashAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheets = remember { MascotSheets(context) }
    val currentJogging by rememberUpdatedState(jogging)
    val currentBpm by rememberUpdatedState(bpm)
    val currentBeat by rememberUpdatedState(beat)
    val currentFlash by rememberUpdatedState(flashAlpha)

    var manualOffset by remember { mutableIntStateOf(0) }
    var angle by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    val frameClock = remember { mutableLongStateOf(0L) }
    val lastBeatAt = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var bubble by remember { mutableStateOf<String?>(null) }
    var lastMessage by remember { mutableIntStateOf(-1) }
    var nextBubbleAtJog by remember { mutableIntStateOf(3) }

    val targetView = MascotLogic.viewIndex(jogSec, manualOffset)

    // 每個畫面影格更新一次時鐘；只有繪製區讀取它，不會讓整個畫面重組。
    LaunchedEffect(Unit) {
        sheets.ensure(listOf(0))
        ready = true
        while (true) {
            withFrameMillis { frameClock.longValue = it }
        }
    }

    // 每一拍記下時間，影格依它對齊腳落地的瞬間。
    LaunchedEffect(beat) { lastBeatAt.longValue = SystemClock.elapsedRealtime() }

    // 視角改變：從目前角度一格（15 度）一格轉過去，總共約 1 秒。
    // 影格表一張約 9 MB，邊轉邊載入，只保留目前與下一張。
    LaunchedEffect(targetView) {
        val to = MascotLogic.VIEW_ANGLES[targetView]
        val path = MascotLogic.anglePath(angle, to)
        if (path.isEmpty()) return@LaunchedEffect
        val stepMs = (1000L / path.size).coerceAtLeast(100L)
        for ((i, a) in path.withIndex()) {
            sheets.ensure(setOf(MascotLogic.sheetAngle(a)))
            angle = a
            val next = path.getOrNull(i + 1)
            sheets.keepOnly(setOfNotNull(MascotLogic.sheetAngle(a), next?.let { MascotLogic.sheetAngle(it) }))
            delay(stepMs)
        }
        sheets.keepOnly(setOf(MascotLogic.sheetAngle(angle)))
    }

    // 超慢跑時間到了就冒出一則氣泡，之後每 1 到 2 分鐘一則。
    LaunchedEffect(jogSec, jogging) {
        if (jogging && jogSec >= nextBubbleAtJog) {
            val (index, text) = MascotLogic.pickMessage(lastMessage, Random.nextDouble(), bpm)
            lastMessage = index
            bubble = text
            nextBubbleAtJog = jogSec + MascotLogic.nextBubbleDelaySec(Random.nextDouble())
        }
    }
    LaunchedEffect(bubble) {
        if (bubble != null) {
            delay(MascotLogic.BUBBLE_SHOW_SEC * 1000L)
            bubble = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                manualOffset += 1
                val (index, text) = MascotLogic.pickMessage(lastMessage, Random.nextDouble(), bpm)
                lastMessage = index
                bubble = text
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val ringColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(top = 8.dp)
                .aspectRatio(MascotSheets.FRAME_WIDTH.toFloat() / MascotSheets.FRAME_HEIGHT),
        ) {
            frameClock.longValue // 讀取以便每個畫面影格重畫

            // 節拍閃動：腳下的橢圓在每一拍亮起再淡出。
            val ringW = size.width * 0.6f
            val ringH = size.height * 0.06f
            drawOval(
                color = ringColor.copy(alpha = currentFlash * 0.6f),
                topLeft = Offset((size.width - ringW) / 2f, size.height * 0.9f),
                size = Size(ringW, ringH),
            )

            if (!ready) return@Canvas
            val sheet = sheets.get(MascotLogic.sheetAngle(angle)) ?: return@Canvas
            val frame = if (currentJogging) {
                val since = SystemClock.elapsedRealtime() - lastBeatAt.longValue
                MascotLogic.frameIndex(currentBeat, since, currentBpm)
            } else {
                0
            }
            val col = frame % MascotSheets.COLUMNS
            val row = frame / MascotSheets.COLUMNS
            scale(
                scaleX = if (MascotLogic.isMirrored(angle)) -1f else 1f,
                scaleY = 1f,
                pivot = center,
            ) {
                drawImage(
                    image = sheet,
                    srcOffset = IntOffset(col * MascotSheets.FRAME_WIDTH, row * MascotSheets.FRAME_HEIGHT),
                    srcSize = IntSize(MascotSheets.FRAME_WIDTH, MascotSheets.FRAME_HEIGHT),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        AnimatedVisibility(
            visible = bubble != null,
            modifier = Modifier.padding(top = 4.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 3.dp,
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    text = bubble.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
