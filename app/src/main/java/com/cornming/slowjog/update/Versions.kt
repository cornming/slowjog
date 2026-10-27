package com.cornming.slowjog.update

/** 版本號比較：只看數字部分，例如 "v0.1.12"、"0.1.0-dev" 分別視為 0.1.12 與 0.1.0。 */
object Versions {

    fun parse(text: String): List<Int>? {
        val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        if (core.isEmpty()) return null
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts
    }

    /** latest 是否比 current 新；任一邊無法解析時一律視為不是新版，避免誤報。 */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
