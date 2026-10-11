package com.dmwnezes.soberano.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmwnezes.soberano.sim.Flag
import com.dmwnezes.soberano.sim.Nation
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object FlagCache {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(ctx: Context, code: String): ImageBitmap? = cache.getOrPut(code) {
        runCatching { ctx.assets.open("flags/$code.webp").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
    }
}

/** Bandeira do país (real) ou desenhada (país criado). Proporção 4:3. */
@Composable
fun FlagView(nation: Nation, height: Dp = 24.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(height * 0.18f)
    val m = modifier.size(height * 4f / 3f, height).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.18f), shape)
    val flag = nation.flag
    if (flag != null) {
        CustomFlag(flag, m)
        return
    }
    val ctx = LocalContext.current
    val img = remember(nation.code) { FlagCache.get(ctx, nation.code) }
    if (img != null) Image(img, nation.name, m, contentScale = ContentScale.Crop)
    else Box(m) { CustomFlag(Flag(colors = listOf(nation.mapColor, 0xFFFFFFFF, nation.mapColor)), Modifier.size(height * 4f / 3f, height)) }
}

@Composable
fun CustomFlag(flag: Flag, modifier: Modifier) {
    Canvas(modifier) { drawFlag(flag) }
}

fun DrawScope.drawFlag(flag: Flag) {
    val c = flag.colors.map { Color(it.toInt()) }.let { if (it.size < 3) it + List(3 - it.size) { Color.White } else it }
    val w = size.width; val h = size.height
    when (flag.layout) {
        0 -> for (i in 0..2) drawRect(c[i], Offset(0f, h * i / 3f), Size(w, h / 3f + 1f))
        1 -> for (i in 0..2) drawRect(c[i], Offset(w * i / 3f, 0f), Size(w / 3f + 1f, h))
        2 -> {
            drawRect(c[0])
            val t = h * 0.2f
            drawRect(c[1], Offset(w * 0.3f - t / 2, 0f), Size(t, h))
            drawRect(c[1], Offset(0f, h / 2 - t / 2), Size(w, t))
            val t2 = t * 0.5f
            drawRect(c[2], Offset(w * 0.3f - t2 / 2, 0f), Size(t2, h))
            drawRect(c[2], Offset(0f, h / 2 - t2 / 2), Size(w, t2))
        }
        3 -> {
            drawRect(c[0])
            val p = Path().apply { moveTo(0f, h); lineTo(w, 0f); lineTo(w, h); close() }
            drawPath(p, c[1])
            val band = Path().apply { moveTo(0f, h * 0.85f); lineTo(w * 0.85f, 0f); lineTo(w, 0f); lineTo(w, h * 0.15f); lineTo(w * 0.15f, h); lineTo(0f, h); close() }
            drawPath(band, c[2])
        }
        4 -> {
            drawRect(c[0], Offset(0f, 0f), Size(w, h / 2f))
            drawRect(c[1], Offset(0f, h / 2f), Size(w, h / 2f))
            drawRect(c[2], Offset(0f, 0f), Size(w * 0.42f, h / 2f))
        }
        else -> {
            drawRect(c[0])
            drawCircle(c[1], radius = h * 0.3f, center = Offset(w / 2, h / 2))
        }
    }
    val emblemColor = when (flag.layout) { 5 -> c[2]; 4 -> c[1]; else -> if (c.size > 3) Color(flag.colors[3].toInt()) else Color.White }
    val center = when (flag.layout) { 4 -> Offset(w * 0.21f, h * 0.25f); 2 -> Offset(w * 0.3f, h / 2); else -> Offset(w / 2, h / 2) }
    val r = min(w, h) * (if (flag.layout == 4) 0.16f else 0.22f)
    when (flag.emblem) {
        1 -> drawPath(star(center, r), emblemColor)
        2 -> {
            drawCircle(emblemColor, r * 0.55f, center)
            for (k in 0 until 12) {
                val a = Math.toRadians(k * 30.0)
                drawLine(emblemColor, Offset(center.x + (r * 0.7f * cos(a)).toFloat(), center.y + (r * 0.7f * sin(a)).toFloat()),
                    Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()), strokeWidth = r * 0.14f)
            }
        }
        3 -> {
            drawCircle(emblemColor, r, center)
            drawCircle(c[if (flag.layout == 5) 1 else 1], r * 0.85f, Offset(center.x + r * 0.35f, center.y - r * 0.1f))
        }
        4 -> {
            val p = Path().apply {
                moveTo(center.x - r * 0.8f, center.y - r); lineTo(center.x + r * 0.8f, center.y - r)
                lineTo(center.x + r * 0.8f, center.y + r * 0.1f); quadraticBezierTo(center.x + r * 0.7f, center.y + r * 0.8f, center.x, center.y + r * 1.1f)
                quadraticBezierTo(center.x - r * 0.7f, center.y + r * 0.8f, center.x - r * 0.8f, center.y + r * 0.1f); close()
            }
            drawPath(p, emblemColor)
        }
    }
}

private fun star(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.42f
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = c.x + (rr * cos(a)).toFloat(); val y = c.y + (rr * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
