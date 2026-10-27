package com.cornming.slowjog.workout

/**
 * 整段運動與超慢跑階段的計時。純 Kotlin，時間由呼叫端傳入，方便測試。
 * 暫停期間兩者都不累計；超慢跑時間只在「運動進行中且超慢跑階段開著」時累計。
 */
class WorkoutTimer {

    data class Summary(val durationSec: Int, val jogSec: Int)

    var started = false
        private set
    var running = false
        private set
    var jogging = false
        private set

    private var activeMs = 0L
    private var jogMs = 0L
    private var lastTick = 0L

    private fun accrue(now: Long) {
        if (running) {
            val dt = (now - lastTick).coerceAtLeast(0L)
            activeMs += dt
            if (jogging) jogMs += dt
        }
        lastTick = now
    }

    fun start(now: Long) {
        activeMs = 0L
        jogMs = 0L
        started = true
        running = true
        jogging = false
        lastTick = now
    }

    fun pause(now: Long) {
        if (!started || !running) return
        accrue(now)
        running = false
    }

    fun resume(now: Long) {
        if (!started || running) return
        lastTick = now
        running = true
    }

    /** 只有運動進行中才能開始超慢跑；成功回傳 true。 */
    fun startJog(now: Long): Boolean {
        if (!started || !running || jogging) return false
        accrue(now)
        jogging = true
        return true
    }

    fun endJog(now: Long) {
        if (!jogging) return
        accrue(now)
        jogging = false
    }

    fun elapsedMs(now: Long): Long {
        if (!started) return 0L
        return if (running) activeMs + (now - lastTick).coerceAtLeast(0L) else activeMs
    }

    fun jogMs(now: Long): Long {
        if (!started) return 0L
        return if (running && jogging) jogMs + (now - lastTick).coerceAtLeast(0L) else jogMs
    }

    /** 結束並回傳摘要，之後回到未開始狀態。 */
    fun finish(now: Long): Summary {
        accrue(now)
        val summary = Summary(
            durationSec = (activeMs / 1000).toInt(),
            jogSec = (jogMs / 1000).toInt(),
        )
        reset()
        return summary
    }

    fun reset() {
        started = false
        running = false
        jogging = false
        activeMs = 0L
        jogMs = 0L
    }
}
