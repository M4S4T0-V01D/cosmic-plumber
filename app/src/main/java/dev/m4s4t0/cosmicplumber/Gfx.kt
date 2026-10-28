package dev.m4s4t0.cosmicplumber

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/** The 1986 palette: warm sodium-lamp oranges, Lake Michigan blues, cosmic goo green. */
object Pal {
    const val INK = 0xFF0E0A1F.toInt()
    const val NIGHT = 0xFF1A1033.toInt()
    const val PURPLE = 0xFF3B1F5C.toInt()
    const val PLUM = 0xFF6B2E6E.toInt()
    const val MAGENTA = 0xFFC2367A.toInt()
    const val PINK = 0xFFFF6FA8.toInt()
    const val RED = 0xFFE04040.toInt()
    const val DKRED = 0xFF8A2030.toInt()
    const val ORANGE = 0xFFFF7A3D.toInt()
    const val AMBER = 0xFFFFB347.toInt()
    const val YELLOW = 0xFFFFE26B.toInt()
    const val CREAM = 0xFFFFF4D6.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val LGRAY = 0xFFC0C4D0.toInt()
    const val GRAY = 0xFF7A8090.toInt()
    const val DGRAY = 0xFF454A5A.toInt()
    const val ASPHALT = 0xFF33303F.toInt()
    const val BROWN = 0xFF7A4A2E.toInt()
    const val DKBROWN = 0xFF4A2C1E.toInt()
    const val TAN = 0xFFC79A6A.toInt()
    const val BRICK = 0xFFA4483A.toInt()
    const val COPPER = 0xFFD9824A.toInt()
    const val DKCOPPER = 0xFF8C4A2A.toInt()
    const val GREEN = 0xFF3DFF9A.toInt()
    const val DKGREEN = 0xFF1D7A5A.toInt()
    const val GRASS = 0xFF4E8A3A.toInt()
    const val DKGRASS = 0xFF365F2C.toInt()
    const val TEAL = 0xFF2EC4B6.toInt()
    const val BLUE = 0xFF3A6EE0.toInt()
    const val LAKE = 0xFF2A5490.toInt()
    const val DKLAKE = 0xFF1C3766.toInt()
    const val SKY = 0xFF7FC8FF.toInt()
    const val CYAN = 0xFF8AF0FF.toInt()

    fun alpha(c: Int, a: Int) = (c and 0xFFFFFF) or (a.coerceIn(0, 255) shl 24)
    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 255) * (1 - u) + ((b shr s) and 255) * u).toInt() shl s
        return ch(24) or ch(16) or ch(8) or ch(0)
    }
}

/** Pixel art from strings: each char maps to a palette color, '.' is clear. Baked to a Bitmap once. */
class Sprite(rows: List<String>, colors: Map<Char, Int>) {
    val w = rows.maxOf { it.length }
    val h = rows.size
    val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
        for (y in rows.indices) for (x in rows[y].indices) colors[rows[y][x]]?.let { b.setPixel(x, y, it) }
    }

    /** The same art with every opaque pixel recolored, used for van paint jobs. */
    fun recolor(from: Int, to: Int): Bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true).also { b ->
        for (y in 0 until h) for (x in 0 until w) if (b.getPixel(x, y) == from) b.setPixel(x, y, to)
    }
}

/** A tiny 3x5 pixel font. Text is drawn uppercase. */
object Font {
    private val glyphs = HashMap<Char, Array<String>>()

    init {
        val src = mapOf(
            'A' to ".#. #.# ### #.# #.#", 'B' to "##. #.# ##. #.# ##.", 'C' to ".## #.. #.. #.. .##",
            'D' to "##. #.# #.# #.# ##.", 'E' to "### #.. ##. #.. ###", 'F' to "### #.. ##. #.. #..",
            'G' to ".## #.. #.# #.# .##", 'H' to "#.# #.# ### #.# #.#", 'I' to "### .#. .#. .#. ###",
            'J' to "..# ..# ..# #.# .#.", 'K' to "#.# #.# ##. #.# #.#", 'L' to "#.. #.. #.. #.. ###",
            'M' to "#.# ### ### #.# #.#", 'N' to "##. #.# #.# #.# #.#", 'O' to ".#. #.# #.# #.# .#.",
            'P' to "##. #.# ##. #.. #..", 'Q' to ".#. #.# #.# ##. .##", 'R' to "##. #.# ##. #.# #.#",
            'S' to ".## #.. .#. ..# ##.", 'T' to "### .#. .#. .#. .#.", 'U' to "#.# #.# #.# #.# ###",
            'V' to "#.# #.# #.# #.# .#.", 'W' to "#.# #.# ### ### #.#", 'X' to "#.# #.# .#. #.# #.#",
            'Y' to "#.# #.# .#. .#. .#.", 'Z' to "### ..# .#. #.. ###",
            '0' to "### #.# #.# #.# ###", '1' to ".#. ##. .#. .#. ###", '2' to "##. ..# .#. #.. ###",
            '3' to "##. ..# .#. ..# ##.", '4' to "#.# #.# ### ..# ..#", '5' to "### #.. ##. ..# ##.",
            '6' to ".## #.. ### #.# ###", '7' to "### ..# .#. .#. .#.", '8' to "### #.# ### #.# ###",
            '9' to "### #.# ### ..# ##.",
            '.' to "... ... ... ... .#.", ',' to "... ... ... .#. #..", '!' to ".#. .#. .#. ... .#.",
            '?' to "##. ..# .#. ... .#.", ':' to "... .#. ... .#. ...", '-' to "... ... ### ... ...",
            '+' to "... .#. ### .#. ...", '$' to ".## ##. .#. .## ##.", '\'' to ".#. .#. ... ... ...",
            '"' to "#.# #.# ... ... ...", '/' to "..# ..# .#. #.. #..", '(' to ".#. #.. #.. #.. .#.",
            ')' to ".#. ..# ..# ..# .#.", '%' to "#.# ..# .#. #.. #.#", '&' to ".#. #.# .#. #.# .##",
            '#' to "#.# ### #.# ### #.#", '*' to "... #.# .#. #.# ...", '=' to "... ### ... ### ...",
            '<' to "..# .#. #.. .#. ..#", '>' to "#.. .#. ..# .#. #..", '_' to "... ... ... ... ###",
            '@' to ".#. #.# ### #.. .##", '|' to ".#. .#. .#. .#. .#.", '^' to ".#. #.# ... ... ...",
        )
        for ((c, s) in src) glyphs[c] = s.split(' ').toTypedArray()
    }

    fun width(s: String, scale: Int = 1) = if (s.isEmpty()) 0 else (s.length * 4 - 1) * scale

    fun draw(g: Gfx, s: String, x: Int, y: Int, color: Int, scale: Int = 1) {
        var cx = x
        for (ch in s.uppercase()) {
            val gl = glyphs[ch]
            if (gl != null) for (r in 0 until 5) for (c in 0 until 3) if (gl[r][c] == '#') g.rect(cx + c * scale, y + r * scale, scale, scale, color)
            cx += 4 * scale
        }
    }
}

/** Drawing on the low-resolution game canvas, plus immediate-mode buttons registered while drawing. */
class Gfx {
    lateinit var c: Canvas
    var w = 320
    var h = 180
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val src = Rect()
    private val dst = Rect()

    class Button(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit)
    val buttons = ArrayList<Button>()

    fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) {
        p.color = color
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }

    fun frame(x: Int, y: Int, w: Int, h: Int, color: Int) {
        rect(x, y, w, 1, color); rect(x, y + h - 1, w, 1, color)
        rect(x, y, 1, h, color); rect(x + w - 1, y, 1, h, color)
    }

    fun circle(x: Float, y: Float, r: Float, color: Int) {
        p.color = color
        c.drawCircle(x, y, r, p)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float = 1f) {
        p.color = color
        p.strokeWidth = width
        c.drawLine(x1, y1, x2, y2, p)
    }

    fun bmp(b: Bitmap, x: Int, y: Int, alpha: Int = 255) {
        p.alpha = alpha
        c.drawBitmap(b, x.toFloat(), y.toFloat(), p)
        p.alpha = 255
    }

    fun bmpScaled(b: Bitmap, x: Int, y: Int, scale: Int) {
        p.alpha = 255
        src.set(0, 0, b.width, b.height)
        dst.set(x, y, x + b.width * scale, y + b.height * scale)
        c.drawBitmap(b, src, dst, p)
    }

    fun sprite(s: Sprite, x: Int, y: Int, flip: Boolean = false) {
        if (!flip) return bmp(s.bmp, x, y)
        c.save()
        c.scale(-1f, 1f, x + s.w / 2f, 0f)
        bmp(s.bmp, x, y)
        c.restore()
    }

    /** A bitmap rotated about its center, drawn centered on (cx, cy). */
    fun rotated(b: Bitmap, cx: Float, cy: Float, deg: Float) {
        c.save()
        c.translate(cx, cy)
        c.rotate(deg)
        p.alpha = 255
        c.drawBitmap(b, -b.width / 2f, -b.height / 2f, p)
        c.restore()
    }

    fun text(s: String, x: Int, y: Int, color: Int, scale: Int = 1, shadow: Int? = Pal.INK) {
        if (shadow != null) Font.draw(this, s, x + scale, y + scale, shadow, scale)
        Font.draw(this, s, x, y, color, scale)
    }

    fun textC(s: String, cx: Int, y: Int, color: Int, scale: Int = 1, shadow: Int? = Pal.INK) =
        text(s, cx - Font.width(s, scale) / 2, y, color, scale, shadow)

    /** Word-wrapped text; returns the height used. */
    fun para(s: String, x: Int, y: Int, maxW: Int, color: Int, shadow: Int? = null): Int {
        val perLine = (maxW + 1) / 4
        var line = ""
        var yy = y
        for (word in s.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (next.length > perLine && line.isNotEmpty()) {
                text(line, x, yy, color, 1, shadow); yy += 7; line = word
            } else line = next
        }
        if (line.isNotEmpty()) { text(line, x, yy, color, 1, shadow); yy += 7 }
        return yy - y
    }

    /** A chunky 80s button; [action] runs when it's tapped. */
    fun button(x: Int, y: Int, w: Int, h: Int, label: String, base: Int = Pal.ORANGE, enabled: Boolean = true, action: () -> Unit) {
        val face = if (enabled) base else Pal.DGRAY
        rect(x, y + 2, w, h, Pal.INK)
        rect(x, y, w, h, face)
        rect(x + 1, y + 1, w - 2, 1, Pal.mix(face, Pal.WHITE, 0.4f))
        rect(x + 1, y + h - 2, w - 2, 1, Pal.mix(face, Pal.INK, 0.35f))
        frame(x, y, w, h, Pal.INK)
        textC(label, x + w / 2, y + (h - 5) / 2, if (enabled) Pal.CREAM else Pal.GRAY)
        if (enabled) buttons += Button(x, y, w, h, action)
    }

    /** A tappable area with no drawing of its own. */
    fun hotspot(x: Int, y: Int, w: Int, h: Int, action: () -> Unit) {
        buttons += Button(x, y, w, h, action)
    }

    fun panel(x: Int, y: Int, w: Int, h: Int, fill: Int = Pal.NIGHT, edge: Int = Pal.AMBER) {
        rect(x + 2, y + 2, w, h, Pal.alpha(Pal.INK, 160))
        rect(x, y, w, h, fill)
        frame(x, y, w, h, edge)
        frame(x + 1, y + 1, w - 2, h - 2, Pal.alpha(edge, 90))
    }
}
