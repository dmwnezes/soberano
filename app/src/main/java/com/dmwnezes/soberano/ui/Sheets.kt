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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Content
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.EconomyDepth
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.NUKE_COST
import com.dmwnezes.soberano.sim.Nation
import com.dmwnezes.soberano.sim.PeaceTerms
import com.dmwnezes.soberano.sim.Regime
import com.dmwnezes.soberano.sim.Setup
import com.dmwnezes.soberano.sim.SpyMission
import com.dmwnezes.soberano.sim.TechBranch
import com.dmwnezes.soberano.sim.TreatyType
import com.dmwnezes.soberano.sim.UnitType

@Composable
private fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp), content = content)
}

@Composable
private fun SheetTitle(title: String, sub: String? = null) {
    Text(title, style = Type.title, color = Ink.text, modifier = Modifier.padding(top = 6.dp))
    if (sub != null) Text(sub, style = Type.label, color = Ink.muted)
}

// ================================================================ GOVERNO

@Composable
fun GovernmentSheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val g = c.game
    SheetBody {
        SheetTitle("${c.game.leaderTitle.label} de ${me.name}", me.regime.label)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("Aprovação", "${me.approval.toInt()}%", Ink.scale(me.approval), Modifier.weight(1f))
            BigStat("Estabilidade", "${me.stability.toInt()}", Ink.scale(me.stability), Modifier.weight(1f))
            BigStat("Capital político", "${me.politicalCapital.toInt()}", Ink.brass, Modifier.weight(1f))
        }
        if (me.regime.elections && g.settings.elections && me.nextElection > 0) {
            val months = ((me.nextElection - g.day) / 30).coerceAtLeast(0)
            val chance = (c.sim.politics.electionChance(me) * 100).toInt()
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HowToVote, null, tint = Ink.muted)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Eleição em $months meses", style = Type.body, color = Ink.text)
                        Text("Chance de vitória hoje: $chance%", style = Type.small, color = Ink.scale(chance.toFloat()))
                    }
                    if (me.regime.congress) Column(horizontalAlignment = Alignment.End) {
                        Text("${me.congress.toInt()}%", style = Type.number, color = Ink.scale(me.congress))
                        Text("Congresso", style = Type.small, color = Ink.muted)
                    }
                }
            }
        }
        SectionTitle("Povo")
        for (gr in Group.entries) {
            val target = c.sim.politics.groupTarget(me, gr)
            MeterRow(GameIcons.group(gr), gr.label, me.groups[gr.ordinal], delta = target - me.groups[gr.ordinal])
        }
        SectionTitle("Leis", trailing = { Btn("Criar lei", kind = BtnKind.PRIMARY, icon = Icons.Rounded.Add) { c.lawEditor = true } })
        Text(if (me.regime.decree) "Você aprova por decreto" else "O Congresso vota (apoio ${me.congress.toInt()}%)", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(6.dp))
        val all = g.customLaws.mapNotNull { Content.law(it.id) } + Content.laws
        for (law in all) {
            if (law.decreeOnly && !me.regime.decree && law.id !in me.laws) continue
            val active = law.id in me.laws
            val custom = law.id.startsWith("custom_")
            val cost = c.sim.politics.lawCost(me, law, !active).toInt()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile)
                    .background(if (active) Ink.brass.copy(alpha = 0.13f) else Ink.raised)
                    .clickable { c.act(if (active) "Lei revogada" else "Lei aprovada") { if (c.sim.politics.toggleLaw(me, law.id)) null else c.sim.politics.canEnact(me, law) ?: "Não foi possível" } }
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.of(law.icon), null, tint = if (active) Ink.brass else Ink.muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(law.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text, modifier = Modifier.weight(1f, fill = false))
                        if (custom) { Spacer(Modifier.width(6.dp)); Tag("sua", Ink.verdigris) }
                    }
                    Text(law.desc, style = Type.small, color = Ink.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Icon(if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (active) Ink.brass else Ink.faint)
                    Text("⚡$cost", style = Type.small, color = Ink.faint)
                }
                IconBtn(Icons.Rounded.Info, size = 36.dp, bg = Color.Transparent, tint = Ink.muted) { c.lawInfo = law.id }
            }
        }
        SectionTitle("Regime")
        Text("Mudar custa ${c.sim.politics.regimeChangeCost.toInt()} de capital político", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        for (r in Regime.entries) {
            val cur = r == me.regime
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (cur) Ink.brass.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable(enabled = !cur) { c.act("Regime alterado") { c.sim.politics.changeRegime(me, r) } }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(r.label, style = Type.body, color = if (cur) Ink.brass else Ink.text, modifier = Modifier.weight(1f))
                Text(if (r.elections) "eleições" else "sem eleições", style = Type.small, color = Ink.faint)
            }
        }
    }
}

@Composable
private fun BigStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
        Text(value, style = Type.big.copy(fontSize = 26.sp), color = color)
        Text(label, style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ================================================================ ECONOMIA

@Composable
fun EconomySheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val g = c.game
    val b = c.sim.economy.budget(me)
    val depth = g.settings.economy
    SheetBody {
        SheetTitle("Economia", "PIB ${Fmt.money(b.gdp)}, crescimento ${Fmt.one(me.growth * 100)}% ao ano")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("Saldo do mês", (if (b.balance >= 0) "+" else "") + Fmt.money(b.balance).removePrefix("US$ "), if (b.balance >= 0) Ink.good else Ink.coral, Modifier.weight(1f))
            BigStat("Tesouro", Fmt.money(me.treasury).removePrefix("US$ "), Ink.text, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Row {
                    Cell("Receita/mês", Fmt.money(b.revenue))
                    Cell("Gastos/mês", Fmt.money(b.civil + b.defense))
                    Cell("Juros/mês", Fmt.money(b.interest), Ink.amber)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    val ratio = if (b.gdp > 0) me.debt / b.gdp else 0.0
                    Cell("Dívida", "${(ratio * 100).toInt()}% PIB", Ink.scale((100 - ratio * 50).toFloat()))
                    if (depth != EconomyDepth.SIMPLE) {
                        Cell("Inflação", "${Fmt.one(me.inflation)}%", Ink.scale(100 - me.inflation * 6))
                        Cell("Desemprego", "${Fmt.one(me.unemployment)}%", Ink.scale(100 - me.unemployment * 5))
                    }
                }
            }
        }
        SectionTitle("Imposto")
        var tax by remember(me.id) { mutableStateOf(me.tax) }
        val taxRev = b.gdp * tax / 12 * (1 - me.corruption / 100.0 * 0.15)
        SliderRow(Icons.Rounded.Percent, "Carga tributária", tax, 0.05f..0.65f, "${(tax * 100).toInt()}%",
            hint = if (kotlin.math.abs(tax - me.tax) > 0.004f) "${if (taxRev > b.revenue) "+" else ""}${Fmt.money(taxRev - b.revenue)}/mês" else null,
            hintGood = tax >= me.tax) { tax = it; c.sim.economy.setTax(me, it); c.refresh() }
        Text(if (me.tax > 0.33f) "Imposto alto freia o crescimento" else "Imposto baixo agrada empresários", style = Type.small, color = Ink.faint)

        SectionTitle("Orçamento", trailing = { Text("% do PIB", style = Type.small, color = Ink.muted) })
        val visible = Area.entries.filter { depth != EconomyDepth.SIMPLE || it in setOf(Area.HEALTH, Area.EDUCATION, Area.SECURITY, Area.DEFENSE) }
        for (a in visible) {
            val share = me.shares[a.ordinal]
            val max = if (a == Area.DEFENSE) 0.25f else 0.15f
            val target = c.sim.economy.indexTarget(me, a)
            val hint = if (a.hasIndex) "índice ${me.index[a.ordinal].toInt()} → ${target.toInt()}" else "fundo ${Fmt.money(me.militaryFund)}"
            SliderRow(GameIcons.area(a), a.label, share, 0f..max, "${Fmt.one(share * 100)}%", hint = hint,
                hintGood = !a.hasIndex || target >= me.index[a.ordinal]) { c.sim.economy.setShare(me, a, it); c.refresh() }
        }
        SectionTitle("Dívida")
        val month = b.gdp / 12
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Pagar ${Fmt.money(month * 0.1)}", Modifier.weight(1f), enabled = me.treasury >= month * 0.1 && me.debt > 0) {
                c.sim.economy.repayDebt(me, month * 0.1); c.refresh()
            }
            Btn("Emprestar ${Fmt.money(month * 0.1)}", Modifier.weight(1f)) { c.sim.economy.borrow(me, month * 0.1); c.refresh() }
        }
        Spacer(Modifier.height(8.dp))
        Btn("Reforçar fundo militar com ${Fmt.money(month * 0.05)}", Modifier.fillMaxWidth(), BtnKind.GHOST, enabled = me.treasury >= month * 0.05) {
            c.sim.economy.transferToMilitary(me, month * 0.05); c.refresh()
        }
    }
}

// ================================================================ DIPLOMACIA

@Composable
fun DiplomacySheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val f = c.focusNation
    if (f >= 0 && f != c.game.player && c.game.nations[f].alive) NationDetail(c, c.game.nations[f]) else NationList(c)
}

@Composable
private fun NationList(c: GameController) {
    val g = c.game
    val me = g.player
    var q by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val neighbors = c.sim.ai.neighborsOf(me)
    SheetBody {
        SheetTitle("Diplomacia", "Toque num país no mapa ou na lista")
        Spacer(Modifier.height(10.dp))
        SearchField(q, "Buscar país", Modifier.fillMaxWidth()) { q = it }
        val all = g.nations.filter { it.alive && it.id != me }
        val list = if (q.isNotBlank()) all.filter { strip(it.name.lowercase()).contains(strip(q.lowercase())) }
        else {
            val war = all.filter { c.sim.atWar(me, it.id) }
            val ally = all.filter { c.sim.allied(me, it.id) && it !in war }
            val near = all.filter { it.id in neighbors && it !in war && it !in ally }
            val big = all.sortedByDescending { g.natGdp(it.id) }.filter { it !in war && it !in ally && it !in near }.take(12)
            war + ally + near + big
        }
        Spacer(Modifier.height(8.dp))
        for (n in list.take(60)) NationRow(c, n)

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
private fun NationRow(c: GameController, n: Nation) {
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
            }
        }
        RelationBadge(r)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text, style = Type.small.copy(fontSize = 11.sp), color = color, modifier = Modifier.clip(Shapes.chip).background(color.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
fun RelationBadge(r: Float) {
    val col = when { r >= 40 -> Ink.verdigris; r >= 0 -> Ink.muted; r >= -40 -> Ink.amber; else -> Ink.coral }
    Text((if (r > 0) "+" else "") + r.toInt(), style = Type.number.copy(fontSize = 14.sp), color = col,
        modifier = Modifier.clip(Shapes.chip).background(col.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NationDetail(c: GameController, n: Nation) {
    val g = c.game
    val me = g.player
    val sim = c.sim
    val r = g.rel(me, n.id)
    var confirmWar by remember { mutableStateOf(false) }
    var spyResult by remember { mutableStateOf<String?>(null) }
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
            for (t in PeaceTerms.entries) {
                val why = sim.diplomacy.peaceRefusal(war, me, t)
                ActionRow(GameIcons.of("dove"), t.label, why ?: "Eles aceitam", why == null) {
                    c.act("Paz assinada") { sim.diplomacy.proposePeace(war, me, t) }
                }
            }
        } else {
            SectionTitle("Acordos")
            for (t in listOf(TreatyType.TRADE, TreatyType.NON_AGGRESSION, TreatyType.PASSAGE, TreatyType.ALLIANCE)) {
                val has = sim.hasTreaty(t, me, n.id)
                if (has) ActionRow(Icons.Rounded.CheckCircle, t.label, "Ativo. Toque para cancelar", true, active = true) {
                    sim.diplomacy.cancel(t, me, n.id); c.refresh()
                } else {
                    val why = sim.diplomacy.refusal(t, me, n.id)
                    ActionRow(Icons.Rounded.Handshake, t.label, why ?: "Eles aceitam", why == null) { c.act("${t.label} assinado") { sim.diplomacy.propose(t, me, n.id) } }
                }
            }
            val myBloc = g.blocs.firstOrNull { it.founder == me }
            if (myBloc != null && n.id !in myBloc.members) {
                val why = sim.diplomacy.inviteRefusal(myBloc, n.id)
                ActionRow(Icons.Rounded.Hub, "Convidar para ${myBloc.name}", why ?: "Eles aceitam", why == null) { c.act("${n.name} entrou no bloco") { sim.diplomacy.invite(myBloc, n.id) } }
            }
        }
        SectionTitle("Relações")
        ActionRow(Icons.Rounded.Flight, "Missão diplomática", "+6 relação, custa ⚡3", c.me.politicalCapital >= 3f) { c.act { sim.diplomacy.envoy(me, n.id) } }
        val gift = (g.natGdp(me) * 0.002).coerceAtLeast(0.2)
        ActionRow(Icons.Rounded.Redeem, "Enviar ajuda de ${Fmt.money(gift)}", "Melhora a relação", c.me.treasury >= gift) { c.act("Ajuda enviada") { sim.diplomacy.gift(me, n.id, gift) } }
        val sanctioned = g.treaties.any { it.type == TreatyType.SANCTION && it.a == me && it.b == n.id }
        val embargo = g.treaties.any { it.type == TreatyType.EMBARGO && it.a == me && it.b == n.id }
        ActionRow(Icons.Rounded.Block, if (sanctioned) "Retirar sanções" else "Aplicar sanções", "Corta o crescimento deles", true, active = sanctioned) { sim.diplomacy.sanction(me, n.id, false); c.refresh() }
        ActionRow(Icons.Rounded.DoNotDisturbOn, if (embargo) "Retirar embargo" else "Embargo total", "Mais forte que a sanção", true, active = embargo) { sim.diplomacy.sanction(me, n.id, true); c.refresh() }

        SectionTitle("Espionagem")
        for (m in SpyMission.entries) {
            val ch = (sim.diplomacy.spyChance(me, n.id, m) * 100).toInt()
            ActionRow(Icons.Rounded.VisibilityOff, m.label, "$ch% de chance, ${Fmt.money(m.cost)}", c.me.treasury >= m.cost) {
                spyResult = sim.diplomacy.spy(me, n.id, m); c.refresh()
            }
        }
        spyResult?.let { Text(it, style = Type.label, color = Ink.brass, modifier = Modifier.padding(top = 6.dp)) }

        if (war == null) {
            Spacer(Modifier.height(18.dp))
            val na = sim.hasTreaty(TreatyType.NON_AGGRESSION, me, n.id)
            Btn("Declarar guerra", Modifier.fillMaxWidth(), BtnKind.DANGER, Icons.Rounded.Whatshot) { confirmWar = true }
            if (na) Text("Quebrar o pacto de não agressão custa estabilidade e reputação", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 6.dp))
        }
    }
    if (confirmWar) {
        val allies = g.nations.filter { it.alive && it.id != n.id && it.id != me && sim.allied(it.id, n.id) && !sim.allied(it.id, me) }
        Overlay({ confirmWar = false }) {
            Text("Guerra contra ${n.name}?", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
            Spacer(Modifier.height(8.dp))
            Text(if (allies.isEmpty()) "Nenhum aliado deve entrar na guerra por eles." else "Aliados que podem entrar: ${allies.take(6).joinToString { it.name }}${if (allies.size > 6) " e mais ${allies.size - 6}" else ""}.",
                style = Type.body, color = Ink.muted)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { confirmWar = false }
                Btn("Declarar", Modifier.weight(1f), BtnKind.DANGER) {
                    sim.diplomacy.declareWar(me, n.id); confirmWar = false; c.mapMode = MapMode.WAR; c.refresh()
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, enabled: Boolean, active: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (active) Ink.verdigris.copy(alpha = 0.13f) else Ink.raised)
            .clickable(enabled = enabled, onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (!enabled) Ink.faint else if (active) Ink.verdigris else Ink.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium), color = if (enabled) Ink.text else Ink.faint)
            Text(sub, style = Type.small, color = if (enabled) Ink.muted else Ink.coral.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

@Composable
private fun CreateBlocDialog(c: GameController, onClose: () -> Unit) {
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

// ================================================================ MILITAR

@Composable
fun MilitarySheet(c: GameController, onNuke: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val g = c.game
    val sim = c.sim
    val armies = sim.armiesOf(me.id)
    var buildAt by remember { mutableIntStateOf(if (c.buildProvince >= 0 && g.controller[c.buildProvince] == me.id) c.buildProvince else me.capital) }
    var picking by remember { mutableStateOf(false) }
    SheetBody {
        SheetTitle("Forças armadas")
        Spacer(Modifier.height(14.dp))
        val b = sim.economy.budget(me)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("Fundo militar", Fmt.money(me.militaryFund).removePrefix("US$ "), if (me.militaryFund >= 0) Ink.text else Ink.coral, Modifier.weight(1f))
            BigStat("Manutenção/mês", Fmt.money(b.upkeep).removePrefix("US$ "), if (b.upkeep > b.defense) Ink.coral else Ink.text, Modifier.weight(1f))
        }
        Text("Verba de defesa: ${Fmt.money(b.defense)}/mês", style = Type.small, color = Ink.muted, modifier = Modifier.padding(top = 6.dp))

        SectionTitle("Tropas")
        if (armies.isEmpty()) Text("Nenhuma tropa. Recrute abaixo.", style = Type.body, color = Ink.muted)
        for (a in armies.sortedBy { it.domain.ordinal }) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                    .clickable { c.selArmy = a.id; c.selProvince = -1; c.tab = null; c.focusRequest = a.province; c.refresh() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.domain(a.domain), null, tint = if (a.battle) Ink.coral else Ink.brass, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = Type.body, color = Ink.text)
                    Text(sim.world.provinces[a.province].name + if (a.moving) ", em movimento" else if (a.battle) ", em combate" else "", style = Type.small, color = Ink.muted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.units((a.total() * 2).toInt() / 2f), style = Type.number, color = Ink.text)
                    Text("moral ${(a.morale * 100).toInt()}%", style = Type.small, color = Ink.scale(a.morale * 100))
                }
            }
        }

        SectionTitle("Recrutar")
        Row(
            Modifier.fillMaxWidth().clip(Shapes.tile).background(Ink.oceanDeep).clickable { picking = true }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Place, null, tint = Ink.brass)
            Spacer(Modifier.width(8.dp))
            Text("Em ${sim.world.provinces[buildAt].name}", style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
            Text("trocar", style = Type.label, color = Ink.verdigris)
        }
        Spacer(Modifier.height(8.dp))
        for (d in Domain.entries) {
            Text(when (d) { Domain.LAND -> "Exército"; Domain.SEA -> "Marinha"; Domain.AIR -> "Aeronáutica" }, style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
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

        SectionTitle("Tecnologia", trailing = { Text("toque para focar", style = Type.small, color = Ink.muted) })
        for (br in TechBranch.entries) {
            val focus = me.researchFocus == br.ordinal
            val lvl = me.tech[br.ordinal]
            val prog = if (lvl >= 10) 100f else me.techProgress[br.ordinal] / sim.economy.techCost(lvl + 1) * 100
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (focus) Ink.brass.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable { me.researchFocus = br.ordinal; c.refresh() }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(br.label, style = Type.body, color = if (focus) Ink.brass else Ink.text, modifier = Modifier.width(100.dp))
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
    if (picking) {
        val provs = sim.provincesOf(me.id).filter { g.controller[it] == me.id }.sortedByDescending { g.provPop[it] }
        Overlay({ picking = false }) {
            Text("Onde recrutar", style = Type.heading, color = Ink.text)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
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

private fun statLine(t: UnitType): String = when (t.domain) {
    Domain.LAND -> "ataque ${t.attack.toInt()}, defesa ${t.defense.toInt()}" + if (t.antiAir > 1) ", antiaérea ${t.antiAir.toInt()}" else ""
    Domain.SEA -> "contra navios ${t.antiShip.toInt()}, defesa ${t.defense.toInt()}" + if (t == UnitType.TRANSPORT) ", leva 4 divisões" else ""
    Domain.AIR -> "alcance ${t.speed.toInt()} km, ataque ${t.attack.toInt()}" + if (t.antiAir > 1) ", combate aéreo ${t.antiAir.toInt()}" else ""
}

// ================================================================ MUNDO

@Composable
fun WorldSheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val sim = c.sim
    var rank by remember { mutableIntStateOf(0) }
    SheetBody {
        SheetTitle("Mundo", "${g.nations.count { it.alive }} países, ${g.wars.size} guerras")
        if (g.proposals.isNotEmpty()) {
            SectionTitle("Propostas")
            for (p in g.proposals.toList()) {
                val from = sim.nation(p.from)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlagView(from, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${from.name}: " + if (p.kind.startsWith("PEACE")) "paz branca" else runCatching { TreatyType.valueOf(p.kind).label }.getOrDefault(p.kind),
                        style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
                    IconBtn(Icons.Rounded.Close, size = 32.dp, tint = Ink.coral) { g.proposals.remove(p); c.refresh() }
                    Spacer(Modifier.width(6.dp))
                    IconBtn(Icons.Rounded.Check, size = 32.dp, bg = Ink.brass, tint = Ink.onBrass) { acceptProposal(c, p); c.refresh() }
                }
            }
        }
        SectionTitle("Guerras")
        if (g.wars.isEmpty()) Text("O mundo está em paz.", style = Type.body, color = Ink.muted)
        for (w in g.wars) {
            val mine = w.involves(g.player)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (mine) Ink.coral.copy(alpha = 0.12f) else Ink.raised)
                    .clickable { c.focusRequest = sim.nation(w.leaderD()).capital; c.tab = null; c.mapMode = MapMode.WAR; c.refresh() }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlagView(sim.nation(w.leaderA()), 18.dp)
                Text("  ×  ", style = Type.label, color = Ink.muted)
                FlagView(sim.nation(w.leaderD()), 18.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.name.removePrefix("Guerra "), style = Type.label, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val extra = w.attackers.size + w.defenders.size - 2
                    Text("${(g.day - w.start) / 30} meses" + if (extra > 0) ", +$extra aliados" else "", style = Type.small, color = Ink.muted)
                }
                val s = sim.diplomacy.warScore(w, w.leaderA()).toInt()
                Text("$s", style = Type.number.copy(fontSize = 14.sp), color = if (s >= 0) Ink.good else Ink.coral)
            }
        }
        SectionTitle("Ranking")
        Segmented(listOf(0, 1, 2), rank, { listOf("PIB", "Poder militar", "Território")[it] }) { rank = it }
        Spacer(Modifier.height(8.dp))
        val alive = g.nations.filter { it.alive }
        val sorted = when (rank) {
            0 -> alive.sortedByDescending { g.natGdp(it.id) }
            1 -> alive.sortedByDescending { sim.power(it.id) }
            else -> alive.sortedByDescending { g.natProvinces(it.id) }
        }
        val myPos = sorted.indexOfFirst { it.id == g.player }
        sorted.take(10).forEachIndexed { i, n -> RankRow(c, i + 1, n, rank) }
        if (myPos >= 10) RankRow(c, myPos + 1, sorted[myPos], rank)

        SectionTitle("Avisos")
        for (f in g.feed.take(40)) {
            val d = com.dmwnezes.soberano.sim.START_DATE.plusDays(f.day.toLong())
            Row(
                Modifier.fillMaxWidth().clip(Shapes.small).clickable(enabled = f.province >= 0) { c.focusRequest = f.province; c.tab = null; c.selectProvince(f.province) }.padding(vertical = 6.dp),
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
                else -> "${c.game.natProvinces(n.id)} prov."
            }, style = Type.label, color = Ink.muted,
        )
    }
}

