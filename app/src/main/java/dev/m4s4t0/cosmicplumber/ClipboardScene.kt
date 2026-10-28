package dev.m4s4t0.cosmicplumber

/** The clipboard: today's calls, handwritten by Dolores. Tap one to make it the job you're heading to. */
class ClipboardScene(game: Game, private val from: Scene) : Scene(game) {
    private var t = 0f

    override fun update(dt: Float) { t += dt }

    override fun back() = game.go(from)

    override fun draw(g: Gfx) {
        val save = game.save
        g.rect(0, 0, g.w, g.h, Pal.DKBROWN)
        for (y in 0 until g.h step 6) g.rect(0, y, g.w, 1, Pal.alpha(Pal.BROWN, 90))
        val bw = minOf(g.w - 16, 300)
        val bx = (g.w - bw) / 2
        // Masonite board, paper, and the spring clip.
        g.rect(bx + 3, 7, bw, g.h - 8, Pal.alpha(Pal.INK, 120))
        g.rect(bx, 4, bw, g.h - 8, Pal.TAN)
        g.frame(bx, 4, bw, g.h - 8, Pal.BROWN)
        val px = bx + 8
        val pw = bw - 16
        g.rect(px, 14, pw, g.h - 24, Pal.CREAM)
        for (y in 34 until g.h - 12 step 8) g.rect(px, y, pw, 1, Pal.alpha(Pal.SKY, 90))
        g.rect(px + 12, 14, 1, g.h - 24, Pal.alpha(Pal.RED, 120))
        g.rect(bx + bw / 2 - 22, 2, 44, 10, Pal.LGRAY)
        g.rect(bx + bw / 2 - 22, 10, 44, 2, Pal.GRAY)
        g.rect(bx + bw / 2 - 6, 4, 12, 4, Pal.DGRAY)

        g.text("WORK ORDERS - ${save.date()}", px + 16, 18, Pal.INK, 1, null)
        g.text(save.clock(), px + pw - Font.width(save.clock()) - 4, 18, Pal.DKRED, 1, null)
        val pagerLine = when {
            save.level("cb") > 0 -> "CB RADIO ON. DOLORES RADIOS NEW CALLS."
            save.pager > 0 -> "PAGER: ${save.pager} PAGE${if (save.pager > 1) "S" else ""}. CALL DISPATCH FROM A PAYPHONE!"
            else -> "PAGER QUIET. WAIT FOR THE BEEP."
        }
        g.text(pagerLine, px + 16, 26, if (save.pager > 0) Pal.DKRED else Pal.GRAY, 1, null)

        if (save.jobs.isEmpty()) {
            g.para("NO CALLS. ENJOY AN OLD STYLE AND WAIT FOR THE PAGER, OR SLEEP AT THE GARAGE.", px + 16, 50, pw - 24, Pal.DGRAY)
        }
        var y = 36
        for (j in save.jobs) {
            val sel = j.id == save.target
            if (sel) g.rect(px + 13, y - 1, pw - 14, 21, Pal.alpha(Pal.YELLOW, 140))
            g.text(if (sel) ">" else "-", px + 4, y + 3, Pal.DKRED, 1, null)
            g.text(j.client, px + 16, y + 1, if (j.cosmic) Pal.PLUM else Pal.INK, 1, null)
            g.text("\$${j.pay}", px + pw - Font.width("\$${j.pay}") - 4, y + 1, Pal.DKGREEN, 1, null)
            g.text(game.city.address(j.tx, j.ty), px + 16, y + 8, Pal.DGRAY, 1, null)
            val tag = "${j.kind.label} LV${j.level}" + if (j.cosmic) " * COSMIC" else ""
            g.text(tag, px + pw - Font.width(tag) - 4, y + 8, if (j.cosmic) Pal.MAGENTA else Pal.BROWN, 1, null)
            g.text(j.problem.take((pw - 20) / 4), px + 16, y + 14, Pal.GRAY, 1, null)
            g.hotspot(px, y - 1, pw, 21) {
                save.target = if (sel) -1 else j.id
                game.audio.play(Audio.Sfx.CLICK)
            }
            y += 22
        }
        if (save.jobs.size in 1..4) g.text("TAP A JOB TO MARK IT. THE COMPASS POINTS THERE.", px + 16, g.h - 20, Pal.GRAY, 1, null)

        g.button(bx + bw - 42, 1, 40, 12, "BACK", Pal.GRAY) { back() }
    }
}
