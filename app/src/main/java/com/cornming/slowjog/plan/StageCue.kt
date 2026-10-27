package com.cornming.slowjog.plan

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.cornming.slowjog.sound.Tones

/** 階段切換時的提示：不同次數的「嗶」聲加對應次數的震動。 */
object StageCue {

    fun play(context: Context, kind: StageKind, sound: Boolean, vibrate: Boolean) {
        if (sound) Tones.play(Tones.beeps(kind.beepCount))
        if (vibrate) vibrate(context, kind.beepCount)
    }

    /** [count] 下短震，節奏和提示音一致。 */
    private fun vibrate(context: Context, count: Int) {
        val app = context.applicationContext
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = LongArray(count * 2) { if (it % 2 == 0) 0L else 130L }
        for (i in 2 until pattern.size step 2) pattern[i] = 110L
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
