package app.keybuilder.ime

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import app.keybuilder.engine.KeyboardEngine

/**
 * The buzz when a key is touched, at a strength you choose.
 *
 * Android's ready-made "keyboard tap" comes in one strength only, so this asks the vibration
 * motor directly: longer and harder for stronger. Phones that cannot vary how hard they
 * vibrate still vary how long. How long and how hard is decided in KeyboardEngine.buzzFor.
 */
object Haptics {

    fun buzz(context: Context, strength: Int) {
        val buzz = KeyboardEngine.buzzFor(strength) ?: return
        try {
            val vibrator = context.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            val amplitude = if (vibrator.hasAmplitudeControl()) buzz.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            vibrator.vibrate(VibrationEffect.createOneShot(buzz.milliseconds, amplitude))
        } catch (e: Exception) {
            // A phone without a vibration motor, or one that refuses. Typing goes on without it.
        }
    }
}
