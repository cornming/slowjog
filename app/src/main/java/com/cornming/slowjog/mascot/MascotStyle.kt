package com.cornming.slowjog.mascot

/** 陪跑小人物的外型。骨架與動作都一樣，只有外型不同。 */
enum class MascotStyle(val id: String, val nameZh: String, val kindZh: String, val pixelArt: Boolean) {
    BLUE("blue", "小藍", "3D Q 版", false),
    ORANGE("orange", "小橘", "卡通描邊", false),
    PIXEL("pixel", "小點", "像素風", true),
    SHIBA("shiba", "阿柴", "狗狗跑者", false);

    companion object {
        fun fromId(id: String?): MascotStyle = values().firstOrNull { it.id == id } ?: BLUE
    }
}
