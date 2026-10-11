package com.dmwnezes.soberano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.sim.Content
import com.dmwnezes.soberano.sim.CustomLaw
import com.dmwnezes.soberano.sim.LawAnalysis
import com.dmwnezes.soberano.sim.LawMaker
import com.dmwnezes.soberano.sim.Fmt

private const val MAX_MEASURES = 5

/** Tela para montar uma lei a partir de medidas e ver as consequências antes de aprovar. */
@Composable
fun LawEditor(c: GameController, onClose: () -> Unit) {
    val me = c.me
    val chosen = remember { mutableStateListOf<String>() }
    var name by remember { mutableStateOf("") }
    var intensity by remember { mutableIntStateOf(2) }
    var category by remember { mutableStateOf(LawMaker.categories.first()) }
    val draft = CustomLaw("custom_rascunho", name.ifBlank { LawMaker.suggestName(chosen) }, chosen.toList(), intensity)
    val law = LawMaker.build(draft)
    val analysis = LawMaker.analyze(law, me, c.game.natGdp(me.id))
    val cost = c.sim.politics.lawCost(me, law, true)

    Box(Modifier.fillMaxSize().background(Ink.panelSolid).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Nova lei", style = Type.title, color = Ink.text)
                    Text("Escolha até $MAX_MEASURES medidas e veja o que acontece", style = Type.small, color = Ink.muted)
                }
                IconBtn(Icons.Rounded.Close, onClick = onClose)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SearchField(name, LawMaker.suggestName(chosen).ifEmpty { "Nome da lei" }, Modifier.fillMaxWidth()) { if (it.length <= 40) name = it }
                Spacer(Modifier.height(12.dp))
                Text("Intensidade", style = Type.label, color = Ink.muted)
                Spacer(Modifier.height(6.dp))
                Segmented(LawMaker.intensities.map { it.first }, intensity, { i -> LawMaker.intensities.first { it.first == i }.second }) { intensity = it }

                // ---- medidas
                SectionTitle("Medidas", trailing = { Text("${chosen.size}/$MAX_MEASURES", style = Type.number.copy(fontSize = 14.sp), color = Ink.brass) })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (cat in LawMaker.categories) {
                        val sel = cat == category
                        val count = chosen.count { LawMaker.byId[it]?.category == cat }
                        Text(
                            cat + if (count > 0) " ($count)" else "", style = Type.label, color = if (sel) Ink.onBrass else Ink.muted,
                            modifier = Modifier.clip(Shapes.chip).background(if (sel) Ink.brass else Ink.raised)
                                .clickable { category = cat }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                for (m in LawMaker.measures.filter { it.category == category }) {
                    val on = m.id in chosen
                    val full = !on && chosen.size >= MAX_MEASURES
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile)
                            .background(if (on) Ink.brass.copy(alpha = 0.14f) else Ink.raised)
                            .border(if (on) 1.5.dp else 0.dp, if (on) Ink.brass else Color.Transparent, Shapes.tile)
                            .clickable(enabled = !full) { if (on) chosen.remove(m.id) else chosen.add(m.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(GameIcons.of(m.icon), null, tint = if (on) Ink.brass else Ink.muted, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (full) Ink.faint else Ink.text)
                            Text(m.does, style = Type.small, color = Ink.muted)
                        }
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(if (on) Ink.brass else Color.Transparent).border(1.5.dp, if (on) Ink.brass else Ink.line, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Icon(Icons.Rounded.Check, null, tint = Ink.onBrass, modifier = Modifier.size(16.dp)) }
                    }
                }

                // ---- o que a lei faz e consequências
                if (chosen.isNotEmpty()) {
                    SectionTitle("O que ela faz")
                    for (id in chosen) {
                        val m = LawMaker.byId[id] ?: continue
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Icon(GameIcons.of(m.icon), null, tint = Ink.brass, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(m.does, style = Type.body, color = Ink.text)
                        }
                    }
                    Consequences(analysis)
                    Spacer(Modifier.height(12.dp))
                    Panel(Modifier.fillMaxWidth()) {
                        Column {
                            Row {
                                Cell("Aprovação", (if (analysis.approvalDelta >= 0) "+" else "") + Fmt.one(analysis.approvalDelta), if (analysis.approvalDelta >= 0) Ink.good else Ink.coral)
                                Cell("Custo para aprovar", "⚡${cost.toInt()}", if (me.politicalCapital >= cost) Ink.brass else Ink.coral)
                                Cell("Seu capital", "⚡${me.politicalCapital.toInt()}")
                            }
                            if (me.regime.congress && me.congress < 50f)
                                Text("O Congresso está contra você: aprovar sai mais caro.", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Salvar rascunho", Modifier.weight(1f), BtnKind.GHOST, enabled = chosen.isNotEmpty()) {
                    val cl = LawMaker.create(c.game, name, chosen.toList(), intensity)
                    c.say("Lei \"${cl.name}\" guardada", 1, "law"); onClose()
                }
                Btn("Aprovar agora", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.Gavel, enabled = chosen.isNotEmpty() && me.politicalCapital >= cost) {
                    val cl = LawMaker.create(c.game, name, chosen.toList(), intensity)
                    if (!c.sim.politics.toggleLaw(me, cl.id)) c.say("Capital político insuficiente; a lei ficou guardada", 2)
                    onClose()
                }
            }
        }
    }
}

/** Lista de consequências com cores e o resumo de quem apoia e quem rejeita. */
@Composable
private fun ColumnScope.Consequences(a: LawAnalysis) {
    SectionTitle("Consequências")
    Text(a.summary, style = Type.body, color = Ink.text)
    Spacer(Modifier.height(8.dp))
    if (a.supporters.isNotEmpty() || a.opponents.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (a.supporters.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbUp, null, tint = Ink.good, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                for (g in a.supporters) Icon(GameIcons.group(g), g.label, tint = Ink.good, modifier = Modifier.size(18.dp).padding(horizontal = 1.dp))
            }
            if (a.opponents.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ThumbDown, null, tint = Ink.coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                for (g in a.opponents) Icon(GameIcons.group(g), g.label, tint = Ink.coral, modifier = Modifier.size(18.dp).padding(horizontal = 1.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    for (con in a.consequences) {
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (con.good) Ink.good else Ink.coral))
            Spacer(Modifier.width(10.dp))
            Text(con.text, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
        }
    }
    if (a.consequences.isEmpty()) Text("Nenhum efeito relevante.", style = Type.small, color = Ink.muted)
}

/** Explicação de qualquer lei: o que faz, quem apoia, quem rejeita e o efeito nos números. */
@Composable
fun LawInfo(c: GameController, id: String, onClose: () -> Unit) {
    val law = Content.law(id) ?: run { onClose(); return }
    val me = c.me
    val active = id in me.laws
    val analysis = LawMaker.analyze(law, me, c.game.natGdp(me.id))
    val custom = c.game.customLaws.firstOrNull { it.id == id }
    Overlay(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Ink.brass.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(GameIcons.of(law.icon), null, tint = Ink.brass, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(law.name, style = Type.heading.copy(fontWeight = FontWeight.ExtraBold), color = Ink.text)
                Text(if (active) "Em vigor" else "Fora de vigor", style = Type.small, color = if (active) Ink.good else Ink.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            if (custom != null) {
                Text("O que ela faz", style = Type.label, color = Ink.muted)
                for (mid in custom.measures) LawMaker.byId[mid]?.let { m ->
                    Text("• ${m.does}", style = Type.body, color = Ink.text, modifier = Modifier.padding(vertical = 2.dp))
                }
                Text("Intensidade: ${LawMaker.intensities.first { it.first == custom.intensity }.second.lowercase()}", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 4.dp))
            } else Text(law.desc, style = Type.body, color = Ink.text)
            if (law.decreeOnly) Text("Só pode ser aprovada por decreto (ditadura ou monarquia absoluta).", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 6.dp))
            Consequences(analysis)
            law.excludes?.let { ex -> Content.law(ex)?.let { Text("Substitui: ${it.name}", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 8.dp)) } }
            if (law.onEnact.isNotEmpty()) Text("Ao aprovar: " + law.onEnact.joinToString { c.sim.events.label(it, me) }, style = Type.small, color = Ink.good, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        val cost = c.sim.politics.lawCost(me, law, !active).toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (custom != null && !active) IconBtn(Icons.Rounded.Delete, tint = Ink.coral) { LawMaker.delete(c.game, id); c.say("Lei apagada", 0, "law"); onClose() }
            Btn(if (active) "Revogar (⚡$cost)" else "Aprovar (⚡$cost)", Modifier.weight(1f), if (active) BtnKind.SECONDARY else BtnKind.PRIMARY) {
                c.act(if (active) "Lei revogada" else "Lei aprovada") { if (c.sim.politics.toggleLaw(me, id)) null else c.sim.politics.canEnact(me, law) ?: "Não foi possível" }
                onClose()
            }
        }
    }
}
