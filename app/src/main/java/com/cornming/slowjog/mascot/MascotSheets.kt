package com.cornming.slowjog.mascot

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 影格表的載入與快取（以資源 ID 為鍵）。動作的影格表每張約 18 MB 記憶體，
 * 所以只保留目前用得到的幾張。
 */
class MascotSheets(private val context: Context) {

    private val cache = HashMap<Int, ImageBitmap>()

    fun get(resId: Int): ImageBitmap? = cache[resId]

    suspend fun ensure(resIds: Collection<Int>) {
        for (id in resIds) {
            if (cache.containsKey(id)) continue
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
            } ?: continue
            cache[id] = bitmap.asImageBitmap()
        }
    }

    fun keepOnly(resIds: Set<Int>) {
        cache.keys.retainAll(resIds)
    }

    companion object {
        const val FRAME_WIDTH = 384
        const val FRAME_HEIGHT = 480
        const val COLUMNS = 6
    }
}
