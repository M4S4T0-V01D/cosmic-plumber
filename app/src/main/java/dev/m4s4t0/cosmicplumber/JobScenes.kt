package dev.m4s4t0.cosmicplumber

import kotlin.math.sin

/** At the customer's door: who they are, what's wrong, and what it pays. */
class JobIntroScene(game: Game, private val job: Job) : Scene(game) {
    private var t = 0f

    override fun update(dt: Float) { t += dt }

    override fun back() = game.go(game.drive)

    private fun start() = game.go(if (job.kind == Kind.PIPES) PipeScene(game, job) else RhythmScene(game, job))

    override fun draw(g: Gfx) {
        val save = game.save
        g.rect(0, 0, g.w, g.h, if (job.cosmic) Pal.INK else Pal.NIGHT)
        if (job.cosmic) for (i in 0 until 40) {
            val sx = (i * 97 + (t * (5 + i % 7)).toInt()) % g.w
            g.rect(sx, (i * 53) % g.h, 1, 1, Pal.alpha(Pal.CREAM, 80 + (i * 37) % 170))
        } else for (y in 0 until g.h step 8) g.rect(0, y, g.w, 4, Pal.alpha(Pal.PURPLE, 70))

        val s = People.of(job)
        val bob = (sin(t * 3) * 1.5f).toInt()
        People.portrait(g, s, 10, 14 + bob, 5, if (job.cosmic) Pal.DKGREEN else Pal.PLUM)
        val x = 10 + s.w * 5 + 18
        val w = g.w - x - 10
        g.text(job.client, x, 14, if (job.cosmic) Pal.GREEN else Pal.AMBER, 1)
        g.text(game.city.address(job.tx, job.ty), x, 23, Pal.GRAY, 1, null)
        var y = 34
        y += g.para(job.problem, x, y, w, Pal.CREAM) + 4
        y += g.para(job.quote, x, y, w, Pal.PINK) + 6
        g.text("JOB: ${job.kind.label}", x, y, Pal.TEAL); y += 9
        g.text("DIFFICULTY:", x, y, Pal.TEAL)
        for (k in 0 until 5) g.rect(x + 48 + k * 7, y, 5, 5, if (k < job.level) Pal.ORANGE else Pal.DGRAY)
        y += 9
        val ot = if (save.night) "  (NIGHT OVERTIME x1.5!)" else ""
        g.text("PAYS UP TO \$${Catalog.payout(job, 1f, save.night, save.level("alderman"))}$ot", x, y, Pal.GREEN)
        y += 9
        val tip = if (job.kind == Kind.PIPES) "TAP PIPES TO TURN THEM. CONNECT THE LEAK TO THE DRAIN BEFORE THE PRESSURE BLOWS."
        else "TAP THE LANE WHEN A BOLT HITS THE WRENCH LINE. STAY ON THE BEAT."
        g.para(tip, x, y + 2, w, Pal.LGRAY)

        g.button(10, g.h - 26, 70, 20, "LATER", Pal.GRAY) { back() }
        g.button(g.w - 110, g.h - 26, 100, 20, "GET TO WORK", Pal.DKGREEN) { start() }
    }
}

/** The bill, the payment and how the customer took it. */
class ResultScene(game: Game, private val job: Job, private val score: Float, private val success: Boolean) : Scene(game) {
    private var t = 0f
    private val pay: Int
    private val line: String

    init {
        val save = game.save
        val minutes = 40f + job.level * 10f
        if (success) {
            pay = Catalog.payout(job, score, save.night, save.level("alderman"))
            game.earn(pay)
            save.jobs.removeAll { it.id == job.id }
            save.jobsDone++
            if (save.target == job.id) save.target = save.jobs.firstOrNull()?.id ?: -1
            line = when {
                job.cosmic -> listOf("\"THE COSMOS THANKS YOU, EARTH PLUMBER.\"", "\"GLORP IS PLEASED. GLORP TIPS.\"", "\"OUR PORTAL IS SEALED. NICE CRACK, BY THE WAY.\"").random()
                score > 0.85f -> listOf("\"WOW, FASTER THAN THE BEARS' DEFENSE!\"", "\"YOU'RE A REAL PIPE WIZARD, HON.\"", "\"BETTER THAN MY BROTHER-IN-LAW. WAY BETTER.\"").random()
                else -> listOf("\"IT WORKS. I GUESS. HERE.\"", "\"TOOK YOU LONG ENOUGH.\"", "\"FINE. DON'T TRACK MUD ON THE SHAG.\"").random()
            }
            game.audio.play(Audio.Sfx.WIN)
            game.audio.play(Audio.Sfx.COIN, 1f, 1.2f)
        } else {
            pay = 0
            line = if (job.cosmic) "\"PATHETIC. RETURN WHEN YOUR SKILLS EVOLVE.\"" else "\"YOU MADE IT WORSE! COME BACK AND FIX IT!\""
            game.audio.play(Audio.Sfx.FAIL)
        }
        save.minutes += minutes
        game.buzz(if (success) 50 else 120)
        game.persist()
    }

    override fun update(dt: Float) { t += dt }

    override fun back() = game.go(game.drive)

    override fun draw(g: Gfx) {
        g.rect(0, 0, g.w, g.h, Pal.NIGHT)
        val pw = 180
        val px = (g.w - pw) / 2
        // A carbon-copy invoice.
        g.rect(px + 3, 13, pw, 140, Pal.alpha(Pal.INK, 140))
        g.rect(px, 10, pw, 140, if (success) Pal.CREAM else 0xFFFFD6D6.toInt())
        g.rect(px, 10, pw, 14, Pal.DKRED)
        g.textC("GALACTIC DRAIN & SEWER", px + pw / 2, 15, Pal.CREAM)
        g.text("INVOICE #${1000 + job.id}", px + 6, 28, Pal.INK, 1, null)
        g.text(game.save.date(), px + pw - Font.width(game.save.date()) - 6, 28, Pal.GRAY, 1, null)
        g.text("BILL TO: ${job.client}", px + 6, 37, Pal.INK, 1, null)
        g.rect(px + 6, 45, pw - 12, 1, Pal.GRAY)
        g.text(job.kind.label, px + 6, 49, Pal.INK, 1, null)
        g.text("${(score * 100).toInt()}%", px + pw - 26, 49, Pal.INK, 1, null)
        if (game.save.night) g.text("NIGHT OVERTIME x1.5", px + 6, 57, Pal.PLUM, 1, null)
        val ald = game.save.level("alderman")
        if (ald > 0) g.text("WARD 'CONSIDERATION' +${ald * 15}%", px + 6, 65, Pal.PLUM, 1, null)
        g.rect(px + 6, 74, pw - 12, 1, Pal.GRAY)
        g.text("TOTAL", px + 6, 79, Pal.INK, 2, null)
        val amount = "\$$pay"
        g.text(amount, px + pw - Font.width(amount, 2) - 6, 79, if (success) Pal.DKGREEN else Pal.DKRED, 2, null)
        g.para(line, px + 6, 96, pw - 12, Pal.DGRAY)
        if (t > 0.4f) {
            // The stamp lands a beat after the invoice.
            val sc = if (t < 0.55f) 3 else 2
            val word = if (success) "PAID" else "REDO"
            val c = if (success) Pal.alpha(Pal.GREEN, 200) else Pal.alpha(Pal.RED, 210)
            val sw = Font.width(word, sc) + 8
            g.frame(px + pw - sw - 10, 116, sw, 5 * sc + 8, c)
            g.text(word, px + pw - sw - 6, 120, c, sc, null)
        }
        if (!success) g.text("THE JOB STAYS ON YOUR CLIPBOARD.", px + 6, 140, Pal.DKRED, 1, null)
        g.button(g.w / 2 - 55, g.h - 24, 110, 18, "BACK TO THE VAN", Pal.ORANGE) { back() }
    }
}
