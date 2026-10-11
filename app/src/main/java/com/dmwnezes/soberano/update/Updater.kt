package com.dmwnezes.soberano.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.dmwnezes.soberano.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Uma versão publicada nas Releases do GitHub. */
data class Release(val tag: String, val number: Int, val notes: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Atualização dentro do app: busca a versão mais nova no GitHub, baixa o APK e instala
 * por uma sessão do PackageInstaller (do Android 12 em diante, depois da primeira vez, entra sem pedir).
 */
object Updater {
    private const val API = "https://api.github.com/repos/dmwnezes/soberano/releases/latest"
    private const val TAG = "Updater"

    val currentNumber: Int get() = BuildConfig.VERSION_CODE
    val currentName: String get() = BuildConfig.VERSION_NAME

    /** "v0.1.12" → 12 (o mesmo número do versionCode gerado no GitHub). */
    fun numberFromTag(tag: String): Int = tag.substringAfterLast('.').filter(Char::isDigit).toIntOrNull() ?: 0

    fun isNewer(r: Release) = r.number > currentNumber

    fun cleanNotes(body: String): String = body.lines()
        .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
        .joinToString("\n").trim()

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Soberano-app")
            connectTimeout = 10_000; readTimeout = 15_000
        }
        try {
            if (c.responseCode != 200) error("GitHub respondeu ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: error("Versão sem APK")
            val tag = o.optString("tag_name")
            Release(tag, numberFromTag(tag), cleanNotes(o.optString("body")), apk.getString("browser_download_url"), apk.optLong("size"))
        } finally { c.disconnect() }
    }

    suspend fun download(context: Context, r: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Soberano-${r.tag}.apk")
        var url = r.apkUrl
        // o GitHub redireciona para outro domínio
        var conn: HttpURLConnection
        var hops = 0
        while (true) {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Soberano-app")
                connectTimeout = 15_000; readTimeout = 60_000
            }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = conn.getHeaderField("Location"); conn.disconnect(); continue }
            if (code != 200) error("Download falhou ($code)")
            break
        }
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.sizeBytes
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { conn.disconnect() }
        file
    }

    /** O Android pede uma vez que a pessoa permita o Soberano instalar atualizações. */
    fun canInstall(context: Context): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, apk: File) {
        if (!canInstall(context)) { openInstallPermission(context); return }
        runCatching { installSession(context, apk) }.onFailure {
            Log.w(TAG, "sessão falhou, abrindo o instalador", it)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", apk)
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun installSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("Soberano.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, 7300, Intent(context, InstallReceiver::class.java), flags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }
}

/** Resultado da instalação: se o Android pedir confirmação, mostra a tela dele. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("Updater", "atualizado")
            else -> Log.w("Updater", "instalação falhou: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
