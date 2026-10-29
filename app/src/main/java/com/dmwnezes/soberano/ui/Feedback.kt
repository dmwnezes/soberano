package com.dmwnezes.soberano.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Sfx { WAR, BATTLE, VICTORY, DEFEAT, CAPTURE, EVENT, BUILD, NUKE, VOTE }

/**
 * Sons sintetizados na hora (sem arquivos de áudio) e vibração.
 * Ligar e desligar fica salvo nas preferências do app.
 */
class Feedback(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("soberano", Context.MODE_PRIVATE)
    // criar AudioTrack e vibrar são chamadas ao sistema (dezenas de ms): na thread da tela travavam o mapa durante as guerras
    private val handler get() = worker
    private var lastPlay = 0L
    private val cache = HashMap<Sfx, ShortArray>()

    companion object {
        /** uma thread só para todas as partidas */
        private val worker by lazy { Handler(HandlerThread("sfx").apply { start() }.looper) }
    }

    var sound: Boolean
        get() = prefs.getBoolean("sound", true)
        set(v) { prefs.edit().putBoolean("sound", v).apply() }
    var vibration: Boolean
        get() = prefs.getBoolean("vibration", true)
        set(v) { prefs.edit().putBoolean("vibration", v).apply() }

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }.getOrNull()

    fun play(s: Sfx) {
        val now = System.currentTimeMillis()
        if (now - lastPlay < 350) return
        lastPlay = now
        val snd = sound; val vib = vibration
        handler.post {
            if (snd) runCatching { playPcm(cache.getOrPut(s) { Synth.make(s) }) }
            if (vib) runCatching { vibrate(s) }
        }
    }

    private fun playPcm(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        handler.postDelayed({ runCatching { track.stop(); track.release() } }, pcm.size * 1000L / Synth.RATE + 200)
    }

    private fun vibrate(s: Sfx) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val (timings, amps) = when (s) {
            Sfx.WAR -> longArrayOf(0, 120, 80, 120, 80, 220) to intArrayOf(0, 255, 0, 255, 0, 255)
            Sfx.BATTLE -> longArrayOf(0, 60, 40, 60) to intArrayOf(0, 180, 0, 120)
            Sfx.VICTORY -> longArrayOf(0, 40, 50, 40, 50, 90) to intArrayOf(0, 120, 0, 160, 0, 220)
            Sfx.DEFEAT -> longArrayOf(0, 260) to intArrayOf(0, 200)
            Sfx.CAPTURE -> longArrayOf(0, 50, 40, 80) to intArrayOf(0, 140, 0, 200)
            Sfx.EVENT, Sfx.VOTE -> longArrayOf(0, 30) to intArrayOf(0, 110)
            Sfx.BUILD -> longArrayOf(0, 20) to intArrayOf(0, 90)
            Sfx.NUKE -> longArrayOf(0, 600) to intArrayOf(0, 255)
        }
        v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
    }
}

/** Gera as ondas sonoras. */
object Synth {
    const val RATE = 22050

    private fun buffer(sec: Double) = DoubleArray((RATE * sec).toInt())

    private fun tone(buf: DoubleArray, start: Double, dur: Double, freq: Double, vol: Double, decay: Double = 6.0, tri: Boolean = false) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toDouble() / RATE
            val ph = 2 * PI * freq * t
            val w = if (tri) (2 / PI) * kotlin.math.asin(sin(ph)) else sin(ph) + 0.25 * sin(2 * ph)
            val env = exp(-decay * t) * (if (i < 200) i / 200.0 else 1.0)
            buf[idx] += w * vol * env
        }
    }

    private var seed = 12345L
    private fun noise(): Double { seed = seed * 6364136223846793005L + 1442695040888963407L; return ((seed ushr 33).toDouble() / (1L shl 31)) - 1.0 }

    private fun drum(buf: DoubleArray, start: Double, vol: Double, freq: Double = 70.0) {
        val s0 = (start * RATE).toInt()
        val n = (0.35 * RATE).toInt()
        var lp = 0.0
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx >= buf.size) break
            val t = i.toDouble() / RATE
            val f = freq * (1 + 1.5 * exp(-30 * t))
            lp += (noise() - lp) * 0.08
            buf[idx] += (sin(2 * PI * f * t) * 0.9 + lp * 0.6) * vol * exp(-9 * t)
        }
    }

    private fun rumble(buf: DoubleArray, vol: Double) {
        var lp = 0.0; var lp2 = 0.0
        for (i in buf.indices) {
            val t = i.toDouble() / RATE
            lp += (noise() - lp) * 0.02
            lp2 += (lp - lp2) * 0.05
            buf[i] += (lp2 * 6 + sin(2 * PI * 40 * t) * 0.4) * vol * exp(-2.2 * t) * (if (i < 400) i / 400.0 else 1.0)
        }
    }

    private fun toPcm(buf: DoubleArray): ShortArray {
        val peak = buf.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
        val k = 0.38 / peak * Short.MAX_VALUE
        return ShortArray(buf.size) { (buf[it] * k).toInt().coerceIn(-32767, 32767).toShort() }
    }

    fun make(s: Sfx): ShortArray {
        val b: DoubleArray
        when (s) {
            Sfx.WAR -> { b = buffer(1.0); drum(b, 0.0, 1.0); drum(b, 0.2, 0.9); drum(b, 0.4, 1.0, 55.0); tone(b, 0.4, 0.6, 110.0, 0.3, 3.0) }
            Sfx.BATTLE -> { b = buffer(0.45); drum(b, 0.0, 1.0, 90.0); drum(b, 0.12, 0.6, 120.0) }
            Sfx.VICTORY -> { b = buffer(0.9); listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f -> tone(b, i * 0.11, 0.5, f, 0.5, 5.0, tri = true) } }
            Sfx.DEFEAT -> { b = buffer(0.9); listOf(392.0, 311.13, 261.63).forEachIndexed { i, f -> tone(b, i * 0.17, 0.5, f, 0.5, 4.0, tri = true) } }
            Sfx.CAPTURE -> { b = buffer(0.5); tone(b, 0.0, 0.25, 783.99, 0.5, 7.0, true); tone(b, 0.12, 0.35, 1046.5, 0.5, 6.0, true) }
            Sfx.EVENT -> { b = buffer(0.7); tone(b, 0.0, 0.7, 880.0, 0.5, 5.0); tone(b, 0.0, 0.7, 1318.5, 0.25, 6.0) }
            Sfx.VOTE -> { b = buffer(0.5); tone(b, 0.0, 0.45, 659.25, 0.4, 6.0); tone(b, 0.08, 0.4, 987.77, 0.3, 6.0) }
            Sfx.BUILD -> { b = buffer(0.12); tone(b, 0.0, 0.1, 1400.0, 0.4, 40.0) }
            Sfx.NUKE -> { b = buffer(1.8); rumble(b, 1.0); drum(b, 0.0, 1.0, 40.0) }
        }
        return toPcm(b)
    }
}
