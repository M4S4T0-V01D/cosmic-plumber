package dev.m4s4t0.cosmicplumber

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import android.graphics.Paint as APaint

/** Driving the van around 1986 Chicago: the clock runs, the pager beeps, the gas gauge drops. */
class DriveScene(game: Game) : Scene(game) {
    private val save get() = game.save
    private val city get() = game.city

    private var x = 0f
    private var y = 0f
    private var angle = 0f
    private var speed = 0f
    private var camX = 0
    private var camY = 0
    private var t = 0f
    private var bumpCool = 0f
    private var autosave = 0f
    private var honkCool = 0f
    private var warnedFuel = false
    private var warnedLate = -1

    // Twin-stick style: the stick points where the van should go.
    private var stickId = -1
    private var stickOX = 0f
    private var stickOY = 0f
    private var stickX = 0f
    private var stickY = 0f

    private class Car(var x: Float, var y: Float, val dir: Int, val speed: Float, val sprite: Sprite) {
        var wait = 0f
    }
    private val cars = ArrayList<Car>()
    private var elS = 0f
    private val rnd = Random(7)

    private var shade: Bitmap? = null
    private var shadeCanvas: Canvas? = null
    private val glow: Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
        APaint().apply {
            shader = RadialGradient(32f, 32f, 32f, intArrayOf(0xFF000000.toInt(), 0xC0000000.toInt(), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        }.let { Canvas(b).drawCircle(32f, 32f, 32f, it) }
    }
    private val cut = APaint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); isFilterBitmap = true }
    private val lightPaint = APaint().apply { isFilterBitmap = false }
    private val src = Rect()
    private val dst = Rect()
    private val dstF = RectF()

    init {
        placeFromSave()
        spawnTraffic()
    }

    fun placeFromSave() {
        if (save.vanX < 0) {
            save.vanX = city.hq.first * T + T / 2f
            save.vanY = city.hq.second * T + T / 2f + 6
            save.vanAngle = 90f
        }
        x = save.vanX
        y = save.vanY
        angle = save.vanAngle
        speed = 0f
    }

    private fun spawnTraffic() {
        val P = City.P
        for (k in 0 until City.BY + 1) {
            if (rnd.nextFloat() < 0.7f) cars += car(rnd.nextFloat() * City.CITY_W * T, (k * P + 1) * T + 8f, 0)
            if (rnd.nextFloat() < 0.7f) cars += car(rnd.nextFloat() * City.CITY_W * T, (k * P) * T + 8f, 2)
        }
        for (k in 0 until City.BX + 1) {
            if (rnd.nextFloat() < 0.6f) cars += car((k * P) * T + 8f, rnd.nextFloat() * City.H * T, 1)
            if (rnd.nextFloat() < 0.6f) cars += car((k * P + 1) * T + 8f, rnd.nextFloat() * City.H * T, 3)
        }
    }

    private fun car(x: Float, y: Float, dir: Int) = Car(x, y, dir, 34f + rnd.nextFloat() * 22f, Sprites.cars[rnd.nextInt(Sprites.cars.size)])

    override fun enter() {
        stickId = -1
        if (save.music) game.playRadio()
    }

    override fun leave() {
        save.vanX = x
        save.vanY = y
        save.vanAngle = angle
        stickId = -1
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float) = hypot(ax - bx, ay - by)
    private fun siteX(j: Job) = j.tx * T + T / 2f
    private fun siteY(j: Job) = j.ty * T + T / 2f

    private fun nearJob() = save.jobs.filter { dist(x, y, siteX(it), siteY(it)) < 24 }.minByOrNull { dist(x, y, siteX(it), siteY(it)) }
    private fun nearPhone() = save.level("cb") == 0 && city.payphones.any { (px, py) -> dist(x, y, px + 2f, py + 3f) < 24 }
    private fun nearGas() = city.gas.any { (gx, gy) -> dist(x, y, gx.toFloat(), gy.toFloat()) < 40 }
    private fun nearHq() = dist(x, y, city.hq.first * T + T / 2f, city.hq.second * T + T / 2f) < 24

    /** Signed smallest difference a - b in degrees. */
    private fun angDiff(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /** Which way (-1, 1, or 0 for neither) a sideways shift of up to 5px frees the spot ahead. */
    private fun cornerSlide(px: Float, py: Float, vertical: Boolean): Int {
        for (d in 1..5) for (s in intArrayOf(-1, 1)) {
            val free = if (vertical) !blocked(px, py + s * d) else !blocked(px + s * d, py)
            if (free) return s
        }
        return 0
    }

    private fun blocked(px: Float, py: Float): Boolean {
        for ((ox, oy) in CORNERS) if (city.solidAt(px + ox, py + oy)) return true
        return false
    }

    override fun update(dt: Float) {
        t += dt
        bumpCool -= dt
        honkCool -= dt
        clock(dt)
        autosave += dt
        if (autosave > 20f) {
            // apply() writes off the main thread; this keeps a kill or crash from costing more than a few blocks.
            autosave = 0f
            save.vanX = x; save.vanY = y; save.vanAngle = angle
            game.persist()
        }
        drive(dt)
        traffic(dt)
        elS += dt * 70f
    }

    private fun clock(dt: Float) {
        val mins = dt * 2f
        save.minutes += mins
        if (save.minutes >= 24 * 60f) {
            save.minutes -= 24 * 60f
            save.day++
            game.toast("MIDNIGHT. IT'S ${save.date()}")
        }
        val h = save.hour
        if (h in 1.5f..5f && warnedLate != save.day) {
            warnedLate = save.day
            game.toast("IT'S LATE. HEAD BACK TO THE GARAGE AND SLEEP.")
        }
        save.pagerClock += mins
        if (save.pagerClock >= 90f) {
            save.pagerClock -= 90f
            if (save.level("cb") > 0) {
                if (game.newCalls(1) > 0) {
                    game.audio.play(Audio.Sfx.PAGER, 0.8f)
                    game.buzz(60)
                    game.toast("CB: NEW CALL ON THE CLIPBOARD. 10-4!")
                }
            } else if (save.pager + save.jobs.size < MAX_JOBS) {
                save.pager++
                game.audio.play(Audio.Sfx.PAGER, 0.8f)
                game.buzz(60)
                game.toast("BEEP BEEP! DISPATCH PAGED YOU. FIND A PAYPHONE.")
            }
        }
    }

    private fun drive(dt: Float) {
        val grip = city.grip(x, y)
        val top = (if (save.fuel <= 0f) 18f else 72f + 14f * save.level("engine")) * grip
        var target = 0f
        if (stickId != -1) {
            val dx = stickX - stickOX
            val dy = stickY - stickOY
            val mag = (hypot(dx, dy) / 22f).coerceAtMost(1f)
            if (mag > 0.15f) {
                val want = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                var diff = angDiff(want, angle)
                val turn = 210f * (abs(speed) / 40f).coerceIn(0.3f, 1f) * dt
                if (abs(diff) > 115f) {
                    if (speed > 6f) target = 0f // brake before backing up
                    else {
                        diff = angDiff(want + 180f, angle)
                        target = -top * 0.45f * mag
                        angle += diff.coerceIn(-turn, turn) * 0.6f
                    }
                } else {
                    target = top * mag * cos(Math.toRadians(diff.toDouble())).toFloat().coerceAtLeast(0.35f)
                    angle += diff.coerceIn(-turn, turn)
                }
            }
        }
        val rate = if (abs(target) < abs(speed) || target * speed < 0) 170f else 95f
        speed += (target - speed).coerceIn(-rate * dt, rate * dt)
        angle = (angle + 360f) % 360f

        val rad = Math.toRadians(angle.toDouble())
        val nx = x + cos(rad).toFloat() * speed * dt
        val ny = y + sin(rad).toFloat() * speed * dt
        var hit = false
        val slide = abs(speed) * dt * 0.8f
        if (!blocked(nx, y)) x = nx
        else if (abs(nx - x) > abs(ny - y)) {
            // Clipping a corner head-on: ease sideways toward the open side instead of stopping dead.
            val s = cornerSlide(nx, y, vertical = true)
            if (s != 0 && !blocked(x, y + s * slide)) y += s * slide else hit = true
        } else hit = true
        if (!blocked(x, ny)) y = ny
        else if (abs(ny - y) >= abs(nx - x)) {
            val s = cornerSlide(x, ny, vertical = false)
            if (s != 0 && !blocked(x + s * slide, y)) x += s * slide else hit = true
        } else hit = true
        for (c in cars) if (dist(x, y, c.x, c.y) < 11f) {
            hit = true
            val a = atan2(y - c.y, x - c.x)
            val px = x + cos(a) * 2f
            val py = y + sin(a) * 2f
            if (!blocked(px, py)) { x = px; y = py }
        }
        if (hit) {
            if (abs(speed) > 30f && bumpCool <= 0f) {
                game.audio.play(Audio.Sfx.BUMP, (abs(speed) / 90f).coerceIn(0.3f, 1f))
                game.buzz(35)
                bumpCool = 0.4f
            }
            speed *= -0.3f
        }

        if (save.fuel > 0f) {
            save.fuel = (save.fuel - abs(speed) * dt * 0.0042f).coerceAtLeast(0f)
            if (save.fuel < 15f && !warnedFuel) {
                warnedFuel = true
                game.toast("LOW ON GAS! FIND A STATION.")
            }
            if (save.fuel <= 0f) game.toast("OUT OF GAS. PUSHING IT...")
        }
        if (save.fuel > 20f) warnedFuel = false
    }

    private fun traffic(dt: Float) {
        val w = City.CITY_W * T.toFloat()
        val h = City.H * T.toFloat()
        for (c in cars) {
            val dx = DX[c.dir]
            val dy = DY[c.dir]
            // Stop for the van if it's just ahead in this lane.
            val ahead = (x - c.x) * dx + (y - c.y) * dy
            val side = abs((x - c.x) * dy - (y - c.y) * dx)
            if (ahead in 4f..26f && side < 10f) {
                c.wait += dt
                if (c.wait > 1.6f && honkCool <= 0f && dist(x, y, c.x, c.y) < 40) {
                    game.audio.play(Audio.Sfx.HONK, 0.5f, 0.9f + rnd.nextFloat() * 0.3f)
                    honkCool = 2.5f
                    c.wait = 0f
                }
                continue
            }
            c.wait = 0f
            c.x += dx * c.speed * dt
            c.y += dy * c.speed * dt
            if (c.x < -20) c.x += w + 40
            if (c.x > w + 20) c.x -= w + 40
            if (c.y < -20) c.y += h + 40
            if (c.y > h + 20) c.y -= h + 40
        }
    }

    private fun honk() {
        if (honkCool > 0f) return
        honkCool = 0.5f
        game.audio.play(Audio.Sfx.HONK)
    }

    private fun callDispatch() {
        game.audio.play(Audio.Sfx.DIME)
        if (save.pager > 0) {
            val n = game.newCalls(save.pager)
            save.pager = 0
            game.toast(if (n > 0) "DOLORES: $n NEW CALL${if (n > 1) "S" else ""}. CHECK THE CLIPBOARD." else "DOLORES: YOUR CLIPBOARD'S FULL, HON.")
        } else game.toast("DOLORES: NOTHING NEW. QUIT TYING UP THE LINE.")
    }

    private fun gasPrice() = ((save.tankSize - save.fuel) * 0.2225f).toInt() + 1

    private fun buyGas() {
        val need = save.tankSize - save.fuel
        if (need < 1f) return game.toast("TANK'S FULL.")
        val cost = gasPrice()
        if (save.money >= cost) {
            save.money -= cost
            save.fuel = save.tankSize
        } else if (save.money > 0) {
            save.fuel += save.money / 0.2225f
            save.money = 0
        } else return game.toast("NO MONEY, NO GAS. THIS AIN'T A CHARITY.")
        game.audio.play(Audio.Sfx.COIN)
        game.toast("FILLED UP. 89 CENTS A GALLON, HIGHWAY ROBBERY.")
    }

    override fun down(id: Int, x: Float, y: Float) {
        if (stickId == -1 && x < game.g.w * 0.6f) {
            stickId = id
            stickOX = x; stickOY = y; stickX = x; stickY = y
        }
    }

    override fun move(id: Int, x: Float, y: Float) {
        if (id != stickId) return
        stickX = x; stickY = y
        // The base follows a finger that strays far, like a floating stick.
        val dx = x - stickOX
        val dy = y - stickOY
        val d = hypot(dx, dy)
        if (d > 30f) { stickOX = x - dx / d * 30f; stickOY = y - dy / d * 30f }
    }

    override fun up(id: Int, x: Float, y: Float) {
        if (id == stickId) stickId = -1
    }

    override fun back() = game.go(ClipboardScene(game, this))

    override fun draw(g: Gfx) {
        val art = game.art
        val worldW = City.W * T
        val worldH = City.H * T
        val rad = Math.toRadians(angle.toDouble())
        camX = (x - g.w / 2 + cos(rad).toFloat() * speed * 0.35f).toInt().coerceIn(0, worldW - g.w)
        camY = (y - g.h / 2 + sin(rad).toFloat() * speed * 0.25f).toInt().coerceIn(0, worldH - g.h)
        val c = g.c
        c.save()
        c.translate(-camX.toFloat(), -camY.toFloat())
        src.set(camX, camY, camX + g.w, camY + g.h)
        c.drawBitmap(art.day, src, src, null)
        shimmer(g)
        for (j in save.jobs) marker(g, j)
        for (car in cars) if (onScreen(car.x, car.y, g)) {
            g.rect(car.x.toInt() - 5, car.y.toInt() - 2, 12, 6, Pal.alpha(Pal.INK, 60))
            g.rotated(car.sprite.bmp, car.x, car.y, car.dir * 90f)
        }
        val van = Sprites.van(Catalog.paint(save.paint))
        g.circle(x + 2, y + 3, 8f, Pal.alpha(Pal.INK, 70))
        g.rotated(van.bmp, x, y, angle)
        el(g)
        c.restore()
        lighting(g)
        hud(g)
    }

    private fun onScreen(px: Float, py: Float, g: Gfx) = px > camX - 30 && px < camX + g.w + 30 && py > camY - 30 && py < camY + g.h + 30

    private fun shimmer(g: Gfx) {
        val tx0 = camX / T
        val ty0 = camY / T
        for (ty in ty0..ty0 + g.h / T + 1) for (tx in tx0..tx0 + g.w / T + 1) {
            if (city.tile(tx, ty) != City.WATER) continue
            val k = (tx * 31 + ty * 17) % 13
            val ph = ((t * 0.8f + k / 13f) % 1f)
            val a = (sin(ph * Math.PI).toFloat() * 120).toInt()
            g.rect(tx * T + k, ty * T + (k * 3) % 14, 3 + k % 3, 1, Pal.alpha(Pal.CYAN, a))
        }
    }

    private fun marker(g: Gfx, j: Job) {
        val mx = siteX(j)
        val my = siteY(j)
        if (!onScreen(mx, my, g)) return
        val bob = sin(t * 4 + j.id) * 2
        val col = if (j.id == save.target) Pal.GREEN else if (j.cosmic) Pal.MAGENTA else Pal.AMBER
        val r = 5f + sin(t * 5) * 1f
        g.circle(mx, my + 2, r + 2, Pal.alpha(col, 60))
        g.rect(mx.toInt() - 4, (my - 18 + bob).toInt(), 9, 11, Pal.INK)
        g.rect(mx.toInt() - 3, (my - 17 + bob).toInt(), 7, 9, col)
        g.text("!", mx.toInt() - 1, (my - 15 + bob).toInt(), Pal.INK, 1, null)
        g.rect(mx.toInt(), (my - 7 + bob).toInt(), 1, 5, Pal.INK)
    }

    private fun el(g: Gfx) {
        val p = city.elPath
        for (i in 0 until 4) {
            val (ax, ay) = p[i]
            val (bx, by) = p[(i + 1) % 4]
            val horiz = ay == by
            val x0 = minOf(ax, bx).toInt() - 5
            val y0 = minOf(ay, by).toInt() - 5
            val w = if (horiz) abs(bx - ax).toInt() + 10 else 10
            val h = if (horiz) 10 else abs(by - ay).toInt() + 10
            if (x0 > camX + g.w || y0 > camY + g.h || x0 + w < camX || y0 + h < camY) continue
            g.rect(x0 + 3, y0 + 4, w, h, Pal.alpha(Pal.INK, 60))
            g.rect(x0, y0, w, h, 0xFF3C3A44.toInt())
            if (horiz) {
                var k = x0
                while (k < x0 + w) { g.rect(k, y0 + 1, 1, h - 2, Pal.DKBROWN); k += 3 }
                g.rect(x0, y0 + 2, w, 1, Pal.LGRAY); g.rect(x0, y0 + h - 3, w, 1, Pal.LGRAY)
            } else {
                var k = y0
                while (k < y0 + h) { g.rect(x0 + 1, k, w - 2, 1, Pal.DKBROWN); k += 3 }
                g.rect(x0 + 2, y0, 1, h, Pal.LGRAY); g.rect(x0 + w - 3, y0, 1, h, Pal.LGRAY)
            }
            g.frame(x0, y0, w, h, Pal.INK)
        }
        for (k in 0 until 4) {
            val (tx, ty, d) = city.elAt(elS - k * 27f)
            if (onScreen(tx, ty, g)) g.rotated(Sprites.train.bmp, tx, ty, d * 90f)
        }
    }

    private fun lighting(g: Gfx) {
        val h = save.hour
        val dusk = maxOf(1f - abs(h - 19f) / 1.6f, 1f - abs(h - 6.3f) / 1.3f).coerceIn(0f, 1f)
        if (dusk > 0f) g.rect(0, 0, g.w, g.h, Pal.alpha(Pal.MAGENTA, (dusk * 40).toInt()))
        val dark = save.darkness
        if (dark < 0.02f) return
        val sb = shade.takeIf { it?.width == g.w && it.height == g.h }
            ?: Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888).also { shade = it; shadeCanvas = Canvas(it) }
        val sc = shadeCanvas!!
        sc.drawColor(0, PorterDuff.Mode.CLEAR)
        sc.drawColor(Pal.alpha(0xFF0A0830.toInt(), (dark * 175).toInt()))
        fun hole(wx: Float, wy: Float, r: Float) {
            val sx = wx - camX
            val sy = wy - camY
            if (sx < -r || sy < -r || sx > g.w + r || sy > g.h + r) return
            dstF.set(sx - r, sy - r, sx + r, sy + r)
            sc.drawBitmap(glow, null, dstF, cut)
        }
        for ((lx, ly) in city.lamps) hole(lx + 1f, ly + 1f, 30f)
        val rad = Math.toRadians(angle.toDouble())
        hole(x + cos(rad).toFloat() * 24f, y + sin(rad).toFloat() * 24f, 26f)
        hole(x, y, 12f)
        for (car in cars) hole(car.x + DX[car.dir] * 14f, car.y + DY[car.dir] * 14f, 12f)
        val (tx, ty, _) = city.elAt(elS)
        hole(tx, ty, 16f)
        g.c.drawBitmap(sb, 0f, 0f, null)
        // Windows, signs and lamps glow on top of the dark.
        lightPaint.alpha = (dark * 255).toInt()
        src.set(camX, camY, camX + g.w, camY + g.h)
        dst.set(0, 0, g.w, g.h)
        g.c.drawBitmap(game.art.lights, src, dst, lightPaint)
    }

    private fun hud(g: Gfx) {
        g.panel(3, 3, 92, 30, Pal.alpha(Pal.NIGHT, 220), Pal.AMBER)
        g.text(save.date(), 7, 7, Pal.CREAM)
        g.text(save.clock(), 7, 15, if (save.night) Pal.CYAN else Pal.YELLOW)
        g.text("\$${save.money}", 52, 15, Pal.GREEN)
        g.text("GAS", 7, 24, Pal.LGRAY)
        g.rect(21, 24, 70, 5, Pal.INK)
        val f = save.fuel / save.tankSize
        g.rect(21, 24, (70 * f).toInt(), 5, if (f < 0.2f && (t * 3).toInt() % 2 == 0) Pal.RED else Pal.AMBER)

        if (save.pager > 0) {
            val blink = (t * 2).toInt() % 2 == 0
            g.panel(98, 3, 74, 16, Pal.INK, if (blink) Pal.GREEN else Pal.DKGREEN)
            g.rect(101, 6, 18, 10, 0xFF9AB89A.toInt())
            g.text("${save.pager}", 104, 8, Pal.INK, 1, null)
            g.text("PAGE", 121, 5, Pal.GREEN, 1, null)
            g.text("PAYPHONE", 121, 11, Pal.LGRAY, 1, null)
        }

        g.button(g.w - 58, 4, 54, 16, "CLIPBOARD", Pal.BROWN) { game.go(ClipboardScene(game, this)) }

        // Compass toward the job on the clipboard's top.
        save.jobs.firstOrNull { it.id == save.target }?.let { j ->
            val dx = siteX(j) - x
            val dy = siteY(j) - y
            val d = hypot(dx, dy)
            val blocks = (d / (City.P * T)).let { if (it < 0.5f) "HERE" else "%.1f BLOCKS".format(it) }
            g.text(blocks, g.w - 58, 23, Pal.GREEN)
            if (d > 30f) {
                val a = atan2(dy, dx)
                val sx = x - camX
                val sy = y - camY
                for ((r, s) in listOf(18f to 3f, 23f to 2f, 27f to 1.2f)) {
                    g.circle(sx + cos(a) * r, sy + sin(a) * r, s + 1f, Pal.INK)
                    g.circle(sx + cos(a) * r, sy + sin(a) * r, s, Pal.GREEN)
                }
            }
        }

        // Context actions, bottom right.
        var by = g.h - 22
        val bx = g.w - 70
        g.button(bx, by, 66, 18, "HONK", Pal.DGRAY.let { Pal.GRAY }) { honk() }
        by -= 22
        nearJob()?.let { j ->
            g.button(bx, by, 66, 18, "DO THE JOB", Pal.DKGREEN) {
                speed = 0f
                game.go(JobIntroScene(game, j))
            }
            by -= 22
        }
        if (nearHq()) { g.button(bx, by, 66, 18, "GARAGE", Pal.PURPLE) { speed = 0f; game.go(HqScene(game)) }; by -= 22 }
        if (nearGas()) { g.button(bx, by, 66, 18, "GAS \$${gasPrice()}", Pal.RED) { buyGas() }; by -= 22 }
        if (nearPhone()) { g.button(bx, by, 66, 18, "PAYPHONE", Pal.BLUE) { callDispatch() } }

        // The stick.
        if (stickId != -1) {
            g.circle(stickOX, stickOY, 22f, Pal.alpha(Pal.CREAM, 40))
            g.circle(stickOX, stickOY, 21f, Pal.alpha(Pal.INK, 50))
            val dx = stickX - stickOX
            val dy = stickY - stickOY
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            val k = minOf(d, 22f) / d
            g.circle(stickOX + dx * k, stickOY + dy * k, 9f, Pal.alpha(Pal.ORANGE, 200))
        } else if (save.jobsDone == 0) {
            g.circle(40f, g.h - 40f, 22f, Pal.alpha(Pal.CREAM, 30))
            g.textC("DRAG TO DRIVE", 40, g.h - 12, Pal.CREAM)
        }
    }

    companion object {
        private val CORNERS = listOf(-5f to -4f, 5f to -4f, -5f to 4f, 5f to 4f)
        private val DX = floatArrayOf(1f, 0f, -1f, 0f)
        private val DY = floatArrayOf(0f, 1f, 0f, -1f)
    }
}
