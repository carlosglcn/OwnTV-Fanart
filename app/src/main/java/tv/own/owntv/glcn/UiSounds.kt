package tv.own.owntv.glcn

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.own.owntv.core.theme.Season
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * glcn: menu sounds (Settings → Appearance → Menu sounds) and the opening chime. All of it synthesised
 * here — a few milliseconds of sine and bell, no audio files — and played as system sonification, so it
 * mixes under nothing: it is silent while anything plays (the player, the mini player, sound only),
 * where it could disturb a receiver taking Dolby untouched.
 */
object UiSounds {

    private const val RATE = 44_100
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 0 off, 1 soft, 2 normal. */
    @Volatile var level: Int = 0

    /** True while something plays: no sounds then. */
    @Volatile var suppressed: Boolean = false
        set(value) {
            // A player coming and going can change the audio route (HDMI, Dolby) and leave a kept track
            // dead and silent; the next sounds are rebuilt once the player is gone.
            if (field && !value) dropTracks()
            field = value
        }

    /** 0 classic, 1 crystal, 2 wood, 3 bubble, 4 soft, 5 kalimba. Changing it drops the cached sounds so the next ones are built in the new style. */
    @Volatile var style: Int = 0
        set(value) {
            if (value == field) return
            field = value
            dropTracks()
        }

    private fun dropTracks() {
        listOf(move, select, back).forEach { runCatching { it?.release() } }
        move = null; select = null; back = null
    }

    /** Plays the OK sound once, so a new style is heard as soon as it is chosen. */
    fun preview() {
        if (level == 0) return
        play(Kind.SELECT)
    }

    private var move: AudioTrack? = null
    private var select: AudioTrack? = null
    private var back: AudioTrack? = null
    private var lastMove = 0L

    /** Called by the activity for every key: D-pad ticks, OK and Back. */
    fun onKey(event: KeyEvent) {
        if (level == 0 || suppressed) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // A held key repeats fast: one tick every so often is enough.
                    val now = SystemClock.uptimeMillis()
                    if (now - lastMove < 70) return
                    lastMove = now
                    play(Kind.MOVE)
                }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) play(Kind.SELECT)
            KeyEvent.KEYCODE_BACK ->
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) play(Kind.BACK)
        }
    }

    /** The opening chime — the season's own when a seasonal theme is on. */
    fun startup(season: Season) {
        scope.launch {
            val pcm = when (season) {
                Season.HALLOWEEN -> halloween()
                Season.CHRISTMAS -> christmas()
                Season.NEW_YEAR -> newYear()
                Season.CARNIVAL -> carnival()
                Season.VALENTINE -> valentine()
                Season.NONE -> chime()
            }
            val track = trackOf(pcm, gain = 0.5f) ?: return@launch
            runCatching { track.play() }
            // Static tracks are released once played out.
            kotlinx.coroutines.delay(pcm.size * 1000L / RATE + 500)
            runCatching { track.release() }
        }
    }

    // --- Playing ------------------------------------------------------------------------------------

    private fun build(make: () -> ShortArray): AudioTrack? = trackOf(make(), gain = 1f)

    private enum class Kind { MOVE, SELECT, BACK }

    private fun trackFor(kind: Kind): AudioTrack? = when (kind) {
        Kind.MOVE -> move ?: build { tick() }.also { move = it }
        Kind.SELECT -> select ?: build { pop() }.also { select = it }
        Kind.BACK -> back ?: build { swoosh() }.also { back = it }
    }

    private fun forget(kind: Kind) {
        when (kind) {
            Kind.MOVE -> { runCatching { move?.release() }; move = null }
            Kind.SELECT -> { runCatching { select?.release() }; select = null }
            Kind.BACK -> { runCatching { back?.release() }; back = null }
        }
    }

    /**
     * Plays a kept sound. A kept AudioTrack can die under us (the audio server restarted, the route changed
     * after a player): then every later call failed inside runCatching and the sounds were simply gone
     * until the app restarted. Now a failed or dead track is rebuilt and tried once more.
     */
    private fun play(kind: Kind) {
        if (!tryPlay(trackFor(kind))) {
            forget(kind)
            tryPlay(trackFor(kind))
        }
    }

    private fun tryPlay(t: AudioTrack?): Boolean {
        if (t == null || t.state != AudioTrack.STATE_INITIALIZED) return false
        val gain = if (level == 1) 0.35f else 0.7f
        return runCatching {
            t.setVolume(gain)
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            if (t.reloadStaticData() != AudioTrack.SUCCESS) return false
            t.play()
            true
        }.getOrDefault(false)
    }

    private fun trackOf(pcm: ShortArray, gain: Float): AudioTrack? = runCatching {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setVolume(gain)
        track
    }.getOrNull()

    // --- Synthesis ----------------------------------------------------------------------------------

    private class Mix(seconds: Double) {
        val buf = FloatArray((seconds * RATE).toInt())

        /** A bell-like note: a sine and a few soft partials, struck and decaying. */
        fun bell(atSec: Double, freq: Double, durSec: Double, amp: Double, decay: Double = 5.0, partials: DoubleArray = doubleArrayOf(1.0, 0.35, 0.12), attack: Double = 0.004) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val t = i.toDouble() / RATE
                val attackEnv = (t / attack).coerceAtMost(1.0)
                val env = attackEnv * exp(-decay * t)
                var v = 0.0
                partials.forEachIndexed { k, a -> v += a * sin(2 * PI * freq * (k + 1) * t) }
                buf[idx] += (amp * env * v).toFloat()
            }
        }

        /** A drop of water: a sine whose pitch glides [from] → [to] while it fades. */
        fun drop(atSec: Double, from: Double, to: Double, durSec: Double, amp: Double, decay: Double) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            var phase = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val t = i.toDouble() / RATE
                val f = from + (to - from) * (t / durSec)
                phase += 2 * PI * f / RATE
                val env = (t / 0.003).coerceAtMost(1.0) * exp(-decay * t)
                buf[idx] += (amp * env * sin(phase)).toFloat()
            }
        }

        /** A breath of air: noise softened by a one-pole low-pass at [cutoff] Hz, swelling in and fading out. */
        fun air(atSec: Double, durSec: Double, amp: Double, cutoff: Double) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            val a = 1 - exp(-2 * PI * cutoff / RATE)
            var y = 0.0
            val rnd = java.util.Random(7)
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                y += a * ((rnd.nextDouble() * 2 - 1) - y)
                val p = i.toDouble() / n
                buf[idx] += (amp * 3.0 * y * sin(PI * p) * (1 - p * 0.4)).toFloat()
            }
        }

        /** A sliding tone with vibrato — the theremin of an old horror film. */
        fun glide(atSec: Double, from: Double, to: Double, durSec: Double, amp: Double) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            var phase = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val t = i.toDouble() / RATE
                val p = t / durSec
                val f = (from + (to - from) * p) * (1 + 0.012 * sin(2 * PI * 5.5 * t))
                phase += 2 * PI * f / RATE
                val env = sin(PI * p).coerceAtLeast(0.0)
                buf[idx] += (amp * env * sin(phase)).toFloat()
            }
        }

        fun pcm(): ShortArray {
            val peak = buf.maxOfOrNull { kotlin.math.abs(it) }?.coerceAtLeast(1f) ?: 1f
            val norm = if (peak > 0.95f) 0.95f / peak else 1f
            return ShortArray(buf.size) { (buf[it] * norm * Short.MAX_VALUE).toInt().toShort() }
        }
    }

    private fun tick() = when (style) {
        1 -> Mix(0.04).apply { bell(0.0, 3200.0, 0.04, 0.14, decay = 200.0, partials = doubleArrayOf(1.0, 0.22)) }
        2 -> Mix(0.05).apply { bell(0.0, 620.0, 0.05, 0.3, decay = 90.0, partials = doubleArrayOf(1.0, 0.4, 0.15)) }
        3 -> Mix(0.04).apply { drop(0.0, 900.0, 1500.0, 0.04, 0.24, decay = 70.0) }
        4 -> Mix(0.06).apply { bell(0.0, 520.0, 0.06, 0.24, decay = 55.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.007) }
        // 5 — kalimba: a plucked tine, a soft fundamental with a little of its fourth harmonic.
        5 -> Mix(0.09).apply { bell(0.0, 880.0, 0.09, 0.2, decay = 48.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        else -> Mix(0.03).apply { bell(0.0, 1900.0, 0.03, 0.18, decay = 160.0, partials = doubleArrayOf(1.0)) }
    }.pcm()

    private fun pop() = when (style) {
        1 -> Mix(0.22).apply { bell(0.0, 1568.0, 0.2, 0.2, decay = 24.0, partials = doubleArrayOf(1.0, 0.25)); bell(0.05, 2349.0, 0.17, 0.17, decay = 28.0, partials = doubleArrayOf(1.0, 0.2)) }
        2 -> Mix(0.16).apply { bell(0.0, 392.0, 0.15, 0.34, decay = 28.0, partials = doubleArrayOf(1.0, 0.45, 0.2)); bell(0.045, 587.0, 0.12, 0.28, decay = 34.0, partials = doubleArrayOf(1.0, 0.4, 0.15)) }
        3 -> Mix(0.12).apply { drop(0.0, 600.0, 1500.0, 0.1, 0.32, decay = 26.0) }
        4 -> Mix(0.3).apply { bell(0.0, 523.0, 0.28, 0.26, decay = 16.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.009); bell(0.07, 659.0, 0.22, 0.22, decay = 18.0, partials = doubleArrayOf(1.0, 0.05), attack = 0.009) }
        5 -> Mix(0.4).apply { bell(0.0, 659.0, 0.36, 0.3, decay = 11.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003); bell(0.07, 988.0, 0.32, 0.26, decay = 13.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        else -> Mix(0.09).apply { bell(0.0, 1250.0, 0.09, 0.3, decay = 45.0); bell(0.012, 1870.0, 0.07, 0.15, decay = 60.0) }
    }.pcm()

    private fun swoosh() = when (style) {
        1 -> Mix(0.16).apply { bell(0.0, 1175.0, 0.15, 0.18, decay = 32.0, partials = doubleArrayOf(1.0, 0.22)) }
        2 -> Mix(0.14).apply { bell(0.0, 330.0, 0.13, 0.34, decay = 36.0, partials = doubleArrayOf(1.0, 0.45, 0.2)) }
        3 -> Mix(0.12).apply { drop(0.0, 1300.0, 520.0, 0.1, 0.28, decay = 26.0) }
        4 -> Mix(0.26).apply { bell(0.0, 440.0, 0.24, 0.26, decay = 18.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.009); bell(0.07, 349.0, 0.2, 0.22, decay = 20.0, partials = doubleArrayOf(1.0, 0.05), attack = 0.009) }
        5 -> Mix(0.34).apply { bell(0.0, 784.0, 0.3, 0.26, decay = 13.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003); bell(0.07, 523.0, 0.28, 0.24, decay = 14.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        else -> Mix(0.09).apply { glide(0.0, 1100.0, 650.0, 0.09, 0.22) }
    }.pcm()

    private fun note(semitonesFromA4: Int) = 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)

    /** Every day: four soft rising bell notes (C E G C). */
    private fun chime() = Mix(1.6).apply {
        listOf(3, 7, 10, 15).forEachIndexed { i, s -> bell(i * 0.12, note(s), 1.3, 0.26, decay = 3.2) }
    }.pcm()

    /** Halloween: a low minor chord and a theremin that rises and wavers. */
    private fun halloween() = Mix(2.2).apply {
        bell(0.0, note(-24), 2.0, 0.22, decay = 1.6) // A2
        bell(0.05, note(-21), 2.0, 0.16, decay = 1.6) // C3
        bell(0.1, note(-17), 2.0, 0.14, decay = 1.6) // E3
        glide(0.35, note(0), note(7), 1.6, 0.16) // A4 up to E5, wavering
    }.pcm()

    /** Christmas: the first notes of "Jingle Bells" on little bells, with a sleigh-bell shimmer. */
    private fun christmas() = Mix(1.8).apply {
        val bells = doubleArrayOf(1.0, 0.5, 0.25, 0.12)
        listOf(0.0 to 7, 0.18 to 7, 0.36 to 7).forEach { (t, s) -> bell(t, note(s), 0.6, 0.24, decay = 5.0, partials = bells) }
        listOf(0.62 to 7, 0.80 to 7, 0.98 to 7).forEach { (t, s) -> bell(t, note(s), 0.6, 0.24, decay = 5.0, partials = bells) }
        repeat(10) { k -> bell(k * 0.11, 5200.0 + (k % 3) * 300, 0.06, 0.05, decay = 70.0, partials = doubleArrayOf(1.0)) }
    }.pcm()

    /** New Year's Eve: a quick sparkle rising to a bright chord. */
    private fun newYear() = Mix(1.8).apply {
        listOf(3, 7, 10, 15, 19, 22).forEachIndexed { i, s -> bell(i * 0.07, note(s), 0.9, 0.18, decay = 4.0) }
        bell(0.5, note(15), 1.2, 0.18, decay = 2.5); bell(0.5, note(19), 1.2, 0.14, decay = 2.5); bell(0.5, note(22), 1.2, 0.12, decay = 2.5)
    }.pcm()

    /** Carnival: a bouncy little fanfare. */
    private fun carnival() = Mix(1.3).apply {
        listOf(0.0 to 3, 0.1 to 7, 0.2 to 10, 0.3 to 15, 0.45 to 10, 0.55 to 15).forEach { (t, s) -> bell(t, note(s), 0.5, 0.22, decay = 6.0) }
    }.pcm()

    /** Valentine's: two warm notes and a soft chord. */
    private fun valentine() = Mix(1.8).apply {
        bell(0.0, note(3), 1.4, 0.22, decay = 2.4); bell(0.2, note(10), 1.4, 0.2, decay = 2.4)
        bell(0.45, note(7), 1.3, 0.14, decay = 2.0); bell(0.45, note(15), 1.3, 0.12, decay = 2.0)
    }.pcm()
}
