package com.cornming.slowjog.workout

/**
 * 超慢跑距離的「估算」：App 沒有定位，也不讀計步器，
 * 所以用「節拍 x 時間 = 步數」再乘上使用者設定的步幅。
 * 前提是一拍就是一步（超慢跑一般抓 180 BPM 左右，就是每分鐘 180 步）；步幅是預設值，需要自己校正。
 */
object Distance {

    const val DEFAULT_STRIDE_CM = 50
    const val MIN_STRIDE_CM = 30
    const val MAX_STRIDE_CM = 100

    /** 在 [durationMs] 毫秒內、以 [bpm] 的節拍踏出的步數。 */
    fun steps(durationMs: Long, bpm: Int): Double =
        if (durationMs <= 0 || bpm <= 0) 0.0 else durationMs / 60_000.0 * bpm

    fun meters(steps: Double, strideCm: Int): Double =
        steps * strideCm.coerceIn(MIN_STRIDE_CM, MAX_STRIDE_CM) / 100.0

    /** 以目前節拍與步幅，每分鐘大約跑幾公尺。 */
    fun metersPerMinute(bpm: Int, strideCm: Int): Double = meters(bpm.toDouble(), strideCm)

    /** 跑完 [targetMeters] 大約需要幾分鐘（無條件進位）；算不出來回傳 null。 */
    fun minutesFor(targetMeters: Int, bpm: Int, strideCm: Int): Int? {
        val perMinute = metersPerMinute(bpm, strideCm)
        if (targetMeters <= 0 || perMinute <= 0.0) return null
        return Math.ceil(targetMeters / perMinute).toInt()
    }
}
