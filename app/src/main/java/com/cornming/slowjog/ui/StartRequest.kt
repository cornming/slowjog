package com.cornming.slowjog.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 從通知上按「開始運動」時，MainActivity 在這裡留下一個請求，
 * 首頁看到後執行和按下「開始運動」按鈕一樣的流程（包含詢問音樂），然後清掉請求。
 */
object StartRequest {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() { _pending.value = true }
    fun clear() { _pending.value = false }
}
