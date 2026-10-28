package com.cornming.slowjog.scenery

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * 跑步時的風景：天空與遠近好幾層，遠的捲得慢、近的捲得快，做出景深。
 * 捲動量跟著 [meters]（估算距離）走，所以暫停時不動、節拍越快捲得越快。
 * 圖形用設計稿單位（一段寬 600、高 300）描述，依畫面高度等比例縮放。
 */
@Composable
fun SceneryView(scene: Scene, meters: () -> Double, moving: Boolean, modifier: Modifier = Modifier) {
    val frameClock = remember { mutableLongStateOf(0L) }
    val currentMeters by rememberUpdatedState(meters)
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        while (true) {
            withFrameMillis { frameClock.longValue = it }
        }
    }
    val art = remember(scene) { artOf(scene) }
    Canvas(modifier = modifier) {
        frameClock.longValue // 讀取以便每個畫面影格重畫
        val m = if (moving) currentMeters() else 0.0
        val s = size.height / SceneryLogic.TILE_H
        val visibleW = size.width / s
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            drawRect(art.sky, size = Size(visibleW, SceneryLogic.TILE_H))
            art.back(this, visibleW)
            for (layer in art.layers) {
                val o = SceneryLogic.layerOffset(m, layer.speed)
                var x = -o
                while (x < visibleW) {
                    translate(left = x) { layer.draw(this) }
                    x += SceneryLogic.TILE_W
                }
                if (layer.bandAfter != null) layer.bandAfter.invoke(this, visibleW)
            }
        }
    }
}

private class Layer(
    val speed: Float,
    val draw: DrawScope.() -> Unit,
    /** 這一層畫完之後，在上面鋪一條不捲動的色帶（草地、路面等），寬度為可見寬度。 */
    val bandAfter: (DrawScope.(Float) -> Unit)? = null,
)

private class Art(val sky: Color, val back: DrawScope.(Float) -> Unit, val layers: List<Layer>)

private fun c(hex: Long) = Color(hex or 0xFF000000)

/** 依序連接的折線，最後封閉到底部（[bottom]）。座標為 x1, y1, x2, y2…。 */
private fun ridge(bottom: Float, vararg pts: Float): Path = Path().apply {
    moveTo(pts[0], bottom)
    var i = 0
    while (i < pts.size) {
        lineTo(pts[i], pts[i + 1])
        i += 2
    }
    lineTo(pts[pts.size - 2], bottom)
    close()
}

/** 底寬 [w]、高 [h]、底邊中心在 (cx, by) 的三角形（針葉樹、帆）。 */
private fun triangle(cx: Float, by: Float, w: Float, h: Float): Path = Path().apply {
    moveTo(cx - w / 2, by)
    lineTo(cx, by - h)
    lineTo(cx + w / 2, by)
    close()
}

private fun DrawScope.band(color: Color, top: Float, width: Float) {
    drawRect(color, topLeft = Offset(0f, top), size = Size(width, SceneryLogic.TILE_H - top))
}

private fun DrawScope.dashes(color: Color, y: Float, w: Float, h: Float, vararg xs: Float) {
    for (x in xs) drawRoundRect(color, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
}

private fun artOf(scene: Scene): Art = when (scene) {
    Scene.RIVERSIDE -> riverside()
    Scene.CITY -> city()
    Scene.FOREST -> forest()
    Scene.SEASIDE -> seaside()
}

private fun riverside(): Art {
    val far = ridge(190f, 0f, 190f, 70f, 140f, 150f, 170f, 240f, 110f, 330f, 165f, 420f, 130f, 510f, 175f, 600f, 150f, 600f, 190f)
    val trunk = c(0x8C5B3B)
    return Art(
        sky = c(0xFFE2C4),
        back = { _ -> drawCircle(c(0xFFC94D), radius = 40f, center = Offset(470f, 70f)) },
        layers = listOf(
            Layer(0.05f, { drawPath(far, c(0xF4BE96)) }),
            Layer(
                0.3f,
                {
                    drawRect(c(0xE9A06F), topLeft = Offset(0f, 190f), size = Size(600f, 30f))
                    for ((x, y, r) in listOf(Triple(60f, 176f, 26f), Triple(220f, 170f, 32f), Triple(460f, 178f, 24f), Triple(560f, 172f, 28f))) {
                        drawRect(trunk, topLeft = Offset(x - 3f, y + r - 6f), size = Size(6f, 22f))
                        drawCircle(if (r > 27f) c(0xD0905F) else c(0xC9875C), radius = r, center = Offset(x, y))
                    }
                    drawRect(trunk, topLeft = Offset(330f, 144f), size = Size(4f, 70f))
                    drawRoundRect(trunk, topLeft = Offset(322f, 140f), size = Size(20f, 8f), cornerRadius = CornerRadius(3f))
                },
                bandAfter = { w ->
                    band(c(0x7FB77E), 214f, w)
                    band(c(0xE8CFA8), 250f, w)
                },
            ),
            Layer(1f, { dashes(c(0xFFF6EC), 274f, 44f, 6f, 20f, 170f, 320f, 470f) }),
        ),
    )
}

private fun city(): Art {
    val farColor = c(0x9DB8D0)
    val farRects = listOf(
        floatArrayOf(0f, 120f, 50f, 80f), floatArrayOf(56f, 80f, 40f, 120f), floatArrayOf(102f, 140f, 64f, 60f),
        floatArrayOf(172f, 60f, 36f, 140f), floatArrayOf(214f, 110f, 70f, 90f), floatArrayOf(290f, 90f, 46f, 110f),
        floatArrayOf(342f, 130f, 80f, 70f), floatArrayOf(428f, 100f, 60f, 100f), floatArrayOf(494f, 70f, 44f, 130f),
        floatArrayOf(544f, 120f, 56f, 80f),
    )
    val window = c(0xFFE8A8)
    return Art(
        sky = c(0xBFD7EA),
        back = { _ -> },
        layers = listOf(
            Layer(0.05f, { for (r in farRects) drawRect(farColor, topLeft = Offset(r[0], r[1]), size = Size(r[2], r[3])) }),
            Layer(
                0.3f,
                {
                    drawRect(c(0x6F8AA6), topLeft = Offset(0f, 160f), size = Size(120f, 70f))
                    drawRect(c(0x5E7894), topLeft = Offset(140f, 140f), size = Size(90f, 90f))
                    drawRect(c(0x6F8AA6), topLeft = Offset(280f, 150f), size = Size(150f, 80f))
                    drawRect(c(0x5E7894), topLeft = Offset(450f, 170f), size = Size(150f, 60f))
                    for ((x, y) in listOf(14f to 174f, 46f to 174f, 46f to 196f, 156f to 154f, 200f to 176f, 296f to 164f, 330f to 164f, 364f to 186f, 470f to 182f, 520f to 182f)) {
                        drawRect(window, topLeft = Offset(x, y), size = Size(12f, 9f))
                    }
                    drawRect(c(0x3E4E60), topLeft = Offset(250f, 180f), size = Size(4f, 50f))
                    drawCircle(window, radius = 7f, center = Offset(252f, 178f))
                },
                bandAfter = { w ->
                    band(c(0x8C99A6), 224f, w)
                    band(c(0x5D6875), 254f, w)
                },
            ),
            Layer(1f, { dashes(c(0xF2F2F2), 274f, 56f, 6f, 20f, 220f, 420f) }),
        ),
    )
}

private fun forest(): Art {
    val far = ridge(190f, 0f, 190f, 80f, 130f, 160f, 170f, 260f, 110f, 360f, 175f, 450f, 135f, 540f, 170f, 600f, 150f, 600f, 190f)
    val trees = listOf(42f to 70f, 118f to 90f, 220f to 62f, 326f to 84f, 442f to 70f, 548f to 92f).map { (x, h) -> triangle(x, 230f, h * 0.62f, h) }
    val bush = c(0x5E9C6F)
    return Art(
        sky = c(0xDCEBDF),
        back = { _ -> },
        layers = listOf(
            Layer(0.05f, { drawPath(far, c(0xB9D6C0)) }),
            Layer(
                0.3f,
                { for (t in trees) drawPath(t, c(0x7FB394)) },
                bandAfter = { w ->
                    band(c(0x9BC7A6), 224f, w)
                    band(c(0xD9C9A6), 256f, w)
                },
            ),
            Layer(0.6f, {
                for ((x, r) in listOf(40f to 10f, 56f to 8f, 260f to 11f, 430f to 9f, 446f to 10f)) {
                    drawCircle(bush, radius = r, center = Offset(x, 252f - r * 0.2f))
                }
            }),
            Layer(1f, {
                for ((x, w) in listOf(60f to 10f, 250f to 7f, 380f to 12f, 520f to 8f)) {
                    drawOval(c(0xC4B28E), topLeft = Offset(x, 280f), size = Size(w, w * 0.45f))
                }
            }),
        ),
    )
}

private fun seaside(): Art {
    val wave = Path().apply {
        moveTo(0f, 216f)
        var x = 0f
        while (x < 600f) {
            quadraticTo(x + 25f, 202f, x + 50f, 216f)
            x += 50f
        }
        close()
    }
    return Art(
        sky = c(0xCFEAF4),
        back = { w ->
            drawCircle(c(0xFFE08A), radius = 35f, center = Offset(95f, 69f))
            band(c(0x5AAFD0), 144f, w)
        },
        layers = listOf(
            Layer(0.05f, {
                drawPath(triangle(70f, 160f, 18f, 18f), Color.White)
                drawPath(triangle(390f, 160f, 13f, 14f), Color.White)
            }),
            Layer(
                0.3f,
                { drawPath(wave, Color.White.copy(alpha = 0.8f)) },
                bandAfter = { w -> band(c(0xF2DDB0), 214f, w) },
            ),
            Layer(1f, {
                for ((x, rx, ry) in listOf(Triple(40f, 10f, 4f), Triple(210f, 7f, 3f), Triple(400f, 12f, 4f), Triple(520f, 6f, 3f))) {
                    drawOval(c(0xE2C690), topLeft = Offset(x - rx, 264f - ry), size = Size(rx * 2, ry * 2))
                }
            }),
        ),
    )
}
