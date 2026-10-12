package com.dmwnezes.soberano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.sim.BuildingType
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.InsurgentKind
import com.dmwnezes.soberano.sim.Law
import com.dmwnezes.soberano.sim.SpeechResult
import com.dmwnezes.soberano.sim.SpeechTone
import com.dmwnezes.soberano.sim.SpeechTopic

// ================================================================ corrida espacial

@Composable
fun SpaceSection(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val sp = c.sim.space
    val g = c.game
    SectionTitle("Programa espacial", trailing = { Text("${me.satellites} satélites", style = Type.label, color = Ink.brass) })
    Text("Cada satélite dá +3% em combate, mais pesquisa e espiões melhores.", style = Type.small, color = Ink.muted)
    Spacer(Modifier.height(6.dp))
    val satWhy = sp.satRefusal(me)
    ActionRow(GameIcons.of("satellite"), if (me.satBuild > 0) "Foguete na plataforma" else "Lançar satélite",
        if (me.satBuild > 0) "Lança em ${me.satBuild} dias" else satWhy ?: "${Fmt.money(sp.satCost(me))}, pronto em ${sp.satDays} dias",
        satWhy == null, active = me.satBuild > 0) { c.act("Foguete em montagem") { sp.launchSatellite(me) } }
    if (me.spaceStage < 2) {
        val why = sp.stageRefusal(me)
        val name = sp.stageName(me.spaceStage)
        val firstBy = if (me.spaceStage == 0) g.stationFirst else g.moonFirst
        ActionRow(GameIcons.of("rocket"), if (me.spaceBuild > 0) "$name em andamento" else name,
            if (me.spaceBuild > 0) "Faltam ${me.spaceBuild} dias" else (why ?: "${Fmt.money(sp.stageCost(me))}. Prestígio, estabilidade e pesquisa") +
                if (firstBy >= 0) " · ${c.sim.nation(firstBy).name} chegou primeiro" else " · ninguém chegou ainda",
            why == null, active = me.spaceBuild > 0) { c.act("Missão iniciada") { sp.startStage(me) } }
    } else Text("Você já pisou na Lua.", style = Type.small, color = Ink.good, modifier = Modifier.padding(top = 4.dp))
    val leaders = g.nations.filter { it.alive && (it.satellites > 0 || it.spaceStage > 0) }.sortedWith(compareByDescending<com.dmwnezes.soberano.sim.Nation> { it.spaceStage }.thenByDescending { it.satellites }).take(5)
    if (leaders.isNotEmpty()) {
        Text("Corrida espacial", style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        for (n in leaders) Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagView(n, 16.dp); Spacer(Modifier.width(8.dp))
            Text(n.name, style = Type.small, color = if (n.id == me.id) Ink.brass else Ink.text, modifier = Modifier.weight(1f))
            Text(listOf("", "estação · ", "Lua · ")[n.spaceStage] + "${n.satellites} sat.", style = Type.small, color = Ink.muted)
        }
    }
}

// ================================================================ bloqueio naval

@Composable
fun BlockadeRow(c: GameController, enemy: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val tr = c.sim.trade
    val me = c.me
    val on = tr.blockading(me.id, enemy)
    val why = if (on) null else tr.blockadeRefusal(me, enemy)
    ActionRow(GameIcons.of("anchor"), if (on) "Bloqueio naval ativo" else "Bloquear os portos deles",
        if (on) "Comércio deles cortado. Toque para suspender" else why ?: "Corta o comércio e o crescimento deles",
        why == null, active = on) { c.act(if (on) "Bloqueio suspenso" else "Portos bloqueados") { tr.toggleBlockade(me, enemy) } }
}

// ================================================================ cartão da província: insurgência e obras

@Composable
fun ProvinceExtras(c: GameController, p: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val sim = c.sim
    val me = c.me
    val ins = sim.insurgency.at(p)
    if (ins != null) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(Shapes.tile).background(Ink.coral.copy(alpha = 0.12f)).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(GameIcons.of(ins.kind.icon), null, tint = Ink.coral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(ins.name, style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${ins.kind.label} · força ${Fmt.one(ins.strength)}", style = Type.small, color = Ink.coral)
                }
            }
            if (g.controller[p] == me.id) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Operação (${Fmt.money(sim.insurgency.operationCost(me))})", Modifier.weight(1.3f), BtnKind.DANGER) { c.act { sim.insurgency.operation(me, ins) } }
                if (ins.kind != InsurgentKind.TERROR) Btn("Negociar ⚡${sim.insurgency.negotiateCost.toInt()}", Modifier.weight(1f)) { c.act { sim.insurgency.negotiate(me, ins) } }
            }
        }
    }
    val list = sim.buildings.at(p)
    if (list.isNotEmpty() || (g.owner[p] == me.id && g.controller[p] == me.id)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (b in list) Box(
                    Modifier.size(28.dp).clip(CircleShape).background(if (b.daysLeft > 0) Ink.raised else Ink.brass.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(GameIcons.of(b.type.icon), b.type.label, tint = if (b.daysLeft > 0) Ink.muted else Ink.brass, modifier = Modifier.size(16.dp)) }
                if (list.isEmpty()) Text("Sem obras neste estado", style = Type.small, color = Ink.faint)
            }
            if (g.owner[p] == me.id && g.controller[p] == me.id)
                Text("Construir", style = Type.label, color = Ink.verdigris,
                    modifier = Modifier.clip(Shapes.chip).clickable { c.dialog = "build" }.padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

@Composable
fun BuildDialog(c: GameController, p: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val b = c.sim.buildings
    val pr = c.sim.world.provinces[p]
    Overlay({ c.dialog = null }) {
        Text("Obras em ${pr.name}", style = Type.heading, color = Ink.text)
        Text("Tesouro ${Fmt.money(me.treasury)}", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.height(430.dp).verticalScroll(rememberScrollState())) {
            for (t in BuildingType.entries) {
                val existing = b.at(p).firstOrNull { it.type == t }
                val why = b.refusal(me, p, t)
                val sub = when {
                    existing != null && existing.daysLeft > 0 -> "Em obra: faltam ${existing.daysLeft} dias"
                    existing != null -> "Pronto"
                    else -> "${Fmt.money(b.cost(me, t))} · ${t.days} dias · ${t.desc}" + (why?.takeIf { !it.startsWith("Tesouro") }?.let { " — $it" } ?: "")
                }
                ActionRow(GameIcons.of(t.icon), t.label, sub, why == null, active = existing != null) {
                    c.act("Obra iniciada: ${t.label}") { b.build(me, p, t) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Btn("Fechar", Modifier.fillMaxWidth(), BtnKind.GHOST) { c.dialog = null }
    }
}

// ================================================================ congresso

@Composable
fun GovCongress(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val g = c.game
    val cg = c.sim.congress
    SheetBody {
        Spacer(Modifier.height(8.dp))
        if (!me.regime.congress) {
            Text("Seu regime não tem Congresso: você governa por decreto.", style = Type.body, color = Ink.muted)
            return@SheetBody
        }
        val coal = cg.coalitionSeats()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("Base do governo", "$coal de ${cg.totalSeats}", if (coal > cg.totalSeats / 2) Ink.good else Ink.coral, Modifier.weight(1f))
            BigStat("Apoio", "${me.congress.toInt()}%", Ink.scale(me.congress), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        // plenário: barra com as cadeiras de cada partido (base primeiro)
        val ordered = g.parties.sortedByDescending { it.inCoalition }
        Row(Modifier.fillMaxWidth().height(16.dp).clip(Shapes.chip)) {
            for (p in ordered) Box(Modifier.weight(p.seats.toFloat().coerceAtLeast(1f)).height(16.dp).background(Color(p.ideology.color).copy(alpha = if (p.inCoalition) 1f else 0.45f)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("governo", style = Type.small, color = Ink.muted, modifier = Modifier.weight(1f))
            Text("oposição", style = Type.small, color = Ink.muted)
        }
        Text("Maioria: ${cg.totalSeats / 2 + 1} cadeiras. As leis passam por votação aqui.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 4.dp))
        SectionTitle("Partidos")
        for (p in g.parties.sortedByDescending { it.seats }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(p.ideology.color)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${p.name} (${p.short})", style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.ideology.label} · ${p.seats} cadeiras · humor ${p.mood.toInt()}", style = Type.small, color = Ink.muted)
                    }
                    Text(if (p.inCoalition) "base" else "oposição", style = Type.small, color = if (p.inCoalition) Ink.good else Ink.faint)
                }
                Spacer(Modifier.height(8.dp))
                if (p.inCoalition) Btn("Romper com o partido", Modifier.fillMaxWidth(), BtnKind.GHOST) { c.act { cg.dropParty(p) } }
                else Btn("Oferecer cargos (⚡${cg.postsCost.toInt()})", Modifier.fillMaxWidth(), BtnKind.SECONDARY, Icons.Rounded.Handshake,
                    enabled = me.politicalCapital >= cg.postsCost) { c.act { cg.offerPosts(p) } }
            }
        }
        Text("Oferecer cargos traz votos, mas aumenta a corrupção.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Previsão da votação de uma lei (para cartões de lei). */
@Composable
fun VoteForecast(c: GameController, law: Law) {
    if (!c.sim.congress.active() || c.me.regime.decree) return
    val (yes, no) = c.sim.congress.tally(law)
    val ok = yes > no
    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(Shapes.tile).background(Ink.raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(GameIcons.of("congress"), null, tint = if (ok) Ink.good else Ink.coral, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (ok) "Deve passar no Congresso" else "Deve ser rejeitada", style = Type.label, color = if (ok) Ink.good else Ink.coral)
            Text("Previsão: $yes sim × $no não" + if (!ok) " · ofereça cargos a partidos" else "", style = Type.small, color = Ink.muted)
        }
    }
}

// ================================================================ pronunciamento

@Composable
fun SpeechButton(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val wait = c.sim.speeches.waitDays(c.me)
    ActionRow(GameIcons.of("speech"), "Pronunciamento à nação", if (wait > 0) "Próximo em $wait dias" else "Fale ao país em rede nacional", wait == 0) { c.dialog = "speech" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechDialog(c: GameController) {
    var topic by remember { mutableStateOf(SpeechTopic.UNITY) }
    var tone by remember { mutableStateOf(SpeechTone.CALM) }
    var result by remember { mutableStateOf<SpeechResult?>(null) }
    Overlay({ c.dialog = null }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Ink.brass.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(GameIcons.of("speech"), null, tint = Ink.brass, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Pronunciamento", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        }
        val r = result
        if (r == null) {
            Text("Tema", style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in SpeechTopic.entries) {
                    val sel = t == topic
                    Row(Modifier.clip(Shapes.chip).background(if (sel) Ink.brass else Ink.raised).clickable { topic = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(GameIcons.of(t.icon), null, tint = if (sel) Ink.onBrass else Ink.muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, style = Type.label, color = if (sel) Ink.onBrass else Ink.text)
                    }
                }
            }
            Text("Tom", style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Segmented(SpeechTone.entries, tone, { it.label }) { tone = it }
            Text(tone.desc, style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { c.dialog = null }
                Btn("Ir ao ar", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.Mic) {
                    result = c.sim.speeches.give(c.me, topic, tone); c.refresh()
                    if (result == null) c.dialog = null
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(r.headline, style = Type.heading, color = when (r.reception) { 2 -> Ink.good; 1 -> Ink.amber; else -> Ink.coral })
            Text(when (r.reception) { 2 -> "O país gostou."; 1 -> "Reação morna."; else -> "Pegou mal." }, style = Type.small, color = Ink.muted)
            Spacer(Modifier.height(8.dp))
            for ((t, good) in r.effects) Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (good) Ink.good else Ink.coral))
                Spacer(Modifier.width(8.dp))
                Text(t, style = Type.body, color = Ink.text)
            }
            if (r.effects.isEmpty()) Text("Quase nada mudou.", style = Type.body, color = Ink.muted)
            Spacer(Modifier.height(14.dp))
            Btn("Fechar", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { c.dialog = null }
        }
    }
}

// ================================================================ câmbio

@Composable
fun ColumnScope.CurrencySection(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val cur = c.sim.currency
    SectionTitle("Câmbio", trailing = { Text(cur.rateText(me), style = Type.label, color = Ink.brass) })
    val idx = me.currency
    val label = when { idx < 0.6f -> "Em crise"; idx < 0.85f -> "Desvalorizada"; idx < 1.15f -> "Estável"; else -> "Valorizada" }
    Row {
        Cell("Moeda", label, when { idx < 0.6f -> Ink.coral; idx < 0.85f -> Ink.amber; idx < 1.15f -> Ink.text; else -> Ink.good })
        Cell("Índice", "${(idx * 100).toInt()}")
        Cell("Tendência", if (cur.target(me) > idx + 0.02f) "subindo" else if (cur.target(me) < idx - 0.02f) "caindo" else "parada",
            if (cur.target(me) >= idx) Ink.good else Ink.coral)
    }
    Text("Moeda fraca ajuda a exportar, mas traz inflação e encarece a dívida em dólar. Juros altos e contas em ordem a fortalecem.",
        style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(8.dp))
    Btn("Vender reservas para segurar a moeda (${Fmt.money(cur.interventionCost(me))})", Modifier.fillMaxWidth(), BtnKind.GHOST, GameIcons.of("currency"),
        enabled = me.treasury >= cur.interventionCost(me)) { c.act("Banco central interveio no câmbio") { cur.intervene(me) } }
}
