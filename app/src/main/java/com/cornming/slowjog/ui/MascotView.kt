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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cornming.slowjog.mascot.MascotLogic
import com.cornming.slowjog.mascot.MascotSheets
import com.cornming.slowjog.mascot.MoveSchedule
import com.cornming.slowjog.plan.StageKind
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
    /** 暖身或伸展階段且沒在跑時，示範動作用的階段資訊；為 null 就是一般的跑步／站立模式。 */
    moveStage: MoveStage? = null,
    /** 運動是否進行中（暫停時動作定格）。 */
    running: Boolean = true,
) {
    val context = LocalContext.current
    val sheets = remember { MascotSheets(context) }
    val currentJogging by rememberUpdatedState(jogging)
    val currentBpm by rememberUpdatedState(bpm)
    val currentBeat by rememberUpdatedState(beat)
    val currentFlash by rememberUpdatedState(flashAlpha)
    val currentMoveStage by rememberUpdatedState(moveStage)
    val currentRunning by rememberUpdatedState(running)
    val moveStageChangedAt = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

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

    // 動作時間每 0.2 秒才更新一次，記下更新的時刻，畫面才能在兩次更新之間走得順。
    LaunchedEffect(moveStage?.elapsedMs) { moveStageChangedAt.longValue = SystemClock.elapsedRealtime() }

    // 暖身與伸展：先載入目前與下一個動作的影格表，用不到的就釋放。
    val moveNow = moveStage?.let { MoveSchedule.at(it.kind, it.stageMs, it.elapsedMs) }
    LaunchedEffect(moveNow?.move) {
        val current = moveNow?.move
        if (current == null) {
            sheets.keepOnlyMoves(emptySet())
        } else {
            sheets.ensureMoves(listOfNotNull(current, moveNow.next))
            sheets.keepOnlyMoves(setOfNotNull(current, moveNow.next))
        }
    }
    // 換動作時冒出氣泡提醒要領。
    LaunchedEffect(moveNow?.move, moveNow?.index) {
        val m = moveNow?.move ?: return@LaunchedEffect
        bubble = "${m.nameZh}：${m.tip}"
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
            val ms = currentMoveStage
            if (ms != null) {
                val extra = if (currentRunning) (SystemClock.elapsedRealtime() - moveStageChangedAt.longValue).coerceIn(0L, 250L) else 0L
                val slot = MoveSchedule.at(ms.kind, ms.stageMs, ms.elapsedMs + extra)
                val moveSheet = slot?.let { sheets.getMove(it.move) }
                if (slot != null && moveSheet != null) {
                    val pos = slot.phase * MascotMoveFrames
                    val i = pos.toInt().coerceIn(0, MascotMoveFrames - 1)
                    val frac = pos - i
                    scale(
                        scaleX = if (slot.mirrored) -1f else 1f,
                        scaleY = 1f,
                        pivot = center,
                    ) {
                        drawFrame(moveSheet, i, 1f)
                        // 相鄰兩格疊在一起淡入，慢動作才不會一格一格跳。
                        if (frac > 0.02f) drawFrame(moveSheet, (i + 1) % MascotMoveFrames, frac)
                    }
                }
                return@Canvas
            }
            val sheet = sheets.get(MascotLogic.sheetAngle(angle)) ?: return@Canvas
            val frame = if (currentJogging) {
                val since = SystemClock.elapsedRealtime() - lastBeatAt.longValue
                MascotLogic.frameIndex(currentBeat, since, currentBpm)
            } else {
                0
            }
            scale(
                scaleX = if (MascotLogic.isMirrored(angle)) -1f else 1f,
                scaleY = 1f,
                pivot = center,
            ) {
                drawFrame(sheet, frame, 1f)
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

/** 暖身與伸展影格表一個循環的格數。 */
private const val MascotMoveFrames = 12

/** 目前階段（暖身或伸展）與階段內已過的時間，讓小人物示範動作。 */
data class MoveStage(val kind: StageKind, val stageMs: Long, val elapsedMs: Long)

private fun DrawScope.drawFrame(sheet: ImageBitmap, frame: Int, alpha: Float) {
    val col = frame % MascotSheets.COLUMNS
    val row = frame / MascotSheets.COLUMNS
    drawImage(
        image = sheet,
        srcOffset = IntOffset(col * MascotSheets.FRAME_WIDTH, row * MascotSheets.FRAME_HEIGHT),
        srcSize = IntSize(MascotSheets.FRAME_WIDTH, MascotSheets.FRAME_HEIGHT),
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )
}
