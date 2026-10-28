package com.cornming.slowjog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** App 自己畫的線條圖示（24 x 24，跟著 Icon 的 tint 換色），不另外引入圖示套件。 */
object AppIcons {

    private fun stroke(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun fill(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        for (d in paths) b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        return b.build()
    }

    val Home = stroke("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z")
    val Calendar = stroke("calendar", "M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 3v4M16 3v4M4 10h16")
    val Sliders = stroke(
        "sliders",
        "M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12",
        "M16 4a2 2 0 1 1 0 4a2 2 0 1 1 0-4z",
        "M10 10a2 2 0 1 1 0 4a2 2 0 1 1 0-4z",
        "M18 16a2 2 0 1 1 0 4a2 2 0 1 1 0-4z",
    )
    val Flame = stroke("flame", "M12 3c1 3 4 5 4 9a4 4 0 0 1-8 0c0-2 1-3 2-4 0 2 1 3 2 3 0-3-1-5 0-8z")
    val Check = stroke("check", "M5 12l5 5 9-10")
    val Play = fill("play", "M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z")
    val Pause = fill("pause", "M6 4h4v16H6z", "M14 4h4v16h-4z")
}
