package com.cornming.slowjog.mascot

/** 小人物的跑步影格與鼓勵氣泡的規則。純 Kotlin，有單元測試。 */
object MascotLogic {

    /** 一個步伐循環共 12 格，對應 2 拍。 */
    const val FRAMES_PER_BEAT = 6

    /** 氣泡停留秒數。 */
    const val BUBBLE_SHOW_SEC = 5

    /** 超慢跑開始後多久說第一句鼓勵（太早會和「超慢跑開始」的語音撞在一起）。 */
    const val FIRST_CHEER_JOG_SEC = 20

    /** 階段快結束、要倒數的時候不說鼓勵（毫秒）。 */
    const val NO_CHEER_BEFORE_STAGE_END_MS = 12_000L

    /**
     * 鼓勵的話。氣泡顯示文字，語音（設定可關）念同一句。
     * 不放會變動的數字（例如節拍），這樣才能事先錄成語音檔。
     */
    val MESSAGES = listOf(
        "今天也出門了，你真的好棒！",
        "步子小小的，剛剛好，繼續保持喔！",
        "慢到還能聊天，就是對的速度！",
        "肩膀放鬆，嘴角上揚，笑一個～",
        "呼吸順順的，你做得超好！",
        "跟著節拍，一二、一二，我們同步囉！",
        "不用追求快，穩穩地跑就很厲害了！",
        "腳掌輕輕落地，像踩在雲上一樣～",
        "抬頭挺胸，看向遠方，帥呆了！",
        "每一步都算數，你超讚的！",
        "太棒了！你已經打敗賴床的自己了！",
        "加油加油，再撐一下下，你可以的！",
        "哇，你的節奏好穩，我都想跟著你跑了！",
        "感覺到風了嗎？今天的早晨好舒服～",
        "身體正在謝謝你喔，繼續保持！",
        "慢慢來比較快，我們不急！",
        "好帥好帥，這個姿勢滿分！",
        "堅持下去，未來的你會謝謝現在的你！",
        "手臂放鬆，輕輕擺動，跑起來好輕鬆～",
        "你不是一個人，我陪你一起跑！",
        "這就是自律的樣子，超酷的！",
        "深呼吸，吸～吐～，節奏剛剛好！",
        "再堅持一下，好心情就在前面等你！",
        "今天的你，比昨天更棒一點點！",
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
    fun pickMessage(previous: Int, rand: Double): Pair<Int, String> {
        var i = (rand * MESSAGES.size).toInt().coerceIn(0, MESSAGES.size - 1)
        if (i == previous) i = (i + 1) % MESSAGES.size
        return i to MESSAGES[i]
    }
}
