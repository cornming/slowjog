package com.cornming.slowjog.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cornming.slowjog.mascot.MascotLogic
import com.cornming.slowjog.mascot.MascotResources
import com.cornming.slowjog.mascot.MascotSheets
import com.cornming.slowjog.mascot.MascotStyle
import com.cornming.slowjog.mascot.MoveSchedule
import com.cornming.slowjog.plan.StageKind
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 運動中畫面的小人物（透明背景，疊在風景上）：
 * - 超慢跑：側面跑步、面向右邊，腳步對齊節拍。
 * - 暖身與伸展（沒在跑）：示範動作。
 * - 其他時候：正面站著。
 * 不定時冒出鼓勵氣泡，點一下也會冒。
 */
@Composable
fun MascotView(
    style: MascotStyle,
    /** 是否擺跑步姿勢（超慢跑階段開著，暫停時也算）。 */
    runPose: Boolean,
    /** 運動是否進行中；暫停時動作定格。 */
    running: Boolean,
    jogSec: Int,
    bpm: Int,
    beat: Long,
    modifier: Modifier = Modifier,
    /** 暖身或伸展階段且沒在跑時，示範動作用的階段資訊。 */
    moveStage: MoveStage? = null,
) {
    val context = LocalContext.current
    val sheets = remember { MascotSheets(context) }
    val res = MascotResources.of(style)
    val currentRunPose by rememberUpdatedState(runPose)
    val currentRunning by rememberUpdatedState(running)
    val currentBpm by rememberUpdatedState(bpm)
    val currentBeat by rememberUpdatedState(beat)
    val currentMoveStage by rememberUpdatedState(moveStage)
    val currentRes by rememberUpdatedState(res)
    val moveClock = remember { MoveClock() }

    var ready by remember { mutableStateOf(false) }
    val frameClock = remember { mutableLongStateOf(0L) }
    val lastBeatAt = remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    var bubble by remember { mutableStateOf<String?>(null) }
    var lastMessage by remember { mutableIntStateOf(-1) }
    var nextBubbleAtJog by remember { mutableIntStateOf(3) }

    // 每個畫面影格更新一次時鐘；只有繪製區讀取它，不會讓整個畫面重組。
    LaunchedEffect(Unit) {
        ready = true
        while (true) {
            withFrameMillis { frameClock.longValue = it }
        }
    }

    // 每一拍記下時間，影格依它對齊腳落地的瞬間。
    LaunchedEffect(beat) { lastBeatAt.longValue = SystemClock.elapsedRealtime() }

    // 載入目前用得到的影格表：跑步、站姿、目前與下一個動作；其他的釋放。
    val moveNow = moveStage?.let { MoveSchedule.at(it.kind, it.stageMs, it.elapsedMs) }
    LaunchedEffect(style, moveNow?.move) {
        val wanted = setOfNotNull(
            res.run,
            res.idle,
            moveNow?.move?.let { res.moves[it] },
            moveNow?.next?.let { res.moves[it] },
        )
        sheets.ensure(wanted)
        sheets.keepOnly(wanted)
    }

    // 換動作時冒出氣泡提醒要領。
    LaunchedEffect(moveNow?.move, moveNow?.index) {
        val m = moveNow?.move ?: return@LaunchedEffect
        bubble = "${m.nameZh}：${m.tip}"
    }

    // 超慢跑時間到了就冒出一則氣泡，之後每 1 到 2 分鐘一則。
    LaunchedEffect(jogSec, runPose, running) {
        if (runPose && running && jogSec >= nextBubbleAtJog) {
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

    val filter = if (style.pixelArt) FilterQuality.None else FilterQuality.Medium

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            val (index, text) = MascotLogic.pickMessage(lastMessage, Random.nextDouble(), bpm)
            lastMessage = index
            bubble = text
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            frameClock.longValue // 讀取以便每個畫面影格重畫
            if (!ready) return@Canvas
            val r = currentRes

            val ms = currentMoveStage
            if (ms != null) {
                val slot = MoveSchedule.at(ms.kind, ms.stageMs, moveClock.now(ms.elapsedMs, currentRunning, SystemClock.elapsedRealtime()))
                val sheet = slot?.let { r.moves[it.move] }?.let { sheets.get(it) }
                if (slot != null && sheet != null) {
                    val pos = slot.phase * MOVE_FRAMES
                    val i = pos.toInt().coerceIn(0, MOVE_FRAMES - 1)
                    val frac = pos - i
                    scale(scaleX = if (slot.mirrored) -1f else 1f, scaleY = 1f, pivot = center) {
                        drawFrame(sheet, i, 1f, filter)
                        // 相鄰兩格疊在一起淡入，慢動作才不會一格一格跳。
                        if (frac > 0.02f) drawFrame(sheet, (i + 1) % MOVE_FRAMES, frac, filter)
                    }
                    return@Canvas
                }
            }

            if (currentRunPose) {
                val sheet = sheets.get(r.run) ?: return@Canvas
                val frame = if (currentRunning) {
                    MascotLogic.frameIndex(currentBeat, SystemClock.elapsedRealtime() - lastBeatAt.longValue, currentBpm)
                } else {
                    0
                }
                // 影格表的側面是面向左；鏡像成面向右，風景往左退。
                scale(scaleX = -1f, scaleY = 1f, pivot = center) { drawFrame(sheet, frame, 1f, filter) }
            } else {
                val idle = sheets.get(r.idle) ?: return@Canvas
                drawFrame(idle, 0, 1f, filter)
            }
        }

        AnimatedVisibility(
            visible = bubble != null,
            modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
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

/** 首頁與設定用的靜態正面站姿。像素風不做平滑，保留像素邊緣。 */
@Composable
fun MascotIdleImage(style: MascotStyle, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap = ImageBitmap.imageResource(MascotResources.of(style).idle)
    Image(
        painter = remember(bitmap, style) {
            BitmapPainter(bitmap, filterQuality = if (style.pixelArt) FilterQuality.None else FilterQuality.Medium)
        },
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

/**
 * 動作用的連續時鐘。外面傳進來的階段時間每 0.2 秒才更新一次，直接拿來畫會一頓一頓；
 * 這裡用系統時間自己往前走，只有和外面差太多（換動作、暫停後繼續）才對齊，不會往回跳。
 */
private class MoveClock {
    private var anchorElapsed = 0L
    private var anchorReal = 0L
    private var started = false

    fun now(elapsedMs: Long, running: Boolean, real: Long): Long {
        if (!running || !started) {
            anchorElapsed = elapsedMs
            anchorReal = real
            started = true
            return elapsedMs
        }
        val predicted = anchorElapsed + (real - anchorReal)
        if (kotlin.math.abs(predicted - elapsedMs) > 600) {
            anchorElapsed = elapsedMs
            anchorReal = real
            return elapsedMs
        }
        return predicted
    }
}

/** 暖身與伸展影格表一個循環的格數。 */
private const val MOVE_FRAMES = 24

/** 目前階段（暖身或伸展）與階段內已過的時間，讓小人物示範動作。 */
data class MoveStage(val kind: StageKind, val stageMs: Long, val elapsedMs: Long)

/** 畫一格影格，等比例縮放、置中、貼齊底部。 */
private fun DrawScope.drawFrame(sheet: ImageBitmap, frame: Int, alpha: Float, filter: FilterQuality) {
    val col = frame % MascotSheets.COLUMNS
    val row = frame / MascotSheets.COLUMNS
    val ratio = MascotSheets.FRAME_WIDTH.toFloat() / MascotSheets.FRAME_HEIGHT
    var h = size.height
    var w = h * ratio
    if (w > size.width) {
        w = size.width
        h = w / ratio
    }
    val left = ((size.width - w) / 2f).toInt()
    val top = (size.height - h).toInt()
    drawImage(
        image = sheet,
        srcOffset = IntOffset(col * MascotSheets.FRAME_WIDTH, row * MascotSheets.FRAME_HEIGHT),
        srcSize = IntSize(MascotSheets.FRAME_WIDTH, MascotSheets.FRAME_HEIGHT),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(w.toInt(), h.toInt()),
        alpha = alpha,
        filterQuality = filter,
    )
}
