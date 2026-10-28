package dev.m4s4t0.cosmicplumber

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything that survives closing the app. */
class Save {
    var money = 60
    var day = 1
    /** Minutes since midnight of [day]. */
    var minutes = 7 * 60f
    var fuel = 100f
    val jobs = ArrayList<Job>()
    var target = -1
    var pager = 0
    var pagerClock = 0f
    var nextId = 1
    val levels = HashMap<String, Int>()
    var paint = "classic"
    val paints = HashSet<String>().apply { add("classic") }
    var jobsDone = 0
    var earned = 0
    var sound = true
    var music = true
    var vibrate = true
    var vanX = -1f
    var vanY = -1f
    var vanAngle = 0f
    var seenIntro = false

    fun level(id: String) = levels[id] ?: 0

    /** A fresh career that keeps the sound settings. */
    fun resetProgress() {
        val fresh = Save()
        money = fresh.money; day = fresh.day; minutes = fresh.minutes; fuel = fresh.fuel
        jobs.clear(); target = -1; pager = 0; pagerClock = 0f; nextId = 1
        levels.clear(); paint = fresh.paint; paints.clear(); paints += fresh.paints
        jobsDone = 0; earned = 0; vanX = -1f; vanY = -1f; vanAngle = 0f; seenIntro = false
    }
    val tankSize get() = 100f + 50f * level("tank")
    val hour get() = (minutes / 60f) % 24f

    /** 0 at midday, 1 deep at night, with dawn and dusk in between. */
    val darkness: Float
        get() {
            val h = hour
            return when {
                h < 5f -> 1f
                h < 7.5f -> 1f - (h - 5f) / 2.5f
                h < 18f -> 0f
                h < 21f -> (h - 18f) / 3f
                else -> 1f
            }
        }
    val night get() = hour >= 20f || hour < 6f

    fun clock(): String {
        val h = hour.toInt()
        val m = (minutes % 60f).toInt()
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }

    /** Day 1 is Monday, March 3, 1986. */
    fun date(): String {
        val names = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
        val months = listOf("JAN" to 31, "FEB" to 28, "MAR" to 31, "APR" to 30, "MAY" to 31, "JUN" to 30, "JUL" to 31,
            "AUG" to 31, "SEP" to 30, "OCT" to 31, "NOV" to 30, "DEC" to 31)
        var d = day + 2 + 31 + 28 - 1 // day of year, 0-based, from Mar 3
        var year = 1986
        while (d >= 365) { d -= 365; year++ }
        var m = 0
        while (d >= months[m].second) { d -= months[m].second; m++ }
        return "${names[(day - 1) % 7]} ${months[m].first} ${d + 1} $year"
    }

    fun toJson(): String = JSONObject().apply {
        put("money", money); put("day", day); put("minutes", minutes.toDouble()); put("fuel", fuel.toDouble())
        put("target", target); put("pager", pager); put("pagerClock", pagerClock.toDouble()); put("nextId", nextId)
        put("paint", paint); put("paints", JSONArray(paints.toList())); put("jobsDone", jobsDone); put("earned", earned)
        put("sound", sound); put("music", music); put("vibrate", vibrate); put("seenIntro", seenIntro)
        put("vanX", vanX.toDouble()); put("vanY", vanY.toDouble()); put("vanAngle", vanAngle.toDouble())
        put("levels", JSONObject().apply { levels.forEach { (k, v) -> put(k, v) } })
        put("jobs", JSONArray().apply {
            jobs.forEach { j ->
                put(JSONObject().apply {
                    put("id", j.id); put("client", j.client); put("problem", j.problem); put("quote", j.quote)
                    put("tx", j.tx); put("ty", j.ty); put("kind", j.kind.name); put("level", j.level); put("pay", j.pay)
                    put("cosmic", j.cosmic)
                })
            }
        })
    }.toString()

    companion object {
        private const val KEY = "save"

        fun load(ctx: Context): Save {
            val s = Save()
            val raw = ctx.getSharedPreferences("game", Context.MODE_PRIVATE).getString(KEY, null) ?: return s
            runCatching {
                val o = JSONObject(raw)
                s.money = o.optInt("money", s.money); s.day = o.optInt("day", 1)
                s.minutes = o.optDouble("minutes", s.minutes.toDouble()).toFloat()
                s.fuel = o.optDouble("fuel", 100.0).toFloat(); s.target = o.optInt("target", -1)
                s.pager = o.optInt("pager"); s.pagerClock = o.optDouble("pagerClock").toFloat(); s.nextId = o.optInt("nextId", 1)
                s.paint = o.optString("paint", "classic"); s.jobsDone = o.optInt("jobsDone"); s.earned = o.optInt("earned")
                s.sound = o.optBoolean("sound", true); s.music = o.optBoolean("music", true); s.vibrate = o.optBoolean("vibrate", true)
                s.seenIntro = o.optBoolean("seenIntro")
                s.vanX = o.optDouble("vanX", -1.0).toFloat(); s.vanY = o.optDouble("vanY", -1.0).toFloat()
                s.vanAngle = o.optDouble("vanAngle").toFloat()
                o.optJSONArray("paints")?.let { a -> for (i in 0 until a.length()) s.paints += a.getString(i) }
                o.optJSONObject("levels")?.let { l -> l.keys().forEach { k -> s.levels[k] = l.getInt(k) } }
                o.optJSONArray("jobs")?.let { a ->
                    for (i in 0 until a.length()) {
                        val j = a.getJSONObject(i)
                        s.jobs += Job(j.getInt("id"), j.getString("client"), j.getString("problem"), j.getString("quote"),
                            j.getInt("tx"), j.getInt("ty"), Kind.valueOf(j.getString("kind")), j.getInt("level"),
                            j.getInt("pay"), j.getBoolean("cosmic"))
                    }
                }
            }
            return s
        }

        fun store(ctx: Context, s: Save) {
            ctx.getSharedPreferences("game", Context.MODE_PRIVATE).edit().putString(KEY, s.toJson()).apply()
        }

        fun wipe(ctx: Context) {
            ctx.getSharedPreferences("game", Context.MODE_PRIVATE).edit().remove(KEY).apply()
        }
    }
}
