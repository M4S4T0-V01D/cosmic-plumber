package dev.m4s4t0.cosmicplumber

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.ConcurrentLinkedQueue

abstract class Scene(val game: Game) {
    open fun enter() {}
    open fun leave() {}
    open fun update(dt: Float) {}
    abstract fun draw(g: Gfx)
    /** Touches the buttons didn't take, in game pixels. */
    open fun down(id: Int, x: Float, y: Float) {}
    open fun move(id: Int, x: Float, y: Float) {}
    open fun up(id: Int, x: Float, y: Float) {}
    /** Android's back gesture. */
    open fun back() = game.go(HqScene(game))
}

/** The hub every scene reaches: save, audio, city, scene switching, toasts and haptics. */
class Game(val activity: Activity) {
    val save = Save.load(activity)
    val audio = Audio(activity, save)
    val city by lazy { City() }
    val g = Gfx()
    var scene: Scene = TitleScene(this)
        private set
    var time = 0f
        private set
    private var fade = 1f
    private var toastText = ""
    private var toastTime = 0f
    private val vibrator = activity.getSystemService(Vibrator::class.java)
    /** Soundtracks are composed once, off the render thread. */
    @Volatile var radio: Song? = null

    /** The city's pre-drawn bitmaps; built on a worker thread at launch. */
    val art by lazy { CityArt(city) }
    /** One street scene for the whole session, so traffic and the van survive trips to the clipboard. */
    val drive by lazy { DriveScene(this) }

    init {
        Thread { radio = Composer.compose(104, 16, 1986, 0, 0f) }.start()
        Thread { art }.start()
    }

    /** Keeps WGN-ish synth radio playing in the hub scenes. */
    fun playRadio() {
        val r = radio ?: return
        if (audio.current !== r || !audio.musicPlaying()) audio.music(r, loop = true)
    }

    fun start() {
        scene.enter()
    }

    fun go(next: Scene) {
        scene.leave()
        scene = next
        fade = 1f
        next.enter()
        persist()
    }

    fun persist() = Save.store(activity, save)

    fun toast(s: String) {
        toastText = s
        toastTime = 2.8f
    }

    fun buzz(ms: Long = 15) {
        if (!save.vibrate) return
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, if (ms > 30) 200 else 90)) }
    }

    fun earn(amount: Int) {
        save.money += amount
        if (amount > 0) save.earned += amount
    }

    fun update(dt: Float) {
        time += dt
        if (fade > 0) fade = (fade - dt * 3f).coerceAtLeast(0f)
        if (toastTime > 0) toastTime -= dt
        scene.update(dt)
    }

    fun draw() {
        g.buttons.clear()
        scene.draw(g)
        if (toastTime > 0) {
            val w = Font.width(toastText) + 12
            val y = if (toastTime > 2.6f) -14 + ((2.8f - toastTime) / 0.2f * 18).toInt() else 4
            g.panel(g.w / 2 - w / 2, y, w, 13, Pal.INK, Pal.YELLOW)
            g.textC(toastText, g.w / 2, y + 4, Pal.YELLOW)
        }
        if (fade > 0) g.rect(0, 0, g.w, g.h, Pal.alpha(Pal.INK, (fade * 255).toInt()))
    }

    // Touch arrives on the UI thread; it's queued and handled on the game thread.
    class Touch(val action: Int, val id: Int, val x: Float, val y: Float)
    val touches = ConcurrentLinkedQueue<Touch>()
    private val held = HashMap<Int, Gfx.Button?>()

    fun handleTouches() {
        while (true) {
            val t = touches.poll() ?: break
            when (t.action) {
                MotionEvent.ACTION_DOWN -> {
                    val b = g.buttons.lastOrNull { t.x >= it.x && t.x < it.x + it.w && t.y >= it.y && t.y < it.y + it.h }
                    held[t.id] = b
                    if (b != null) {
                        audio.play(Audio.Sfx.CLICK, 0.6f)
                        buzz(8)
                        b.action()
                    } else scene.down(t.id, t.x, t.y)
                }
                MotionEvent.ACTION_MOVE -> if (held[t.id] == null) scene.move(t.id, t.x, t.y)
                MotionEvent.ACTION_UP -> {
                    if (held.remove(t.id) == null) scene.up(t.id, t.x, t.y)
                }
                -1 -> scene.back()
            }
        }
    }
}

/** Runs the game on its own thread and scales the low-res frame up to the screen with crisp pixels. */
class GameView(private val activity: Activity) : SurfaceView(activity), SurfaceHolder.Callback {
    val game = Game(activity)
    @Volatile private var running = false
    private var thread: Thread? = null
    private var frame: Bitmap? = null
    private var scale = 1f
    private var offX = 0f
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    init {
        holder.addCallback(this)
        game.start()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val h = 180
        val w = (h * width.toFloat() / height).toInt().coerceIn(240, 420)
        if (frame?.width != w) synchronized(game) {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            game.g.c = Canvas(b)
            game.g.w = w
            game.g.h = h
            frame = b
        }
        scale = height / h.toFloat()
        offX = (width - w * scale) / 2f
        resume()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = pause()

    fun resume() {
        if (running || frame == null) return
        running = true
        thread = Thread(::loop, "game").apply { start() }
    }

    fun pause() {
        running = false
        thread?.join(500)
        thread = null
    }

    private fun loop() {
        var last = System.nanoTime()
        val dst = Rect()
        val src = Rect()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
            last = now
            val bmp = frame ?: break
            synchronized(game) {
                game.handleTouches()
                game.update(dt)
                game.draw()
            }
            val c = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas() ?: continue
            try {
                c.drawColor(Pal.INK)
                src.set(0, 0, bmp.width, bmp.height)
                dst.set(offX.toInt(), 0, (offX + bmp.width * scale).toInt(), c.height)
                c.drawBitmap(bmp, src, dst, paint)
            } finally {
                holder.unlockCanvasAndPost(c)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        fun gx(i: Int) = (e.getX(i) - offX) / scale
        fun gy(i: Int) = e.getY(i) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                game.touches += Game.Touch(MotionEvent.ACTION_DOWN, e.getPointerId(i), gx(i), gy(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) game.touches += Game.Touch(MotionEvent.ACTION_MOVE, e.getPointerId(i), gx(i), gy(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = e.actionIndex
                game.touches += Game.Touch(MotionEvent.ACTION_UP, e.getPointerId(i), gx(i), gy(i))
            }
        }
        return true
    }

    fun back() {
        game.touches += Game.Touch(-1, 0, 0f, 0f)
    }
}
