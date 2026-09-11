package com.haptikit.app.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.haptikit.app.data.PatternEntity

/**
 * Thin wrapper so the rest of the app never touches the Android vibration
 * APIs directly. Handles the API-level split (VibratorManager on 31+,
 * plain Vibrator below, no amplitude control below 26).
 */
class VibrationEngine(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun play(pattern: PatternEntity) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                pattern.timings.toLongArray(),
                pattern.amplitudes.toIntArray(),
                -1 // no repeat
            )
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern.timings.toLongArray(), -1)
        }
    }

    fun playSingleTap(amplitude: Int = 200, durationMs: Long = 40) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    fun cancel() = vibrator.cancel()
}
