package app.keybuilder.ime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import app.keybuilder.R

/**
 * Plays key sounds.
 *
 * The sounds are the short files in res/raw (made by tools/build_sounds.py). They are
 * loaded once into a SoundPool, Android's player for short sounds that must start
 * instantly. "phone" is not a file: it is Android's own key click.
 *
 * Normally key sounds follow the phone: none while it is on silent or vibrate. With
 * "even when silent" they are played like music instead, which the phone does not mute.
 */
class KeySounds(private val context: Context) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var pool: SoundPool? = null
    private var poolIsMedia = false
    private val loaded = HashMap<String, Int>()

    /** The sounds that have finished loading and can be played. */
    private val ready = HashSet<Int>()

    /** A sound asked for before it had finished loading. It is played the moment it is ready. */
    private var waiting: Waiting? = null

    private class Waiting(val id: Int, val volume: Float)

    /** Gets the sounds ready so the first key press is not silent. Safe to call again and again. */
    fun prepare(evenWhenSilent: Boolean) {
        if (pool != null && poolIsMedia == evenWhenSilent) return
        release()
        try {
            val attributes = AudioAttributes.Builder()
                .setUsage(if (evenWhenSilent) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val made = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
            made.setOnLoadCompleteListener { player, id, status ->
                if (status == 0) {
                    ready.add(id)
                    val asked = waiting
                    if (asked != null && asked.id == id) {
                        waiting = null
                        player.play(id, asked.volume, asked.volume, 1, 0, 1f)
                    }
                }
            }
            for ((name, resource) in FILES) loaded[name] = made.load(context, resource, 1)
            pool = made
            poolIsMedia = evenWhenSilent
        } catch (e: Exception) {
            Log.w(TAG, "Could not load the key sounds", e)
        }
    }

    /** Plays [sound] (one of KEY_SOUNDS) at [volumePercent]. Does nothing for "none". */
    fun play(sound: String, volumePercent: Int, evenWhenSilent: Boolean) {
        if (sound == "none" || volumePercent <= 0) return
        try {
            if (!evenWhenSilent && audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val volume = volumePercent.coerceIn(0, 100) / 100f
            if (sound == "phone") {
                audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, volume)
                return
            }
            prepare(evenWhenSilent)
            val id = loaded[sound] ?: return
            if (id in ready) {
                pool?.play(id, volume, volume, 1, 0, 1f)
            } else {
                waiting = Waiting(id, volume) // still loading: it plays as soon as it can
            }
        } catch (e: Exception) {
            // A phone that will not play it. Typing goes on without the sound.
        }
    }

    fun release() {
        try {
            pool?.release()
        } catch (e: Exception) {
            // Already gone.
        }
        pool = null
        loaded.clear()
        ready.clear()
        waiting = null
    }

    private companion object {
        const val TAG = "KeySounds"

        val FILES = mapOf(
            "click" to R.raw.key_click,
            "pop" to R.raw.key_pop,
            "tick" to R.raw.key_tick,
            "thock" to R.raw.key_thock,
            "drop" to R.raw.key_drop,
            "beep" to R.raw.key_beep,
            "typewriter" to R.raw.key_typewriter,
        )
    }
}
