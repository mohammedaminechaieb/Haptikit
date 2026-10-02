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
 * plain Vibrator below).
 */
class VibrationEngine(context: Context) {

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    val hasVibrator: Boolean get() = vibrator.hasVibrator()

    /** Without amplitude control, every non-zero segment buzzes at full strength. */
    val hasAmplitudeControl: Boolean get() = vibrator.hasAmplitudeControl()

    fun play(pattern: PatternEntity) = play(pattern.timings, pattern.amplitudes)

    fun play(timings: List<Long>, amplitudes: List<Int>) {
        if (!vibrator.hasVibrator() || timings.isEmpty() || timings.size != amplitudes.size) return
        if (amplitudes.all { it == 0 }) return
        vibrator.cancel()
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                timings.map { it.coerceAtLeast(1) }.toLongArray(),
                amplitudes.map { it.coerceIn(0, 255) }.toIntArray(),
                -1 // no repeat
            )
        )
    }

    /** Starts a buzz that lasts until [cancel] — used while recording a held beat. */
    fun startHold(amplitude: Int) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(10_000, amplitude.coerceIn(1, 255)))
    }

    fun cancel() = vibrator.cancel()
}
