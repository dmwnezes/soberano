package com.dmwnezes.soberano.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), content = content)
}

/** Cabeçalho fixo da gaveta com sub-abas. */
@Composable
fun SheetHeaderTabs(title: String, sub: String?, tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Text(title, style = Type.title, color = Ink.text, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = Type.label, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                Text(
                    t, style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = if (sel) Ink.onBrass else Ink.muted,
                    modifier = Modifier.clip(Shapes.chip).background(if (sel) Ink.brass else Ink.raised)
                        .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SheetTitle(title: String, sub: String? = null) {
    Text(title, style = Type.title, color = Ink.text, modifier = Modifier.padding(top = 6.dp))
    if (sub != null) Text(sub, style = Type.label, color = Ink.muted)
}

@Composable
fun BigStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
        Text(value, style = Type.big.copy(fontSize = 24.sp), color = color, maxLines = 1)
        Text(label, style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Text(text, style = Type.small.copy(fontSize = 11.sp), color = color, modifier = Modifier.clip(Shapes.chip).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
fun RelationBadge(r: Float) {
    val col = when { r >= 40 -> Ink.verdigris; r >= 0 -> Ink.muted; r >= -40 -> Ink.amber; else -> Ink.coral }
    Text((if (r > 0) "+" else "") + r.toInt(), style = Type.number.copy(fontSize = 14.sp), color = col,
        modifier = Modifier.clip(Shapes.chip).background(col.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun ActionRow(icon: ImageVector, title: String, sub: String, enabled: Boolean, active: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val accent = when { danger -> Ink.coral; active -> Ink.verdigris; else -> Ink.muted }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (active) Ink.verdigris.copy(alpha = 0.13f) else Ink.raised)
            .clickable(enabled = enabled, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (!enabled) Ink.faint else accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium), color = if (enabled) Ink.text else Ink.faint)
            Text(sub, style = Type.small, color = if (enabled) Ink.muted else Ink.coral.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

/** Estrelas de 1 a 5. */
@Composable
fun Stars(n: Int, color: Color = Ink.brass) {
    Row {
        for (i in 1..5) Icon(Icons.Rounded.Star, null, tint = if (i <= n) color else Ink.line, modifier = Modifier.size(13.dp))
    }
}

/** Linha do tempo pequena com valor atual e variação. */
@Composable
fun LineChart(label: String, values: List<Float>, valueText: String, color: Color = Ink.brass, modifier: Modifier = Modifier, higherIsBetter: Boolean = true) {
    Column(modifier.clip(Shapes.tile).background(Ink.raised).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.label, color = Ink.muted, modifier = Modifier.weight(1f))
            if (values.size >= 2) {
                val d = values.last() - values.first()
                val up = d >= 0
                val good = up == higherIsBetter
                Text((if (up) "▲" else "▼"), style = Type.small, color = if (good) Ink.good else Ink.coral)
                Spacer(Modifier.width(4.dp))
            }
            Text(valueText, style = Type.number, color = Ink.text)
        }
        Spacer(Modifier.height(8.dp))
        if (values.size < 2) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                Text("O gráfico aparece depois do primeiro mês", style = Type.small, color = Ink.faint)
            }
        } else Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val min = values.min(); val max = values.max()
            val span = (max - min).takeIf { it > 1e-6f } ?: 1f
            val stepX = size.width / (values.size - 1)
            fun y(v: Float) = size.height - (v - min) / span * (size.height - 6f) - 3f
            val line = Path(); val area = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                if (i == 0) { line.moveTo(x, y(v)); area.moveTo(x, size.height); area.lineTo(x, y(v)) } else { line.lineTo(x, y(v)); area.lineTo(x, y(v)) }
            }
            area.lineTo(size.width, size.height); area.close()
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(color, 3.dp.toPx(), Offset(size.width, y(values.last())))
        }
    }
}

/** Barra dupla produção × consumo. */
@Composable
fun SupplyBar(produced: Float, used: Float, modifier: Modifier = Modifier) {
    val max = maxOf(produced, used, 0.01f)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Shapes.chip).background(Ink.line)) {
            Box(Modifier.fillMaxWidth(produced / max).height(6.dp).clip(Shapes.chip).background(Ink.good))
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(Shapes.chip).background(Ink.line)) {
            Box(Modifier.fillMaxWidth(used / max).height(6.dp).clip(Shapes.chip).background(Ink.amber))
        }
    }
}
