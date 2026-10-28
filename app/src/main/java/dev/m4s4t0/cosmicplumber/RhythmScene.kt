package dev.m4s4t0.cosmicplumber

import kotlin.math.abs
import kotlin.math.sin

/** Scores taps against a chart of (time, lane) notes. No Android types, so it's unit-tested. */
class RhythmJudge(val notes: List<Pair<Float, Int>>, val window: Float) {
    enum class Hit { PERFECT, GOOD, MISS, WHIFF }

    /** Per note: null until judged. */
    val results = arrayOfNulls<Hit>(notes.size)
    var perfect = 0; private set
    var good = 0; private set
    var missed = 0; private set
    var combo = 0; private set
    var maxCombo = 0; private set
    /** The pressure meter: hits push it up, misses drain it; empty means the pipe bursts. */
    var meter = 0.6f; private set
    private var first = 0

    val judged get() = perfect + good + missed
    val finished get() = judged == notes.size
    val score get() = if (notes.isEmpty()) 1f else (perfect + good * 0.6f) / notes.size
    val multiplier get() = (1 + combo / 8).coerceAtMost(4)

    fun tap(lane: Int, time: Float): Hit {
        var best = -1
        var bestDt = Float.MAX_VALUE
        var i = first
        while (i < notes.size && notes[i].first <= time + window) {
            val (nt, nl) = notes[i]
            val d = abs(nt - time)
            if (results[i] == null && nl == lane && d <= window && d < bestDt) { best = i; bestDt = d }
            i++
        }
        if (best < 0) {
            combo = 0
            meter = (meter - 0.02f).coerceAtLeast(0f)
            return Hit.WHIFF
        }
        val hit = if (bestDt <= window / 2) Hit.PERFECT else Hit.GOOD
        results[best] = hit
        if (hit == Hit.PERFECT) perfect++ else good++
        combo++
        maxCombo = maxOf(maxCombo, combo)
        meter = (meter + if (hit == Hit.PERFECT) 0.04f else 0.025f).coerceAtMost(1f)
        skipJudged()
        return hit
    }

    /** Marks notes that scrolled past the window as missed; returns how many. */
    fun advance(time: Float): Int {
        var n = 0
        var i = first
        while (i < notes.size && notes[i].first < time - window) {
            if (results[i] == null) {
                results[i] = Hit.MISS
                missed++
                n++
                combo = 0
                meter = (meter - 0.05f).coerceAtLeast(0f)
            }
            i++
        }
        skipJudged()
        return n
    }

    private fun skipJudged() {
        while (first < notes.size && results[first] != null) first++
    }
}

/** The wrench job: tighten the bolts on the beat, Guitar Hero style. Three lanes: HOT, GAS and COLD. */
class RhythmScene(game: Game, private val job: Job) : Scene(game) {
    private var song: Song? = null
    private var judge: RhythmJudge? = null
    private var started = false
    private var t = 0f
    private var now = 0f
    private var done = 0f
    private var won = false
    private val window = 0.09f + 0.025f * game.save.level("torque")
    private val bpm = 96 + job.level * 8
    private val swing = FloatArray(3)
    private val flash = FloatArray(3)
    private var lastHit: RhythmJudge.Hit? = null
    private var lastHitTime = -9f
    private var points = 0
    private val laneColor = intArrayOf(Pal.RED, Pal.YELLOW, Pal.BLUE)
    private val laneName = arrayOf("HOT", "GAS", "COLD")
    private val speed = 90f + job.level * 8f

    init {
        game.audio.stopMusic()
        Thread {
            val s = Composer.compose(bpm, 8 + job.level, job.id * 31 + 7, 3, 0.25f + job.level * 0.12f)
            synchronized(game) {
                song = s
                judge = RhythmJudge(s.notes, window)
            }
        }.start()
    }

    override fun leave() { game.audio.stopMusic() }

    override fun back() = finish(false)

    private fun finish(success: Boolean) {
        if (done > 0f) return
        won = success
        done = 0.01f
        game.audio.stopMusic()
        game.audio.play(if (success) Audio.Sfx.WIN else Audio.Sfx.SPLASH)
        if (!success) game.buzz(150)
    }

    override fun update(dt: Float) {
        t += dt
        for (i in 0 until 3) { swing[i] = (swing[i] - dt * 5f).coerceAtLeast(0f); flash[i] = (flash[i] - dt * 4f).coerceAtLeast(0f) }
        if (done > 0f) {
            done += dt
            if (done > 1.8f) game.go(ResultScene(game, job, judge?.score ?: 0f, won))
            return
        }
        val s = song ?: return
        val j = judge ?: return
        if (!started && t > 0.6f) {
            started = true
            game.audio.music(s, loop = false, forceAudible = game.save.sound || game.save.music)
        }
        if (!started) return
        now = game.audio.songTime()
        if (j.advance(now) > 0) {
            game.audio.play(Audio.Sfx.MISS, 0.6f)
            lastHit = RhythmJudge.Hit.MISS
            lastHitTime = t
        }
        when {
            j.meter <= 0f -> finish(false)
            j.finished || now >= s.seconds - 0.05f -> { j.advance(now + 10f); finish(j.score >= PASS) }
        }
    }

    override fun down(id: Int, x: Float, y: Float) {
        val j = judge ?: return
        if (!started || done > 0f) return
        val lane = (x / (game.g.w / 3f)).toInt().coerceIn(0, 2)
        val h = j.tap(lane, now)
        swing[lane] = 1f
        if (h != RhythmJudge.Hit.WHIFF) {
            flash[lane] = 1f
            points += (if (h == RhythmJudge.Hit.PERFECT) 100 else 60) * j.multiplier
            game.audio.play(Audio.Sfx.CLANK, 0.8f, 0.9f + lane * 0.15f)
            game.buzz(10)
        } else game.audio.play(Audio.Sfx.CLICK, 0.4f, 0.6f)
        lastHit = h
        lastHitTime = t
    }

    override fun draw(g: Gfx) {
        val beat = if (started) (now * bpm / 60f) % 1f else 0f
        val pulse = (1f - beat).let { it * it }
        backdrop(g, pulse)

        val hw = minOf(g.w - 120, 150)
        val hx = (g.w - hw) / 2
        val lw = hw / 3
        val hitY = g.h - 34
        // The highway: a long run of pipe, with fret-ish couplings scrolling toward you.
        g.rect(hx - 4, 0, hw + 8, g.h, Pal.INK)
        for (l in 0 until 3) {
            g.rect(hx + l * lw, 0, lw, g.h, Pal.alpha(laneColor[l], 30 + (flash[l] * 70).toInt()))
            g.rect(hx + l * lw, 0, 1, g.h, Pal.alpha(Pal.LGRAY, 50))
        }
        if (started) {
            val spb = 60f / bpm
            val first = (now / spb).toInt()
            for (k in first until first + 16) {
                val y = hitY - (k * spb - now) * speed
                if (y < -4 || y > g.h) continue
                g.rect(hx, y.toInt(), hw, if (k % 4 == 0) 2 else 1, Pal.alpha(Pal.COPPER, if (k % 4 == 0) 140 else 60))
            }
        }
        g.rect(hx - 4, 0, 3, g.h, Pal.DKCOPPER)
        g.rect(hx + hw + 1, 0, 3, g.h, Pal.DKCOPPER)

        // Bolts on their way down.
        val j = judge
        if (j != null && started) {
            for (i in j.notes.indices) {
                val (nt, lane) = j.notes[i]
                val y = hitY - (nt - now) * speed
                if (y < -10) break
                if (y > g.h + 10 || j.results[i].let { it == RhythmJudge.Hit.PERFECT || it == RhythmJudge.Hit.GOOD }) continue
                val cx = hx + lane * lw + lw / 2f
                val missed = j.results[i] == RhythmJudge.Hit.MISS
                bolt(g, cx, y, if (missed) Pal.DGRAY else laneColor[lane])
            }
        }

        // The hit line: a nut per lane and a wrench that swings when you tap.
        g.rect(hx, hitY - 1, hw, 3, Pal.alpha(Pal.CREAM, 120 + (pulse * 100).toInt()))
        for (l in 0 until 3) {
            val cx = hx + l * lw + lw / 2f
            g.circle(cx, hitY.toFloat(), 9f, Pal.INK)
            g.circle(cx, hitY.toFloat(), 8f, Pal.mix(Pal.GRAY, laneColor[l], 0.3f + flash[l] * 0.7f))
            g.circle(cx, hitY.toFloat(), 4f, Pal.INK)
            wrench(g, cx, hitY.toFloat(), -35f * swing[l])
            g.textC(laneName[l], cx.toInt(), g.h - 14, laneColor[l])
        }

        // Pressure meter on the left, score and combo on the right.
        val mx = hx - 26
        g.text("PSI", mx - 2, 18, Pal.LGRAY)
        g.rect(mx, 26, 10, 110, Pal.INK)
        val m = j?.meter ?: 0.6f
        val mc = if (m < 0.25f) Pal.RED else if (m < 0.5f) Pal.AMBER else Pal.GREEN
        g.rect(mx + 1, 26 + (109 * (1 - m)).toInt(), 8, (109 * m).toInt(), mc)
        g.frame(mx, 26, 10, 110, Pal.LGRAY)
        val rx = hx + hw + 10
        g.text("SCORE", rx, 18, Pal.LGRAY)
        g.text("$points", rx, 26, Pal.CREAM, 2)
        if (j != null) {
            g.text("COMBO ${j.combo}", rx, 44, Pal.AMBER)
            if (j.multiplier > 1) g.text("x${j.multiplier}", rx, 54, Pal.MAGENTA, 2)
            g.text("${(j.score * 100).toInt()}%", rx, 74, if (j.score >= PASS) Pal.GREEN else Pal.GRAY)
            g.text("NEED ${(PASS * 100).toInt()}%", rx, 82, Pal.GRAY, 1, null)
        }
        g.text(job.client.take(14), 4, 4, Pal.AMBER)

        lastHit?.let { h ->
            val age = t - lastHitTime
            if (age < 0.5f && h != RhythmJudge.Hit.WHIFF) {
                val (txt, c) = when (h) {
                    RhythmJudge.Hit.PERFECT -> "TIGHT!" to Pal.GREEN
                    RhythmJudge.Hit.GOOD -> "OK" to Pal.YELLOW
                    else -> "LEAK!" to Pal.RED
                }
                g.textC(txt, g.w / 2, hitY - 30 - (age * 20).toInt(), c, 2)
            }
        }
        if (song == null) g.textC("TUNING THE PIPES" + ".".repeat((t * 3).toInt() % 4), g.w / 2, g.h / 2, Pal.CREAM)
        else if (!started || now < 1.2f) g.textC("TAP THE LANES ON THE BEAT!", g.w / 2, g.h / 2 - 20, Pal.CREAM)

        g.button(4, g.h - 18, 34, 14, "QUIT", Pal.DKRED) { finish(false) }
        if (done > 0f) {
            if (!won) g.rect(0, 0, g.w, g.h, Pal.alpha(Pal.CYAN, (120 * (1 - done / 1.8f)).toInt().coerceAtLeast(0)))
            g.textC(if (won) "ROCK SOLID!" else "PIPE BURST!", g.w / 2, g.h / 2 - 8, if (won) Pal.GREEN else Pal.RED, 3)
        }
    }

    private fun backdrop(g: Gfx, pulse: Float) {
        if (job.cosmic) {
            g.rect(0, 0, g.w, g.h, Pal.INK)
            for (i in 0 until 50) {
                val y = ((i * 41 + t * (20 + i % 5 * 15)) % g.h)
                g.rect((i * 73) % g.w, y.toInt(), 1, 2, Pal.alpha(Pal.CREAM, 60 + i * 3))
            }
        } else {
            g.rect(0, 0, g.w, g.h, 0xFF2A2033.toInt())
            for (y in 0 until g.h step 8) for (x in (if (y / 8 % 2 == 0) 0 else -8) until g.w step 16) g.frame(x, y, 16, 8, Pal.alpha(Pal.INK, 80))
        }
        // Stage lights on the beat, like the band's here.
        val c = if (job.cosmic) Pal.GREEN else Pal.MAGENTA
        for (k in 0 until 4) {
            val x = g.w * (k + 0.5f) / 4f
            for (i in 0 until 30) {
                val w = 2 + i
                g.rect((x - w / 2).toInt() + (sin(t + k) * i * 0.3f).toInt(), i * 2, w, 2, Pal.alpha(c, ((20 - i * 0.6f) * (0.4f + pulse)).toInt().coerceAtLeast(0)))
            }
        }
    }

    private fun bolt(g: Gfx, cx: Float, cy: Float, color: Int) {
        g.circle(cx, cy + 1, 7f, Pal.INK)
        g.circle(cx, cy, 6f, color)
        g.circle(cx, cy, 2.5f, Pal.INK)
        g.rect((cx - 3).toInt(), (cy - 4).toInt(), 3, 1, Pal.alpha(Pal.WHITE, 160))
    }

    private fun wrench(g: Gfx, cx: Float, cy: Float, deg: Float) {
        val c = g.c
        c.save()
        c.rotate(deg, cx, cy)
        val x = cx.toInt()
        val y = cy.toInt()
        g.rect(x + 6, y - 2, 22, 5, Pal.INK)
        g.rect(x + 7, y - 1, 20, 3, Pal.LGRAY)
        g.rect(x + 7, y - 1, 20, 1, Pal.WHITE)
        g.rect(x + 2, y - 6, 7, 3, Pal.LGRAY)
        g.rect(x + 2, y + 4, 7, 3, Pal.LGRAY)
        g.rect(x + 7, y - 6, 3, 13, Pal.GRAY)
        c.restore()
    }

    companion object {
        const val PASS = 0.55f
    }
}
