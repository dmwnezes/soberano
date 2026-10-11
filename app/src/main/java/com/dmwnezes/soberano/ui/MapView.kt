package com.dmwnezes.soberano.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as ACanvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.dmwnezes.soberano.R
import com.dmwnezes.soberano.sim.Army
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.world.Projection
import com.dmwnezes.soberano.world.WorldMap
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Câmera do mapa: tela = mundo × escala + deslocamento. */
class MapCamera {
    var scale by mutableFloatStateOf(0f)
    var ox by mutableFloatStateOf(0f)
    var oy by mutableFloatStateOf(0f)
    var viewW = 1f; var viewH = 1f
    var worldW = 4000f; var worldH = 2003f

    val minScale get() = viewW / worldW * 0.98f
    val maxScale = 70f

    fun toWorld(o: Offset) = Offset((o.x - ox) / scale, (o.y - oy) / scale)

    fun clamp() {
        scale = scale.coerceIn(minScale, maxScale)
        val w = worldW * scale; val h = worldH * scale
        ox = if (w <= viewW) (viewW - w) / 2 else ox.coerceIn(viewW - w - viewW * 0.35f, viewW * 0.35f)
        oy = oy.coerceIn(viewH * 0.45f - h, viewH * 0.55f)
    }

    fun zoomAt(c: Offset, f: Float) {
        val ns = (scale * f).coerceIn(minScale, maxScale)
        val k = ns / scale
        ox = c.x - (c.x - ox) * k
        oy = c.y - (c.y - oy) * k
        scale = ns
    }

    /** Enquadra um retângulo do mundo na metade de cima da tela. */
    fun fit(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        val bw = (maxX - minX).coerceAtLeast(20f); val bh = (maxY - minY).coerceAtLeast(20f)
        val s = minOf(viewW / bw, viewH * 0.5f / bh) * 0.8f
        centerOn((minX + maxX) / 2, (minY + maxY) / 2, s.coerceIn(minScale * 1.5f, 14f))
    }

    fun centerOn(x: Float, y: Float, s: Float = scale) {
        scale = s.coerceIn(minScale, maxScale)
        ox = viewW / 2 - x * scale
        oy = viewH * 0.42f - y * scale
        clamp()
    }
}

/** Desenho do mapa com cache em bitmap para zoom afastado. */
class MapRenderer(private val world: WorldMap, typeface: Typeface?) {
    val paths: Array<Path> = Array(world.provinces.size) { i ->
        Path().apply {
            for (r in world.provinces[i].rings) {
                moveTo(r[0], r[1])
                var k = 2
                while (k < r.size) { lineTo(r[k], r[k + 1]); k += 2 }
                close()
            }
        }
    }
    private val graticule = Path().apply {
        var lon = -180.0
        while (lon <= 180.0) { val x = Projection.x(lon); moveTo(x, 0f); lineTo(x, world.height); lon += 15.0 }
        var lat = -45.0
        while (lat <= 75.0) { val y = Projection.y(lat); moveTo(0f, y); lineTo(world.width, y); lat += 15.0 }
    }

    companion object {
        const val CACHE_W = 3072
        private val stripe: Bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).also { b ->
            val c = ACanvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; strokeWidth = 3f }
            c.drawLine(-2f, 12f, 12f, -2f, p); c.drawLine(-2f, 2f, 2f, -2f, p); c.drawLine(8f, 12f, 12f, 8f, p)
        }
    }

    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val stripePaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; color = Ink.grid.toArgb(); isAntiAlias = true }
    private val glow = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true; strokeJoin = Paint.Join.ROUND }
    private val marker = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textAlign = Paint.Align.CENTER }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    @Volatile private var cache: Bitmap? = null
    @Volatile private var cacheKey = Long.MIN_VALUE
    @Volatile private var pendingKey = Long.MIN_VALUE
    private val executor = Executors.newSingleThreadExecutor()
    var onCacheReady: (() -> Unit)? = null

    var colors = IntArray(world.provinces.size)
    var stripes = IntArray(world.provinces.size)
    private var labelX = FloatArray(0); private var labelY = FloatArray(0); private var labelA = FloatArray(0)

    fun ensureCache(key: Long, fills: IntArray, stripeColors: IntArray, sim: Sim) {
        colors = fills; stripes = stripeColors
        if (key == cacheKey || key == pendingKey) return
        pendingKey = key
        computeLabels(sim)
        val f = fills.copyOf(); val s = stripeColors.copyOf()
        executor.execute {
            val h = (CACHE_W * world.height / world.width).toInt()
            val bmp = Bitmap.createBitmap(CACHE_W, h, Bitmap.Config.ARGB_8888)
            val c = ACanvas(bmp)
            val k = CACHE_W / world.width
            c.scale(k, k)
            // pincéis próprios: a thread da interface usa os outros ao mesmo tempo
            drawProvinces(c, 0 until paths.size, f, s, 0.7f / k, null, Paints())
            cache = bmp
            cacheKey = key
            onCacheReady?.invoke()
        }
    }

    private fun computeLabels(sim: Sim) {
        val n = sim.game.n
        val sx = FloatArray(n); val sy = FloatArray(n); val sa = FloatArray(n)
        val owner = sim.game.owner
        // o nome fica sobre a parte do país ligada à capital
        for (nat in sim.game.nations) {
            if (!nat.alive) continue
            var start = nat.capital
            if (owner[start] != nat.id) start = owner.indices.firstOrNull { owner[it] == nat.id } ?: continue
            val seen = HashSet<Int>(); val stack = ArrayDeque<Int>()
            stack += start; seen += start
            while (stack.isNotEmpty()) {
                val u = stack.removeLast()
                val p = world.provinces[u]
                sx[nat.id] += p.cx * p.mapArea; sy[nat.id] += p.cy * p.mapArea; sa[nat.id] += p.mapArea
                for (v in p.neighbors) if (owner[v] == nat.id && seen.add(v)) stack += v
            }
        }
        for (i in 0 until n) if (sa[i] > 0) { sx[i] /= sa[i]; sy[i] /= sa[i] }
        labelX = sx; labelY = sy; labelA = sa
    }

    private class Paints {
        val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
        val border = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
        val stripe = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }
    }
    private val uiPaints = Paints()

    private fun drawProvinces(c: ACanvas, ids: Iterable<Int>, f: IntArray, s: IntArray, stroke: Float, visible: RectF?, pp: Paints = uiPaints) {
        val fill = pp.fill; val border = pp.border; val stripePaint = pp.stripe
        border.strokeWidth = stroke
        for (i in ids) {
            if (visible != null && !visibleProv(i, visible)) continue
            fill.color = f[i]
            c.drawPath(paths[i], fill)
            if (s[i] != 0) {
                stripePaint.shader = BitmapShader(stripe, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
                    val m = android.graphics.Matrix(); m.setScale(stroke * 1.4f, stroke * 1.4f); setLocalMatrix(m)
                }
                stripePaint.colorFilter = PorterDuffColorFilter(s[i], PorterDuff.Mode.SRC_IN)
                c.drawPath(paths[i], stripePaint)
            }
            border.color = darken(f[i], 0.72f)
            c.drawPath(paths[i], border)
        }
    }

    private fun visibleProv(i: Int, r: RectF): Boolean {
        val p = world.provinces[i]
        return p.maxX >= r.left && p.minX <= r.right && p.maxY >= r.top && p.minY <= r.bottom
    }

    private fun darken(c: Int, f: Float): Int {
        val r = ((c shr 16 and 0xff) * f).toInt(); val g = ((c shr 8 and 0xff) * f).toInt(); val b = ((c and 0xff) * f).toInt()
        return (0xff shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Desenha tudo. [dp] = pixels por dp. */
    fun draw(
        c: ACanvas, cam: MapCamera, sim: Sim, ui: GameController?, dp: Float, timeMs: Long,
        outlineNation: Int, highlightNation: Int, selectedProvince: Int,
    ) {
        val s = cam.scale
        if (s <= 0f) return
        c.drawColor(Ink.ocean.toArgb())
        val view = RectF((-cam.ox) / s, (-cam.oy) / s, (cam.viewW - cam.ox) / s, (cam.viewH - cam.oy) / s)
        c.save()
        c.translate(cam.ox, cam.oy)
        c.scale(s, s)
        gridPaint.strokeWidth = 1f / s
        c.drawPath(graticule, gridPaint)

        val bmp = cache
        val useBitmap = bmp != null && s * world.width <= CACHE_W * 1.35f
        if (useBitmap) {
            c.drawBitmap(bmp!!, null, RectF(0f, 0f, world.width, world.height), fill.apply { isFilterBitmap = true; color = -1 })
        } else {
            drawProvinces(c, paths.indices, colors, stripes, 1f / s, view)
        }

        // contorno brilhante do país em destaque (jogador) e do país selecionado
        outline(c, sim, outlineNation, Ink.brass.toArgb(), 3.2f * dp / s, view)
        if (highlightNation >= 0 && highlightNation != outlineNation) outline(c, sim, highlightNation, 0xFFFFFFFF.toInt(), 2.2f * dp / s, view)
        if (selectedProvince >= 0) {
            glow.strokeWidth = 2.2f * dp / s
            glow.color = 0xFFFFFFFF.toInt()
            c.drawPath(paths[selectedProvince], glow)
        }
        c.restore()

        labels(c, cam, sim, dp, view)
        overlays(c, cam, sim, ui, dp, timeMs, view)
    }

    private fun outline(c: ACanvas, sim: Sim, nation: Int, color: Int, width: Float, view: RectF) {
        if (nation < 0) return
        val owner = sim.game.owner
        glow.color = color
        glow.strokeWidth = width
        val ids = owner.indices.filter { owner[it] == nation && visibleProv(it, view) }
        for (i in ids) c.drawPath(paths[i], glow)
        // repinta por cima: só a metade externa do traço fica visível
        border.strokeWidth = 0.8f * width / 3.2f
        for (i in ids) {
            fill.color = colors[i]; c.drawPath(paths[i], fill)
            if (stripes[i] != 0) { stripePaint.colorFilter = PorterDuffColorFilter(stripes[i], PorterDuff.Mode.SRC_IN); c.drawPath(paths[i], stripePaint) }
            border.color = darken(colors[i], 0.72f); c.drawPath(paths[i], border)
        }
    }

    private fun labels(c: ACanvas, cam: MapCamera, sim: Sim, dp: Float, view: RectF) {
        val s = cam.scale
        text.setShadowLayer(3f * dp, 0f, 0f, 0xCC0D110B.toInt())
        // nomes de países
        for (i in labelA.indices) {
            val a = labelA[i]
            if (a <= 0f) continue
            val size = (sqrt(a) * s * 0.16f).coerceAtMost(26f * dp)
            if (size < 10.5f * dp) continue
            val x = labelX[i] * s + cam.ox; val y = labelY[i] * s + cam.oy
            if (x < -200 || x > cam.viewW + 200 || y < -50 || y > cam.viewH + 50) continue
            text.textSize = size
            text.color = 0xD8ECE6D4.toInt()
            text.letterSpacing = if (size > 18 * dp) 0.06f else 0.02f
            c.drawText(sim.nation(i).name, x, y + size * 0.35f, text)
        }
        // nomes de províncias com zoom
        if (s > 4.5f) {
            text.letterSpacing = 0f
            for (p in world.provinces) {
                if (p.cx < view.left || p.cx > view.right || p.cy < view.top || p.cy > view.bottom) continue
                val px = p.mapArea * s * s
                if (px < 9000f * dp * dp) continue
                text.textSize = 11f * dp
                text.color = 0xB0ECE6D4.toInt()
                c.drawText(p.name, p.cx * s + cam.ox, p.cy * s + cam.oy + 14f * dp, text)
            }
        }
        text.clearShadowLayer()
    }

    private fun armyPos(a: Army, sim: Sim): Offset {
        val p = world.provinces[a.province]
        if (a.path.isEmpty() || a.legKm <= 0f) return Offset(p.cx, p.cy)
        val q = world.provinces[a.path[0]]
        val t = (a.progress / a.legKm).coerceIn(0f, 1f)
        return Offset(p.cx + (q.cx - p.cx) * t, p.cy + (q.cy - p.cy) * t)
    }

    /** Quais exércitos aparecem: do jogador, aliados, inimigos e, com zoom, todos. */
    fun visibleArmies(sim: Sim, cam: MapCamera): List<Army> {
        val me = sim.game.player
        val zoomed = cam.scale > 2.2f
        return sim.game.armies.filter { a ->
            a.owner == me || sim.atWar(me, a.owner) || (zoomed && (sim.allied(me, a.owner) || cam.scale > 4f)) || a.battle
        }
    }

    fun markerRects(sim: Sim, cam: MapCamera, dp: Float): List<Pair<Army, RectF>> {
        val out = ArrayList<Pair<Army, RectF>>()
        val groups = LinkedHashMap<Int, MutableList<Army>>()
        for (a in visibleArmies(sim, cam)) groups.getOrPut(if (a.moving) -a.id else a.province) { mutableListOf() } += a
        val w = 34f * dp; val h = 20f * dp; val gap = 3f * dp
        for (list in groups.values) {
            val pos = armyPos(list[0], sim)
            val x = pos.x * cam.scale + cam.ox; val y = pos.y * cam.scale + cam.oy
            if (x < -80 || x > cam.viewW + 80 || y < -80 || y > cam.viewH + 80) continue
            val cols = min(3, list.size)
            list.forEachIndexed { k, a ->
                val col = k % 3; val row = k / 3
                val left = x - (cols * w + (cols - 1) * gap) / 2 + col * (w + gap)
                val top = y - h - 7f * dp - row * (h + gap)
                out += a to RectF(left, top, left + w, top + h)
            }
        }
        return out
    }

    private fun overlays(c: ACanvas, cam: MapCamera, sim: Sim, ui: GameController?, dp: Float, timeMs: Long, view: RectF) {
        val g = sim.game
        val s = cam.scale
        val me = g.player
        fun sx(x: Float) = x * s + cam.ox
        fun sy(y: Float) = y * s + cam.oy

        // trajetos dos exércitos do jogador
        for (a in g.armies) {
            if (a.owner != me || a.path.isEmpty()) continue
            val selected = ui?.selArmy == a.id
            dash.strokeWidth = (if (selected) 2.6f else 1.6f) * dp
            dash.color = if (selected) Ink.brass.toArgb() else 0x99F28C28.toInt()
            dash.pathEffect = DashPathEffect(floatArrayOf(6 * dp, 5 * dp), -(timeMs / 30f) % (11 * dp))
            val path = Path()
            val start = armyPos(a, sim)
            path.moveTo(sx(start.x), sy(start.y))
            for (q in a.path) { val p = world.provinces[q]; path.lineTo(sx(p.cx), sy(p.cy)) }
            c.drawPath(path, dash)
            val end = world.provinces[a.path.last()]
            marker.style = Paint.Style.FILL; marker.color = dash.color
            c.drawCircle(sx(end.cx), sy(end.cy), 3.5f * dp, marker)
        }

        // prévia do trajeto da ordem
        val sel = g.armies.firstOrNull { it.id == ui?.selArmy }
        val pv = ui?.previewPath
        if (sel != null && pv != null && pv.isNotEmpty() && (ui.orderTarget >= 0)) {
            dash.strokeWidth = 2.6f * dp
            dash.color = 0xFFECE6D4.toInt()
            dash.pathEffect = DashPathEffect(floatArrayOf(3 * dp, 6 * dp), -(timeMs / 25f) % (9 * dp))
            val path = Path()
            val st = world.provinces[sel.province]
            path.moveTo(sx(st.cx), sy(st.cy))
            for (q in pv) { val p = world.provinces[q]; path.lineTo(sx(p.cx), sy(p.cy)) }
            c.drawPath(path, dash)
            val end = world.provinces[pv.last()]
            marker.style = Paint.Style.STROKE; marker.strokeWidth = 2.5f * dp; marker.color = 0xFFECE6D4.toInt()
            c.drawCircle(sx(end.cx), sy(end.cy), (9f + 3f * ((sin(timeMs / 180.0) + 1) / 2).toFloat()) * dp, marker)
        }

        // cercos
        for (p in g.siege.indices) {
            val v = g.siege[p]
            if (v <= 0f) continue
            val pr = world.provinces[p]
            if (pr.cx < view.left || pr.cx > view.right || pr.cy < view.top || pr.cy > view.bottom) continue
            val x = sx(pr.cx); val y = sy(pr.cy)
            val r = 13f * dp
            marker.style = Paint.Style.STROKE; marker.strokeWidth = 3f * dp
            marker.color = 0x55000000
            c.drawCircle(x, y, r, marker)
            marker.color = Ink.amber.toArgb()
            c.drawArc(RectF(x - r, y - r, x + r, y + r), -90f, 360f * v, false, marker)
        }

        // batalhas
        val pulse = ((sin(timeMs / 220.0) + 1) / 2).toFloat()
        for (b in g.battles) {
            val pr = world.provinces[b.province]
            val x = sx(pr.cx); val y = sy(pr.cy)
            if (x < -60 || x > cam.viewW + 60 || y < -60 || y > cam.viewH + 60) continue
            val mine = b.attacker == me || b.defender == me
            val col = if (mine) Ink.coral.toArgb() else Ink.amber.toArgb()
            marker.style = Paint.Style.FILL
            marker.color = (col and 0x00FFFFFF) or ((0x50 * (1 - pulse)).toInt() shl 24)
            c.drawCircle(x, y, (14f + 14f * pulse) * dp, marker)
            marker.color = col
            c.drawCircle(x, y, 11f * dp, marker)
            // espadas cruzadas
            marker.style = Paint.Style.STROKE; marker.strokeWidth = 2.2f * dp; marker.color = 0xFF1A0E0B.toInt()
            marker.strokeCap = Paint.Cap.ROUND
            val k = 5.5f * dp
            c.drawLine(x - k, y - k, x + k, y + k, marker); c.drawLine(x + k, y - k, x - k, y + k, marker)
            // barra de força
            val tot = (b.attackerPower + b.defenderPower).coerceAtLeast(0.1f)
            val bw = 34f * dp; val by = y + 15f * dp
            marker.style = Paint.Style.FILL
            marker.color = sim.nation(b.attacker).mapColor.toInt()
            c.drawRoundRect(RectF(x - bw / 2, by, x - bw / 2 + bw * b.attackerPower / tot, by + 4 * dp), 2 * dp, 2 * dp, marker)
            marker.color = sim.nation(b.defender).mapColor.toInt()
            c.drawRoundRect(RectF(x - bw / 2 + bw * b.attackerPower / tot, by, x + bw / 2, by + 4 * dp), 2 * dp, 2 * dp, marker)
        }

        // marcadores de tropas
        for ((a, r) in markerRects(sim, cam, dp)) {
            val owner = sim.nation(a.owner)
            val selected = ui?.selArmy == a.id
            val enemy = sim.atWar(me, a.owner)
            marker.style = Paint.Style.FILL
            marker.color = 0xE61E2418.toInt()
            c.drawRoundRect(r, 7 * dp, 7 * dp, marker)
            // faixa com a cor do país
            marker.color = owner.mapColor.toInt()
            c.drawRoundRect(RectF(r.left, r.top, r.left + 12 * dp, r.bottom), 7 * dp, 7 * dp, marker)
            c.drawRect(RectF(r.left + 6 * dp, r.top, r.left + 12 * dp, r.bottom), marker)
            // ícone do ramo
            glyph(c, a.domain, r.left + 6 * dp, r.centerY(), dp)
            // número
            text.textSize = 11.5f * dp; text.color = Ink.text.toArgb(); text.letterSpacing = 0f
            val num = a.total().let { if (it >= 100) it.toInt().toString() else com.dmwnezes.soberano.sim.Fmt.units((it * 2).toInt() / 2f) }
            c.drawText(num, r.left + 12 * dp + (r.width() - 12 * dp) / 2, r.centerY() + 4 * dp, text)
            // borda
            marker.style = Paint.Style.STROKE; marker.strokeWidth = (if (selected) 2.4f else 1.3f) * dp
            marker.color = when { selected -> 0xFFFFFFFF.toInt(); a.owner == me -> Ink.brass.toArgb(); enemy -> Ink.coral.toArgb(); else -> Ink.line.toArgb() }
            c.drawRoundRect(r, 7 * dp, 7 * dp, marker)
            if (a.morale < 0.5f) {
                marker.style = Paint.Style.FILL; marker.color = Ink.coral.toArgb()
                c.drawCircle(r.right - 2 * dp, r.top + 2 * dp, 3 * dp, marker)
            }
        }
    }

    private fun glyph(c: ACanvas, d: Domain, x: Float, y: Float, dp: Float) {
        marker.style = Paint.Style.FILL
        marker.color = 0xFF141A12.toInt()
        val p = Path()
        when (d) {
            Domain.LAND -> { // escudo
                p.moveTo(x - 3.5f * dp, y - 4 * dp); p.lineTo(x + 3.5f * dp, y - 4 * dp); p.lineTo(x + 3.5f * dp, y)
                p.quadTo(x + 3 * dp, y + 3.5f * dp, x, y + 5 * dp); p.quadTo(x - 3 * dp, y + 3.5f * dp, x - 3.5f * dp, y); p.close()
            }
            Domain.SEA -> { // casco
                p.moveTo(x - 4.5f * dp, y); p.lineTo(x + 4.5f * dp, y); p.lineTo(x + 2.5f * dp, y + 3.5f * dp); p.lineTo(x - 2.5f * dp, y + 3.5f * dp); p.close()
                p.addRect(x - 0.8f * dp, y - 4.5f * dp, x + 0.8f * dp, y, Path.Direction.CW)
            }
            Domain.AIR -> { // avião
                p.moveTo(x, y - 5 * dp); p.lineTo(x + 1.2f * dp, y - 1 * dp); p.lineTo(x + 4.5f * dp, y + 1 * dp); p.lineTo(x + 4.5f * dp, y + 2 * dp)
                p.lineTo(x + 1.2f * dp, y + 1.2f * dp); p.lineTo(x + 1 * dp, y + 4 * dp); p.lineTo(x - 1 * dp, y + 4 * dp); p.lineTo(x - 1.2f * dp, y + 1.2f * dp)
                p.lineTo(x - 4.5f * dp, y + 2 * dp); p.lineTo(x - 4.5f * dp, y + 1 * dp); p.lineTo(x - 1.2f * dp, y - 1 * dp); p.close()
            }
        }
        c.drawPath(p, marker)
    }
}

// ---------------------------------------------------------------- cores por modo

object MapColors {
    private fun lerp(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 0xff) + (((b shr sh) and 0xff) - ((a shr sh) and 0xff)) * tt).toInt()
        return (0xff shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    private val NEUTRAL = 0xFF7C806A.toInt()
    private val DIM = 0xFF4E5444.toInt()

    fun compute(sim: Sim, mode: MapMode, focus: Int): Pair<IntArray, IntArray> {
        val g = sim.game
        val np = g.owner.size
        val fills = IntArray(np); val stripes = IntArray(np)
        val me = g.player
        val f = if (focus >= 0) focus else me
        val nationColor = IntArray(g.n) { i ->
            val n = g.nations[i]
            when (mode) {
                MapMode.POLITICAL -> n.mapColor.toInt()
                MapMode.RELATIONS -> when {
                    i == f -> Ink.brass.toArgb()
                    sim.atWar(f, i) -> Ink.coral.toArgb()
                    sim.allied(f, i) -> Ink.verdigris.toArgb()
                    else -> {
                        val r = g.rel(f, i)
                        if (r >= 0) lerp(NEUTRAL, 0xFF7FA35A.toInt(), r / 100f) else lerp(NEUTRAL, 0xFFB5493A.toInt(), -r / 100f)
                    }
                }
                MapMode.WAR -> when {
                    i == me -> Ink.brass.toArgb()
                    sim.atWar(me, i) -> Ink.coral.toArgb()
                    sim.allied(me, i) -> Ink.verdigris.toArgb()
                    sim.atWarAny(i) -> 0xFF9C7A4A.toInt()
                    else -> DIM
                }
                MapMode.APPROVAL -> {
                    val a = n.approval
                    if (a < 50f) lerp(0xFFB5493A.toInt(), 0xFFC9A04F.toInt(), a / 50f) else lerp(0xFFC9A04F.toInt(), 0xFF7FA35A.toInt(), (a - 50f) / 50f)
                }
                MapMode.WEALTH -> {
                    val pc = g.natGdp(i) * 1e9 / max(1.0, g.natPop(i))
                    val t = ((ln(max(500.0, pc)) - ln(500.0)) / (ln(90000.0) - ln(500.0))).toFloat()
                    lerp(0xFF3A4234.toInt(), 0xFFD9CFA0.toInt(), t)
                }
            }
        }
        for (p in 0 until np) {
            val o = g.owner[p]; val c = g.controller[p]
            fills[p] = nationColor[o]
            if (c != o) stripes[p] = if (mode == MapMode.POLITICAL) nationColor[c] else Ink.coral.toArgb()
        }
        return fills to stripes
    }

    fun key(sim: Sim, mode: MapMode, focus: Int): Long {
        val g = sim.game
        var h = 1469598103934665603L
        for (i in g.owner.indices) { h = (h xor g.owner[i].toLong()) * 1099511628211L; h = (h xor g.controller[i].toLong()) * 1099511628211L }
        h = (h xor mode.ordinal.toLong()) * 1099511628211L
        h = (h xor focus.toLong()) * 1099511628211L
        h = (h xor g.player.toLong()) * 1099511628211L
        if (mode != MapMode.POLITICAL) {
            h = (h xor (g.day / 30).toLong()) * 1099511628211L
            h = (h xor g.wars.size.toLong()) * 1099511628211L
            h = (h xor (g.treatyCache?.hashCode()?.toLong() ?: 0L)) * 1099511628211L
        }
        for (n in g.nations) h = (h xor n.mapColor) * 1099511628211L
        return h
    }
}

// ---------------------------------------------------------------- composable

@Composable
fun WorldMapView(
    sim: Sim,
    camera: MapCamera,
    controller: GameController?,
    modifier: Modifier = Modifier,
    mode: MapMode = MapMode.POLITICAL,
    focusNation: Int = -1,
    outlineNation: Int = sim.game.player,
    highlightNation: Int = -1,
    selectedProvince: Int = -1,
    frame: Long = 0L,
    initialCenter: Int = -1,
    onTapProvince: (Int) -> Unit = {},
    onTapArmy: (Army) -> Unit = {},
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember(sim.world) {
        MapRenderer(sim.world, runCatching { ResourcesCompat.getFont(ctx, R.font.outfit_semibold) }.getOrNull())
    }
    var cacheVersion by remember { mutableLongStateOf(0L) }
    LaunchedEffect(renderer) { renderer.onCacheReady = { cacheVersion++ } }

    Canvas(
        modifier
            .onSizeChanged { sz ->
                val first = camera.scale == 0f
                camera.viewW = sz.width.toFloat(); camera.viewH = sz.height.toFloat()
                camera.worldW = sim.world.width; camera.worldH = sim.world.height
                if (first) {
                    if (initialCenter >= 0) fitNation(sim, camera, sim.game.owner[initialCenter], initialCenter)
                    else camera.centerOn(sim.world.width / 2, sim.world.height * 0.45f, camera.minScale)
                } else camera.clamp()
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    camera.zoomAt(centroid, zoom)
                    camera.ox += pan.x; camera.oy += pan.y
                    camera.clamp()
                }
            }
            .pointerInput(sim) {
                detectTapGestures(
                    onDoubleTap = { o -> camera.zoomAt(o, 2f); camera.clamp() },
                    onTap = { o ->
                        val hit = renderer.markerRects(sim, camera, density).lastOrNull { it.second.contains(o.x, o.y) }
                        if (hit != null) { onTapArmy(hit.first); return@detectTapGestures }
                        val w = camera.toWorld(o)
                        var p = sim.world.hit(w.x, w.y)
                        if (p < 0) p = sim.world.nearest(w.x, w.y, 10f * density / camera.scale)
                        onTapProvince(p)
                    },
                )
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") frame; cacheVersion
        val sc = camera.scale; camera.ox; camera.oy
        if (sc <= 0f) return@Canvas
        val key = MapColors.key(sim, mode, focusNation)
        val (fills, stripes) = if (key != lastKey[renderer]) MapColors.compute(sim, mode, focusNation).also { lastColors[renderer] = it; lastKey[renderer] = key }
        else lastColors[renderer]!!
        renderer.ensureCache(key, fills, stripes, sim)
        drawIntoCanvas { canvas ->
            renderer.draw(canvas.nativeCanvas, camera, sim, controller, density, System.currentTimeMillis(), outlineNation, highlightNation, selectedProvince)
        }
    }

    LaunchedEffect(initialCenter) {
        if (initialCenter >= 0 && camera.scale > 0f) fitNation(sim, camera, sim.game.owner[initialCenter], initialCenter)
    }
}

/** Enquadra o território principal de um país (sem ilhas distantes). */
fun fitNation(sim: Sim, camera: MapCamera, nation: Int, around: Int) {
    val w = sim.world
    val owner = sim.game.owner
    var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
    val seen = HashSet<Int>(); val stack = ArrayDeque<Int>()
    val start = if (owner[around] == nation) around else owner.indices.firstOrNull { owner[it] == nation } ?: around
    stack += start; seen += start
    while (stack.isNotEmpty()) {
        val u = stack.removeLast(); val p = w.provinces[u]
        a = minOf(a, p.minX); b = minOf(b, p.minY); c = maxOf(c, p.maxX); d = maxOf(d, p.maxY)
        for (v in p.neighbors) if (owner[v] == nation && seen.add(v)) stack += v
    }
    camera.fit(a, b, c, d)
}

private val lastKey = HashMap<MapRenderer, Long>()
private val lastColors = HashMap<MapRenderer, Pair<IntArray, IntArray>>()
