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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.sim.Army
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.General
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.Nation
import com.dmwnezes.soberano.sim.Promise
import com.dmwnezes.soberano.sim.Resource
import com.dmwnezes.soberano.sim.ResolutionType
import com.dmwnezes.soberano.sim.START_DATE
import com.dmwnezes.soberano.sim.UnitType

private val MONTHS_LONG = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")

// ================================================================ generais

@Composable
fun GeneralRow(c: GameController, a: Army) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = a.general
    val mine = a.owner == c.game.player
    if (g == null) {
        if (mine && a.domain == Domain.LAND) {
            var pick by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(Shapes.tile).background(Ink.oceanDeep).clickable { pick = true }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PersonAdd, null, tint = Ink.brass, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sem general. Toque para nomear", style = Type.label, color = Ink.text, modifier = Modifier.weight(1f))
                Text("⚡${c.sim.military.generalCost.toInt()}", style = Type.small, color = Ink.muted)
            }
            if (pick) AppointDialog(c, a) { pick = false }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(Shapes.tile).background(Ink.oceanDeep).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Ink.brass.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("${g.level}", style = Type.number, color = Ink.brass)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name, style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${g.trait.label}: ${g.trait.desc}", style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (g.level < 5) Meter(g.xp / g.xpNeeded() * 100, Modifier.fillMaxWidth().padding(top = 4.dp), Ink.brass, 4.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("Ataque", style = Type.small, color = Ink.muted); Stars(g.attack)
            Text("Defesa", style = Type.small, color = Ink.muted); Stars(g.defense, Ink.verdigris)
        }
    }
}

@Composable
private fun AppointDialog(c: GameController, a: Army, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Overlay(onClose) {
        Text("Nomear general", style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
        Text("Generais dão bônus ao ${a.name} e sobem de nível vencendo batalhas.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(10.dp))
        for (g in c.game.generalPool.toList()) GeneralOption(g) {
            c.act { c.sim.military.appoint(a, g) }; onClose()
        }
        Text("Custa ⚡${c.sim.military.generalCost.toInt()} de capital político. Novos nomes aparecem quando você nomeia alguém.",
            style = Type.small, color = Ink.faint, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GeneralOption(g: General, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised).clickable(onClick = onPick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.name, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
            Text("${g.trait.label}: ${g.trait.desc}", style = Type.small, color = Ink.good)
        }
        Column(horizontalAlignment = Alignment.End) {
            Stars(g.attack); Stars(g.defense, Ink.verdigris)
        }
    }
}

// ================================================================ fortificação (cartão da província)

@Composable
fun FortRow(c: GameController, p: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val lvl = g.fort.getOrElse(p) { 0 }
    val building = g.fortBuild.getOrElse(p) { 0 }
    val mine = g.owner[p] == g.player && g.controller[p] == g.player
    if (lvl == 0 && building == 0 && !mine) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Fort, null, tint = if (lvl > 0) Ink.brass else Ink.muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                building > 0 -> "Fortificação nível ${lvl + 1} em obras: $building dias"
                lvl > 0 -> "Fortificação nível $lvl: defesa +${lvl * 15}%, cerco ${50 * lvl}% mais lento"
                else -> "Sem fortificação"
            }, style = Type.small, color = if (lvl > 0) Ink.text else Ink.muted, modifier = Modifier.weight(1f),
        )
        if (mine && building == 0 && lvl < 3) {
            val why = c.sim.military.canFortify(c.me, p)
            Text(
                "Fortificar ${Fmt.money(c.sim.military.fortCost(p))}", style = Type.small.copy(fontWeight = FontWeight.SemiBold),
                color = if (why == null) Ink.onBrass else Ink.faint,
                modifier = Modifier.clip(Shapes.chip).background(if (why == null) Ink.brass else Ink.raised)
                    .clickable { c.act("Obra começou: ${c.sim.military.fortDays} dias") { c.sim.military.fortify(c.me, p) } }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

// ================================================================ ONU

@Composable
fun UnVoteCard(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val r = c.game.un.pending ?: return
    val un = c.sim.unitedNations
    val (y, n, a) = remember(r) { un.forecast(r) }
    val veto = remember(r) { un.vetoThreat(r) }
    Overlay({}, dismissable = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF4A90D9).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Public, null, tint = Color(0xFF7DB4EE), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Votação na ONU", style = Type.small, color = Ink.muted)
                Text(r.type.label, style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(un.title(r).replaceFirstChar { it.uppercase() }, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
        Text("Proposta de ${c.sim.nation(r.proposer).name}", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(6.dp))
        Text(un.explain(r), style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Text("Previsão sem o seu voto", style = Type.small, color = Ink.muted)
                Spacer(Modifier.height(6.dp))
                val tot = (y + n + a).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(10.dp).clip(Shapes.chip)) {
                    if (y > 0) Box(Modifier.weight(y.toFloat() / tot).height(10.dp).background(Ink.good))
                    if (a > 0) Box(Modifier.weight(a.toFloat() / tot).height(10.dp).background(Ink.line))
                    if (n > 0) Box(Modifier.weight(n.toFloat() / tot).height(10.dp).background(Ink.coral))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("$y sim", style = Type.small, color = Ink.good, modifier = Modifier.weight(1f))
                    Text("$a abstenções", style = Type.small, color = Ink.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("$n não", style = Type.small, color = Ink.coral, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                if (veto != null) Text("${c.sim.nation(veto).name} deve vetar", style = Type.small, color = Ink.amber, modifier = Modifier.padding(top = 6.dp))
                if (un.isPermanent(c.game.player) && veto == null && r.type != ResolutionType.AID && r.type != ResolutionType.CLIMATE)
                    Text("Você é membro permanente: votar não é veto", style = Type.small, color = Ink.brass, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Não", Modifier.weight(1f), BtnKind.DANGER) { un.resolve(-1); c.refresh() }
            Btn("Abster", Modifier.weight(1f), BtnKind.GHOST) { un.resolve(0); c.refresh() }
            Btn("Sim", Modifier.weight(1f), BtnKind.PRIMARY) { un.resolve(1); c.refresh() }
        }
    }
}

/** Aba ONU da gaveta Mundo. */
@Composable
fun WorldUn(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val un = c.sim.unitedNations
    var proposing by remember { mutableStateOf(false) }
    SheetBody {
        Spacer(Modifier.height(8.dp))
        Text("Sessões a cada 5 a 8 meses. Membros permanentes (EUA, Rússia, China, Reino Unido e França) podem vetar sanções, condenações e missões de paz.",
            style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(10.dp))
        Btn("Propor resolução (⚡${un.proposeCost.toInt()})", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Gavel,
            enabled = g.un.pending == null && c.me.politicalCapital >= un.proposeCost) { proposing = true }
        if (g.un.climateUntil >= g.day) {
            Spacer(Modifier.height(8.dp))
            Text("Acordo do clima em vigor até ${dateText(g.un.climateUntil)} (${g.un.climateMembers.size} países)" +
                if (g.player in g.un.climateMembers) ", com você" else "", style = Type.small, color = Ink.good)
        }
        SectionTitle("Últimas resoluções")
        if (g.un.history.isEmpty()) Text("Nenhuma votação ainda.", style = Type.body, color = Ink.muted)
        for (rec in g.un.history) {
            val col = if (rec.passed) Ink.good else Ink.coral
            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Shapes.tile).background(Ink.raised).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(GameIcons.of(rec.type.icon), null, tint = col, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(rec.type.label + if (rec.target >= 0) ": ${c.sim.nation(rec.target).name}" else "", style = Type.label, color = Ink.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (rec.passed) "aprovada" else if (rec.vetoBy >= 0) "vetada" else "rejeitada", style = Type.small, color = col)
                }
                Text("${rec.yes} sim · ${rec.no} não · ${rec.abstain} abst." + (if (rec.vetoBy >= 0) " · veto de ${c.sim.nation(rec.vetoBy).name}" else "") +
                    " · você: " + when (rec.playerVote) { 1 -> "sim"; -1 -> "não"; else -> "absteve" }, style = Type.small, color = Ink.muted)
            }
        }
    }
    if (proposing) ProposeDialog(c) { proposing = false }
}

@Composable
private fun ProposeDialog(c: GameController, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var type by remember { mutableStateOf<ResolutionType?>(null) }
    val g = c.game
    Box(Modifier.fillMaxSize().background(Ink.panelSolid).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack) { if (type != null) type = null else onClose() }
                Spacer(Modifier.width(12.dp))
                Text(if (type == null) "Nova resolução" else type!!.label, style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
            }
            Spacer(Modifier.height(12.dp))
            val t = type
            if (t == null) {
                for (rt in ResolutionType.entries) ActionRow(GameIcons.of(rt.icon), rt.label, when (rt) {
                    ResolutionType.SANCTIONS -> "Todos que votarem sim sancionam o país"
                    ResolutionType.PEACEKEEPING -> "Encerra uma guerra em paz branca"
                    ResolutionType.CONDEMN -> "O mundo se afasta do país"
                    ResolutionType.AID -> "Países ricos doam a um país necessitado"
                    ResolutionType.CLIMATE -> "Mais meio ambiente, um pouco menos de crescimento"
                }, enabled = rt != ResolutionType.CLIMATE || g.un.climateUntil < g.day) {
                    if (rt == ResolutionType.CLIMATE) { c.act("Resolução enviada à ONU") { c.sim.unitedNations.propose(rt, -1) }; onClose() } else type = rt
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (t == ResolutionType.PEACEKEEPING) {
                        if (g.wars.isEmpty()) Text("Não há guerras.", style = Type.body, color = Ink.muted)
                        for (w in g.wars) ActionRow(GameIcons.of("dove"), w.name.removePrefix("Guerra "), "${(g.day - w.start) / 30} meses", true) {
                            c.act("Resolução enviada à ONU") { c.sim.unitedNations.propose(t, w.leaderA(), w.leaderD()) }; onClose()
                        }
                    } else {
                        val list = g.nations.filter { it.alive && it.id != g.player }.sortedWith(compareByDescending<Nation> { c.sim.atWar(g.player, it.id) }.thenBy { g.rel(g.player, it.id) })
                        for (n in list.take(80)) Row(
                            Modifier.fillMaxWidth().clip(Shapes.small).clickable {
                                c.act("Resolução enviada à ONU") { c.sim.unitedNations.propose(t, n.id) }; onClose()
                            }.padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FlagView(n, 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(n.name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                            RelationBadge(g.rel(g.player, n.id))
                        }
                    }
                }
            }
        }
    }
}

// ================================================================ campanha e eleição

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampaignPanel(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val camp = c.sim.politics.campaigns
    val g = c.game
    val me = c.me
    if (!camp.active(me) && g.campaign.promises.none { !it.resolved }) return
    var groupPick by remember { mutableStateOf(false) }
    var promisePick by remember { mutableStateOf(false) }
    var debateText by remember { mutableStateOf<String?>(null) }
    Spacer(Modifier.height(10.dp))
    Column(Modifier.fillMaxWidth().clip(Shapes.card).background(Ink.brass.copy(alpha = 0.10f)).border(1.dp, Ink.brass.copy(alpha = 0.4f), Shapes.card).padding(14.dp)) {
        if (camp.active(me)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Campaign, null, tint = Ink.brass)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Campanha eleitoral", style = Type.heading, color = Ink.text)
                    Text("${camp.daysLeft(me)} dias para a eleição · contra ${g.campaign.opponent.ifEmpty { "a oposição" }}", style = Type.small, color = Ink.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${Fmt.one(c.sim.politics.voteShare(me))}%", style = Type.number, color = Ink.scale(c.sim.politics.voteShare(me) * 1.6f - 30f))
                    Text("intenção de voto", style = Type.small, color = Ink.muted)
                }
            }
            if (g.campaign.boost != 0f) Text("Efeito da campanha: ${if (g.campaign.boost > 0) "+" else ""}${Fmt.one(g.campaign.boost)} pontos",
                style = Type.small, color = if (g.campaign.boost > 0) Ink.good else Ink.coral, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Comício ${Fmt.money(camp.rallyCost(me))}", icon = Icons.Rounded.Groups) { groupPick = true }
                Btn("TV ${Fmt.money(camp.adsCost(me))} (${3 - g.campaign.adsDone})", icon = Icons.Rounded.Tv, enabled = g.campaign.adsDone < 3) { c.act("Campanha na TV no ar") { camp.ads() } }
                Btn("Debate", icon = Icons.Rounded.Mic, enabled = !g.campaign.debateDone) { debateText = camp.debate(); c.refresh() }
                Btn("Promessa", icon = Icons.Rounded.Handshake, enabled = g.campaign.promises.count { !it.resolved } < 3) { promisePick = true }
            }
            debateText?.let { Text(it, style = Type.label, color = Ink.brass, modifier = Modifier.padding(top = 8.dp)) }
        }
        val open = g.campaign.promises.filter { !it.resolved }
        if (open.isNotEmpty()) {
            Text("Promessas", style = Type.label, color = Ink.muted, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            for (pm in open) {
                val ok = camp.kept(me, pm)
                val left = (365 - (g.day - pm.day)).coerceAtLeast(0)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule, null, tint = if (ok) Ink.good else Ink.amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pm.promise.label, style = Type.label, color = Ink.text)
                        Text(pm.promise.desc, style = Type.small, color = Ink.muted)
                    }
                    Text("$left dias", style = Type.small, color = Ink.muted)
                }
            }
        }
    }
    if (groupPick) Overlay({ groupPick = false }) {
        Text("Comício para quem?", style = Type.heading, color = Ink.text)
        Spacer(Modifier.height(8.dp))
        for (gr in Group.entries) ActionRow(GameIcons.group(gr), gr.label, "apoio hoje ${me.groups[gr.ordinal].toInt()}", true) {
            c.act("Comício realizado") { camp.rally(gr) }; groupPick = false
        }
    }
    if (promisePick) Overlay({ promisePick = false }) {
        Text("Prometer o quê?", style = Type.heading, color = Ink.text)
        Text("Agrada o grupo agora. Em um ano o povo cobra: cumprir dá bônus, quebrar custa caro.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(8.dp))
        for (p in Promise.entries) ActionRow(GameIcons.group(p.group), p.label, "${p.group.label}: ${p.desc.lowercase()}",
            g.campaign.promises.none { it.promise == p && !it.resolved }) {
            c.act("Promessa feita") { camp.promise(p) }; promisePick = false
        }
    }
}

@Composable
fun ElectionCard(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val e = c.game.election ?: return
    if (e.seen) return
    Overlay({}, dismissable = false) {
        Icon(if (e.won) Icons.Rounded.HowToVote else Icons.Rounded.HeartBroken, null, tint = if (e.won) Ink.brass else Ink.coral,
            modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(6.dp))
        Text(if (e.won) "Reeleito!" else "Derrota nas urnas", style = Type.title, color = Ink.text, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        val other = 100f - e.myShare
        Row(Modifier.fillMaxWidth().height(14.dp).clip(Shapes.chip)) {
            Box(Modifier.weight(e.myShare).height(14.dp).background(Ink.brass))
            Box(Modifier.weight(other).height(14.dp).background(Ink.muted))
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("${Fmt.one(e.myShare)}%", style = Type.number, color = Ink.brass)
                Text("Você", style = Type.small, color = Ink.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Fmt.one(other)}%", style = Type.number, color = Ink.text)
                Text(e.opponent, style = Type.small, color = Ink.muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Btn("Continuar", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { e.seen = true; c.refresh() }
    }
}

// ================================================================ jornal

@Composable
fun NewspaperPill(c: GameController, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val p = c.game.paper ?: return
    if (p.day == c.paperSeenDay) return
    val d = START_DATE.plusDays(p.day.toLong()).minusDays(1)
    Row(
        modifier.clip(Shapes.chip).background(Color(0xFFEDE6D3)).clickable { c.showPaper = true; c.paused = true }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Newspaper, null, tint = Color(0xFF2B2418), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Jornal de ${MONTHS_LONG[d.monthValue - 1]}", style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF2B2418))
    }
}

@Composable
fun NewspaperOverlay(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val p = c.game.paper ?: return
    val ink = Color(0xFF231D13)
    val paper = Color(0xFFF1EBDC)
    val serif = FontFamily.Serif
    val d = START_DATE.plusDays(p.day.toLong()).minusDays(1)
    Box(
        Modifier.fillMaxSize().background(Color(0xCC0A0D08)).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
        ) { c.paperSeenDay = p.day; c.showPaper = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(paper)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(18.dp),
        ) {
            Text("O SOBERANO", style = Type.title.copy(fontFamily = serif, fontWeight = FontWeight.Black, fontSize = 34.sp, letterSpacing = 2.sp),
                color = ink, modifier = Modifier.align(Alignment.CenterHorizontally))
            HorizontalDivider(color = ink, thickness = 2.dp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${c.me.name.uppercase()}", style = Type.small.copy(fontFamily = serif), color = ink, modifier = Modifier.weight(1f))
                Text("${MONTHS_LONG[d.monthValue - 1]} de ${d.year}", style = Type.small.copy(fontFamily = serif, fontStyle = FontStyle.Italic), color = ink)
            }
            HorizontalDivider(color = ink, thickness = 0.8.dp)
            Spacer(Modifier.height(12.dp))
            Text(p.headline, style = Type.title.copy(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp), color = ink)
            Spacer(Modifier.height(6.dp))
            Text(p.sub, style = Type.body.copy(fontFamily = serif, fontStyle = FontStyle.Italic), color = ink.copy(alpha = 0.75f))
            PaperSection("Nacional", p.national, ink, serif)
            PaperSection("Mundo", p.world, ink, serif)
            PaperSection("Economia", p.economy, ink, serif)
            Spacer(Modifier.height(14.dp))
            Text("Toque fora para fechar", style = Type.small, color = ink.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ColumnScope.PaperSection(title: String, items: List<String>, ink: Color, serif: FontFamily) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Text(title.uppercase(), style = Type.label.copy(fontFamily = serif, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp), color = ink)
    HorizontalDivider(color = ink.copy(alpha = 0.5f), thickness = 0.6.dp, modifier = Modifier.padding(vertical = 4.dp))
    for (t in items) Text("▪ $t", style = Type.body.copy(fontFamily = serif, fontSize = 15.sp), color = ink, modifier = Modifier.padding(vertical = 3.dp))
}

private fun dateText(day: Int): String {
    val d = START_DATE.plusDays(day.toLong())
    return "${d.dayOfMonth}/${d.monthValue}/${d.year}"
}

// ================================================================ comércio (gaveta Diplomacia)

@Composable
fun TradeSection(c: GameController, n: Nation) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val g = c.game
    val me = g.player
    val dev = c.sim.dev
    var arms by remember { mutableStateOf(false) }
    SectionTitle("Comércio")
    for (r in Resource.entries) {
        val mySurplus = dev.surplus(me, r)
        val theirNeed = -dev.surplus(n.id, r)
        val deal = g.deals.firstOrNull { it.resource == r && ((it.seller == me && it.buyer == n.id) || (it.seller == n.id && it.buyer == me)) }
        if (deal != null) {
            val selling = deal.seller == me
            ActionRow(GameIcons.of(r.icon), "${if (selling) "Vendendo" else "Comprando"} ${r.label.lowercase()}",
                "${Fmt.money(deal.amount.toDouble())}/ano até ${dateText(deal.until)}", true, active = true) {}
            continue
        }
        if (mySurplus < 1f || theirNeed < 1f) continue
        val amount = minOf(mySurplus, theirNeed) * 0.6f
        val why = dev.dealRefusal(me, n.id, r, amount)
        val yearly = amount * g.prices[r.ordinal] * dev.dealPremium
        ActionRow(GameIcons.of(r.icon), "Vender ${r.label.lowercase()} por ${dev.dealYears} anos",
            why ?: "${Fmt.money(amount.toDouble())}/ano, paga ${Fmt.money(yearly / 12.0 * 0.5)}/mês ao seu tesouro", why == null) {
            c.act("Contrato assinado") { dev.sellResource(me, n.id, r, amount) }
        }
    }
    ActionRow(Icons.Rounded.LocalShipping, "Vender armas", "Tropas paradas viram dinheiro e amizade", !c.sim.atWar(me, n.id)) { arms = true }
    if (arms) ArmsDialog(c, n) { arms = false }
}

@Composable
private fun ArmsDialog(c: GameController, n: Nation, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val dev = c.sim.dev
    val me = c.game.player
    var type by remember { mutableStateOf(UnitType.INFANTRY) }
    var qty by remember { mutableIntStateOf(1) }
    val avail = dev.armsAvailable(me, type)
    Overlay(onClose) {
        Text("Vender armas para ${n.name}", style = Type.heading, color = Ink.text)
        Text("Caixa deles: ${Fmt.money(n.treasury)}", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
            for (t in UnitType.entries) {
                val have = dev.armsAvailable(me, t)
                if (have < 1) continue
                val sel = t == type
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(Shapes.small).background(if (sel) Ink.brass.copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { type = t; qty = 1 }.padding(10.dp),
                ) {
                    Text(t.label, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                    Text("$have disponíveis", style = Type.small, color = Ink.muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.Rounded.Remove, size = 36.dp) { qty = (qty - 1).coerceAtLeast(1) }
            Text("$qty", style = Type.number, color = Ink.text, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
            IconBtn(Icons.Rounded.Add, size = 36.dp) { qty = (qty + 1).coerceAtMost(avail.coerceAtLeast(1)) }
            Spacer(Modifier.width(12.dp))
            Text(Fmt.money(dev.armsPrice(type, qty)), style = Type.number, color = Ink.brass)
        }
        val why = dev.armsRefusal(me, n.id, type, qty)
        if (why != null) Text(why, style = Type.small, color = Ink.coral, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Btn("Vender", Modifier.fillMaxWidth(), BtnKind.PRIMARY, enabled = why == null) { c.act("Venda concluída") { dev.sellArms(me, n.id, type, qty) }; onClose() }
    }
}

// ================================================================ atalhos do mapa

@Composable
fun MapShortcuts(c: GameController, onMyCountry: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconBtn(Icons.Rounded.Search, bg = Ink.panel, size = 44.dp) { c.showSearch = true }
        IconBtn(Icons.Rounded.MyLocation, bg = Ink.panel, size = 44.dp) { onMyCountry() }
    }
}

@Composable
fun MapLegend(c: GameController, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val items: List<Pair<Color, String>> = when (c.mapMode) {
        MapMode.POLITICAL -> return
        MapMode.RELATIONS -> listOf(Ink.brass to (if (c.focusNation >= 0) c.sim.nation(c.focusNation).name else "Você"), Ink.verdigris to "Aliado",
            Color(0xFF7FA35A) to "Amigo", Color(0xFF7C806A) to "Neutro", Color(0xFFB5493A) to "Rival", Ink.coral to "Em guerra")
        MapMode.WAR -> listOf(Ink.brass to "Você", Ink.verdigris to "Aliado", Ink.coral to "Inimigo", Color(0xFF9C7A4A) to "Em outra guerra", Color(0xFF4E5444) to "Em paz")
        MapMode.APPROVAL -> listOf(Color(0xFF7FA35A) to "Aprovação alta", Color(0xFFC9A04F) to "Média", Color(0xFFB5493A) to "Baixa")
        MapMode.WEALTH -> listOf(Color(0xFFD9CFA0) to "Renda alta", Color(0xFF8A8A6A) to "Média", Color(0xFF3A4234) to "Baixa")
    }
    Column(modifier.clip(Shapes.tile).background(Ink.panel).padding(10.dp)) {
        Text(c.mapMode.label, style = Type.small.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
        for ((col, label) in items) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(col))
            Spacer(Modifier.width(6.dp))
            Text(label, style = Type.small, color = Ink.muted, maxLines = 1)
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.coral.copy(alpha = 0.5f)))
            Spacer(Modifier.width(6.dp))
            Text("Listras: ocupada", style = Type.small, color = Ink.muted)
        }
    }
}

/** Busca de províncias e países. */
@Composable
fun ProvinceSearch(c: GameController, onPick: (Int) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var q by remember { mutableStateOf("") }
    val w = c.sim.world
    val g = c.game
    val results = remember(q) {
        val norm = strip(q.trim().lowercase())
        if (norm.length < 2) emptyList()
        else w.provinces.filter { p -> strip(p.name.lowercase()).contains(norm) || strip(c.sim.nation(g.owner[p.id]).name.lowercase()).startsWith(norm) }
            .sortedWith(compareByDescending<com.dmwnezes.soberano.world.Province> { strip(it.name.lowercase()).startsWith(norm) }.thenByDescending { g.provPop[it.id] })
            .take(60)
    }
    Box(Modifier.fillMaxSize().background(Color(0xF00A0D08)).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack) { c.showSearch = false }
                Spacer(Modifier.width(12.dp))
                SearchField(q, "Província ou país", Modifier.weight(1f)) { q = it }
            }
            Spacer(Modifier.height(10.dp))
            if (q.trim().length < 2) Text("Digite pelo menos 2 letras.", style = Type.small, color = Ink.muted)
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { p ->
                    val owner = c.sim.nation(g.owner[p.id])
                    Row(
                        Modifier.fillMaxWidth().clip(Shapes.tile).clickable { onPick(p.id) }.padding(horizontal = 6.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlagView(owner, 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = Type.body, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(owner.name + if (owner.capital == p.id) ", capital" else "", style = Type.small, color = Ink.muted)
                        }
                        Text(Fmt.pop(g.provPop[p.id]), style = Type.small, color = Ink.faint)
                    }
                }
            }
        }
    }
}

/** Ajustes de som e vibração (menu). */
@Composable
fun SoundToggles(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val fb = c.feedback ?: return
    var s by remember { mutableStateOf(fb.sound) }
    var v by remember { mutableStateOf(fb.vibration) }
    ToggleRow("Sons", s, Icons.Rounded.VolumeUp) { s = it; fb.sound = it; if (it) fb.play(com.dmwnezes.soberano.ui.Sfx.VICTORY) }
    ToggleRow("Vibração", v, Icons.Rounded.Vibration) { v = it; fb.vibration = it; if (it) fb.play(com.dmwnezes.soberano.ui.Sfx.CAPTURE) }
}

@Suppress("unused") private val keepWidth = Modifier.widthIn(0.dp)
