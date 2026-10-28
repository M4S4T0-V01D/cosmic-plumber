package dev.m4s4t0.cosmicplumber

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * In-app updates from this game's GitHub releases (a public repository, so no key is needed). ./release.sh
 * publishes CosmicPlumber.apk with a latest.json beside it. The APK is checked against the published SHA-256, then
 * handed to Android's installer, which asks the user to confirm. Android itself refuses an update signed with
 * another key.
 */
object Updater {
    private const val API = "https://api.github.com/repos/"
    private const val APK = "CosmicPlumber.apk"

    data class Release(val versionCode: Long, val versionName: String, val sha256: String, val size: Long, val notes: String, val apkUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    /** Read by the game every frame; written from the worker thread. */
    @Volatile var state: State = State.Idle
        private set
    private val io = Executors.newSingleThreadExecutor()

    fun installedCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    /** Checks in the background. [quiet] keeps errors silent (the launch check). */
    fun check(ctx: Context, quiet: Boolean) {
        val app = ctx.applicationContext
        val s = state
        if (s is State.Downloading || s is State.Installing || s is State.Checking) return
        if (!quiet) state = State.Checking
        io.execute {
            state = try {
                val r = latest()
                if (r != null && r.versionCode > installedCode(app)) State.Available(r) else State.UpToDate
            } catch (e: Exception) {
                if (quiet) State.Idle else State.Failed("COULDN'T REACH GITHUB (${e.javaClass.simpleName})")
            }
        }
    }

    private fun open(url: String, accept: String = "application/vnd.github+json"): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        useCaches = false
        instanceFollowRedirects = true
        setRequestProperty("Accept", accept)
        setRequestProperty("User-Agent", "CosmicPlumber/${BuildConfig.VERSION_NAME}")
    }

    private fun readJson(url: String): JSONObject? {
        val c = open(url)
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    /** The latest release's manifest and APK address, or null if nothing is published yet. */
    private fun latest(): Release? {
        val rel = readJson(API + BuildConfig.UPDATE_REPO + "/releases/latest") ?: return null
        val assets = rel.getJSONArray("assets")
        var meta: String? = null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            when (a.getString("name")) {
                "latest.json" -> meta = a.getString("browser_download_url")
                APK -> apk = a.getString("browser_download_url")
            }
        }
        if (meta == null || apk == null) return null
        val j = readJson(meta) ?: return null
        return Release(j.optLong("versionCode"), j.optString("versionName", "?"), j.optString("sha256"), j.optLong("size"), j.optString("notes"), apk)
    }

    /** Downloads, verifies and installs [r]. May first send the user to allow installs from this app. */
    fun install(ctx: Context, r: Release) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            state = State.Failed("ALLOW INSTALLS FOR COSMIC PLUMBER, THEN TAP UPDATE AGAIN.", r)
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val app = ctx.applicationContext
        state = State.Downloading(r, 0f)
        io.execute {
            try {
                val file = download(app, r)
                state = State.Installing(r)
                commit(app, file)
            } catch (e: Exception) {
                state = State.Failed((e.message ?: "THE UPDATE FAILED").uppercase(), r)
            }
        }
    }

    private fun download(ctx: Context, r: Release): File {
        val out = File(ctx.cacheDir, "update-${r.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val c = open(r.apkUrl, accept = "application/octet-stream")
        try {
            if (c.responseCode != 200) throw IllegalStateException("The update server answered ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            var done = 0L
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) state = State.Downloading(r, done.toFloat() / total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sha.equals(r.sha256, ignoreCase = true)) {
            out.delete()
            throw IllegalStateException("The download was damaged; try again")
        }
        return out
    }

    private fun commit(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("cosmic-plumber.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // The installer fills in the status extras, so the PendingIntent must be mutable.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(PendingIntent.getBroadcast(ctx, id, Intent(ctx, UpdateReceiver::class.java), flags).intentSender)
        }
        apk.delete()
    }

    /** The installer's answer, forwarded by [UpdateReceiver]. */
    internal fun onInstallStatus(ctx: Context, intent: Intent) {
        val r = (state as? State.Installing)?.release
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts the game on the new version.
            PackageInstaller.STATUS_FAILURE_ABORTED -> state = if (r != null) State.Available(r) else State.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The update failed"
                state = State.Failed("NOT INSTALLED: ${msg.uppercase()}", r)
            }
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallStatus(context, intent)
}
