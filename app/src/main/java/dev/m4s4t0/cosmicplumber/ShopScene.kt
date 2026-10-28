package dev.m4s4t0.cosmicplumber

/** Sal's Plumbing Supply & Auto Body: upgrades for the van and the tools, and fresh paint. */
class ShopScene(game: Game, private val from: Scene) : Scene(game) {
    private var tab = 0
    private var t = 0f

    override fun update(dt: Float) { t += dt }

    override fun back() = game.go(from)

    override fun draw(g: Gfx) {
        val save = game.save
        g.rect(0, 0, g.w, g.h, Pal.NIGHT)
        for (x in 0 until g.w step 12) g.rect(x, 0, 1, g.h, Pal.alpha(Pal.PURPLE, 120))
        g.rect(0, 0, g.w, 22, Pal.DKRED)
        g.rect(0, 22, g.w, 2, Pal.AMBER)
        g.text("SAL'S PLUMBING SUPPLY & AUTO BODY", 6, 4, Pal.YELLOW)
        g.text("WE ACCEPT CASH. AND CASH.", 6, 12, Pal.PINK, 1, null)
        val cash = "\$${save.money}"
        g.text(cash, g.w - Font.width(cash, 2) - 6, 5, Pal.GREEN, 2)

        g.button(6, 28, 70, 14, "UPGRADES", if (tab == 0) Pal.ORANGE else Pal.DGRAY) { tab = 0 }
        g.button(80, 28, 70, 14, "PAINT JOB", if (tab == 1) Pal.ORANGE else Pal.DGRAY) { tab = 1 }
        g.button(g.w - 46, 28, 40, 14, "BACK", Pal.GRAY) { back() }

        if (tab == 0) upgrades(g) else paints(g)
    }

    private fun upgrades(g: Gfx) {
        val save = game.save
        val colW = (g.w - 18) / 2
        Catalog.upgrades.forEachIndexed { i, u ->
            val x = 6 + (i % 2) * (colW + 6)
            val y = 46 + (i / 2) * 44
            val lv = save.level(u.id)
            g.panel(x, y, colW, 40, Pal.INK, if (lv >= u.max) Pal.GREEN else Pal.PLUM)
            g.text(u.name, x + 4, y + 4, Pal.AMBER)
            for (k in 0 until u.max) g.rect(x + colW - 6 - (u.max - k) * 7, y + 4, 5, 5, if (k < lv) Pal.GREEN else Pal.DGRAY)
            g.para(u.blurb, x + 4, y + 12, colW - 56, Pal.LGRAY)
            if (lv >= u.max) {
                g.text("MAXED", x + colW - 30, y + 27, Pal.GREEN)
            } else {
                val price = u.prices[lv]
                val afford = save.money >= price
                g.button(x + colW - 50, y + 23, 46, 14, "\$$price", if (afford) Pal.DKGREEN else Pal.DGRAY, afford) {
                    save.money -= price
                    save.levels[u.id] = lv + 1
                    if (u.id == "tank") save.fuel += 50f
                    game.audio.play(Audio.Sfx.COIN)
                    game.buzz(30)
                    game.toast("${u.name} LV${lv + 1}. SAL SAYS PLEASURE DOING BUSINESS.")
                    game.persist()
                }
            }
        }
    }

    private fun paints(g: Gfx) {
        val save = game.save
        val n = Catalog.paints.size
        val cw = (g.w - 12) / n
        Catalog.paints.forEachIndexed { i, p ->
            val x = 6 + i * cw
            val y = 48
            val owned = p.id in save.paints
            val on = save.paint == p.id
            g.panel(x + 1, y, cw - 2, 124, Pal.INK, if (on) Pal.GREEN else Pal.PLUM)
            val van = PeopleCache.side(p)
            g.sprite(van, x + (cw - van.w) / 2, y + 14 + (kotlin.math.sin(t * 3 + i) * 1.5f).toInt())
            g.rect(x + 6, y + 30, cw - 12, 1, Pal.DGRAY)
            g.textC(p.name.substringBefore(' '), x + cw / 2, y + 40, Pal.CREAM)
            g.textC(p.name.substringAfter(' ', ""), x + cw / 2, y + 47, Pal.CREAM)
            g.rect(x + cw / 2 - 12, y + 58, 10, 10, p.color)
            g.rect(x + cw / 2 + 2, y + 58, 10, 10, p.trim)
            when {
                on -> g.textC("IN USE", x + cw / 2, y + 104, Pal.GREEN)
                owned -> g.button(x + 5, y + 100, cw - 10, 14, "USE", Pal.BLUE) {
                    save.paint = p.id
                    game.audio.play(Audio.Sfx.CLANK)
                }
                else -> {
                    val afford = save.money >= p.price
                    g.button(x + 5, y + 100, cw - 10, 14, "\$${p.price}", if (afford) Pal.DKGREEN else Pal.DGRAY, afford) {
                        save.money -= p.price
                        save.paints += p.id
                        save.paint = p.id
                        game.audio.play(Audio.Sfx.COIN)
                        game.toast("FRESH COAT OF ${p.name}. TUBULAR.")
                        game.persist()
                    }
                }
            }
        }
    }
}
