package com.cornming.slowjog.scenery

/** 跑步時的風景。 */
enum class Scene(val id: String, val nameZh: String) {
    RIVERSIDE("riverside", "河濱公園"),
    CITY("city", "城市清晨"),
    FOREST("forest", "森林步道"),
    SEASIDE("seaside", "海邊");

    companion object {
        fun fromId(id: String?): Scene? = values().firstOrNull { it.id == id }
    }
}

/** 設定裡選的風景：固定一種，或每天輪換。 */
sealed interface ScenePref {
    data class Fixed(val scene: Scene) : ScenePref
    data object Daily : ScenePref

    val id: String get() = when (this) {
        is Fixed -> scene.id
        Daily -> DAILY_ID
    }

    companion object {
        const val DAILY_ID = "daily"
        fun fromId(id: String?): ScenePref =
            if (id == DAILY_ID) Daily else Fixed(Scene.fromId(id) ?: Scene.RIVERSIDE)
    }
}

/** 風景的規則。純 Kotlin，有單元測試。 */
object SceneryLogic {

    /** 風景設計稿的單位：一段風景寬 600、高 300，畫面依高度等比例縮放後左右接續重複。 */
    const val TILE_W = 600f
    const val TILE_H = 300f

    /** 地面每前進 1 公尺捲動多少設計單位（180 BPM、步幅 50 公分約每秒 135 單位）。 */
    const val UNITS_PER_METER = 90f

    /** 每天輪換：依日期（epoch day）依序換，同一天固定同一種。 */
    fun sceneFor(pref: ScenePref, epochDay: Long): Scene = when (pref) {
        is ScenePref.Fixed -> pref.scene
        ScenePref.Daily -> Scene.values()[Math.floorMod(epochDay, Scene.values().size.toLong()).toInt()]
    }

    /**
     * 某一層目前往左捲了多少設計單位（0 以上、小於一段寬）。[speed] 是相對地面的速度，
     * 遠山慢、地面 1；跟著估算距離走，所以暫停時不動、節拍越快捲得越快。
     */
    fun layerOffset(meters: Double, speed: Float): Float {
        val raw = (meters * UNITS_PER_METER * speed) % TILE_W
        return (if (raw < 0) raw + TILE_W else raw).toFloat()
    }
}
