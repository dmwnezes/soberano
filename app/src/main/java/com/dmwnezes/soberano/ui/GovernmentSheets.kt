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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Content
import com.dmwnezes.soberano.sim.EconomyDepth
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.MinisterSlot
import com.dmwnezes.soberano.sim.Regime
import com.dmwnezes.soberano.sim.Resource

// ================================================================ GOVERNO

@Composable
fun GovernmentSheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val tab = c.subTab[Tab.GOVERNMENT] ?: 0
    Column(Modifier.fillMaxSize()) {
        SheetHeaderTabs("${c.game.leaderTitle.label} de ${me.name}", me.regime.label, listOf("Visão geral", "Leis", "Ministros", "Regime"), tab) { c.subTab[Tab.GOVERNMENT] = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> GovOverview(c)
                1 -> GovLaws(c)
                2 -> GovMinisters(c)
                else -> GovRegime(c)
            }
        }
    }
}

@Composable
private fun GovOverview(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    SheetBody {
        Spacer(Modifier.height(8.dp))
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
        CampaignPanel(c)
        Spacer(Modifier.height(10.dp))
        LineChart("Aprovação", g.history.map { it.approval }, "${me.approval.toInt()}%", Ink.good)
        SectionTitle("Povo")
        for (gr in Group.entries) {
            val target = c.sim.politics.groupTarget(me, gr)
            MeterRow(GameIcons.group(gr), gr.label, me.groups[gr.ordinal], delta = target - me.groups[gr.ordinal])
        }
        Text("A seta mostra para onde cada grupo está indo.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        Row {
            Cell("Corrupção", "${me.corruption.toInt()}", Ink.scale(100 - me.corruption))
            Cell("Cansaço de guerra", "${(me.warExhaustion * 100).toInt()}%", Ink.scale(100 - me.warExhaustion * 100))
            Cell("Leis em vigor", "${me.laws.size}")
        }
    }
}

@Composable
private fun GovLaws(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    SheetBody {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (me.regime.decree) "Você aprova por decreto" else "O Congresso vota (apoio ${me.congress.toInt()}%)",
                style = Type.small, color = Ink.muted, modifier = Modifier.weight(1f))
            Btn("Criar lei", kind = BtnKind.PRIMARY, icon = Icons.Rounded.Add) { c.lawEditor = true }
        }
        Spacer(Modifier.height(8.dp))
        val all = g.customLaws.mapNotNull { Content.law(it.id) } + Content.laws
        for (law in all.sortedByDescending { it.id in me.laws }) {
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
    }
}

@Composable
private fun GovMinisters(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val dev = c.sim.dev
    SheetBody {
        Spacer(Modifier.height(6.dp))
        Text("Ministros competentes aceleram as áreas deles; corruptos geram escândalos; desleais podem sair.", style = Type.small, color = Ink.muted)
        for (slot in MinisterSlot.entries) {
            val m = dev.minister(slot) ?: continue
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth(), androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Ink.oceanDeep), contentAlignment = Alignment.Center) {
                            Text(m.name.split(" ").map { it.first() }.joinToString(""), style = Type.label, color = Ink.brass)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
                            Text(slot.label, style = Type.small, color = Ink.muted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) { Text("Competência", style = Type.small, color = Ink.muted); Stars(m.skill) }
                        Column(Modifier.weight(1f)) { Text("Lealdade", style = Type.small, color = Ink.muted); Stars(m.loyalty, Ink.verdigris) }
                        Column(Modifier.weight(1f)) { Text("Corrupção", style = Type.small, color = Ink.muted); Stars(m.corruption, Ink.coral) }
                    }
                }
            }
        }
        SectionTitle("Candidatos", trailing = { Text("trocar custa ⚡${dev.hireCost.toInt()}", style = Type.small, color = Ink.muted) })
        for (cand in g.candidates.sortedBy { it.slot.ordinal }) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised)
                    .clickable { c.act { dev.hire(cand) } }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(cand.name, style = Type.body, color = Ink.text)
                    Text("para ${cand.slot.label}", style = Type.small, color = Ink.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Stars(cand.skill)
                    Stars(cand.loyalty, Ink.verdigris)
                    Stars(cand.corruption, Ink.coral)
                }
            }
        }
        Text("Novos candidatos aparecem a cada seis meses.", style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GovRegime(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    SheetBody {
        Spacer(Modifier.height(6.dp))
        Text("Mudar de regime custa ${c.sim.politics.regimeChangeCost.toInt()} de capital político. Virar ditadura exige apoio dos militares.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        for (r in Regime.entries) {
            val cur = r == me.regime
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(if (cur) Ink.brass.copy(alpha = 0.13f) else Ink.raised)
                    .clickable(enabled = !cur) { c.act("Regime alterado") { c.sim.politics.changeRegime(me, r) } }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = if (cur) Ink.brass else Ink.text)
                    Text(
                        listOfNotNull(if (r.elections) "eleições" else "sem eleições", if (r.congress) "Congresso vota as leis" else null, if (r.decree) "decretos" else null).joinToString(", "),
                        style = Type.small, color = Ink.muted,
                    )
                }
                if (cur) Tag("atual", Ink.brass)
            }
        }
    }
}

// ================================================================ ECONOMIA

@Composable
fun EconomySheet(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val tab = c.subTab[Tab.ECONOMY] ?: 0
    Column(Modifier.fillMaxSize()) {
        SheetHeaderTabs("Economia", "PIB ${Fmt.money(c.game.natGdp(me.id))}, crescimento ${Fmt.one(me.growth * 100)}% ao ano",
            listOf("Orçamento", "Recursos", "Obras", "Histórico"), tab) { c.subTab[Tab.ECONOMY] = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> EconBudget(c)
                1 -> EconResources(c)
                2 -> EconProjects(c)
                else -> EconHistory(c)
            }
        }
    }
}

@Composable
private fun EconBudget(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val g = c.game
    val b = c.sim.economy.budget(me)
    val depth = g.settings.economy
    SheetBody {
        Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(12.dp))
                val trade = c.sim.dev.tradeBalance(me.id)
                Row {
                    Cell("Comércio de recursos", (if (trade >= 0) "+" else "") + Fmt.money(trade.toDouble()) + "/ano", if (trade >= 0) Ink.good else Ink.coral)
                }
            }
        }
        SectionTitle("Imposto")
        var tax by remember(me.id) { mutableStateOf(me.tax) }
        val taxRev = b.gdp * tax / 12 * (1 - me.corruption / 100.0 * 0.15)
        SliderRow(Icons.Rounded.Percent, "Carga tributária", tax, 0.05f..0.65f, "${(tax * 100).toInt()}%",
            hint = if (kotlin.math.abs(tax - me.tax) > 0.004f) "${if (taxRev > b.revenue) "+" else ""}${Fmt.money(taxRev - b.revenue)}/mês" else null,
            hintGood = tax >= me.tax) { tax = it; c.sim.economy.setTax(me, it); c.refresh() }
        Text(if (me.tax > 0.33f) "Imposto acima de 33% freia o crescimento" else "Imposto baixo agrada empresários", style = Type.small, color = Ink.faint)

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
            Btn("Pagar ${Fmt.money(month * 0.1)}", Modifier.weight(1f), enabled = me.treasury >= month * 0.1 && me.debt > 0) { c.sim.economy.repayDebt(me, month * 0.1); c.refresh() }
            Btn("Emprestar ${Fmt.money(month * 0.1)}", Modifier.weight(1f)) { c.sim.economy.borrow(me, month * 0.1); c.refresh() }
        }
        Spacer(Modifier.height(8.dp))
        Btn("Reforçar fundo militar com ${Fmt.money(month * 0.05)}", Modifier.fillMaxWidth(), BtnKind.GHOST, enabled = me.treasury >= month * 0.05) {
            c.sim.economy.transferToMilitary(me, month * 0.05); c.refresh()
        }
    }
}

@Composable
private fun EconResources(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val dev = c.sim.dev
    val g = c.game
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Text("Quem produz mais do que consome exporta e ganha royalties; quem falta, importa. Os preços mudam todo mês.", style = Type.small, color = Ink.muted)
        for (r in Resource.entries) {
            val prod = dev.production(me.id, r)
            val use = dev.consumption(me.id, r)
            val bal = dev.balance(me.id, r)
            val price = g.prices[r.ordinal]
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth(), androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(GameIcons.of(r.icon), null, tint = Ink.brass, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(r.label, style = Type.heading, color = Ink.text, modifier = Modifier.weight(1f))
                        Text("preço ${(price * 100).toInt()}%", style = Type.small, color = if (price >= 1f) Ink.good else Ink.amber)
                    }
                    Spacer(Modifier.height(10.dp))
                    SupplyBar(prod, use, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text("Produz ${Fmt.money(prod.toDouble())}", style = Type.small, color = Ink.good, modifier = Modifier.weight(1f))
                        Text("Consome ${Fmt.money(use.toDouble())}", style = Type.small, color = Ink.amber)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text((if (bal >= 0) "Exporta " else "Importa ") + Fmt.money(kotlin.math.abs(bal).toDouble()) + " por ano",
                        style = Type.label, color = if (bal >= 0) Ink.good else Ink.coral)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Conquistar províncias produtoras traz os recursos delas. Embargos cortam metade das exportações.", style = Type.small, color = Ink.faint)
    }
}

@Composable
private fun EconProjects(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val me = c.me
    val dev = c.sim.dev
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Text("Obras caras e demoradas com bônus permanente. No máximo 3 ao mesmo tempo.", style = Type.small, color = Ink.muted)
        if (me.projects.isNotEmpty()) {
            SectionTitle("Em obras")
            for (b in me.projects) {
                val p = dev.projectById[b.id] ?: continue
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(GameIcons.of(p.icon), null, tint = Ink.brass, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = Type.body, color = Ink.text)
                        Meter((1f - b.monthsLeft.toFloat() / b.total) * 100, Modifier.fillMaxWidth().padding(top = 4.dp), Ink.verdigris)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${b.monthsLeft} meses", style = Type.small, color = Ink.muted)
                }
            }
        }
        SectionTitle("Projetos")
        for (p in dev.projects) {
            val built = p.id in me.built
            val why = dev.canStart(me, p)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(if (built) Ink.verdigris.copy(alpha = 0.12f) else Ink.raised)
                    .clickable(enabled = why == null) { c.act("Obra iniciada") { dev.start(me, p) } }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.of(p.icon), null, tint = if (built) Ink.verdigris else if (why == null) Ink.brass else Ink.faint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
                    Text(p.desc, style = Type.small, color = Ink.muted)
                    val fx = buildList {
                        for ((a, v) in p.index) add("${a.label} +${v.toInt()}")
                        if (p.growth > 0) add("crescimento +${Fmt.one(p.growth * 100)}")
                        if (p.unitDiscount > 0) add("tropas −${(p.unitDiscount * 100).toInt()}%")
                        if (p.research > 0) add("pesquisa +${p.research.toInt()}")
                        if (p.stability > 0) add("estabilidade +${p.stability.toInt()}")
                    }
                    if (fx.isNotEmpty()) Text(fx.joinToString(", "), style = Type.small, color = Ink.good)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (built) Tag("pronta", Ink.verdigris)
                    else {
                        Text(Fmt.money(dev.projectCost(me, p)), style = Type.number.copy(fontSize = 13.sp), color = if (why == null) Ink.brass else Ink.faint)
                        Text("${p.months} meses", style = Type.small, color = Ink.muted)
                    }
                }
            }
            if (!built && why != null && !why.startsWith("Tesouro") && !why.startsWith("Em obras")) Text(why, style = Type.small, color = Ink.amber, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun EconHistory(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val h = c.game.history
    val me = c.me
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Text("Últimos ${h.size} meses.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        LineChart("PIB", h.map { it.gdp.toFloat() }, Fmt.money(c.game.natGdp(me.id)), Ink.good)
        Spacer(Modifier.height(10.dp))
        LineChart("Tesouro", h.map { it.treasury.toFloat() }, Fmt.money(me.treasury), Ink.brass)
        Spacer(Modifier.height(10.dp))
        LineChart("Dívida (% do PIB)", h.map { it.debtRatio * 100 }, "${((h.lastOrNull()?.debtRatio ?: 0f) * 100).toInt()}%", Ink.coral, higherIsBetter = false)
        Spacer(Modifier.height(10.dp))
        LineChart("Estabilidade", h.map { it.stability }, "${me.stability.toInt()}", Ink.verdigris)
        Spacer(Modifier.height(10.dp))
        LineChart("Força terrestre", h.map { it.power }, "${c.sim.landPower(me.id).toInt()}", Ink.amber)
    }
}
