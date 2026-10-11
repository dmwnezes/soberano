package com.dmwnezes.soberano.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Group

object GameIcons {
    fun of(key: String): ImageVector = when (key) {
        "quake" -> Icons.Rounded.Landslide
        "flood" -> Icons.Rounded.Water
        "sun" -> Icons.Rounded.WbSunny
        "fire" -> Icons.Rounded.LocalFireDepartment
        "storm" -> Icons.Rounded.Thunderstorm
        "virus" -> Icons.Rounded.Coronavirus
        "syringe" -> Icons.Rounded.Vaccines
        "scandal" -> Icons.Rounded.MoneyOff
        "strike" -> Icons.Rounded.Engineering
        "protest" -> Icons.Rounded.Campaign
        "door" -> Icons.Rounded.MeetingRoom
        "tank" -> Icons.Rounded.MilitaryTech
        "chart_down" -> Icons.AutoMirrored.Rounded.TrendingDown
        "chart_up" -> Icons.AutoMirrored.Rounded.TrendingUp
        "oil" -> Icons.Rounded.OilBarrel
        "rocket" -> Icons.Rounded.RocketLaunch
        "siren" -> Icons.Rounded.LocalPolice
        "bomb" -> Icons.Rounded.Dangerous
        "prison" -> Icons.Rounded.Gavel
        "atom" -> Icons.Rounded.Science
        "person" -> Icons.Rounded.Person
        "satellite" -> Icons.Rounded.SatelliteAlt
        "people" -> Icons.Rounded.Groups
        "handshake" -> Icons.Rounded.Handshake
        "alert" -> Icons.Rounded.Warning
        "trophy", "victory" -> Icons.Rounded.EmojiEvents
        "goat" -> Icons.Rounded.Pets
        "camera" -> Icons.Rounded.PhotoCamera
        "ufo" -> Icons.Rounded.Rocket
        "party" -> Icons.Rounded.Celebration
        "vote" -> Icons.Rounded.HowToVote
        "law" -> Icons.Rounded.Gavel
        "flag" -> Icons.Rounded.Flag
        "block" -> Icons.Rounded.Block
        "dove" -> Icons.Rounded.Spa
        "war" -> Icons.Rounded.Whatshot
        "skull" -> Icons.Rounded.SentimentVeryDissatisfied
        "bloc" -> Icons.Rounded.Hub
        "ship" -> Icons.Rounded.DirectionsBoat
        "battle" -> Icons.Rounded.Bolt
        "defeat" -> Icons.Rounded.HeartBroken
        "factory" -> Icons.Rounded.Factory
        "nuke" -> Icons.Rounded.FlashOn
        "military" -> Icons.Rounded.MilitaryTech
        "health" -> Icons.Rounded.LocalHospital
        "school" -> Icons.Rounded.School
        "money" -> Icons.Rounded.Payments
        "shield" -> Icons.Rounded.Shield
        "mute" -> Icons.Rounded.VoiceOverOff
        "border" -> Icons.Rounded.Fence
        "leaf" -> Icons.Rounded.Eco
        "balance" -> Icons.Rounded.Balance
        "temple" -> Icons.Rounded.Church
        "field" -> Icons.Rounded.Agriculture
        "lock" -> Icons.Rounded.Lock
        else -> Icons.Rounded.Info
    }

    fun area(a: Area): ImageVector = when (a) {
        Area.HEALTH -> Icons.Rounded.LocalHospital
        Area.EDUCATION -> Icons.Rounded.School
        Area.SECURITY -> Icons.Rounded.LocalPolice
        Area.INFRA -> Icons.Rounded.Construction
        Area.SOCIAL -> Icons.Rounded.VolunteerActivism
        Area.ENVIRONMENT -> Icons.Rounded.Eco
        Area.RESEARCH -> Icons.Rounded.Science
        Area.DEFENSE -> Icons.Rounded.Shield
    }

    fun group(g: Group): ImageVector = when (g) {
        Group.WORKERS -> Icons.Rounded.Engineering
        Group.BUSINESS -> Icons.Rounded.BusinessCenter
        Group.MILITARY -> Icons.Rounded.MilitaryTech
        Group.RELIGIOUS -> Icons.Rounded.Church
        Group.YOUTH -> Icons.Rounded.EmojiPeople
        Group.RETIREES -> Icons.Rounded.Elderly
    }

    fun domain(d: Domain): ImageVector = when (d) {
        Domain.LAND -> Icons.Rounded.Shield
        Domain.SEA -> Icons.Rounded.Anchor
        Domain.AIR -> Icons.Rounded.Flight
    }
}

/** Painel arredondado escuro. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Box(modifier.clip(Shapes.card).background(Ink.raised).padding(padding)) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.heading, color = Ink.text, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun StatPill(icon: ImageVector, value: String, color: Color = Ink.text, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.clip(Shapes.chip).background(Ink.raised)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = Type.label, color = color, maxLines = 1)
    }
}

/** Chip de efeito (+ verde / − vermelho). */
@Composable
fun EffectChip(text: String, good: Boolean) {
    val c = if (good) Ink.good else Ink.coral
    Text(
        text, style = Type.small, color = c, maxLines = 1,
        modifier = Modifier.clip(Shapes.chip).background(c.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

enum class BtnKind { PRIMARY, SECONDARY, DANGER, GHOST }

@Composable
fun Btn(
    text: String,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.SECONDARY,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val bg = when (kind) {
        BtnKind.PRIMARY -> Ink.brass
        BtnKind.SECONDARY -> Ink.line
        BtnKind.DANGER -> Ink.coral.copy(alpha = 0.9f)
        BtnKind.GHOST -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.PRIMARY -> Ink.onBrass
        BtnKind.DANGER -> Color(0xFF2A0B07)
        else -> Ink.text
    }
    Row(
        modifier.clip(Shapes.chip).background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .let { if (kind == BtnKind.GHOST) it.border(1.dp, Ink.line, Shapes.chip) else it }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) fg else fg.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) fg else fg.copy(alpha = 0.5f), maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Ink.text, bg: Color = Ink.raised, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Barra 0..100 com cor por faixa. */
@Composable
fun Meter(value: Float, modifier: Modifier = Modifier, color: Color = Ink.scale(value), height: Dp = 6.dp) {
    Box(modifier.height(height).clip(Shapes.chip).background(Ink.line)) {
        Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).height(height).clip(Shapes.chip).background(color))
    }
}

/** Linha: ícone, nome, barra e número. */
@Composable
fun MeterRow(icon: ImageVector, label: String, value: Float, trailing: String = value.toInt().toString(), delta: Float? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
                if (delta != null && kotlin.math.abs(delta) >= 0.5f) {
                    Icon(if (delta > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null,
                        tint = if (delta > 0) Ink.good else Ink.coral, modifier = Modifier.size(13.dp))
                }
                Text(trailing, style = Type.number.copy(fontSize = 14.sp), color = Ink.text)
            }
            Spacer(Modifier.height(4.dp))
            Meter(value, Modifier.fillMaxWidth())
        }
    }
}


@Composable
fun ToggleRow(label: String, checked: Boolean, icon: ImageVector? = null, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Shapes.small).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Ink.muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = Ink.text)
            if (sub != null) Text(sub, style = Type.small, color = Ink.muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Ink.onBrass, checkedTrackColor = Ink.brass, uncheckedTrackColor = Ink.line, uncheckedThumbColor = Ink.muted, uncheckedBorderColor = Ink.line),
        )
    }
}

/** Controle deslizante com rótulo e valor. */
@Composable
fun SliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    hint: String? = null,
    hintGood: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Ink.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
            if (hint != null) {
                Text(hint, style = Type.small, color = if (hintGood) Ink.good else Ink.coral, maxLines = 1)
                Spacer(Modifier.width(8.dp))
            }
            Text(valueText, style = Type.number.copy(fontSize = 15.sp), color = Ink.brass)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Ink.brass, activeTrackColor = Ink.brass, inactiveTrackColor = Ink.line),
            modifier = Modifier.height(30.dp),
        )
    }
}

/** Seletor segmentado. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    Row(modifier.clip(Shapes.chip).background(Ink.oceanDeep).padding(3.dp)) {
        for (o in options) {
            val sel = o == selected
            val bg by animateColorAsState(if (sel) Ink.brass else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(Shapes.chip).background(bg).clickable { onSelect(o) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = Type.label, color = if (sel) Ink.onBrass else Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun RowScope.Cell(label: String, value: String, color: Color = Ink.text) {
    Column(Modifier.weight(1f)) {
        Text(value, style = Type.number, color = color, maxLines = 1)
        Text(label, style = Type.small, color = Ink.muted, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

val Pill = RoundedCornerShape(50)
