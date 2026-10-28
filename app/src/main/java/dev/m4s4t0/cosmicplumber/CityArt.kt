package dev.m4s4t0.cosmicplumber

import android.graphics.Bitmap
import android.graphics.Canvas
import dev.m4s4t0.cosmicplumber.City.Style
import kotlin.random.Random

/** Sprites for the street: the van in each paint, traffic, props. All face east (angle 0). */
object Sprites {
    private val vanArt = listOf(
        "..kkk.......kkk.....",
        ".BBBBBBBBBBBBBBBBB..",
        "rBssssssssssssBwwBl.",
        "BBsttttttttttsBwwwB.",
        "BBsssssssssssBBwwwBB",
        "BBsssssssssssBBwwwBB",
        "BBsttttttttttsBwwwB.",
        "rBssssssssssssBwwBl.",
        ".BBBBBBBBBBBBBBBBB..",
        "..kkk.......kkk.....",
    )
    private val vans = HashMap<String, Sprite>()

    fun van(p: Paint): Sprite = vans.getOrPut(p.id) {
        Sprite(vanArt, mapOf('B' to p.color, 's' to Pal.mix(p.color, Pal.WHITE, 0.25f), 't' to p.trim, 'w' to Pal.CYAN,
            'k' to Pal.INK, 'r' to Pal.RED, 'l' to Pal.YELLOW))
    }

    private val carArt = listOf(
        ".kk......kk...",
        "CCCCCCCCCCCCC.",
        "rCwcccccccwCCl",
        "rCwcccccccwCCl",
        "CCCCCCCCCCCCC.",
        ".kk......kk...",
    )
    val carColors = listOf(Pal.RED, Pal.BLUE, Pal.CREAM, Pal.YELLOW, Pal.DKGREEN, Pal.TAN, Pal.PLUM, Pal.LGRAY)
    val cars = carColors.map { c ->
        Sprite(carArt, mapOf('C' to c, 'c' to Pal.mix(c, Pal.INK, 0.25f), 'w' to Pal.SKY, 'k' to Pal.INK, 'r' to Pal.RED, 'l' to Pal.YELLOW))
    }

    val train = Sprite(listOf(
        "kkkkkkkkkkkkkkkkkkkkkkkkkk",
        "kLLLLLLLLLLLLLLLLLLLLLLLLk",
        "kLwwLwwLwwLwwLwwLwwLwwLwwk",
        "kLLLLLLLLLLLLLLLLLLLLLLLLk",
        "kLLLLLLLLLLLLLLLLLLLLLLLLk",
        "kLwwLwwLwwLwwLwwLwwLwwLwwk",
        "kLLLLLLLLLLLLLLLLLLLLLLLLk",
        "kkkkkkkkkkkkkkkkkkkkkkkkkk",
    ), mapOf('k' to Pal.DGRAY, 'L' to Pal.LGRAY, 'w' to Pal.SKY))

    val payphone = Sprite(listOf(".bb.", "bbbb", "gLLg", "gllg", "gllg", ".kk."),
        mapOf('b' to Pal.BLUE, 'g' to Pal.GRAY, 'L' to Pal.CREAM, 'l' to Pal.LGRAY, 'k' to Pal.INK))
    val pump = Sprite(listOf("rrrr", "rYYr", "rkkr", "rrrr", "rrrr", "kkkk"),
        mapOf('r' to Pal.RED, 'Y' to Pal.YELLOW, 'k' to Pal.INK))
}

/** The city pre-drawn once: [day] holds the ground and buildings, [lights] what glows at night. */
class CityArt(val city: City) {
    val day: Bitmap = Bitmap.createBitmap(City.W * T, City.H * T, Bitmap.Config.ARGB_8888)
    val lights: Bitmap = Bitmap.createBitmap(City.W * T, City.H * T, Bitmap.Config.ARGB_8888)
    private val g = Gfx().apply { c = Canvas(day); w = day.width; h = day.height }
    private val l = Gfx().apply { c = Canvas(lights); w = lights.width; h = lights.height }
    private val rnd = Random(42)

    init {
        for (ty in 0 until City.H) for (tx in 0 until City.W) ground(tx, ty)
        for ((x, y) in city.trees) tree(x, y)
        for (b in city.buildings) building(b)
        for ((x, y) in city.payphones) { g.rect(x + 1, y + 5, 4, 2, Pal.alpha(Pal.INK, 80)); g.sprite(Sprites.payphone, x, y); l.rect(x + 1, y + 2, 2, 1, Pal.CREAM) }
        for ((x, y) in city.gas) for (i in 0 until 3) g.sprite(Sprites.pump, x - 20 + i * 14, y - 30)
        for ((x, y) in city.lamps) { g.rect(x, y, 2, 2, Pal.DGRAY); g.rect(x + 2, y + 2, 2, 1, Pal.alpha(Pal.INK, 90)); l.rect(x, y, 2, 2, Pal.YELLOW) }
    }

    private fun speckle(x: Int, y: Int, base: Int, dots: Int, amount: Float) {
        g.rect(x, y, T, T, base)
        repeat(dots) {
            val c = if (rnd.nextBoolean()) Pal.mix(base, Pal.WHITE, amount) else Pal.mix(base, Pal.INK, amount)
            g.rect(x + rnd.nextInt(T), y + rnd.nextInt(T), 1, 1, c)
        }
    }

    private fun ground(tx: Int, ty: Int) {
        val x = tx * T
        val y = ty * T
        val t = city.tile(tx, ty)
        when (t) {
            City.ROAD, City.BRIDGE -> {
                speckle(x, y, Pal.ASPHALT, 10, 0.12f)
                val row = city.isRoadRow(ty)
                val col = city.isRoadCol(tx) || tx >= City.CITY_W
                if (row && !col && ty % City.P == 1) for (i in 0 until T step 8) { g.rect(x + i, y - 1, 5, 1, Pal.YELLOW); g.rect(x + i, y + 1, 5, 1, Pal.AMBER) }
                if (col && !row && tx % City.P == 1) for (i in 0 until T step 8) { g.rect(x - 1, y + i, 1, 5, Pal.YELLOW); g.rect(x + 1, y + i, 1, 5, Pal.AMBER) }
                // Crosswalks beside each intersection.
                if (row && !col) {
                    if (tx % City.P == 2) for (i in 1 until T step 3) g.rect(x + 1, y + i, 3, 2, Pal.LGRAY)
                    if (tx % City.P == 6) for (i in 1 until T step 3) g.rect(x + 12, y + i, 3, 2, Pal.LGRAY)
                }
                if (col && !row) {
                    if (ty % City.P == 2) for (i in 1 until T step 3) g.rect(x + i, y + 1, 2, 3, Pal.LGRAY)
                    if (ty % City.P == 6) for (i in 1 until T step 3) g.rect(x + i, y + 12, 2, 3, Pal.LGRAY)
                }
                if (t == City.BRIDGE) {
                    if (tx % City.P == 0) { g.rect(x, y, 2, T, Pal.DKRED); g.rect(x + 2, y, 1, T, Pal.INK) }
                    if (tx % City.P == 1) { g.rect(x + T - 2, y, 2, T, Pal.DKRED); g.rect(x + T - 3, y, 1, T, Pal.INK) }
                    if (ty % 2 == 0) { g.rect(x, y + 4, 2, 1, Pal.GRAY); g.rect(x + T - 2, y + 4, 2, 1, Pal.GRAY) }
                }
            }
            City.WALK -> {
                g.rect(x, y, T, T, 0xFF9C98A8.toInt())
                g.rect(x, y + 7, T, 1, 0xFF86829A.toInt())
                g.rect(x + 7, y, 1, T, 0xFF86829A.toInt())
                if (city.isRoad(tx, ty - 1)) g.rect(x, y, T, 1, Pal.LGRAY)
                if (city.isRoad(tx, ty + 1)) g.rect(x, y + T - 1, T, 1, Pal.DGRAY)
                if (city.isRoad(tx - 1, ty)) g.rect(x, y, 1, T, Pal.LGRAY)
                if (city.isRoad(tx + 1, ty)) g.rect(x + T - 1, y, 1, T, Pal.DGRAY)
            }
            City.GRASS -> speckle(x, y, Pal.GRASS, 22, 0.18f)
            City.SAND -> speckle(x, y, 0xFFE0C08A.toInt(), 14, 0.08f)
            City.LOT -> {
                speckle(x, y, 0xFF423E52.toInt(), 6, 0.1f)
                if (tx % 2 == 0) g.rect(x, y + 12, 1, 4, Pal.LGRAY)
            }
            City.WATER -> {
                val base = if (tx >= City.CITY_W) Pal.LAKE else 0xFF2E6A6A.toInt() // the river is famously green-ish
                g.rect(x, y, T, T, base)
                repeat(3) { g.rect(x + rnd.nextInt(12), y + rnd.nextInt(T), 4, 1, Pal.mix(base, Pal.WHITE, 0.15f)) }
                if (tx >= City.CITY_W && city.tile(tx - 1, ty) == City.SAND) g.rect(x, y, 2, T, Pal.CREAM)
            }
            City.BUILD -> g.rect(x, y, T, T, 0xFF9C98A8.toInt())
        }
    }

    private fun tree(x: Int, y: Int) {
        g.circle(x + 3f, y + 4f, 6f, Pal.alpha(Pal.INK, 70))
        g.circle(x.toFloat(), y.toFloat(), 6f, Pal.DKGRASS)
        g.circle(x - 1f, y - 1f, 4.5f, Pal.GRASS)
        g.circle(x - 2f, y - 2f, 2f, 0xFF7FBF5A.toInt())
    }

    private fun windows(x: Int, y: Int, w: Int, h: Int, glass: Int, stepX: Int, stepY: Int, ww: Int, wh: Int, litChance: Float) {
        var yy = y + 2
        while (yy + wh <= y + h - 2) {
            var xx = x + 2
            while (xx + ww <= x + w - 1) {
                g.rect(xx, yy, ww, wh, glass)
                if (rnd.nextFloat() < litChance) l.rect(xx, yy, ww, wh, if (rnd.nextInt(5) == 0) Pal.CREAM else Pal.AMBER)
                xx += stepX
            }
            yy += stepY
        }
    }

    private fun building(b: City.Building) {
        val x = b.tx * T
        val y = b.ty * T
        val w = b.tw * T
        val d = b.th * T
        val h = b.h
        val roofY = y - h
        val wallY = y + d - h
        // Shadow to the southeast.
        g.rect(x + 4, y + 4 - h / 2, w, d, Pal.alpha(Pal.INK, 70))
        when (b.style) {
            Style.TOWER -> {
                val wall = if (b.seed % 2 == 0) 0xFF3A4660.toInt() else 0xFF4C4458.toInt()
                g.rect(x, roofY, w, d, Pal.mix(wall, Pal.GRAY, 0.4f))
                g.frame(x, roofY, w, d, Pal.mix(wall, Pal.WHITE, 0.3f))
                g.rect(x + 6, roofY + 6, 8, 6, Pal.LGRAY); g.rect(x + 6, roofY + 12, 8, 1, Pal.DGRAY)
                g.rect(x + w - 16, roofY + d - 18, 10, 8, Pal.GRAY)
                if (b.seed % 3 == 0) { g.circle(x + w - 12f, roofY + 10f, 6f, Pal.DGRAY); g.text("H", x + w - 13, roofY + 8, Pal.YELLOW, 1, null) }
                l.rect(x + w / 2, roofY + 2, 2, 2, Pal.RED)
                g.rect(x, wallY, w, h, wall)
                for (row in 0 until h / 3) {
                    g.rect(x + 1, wallY + 1 + row * 3, w - 2, 1, Pal.mix(Pal.LAKE, Pal.SKY, 0.3f))
                    var xx = x + 1
                    while (xx < x + w - 3) {
                        if (rnd.nextFloat() < 0.35f) l.rect(xx, wallY + 1 + row * 3, 3, 1, Pal.CREAM)
                        xx += 4
                    }
                }
            }
            Style.BROWNSTONE -> {
                val wall = if (b.seed % 2 == 0) Pal.BRICK else Pal.DKBROWN
                g.rect(x, roofY, w, d, 0xFF3A3242.toInt())
                g.frame(x, roofY, w, d, Pal.mix(wall, Pal.WHITE, 0.2f))
                g.rect(x + 3, roofY + 4, 3, 3, Pal.BRICK); g.rect(x + 3, roofY + 4, 3, 1, Pal.INK)
                g.rect(x, wallY, w, h, wall)
                g.rect(x, wallY, w, 1, Pal.TAN)
                windows(x, wallY, w, h, Pal.INK, 4, 4, 2, 2, 0.45f)
                g.rect(x + w / 2 - 2, y + d - 4, 4, 4, Pal.DKBROWN)
            }
            Style.TWOFLAT -> {
                val wall = if (b.seed % 2 == 0) 0xFFB0AAA0.toInt() else 0xFF8E8A84.toInt()
                g.rect(x, roofY, w, d, 0xFF55505E.toInt())
                g.frame(x, roofY, w, d, wall)
                g.rect(x, wallY, w, h, wall)
                windows(x, wallY, w, h, Pal.NIGHT, 5, 5, 3, 3, 0.5f)
                g.rect(x + w / 2 - 2, y + d - 4, 4, 4, Pal.BROWN)
            }
            Style.BUNGALOW -> {
                val roof = listOf(Pal.DKRED, Pal.DKBROWN, Pal.DGRAY, 0xFF5A3A5A.toInt())[Math.floorMod(b.seed, 4)]
                g.rect(x, roofY, w, d / 2, Pal.mix(roof, Pal.WHITE, 0.15f))
                g.rect(x, roofY + d / 2, w, d - d / 2, roof)
                g.rect(x, roofY + d / 2, w, 1, Pal.mix(roof, Pal.WHITE, 0.4f))
                g.rect(x, wallY, w, h, Pal.TAN)
                windows(x, wallY, w, h, Pal.NIGHT, 6, 6, 3, 2, 0.5f)
                g.rect(x + w / 2 - 2, y + d - 4, 4, 4, Pal.BROWN)
            }
            Style.FACTORY -> {
                g.rect(x, roofY, w, d, Pal.GRAY)
                for (i in 0 until d step 6) { g.rect(x, roofY + i, w, 3, Pal.LGRAY); g.rect(x, roofY + i + 3, w, 1, Pal.SKY) }
                g.rect(x, wallY, w, h, Pal.BRICK)
                windows(x, wallY, w, h, 0xFF5A6A7A.toInt(), 6, 5, 4, 3, 0.15f)
                // Smokestack.
                g.rect(x + w - 10, roofY - 14, 6, 20, Pal.BRICK)
                g.rect(x + w - 10, roofY - 14, 6, 2, Pal.INK)
                for (i in 0 until 3) g.rect(x + w - 10, roofY - 8 + i * 6, 6, 1, Pal.CREAM)
                g.rect(x + 8, y + d - 8, 12, 8, Pal.DGRAY)
            }
            Style.HQ -> {
                g.rect(x, roofY, w, d, Pal.PURPLE)
                g.frame(x, roofY, w, d, Pal.GREEN)
                g.rect(x + 4, roofY + 10, w - 8, 14, Pal.INK)
                g.textC("GALACTIC", x + w / 2, roofY + 11, Pal.GREEN, 1, null)
                g.textC("DRAIN", x + w / 2, roofY + 18, Pal.GREEN, 1, null)
                l.rect(x + 4, roofY + 10, w - 8, 14, Pal.alpha(Pal.GREEN, 70))
                Gfx().apply { c = l.c }.let { lg -> lg.textC("GALACTIC", x + w / 2, roofY + 11, Pal.GREEN, 1, null); lg.textC("DRAIN", x + w / 2, roofY + 18, Pal.GREEN, 1, null) }
                g.circle(x + w - 10f, roofY + 34f, 5f, Pal.LGRAY); g.circle(x + w - 10f, roofY + 34f, 2f, Pal.GREEN)
                g.rect(x, wallY, w, h, Pal.TEAL)
                g.rect(x + 8, wallY + 2, 22, h - 2, Pal.LGRAY)
                for (i in 0 until h - 2 step 2) g.rect(x + 8, wallY + 2 + i, 22, 1, Pal.GRAY)
                windows(x + 32, wallY, 14, h, Pal.NIGHT, 5, 5, 3, 3, 0.8f)
            }
            Style.STADIUM -> {
                g.rect(x, roofY, w, d, 0xFF2E5A2A.toInt())
                g.rect(x + 3, roofY + 3, w - 6, d - 6, Pal.GRASS)
                for (i in 0 until w - 6 step 6) g.rect(x + 3 + i, roofY + 3, 3, d - 6, 0xFF5A9A46.toInt())
                g.rect(x + w / 2 - 8, roofY + d - 22, 16, 16, 0xFFC08A5A.toInt())
                g.rect(x + w / 2 - 4, roofY + d - 18, 8, 8, Pal.GRASS)
                for ((dx, dy) in listOf(-8 to -8, 7 to -8, 7 to 7, -8 to 7)) g.rect(x + w / 2 + dx, roofY + d - 14 + dy, 2, 2, Pal.WHITE)
                g.rect(x, wallY, w, h, Pal.DKGRASS)
                for (i in 0 until w step 3) g.rect(x + i, wallY + rnd.nextInt(3), 2, h - 3, Pal.GRASS)
                g.rect(x + w / 2 - 26, y + d - 9, 52, 9, Pal.RED)
                g.textC("WRIGGLY FIELD", x + w / 2, y + d - 7, Pal.WHITE, 1, null)
                l.rect(x + w / 2 - 26, y + d - 9, 52, 9, Pal.alpha(Pal.RED, 200))
                Gfx().apply { c = l.c }.textC("WRIGGLY FIELD", x + w / 2, y + d - 7, Pal.WHITE, 1, null)
            }
            Style.GAS -> {
                g.rect(x - 34, roofY - 6, w + 30, d + 6, Pal.alpha(Pal.INK, 50))
                g.rect(x - 36, roofY - 10, w + 30, 14, Pal.WHITE)
                g.rect(x - 36, roofY - 10, w + 30, 3, Pal.RED)
                g.textC(b.sign ?: "GAS", x - 21 + w / 2, roofY - 5, Pal.RED, 1, null)
                l.rect(x - 36, roofY - 10, w + 30, 14, Pal.alpha(Pal.CREAM, 160))
                g.rect(x, wallY, w, h, Pal.CREAM)
                windows(x, wallY, w, h, Pal.SKY, 6, 4, 4, 2, 0.9f)
            }
        }
        if (b.sign != null && b.style != Style.GAS && b.style != Style.HQ && b.style != Style.STADIUM) {
            val sw = Font.width(b.sign) + 6
            val sx = x + (w - sw) / 2
            val sy = roofY + 6
            g.rect(sx + 2, sy + 9, 1, 4, Pal.INK); g.rect(sx + sw - 3, sy + 9, 1, 4, Pal.INK)
            g.rect(sx, sy, sw, 9, Pal.CREAM)
            g.frame(sx, sy, sw, 9, Pal.INK)
            g.text(b.sign, sx + 3, sy + 2, Pal.MAGENTA, 1, null)
            l.rect(sx, sy, sw, 9, Pal.alpha(Pal.CREAM, 120))
        }
    }
}
