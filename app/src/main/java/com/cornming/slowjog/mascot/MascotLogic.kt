package com.cornming.slowjog.mascot

import kotlin.math.abs

/** 小人物的視角、影格與鼓勵氣泡的規則。純 Kotlin，有單元測試。 */
object MascotLogic {

    /** 視角循環：正面 → 45 度側面 → 90 度側面 → 另一側 45 度（負值為左右鏡像）。 */
    val VIEW_ANGLES = listOf(0, 45, 90, -45)

    /** 影格表的角度間隔。 */
    const val ANGLE_STEP = 15

    /** 每隔多少秒的超慢跑時間轉換一次視角。 */
    const val VIEW_PERIOD_SEC = 300

    /** 一個步伐循環共 12 格，對應 2 拍。 */
    const val FRAMES_PER_BEAT = 6

    /** 氣泡停留秒數。 */
    const val BUBBLE_SHOW_SEC = 5

    val MESSAGES = listOf(
        "今天也出門了，很棒！",
        "步子小小的，剛剛好。",
        "慢到還能聊天，就是對的速度。",
        "肩膀放鬆，嘴角上揚～",
        "呼吸順順的，繼續保持。",
        "跟著我的腳步，一二、一二～",
        "不用追求快，穩穩地跑就好。",
        "腳掌輕輕落地，像踩在雲上。",
        "抬頭挺胸，看向遠方。",
        "每一步都算數！",
        "節拍 {bpm}，我們同步囉！",
    )

    /** 依超慢跑時間決定目前視角；manualOffset 是使用者點小人物手動多換的次數。 */
    fun viewIndex(jogSec: Int, manualOffset: Int = 0): Int =
        Math.floorMod(jogSec / VIEW_PERIOD_SEC + manualOffset, VIEW_ANGLES.size)

    /** 從 from 轉到 to 要依序經過的影格表角度（不含 from，含 to），每 15 度一格。 */
    fun anglePath(from: Int, to: Int): List<Int> {
        if (from == to) return emptyList()
        val step = if (to > from) ANGLE_STEP else -ANGLE_STEP
        val out = ArrayList<Int>()
        var a = from
        while (a != to) {
            a += step
            out.add(a)
        }
        return out
    }

    /** 影格表檔案用的角度（0 到 90）。 */
    fun sheetAngle(angle: Int): Int = abs(angle)

    fun isMirrored(angle: Int): Boolean = angle < 0

    /**
     * 目前該畫第幾格（0 到 11）。第 0 與第 6 格是腳落地，對齊節拍：
     * 偶數拍從第 0 格開始，奇數拍從第 6 格開始，拍與拍之間走 6 格。
     */
    fun frameIndex(beat: Long, sinceBeatMs: Long, bpm: Int): Int {
        val periodMs = 60_000.0 / bpm.coerceAtLeast(1)
        val within = ((sinceBeatMs / periodMs) * FRAMES_PER_BEAT).toInt().coerceIn(0, FRAMES_PER_BEAT - 1)
        return (Math.floorMod(beat, 2L).toInt()) * FRAMES_PER_BEAT + within
    }

    /** 下一則氣泡間隔 60 到 119 秒；rand 為 0 以上、小於 1 的亂數。 */
    fun nextBubbleDelaySec(rand: Double): Int = 60 + (rand * 60).toInt().coerceIn(0, 59)

    /** 挑一則和上一則不同的鼓勵文字，回傳（索引, 文字）。 */
    fun pickMessage(previous: Int, rand: Double, bpm: Int): Pair<Int, String> {
        var i = (rand * MESSAGES.size).toInt().coerceIn(0, MESSAGES.size - 1)
        if (i == previous) i = (i + 1) % MESSAGES.size
        return i to MESSAGES[i].replace("{bpm}", bpm.toString())
    }
}
