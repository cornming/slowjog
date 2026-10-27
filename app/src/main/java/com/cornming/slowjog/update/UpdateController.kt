package com.cornming.slowjog.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 檢查 GitHub 上的最新版本，並在 App 內下載、交給系統安裝程式。
 * 唯一會連網的功能；只向 GitHub 取得版本資訊與 APK，不送出任何使用者資料。
 */
object UpdateController {

    const val LATEST_RELEASE_API = "https://api.github.com/repos/cornming/slowjog/releases/latest"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val info: ReleaseInfo) : State
        /** 尚未允許本 App 安裝應用程式；已帶使用者到系統設定。 */
        data class NeedPermission(val info: ReleaseInfo) : State
        data class Downloading(val info: ReleaseInfo, val progress: Float) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun busy() = _state.value is State.Checking || _state.value is State.Downloading

    /** 檢查最新版。silent 為 true（啟動時自動檢查）時，沒有新版或失敗都不打擾使用者。 */
    fun check(context: Context, silent: Boolean) {
        if (busy()) return
        val app = context.applicationContext
        if (!silent) _state.value = State.Checking
        scope.launch {
            try {
                val body = httpGet(LATEST_RELEASE_API)
                val info = ReleaseParser.parse(body) ?: throw IOException("無法讀取最新版本資訊")
                _state.value = if (Versions.isNewer(info.version, currentVersion(app))) {
                    State.Available(info)
                } else if (silent) {
                    State.Idle
                } else {
                    State.UpToDate
                }
            } catch (e: Exception) {
                _state.value = if (silent) State.Idle else State.Failed("檢查失敗：${e.message ?: "請確認網路連線"}")
            }
        }
    }

    /** 下載新版 APK 並開啟系統安裝畫面。 */
    fun startUpdate(context: Context) {
        val info = when (val s = _state.value) {
            is State.Available -> s.info
            is State.NeedPermission -> s.info
            else -> return
        }
        val app = context.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedPermission(info)
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            return
        }
        _state.value = State.Downloading(info, 0f)
        scope.launch {
            try {
                val file = download(app, info) { _state.value = State.Downloading(info, it) }
                install(app, file)
                _state.value = State.Available(info)
            } catch (e: Exception) {
                _state.value = State.Failed("下載失敗：${e.message ?: "請確認網路連線"}")
            }
        }
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SlowJog-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
        }

    private fun httpGet(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("伺服器回應 ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "slowjog-v${info.version}.apk")
        val conn = open(info.apkUrl)
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("伺服器回應 ${conn.responseCode}")
            }
            val total = if (info.apkSize > 0) info.apkSize else conn.contentLengthLong
            var done = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (info.apkSize > 0 && file.length() != info.apkSize) {
                file.delete()
                throw IOException("檔案不完整，請再試一次")
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    private fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
