package com.cornming.slowjog.update

import org.json.JSONException
import org.json.JSONObject

/** GitHub 上最新一版 Release 裡，更新需要的資訊。 */
data class ReleaseInfo(
    /** 不含開頭 v 的版本號，例如 0.1.3。 */
    val version: String,
    val apkUrl: String,
    /** APK 位元組數；未知時為 0。 */
    val apkSize: Long,
    val pageUrl: String,
)

object ReleaseParser {

    /** 解析 GitHub API「releases/latest」的回應；找不到版本或 APK 時回傳 null。 */
    fun parse(json: String): ReleaseInfo? {
        return try {
            val root = JSONObject(json)
            val tag = root.optString("tag_name").trim()
            if (tag.isEmpty()) return null
            val assets = root.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotEmpty()) {
                    return ReleaseInfo(
                        version = tag.removePrefix("v").removePrefix("V"),
                        apkUrl = url,
                        apkSize = asset.optLong("size", 0L),
                        pageUrl = root.optString("html_url"),
                    )
                }
            }
            null
        } catch (e: JSONException) {
            null
        }
    }
}
