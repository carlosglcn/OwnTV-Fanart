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
    private const val EMBER = 7
    private const val EMBER_TICKS = 4
    private const val PAD_PARTIALS = 5
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /**
     * glcn: the sounds are started on their own quiet thread, never on the main one — stopping and restarting an
     * AudioTrack waits on the audio server, and while the box was busy (a trailer starting, memory short) that wait
     * froze a key press for seconds (5.7 s seen in a report). One thread, so the sounds stay in order.
     */
    private val soundThread by lazy { android.os.HandlerThread("glcn-sounds").apply { start() } }
    private val soundHandler by lazy { android.os.Handler(soundThread.looper) }

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

    /** 0 classic, 1 crystal, 2 wood, 3 bubble, 4 soft, 5 kalimba, 6 natural (recorded: Kenney UI sounds, CC0; the opening chime is our own), 7 ember (warm and low, made for the Brasa style). Changing it drops the cached sounds so the next ones are built in the new style. */
    @Volatile var style: Int = 0
        set(value) {
            if (value == field) return
            field = value
            dropTracks()
        }

    // Ember's takes of its move sound; touched on the sounds' thread only.
    private val takes = arrayOfNulls<AudioTrack>(EMBER_TICKS)
    private var lastTake = 0

    private fun dropTracks() {
        // On the sounds' own thread, after anything still being played there.
        soundHandler.post {
            (listOf(move, select, back) + takes).forEach { runCatching { it?.release() } }
            move = null; select = null; back = null; takes.fill(null)
        }
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
                Season.NONE -> (if (style == NATURAL) raw(tv.own.owntv.R.raw.glcn_snd_open) else null) ?: if (style == EMBER) emberChime() else chime()
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
        // glcn: Ember moves through a few takes of its knock, never the same twice running.
        Kind.MOVE -> if (style == EMBER) {
            val take = (lastTake + 1 + (SystemClock.uptimeMillis() % (EMBER_TICKS - 1)).toInt()) % EMBER_TICKS
            // The take before is cut short, as one kept sound would be: two takes ringing over each other read as an echo.
            runCatching { takes[lastTake]?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.stop() }
            lastTake = take
            takes[take] ?: build { emberTick(take).pcm() }.also { takes[take] = it }
        } else move ?: build { tick() }.also { move = it }
        Kind.SELECT -> select ?: build { pop() }.also { select = it }
        Kind.BACK -> back ?: build { swoosh() }.also { back = it }
    }

    private fun forget(kind: Kind) {
        when (kind) {
            Kind.MOVE -> { runCatching { move?.release() }; move = null; takes.forEach { runCatching { it?.release() } }; takes.fill(null) }
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
        soundHandler.post {
            if (!tryPlay(trackFor(kind))) {
                forget(kind)
                tryPlay(trackFor(kind))
            }
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

        /**
         * glcn: a struck wooden bar, as a marimba's — what makes a made sound pass for a real one: partials that are not
         * exact multiples (1, 3.93, 9.6) and die at their own speed (the high ones first, so the note mellows as it
         * rings), a second voice a hair out of tune (the slow shimmer of a real bar), a pitch that settles in the first
         * instants, and the mallet's own soft thud and breath at the start.
         */
        fun struck(atSec: Double, freq: Double, durSec: Double, amp: Double, decay: Double, hard: Double = 1.0, seed: Long = 3, open: Double = 0.0) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            val ratios = doubleArrayOf(1.0, 3.93, 9.6)
            // [open] (0–1) lets more of the upper partials and of the mallet's click through, and less of its low thud:
            // a short knock with them held back sounds shut in, as if heard through a bottle.
            val levels = doubleArrayOf(1.0, (0.22 + 0.2 * open) * hard, (0.07 + 0.09 * open) * hard)
            val decays = doubleArrayOf(decay, decay * 2.7, decay * 5.5)
            val phases = DoubleArray(3)
            var twin = 0.0
            var thud = 0.0
            val rnd = java.util.Random(seed)
            val lp = 1 - exp(-2 * PI * (2600.0 + 4400.0 * open) / RATE)
            var noise = 0.0
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val t = i.toDouble() / RATE
                // The bar is a touch sharp while the mallet is still on it.
                val f = freq * (1 + 0.012 * exp(-t / 0.012))
                var v = 0.0
                for (k in 0 until 3) {
                    phases[k] += 2 * PI * f * ratios[k] / RATE
                    v += levels[k] * exp(-decays[k] * t) * sin(phases[k])
                }
                twin += 2 * PI * f * 1.0035 / RATE
                v += 0.32 * exp(-decay * 0.9 * t) * sin(twin)
                // The mallet: a low thud and a little filtered noise, gone in a few milliseconds.
                thud += 2 * PI * f * 0.5 / RATE
                v += 0.3 * (1 - 0.7 * open) * hard * exp(-t / 0.006) * sin(thud)
                noise += lp * ((rnd.nextDouble() * 2 - 1) - noise)
                v += 0.5 * hard * exp(-t / 0.004) * noise
                val attack = (t / 0.0025).coerceAtMost(1.0)
                buf[idx] += (amp * attack * v).toFloat()
            }
        }

        /**
         * glcn: a short, dry tap — for a sound heard dozens of times in a row. One clear note with a little of its
         * octave, gone in a few hundredths of a second, and the fingertip's click at its start (noise with its low part
         * taken out, so it ticks rather than thumps). Nothing under the note and nothing after it: a low thud, a second
         * voice or a room's echoes, in so short a sound, are what make it seem to come from inside a bottle.
         */
        fun tap(freq: Double, durSec: Double, amp: Double, decay: Double, click: Double, seed: Long) {
            val n = (durSec * RATE).toInt().coerceAtMost(buf.size)
            val rnd = java.util.Random(seed)
            val lp = 1 - exp(-2 * PI * 1800.0 / RATE)
            var low = 0.0
            var phase = 0.0
            for (i in 0 until n) {
                val t = i.toDouble() / RATE
                phase += 2 * PI * freq * (1 + 0.006 * exp(-t / 0.008)) / RATE
                val white = rnd.nextDouble() * 2 - 1
                low += lp * (white - low)
                val note = exp(-decay * t) * (sin(phase) + 0.2 * exp(-decay * t) * sin(2 * phase))
                val tick = click * exp(-t / 0.0022) * (white - low)
                buf[i] += (amp * (t / 0.0015).coerceAtMost(1.0) * (note + tick)).toFloat()
            }
        }

        /**
         * glcn: a held, swelling note, as a film score's strings or low brass — two voices a hair apart (the width of a
         * section playing together), each a handful of soft harmonics, rising slowly over [attack] seconds and letting
         * go over [release]. Nothing is struck: it is the sound of a cinema's logo, not of a bell.
         */
        fun pad(atSec: Double, freq: Double, durSec: Double, amp: Double, attack: Double, release: Double, bright: Double = 0.5) {
            val start = (atSec * RATE).toInt()
            val n = (durSec * RATE).toInt()
            val voices = doubleArrayOf(0.9985, 1.0015)
            val phases = Array(voices.size) { DoubleArray(PAD_PARTIALS) }
            for (i in 0 until n) {
                val idx = start + i
                if (idx >= buf.size) break
                val t = i.toDouble() / RATE
                val up = (t / attack).coerceAtMost(1.0)
                val down = ((durSec - t) / release).coerceIn(0.0, 1.0)
                // Smooth both ends (no click) and open up a little as it swells, the way a section grows louder.
                val env = up * up * (3 - 2 * up) * down * down * (3 - 2 * down)
                var v = 0.0
                for (vi in voices.indices) for (k in 0 until PAD_PARTIALS) {
                    phases[vi][k] += 2 * PI * freq * voices[vi] * (k + 1) / RATE
                    v += Math.pow(bright * (0.6 + 0.4 * up), k.toDouble()) / (k + 1) * sin(phases[vi][k])
                }
                buf[idx] += (amp * env * v * 0.5).toFloat()
            }
        }

        /** glcn: a small room around what is in the buffer — a few early, duller echoes, so it does not sound dry. */
        fun room(amount: Double = 1.0) {
            val dry = buf.copyOf()
            val taps = listOf(0.0113 to 0.2, 0.0171 to 0.14, 0.0237 to 0.1, 0.0319 to 0.06)
            val lp = (1 - exp(-2 * PI * 2200.0 / RATE)).toFloat()
            for ((delay, gain) in taps) {
                val d = (delay * RATE).toInt()
                var y = 0f
                for (i in d until buf.size) {
                    y += lp * (dry[i - d] - y)
                    buf[i] += (gain * amount).toFloat() * y
                }
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

    // --- Recorded (style 6, "Natural") --------------------------------------------------------------

    @Volatile private var app: android.content.Context? = null

    /** glcn: the recorded sounds are read from the app's resources; the activity hands its context over once. */
    fun init(context: android.content.Context) { app = context.applicationContext }

    /** A 16-bit mono 44.1 kHz WAV from res/raw as samples (its "data" chunk), or null if it cannot be read. */
    private fun raw(id: Int): ShortArray? = runCatching {
        val bytes = app?.resources?.openRawResource(id)?.use { it.readBytes() } ?: return null
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val idTag = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bb.getInt(pos + 4)
            if (idTag == "data") {
                val n = (minOf(size, bytes.size - pos - 8)) / 2
                val out = ShortArray(n)
                bb.position(pos + 8)
                bb.asShortBuffer().get(out, 0, n)
                return out
            }
            pos += 8 + size + (size and 1)
        }
        null
    }.getOrNull()

    private const val NATURAL = 6

    private fun tick(): ShortArray = (if (style == NATURAL) raw(tv.own.owntv.R.raw.glcn_snd_move) else null) ?: synthTick()
    private fun pop(): ShortArray = (if (style == NATURAL) raw(tv.own.owntv.R.raw.glcn_snd_ok) else null) ?: synthPop()
    private fun swoosh(): ShortArray = (if (style == NATURAL) raw(tv.own.owntv.R.raw.glcn_snd_back) else null) ?: synthSwoosh()

    private fun synthTick() = when (style) {
        1 -> Mix(0.04).apply { bell(0.0, 3200.0, 0.04, 0.14, decay = 200.0, partials = doubleArrayOf(1.0, 0.22)) }
        2 -> Mix(0.05).apply { bell(0.0, 620.0, 0.05, 0.3, decay = 90.0, partials = doubleArrayOf(1.0, 0.4, 0.15)) }
        3 -> Mix(0.04).apply { drop(0.0, 900.0, 1500.0, 0.04, 0.24, decay = 70.0) }
        4 -> Mix(0.06).apply { bell(0.0, 520.0, 0.06, 0.24, decay = 55.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.007) }
        // 5 — kalimba: a plucked tine, a soft fundamental with a little of its fourth harmonic.
        5 -> Mix(0.09).apply { bell(0.0, 880.0, 0.09, 0.2, decay = 48.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        // 7 — ember: a low, soft knock on wood (see emberTick).
        7 -> emberTick(0)
        else -> Mix(0.03).apply { bell(0.0, 1900.0, 0.03, 0.18, decay = 160.0, partials = doubleArrayOf(1.0)) }
    }.pcm()

    /**
     * glcn: Ember's tap, in one of [EMBER_TICKS] takes: no two strikes of a real thing sound alike, and the
     * same sample at every step of a long list is what gives a made sound away. Each take is a shade higher or lower
     * and a shade harder or softer.
     */
    private fun emberTick(take: Int): Mix {
        val tune = doubleArrayOf(1.0, 0.985, 1.018, 0.996)[take % EMBER_TICKS]
        val hard = doubleArrayOf(1.0, 0.8, 0.9, 1.15)[take % EMBER_TICKS]
        return Mix(0.09).apply { tap(440.0 * tune, 0.085, 0.3, decay = 70.0, click = 0.22 * hard, seed = 11L + take) }
    }

    private fun synthPop() = when (style) {
        1 -> Mix(0.22).apply { bell(0.0, 1568.0, 0.2, 0.2, decay = 24.0, partials = doubleArrayOf(1.0, 0.25)); bell(0.05, 2349.0, 0.17, 0.17, decay = 28.0, partials = doubleArrayOf(1.0, 0.2)) }
        2 -> Mix(0.16).apply { bell(0.0, 392.0, 0.15, 0.34, decay = 28.0, partials = doubleArrayOf(1.0, 0.45, 0.2)); bell(0.045, 587.0, 0.12, 0.28, decay = 34.0, partials = doubleArrayOf(1.0, 0.4, 0.15)) }
        3 -> Mix(0.12).apply { drop(0.0, 600.0, 1500.0, 0.1, 0.32, decay = 26.0) }
        4 -> Mix(0.3).apply { bell(0.0, 523.0, 0.28, 0.26, decay = 16.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.009); bell(0.07, 659.0, 0.22, 0.22, decay = 18.0, partials = doubleArrayOf(1.0, 0.05), attack = 0.009) }
        5 -> Mix(0.4).apply { bell(0.0, 659.0, 0.36, 0.3, decay = 11.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003); bell(0.07, 988.0, 0.32, 0.26, decay = 13.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        // Ember: a cinema's "confirm", kept clean — a soft low knock and two round notes a fifth apart (D, A) that
        // bloom and fade. Nearly pure tones, no echoes and no noise: the first try (detuned voices, a deep sub and a
        // room) came out metallic and distorted on the TV's speakers.
        7 -> Mix(0.45).apply {
            drop(0.0, 120.0, 74.0, 0.16, 0.26, decay = 16.0)
            bell(0.0, 293.66, 0.42, 0.22, decay = 7.5, partials = doubleArrayOf(1.0, 0.1), attack = 0.018)
            bell(0.03, 440.0, 0.4, 0.13, decay = 8.5, partials = doubleArrayOf(1.0, 0.06), attack = 0.022)
        }
        else -> Mix(0.09).apply { bell(0.0, 1250.0, 0.09, 0.3, decay = 45.0); bell(0.012, 1870.0, 0.07, 0.15, decay = 60.0) }
    }.pcm()

    private fun synthSwoosh() = when (style) {
        1 -> Mix(0.16).apply { bell(0.0, 1175.0, 0.15, 0.18, decay = 32.0, partials = doubleArrayOf(1.0, 0.22)) }
        2 -> Mix(0.14).apply { bell(0.0, 330.0, 0.13, 0.34, decay = 36.0, partials = doubleArrayOf(1.0, 0.45, 0.2)) }
        3 -> Mix(0.12).apply { drop(0.0, 1300.0, 520.0, 0.1, 0.28, decay = 26.0) }
        4 -> Mix(0.26).apply { bell(0.0, 440.0, 0.24, 0.26, decay = 18.0, partials = doubleArrayOf(1.0, 0.06), attack = 0.009); bell(0.07, 349.0, 0.2, 0.22, decay = 20.0, partials = doubleArrayOf(1.0, 0.05), attack = 0.009) }
        5 -> Mix(0.34).apply { bell(0.0, 784.0, 0.3, 0.26, decay = 13.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003); bell(0.07, 523.0, 0.28, 0.24, decay = 14.0, partials = doubleArrayOf(1.0, 0.0, 0.0, 0.16), attack = 0.003) }
        // Ember: the same warmth falling away.
        7 -> Mix(0.34).apply {
            struck(0.0, 440.0, 0.26, 0.26, decay = 16.0, hard = 0.8, seed = 8)
            struck(0.06, 293.66, 0.26, 0.28, decay = 14.0, hard = 0.7, seed = 9)
            room()
        }
        else -> Mix(0.09).apply { glide(0.0, 1100.0, 650.0, 0.09, 0.22) }
    }.pcm()

    private fun note(semitonesFromA4: Int) = 440.0 * Math.pow(2.0, semitonesFromA4 / 12.0)

    /** Every day: four soft rising bell notes (C E G C). */
    private fun chime() = Mix(1.6).apply {
        listOf(3, 7, 10, 15).forEachIndexed { i, s -> bell(i * 0.12, note(s), 1.3, 0.26, decay = 3.2) }
    }.pcm()

    /** Halloween: a low minor chord and a theremin that rises and wavers. */
    /** glcn: Ember's own opening, a film studio's logo in three seconds: a low swell, the chord opening over a deep hit, a glint. */
    private fun emberChime() = Mix(3.6).apply {
        // A low note swells out of silence…
        pad(0.0, 73.42, 3.5, 0.18, attack = 1.0, release = 1.5, bright = 0.3)
        pad(0.2, 110.0, 3.3, 0.1, attack = 0.95, release = 1.5, bright = 0.25)
        // …then the chord opens (D, A, F♯) over a soft low knock, and rings out with a glint on top. Round tones and
        // no echoes, so it stays clean on small speakers.
        drop(1.15, 110.0, 62.0, 0.6, 0.3, decay = 5.0)
        pad(1.1, 146.83, 2.5, 0.14, attack = 0.22, release = 1.6, bright = 0.3)
        pad(1.1, 220.0, 2.5, 0.1, attack = 0.25, release = 1.6, bright = 0.25)
        pad(1.12, 369.99, 2.45, 0.07, attack = 0.3, release = 1.6, bright = 0.2)
        bell(1.2, 587.33, 2.0, 0.05, decay = 2.0, partials = doubleArrayOf(1.0, 0.15), attack = 0.02)
        bell(1.34, 880.0, 1.8, 0.03, decay = 2.4, partials = doubleArrayOf(1.0, 0.1), attack = 0.02)
    }.pcm()

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
