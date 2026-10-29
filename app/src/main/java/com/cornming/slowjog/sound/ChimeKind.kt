package com.cornming.slowjog.sound

/** 運動完成時可以選的音效。全部由程式合成，不是真人錄音。 */
enum class ChimeKind(val id: String, val nameZh: String, val descZh: String) {
    FIREWORKS("fireworks", "煙火歡呼", "三發煙火加人群歡呼"),
    FANFARE("fanfare", "勝利號角", "銅管短促三響，接一個長音"),
    BELLS("bells", "清脆鐘聲", "叮叮叮，最後一聲低沉的咚"),
    CHIPTUNE("chiptune", "8-bit 過關", "復古遊戲機的過關音效"),
    MUSIC_BOX("musicbox", "音樂盒", "輕柔的音樂盒旋律"),
    APPLAUSE("applause", "掌聲歡呼", "熱烈掌聲加口哨聲");

    companion object {
        fun fromId(id: String?): ChimeKind? = values().firstOrNull { it.id == id }
    }
}

/** 設定裡選的完成音效：固定一種，或每次隨機。 */
sealed interface ChimeChoice {
    data class Fixed(val kind: ChimeKind) : ChimeChoice
    data object Random : ChimeChoice

    val id: String get() = when (this) {
        is Fixed -> kind.id
        Random -> RANDOM_ID
    }

    companion object {
        const val RANDOM_ID = "random"

        /** 預設每次隨機。舊版沒有這個設定，讀不到就當作隨機。 */
        fun fromId(id: String?): ChimeChoice =
            ChimeKind.fromId(id)?.let { Fixed(it) } ?: Random
    }
}

/** 挑這次要響哪一種。純邏輯，有單元測試。 */
object ChimeLogic {

    /** 固定就用那一種；隨機時避開上一次響的，[rand] 為 0 以上、小於 1 的亂數。 */
    fun pick(choice: ChimeChoice, last: ChimeKind?, rand: Double): ChimeKind = when (choice) {
        is ChimeChoice.Fixed -> choice.kind
        ChimeChoice.Random -> {
            val pool = ChimeKind.values().filter { it != last }
            pool[(rand * pool.size).toInt().coerceIn(0, pool.size - 1)]
        }
    }
}
