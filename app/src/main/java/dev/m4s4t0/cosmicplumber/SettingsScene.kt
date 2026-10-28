package dev.m4s4t0.cosmicplumber

/** Sound, haptics, the GitHub updater and starting over. */
class SettingsScene(game: Game, private val from: Scene) : Scene(game) {
    private var confirmReset = 0f
    private var t = 0f

    override fun enter() {
        if (Updater.state is Updater.State.Idle) Updater.check(game.activity, quiet = false)
    }

    override fun update(dt: Float) {
        t += dt
        if (confirmReset > 0) confirmReset -= dt
    }

    override fun back() = game.go(from)

    override fun draw(g: Gfx) {
        val save = game.save
        g.rect(0, 0, g.w, g.h, Pal.NIGHT)
        for (y in 0 until g.h step 4) g.rect(0, y, g.w, 1, Pal.alpha(Pal.INK, 90))
        g.text("SETTINGS", 8, 6, Pal.AMBER, 2)
        g.button(g.w - 46, 5, 40, 14, "BACK", Pal.GRAY) { back() }

        val colW = (g.w - 24) / 2
        // Left column: toggles.
        var y = 30
        fun toggle(label: String, on: Boolean, flip: () -> Unit) {
            g.panel(8, y, colW, 20, Pal.INK, Pal.PLUM)
            g.text(label, 14, y + 8, Pal.CREAM)
            g.button(8 + colW - 40, y + 3, 34, 14, if (on) "ON" else "OFF", if (on) Pal.DKGREEN else Pal.DGRAY) { flip() }
            y += 24
        }
        toggle("SOUND EFFECTS", save.sound) { save.sound = !save.sound }
        toggle("RADIO MUSIC", save.music) {
            save.music = !save.music
            game.audio.refreshVolume()
            if (save.music) game.playRadio() else game.audio.stopMusic()
        }
        toggle("VIBRATION", save.vibrate) { save.vibrate = !save.vibrate; game.buzz(40) }

        g.panel(8, y, colW, 34, Pal.INK, Pal.DKRED)
        g.text("START OVER", 14, y + 5, Pal.PINK)
        g.text("WIPES MONEY, UPGRADES, JOBS.", 14, y + 13, Pal.GRAY, 1, null)
        g.button(8 + colW - 64, y + 18, 58, 13, if (confirmReset > 0) "SURE?" else "RESET", if (confirmReset > 0) Pal.RED else Pal.DKRED) {
            if (confirmReset > 0) {
                save.resetProgress()
                game.newCalls(3)
                game.drive.placeFromSave()
                game.persist()
                game.toast("FRESH START. WELCOME TO GALACTIC DRAIN, ROOKIE.")
                game.go(TitleScene(game))
            } else confirmReset = 3f
        }
        y += 40
        g.text("CAREER: ${save.jobsDone} JOBS, \$${save.earned} EARNED", 10, y, Pal.LGRAY, 1, null)

        // Right column: the updater.
        val x = 16 + colW
        g.panel(x, 30, colW, 138, Pal.INK, Pal.TEAL)
        g.text("UPDATES", x + 6, 36, Pal.TEAL)
        g.text("V${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", x + 6, 46, Pal.CREAM)
        g.text("FROM GITHUB.COM/${BuildConfig.UPDATE_REPO.uppercase()}", x + 6, 54, Pal.GRAY, 1, null)
        val bx = x + 6
        val bw = colW - 12
        val by = 30 + 138 - 22
        when (val s = Updater.state) {
            Updater.State.Idle, Updater.State.UpToDate -> {
                if (s == Updater.State.UpToDate) g.para("YOU'RE ON THE LATEST VERSION. TOTALLY RADICAL.", bx, 66, bw, Pal.GREEN)
                g.button(bx, by, bw, 16, "CHECK FOR UPDATES", Pal.BLUE) { Updater.check(game.activity, quiet = false) }
            }
            Updater.State.Checking -> {
                g.text("DIALING GITHUB" + ".".repeat((t * 3).toInt() % 4), bx, 66, Pal.AMBER)
                modem(g, bx, 78, bw)
            }
            is Updater.State.Available -> {
                g.text("NEW: V${s.release.versionName}", bx, 66, Pal.GREEN)
                g.text("%.1f MB".format(s.release.size / 1_048_576f), bx + bw - 34, 66, Pal.GRAY, 1, null)
                g.para(s.release.notes.uppercase().replace('\n', ' ').take(160), bx, 76, bw, Pal.LGRAY)
                g.button(bx, by, bw, 16, "DOWNLOAD & INSTALL", Pal.DKGREEN) { Updater.install(game.activity, s.release) }
            }
            is Updater.State.Downloading -> {
                g.text("DOWNLOADING V${s.release.versionName}", bx, 66, Pal.AMBER)
                g.rect(bx, 78, bw, 8, Pal.DGRAY)
                g.rect(bx, 78, (bw * s.progress).toInt(), 8, Pal.GREEN)
                g.frame(bx, 78, bw, 8, Pal.INK)
                g.text("${(s.progress * 100).toInt()}%", bx, 90, Pal.CREAM)
            }
            is Updater.State.Installing -> {
                g.para("CONFIRM THE INSTALL IN THE ANDROID PROMPT. THE GAME RESTARTS AFTER.", bx, 66, bw, Pal.AMBER)
            }
            is Updater.State.Failed -> {
                g.para(s.message, bx, 66, bw, Pal.PINK)
                val r = s.release
                if (r != null) g.button(bx, by, bw, 16, "TRY AGAIN", Pal.ORANGE) { Updater.install(game.activity, r) }
                else g.button(bx, by, bw, 16, "CHECK AGAIN", Pal.BLUE) { Updater.check(game.activity, quiet = false) }
            }
        }
    }

    /** A blinking 1200-baud modem, for flavor. */
    private fun modem(g: Gfx, x: Int, y: Int, w: Int) {
        g.rect(x, y, w, 12, Pal.LGRAY)
        g.frame(x, y, w, 12, Pal.INK)
        for (i in 0 until 6) {
            val on = ((t * 8).toInt() + i * 3) % 5 < 2
            g.rect(x + 6 + i * 8, y + 5, 3, 2, if (on) Pal.RED else Pal.DKRED)
        }
    }
}
