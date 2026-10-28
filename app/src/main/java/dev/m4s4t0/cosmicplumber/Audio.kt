package dev.m4s4t0.cosmicplumber

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

const val SR = 22050

/** A composed track: its samples and, for wrench jobs, the lead melody as (seconds, lane) notes. */
class Song(val pcm: ShortArray, val notes: List<Pair<Float, Int>>, val seconds: Float)

/** Synthwave in the key of A minor, written on the spot. The lead melody is the wrench chart. */
object Composer {
    private fun hz(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** [lanes] > 0 adds a lead line and charts it across that many lanes; [density] 0..1 is how busy it is. */
    fun compose(bpm: Int, bars: Int, seed: Int, lanes: Int, density: Float, lead: Float = 0.22f, intro: Int = 1): Song {
        val rnd = Random(seed)
        val beat = 60.0 / bpm
        val total = (bars + intro) * 4 * beat + 1.0
        val n = (total * SR).toInt()
        val mix = FloatArray(n)
        val prog = listOf(57, 53, 48, 55) // Am F C G roots (as bass midi + 12)
        val scale = listOf(0, 3, 5, 7, 10) // minor pentatonic over A
        val notes = ArrayList<Pair<Float, Int>>()

        fun add(start: Double, dur: Double, freq: Double, vol: Float, wave: Int, decay: Double = 6.0) {
            val s0 = (start * SR).toInt()
            val len = (dur * SR).toInt()
            var ph = 0.0
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= n) break
                val t = i.toDouble() / SR
                ph += freq / SR
                val f = ph - ph.toInt()
                val v = when (wave) {
                    0 -> if (f < 0.5) 1.0 else -1.0 // square
                    1 -> 4.0 * kotlin.math.abs(f - 0.5) - 1.0 // triangle
                    2 -> 2.0 * f - 1.0 // saw
                    else -> sin(2 * PI * f)
                }
                val env = exp(-t * decay) * (if (i < 60) i / 60.0 else 1.0) * (if (len - i < 200) (len - i) / 200.0 else 1.0)
                mix[idx] += (v * env * vol).toFloat()
            }
        }

        fun kick(start: Double) {
            val s0 = (start * SR).toInt()
            var ph = 0.0
            for (i in 0 until (0.18 * SR).toInt()) {
                if (s0 + i >= n) break
                val t = i.toDouble() / SR
                ph += (50 + 120 * exp(-t * 30)) / SR
                mix[s0 + i] += (sin(2 * PI * ph) * exp(-t * 18) * 0.55).toFloat()
            }
        }

        fun noise(start: Double, dur: Double, vol: Float, decay: Double) {
            val s0 = (start * SR).toInt()
            var last = 0f
            for (i in 0 until (dur * SR).toInt()) {
                if (s0 + i >= n) break
                val t = i.toDouble() / SR
                val r = rnd.nextFloat() * 2 - 1
                val hp = r - last // a crude high-pass for hats
                last = r
                mix[s0 + i] += (hp * exp(-t * decay) * vol).toFloat()
            }
        }

        val introLen = intro * 4 * beat
        // Count-in: four hat ticks so the player feels the tempo.
        for (b in 0 until intro * 4) noise(b * beat, 0.05, 0.3f, 60.0)
        var lastDeg = 0
        for (bar in 0 until bars) {
            val t0 = introLen + bar * 4 * beat
            val root = prog[bar % 4]
            // Pads: a soft triad held over the bar.
            for (iv in listOf(0, if (bar % 4 == 0) 3 else 4, 7)) add(t0, 4 * beat, hz(root + 12 + iv), 0.05f, 1, 0.4)
            for (step in 0 until 8) {
                val t = t0 + step * beat / 2
                // Bass: driving eighths, octave on the offbeat.
                add(t, beat / 2 * 0.9, hz(root - 12 + if (step % 2 == 1) 12 else 0), 0.16f, 2, 5.0)
                if (step % 2 == 0) kick(t)
                if (step == 2 || step == 6) noise(t, 0.16, 0.35f, 22.0)
                noise(t, 0.04, 0.12f, 80.0)
                if (lanes > 0) {
                    val strong = step % 2 == 0
                    val chance = if (strong) 0.35f + density * 0.6f else density * 0.45f
                    if (rnd.nextFloat() < chance) {
                        lastDeg = (lastDeg + rnd.nextInt(-2, 3)).coerceIn(0, 9)
                        val midi = 69 + 12 * (lastDeg / 5) + scale[lastDeg % 5]
                        add(t, beat / 2 * 0.95, hz(midi), lead, 0, 7.0)
                        add(t, beat / 2 * 0.95, hz(midi) * 1.005, lead * 0.5f, 2, 7.0)
                        notes += t.toFloat() to (lastDeg * lanes / 10).coerceIn(0, lanes - 1)
                    }
                }
            }
        }

        // Soft clip to 16-bit.
        val pcm = ShortArray(n) { i ->
            val x = mix[i]
            val y = x / (1f + kotlin.math.abs(x))
            (y * 30000).toInt().toShort()
        }
        return Song(pcm, notes, total.toFloat())
    }
}

/** Sound effects (synthesized WAVs in a SoundPool) and one music track at a time. */
class Audio(private val ctx: Context, private val save: Save) {
    enum class Sfx { CLICK, CLANK, MISS, COIN, PAGER, SPLASH, HONK, WIN, FAIL, ROTATE, DIME, BUMP }

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
    private val ids = HashMap<Sfx, Int>()
    private var track: AudioTrack? = null
    private var trackFrames = 0
    private var looping = false
    private var paused = false
    /** The song loaded right now, if any. */
    @Volatile var current: Song? = null
        private set

    init {
        Thread {
            for (s in Sfx.entries) {
                val f = File(ctx.cacheDir, "sfx_${s.name.lowercase()}_v1.wav")
                if (!f.exists()) writeWav(f, synth(s))
                ids[s] = pool.load(f.path, 1)
            }
        }.start()
    }

    fun play(s: Sfx, vol: Float = 1f, rate: Float = 1f) {
        if (!save.sound) return
        ids[s]?.let { pool.play(it, vol, vol, 1, 0, rate.coerceIn(0.5f, 2f)) }
    }

    /** Plays [song] from the start, looping it if [loop]; music off still plays it silently so timing works. */
    @Synchronized
    fun music(song: Song, loop: Boolean, forceAudible: Boolean = false) {
        stopMusic()
        val bytes = song.pcm.size * 2
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()
        t.write(song.pcm, 0, song.pcm.size)
        if (loop) t.setLoopPoints(0, song.pcm.size, -1)
        t.setVolume(if (save.music || forceAudible) 0.8f else 0f)
        trackFrames = song.pcm.size
        looping = loop
        current = song
        t.play()
        track = t
        paused = false
    }

    @Synchronized
    fun stopMusic() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        current = null
    }

    /** Seconds into the current track (for the rhythm game). */
    fun songTime(): Float {
        val t = track ?: return 0f
        return runCatching { t.playbackHeadPosition.toFloat() / SR }.getOrDefault(0f)
    }

    fun musicPlaying() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    fun refreshVolume() {
        track?.setVolume(if (save.music) 0.8f else 0f)
    }

    @Synchronized
    fun pause() {
        track?.let { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) { it.pause(); paused = true } }
    }

    @Synchronized
    fun resume() {
        if (paused) track?.play()
        paused = false
    }

    fun release() {
        stopMusic()
        pool.release()
    }

    private fun synth(s: Sfx): ShortArray {
        val rnd = Random(s.ordinal)
        fun gen(sec: Float, f: (Float, Float) -> Float): ShortArray {
            val n = (sec * SR).toInt()
            return ShortArray(n) { i ->
                val t = i.toFloat() / SR
                (f(t, t / sec).coerceIn(-1f, 1f) * 26000).toInt().toShort()
            }
        }
        fun sq(t: Float, hz: Float) = if ((t * hz) % 1f < 0.5f) 1f else -1f
        fun sn(t: Float, hz: Float) = sin(2 * PI.toFloat() * hz * t)
        return when (s) {
            Sfx.CLICK -> gen(0.05f) { t, p -> sq(t, 900f) * (1 - p) * 0.4f }
            Sfx.ROTATE -> gen(0.07f) { t, p -> sq(t, 500f + 400f * p) * (1 - p) * 0.35f }
            Sfx.CLANK -> gen(0.18f) { t, p ->
                (sn(t, 1250f) * 0.5f + sn(t, 1870f) * 0.35f + sn(t, 3100f) * 0.2f + (rnd.nextFloat() - 0.5f) * (1 - p).pow(8)) *
                    exp(-t * 22f)
            }
            Sfx.MISS -> gen(0.2f) { t, p -> sq(t, 140f - 60f * p) * (1 - p) * 0.35f }
            Sfx.COIN -> gen(0.28f) { t, p -> sq(t, if (t < 0.07f) 988f else 1319f) * (1 - p) * 0.3f }
            Sfx.DIME -> gen(0.16f) { t, p -> (sn(t, 2400f) + sn(t, 3600f) * 0.5f) * exp(-t * 25f) * 0.5f }
            Sfx.PAGER -> gen(0.6f) { t, _ -> if ((t * 8).toInt() % 2 == 0) sq(t, 2900f) * 0.25f else 0f }
            Sfx.SPLASH -> gen(0.5f) { t, p -> (rnd.nextFloat() * 2 - 1) * (1 - p).pow(2) * 0.4f * (0.6f + 0.4f * sn(t, 30f)) }
            Sfx.HONK -> gen(0.35f) { t, p -> (sq(t, 392f) + sq(t, 494f)) * 0.18f * (if (p > 0.9f) (1 - p) * 10 else 1f) }
            Sfx.BUMP -> gen(0.15f) { t, p -> (sn(t, 70f) + (rnd.nextFloat() - 0.5f) * 0.6f) * (1 - p) * 0.6f }
            Sfx.WIN -> gen(0.9f) { t, p ->
                val notes = floatArrayOf(523f, 659f, 784f, 1047f)
                sq(t, notes[(p * 4).toInt().coerceAtMost(3)]) * 0.25f * (1 - p * 0.6f)
            }
            Sfx.FAIL -> gen(0.8f) { t, p ->
                val notes = floatArrayOf(392f, 370f, 349f, 330f)
                sq(t, notes[(p * 4).toInt().coerceAtMost(3)] * (1 + 0.01f * sn(t, 6f))) * 0.25f * (1 - p * 0.5f)
            }
        }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        RandomAccessFile(f, "rw").use { r ->
            r.setLength(0)
            fun i32(v: Int) = r.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun i16(v: Int) = r.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            r.writeBytes("RIFF"); i32(36 + pcm.size * 2); r.writeBytes("WAVE")
            r.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(SR); i32(SR * 2); i16(2); i16(16)
            r.writeBytes("data"); i32(pcm.size * 2)
            val b = ByteArray(pcm.size * 2)
            for (i in pcm.indices) { b[i * 2] = pcm[i].toInt().toByte(); b[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
            r.write(b)
        }
    }
}
