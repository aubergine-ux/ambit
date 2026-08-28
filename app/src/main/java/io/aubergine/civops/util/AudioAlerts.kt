package io.aubergine.civops.util

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.annotation.MainThread
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short synthesised tones for hands-free operation.
 *
 * Deliberately generated rather than shipped as audio assets: it keeps the APK small, and
 * a tone that is obviously a tone is less likely to be mistaken for a notification from
 * something else while the phone is on a dashboard mount.
 *
 * Distinct pitches so the operator can tell the events apart without looking at the screen.
 */
@Singleton
class AudioAlerts @Inject constructor() {

    private var generator: ToneGenerator? = null

    enum class Cue(val tone: Int, val durationMs: Int, val repeats: Int) {
        /** A device we have not logged before entered the picture. */
        NEW_DEVICE(ToneGenerator.TONE_PROP_BEEP, 90, 1),

        /** A device whose vendor sits in the priority bucket. Two rising beeps. */
        PRIORITY(ToneGenerator.TONE_PROP_BEEP2, 120, 2),

        /** The correlator produced a following verdict. The one that should interrupt you. */
        TRACKER(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260, 3),

        /** Scanning started or stopped. */
        STATE_CHANGE(ToneGenerator.TONE_PROP_ACK, 70, 1),
    }

    @MainThread
    fun play(cue: Cue) {
        val gen = ensureGenerator() ?: return
        runCatching {
            repeat(cue.repeats) { gen.startTone(cue.tone, cue.durationMs) }
        }
    }

    private fun ensureGenerator(): ToneGenerator? {
        generator?.let { return it }
        // Some devices throw here when the audio HAL is busy. A missing beep is not worth
        // taking the scan session down for.
        return runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
        }.getOrNull()?.also { generator = it }
    }

    fun release() {
        runCatching { generator?.release() }
        generator = null
    }

    private companion object {
        const val VOLUME = 80
    }
}
