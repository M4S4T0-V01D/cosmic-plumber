package dev.m4s4t0.cosmicplumber

import kotlin.math.abs
import kotlin.random.Random

const val T = 16

/** 1986 Chicago, more or less: a street grid, the river, the Loop and its El, the lake to the east. */
class City {
    enum class Style { TOWER, BROWNSTONE, BUNGALOW, FACTORY, TWOFLAT, HQ, STADIUM, GAS }
    class Building(val tx: Int, val ty: Int, val tw: Int, val th: Int, val style: Style, val h: Int, val seed: Int, val sign: String? = null)

    companion object {
        const val BX = 10
        const val BY = 7
        const val P = 7 // tiles per block period: 2 road + 5 block
        const val CITY_W = BX * P + 2
        const val W = CITY_W + 12
        const val H = BY * P + 2
        const val ROAD = 0; const val WALK = 1; const val BUILD = 2; const val GRASS = 3
        const val WATER = 4; const val BRIDGE = 5; const val LOT = 6; const val SAND = 7
        const val RIVER_ROW = 2
        // The Loop: El tracks around blocks 6..8 x 3..4.
        const val LOOP_X0 = 6 * P; const val LOOP_X1 = 9 * P; const val LOOP_Y0 = 3 * P; const val LOOP_Y1 = 5 * P
    }

    val tiles = IntArray(W * H)
    val buildings = ArrayList<Building>()
    /** Job sites: the sidewalk tile in front of a door. */
    val sites = ArrayList<Pair<Int, Int>>()
    val payphones = ArrayList<Pair<Int, Int>>()
    val gas = ArrayList<Pair<Int, Int>>()
    val lamps = ArrayList<Pair<Int, Int>>()
    val trees = ArrayList<Pair<Int, Int>>()
    var hq = 0 to 0
        private set
    val hqBlock = 3 to 5

    fun tile(tx: Int, ty: Int) = if (tx < 0 || ty < 0 || tx >= W || ty >= H) WATER else tiles[ty * W + tx]
    private fun set(tx: Int, ty: Int, v: Int) { if (tx in 0 until W && ty in 0 until H) tiles[ty * W + tx] = v }

    fun isRoadRow(ty: Int) = ty % P < 2
    fun isRoadCol(tx: Int) = tx % P < 2 && tx < CITY_W
    fun drivable(tx: Int, ty: Int) = tile(tx, ty).let { it == ROAD || it == BRIDGE || it == WALK || it == LOT || it == SAND || it == GRASS }
    fun isRoad(tx: Int, ty: Int) = tile(tx, ty).let { it == ROAD || it == BRIDGE }

    /** Movement multiplier for the surface under a world point. */
    fun grip(x: Float, y: Float): Float = when (tile((x / T).toInt(), (y / T).toInt())) {
        ROAD, BRIDGE, LOT -> 1f
        WALK -> 0.75f
        else -> 0.5f
    }

    fun solidAt(x: Float, y: Float) = !drivable((x / T).toInt(), (y / T).toInt())

    private fun district(bx: Int, by: Int): Style? = when {
        bx to by == hqBlock -> Style.HQ
        bx == 5 && by == 0 -> Style.STADIUM
        bx == 9 && by in 3..5 -> null // Grant Park
        bx == 2 && by == 6 -> null // a neighborhood park
        (bx to by) in listOf(2 to 1, 4 to 4, 8 to 6, 0 to 3, 7 to 0) -> Style.GAS
        by == RIVER_ROW -> null
        bx in 6..8 && by in 3..4 -> Style.TOWER
        bx >= 6 && by <= 1 -> if ((bx + by) % 2 == 0) Style.TOWER else Style.BROWNSTONE
        by >= 5 && bx <= 2 -> Style.FACTORY
        by <= 1 -> if ((bx + by) % 2 == 0) Style.TWOFLAT else Style.BROWNSTONE
        else -> if ((bx * 3 + by) % 3 == 0) Style.TWOFLAT else Style.BUNGALOW
    }

    private val billboards = listOf("NU COLA", "DA BEARS", "VOTE O'FLAHERTY", "1-800-DRAIN", "WGN 720", "MALL OPENS SOON", "SAY NO TO UFOS")

    init {
        val rnd = Random(1986)
        // Ground: roads on the grid, sidewalks around each block.
        for (ty in 0 until H) for (tx in 0 until W) {
            set(tx, ty, when {
                tx >= CITY_W + 2 -> WATER
                tx >= CITY_W -> SAND
                isRoadRow(ty) || isRoadCol(tx) -> ROAD
                else -> WALK
            })
        }
        for (by in 0 until BY) for (bx in 0 until BX) {
            val x0 = bx * P + 2
            val y0 = by * P + 2
            val style = district(bx, by)
            if (by == RIVER_ROW) {
                // The Chicago River: water across the whole block row, bridges where the avenues cross.
                for (ty in y0 until y0 + 5) for (tx in 0 until CITY_W) if (!isRoadCol(tx)) set(tx, ty, WATER) else set(tx, ty, BRIDGE)
                for (ty in y0 until y0 + 5) for (tx in CITY_W until CITY_W + 2) set(tx, ty, WATER)
                continue
            }
            if (style == null) {
                for (ty in y0 + 1 until y0 + 4) for (tx in x0 + 1 until x0 + 4) set(tx, ty, GRASS)
                for (i in 0 until 4) trees += (x0 + 1) * T + rnd.nextInt(4, 44) to (y0 + 1) * T + rnd.nextInt(6, 44)
                lamps += x0 * T + 3 to y0 * T + 3
                continue
            }
            when (style) {
                Style.GAS -> {
                    for (ty in y0 until y0 + 5) for (tx in x0 until x0 + 5) set(tx, ty, LOT)
                    set(x0 + 3, y0 + 1, BUILD); set(x0 + 4, y0 + 1, BUILD)
                    buildings += Building(x0 + 3, y0 + 1, 2, 1, style, 9, rnd.nextInt(), "GAS 89")
                    gas += x0 * T + 32 to y0 * T + 56
                    payphones += x0 * T + 6 to y0 * T + 70
                }
                Style.STADIUM -> {
                    for (ty in y0 + 1 until y0 + 4) for (tx in x0 + 1 until x0 + 4) set(tx, ty, BUILD)
                    buildings += Building(x0 + 1, y0 + 1, 3, 3, style, 10, rnd.nextInt(), "WRIGGLY FIELD")
                }
                Style.HQ -> {
                    for (ty in y0 + 1 until y0 + 4) for (tx in x0 + 1 until x0 + 4) set(tx, ty, BUILD)
                    buildings += Building(x0 + 1, y0 + 1, 3, 3, style, 12, 7, "GALACTIC DRAIN")
                    hq = x0 + 2 to y0 + 4
                    payphones += x0 * T + 8 to y0 * T + 74
                }
                else -> {
                    for (ty in y0 + 1 until y0 + 4) for (tx in x0 + 1 until x0 + 4) set(tx, ty, BUILD)
                    val split = style == Style.BUNGALOW || style == Style.TWOFLAT || (style == Style.BROWNSTONE && rnd.nextBoolean())
                    if (split) {
                        for (i in 0 until 3) buildings += Building(x0 + 1 + i, y0 + 1, 1, 3, style, heightOf(style, rnd), rnd.nextInt())
                    } else {
                        val sign = if (style != Style.TOWER && rnd.nextInt(3) == 0) billboards.random(rnd) else null
                        buildings += Building(x0 + 1, y0 + 1, 3, 3, style, heightOf(style, rnd), rnd.nextInt(), sign)
                    }
                    sites += x0 + 2 to y0 + 4
                    if ((bx + by) % 3 == 1) payphones += x0 * T + 70 to y0 * T + 8
                }
            }
            lamps += x0 * T + 3 to y0 * T + 3
            lamps += x0 * T + 77 to y0 * T + 77
        }
        // Lake Shore Drive gets lamps all the way down.
        for (ty in 0 until H step 4) lamps += CITY_W * T + 6 to ty * T + 8
        buildings.sortBy { it.ty + it.th }
    }

    private fun heightOf(s: Style, rnd: Random) = when (s) {
        Style.TOWER -> rnd.nextInt(13, 17)
        Style.BROWNSTONE -> rnd.nextInt(10, 13)
        Style.TWOFLAT -> rnd.nextInt(9, 12)
        Style.FACTORY -> rnd.nextInt(10, 13)
        else -> rnd.nextInt(6, 9)
    }

    /** Street address of a tile in Chicago's grid: State & Madison is 0/0, 800 numbers to the mile. */
    fun address(tx: Int, ty: Int): String {
        val ns = (ty - 3 * P) * 100 / P * 4
        val ew = (tx - 6 * P) * 100 / P * 4
        val streetsNS = listOf("CLARK", "STATE", "HALSTED", "ASHLAND", "WESTERN", "DAMEN", "RACINE", "WELLS", "DEARBORN", "LA SALLE")
        val streetsEW = listOf("BELMONT", "ADDISON", "DIVISION", "MADISON", "ROOSEVELT", "CERMAK", "PERSHING")
        val num = abs(if (abs(ns) > abs(ew)) ns else ew).coerceAtLeast(100) / 100 * 100 + (tx * 7 + ty) % 50 * 2
        return if ((tx + ty) % 2 == 0) "$num ${if (ns < 0) "N" else "S"} ${streetsNS[(tx / P) % streetsNS.size]}"
        else "$num ${if (ew < 0) "W" else "E"} ${streetsEW[(ty / P) % streetsEW.size]}"
    }

    /** The El around the Loop as a closed path, in world pixels (clockwise). */
    val elPath: List<Pair<Float, Float>> = run {
        val x0 = LOOP_X0 * T + T.toFloat(); val x1 = LOOP_X1 * T + T.toFloat()
        val y0 = LOOP_Y0 * T + T.toFloat(); val y1 = LOOP_Y1 * T + T.toFloat()
        listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1)
    }
    val elLength: Float = run {
        val p = elPath
        (0 until 4).sumOf { i -> val a = p[i]; val b = p[(i + 1) % 4]; (abs(b.first - a.first) + abs(b.second - a.second)).toDouble() }.toFloat()
    }

    /** A point [s] pixels along the El, with the direction it faces (0 = east, 1 = south, 2 = west, 3 = north). */
    fun elAt(s: Float): Triple<Float, Float, Int> {
        var d = ((s % elLength) + elLength) % elLength
        for (i in 0 until 4) {
            val a = elPath[i]; val b = elPath[(i + 1) % 4]
            val len = abs(b.first - a.first) + abs(b.second - a.second)
            if (d <= len) {
                val t = d / len
                return Triple(a.first + (b.first - a.first) * t, a.second + (b.second - a.second) * t, i)
            }
            d -= len
        }
        return Triple(elPath[0].first, elPath[0].second, 0)
    }
}
