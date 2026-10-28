package com.cornming.slowjog.mascot

/** 小人物的跑步影格與鼓勵氣泡的規則。純 Kotlin，有單元測試。 */
object MascotLogic {

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
