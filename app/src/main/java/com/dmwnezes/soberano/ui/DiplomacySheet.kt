package com.dmwnezes.soberano.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Nation
import com.dmwnezes.soberano.sim.PeaceTerms
import com.dmwnezes.soberano.sim.SpyMission
import com.dmwnezes.soberano.sim.TreatyType

@Composable
fun DiplomacySheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val f = c.focusNation
    if (f >= 0 && f != c.game.player && c.game.nations[f].alive) NationDetail(c, c.game.nations[f]) else NationList(c)
}

@Composable
private fun NationList(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val me = g.player
    var q by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val neighbors = c.sim.ai.neighborsOf(me)
    SheetBody {
        SheetTitle("Diplomacia", "Toque num país no mapa ou na lista")
        if (g.proposals.isNotEmpty()) {
            SectionTitle("Propostas para você")
            for (p in g.proposals.toList()) ProposalRow(c, p)
        }
        Spacer(Modifier.height(10.dp))
        SearchField(q, "Buscar país", Modifier.fillMaxWidth()) { q = it }
        val all = g.nations.filter { it.alive && it.id != me }
        Spacer(Modifier.height(8.dp))
        if (q.isNotBlank()) {
            for (n in all.filter { strip(it.name.lowercase()).contains(strip(q.lowercase())) }.take(40)) NationRow(c, n)
        } else {
            val war = all.filter { c.sim.atWar(me, it.id) }
            val ally = all.filter { c.sim.allied(me, it.id) && it !in war }
            val near = all.filter { it.id in neighbors && it !in war && it !in ally }
            val big = all.sortedByDescending { g.natGdp(it.id) }.filter { it !in war && it !in ally && it !in near }.take(12)
            for ((label, list) in listOf("Em guerra" to war, "Aliados" to ally, "Vizinhos" to near, "Potências" to big)) {
                if (list.isEmpty()) continue
                Text(label, style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                for (n in list) NationRow(c, n)
            }
        }

        SectionTitle("Blocos", trailing = { Btn("Criar", kind = BtnKind.GHOST, icon = Icons.Rounded.Add) { creating = true } })
        for (b in g.blocs) {
            val member = me in b.members
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(Color(b.color.toInt()), 12.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = Type.body, color = Ink.text)
                    Text("${b.members.size} membros, " + listOfNotNull(if (b.military) "militar" else null, if (b.economic) "econômico" else null).joinToString(" e "), style = Type.small, color = Ink.muted)
                }
                if (member) Btn("Sair", kind = BtnKind.GHOST) { c.sim.diplomacy.leave(b, me); c.refresh() }
                else {
                    val why = c.sim.diplomacy.joinRefusal(b, me)
                    Btn("Entrar", enabled = why == null) { c.act("Você entrou em ${b.name}") { c.sim.diplomacy.invite(b, me) } }
                }
            }
        }
    }
    if (creating) CreateBlocDialog(c) { creating = false }
}

@Composable
fun ProposalRow(c: GameController, p: com.dmwnezes.soberano.sim.Proposal) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val from = c.sim.nation(p.from)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FlagView(from, 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(from.name, style = Type.label, color = Ink.text)
            Text(proposalText(c, p), style = Type.small, color = if (p.kind == "DEMAND") Ink.coral else Ink.muted, maxLines = 2)
        }
        IconBtn(Icons.Rounded.Close, size = 32.dp, tint = Ink.coral) { declineProposal(c, p); c.refresh() }
        Spacer(Modifier.width(6.dp))
        IconBtn(Icons.Rounded.Check, size = 32.dp, bg = Ink.brass, tint = Ink.onBrass) { acceptProposal(c, p); c.refresh() }
    }
}

@Composable
private fun NationRow(c: GameController, n: Nation) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val me = g.player
    val r = g.rel(me, n.id)
    Row(
        Modifier.fillMaxWidth().clip(Shapes.tile).clickable { c.focusNation = n.id; c.refresh() }.padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagView(n, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.name, style = Type.body, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (c.sim.atWar(me, n.id)) Tag("guerra", Ink.coral)
                if (c.sim.allied(me, n.id)) Tag("aliado", Ink.verdigris)
                if (c.sim.hasTreaty(TreatyType.TRADE, me, n.id)) Tag("comércio", Ink.muted)
                if (c.sim.hasTreaty(TreatyType.GUARANTEE, me, n.id)) Tag("garantido", Ink.verdigris)
            }
        }
        RelationBadge(r)
    }
}

@Composable
private fun NationDetail(c: GameController, n: Nation) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val me = g.player
    val sim = c.sim
    val r = g.rel(me, n.id)
    var confirmWar by remember { mutableStateOf(false) }
    var spyResult by remember { mutableStateOf<String?>(null) }
    var pickUltimatum by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, size = 36.dp) { c.focusNation = -1; c.refresh() }
                Spacer(Modifier.width(12.dp))
                FlagView(n, 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.name, style = Type.title.copy(fontSize = 22.sp), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${n.regime.label}, ${n.personality.label.lowercase()}", style = Type.small, color = Ink.muted)
                }
                RelationBadge(r)
            }
            Spacer(Modifier.height(14.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    Row {
                        Cell("PIB", Fmt.money(g.natGdp(n.id)))
                        Cell("População", Fmt.pop(g.natPop(n.id)))
                        Cell("Aprovação", "${n.approval.toInt()}%", Ink.scale(n.approval))
                    }
                    Spacer(Modifier.height(12.dp))
                    val mine = sim.landPower(me); val theirs = sim.landPower(n.id)
                    Text("Força terrestre", style = Type.small, color = Ink.muted)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.chip)) {
                        val t = (mine + theirs).coerceAtLeast(1.0)
                        Box(Modifier.weight((mine / t).toFloat().coerceAtLeast(0.01f)).height(8.dp).background(Ink.brass))
                        Box(Modifier.weight((theirs / t).toFloat().coerceAtLeast(0.01f)).height(8.dp).background(Color(n.mapColor.toInt())))
                    }
                    Row { Text("Você ${mine.toInt()}", style = Type.small, color = Ink.brass, modifier = Modifier.weight(1f)); Text("${theirs.toInt()}", style = Type.small, color = Ink.muted) }
                    if (n.nukes > 0) Text("Possui armas nucleares", style = Type.small, color = Ink.coral, modifier = Modifier.padding(top = 6.dp))
                    val blocs = g.blocs.filter { n.id in it.members }
                    if (blocs.isNotEmpty()) Text("Blocos: " + blocs.joinToString { it.name }, style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }

            SectionTitle("Por que a relação está assim")
            for ((why, v) in sim.diplomacy.reasons(me, n.id).take(8)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(if (v >= 0) Ink.good else Ink.coral, 8.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(why, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                    Text((if (v >= 0) "+" else "") + v.toInt(), style = Type.number.copy(fontSize = 13.sp), color = if (v >= 0) Ink.good else Ink.coral)
                }
            }

            val war = sim.warBetween(me, n.id)
            if (war != null) {
                SectionTitle("Guerra")
                val score = sim.diplomacy.warScore(war, me)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pontuação", style = Type.label, color = Ink.muted, modifier = Modifier.weight(1f))
                    Text("${score.toInt()}", style = Type.number, color = if (score >= 0) Ink.good else Ink.coral)
                }
                Meter((score + 100) / 2, Modifier.fillMaxWidth().padding(vertical = 6.dp), if (score >= 0) Ink.good else Ink.coral)
                BlockadeRow(c, n.id)
                for (t in PeaceTerms.entries) {
                    val why = sim.diplomacy.peaceRefusal(war, me, t)
                    ActionRow(GameIcons.of("dove"), t.label, why ?: "Eles aceitam", why == null) { c.act("Paz assinada") { sim.diplomacy.proposePeace(war, me, t) } }
                }
                val allies = g.nations.filter { it.alive && it.id != me && sim.allied(me, it.id) && !war.involves(it.id) }
                if (allies.isNotEmpty()) {
                    Text("Chamar aliados", style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    for (a in allies.take(8)) ActionRow(Icons.Rounded.Campaign, "Pedir ajuda a ${a.name}", "relação ${g.rel(me, a.id).toInt()}", true) {
                        c.act("${a.name} entrou na guerra") { sim.diplomacy.callToArms(war, me, a.id) }
                    }
                }
            } else {
                SectionTitle("Acordos")
                for (t in listOf(TreatyType.TRADE, TreatyType.NON_AGGRESSION, TreatyType.PASSAGE, TreatyType.ALLIANCE, TreatyType.GUARANTEE)) {
                    val has = sim.hasTreaty(t, me, n.id)
                    if (has) ActionRow(Icons.Rounded.CheckCircle, t.label, "Ativo. Toque para cancelar", true, active = true) { sim.diplomacy.cancel(t, me, n.id); c.refresh() }
                    else {
                        val why = sim.diplomacy.refusal(t, me, n.id)
                        val sub = if (t == TreatyType.GUARANTEE) (why ?: "Se alguém atacar ${n.name}, você entra na guerra") else (why ?: "Eles aceitam")
                        ActionRow(if (t == TreatyType.GUARANTEE) Icons.Rounded.Shield else Icons.Rounded.Handshake, t.label, sub, why == null) {
                            c.act("${t.label} assinado") { sim.diplomacy.propose(t, me, n.id) }
                        }
                    }
                }
                val myBloc = g.blocs.firstOrNull { it.founder == me }
                if (myBloc != null && n.id !in myBloc.members) {
                    val why = sim.diplomacy.inviteRefusal(myBloc, n.id)
                    ActionRow(Icons.Rounded.Hub, "Convidar para ${myBloc.name}", why ?: "Eles aceitam", why == null) { c.act("${n.name} entrou no bloco") { sim.diplomacy.invite(myBloc, n.id) } }
                }
                val border = sim.diplomacy.borderProvinces(me, n.id)
                if (border.isNotEmpty()) ActionRow(Icons.Rounded.Gavel, "Ultimato", "Exigir uma província da fronteira; só aceitam se forem muito mais fracos", true, danger = true) { pickUltimatum = true }
            }
            SectionTitle("Relações")
            ActionRow(Icons.Rounded.Flight, "Missão diplomática", "+6 relação, custa ⚡3", c.me.politicalCapital >= 3f) { c.act { sim.diplomacy.envoy(me, n.id) } }
            val gift = (g.natGdp(me) * 0.002).coerceAtLeast(0.2)
            ActionRow(Icons.Rounded.Redeem, "Enviar ajuda de ${Fmt.money(gift)}", "Melhora a relação", c.me.treasury >= gift) { c.act("Ajuda enviada") { sim.diplomacy.gift(me, n.id, gift) } }
            val sanctioned = g.treaties.any { it.type == TreatyType.SANCTION && it.a == me && it.b == n.id }
            val embargo = g.treaties.any { it.type == TreatyType.EMBARGO && it.a == me && it.b == n.id }
            ActionRow(Icons.Rounded.Block, if (sanctioned) "Retirar sanções" else "Aplicar sanções", "Corta o crescimento deles", true, active = sanctioned) { sim.diplomacy.sanction(me, n.id, false); c.refresh() }
            ActionRow(Icons.Rounded.DoNotDisturbOn, if (embargo) "Retirar embargo" else "Embargo total", "Corta comércio e metade das exportações de recursos", true, active = embargo) { sim.diplomacy.sanction(me, n.id, true); c.refresh() }

            TradeSection(c, n)

            SectionTitle("Espionagem")
            for (m in SpyMission.entries) {
                val ch = (sim.diplomacy.spyChance(me, n.id, m) * 100).toInt()
                ActionRow(Icons.Rounded.VisibilityOff, m.label, "$ch% de chance, ${Fmt.money(m.cost)}", c.me.treasury >= m.cost) { spyResult = sim.diplomacy.spy(me, n.id, m); c.refresh() }
            }
            spyResult?.let { Text(it, style = Type.label, color = Ink.brass, modifier = Modifier.padding(top = 6.dp)) }

            if (war == null) {
                Spacer(Modifier.height(18.dp))
                Btn("Declarar guerra", Modifier.fillMaxWidth(), BtnKind.DANGER, Icons.Rounded.Whatshot) { confirmWar = true }
                if (sim.hasTreaty(TreatyType.NON_AGGRESSION, me, n.id)) Text("Quebrar o pacto de não agressão custa estabilidade e reputação", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (confirmWar) {
            val resp = remember(n.id) { sim.diplomacy.allyResponse(me, n.id) }
            val guards = g.treaties.filter { it.type == TreatyType.GUARANTEE && it.b == n.id && it.a != me && it.a !in resp.fight }.map { it.a }
            Overlay({ confirmWar = false }) {
                Text("Guerra contra ${n.name}?", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
                Spacer(Modifier.height(8.dp))
                Text(allyText(c, com.dmwnezes.soberano.sim.Diplomacy.AllyResponse(resp.fight + guards, resp.aid, resp.sanction)), style = Type.body, color = Ink.muted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { confirmWar = false }
                    Btn("Declarar", Modifier.weight(1f), BtnKind.DANGER) { sim.diplomacy.declareWar(me, n.id); confirmWar = false; c.mapMode = MapMode.WAR; c.refresh() }
                }
            }
        }
        if (pickUltimatum) {
            val border = sim.diplomacy.borderProvinces(me, n.id).sortedByDescending { g.provPop[it] }
            Overlay({ pickUltimatum = false }) {
                Text("Exigir qual província?", style = Type.heading, color = Ink.text)
                Text("Se recusarem, a relação piora. Aí só pela guerra.", style = Type.small, color = Ink.muted)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
                    for (p in border) {
                        Row(Modifier.fillMaxWidth().clip(Shapes.small).clickable {
                            val (_, msg) = sim.diplomacy.ultimatum(me, n.id, p)
                            c.say(msg, if (msg.startsWith("Aceitaram")) 1 else 2, "gavel"); pickUltimatum = false; c.refresh()
                        }.padding(10.dp)) {
                            Text(sim.world.provinces[p].name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                            Text(Fmt.pop(g.provPop[p]), style = Type.small, color = Ink.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateBlocDialog(c: GameController, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var name by remember { mutableStateOf("") }
    var military by remember { mutableStateOf(true) }
    var economic by remember { mutableStateOf(true) }
    Overlay(onClose) {
        Text("Novo bloco", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        Spacer(Modifier.height(12.dp))
        SearchField(name, "Nome do bloco", Modifier.fillMaxWidth()) { if (it.length <= 30) name = it }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Aliança militar", military) { military = it }
        ToggleRow("Comércio", economic) { economic = it }
        Spacer(Modifier.height(12.dp))
        Btn("Fundar", Modifier.fillMaxWidth(), BtnKind.PRIMARY, enabled = name.isNotBlank() && (military || economic)) {
            c.sim.diplomacy.createBloc(c.game.player, name.trim(), military, economic); onClose(); c.refresh()
        }
    }
}

// ---------------------------------------------------------------- propostas

fun proposalText(c: GameController, p: com.dmwnezes.soberano.sim.Proposal): String = when (p.kind) {
    "PEACE_WHITE" -> "propõe paz branca"
    "BLOC" -> "convida você para ${c.game.blocs.firstOrNull { it.id == p.value.toInt() }?.name ?: "um bloco"}"
    "JOIN_WAR" -> "pede que você entre na ${c.game.wars.firstOrNull { it.id == p.value.toInt() }?.name ?: "guerra"}"
    "DEMAND" -> "exige ${c.sim.world.provinces.getOrNull(p.value.toInt())?.name ?: "uma província"}, ou guerra"
    else -> if (p.kind.startsWith("BUY_RES:")) {
        val r = com.dmwnezes.soberano.sim.Resource.valueOf(p.kind.substringAfter(":"))
        val yearly = p.value * c.game.prices[r.ordinal] * c.sim.dev.dealPremium
        "quer comprar ${r.label.lowercase()}: ${Fmt.money(p.value)}/ano por ${c.sim.dev.dealYears} anos (rende ${Fmt.money(yearly / 12 * 0.5)}/mês)"
    } else if (p.kind.startsWith("BUY_ARMS:")) {
        val t = com.dmwnezes.soberano.sim.UnitType.valueOf(p.kind.substringAfter(":"))
        "quer comprar ${p.value.toInt()} ${t.label.lowercase()} por ${Fmt.money(c.sim.dev.armsPrice(t, p.value.toInt()))}"
    } else proposalTextBasic(p)
}

private fun proposalTextBasic(p: com.dmwnezes.soberano.sim.Proposal): String = when (p.kind) {
    else -> runCatching { "propõe ${TreatyType.valueOf(p.kind).label.lowercase()}" }.getOrDefault(p.kind)
}

fun acceptProposal(c: GameController, p: com.dmwnezes.soberano.sim.Proposal) {
    val g = c.game
    val sim = c.sim
    g.proposals.remove(p)
    when (p.kind) {
        "PEACE_WHITE" -> g.wars.firstOrNull { it.id == p.value.toInt() }?.let { sim.diplomacy.makePeace(it, p.from, PeaceTerms.WHITE) }
        "BLOC" -> g.blocs.firstOrNull { it.id == p.value.toInt() }?.let { b ->
            if (g.player !in b.members) { b.members += g.player; g.treatyCache = null; sim.feed("bloc", "Você entrou em ${b.name}", 1) }
        }
        "JOIN_WAR" -> g.wars.firstOrNull { it.id == p.value.toInt() }?.let { w -> sim.diplomacy.joinWar(w, g.player, w.side(p.from)) }
        "BUY_RES", "BUY_ARMS" -> {}
        "DEMAND" -> {
            val prov = p.value.toInt()
            if (g.owner.getOrNull(prov) == g.player) {
                sim.diplomacy.transfer(prov, p.from)
                sim.diplomacy.evacuateIllegalArmies()
                g.addRel(p.from, g.player, 10f)
                sim.feed("flag", "Você entregou ${sim.world.provinces[prov].name}", 2)
            }
        }
        else -> when {
            p.kind.startsWith("BUY_RES:") -> {
                val r = com.dmwnezes.soberano.sim.Resource.valueOf(p.kind.substringAfter(":"))
                sim.dev.sellResource(g.player, p.from, r, p.value.toFloat())?.let { c.say(it, 2) }
            }
            p.kind.startsWith("BUY_ARMS:") -> {
                val t = com.dmwnezes.soberano.sim.UnitType.valueOf(p.kind.substringAfter(":"))
                sim.dev.sellArms(g.player, p.from, t, p.value.toInt())?.let { c.say(it, 2) }
            }
            else -> runCatching { TreatyType.valueOf(p.kind) }.getOrNull()?.let { t ->
                if (!sim.hasTreaty(t, p.from, g.player)) sim.diplomacy.sign(t, p.from, g.player)
            }
        }
    }
    sim.touch()
}

fun declineProposal(c: GameController, p: com.dmwnezes.soberano.sim.Proposal) {
    val g = c.game
    g.proposals.remove(p)
    if (p.kind == "DEMAND") {
        g.addRel(p.from, g.player, -20f)
        // recusar um ultimato pode virar guerra na hora
        if (c.sim.chance(0.5)) c.sim.diplomacy.declareWar(p.from, g.player)
    } else g.addRel(p.from, g.player, -3f)
    c.sim.touch()
}
