package com.cornming.slowjog.plan

/** 階段結束前的倒數，以及換階段時要不要自動開始或停止超慢跑。純邏輯，有單元測試。 */
object StageTransition {

    /** 階段結束前倒數幾秒。 */
    const val COUNTDOWN_SEC = 5

    /**
     * 目前該顯示與念出的倒數數字（5、4、3、2、1）；還沒到倒數、階段已結束、
     * 或這是最後一個階段（後面沒有下一階段）時回傳 null。
     */
    fun countdown(remainingMs: Long, hasNext: Boolean): Int? {
        if (!hasNext || remainingMs <= 0 || remainingMs > COUNTDOWN_SEC * 1000L) return null
        return ((remainingMs + 999) / 1000).toInt()
    }

    private val NUMBERS = listOf("零", "一", "二", "三", "四", "五")

    /** 倒數用語音念的字。 */
    fun spoken(number: Int): String = NUMBERS.getOrElse(number) { number.toString() }

    enum class JogAction { NONE, START, END }

    /**
     * 換到 [newKind]（null 代表課表全部完成）時，超慢跑要怎麼動：
     * 進入超慢跑階段就自動開始；離開超慢跑階段（進入肌力、伸展或結束）而還在跑就自動停止。
     */
    fun jogAction(newKind: StageKind?, jogging: Boolean): JogAction = when {
        newKind == StageKind.JOG && !jogging -> JogAction.START
        newKind != StageKind.JOG && jogging -> JogAction.END
        else -> JogAction.NONE
    }
}
