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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.NUKE_COST
import com.dmwnezes.soberano.sim.Nation
import com.dmwnezes.soberano.sim.TechBranch
import com.dmwnezes.soberano.sim.UnitType

// ================================================================ MILITAR

@Composable
fun MilitarySheet(c: GameController, onNuke: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val tab = c.subTab[Tab.MILITARY] ?: 0
    val me = c.me
    val b = c.sim.economy.budget(me)
    Column(Modifier.fillMaxSize()) {
        SheetHeaderTabs("Forças armadas", "Fundo ${Fmt.money(me.militaryFund)}, manutenção ${Fmt.money(b.upkeep)}/mês",
            listOf("Tropas", "Recrutar", "Comando", "Automático", "Tecnologia"), tab) { c.subTab[Tab.MILITARY] = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> MilTroops(c)
                1 -> MilRecruit(c)
                2 -> MilCommand(c)
                3 -> MilAuto(c)
                else -> MilTech(c, onNuke)
            }
        }
    }
}

@Composable
private fun MilTroops(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val sim = c.sim
    val armies = sim.armiesOf(me.id)
    Box(Modifier.fillMaxSize()) {
        SheetBody {
            Spacer(Modifier.height(8.dp))
            val divs = armies.filter { it.domain == Domain.LAND }.sumOf { it.divisions().toDouble() }
            val ships = armies.filter { it.domain == Domain.SEA }.sumOf { it.total().toDouble() }
            val planes = armies.filter { it.domain == Domain.AIR }.sumOf { it.total().toDouble() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStat("Divisões", Fmt.units(divs.toFloat()), Ink.text, Modifier.weight(1f))
                BigStat("Navios", Fmt.units(ships.toFloat()), Ink.text, Modifier.weight(1f))
                BigStat("Aviões", Fmt.units(planes.toFloat()), Ink.text, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Reunir tropas", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.CallMerge) { c.dialog = "gather" }
                Btn("Juntar paradas", Modifier.weight(1f), icon = Icons.Rounded.MergeType) {
                    var n = 0
                    for (a in sim.armiesOf(me.id).toList()) if (g.armies.contains(a) && !a.moving && !a.battle) {
                        val before = g.armies.size; sim.military.mergeAllAt(a); n += before - g.armies.size
                    }
                    c.say(if (n > 0) "$n grupos juntados" else "Nada para juntar", if (n > 0) 1 else 0, "military")
                }
            }
            Spacer(Modifier.height(8.dp))
            Btn("Dividir igualmente pelas províncias", Modifier.fillMaxWidth(), BtnKind.GHOST, Icons.Rounded.GridView) { c.dialog = "spread" }
            ToggleRow("Juntar automaticamente", g.autoMerge, Icons.Rounded.AutoMode, "Exércitos parados no mesmo lugar viram um só") { g.autoMerge = it; c.refresh() }

            SectionTitle("Exércitos, frotas e alas")
            if (armies.isEmpty()) Text("Nenhuma tropa. Recrute na aba ao lado.", style = Type.body, color = Ink.muted)
            for (a in armies.sortedWith(compareBy({ it.domain.ordinal }, { -it.total() }))) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                        .clickable { c.selArmy = a.id; c.selProvince = -1; c.tab = null; c.focusRequest = a.province; c.refresh() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(GameIcons.domain(a.domain), null, tint = if (a.battle) Ink.coral else Ink.brass, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name + (a.general?.let { " · ${it.name.removePrefix("Gen. ")} (nív. ${it.level})" } ?: ""), style = Type.body, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sim.world.provinces[a.province].name + when {
                            a.battle -> ", em combate"
                            a.moving -> " → ${sim.world.provinces[a.path.last()].name}"
                            else -> ""
                        }, style = Type.small, color = if (a.battle) Ink.coral else Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Fmt.units((a.total() * 2).toInt() / 2f), style = Type.number, color = Ink.text)
                        Text("moral ${(a.morale * 100).toInt()}%", style = Type.small, color = Ink.scale(a.morale * 100))
                    }
                }
            }
        }
    }
}

/** Divide as divisões paradas igualmente entre as províncias (todas ou só a fronteira). */
@Composable
fun SpreadDialog(c: GameController, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val sim = c.sim
    val me = g.player
    var borderOnly by remember { mutableStateOf(false) }
    val idle = sim.armiesOf(me).filter { it.domain == Domain.LAND && !it.battle && !it.moving }
    val divs = UnitType.of(Domain.LAND).sumOf { t -> idle.sumOf { it.units[t.ordinal].toDouble() }.toInt() }
    val targets = remember(borderOnly, c.tick) { sim.military.distributionTargets(me, borderOnly) }
    val n = minOf(targets.size, divs)
    val each = if (n > 0) divs / n else 0
    val extra = if (n > 0) divs % n else 0
    Overlay(onClose) {
        Text("Dividir igualmente", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        Spacer(Modifier.height(6.dp))
        Text("As tropas terrestres paradas se espalham pelas províncias, cada parte saindo da tropa mais próxima.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(12.dp))
        Segmented(listOf(false, true), borderOnly, { if (it) "Só fronteira" else "Todas as províncias" }) { borderOnly = it }
        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cell("Divisões", "$divs")
                Text("÷", style = Type.heading, color = Ink.muted, modifier = Modifier.padding(horizontal = 6.dp))
                Cell("Províncias", "$n")
                Text("=", style = Type.heading, color = Ink.muted, modifier = Modifier.padding(horizontal = 6.dp))
                Cell("Em cada", if (extra > 0) "$each a ${each + 1}" else "$each", Ink.brass)
            }
        }
        if (divs == 0) Text(if (idle.isEmpty() && sim.armiesOf(me).any { it.domain == Domain.LAND }) "Todas as tropas estão marchando ou em combate. Pare alguma para dividir."
            else "Nenhuma tropa terrestre parada.", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 8.dp))
        if (divs < targets.size && divs > 0) Text("Há menos divisões que províncias: ${targets.size - divs} ficam sem tropa (as menos importantes).",
            style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 8.dp))
        if (targets.isNotEmpty() && targets.size < sim.provincesOf(me).size && !borderOnly)
            Text("Províncias que só se alcançam por mar ficam de fora.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { onClose() }
            Btn("Dividir", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.GridView, enabled = n > 0) {
                val (d, p) = sim.military.distributeEvenly(me, borderOnly)
                c.say(if (p > 0) "$d divisões espalhadas por $p províncias" else "Nenhuma tropa parada para dividir", if (p > 0) 1 else 2, "military")
                onClose(); c.refresh()
            }
        }
    }
}

/** Escolhe onde reunir todas as tropas terrestres paradas. */
@Composable
fun GatherDialog(c: GameController, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val sim = c.sim
    val me = g.player
    val mine = sim.provincesOf(me).filter { g.controller[it] == me }
    val front = mine.filter { p -> sim.world.provinces[p].neighbors.any { sim.atWar(me, g.controller[it]) } }
    val options = (listOfNotNull(c.me.capital.takeIf { g.controller[it] == me }, g.rallyPoint.takeIf { it >= 0 && g.controller[it] == me }) +
        front.sortedByDescending { g.provPop[it] } + mine.sortedByDescending { g.provPop[it] }).distinct()
    Overlay(onClose) {
        Text("Reunir tropas em", style = Type.heading, color = Ink.text)
        Text("Todos os exércitos terrestres parados marcham para lá e se juntam.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
            for (p in options) {
                val tag = when { p == c.me.capital -> "capital"; p == g.rallyPoint -> "ponto de reunião"; p in front -> "frente"; else -> null }
                Row(Modifier.fillMaxWidth().clip(Shapes.small).clickable {
                    val n = sim.military.gatherAt(me, p)
                    g.autoMerge = true
                    c.say(if (n > 0) "$n exércitos a caminho de ${sim.world.provinces[p].name}" else "Nenhum exército disponível", if (n > 0) 1 else 2, "flag", p)
                    onClose(); c.refresh()
                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(sim.world.provinces[p].name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                    if (tag != null) Tag(tag, if (tag == "frente") Ink.coral else Ink.brass)
                }
            }
        }
    }
}

@Composable
private fun MilRecruit(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val sim = c.sim
    var buildAt by remember { mutableIntStateOf(if (c.buildProvince >= 0 && g.controller[c.buildProvince] == me.id) c.buildProvince else me.capital) }
    var picking by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SheetBody {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.oceanDeep).clickable { picking = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Place, null, tint = Ink.brass)
                Spacer(Modifier.width(8.dp))
                Text("Em ${sim.world.provinces[buildAt].name}", style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                Text("trocar", style = Type.label, color = Ink.verdigris)
            }
            for (d in Domain.entries) {
                Text(when (d) { Domain.LAND -> "Exército"; Domain.SEA -> "Marinha"; Domain.AIR -> "Aeronáutica" }, style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                for (t in UnitType.of(d)) {
                    val cost = sim.military.unitCost(me, t)
                    val why = sim.military.canBuildAt(me, t, buildAt) ?: if (me.militaryFund + me.treasury < cost) "Sem dinheiro" else null
                    val queued = me.queue.count { it.type == t }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                            .clickable(enabled = why == null) { c.act("${t.label} encomendada") { sim.military.buy(me, t, buildAt) } }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.label + if (queued > 0) "  (+$queued)" else "", style = Type.body, color = if (why == null) Ink.text else Ink.faint)
                            Text(statLine(t), style = Type.small, color = Ink.muted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.money(cost), style = Type.number.copy(fontSize = 14.sp), color = if (why == null) Ink.brass else Ink.faint)
                            Text(why ?: "${t.buildDays} dias", style = Type.small, color = if (why == null) Ink.muted else Ink.coral)
                        }
                    }
                }
            }
            if (me.queue.isNotEmpty()) {
                SectionTitle("Em produção", trailing = { Text("${sim.military.productionSlots(me)} linhas", style = Type.small, color = Ink.muted) })
                for (o in me.queue.take(12)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(o.type.label, style = Type.label, color = Ink.text, modifier = Modifier.width(110.dp))
                        Meter((1f - o.daysLeft.toFloat() / o.total) * 100, Modifier.weight(1f), Ink.verdigris)
                        Spacer(Modifier.width(8.dp))
                        Text("${o.daysLeft}d", style = Type.small, color = Ink.muted)
                        IconBtn(Icons.Rounded.Close, size = 28.dp, bg = Color.Transparent, tint = Ink.faint) { sim.military.cancelOrder(me, o); c.refresh() }
                    }
                }
                if (me.queue.size > 12) Text("e mais ${me.queue.size - 12}", style = Type.small, color = Ink.muted)
            }
        }
        if (picking) {
            val provs = sim.provincesOf(me.id).filter { g.controller[it] == me.id }.sortedByDescending { g.provPop[it] }
            Overlay({ picking = false }) {
                Text("Onde recrutar", style = Type.heading, color = Ink.text)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                    for (p in provs) {
                        Row(Modifier.fillMaxWidth().clip(Shapes.small).clickable { buildAt = p; c.buildProvince = p; picking = false }.padding(10.dp)) {
                            Text(sim.world.provinces[p].name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                            if (sim.world.provinces[p].coastal) Icon(Icons.Rounded.Anchor, null, tint = Ink.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilAuto(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val sim = c.sim
    val dev = sim.dev
    val enemies = g.nations.filter { it.alive && sim.atWar(me.id, it.id) }
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("Piloto automático de guerra", g.autoWar, Icons.Rounded.SmartToy,
                    "Seus generais comandam as tropas e recrutam perto da frente") { g.autoWar = it; c.refresh() }
                if (g.autoWar && enemies.isEmpty()) Text("Fica esperando: só age quando você estiver em guerra.", style = Type.small, color = Ink.amber)
            }
        }
        if (enemies.isNotEmpty()) {
            SectionTitle("Alvo do ataque")
            TargetRow(c, null, g.autoTarget < 0 || !sim.atWar(me.id, g.autoTarget))
            for (e in enemies) TargetRow(c, e, g.autoTarget == e.id)
            Text("Os generais priorizam o alvo, mas defendem o seu território quando ele é invadido.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
        }
        SectionTitle("Reservistas")
        val rate = dev.recruitRate(me)
        val cap = dev.manpowerCap(me.id)
        val have = dev.landDivisions(me.id)
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Text("Cada estado treina reservistas conforme a população. Todos os países fazem isso.", style = Type.small, color = Ink.muted)
                Spacer(Modifier.height(10.dp))
                Row {
                    Cell("Por mês", "+${Fmt.one(rate)} div.", Ink.good)
                    Cell("Exército", "${have.toInt()} / ${cap.toInt()}", if (have >= cap) Ink.amber else Ink.text)
                    Cell("Em treino", "${Fmt.one(sim.dev.reservesInTraining(me.id) + me.reserves)} div.")
                }
                Spacer(Modifier.height(8.dp))
                Meter(have / cap * 100, Modifier.fillMaxWidth(), if (have >= cap) Ink.amber else Ink.verdigris)
                Spacer(Modifier.height(10.dp))
                val rp = g.rallyPoint.takeIf { it >= 0 && g.controller[it] == me.id }
                Text(if (rp == null) "Treinam em todos os seus estados ao mesmo tempo" else "Chegam em: ${sim.world.provinces[rp].name} (ponto de reunião)", style = Type.label, color = Ink.text)
                if (rp != null) Btn("Voltar a treinar em todos os estados", Modifier.fillMaxWidth().padding(top = 6.dp), BtnKind.GHOST) { c.game.rallyPoint = -1; c.refresh() }
                else Text("Estados com tropa parada recebem reforço todo mês; os outros formam uma divisão nova quando completam o treino.", style = Type.small, color = Ink.faint)
            }
        }
        Text("Defesa alta no orçamento, serviço militar obrigatório e guerra aceleram o treinamento.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun TargetRow(c: GameController, n: Nation?, selected: Boolean) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (selected) Ink.brass.copy(alpha = 0.14f) else Ink.raised)
            .clickable { c.game.autoTarget = n?.id ?: -1; c.refresh() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (n != null) FlagView(n, 20.dp) else Icon(Icons.Rounded.Public, null, tint = Ink.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(n?.name ?: "Todos os inimigos", style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
        if (n != null) Text("força ${c.sim.landPower(n.id).toInt()}", style = Type.small, color = Ink.muted)
        Spacer(Modifier.width(8.dp))
        Icon(if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null, tint = if (selected) Ink.brass else Ink.faint)
    }
}

@Composable
private fun MilTech(c: GameController, onNuke: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val sim = c.sim
    SheetBody {
        Spacer(Modifier.height(6.dp))
        Text("Toque num ramo para concentrar a pesquisa nele.", style = Type.small, color = Ink.muted)
        for (br in TechBranch.entries) {
            val focus = me.researchFocus == br.ordinal
            val lvl = me.tech[br.ordinal]
            val prog = if (lvl >= 10) 100f else me.techProgress[br.ordinal] / sim.economy.techCost(lvl + 1) * 100
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (focus) Ink.brass.copy(alpha = 0.13f) else Ink.raised)
                    .clickable { me.researchFocus = br.ordinal; c.refresh() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(110.dp)) {
                    Text(br.label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (focus) Ink.brass else Ink.text)
                    Text(techEffect(br), style = Type.small, color = Ink.muted, maxLines = 1)
                }
                Meter(prog, Modifier.weight(1f), if (focus) Ink.brass else Ink.verdigris)
                Spacer(Modifier.width(10.dp))
                Text("nív. $lvl", style = Type.number.copy(fontSize = 13.sp), color = Ink.text)
            }
        }
        if (g.settings.nuclear) {
            SectionTitle("Arsenal nuclear")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FlashOn, null, tint = Ink.coral)
                Spacer(Modifier.width(8.dp))
                Text("${me.nukes} mísseis" + if (me.nukeBuild > 0) ", outro pronto em ${me.nukeBuild} dias" else "", style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Fabricar (${Fmt.money(NUKE_COST)})", Modifier.weight(1f)) { c.act("Míssil em produção") { sim.military.buildNuke(me) } }
                Btn("Lançar", Modifier.weight(1f), BtnKind.DANGER, enabled = me.nukes > 0) { onNuke() }
            }
            if (me.tech[TechBranch.NUCLEAR.ordinal] < 6) Text("Precisa de tecnologia nuclear nível 6", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

private fun techEffect(b: TechBranch) = when (b) {
    TechBranch.LAND -> "+5% força em terra"
    TechBranch.SEA -> "+5% força no mar"
    TechBranch.AIR -> "+5% força no ar"
    TechBranch.ECONOMY -> "+0,1 crescimento"
    TechBranch.SOCIETY -> "+1 em todos os índices"
    TechBranch.NUCLEAR -> "mísseis no nível 6"
}

fun statLine(t: UnitType): String = when (t.domain) {
    Domain.LAND -> "ataque ${t.attack.toInt()}, defesa ${t.defense.toInt()}" + if (t.antiAir > 1) ", antiaérea ${t.antiAir.toInt()}" else ""
    Domain.SEA -> "contra navios ${t.antiShip.toInt()}, defesa ${t.defense.toInt()}" + if (t == UnitType.TRANSPORT) ", leva 4 divisões" else ""
    Domain.AIR -> "alcance ${t.speed.toInt()} km, ataque ${t.attack.toInt()}" + if (t.antiAir > 1) ", combate aéreo ${t.antiAir.toInt()}" else ""
}

// ================================================================ MUNDO

@Composable
fun WorldSheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val tab = c.subTab[Tab.WORLD] ?: (if (g.proposals.isNotEmpty()) 0 else 1)
    Column(Modifier.fillMaxSize()) {
        SheetHeaderTabs("Mundo", "${g.nations.count { it.alive }} países, ${g.wars.size} guerras",
            listOf("Propostas" + if (g.proposals.isNotEmpty()) " (${g.proposals.size})" else "", "Guerras", "ONU", "Ranking", "Avisos"), tab) { c.subTab[Tab.WORLD] = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> WorldProposals(c)
                1 -> WorldWars(c)
                2 -> WorldUn(c)
                3 -> WorldRanking(c)
                else -> WorldFeed(c)
            }
        }
    }
}

@Composable
private fun WorldProposals(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    SheetBody {
        Spacer(Modifier.height(8.dp))
        if (g.proposals.isEmpty()) Text("Nenhuma proposta agora. Países que gostam de você oferecem acordos, alianças e convites para blocos.", style = Type.body, color = Ink.muted)
        for (p in g.proposals.toList()) ProposalRow(c, p)
    }
}

@Composable
private fun WorldWars(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val sim = c.sim
    SheetBody {
        Spacer(Modifier.height(8.dp))
        if (g.wars.isEmpty()) Text("O mundo está em paz.", style = Type.body, color = Ink.muted)
        for (w in g.wars.sortedByDescending { it.involves(g.player) }) {
            val mine = w.involves(g.player)
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (mine) Ink.coral.copy(alpha = 0.12f) else Ink.raised)
                    .clickable { c.focusRequest = sim.nation(w.leaderD()).capital; c.tab = null; c.mapMode = MapMode.WAR; c.refresh() }.padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagView(sim.nation(w.leaderA()), 18.dp)
                    Text("  ×  ", style = Type.label, color = Ink.muted)
                    FlagView(sim.nation(w.leaderD()), 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(w.name.removePrefix("Guerra "), style = Type.label, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val s = sim.diplomacy.warScore(w, w.leaderA()).toInt()
                    Text("$s", style = Type.number.copy(fontSize = 14.sp), color = if (s >= 0) Ink.good else Ink.coral)
                }
                val extra = w.attackers.size + w.defenders.size - 2
                Text("${(g.day - w.start) / 30} meses" + if (extra > 0) ", +$extra aliados" else "", style = Type.small, color = Ink.muted)
                val s = sim.diplomacy.warScore(w, w.leaderA())
                Meter((s + 100) / 2, Modifier.fillMaxWidth().padding(top = 6.dp), Color(sim.nation(w.leaderA()).mapColor.toInt()))
            }
        }
    }
}

@Composable
private fun WorldRanking(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val sim = c.sim
    var rank by remember { mutableIntStateOf(0) }
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Segmented(listOf(0, 1, 2, 3), rank, { listOf("PIB", "Militar", "Território", "Aprovação")[it] }) { rank = it }
        Spacer(Modifier.height(8.dp))
        val alive = g.nations.filter { it.alive }
        val sorted = when (rank) {
            0 -> alive.sortedByDescending { g.natGdp(it.id) }
            1 -> alive.sortedByDescending { sim.power(it.id) }
            2 -> alive.sortedByDescending { g.natProvinces(it.id) }
            else -> alive.sortedByDescending { it.approval }
        }
        val myPos = sorted.indexOfFirst { it.id == g.player }
        sorted.take(15).forEachIndexed { i, n -> RankRow(c, i + 1, n, rank) }
        if (myPos >= 15) RankRow(c, myPos + 1, sorted[myPos], rank)
    }
}

@Composable
private fun WorldFeed(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    SheetBody {
        Spacer(Modifier.height(8.dp))
        for (f in g.feed.take(60)) {
            val d = com.dmwnezes.soberano.sim.START_DATE.plusDays(f.day.toLong())
            Row(
                Modifier.fillMaxWidth().clip(Shapes.small).clickable(enabled = f.province >= 0) { c.focusRequest = f.province; c.tab = null; c.selectProvince(f.province) }.padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.of(f.icon), null, tint = Ink.tone(f.tone), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(f.text, style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
                Text("${d.dayOfMonth}/${d.monthValue}/${d.year % 100}", style = Type.small, color = Ink.faint)
            }
        }
    }
}

@Composable
private fun RankRow(c: GameController, pos: Int, n: Nation, kind: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = n.id == c.game.player
    Row(
        Modifier.fillMaxWidth().clip(Shapes.small).background(if (me) Ink.brass.copy(alpha = 0.13f) else Color.Transparent)
            .clickable { if (!me) { c.focusNation = n.id; c.tab = Tab.DIPLOMACY; c.refresh() } }.padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$pos", style = Type.number.copy(fontSize = 14.sp), color = if (me) Ink.brass else Ink.muted, modifier = Modifier.width(30.dp))
        FlagView(n, 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(n.name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            when (kind) {
                0 -> Fmt.money(c.game.natGdp(n.id))
                1 -> c.sim.power(n.id).toInt().toString()
                2 -> "${c.game.natProvinces(n.id)} prov."
                else -> "${n.approval.toInt()}%"
            }, style = Type.label, color = Ink.muted,
        )
    }
}

/** Mobilização, exercícios, mercenários e bombardeio estratégico. */
@Composable
private fun MilCommand(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val sim = c.sim
    val pol = sim.policy
    var bombing by remember { mutableStateOf(false) }
    SheetBody {
        Spacer(Modifier.height(8.dp))
        ToggleRow("Mobilização geral", me.mobilized, GameIcons.of("mobilize"),
            if (me.mobilized) "Reservistas 2,5× mais rápidos, exército até 50% maior; PIB cresce 1,5% menos"
            else "Custa ⚡${pol.mobilizeCost.toInt()}. Reservistas 2,5× e exército maior; a economia sofre") {
            c.act(if (it) "Mobilização geral decretada" else "Mobilização encerrada") { pol.toggleMobilization(me) }
        }
        val drill = pol.drillOn(me)
        ActionRow(GameIcons.of("drill"), if (drill) "Tropas treinadas" else "Exercícios militares",
            if (drill) "+10% em combate até ${dateText(me.drillUntil)}" else "Custa ${Fmt.money(pol.drillCost(me))}: moral +20% e +10% em combate por 6 meses",
            !drill, active = drill) { c.act("Exercícios concluídos") { pol.drill(me) } }
        val mercWhy = pol.mercsRefusal(me)
        ActionRow(GameIcons.of("mercs"), "Contratar mercenários",
            mercWhy ?: "${pol.mercsCount} divisões na hora, na capital, por ${Fmt.money(pol.mercsCost(me))}", mercWhy == null) {
            c.act { pol.hireMercs(me, me.capital.takeIf { g.controller[it] == me.id } ?: sim.provincesOf(me.id).first { g.controller[it] == me.id }) }
        }
        val enemies = g.nations.filter { it.alive && sim.atWar(me.id, it.id) }
        ActionRow(GameIcons.of("bomb"), "Bombardeio estratégico",
            if (enemies.isEmpty()) "Só em guerra" else if (g.day - me.lastBombing < 30) "Aviões voltando (${30 - (g.day - me.lastBombing)} dias)"
            else "A aviação ataca a economia do inimigo", enemies.isNotEmpty() && g.day - me.lastBombing >= 30, danger = true) { bombing = true }
        Text("Para desmobilizar, toque num exército no mapa e escolha \"Desmobilizar\".", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 8.dp))
    }
    if (bombing) Overlay({ bombing = false }) {
        Text("Bombardear quem?", style = Type.heading, color = Ink.text)
        Text("Até 6 províncias ao alcance perdem 3% do PIB; parte dos aviões cai.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        for (n in g.nations.filter { it.alive && sim.atWar(me.id, it.id) }) {
            val why = pol.bombingRefusal(me, n.id)
            ActionRow(GameIcons.of("bomb"), n.name, why ?: "${pol.bombingTargets(me, n.id).size} alvos ao alcance", why == null, danger = true) {
                c.act { pol.bomb(me, n.id) }; bombing = false
            }
        }
    }
}
