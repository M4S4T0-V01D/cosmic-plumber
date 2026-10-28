package dev.m4s4t0.cosmicplumber

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.window.OnBackInvokedDispatcher

class MainActivity : Activity() {
    private lateinit var view: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = GameView(this)
        setContentView(view)
        // Debug builds only: `am start -n ... --es job pipes|wrench` opens the first call as that minigame, for testing.
        if (BuildConfig.DEBUG) intent.getStringExtra("job")?.let { kind ->
            val game = view.game
            if (game.save.jobs.isEmpty()) game.newCalls(3)
            game.save.jobs.firstOrNull()?.let { j ->
                game.go(JobIntroScene(game, j.copy(kind = if (kind == "wrench") Kind.WRENCH else Kind.PIPES)))
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { view.back() }
        }
        Updater.check(this, quiet = true)
    }

    @Deprecated("Android 12 and older")
    override fun onBackPressed() = view.back()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        view.game.audio.resume()
        view.resume()
    }

    override fun onPause() {
        view.pause()
        synchronized(view.game) { view.game.persist() }
        view.game.audio.pause()
        super.onPause()
    }

    override fun onDestroy() {
        view.game.audio.release()
        super.onDestroy()
    }
}
