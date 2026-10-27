package com.cornming.slowjog.mascot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cornming.slowjog.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 影格表（每張 2304 x 960，約 9 MB 記憶體）的載入與快取。
 * 只保留目前用得到的幾張，轉換視角前先把要經過的幾張載好。
 */
class MascotSheets(private val context: Context) {

    private val cache = HashMap<Int, ImageBitmap>()
    private val moveCache = HashMap<Move, ImageBitmap>()

    fun get(sheetAngle: Int): ImageBitmap? = cache[sheetAngle]

    fun getMove(move: Move): ImageBitmap? = moveCache[move]

    /** 載入暖身與伸展動作的影格表（每張同樣約 9 MB，只留目前與下一個動作）。 */
    suspend fun ensureMoves(moves: Collection<Move>) {
        for (move in moves) {
            if (moveCache.containsKey(move)) continue
            val resId = MOVE_RES[move] ?: continue
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(context.resources, resId)
            } ?: continue
            moveCache[move] = bitmap.asImageBitmap()
        }
    }

    fun keepOnlyMoves(moves: Set<Move>) {
        moveCache.keys.retainAll(moves)
    }

    suspend fun ensure(sheetAngles: Collection<Int>) {
        for (angle in sheetAngles) {
            if (cache.containsKey(angle)) continue
            val resId = RES[angle] ?: continue
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(context.resources, resId)
            } ?: continue
            cache[angle] = bitmap.asImageBitmap()
        }
    }

    fun keepOnly(sheetAngles: Set<Int>) {
        cache.keys.retainAll(sheetAngles)
    }

    companion object {
        const val FRAME_WIDTH = 384
        const val FRAME_HEIGHT = 480
        const val COLUMNS = 6

        private val MOVE_RES = mapOf(
            Move.MARCH to R.drawable.mascot_m_march,
            Move.ARM_CIRCLE to R.drawable.mascot_m_armcircle,
            Move.TWIST to R.drawable.mascot_m_twist,
            Move.SQUAT to R.drawable.mascot_m_squat,
            Move.CALF to R.drawable.mascot_m_calf,
            Move.QUAD to R.drawable.mascot_m_quad,
            Move.FORWARD to R.drawable.mascot_m_forward,
            Move.SIDEBEND to R.drawable.mascot_m_sidebend,
            Move.CROSSARM to R.drawable.mascot_m_crossarm,
        )

        private val RES = mapOf(
            0 to R.drawable.mascot_v000,
            15 to R.drawable.mascot_v015,
            30 to R.drawable.mascot_v030,
            45 to R.drawable.mascot_v045,
            60 to R.drawable.mascot_v060,
            75 to R.drawable.mascot_v075,
            90 to R.drawable.mascot_v090,
        )
    }
}
