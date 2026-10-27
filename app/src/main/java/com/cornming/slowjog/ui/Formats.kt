package com.cornming.slowjog.ui

import java.util.Locale

/** 計時用：不到一小時顯示 m:ss，超過顯示 h:mm:ss。 */
fun formatClock(totalSec: Int): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, sec) else "%d:%02d".format(Locale.ROOT, m, sec)
}

/** 累計用：例如「3 小時 25 分」；不到一小時顯示「25 分」，不到一分鐘顯示「0 分」。 */
fun formatTotal(totalSec: Long): String {
    val minutes = (totalSec.coerceAtLeast(0L) / 60).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "$h 小時 $m 分" else "$m 分"
}
