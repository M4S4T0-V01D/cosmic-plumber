package dev.m4s4t0.cosmicplumber

import kotlin.math.sin
import kotlin.random.Random

/**
 * A rotate-the-pieces pipe puzzle. Water enters the west side of the left column at [sourceRow] and must leave the
 * east side of the right column at [drainRow]. Masks use N=1, E=2, S=4, W=8.
 */
class PipePuzzle(val cols: Int, val rows: Int, seed: Int) {
    val sourceRow: Int
    val drainRow: Int
    val masks = IntArray(cols * rows)
    /** The generator's own answer; the player may find another. */
    val solution: IntArray

    init {
        val rnd = Random(seed)
        sourceRow = rnd.nextInt(rows)
        drainRow = rnd.nextInt(rows)
        // Loop-erased random walk from source to drain gives a winding but simple path.
        val path = ArrayList<Int>()
        val pos = HashMap<Int, Int>()
        var c = 0
        var r = sourceRow
        val goal = idx(cols - 1, drainRow)
        path += idx(c, r); pos[idx(c, r)] = 0
        while (path.last() != goal) {
            val opts = ArrayList<Pair<Int, Int>>()
            for (d in 0 until 4) {
                val nc = c + DC[d]
                val nr = r + DR[d]
                if (nc in 0 until cols && nr in 0 until rows) {
                    opts += nc to nr
                    if (d == 1) { opts += nc to nr } // lean east so the walk finishes
                }
            }
            val (nc, nr) = opts[rnd.nextInt(opts.size)]
            c = nc; r = nr
            val i = idx(c, r)
            val seen = pos[i]
            if (seen != null) {
                while (path.size > seen + 1) pos.remove(path.removeAt(path.size - 1))
            } else {
                pos[i] = path.size
                path += i
            }
        }
        for ((k, i) in path.withIndex()) {
            var m = 0
            m = m or if (k == 0) W else dirTo(i, path[k - 1])
            m = m or if (k == path.size - 1) E else dirTo(i, path[k + 1])
            masks[i] = m
        }
        val onPath = path.toHashSet()
        for (i in masks.indices) if (i !in onPath) {
            var m = listOf(N or S, N or E, N or E or S, N or E, N or S).random(rnd)
            repeat(rnd.nextInt(4)) { m = rot(m) }
            masks[i] = m
        }
        solution = masks.copyOf()
        do {
            for (i in masks.indices) repeat(rnd.nextInt(4)) { masks[i] = rot(masks[i]) }
        } while (solved())
    }

    fun idx(c: Int, r: Int) = r * cols + c

    private fun dirTo(from: Int, to: Int): Int {
        val dc = to % cols - from % cols
        val dr = to / cols - from / cols
        return when {
            dc == 1 -> E
            dc == -1 -> W
            dr == 1 -> S
            else -> N
        }
    }

    fun rotate(c: Int, r: Int) { masks[idx(c, r)] = rot(masks[idx(c, r)]) }

    /** Cells reached by water from the source. */
    fun flowed(): BooleanArray {
        val wet = BooleanArray(masks.size)
        val start = idx(0, sourceRow)
        if (masks[start] and W == 0) return wet
        val queue = ArrayDeque<Int>()
        queue += start
        wet[start] = true
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val c = i % cols
            val r = i / cols
            for (d in 0 until 4) {
                val bit = 1 shl d
                if (masks[i] and bit == 0) continue
                val nc = c + DC[d]
                val nr = r + DR[d]
                if (nc !in 0 until cols || nr !in 0 until rows) continue
                val j = idx(nc, nr)
                if (!wet[j] && masks[j] and opposite(bit) != 0) { wet[j] = true; queue += j }
            }
        }
        return wet
    }

    fun solved(): Boolean {
        val d = idx(cols - 1, drainRow)
        return masks[d] and E != 0 && flowed()[d]
    }

    companion object {
        const val N = 1; const val E = 2; const val S = 4; const val W = 8
        private val DC = intArrayOf(0, 1, 0, -1)
        private val DR = intArrayOf(-1, 0, 1, 0)
        fun rot(m: Int) = ((m shl 1) or (m shr 3)) and 15
        fun opposite(bit: Int) = rot(rot(bit))
    }
}

/** The pipe job: turn the copper before the pressure gauge redlines. */
class PipeScene(game: Game, private val job: Job) : Scene(game) {
    private val cols = (5 + job.level).coerceAtMost(10)
    private val rows = (4 + (job.level + 1) / 2).coerceAtMost(7)
    private val puzzle = PipePuzzle(cols, rows, job.id * 7919 + job.level)
    private val limit = 20f + cols * rows * 0.9f - job.level * 2f + game.save.level("gauge") * 8f
    private var left = limit
    private var t = 0f
    private var wet = puzzle.flowed()
    private var done = 0f
    private var won = false
    private val spin = FloatArray(cols * rows)
    private var cell = 16
    private var ox = 0
    private var oy = 0
    private val water = if (job.cosmic) Pal.GREEN else Pal.CYAN

    override fun enter() { game.audio.stopMusic() }

    override fun back() = finish(false)

    private fun finish(success: Boolean) {
        if (done > 0f) return
        won = success
        done = 0.01f
        if (!success) { game.audio.play(Audio.Sfx.SPLASH); game.buzz(150) }
    }

    override fun update(dt: Float) {
        t += dt
        for (i in spin.indices) if (spin[i] > 0f) spin[i] = (spin[i] - dt * 9f).coerceAtLeast(0f)
        if (done > 0f) {
            done += dt
            if (done > 1.6f) game.go(ResultScene(game, job, if (won) 0.5f + 0.5f * left / limit else 0f, won))
            return
        }
        left -= dt
        if (left <= 0f) { left = 0f; finish(false) }
    }

    override fun down(id: Int, x: Float, y: Float) {
        if (done > 0f) return
        val c = ((x - ox) / cell).toInt()
        val r = ((y - oy) / cell).toInt()
        if (x < ox || y < oy || c !in 0 until cols || r !in 0 until rows) return
        puzzle.rotate(c, r)
        spin[puzzle.idx(c, r)] = 1f
        game.audio.play(Audio.Sfx.ROTATE, 0.7f, 0.9f + (c + r) % 3 * 0.1f)
        game.buzz(6)
        wet = puzzle.flowed()
        if (puzzle.solved()) {
            game.audio.play(Audio.Sfx.SPLASH, 0.6f, 1.4f)
            finish(true)
        }
    }

    override fun draw(g: Gfx) {
        cell = minOf((g.h - 20) / rows, (g.w - 110) / cols).coerceAtMost(24)
        ox = 40 + (g.w - 110 - cols * cell) / 2
        oy = 10 + (g.h - 20 - rows * cell) / 2
        // Basement wall: cinder block and a bare bulb's worth of light.
        g.rect(0, 0, g.w, g.h, if (job.cosmic) 0xFF1A1438.toInt() else 0xFF3A3040.toInt())
        for (y in 0 until g.h step 8) for (x in (if (y / 8 % 2 == 0) 0 else -8) until g.w step 16) g.frame(x, y, 16, 8, Pal.alpha(Pal.INK, 70))
        g.rect(ox - 3, oy - 3, cols * cell + 6, rows * cell + 6, Pal.INK)
        g.frame(ox - 4, oy - 4, cols * cell + 8, rows * cell + 8, Pal.DGRAY)

        for (r in 0 until rows) for (c in 0 until cols) {
            val i = puzzle.idx(c, r)
            val x = ox + c * cell
            val y = oy + r * cell
            g.rect(x, y, cell, cell, if ((c + r) % 2 == 0) 0xFF2A2436.toInt() else 0xFF241F30.toInt())
            val s = spin[i]
            if (s > 0f) {
                g.c.save()
                g.c.rotate(-90f * s, x + cell / 2f, y + cell / 2f)
                pipe(g, x, y, puzzle.masks[i], wet[i])
                g.c.restore()
            } else pipe(g, x, y, puzzle.masks[i], wet[i])
        }

        // Where the trouble comes from, and where it needs to go.
        val sy = oy + puzzle.sourceRow * cell + cell / 2
        val dy = oy + puzzle.drainRow * cell + cell / 2
        val pw = maxOf(3, cell / 3)
        g.rect(ox - 14, sy - pw / 2, 14, pw, Pal.COPPER)
        g.rect(ox - 14, sy - pw / 2 + 1, 14, pw - 2, water)
        if (job.cosmic) {
            for (k in 0 until 4) g.circle(ox - 22f, sy.toFloat(), 10f - k * 2 + sin(t * 6 + k) * 1.5f, listOf(Pal.PURPLE, Pal.MAGENTA, Pal.GREEN, Pal.INK)[k])
        } else {
            g.rect(ox - 34, sy - 10, 18, 20, Pal.LGRAY)
            g.frame(ox - 34, sy - 10, 18, 20, Pal.INK)
            g.text("H2O", ox - 32, sy - 3, Pal.BLUE, 1, null)
        }
        g.rect(ox + cols * cell, dy - pw / 2, 12, pw, Pal.COPPER)
        if (won) g.rect(ox + cols * cell, dy - pw / 2 + 1, 12, pw - 2, water)
        g.circle(ox + cols * cell + 18f, dy.toFloat(), 8f, Pal.DGRAY)
        g.circle(ox + cols * cell + 18f, dy.toFloat(), 5f, Pal.INK)
        for (k in -1..1) g.rect(ox + cols * cell + 14, dy + k * 3, 9, 1, Pal.GRAY)
        g.text("DRAIN", ox + cols * cell + 8, dy + 11, Pal.LGRAY, 1, null)

        gauge(g, g.w - 30, 50)
        g.text(job.client.take(16), 4, 4, Pal.AMBER)
        g.button(4, g.h - 18, 34, 14, "QUIT", Pal.DKRED) { finish(false) }

        if (done > 0f) {
            val msg = if (won) "IT FLOWS!" else "KA-BLOOEY!"
            val a = (done * 400).toInt().coerceAtMost(200)
            if (!won) g.rect(0, 0, g.w, g.h, Pal.alpha(water, (a * (1.2f - done / 1.6f)).toInt().coerceAtLeast(0)))
            g.textC(msg, g.w / 2, g.h / 2 - 8, if (won) Pal.GREEN else Pal.RED, 3)
        }
    }

    private fun pipe(g: Gfx, x: Int, y: Int, m: Int, wet: Boolean) {
        val h = cell / 2
        val pw = maxOf(3, cell / 3)
        val a = h - pw / 2
        fun arm(ax: Int, ay: Int, aw: Int, ahh: Int) {
            g.rect(x + ax, y + ay, aw, ahh, Pal.DKCOPPER)
            g.rect(x + ax + 1, y + ay + 1, aw - 2, ahh - 2, Pal.COPPER)
            if (wet) g.rect(x + ax + 1, y + ay + 1, aw - 2, ahh - 2, Pal.alpha(water, 170 + (sin(t * 8 + x + y) * 60).toInt()))
        }
        if (m and PipePuzzle.N != 0) arm(a, 0, pw, h + pw / 2)
        if (m and PipePuzzle.S != 0) arm(a, a, pw, cell - a)
        if (m and PipePuzzle.W != 0) arm(0, a, h + pw / 2, pw)
        if (m and PipePuzzle.E != 0) arm(a, a, cell - a, pw)
        // The fitting at the middle, and flanges at the open ends.
        g.rect(x + a - 1, y + a - 1, pw + 2, pw + 2, Pal.DKCOPPER)
        g.rect(x + a, y + a, pw, pw, if (wet) water else Pal.COPPER)
        g.rect(x + a, y + a, pw, 1, Pal.alpha(Pal.CREAM, 120))
    }

    private fun gauge(g: Gfx, cx: Int, cy: Int) {
        val f = 1f - left / limit
        g.circle(cx.toFloat(), cy.toFloat(), 20f, Pal.INK)
        g.circle(cx.toFloat(), cy.toFloat(), 18f, Pal.CREAM)
        for (k in 0..8) {
            val ang = Math.toRadians(135.0 + k * 33.75)
            val col = if (k >= 6) Pal.RED else Pal.INK
            g.line(cx + kotlin.math.cos(ang).toFloat() * 13, cy + kotlin.math.sin(ang).toFloat() * 13, cx + kotlin.math.cos(ang).toFloat() * 17, cy + kotlin.math.sin(ang).toFloat() * 17, col)
        }
        val shake = if (f > 0.75f) sin(t * 50) * 3f else 0f
        val ang = Math.toRadians(135.0 + f * 270 + shake)
        g.line(cx.toFloat(), cy.toFloat(), cx + kotlin.math.cos(ang).toFloat() * 15, cy + kotlin.math.sin(ang).toFloat() * 15, Pal.RED, 1.5f)
        g.circle(cx.toFloat(), cy.toFloat(), 2f, Pal.INK)
        g.textC("PSI", cx, cy + 6, Pal.GRAY, 1, null)
        g.textC("${left.toInt()}S", cx, cy + 25, if (f > 0.75f) Pal.RED else Pal.CREAM)
        g.textC("PRESSURE", cx, cy - 30, Pal.LGRAY)
    }
}
