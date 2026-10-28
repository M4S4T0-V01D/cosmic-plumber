package dev.m4s4t0.cosmicplumber

import kotlin.math.sin
import kotlin.random.Random

/** Sky and skyline painters shared by the title and the garage window. */
object Skyline {
    private val stars = List(60) { Random(it * 977).let { r -> r.nextInt(1000) to r.nextInt(100) } }

    fun sky(g: Gfx, x: Int, y: Int, w: Int, h: Int, darkness: Float, sunset: Float) {
        val bands = 12
        for (i in 0 until bands) {
            val t = i / (bands - 1f)
            val day = Pal.mix(Pal.SKY, Pal.CREAM, t * 0.6f)
            val dusk = Pal.mix(Pal.PURPLE, Pal.ORANGE, t)
            val night = Pal.mix(Pal.INK, Pal.PURPLE, t * 0.7f)
            val c = Pal.mix(Pal.mix(day, dusk, sunset), night, darkness)
            g.rect(x, y + h * i / bands, w, h / bands + 1, c)
        }
        if (darkness > 0.3f) for ((sx, sy) in stars) if (sy < h * 0.7f) g.rect(x + sx % w, y + sy * h / 140, 1, 1, Pal.alpha(Pal.CREAM, ((darkness - 0.3f) * 360).toInt()))
    }

    /** Chicago, 1986: the Sears Tower, the Hancock, and a lot of other buildings. [lit] lights the windows. */
    fun city(g: Gfx, x: Int, base: Int, w: Int, lit: Float, color: Int = Pal.INK) {
        val r = Random(312)
        var bx = x - 4
        while (bx < x + w) {
            val bw = r.nextInt(10, 22)
            val bh = r.nextInt(14, 46)
            g.rect(bx, base - bh, bw, bh, color)
            if (lit > 0) for (wy in base - bh + 3 until base - 2 step 4) for (wx in bx + 2 until bx + bw - 2 step 3)
                if (r.nextFloat() < 0.3f) g.rect(wx, wy, 1, 2, Pal.alpha(Pal.AMBER, (lit * 230).toInt()))
            bx += bw + r.nextInt(0, 3)
        }
        // Sears Tower: stepped setbacks and the two antennas.
        val sx = x + w * 3 / 5
        g.rect(sx, base - 96, 22, 96, color)
        g.rect(sx + 4, base - 112, 14, 16, color)
        g.rect(sx + 7, base - 124, 8, 12, color)
        g.rect(sx + 7, base - 140, 1, 16, color)
        g.rect(sx + 13, base - 136, 1, 12, color)
        g.rect(sx + 7, base - 141, 1, 1, Pal.RED)
        g.rect(sx + 13, base - 137, 1, 1, Pal.RED)
        // The Hancock: tapered, with its cross-bracing.
        val hx = x + w / 4
        for (i in 0 until 84) { val inset = i / 14; g.rect(hx + inset, base - i, 20 - inset * 2, 1, color) }
        g.rect(hx + 6, base - 100, 1, 16, color); g.rect(hx + 12, base - 100, 1, 16, color)
        if (lit > 0) {
            for (wy in base - 92 until base - 4 step 3) for (wx in sx + 2 until sx + 20 step 3) if (r.nextFloat() < 0.35f) g.rect(wx, wy, 1, 1, Pal.alpha(Pal.CREAM, (lit * 220).toInt()))
            for (k in 0 until 4) {
                val y0 = base - 6 - k * 20
                g.line(hx + 2f + k * 0.5f, y0.toFloat(), hx + 17f - k * 0.5f, y0 - 18f, Pal.alpha(Pal.DGRAY, 160))
            }
        }
    }

    fun ufo(g: Gfx, cx: Float, cy: Float, t: Float, beam: Boolean) {
        if (beam) for (i in 0 until 40) {
            val w = 6 + i / 2
            g.rect((cx - w / 2).toInt(), (cy + 4 + i).toInt(), w, 1, Pal.alpha(Pal.GREEN, 70 - i))
        }
        g.circle(cx, cy - 2, 5f, Pal.alpha(Pal.CYAN, 200))
        g.rect((cx - 12).toInt(), cy.toInt(), 24, 3, Pal.LGRAY)
        g.rect((cx - 8).toInt(), (cy + 3).toInt(), 16, 2, Pal.GRAY)
        for (i in 0 until 4) {
            val on = ((t * 6).toInt() + i) % 4 == 0
            g.rect((cx - 9 + i * 6).toInt(), (cy + 1).toInt(), 2, 1, if (on) Pal.YELLOW else Pal.MAGENTA)
        }
    }
}

class TitleScene(game: Game) : Scene(game) {
    private var t = 0f

    override fun update(dt: Float) {
        t += dt
        if (game.save.music) game.playRadio()
    }

    override fun draw(g: Gfx) {
        val horizon = 132
        Skyline.sky(g, 0, 0, g.w, horizon, 0.35f, 1f)
        // A fat 80s sun with its stripes.
        val sunX = g.w / 2f
        for (i in 0 until 26) {
            val y = horizon - 30 + i
            if (i > 12 && (i - 12) % 4 < 1 + (i - 12) / 5) continue
            val half = kotlin.math.sqrt((26f * 26f - (i - 26f) * (i - 26f)).coerceAtLeast(0f)) * 0.95f
            g.rect((sunX - half).toInt(), y, (half * 2).toInt(), 1, Pal.mix(Pal.YELLOW, Pal.MAGENTA, i / 26f))
        }
        Skyline.city(g, 0, horizon, g.w, 0.8f)
        // Lake Michigan with sun streaks.
        g.rect(0, horizon, g.w, g.h - horizon, Pal.DKLAKE)
        for (i in 0 until 10) {
            val y = horizon + 3 + i * 5
            val w = 40 - i * 3 + (sin(t * 2 + i) * 4).toInt()
            g.rect((sunX - w / 2).toInt(), y, w, 1, Pal.alpha(Pal.ORANGE, 160 - i * 12))
        }
        val ux = g.w * 0.2f + sin(t * 0.7f) * 30
        val uy = 40 + sin(t * 1.9f) * 5
        Skyline.ufo(g, ux, uy, t, true)

        g.textC("COSMIC", g.w / 2 + 2, 18, Pal.PLUM, 4, null)
        g.textC("COSMIC", g.w / 2, 16, Pal.GREEN, 4, Pal.INK)
        g.textC("PLUMBER", g.w / 2 + 2, 42, Pal.DKRED, 4, null)
        g.textC("PLUMBER", g.w / 2, 40, Pal.ORANGE, 4, Pal.INK)
        g.textC("CHICAGO, 1986", g.w / 2, 66, Pal.CREAM)
        if ((t * 2).toInt() % 2 == 0) g.textC("TAP TO PUNCH IN", g.w / 2, 150, Pal.YELLOW)
        g.text("V${BuildConfig.VERSION_NAME}", 3, g.h - 8, Pal.GRAY, 1, null)
        g.hotspot(0, 0, g.w, g.h) { game.go(HqScene(game)) }
    }

    override fun back() = game.activity.runOnUiThread { game.activity.finish() }
}
