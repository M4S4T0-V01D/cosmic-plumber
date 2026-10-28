package dev.m4s4t0.cosmicplumber

import kotlin.math.sin

const val MAX_JOBS = 6

/** Dolores writes [n] new calls onto the clipboard (as many as fit); returns how many. */
fun Game.newCalls(n: Int): Int {
    var added = 0
    repeat(n) {
        if (save.jobs.size < MAX_JOBS) {
            val taken = save.jobs.map { it.tx to it.ty }.toSet()
            val free = city.sites.filter { it !in taken }.ifEmpty { city.sites }
            save.jobs += Catalog.newJob(save.nextId++, save.day, save.night, free)
            added++
        }
    }
    if (save.jobs.none { it.id == save.target }) save.target = save.jobs.firstOrNull()?.id ?: -1
    return added
}

object People {
    val dolores = Sprite(listOf(
        "...hhhhhh...",
        "..hhhhhhhh..",
        ".hhhhhhhhhh.",
        ".hhssssssshh",
        "hhsGGsGGsshh",
        "hhsGwsGwsshh",
        "hhsssssssshh",
        ".hssslllsshh",
        "..sssssss.h.",
        "...pppppp...",
        "..pppppppp..",
        ".pppPPPPppp.",
        ".pppppppppp.",
    ), mapOf('h' to Pal.AMBER, 's' to 0xFFF0C8A0.toInt(), 'G' to Pal.MAGENTA, 'w' to Pal.CYAN, 'l' to Pal.RED,
        'p' to Pal.PINK, 'P' to Pal.CREAM))

    fun human(seed: Int): Sprite {
        val hair = listOf(Pal.DKBROWN, Pal.YELLOW, Pal.INK, Pal.BRICK, Pal.LGRAY)[Math.floorMod(seed, 5)]
        val skin = listOf(0xFFF0C8A0.toInt(), 0xFFC08A5A.toInt(), 0xFF8A5A3A.toInt(), 0xFFE0B080.toInt())[Math.floorMod(seed / 5, 4)]
        val shirt = listOf(Pal.BLUE, Pal.RED, Pal.TEAL, Pal.PURPLE, Pal.ORANGE, Pal.GRASS)[Math.floorMod(seed / 20, 6)]
        return Sprite(listOf(
            "...hhhhhh...",
            "..hhhhhhhh..",
            "..hssssssh..",
            "..sskssks...",
            "..ssssssss..",
            "..sssmmsss..",
            "...ssssss...",
            "....ssss....",
            "..cccccccc..",
            ".cccccccccc.",
            ".cccccccccc.",
        ), mapOf('h' to hair, 's' to skin, 'k' to Pal.INK, 'm' to Pal.DKRED, 'c' to shirt))
    }

    val alien = Sprite(listOf(
        "....g..g....",
        "....g..g....",
        "..gggggggg..",
        ".gggggggggg.",
        ".gKKggggKKg.",
        ".gKwggggKwg.",
        ".gggggggggg.",
        "..ggg__ggg..",
        "...gggggg...",
        "..SSSSSSSS..",
        ".SSSSSSSSSS.",
        ".SSSSSSSSSS.",
    ), mapOf('g' to 0xFF6ADF6A.toInt(), 'K' to Pal.INK, 'w' to Pal.WHITE, '_' to Pal.DKGREEN, 'S' to Pal.LGRAY))

    fun of(job: Job) = if (job.cosmic) alien else human(job.id * 7 + job.client.length)

    /** A portrait in a little frame, scaled up. */
    fun portrait(g: Gfx, s: Sprite, x: Int, y: Int, scale: Int, bg: Int) {
        g.panel(x, y, s.w * scale + 6, s.h * scale + 6, bg, Pal.AMBER)
        g.bmpScaled(s.bmp, x + 3, y + 3, scale)
    }

    /** The van from the side, for the garage. */
    fun vanSide(p: Paint) = Sprite(listOf(
        "....BBBBBBBBBBBBBBBBBBBBB.......",
        "...BBBBBBBBBBBBBBBBBBBBBBBB.....",
        "..BBwwwwBBBBBBBBBBBBBBBBBBBBB...",
        "..BBwwwwBBBBBBBBBBBBBBBBBBBBBB..",
        "..BBBBBBBBttttttttttttttttBBBBB.",
        "..BBBBBBBBttttttttttttttttBBBBBl",
        "rBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "rBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
        "kkBBkkkkBBBBBBBBBBBBBBkkkkBBBBkk",
        "...kKKk..............kKKk.......",
        "...kKKk..............kKKk.......",
        "....kk................kk........",
    ), mapOf('B' to p.color, 't' to p.trim, 'w' to Pal.SKY, 'k' to Pal.INK, 'K' to Pal.GRAY, 'r' to Pal.RED, 'l' to Pal.YELLOW))
}

class HqScene(game: Game) : Scene(game) {
    private var t = 0f
    private var line = ""
    private val save get() = game.save

    override fun enter() {
        val added = if (save.jobs.size < 3) game.newCalls(3 - save.jobs.size) else 0
        save.pager = 0
        line = when {
            !save.seenIntro -> {
                save.seenIntro = true
                "WELCOME TO GALACTIC DRAIN & SEWER, HON. WE FIX ALL PIPES. EVEN THE WEIRD ONES. YOUR CALLS ARE ON THE CLIPBOARD. " +
                    "WHEN YOUR PAGER BEEPS, FIND A PAYPHONE AND CALL ME."
            }
            added > 0 -> "$added NEW CALL${if (added > 1) "S" else ""} ON THE CLIPBOARD, HON. DRIVE SAFE, THE POTHOLES ARE UNION."
            save.night -> "IT'S LATE. NIGHT CALLS PAY TIME AND A HALF... AND THE WEIRD ONES COME OUT."
            save.fuel < 25 -> "YOU'RE RUNNING ON FUMES. GAS STATIONS ARE ON THE MAP, 89 CENTS A GALLON. HIGHWAY ROBBERY."
            else -> listOf(
                "THE ALDERMAN CALLED. HE SAYS HI. THAT MEANS PAY HIM.",
                "SOMEBODY SAW A FLYING SAUCER OVER WRIGGLY FIELD AGAIN.",
                "I TAPED THE BEARS GAME. DON'T TELL ME THE SCORE.",
                "MY COUSIN SAYS COMPUTERS WILL DO PLUMBING SOMEDAY. HA!",
            ).random()
        }
    }

    override fun update(dt: Float) {
        t += dt
        if (save.music) game.playRadio()
    }

    override fun draw(g: Gfx) {
        val side = 104
        val wallW = g.w - side
        // Back wall: brick, a window on the city, the pegboard.
        g.rect(0, 0, wallW, 118, Pal.BRICK)
        for (row in 0 until 30) {
            val y = row * 4
            g.rect(0, y + 3, wallW, 1, Pal.DKRED)
            for (x in (if (row % 2 == 0) 0 else 4) until wallW step 8) g.rect(x, y, 1, 3, Pal.DKRED)
        }
        val wx = 8; val wy = 18; val ww = 76; val wh = 46
        Skyline.sky(g, wx, wy, ww, wh, save.darkness, if (save.hour in 16f..20f || save.hour in 5f..7.5f) 1f else 0f)
        g.c.save(); g.c.clipRect(wx, wy, wx + ww, wy + wh)
        Skyline.city(g, wx - 40, wy + wh, ww + 60, save.darkness, Pal.mix(Pal.DGRAY, Pal.INK, save.darkness))
        g.c.restore()
        g.frame(wx - 2, wy - 2, ww + 4, wh + 4, Pal.LGRAY); g.rect(wx + ww / 2, wy, 1, wh, Pal.LGRAY); g.rect(wx, wy + wh / 2, ww, 1, Pal.LGRAY)
        // Pegboard with tools.
        val px = wx + ww + 12
        if (px + 60 < wallW) {
            g.rect(px, 14, 60, 40, Pal.TAN)
            for (yy in 16 until 54 step 4) for (xx in px + 2 until px + 60 step 4) g.rect(xx, yy, 1, 1, Pal.BROWN)
            g.rect(px + 6, 18, 3, 26, Pal.LGRAY); g.rect(px + 4, 18, 7, 4, Pal.LGRAY)
            g.rect(px + 18, 20, 16, 4, Pal.RED); g.rect(px + 24, 24, 4, 16, Pal.DKBROWN)
            g.circle(px + 46f, 32f, 8f, Pal.DGRAY); g.circle(px + 46f, 32f, 5f, Pal.TAN)
        }
        // A 1986 calendar.
        val cx = wallW - 34
        g.rect(cx, 66, 26, 30, Pal.CREAM); g.rect(cx, 66, 26, 8, Pal.RED)
        g.textC(save.date().split(' ')[1], cx + 13, 68, Pal.CREAM, 1, null)
        g.textC(save.date().split(' ')[2], cx + 13, 78, Pal.INK, 2, null)
        // Floor, oil stain, the van.
        g.rect(0, 118, wallW, g.h - 118, 0xFF5A5866.toInt())
        g.rect(0, 118, wallW, 2, Pal.DGRAY)
        g.circle(60f, 160f, 14f, 0xFF4A4856.toInt())
        val van = PeopleCache.side(Catalog.paint(save.paint))
        g.bmpScaled(van.bmp, 14, 112, 3)
        g.text("GALACTIC DRAIN", 44, 129, Pal.CREAM, 1, null)
        // Dolores at her desk with the rotary phone.
        val dx = wallW - 70
        g.bmpScaled(People.dolores.bmp, dx + 14, 70, 3)
        g.rect(dx, 106, 66, 6, Pal.BROWN); g.rect(dx + 2, 112, 4, 24, Pal.DKBROWN); g.rect(dx + 60, 112, 4, 24, Pal.DKBROWN)
        g.rect(dx + 44, 100, 12, 6, Pal.RED); g.rect(dx + 42, 98, 16, 3, Pal.RED); g.circle(dx + 50f, 103f, 2f, Pal.CREAM)
        // Her speech bubble.
        if (line.isNotEmpty()) drawBubble(g, 6, 4, wallW - 12)

        // Status column.
        g.rect(wallW, 0, side, g.h, Pal.NIGHT)
        g.rect(wallW, 0, 1, g.h, Pal.AMBER)
        val sx = wallW + 6
        g.text(save.date(), sx, 5, Pal.CREAM)
        g.text(save.clock(), sx, 13, if (save.night) Pal.CYAN else Pal.YELLOW)
        g.text("\$${save.money}", sx, 21, Pal.GREEN)
        g.text("GAS", sx + 44, 21, Pal.LGRAY)
        g.rect(sx + 58, 21, 32, 5, Pal.INK)
        g.rect(sx + 58, 21, (32 * save.fuel / save.tankSize).toInt(), 5, if (save.fuel < 25) Pal.RED else Pal.AMBER)
        val bw2 = side - 12
        var y = 34
        g.button(sx, y, bw2, 22, "HIT THE ROAD", Pal.DKGREEN) {
            save.vanX = game.city.hq.first * T + T / 2f
            save.vanY = game.city.hq.second * T + T / 2f + 6
            save.vanAngle = 90f
            game.drive.placeFromSave()
            game.go(game.drive)
        }
        y += 28
        g.button(sx, y, bw2, 18, "CLIPBOARD (${save.jobs.size})") { game.go(ClipboardScene(game, this)) }
        y += 23
        g.button(sx, y, bw2, 18, "SUPPLY STORE", Pal.MAGENTA) { game.go(ShopScene(game, this)) }
        y += 23
        g.button(sx, y, bw2, 18, if (save.hour >= 6 && save.hour < 17) "CLOCK OUT" else "SLEEP", Pal.PURPLE) { sleep() }
        y += 23
        g.button(sx, y, bw2, 18, "SETTINGS", Pal.GRAY) { game.go(SettingsScene(game, this)) }
        if (Updater.state is Updater.State.Available && (t * 2).toInt() % 2 == 0) g.rect(sx + bw2 - 6, y + 2, 4, 4, Pal.GREEN)
        g.textC("JOBS DONE: ${save.jobsDone}", wallW + side / 2, g.h - 9, Pal.GRAY, 1, null)
    }

    private fun drawBubble(g: Gfx, x: Int, y: Int, w: Int) {
        // The font is fixed-width, so the wrapped height can be counted up front.
        val perLine = (w - 10 + 1) / 4
        var lines = 1
        var len = 0
        for (word in line.split(' ')) {
            if (len > 0 && len + 1 + word.length > perLine) { lines++; len = word.length } else len += (if (len > 0) 1 else 0) + word.length
        }
        val h = lines * 7 + 8
        val by = 66 - h + 2
        g.panel(x, by.coerceAtLeast(y), w, h, Pal.CREAM, Pal.INK)
        g.rect(x + w - 50, by.coerceAtLeast(y) + h, 6, 4, Pal.CREAM)
        g.para(line, x + 5, by.coerceAtLeast(y) + 5, w - 10, Pal.INK)
        g.text("DOLORES", x + w - 82,by.coerceAtLeast(y) + h + 1, Pal.YELLOW)
        g.hotspot(x, by.coerceAtLeast(y), w, h) { line = "" }
    }

    private fun sleep() {
        if (save.hour >= 6) save.day++
        save.minutes = 7 * 60f
        save.jobs.clear()
        save.target = -1
        save.pagerClock = 0f
        game.newCalls(3)
        save.pager = 0
        game.audio.play(Audio.Sfx.WIN, 0.6f)
        game.toast("GOOD MORNING! ${save.date()}")
        line = "RISE AND SHINE. YESTERDAY'S CALLS FOUND ANOTHER GUY. HERE'S TODAY'S."
    }

    override fun back() = game.go(TitleScene(game))
}

/** Side-view vans are rebuilt only when the paint changes. */
object PeopleCache {
    private val vans = HashMap<String, Sprite>()
    fun side(p: Paint) = vans.getOrPut(p.id) { People.vanSide(p) }
}
