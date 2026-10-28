package com.dmwnezes.soberano.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Army
import com.dmwnezes.soberano.sim.Content
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.PendingEvent
import com.dmwnezes.soberano.sim.TreatyType
import com.dmwnezes.soberano.sim.UnitType
import kotlin.math.ceil

private val MONTHS = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

@Composable
fun GameScreen(
    c: GameController,
    onExit: () -> Unit,
    onLoad: (String) -> Unit,
    onSwitchCountry: (Int) -> Unit,
    store: SaveStore,
) {
    val camera = c.camera
    var frame by remember { mutableLongStateOf(0L) }
    var nukeTargeting by remember { mutableStateOf(false) }
    var confirmNuke by remember { mutableStateOf(-1) }
    /** país contra o qual o jogador quer declarar guerra para atacar (-1 = nenhum) */
    var warTarget by remember { mutableStateOf(-1) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember(c) { c.feedback = Feedback(ctx); 0 }

    // relógio do jogo; o mapa anima a no máximo ~30 quadros por segundo (bateria)
    LaunchedEffect(c) {
        var last = 0L
        var lastDraw = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L) c.onFrame((t - last) / 1e9, System.currentTimeMillis())
            last = t
            val animating = !c.paused || c.game.battles.isNotEmpty() || c.orderTarget >= 0 ||
                c.game.armies.any { it.owner == c.game.player && it.moving }
            if (animating && t - lastDraw >= 33_000_000L) { frame++; lastDraw = t }
        }
    }
    LaunchedEffect(c.focusRequest) {
        val p = c.focusRequest
        if (p >= 0 && camera.scale > 0f) {
            val pr = c.sim.world.provinces[p]
            camera.centerOn(pr.cx, pr.cy, maxOf(camera.scale, camera.minScale * 4f))
            c.focusRequest = -1
        }
    }

    BackHandler {
        when {
            nukeTargeting -> nukeTargeting = false
            c.orderTarget >= 0 -> c.clearTarget()
            c.tab != null -> c.tab = null
            c.selArmy >= 0 || c.selProvince >= 0 -> { c.selArmy = -1; c.selProvince = -1 }
            else -> c.showSettings = true
        }
    }

    @Suppress("UNUSED_EXPRESSION") c.tick
    val game = c.game
    val selArmy = game.armies.firstOrNull { it.id == c.selArmy }

    Box(Modifier.fillMaxSize()) {
        WorldMapView(
            c.sim, camera, c, Modifier.fillMaxSize(),
            mode = c.mapMode, focusNation = c.focusNation.takeIf { c.mapMode == MapMode.RELATIONS } ?: -1,
            outlineNation = game.player,
            highlightNation = c.focusNation.takeIf { c.tab == Tab.DIPLOMACY } ?: (if (c.selProvince >= 0) game.owner[c.selProvince] else -1),
            selectedProvince = if (c.orderTarget >= 0) c.orderTarget else c.selProvince,
            frame = { frame + c.tick },
            initialCenter = c.me.capital,
            onTapProvince = { p ->
                if (nukeTargeting) {
                    if (p >= 0) confirmNuke = p
                    nukeTargeting = false
                    return@WorldMapView
                }
                val a = game.armies.firstOrNull { it.id == c.selArmy }
                if (a != null && a.owner == game.player && p >= 0) {
                    if (p == a.province) { c.clearTarget(); c.selArmy = -1; c.selProvince = p; c.refresh(); return@WorldMapView }
                    // mostra o cartão de ordem: atacar, mover ou declarar guerra
                    c.chooseTarget(p)
                } else { c.clearTarget(); c.selectProvince(p) }
            },
            onTapArmy = { a ->
                val selArmy = game.armies.firstOrNull { it.id == c.selArmy }
                if (selArmy != null && selArmy.owner == game.player && a.owner != game.player) {
                    c.chooseTarget(a.province)
                } else { c.clearTarget(); c.selArmy = if (c.selArmy == a.id) -1 else a.id; c.selProvince = -1; c.refresh() }
            },
        )

        // degradê para leitura da barra de cima
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Ink.oceanDeep.copy(alpha = 0.85f), Color.Transparent))))

        Column(Modifier.fillMaxSize()) {
            TopBar(c)
            Toasts(c)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
                MapLegend(c, Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    MapShortcuts(c) { fitNation(c.sim, camera, game.player, c.me.capital) }
                    Spacer(Modifier.height(8.dp))
                    MapModeButton(c)
                }
            }
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(c.tab == null && selArmy != null && c.orderTarget < 0, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    selArmy?.let { ArmyCard(c, it) }
                }
                androidx.compose.animation.AnimatedVisibility(c.tab == null && selArmy != null && c.orderTarget >= 0, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    if (selArmy != null && c.orderTarget >= 0) OrderCard(c, selArmy, c.orderTarget, onDeclare = { warTarget = it })
                }
                androidx.compose.animation.AnimatedVisibility(c.tab == null && selArmy == null && c.selProvince >= 0, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
                    if (c.selProvince >= 0) ProvinceCard(c, c.selProvince)
                }
            }
            if (c.tab == null && game.proposals.isNotEmpty() && c.selArmy < 0 && c.selProvince < 0) ProposalBanner(c)
            if (c.tab == null && c.selArmy < 0 && c.selProvince < 0) NewspaperPill(c, Modifier.padding(start = 12.dp, bottom = 6.dp))
            Dock(c)
        }

        // gavetas
        AnimatedVisibility(c.tab != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.align(Alignment.BottomCenter)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val h = maxHeight
                Column(
                    Modifier.fillMaxWidth().height(h * 0.68f).clip(Shapes.sheet).background(Ink.panelSolid),
                ) {
                    SheetHeader(c)
                    Box(Modifier.weight(1f)) {
                        when (c.tab) {
                            Tab.GOVERNMENT -> GovernmentSheet(c)
                            Tab.ECONOMY -> EconomySheet(c)
                            Tab.DIPLOMACY -> DiplomacySheet(c)
                            Tab.MILITARY -> MilitarySheet(c, onNuke = { nukeTargeting = true; c.tab = null; c.say("Toque no alvo do míssil", 2, "nuke") })
                            Tab.WORLD -> WorldSheet(c)
                            null -> {}
                        }
                    }
                    Dock(c, inSheet = true)
                }
            }
        }

        if (nukeTargeting) {
            Text("Toque no alvo", style = Type.heading, color = Ink.coral,
                modifier = Modifier.align(Alignment.Center).clip(Shapes.chip).background(Ink.panel).padding(horizontal = 18.dp, vertical = 10.dp))
        }

        // carta de evento
        game.events.firstOrNull()?.let { EventCard(c, it) }

        if (confirmNuke >= 0) NukeConfirm(c, confirmNuke) { confirmNuke = -1 }

        // votação da ONU e resultado de eleição (depois dos eventos)
        if (game.events.isEmpty()) {
            if (game.un.pending != null) UnVoteCard(c)
            else ElectionCard(c)
        }
        if (c.showPaper) NewspaperOverlay(c)
        when (c.dialog) {
            "spread" -> SpreadDialog(c) { c.dialog = null }
            "gather" -> GatherDialog(c) { c.dialog = null }
            "speech" -> SpeechDialog(c)
            "build" -> if (c.selProvince >= 0) BuildDialog(c, c.selProvince) else c.dialog = null
            "disband" -> game.armies.firstOrNull { it.id == c.selArmy }?.let { DisbandDialog(c, it) } ?: run { c.dialog = null }
        }
        if (c.showSearch) ProvinceSearch(c) { p ->
            c.showSearch = false; c.focusRequest = p; c.selectProvince(p)
        }

        if (warTarget >= 0) {
            val enemy = c.sim.nation(warTarget)
            val army = selArmy
            val resp = remember(warTarget) { c.sim.diplomacy.allyResponse(game.player, enemy.id) }
            Overlay({ warTarget = -1 }) {
                Icon(Icons.Rounded.Whatshot, null, tint = Ink.coral, modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally))
                Text("Declarar guerra a ${enemy.name}?", style = Type.title.copy(fontSize = 22.sp), color = Ink.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    allyText(c, resp) +
                        if (c.sim.hasTreaty(TreatyType.NON_AGGRESSION, game.player, enemy.id)) " Você vai quebrar um pacto de não agressão." else "",
                    style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { warTarget = -1 }
                    Btn("Declarar e atacar", Modifier.weight(1f), BtnKind.DANGER) {
                        c.sim.diplomacy.declareWar(game.player, enemy.id)
                        if (army != null && c.orderTarget >= 0) {
                            val why = c.sim.military.order(army, c.orderTarget)
                            if (why != null) c.say(why, 2) else c.say("${army.name} avança sobre ${c.sim.world.provinces[c.orderTarget].name}", 0, "battle")
                        }
                        warTarget = -1; c.clearTarget(); c.mapMode = MapMode.WAR; c.refresh()
                    }
                }
            }
        }

        game.gameOver?.let { go ->
            Overlay({}, dismissable = false) {
                Icon(if (go.win) Icons.Rounded.EmojiEvents else Icons.Rounded.HeartBroken, null,
                    tint = if (go.win) Ink.brass else Ink.coral, modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text(if (go.win) "Vitória" else "Fim do governo", style = Type.title, color = Ink.text, modifier = Modifier.align(Alignment.CenterHorizontally))
                Text(go.reason, style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Spacer(Modifier.height(12.dp))
                if (go.win) {
                    Btn("Continuar governando", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { game.gameOver = null; c.refresh() }
                } else {
                    var pick by remember { mutableStateOf(false) }
                    Btn("Assumir outro país", Modifier.fillMaxWidth(), BtnKind.PRIMARY) { pick = true }
                    if (pick) CountrySearch(c.sim, onDismiss = { pick = false }) { onSwitchCountry(it) }
                }
                Spacer(Modifier.height(8.dp))
                Btn("Menu inicial", Modifier.fillMaxWidth(), BtnKind.GHOST) { onExit() }
            }
        }

        if (c.lawEditor) LawEditor(c) { c.lawEditor = false; c.refresh() }
        c.lawInfo?.let { id -> LawInfo(c, id) { c.lawInfo = null; c.refresh() } }

        if (c.showSettings) SettingsOverlay(c, store, onExit, onLoad)
    }
}

fun etaDays(c: GameController, a: Army): Int {
    if (a.path.isEmpty()) return 0
    var km = a.legKm - a.progress
    var prev = a.path[0]
    for (i in 1 until a.path.size) { km += c.sim.military.legKm(prev, a.path[i]); prev = a.path[i] }
    return ceil(km / c.sim.military.armySpeed(a).coerceAtLeast(1f)).toInt().coerceAtLeast(1)
}

// ---------------------------------------------------------------- barra superior

@Composable
private fun TopBar(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val me = c.me
    val g = c.game
    val d = c.sim.date()
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(Shapes.chip).clickable { c.openTab(Tab.GOVERNMENT) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlagView(me, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(me.name, style = Type.heading.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.dayOfMonth} ${MONTHS[d.monthValue - 1]} ${d.year}", style = Type.small.copy(fontFeatureSettings = "tnum"), color = Ink.muted)
                }
            }
            SpeedControl(c)
            Spacer(Modifier.width(8.dp))
            IconBtn(Icons.Rounded.Menu, size = 38.dp, bg = Ink.panel) { c.paused = true; c.showSettings = true }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatPill(Icons.Rounded.Payments, Fmt.money(me.treasury).removePrefix("US$ "), if (me.treasury < g.natGdp(me.id) * 0.005) Ink.amber else Ink.text) { c.openTab(Tab.ECONOMY) }
            StatPill(Icons.Rounded.ThumbUp, "${me.approval.toInt()}%", Ink.scale(me.approval)) { c.openTab(Tab.GOVERNMENT) }
            StatPill(Icons.Rounded.Balance, "${me.stability.toInt()}", Ink.scale(me.stability)) { c.openTab(Tab.GOVERNMENT) }
            StatPill(Icons.Rounded.Bolt, "${me.politicalCapital.toInt()}", Ink.brass) { c.openTab(Tab.GOVERNMENT) }
            val wars = g.wars.count { it.involves(me.id) }
            if (wars > 0) StatPill(Icons.Rounded.Whatshot, "$wars", Ink.coral) { c.openTab(Tab.WORLD) }
            if (g.autoWar) StatPill(Icons.Rounded.SmartToy, "Auto", Ink.brass) { c.subTab[Tab.MILITARY] = 2; c.openTab(Tab.MILITARY) }
        }
    }
}

@Composable
private fun SpeedControl(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Row(Modifier.clip(Shapes.chip).background(Ink.panel).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (c.paused) Ink.brass else Ink.raised).clickable { c.togglePause() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (c.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, tint = if (c.paused) Ink.onBrass else Ink.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        for (s in 1..5) {
            val on = s <= c.speed
            Box(
                Modifier.size(width = 14.dp, height = 34.dp).clickable { c.setSpeedLevel(s) },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 5.dp, height = (8 + s * 3).dp).clip(Shapes.chip).background(if (on && !c.paused) Ink.brass else if (on) Ink.muted else Ink.line))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun Toasts(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.End) {
        for (t in c.toasts.take(3)) {
            Row(
                Modifier.padding(top = 6.dp).widthIn(max = 320.dp).clip(Shapes.chip).background(Ink.panel)
                    .clickable { if (t.province >= 0) { c.focusRequest = t.province; c.selectProvince(t.province) }; c.toasts.remove(t) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(GameIcons.of(t.icon), null, tint = Ink.tone(t.tone), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(t.text, style = Type.label, color = Ink.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MapModeButton(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var open by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 8.dp), horizontalAlignment = Alignment.End) {
        if (open) {
            Column(Modifier.clip(Shapes.card).background(Ink.panel).padding(6.dp)) {
                for (m in MapMode.entries) {
                    val sel = m == c.mapMode
                    Text(
                        m.label, style = Type.label, color = if (sel) Ink.onBrass else Ink.text,
                        modifier = Modifier.padding(2.dp).clip(Shapes.chip).background(if (sel) Ink.brass else Color.Transparent)
                            .clickable { c.mapMode = m; open = false; c.refresh() }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        IconBtn(Icons.Rounded.Layers, bg = if (c.mapMode != MapMode.POLITICAL) Ink.brass else Ink.panel,
            tint = if (c.mapMode != MapMode.POLITICAL) Ink.onBrass else Ink.text, size = 44.dp) { open = !open }
    }
}

// ---------------------------------------------------------------- dock

@Composable
fun Dock(c: GameController, inSheet: Boolean = false) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val icons = mapOf(
        Tab.GOVERNMENT to Icons.Rounded.AccountBalance,
        Tab.ECONOMY to Icons.Rounded.Payments,
        Tab.DIPLOMACY to Icons.Rounded.Handshake,
        Tab.MILITARY to Icons.Rounded.MilitaryTech,
        Tab.WORLD to Icons.Rounded.Public,
    )
    Row(
        Modifier.fillMaxWidth().let { if (inSheet) it.background(Ink.oceanDeep) else it.padding(horizontal = 10.dp).clip(Shapes.card).background(Ink.panel) }
            .navigationBarsPadding().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (t in Tab.entries) {
            val sel = c.tab == t
            val badge = when (t) {
                Tab.WORLD -> c.game.proposals.size
                Tab.GOVERNMENT -> 0
                else -> 0
            }
            Column(
                Modifier.weight(1f).clip(Shapes.tile).clickable { c.openTab(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Box(Modifier.size(width = 52.dp, height = 30.dp).clip(Shapes.chip).background(if (sel) Ink.brass else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(icons[t]!!, t.label, tint = if (sel) Ink.onBrass else Ink.muted, modifier = Modifier.size(22.dp))
                    }
                    if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp).size(16.dp).clip(CircleShape).background(Ink.coral), contentAlignment = Alignment.Center) {
                        Text("$badge", style = Type.small.copy(fontSize = 10.sp), color = Color.White)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(t.label, style = Type.small.copy(fontSize = 11.sp), color = if (sel) Ink.text else Ink.muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SheetHeader(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    Column(Modifier.fillMaxWidth().clickable { c.tab = null }.padding(top = 8.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(Shapes.chip).background(Ink.line))
    }
}

// ---------------------------------------------------------------- cartões no mapa

@Composable
private fun ProvinceCard(c: GameController, p: Int) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val w = c.sim.world
    val pr = w.provinces[p]
    val owner = c.sim.nation(g.owner[p])
    val ctrl = g.controller[p]
    val me = g.player
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagView(owner, 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pr.name, style = Type.heading, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(owner.name + if (owner.capital == p) ", capital" else "", style = Type.small, color = Ink.muted)
            }
            IconBtn(Icons.Rounded.Close, size = 32.dp) { c.selProvince = -1; c.refresh() }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Cell("População", Fmt.pop(g.provPop[p]))
            Cell("PIB", Fmt.money(g.provGdp[p]))
            if (g.core[p] != g.controller[p]) Cell("Resistência", "${g.resistance[p].toInt()}", Ink.scale(100 - g.resistance[p]))
            else Cell("Costa", if (pr.coastal) "Sim" else "Não")
        }
        if (ctrl != g.owner[p]) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = Ink.coral, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ocupada por ${c.sim.nation(ctrl).name}", style = Type.label, color = Ink.coral)
            }
        }
        if (g.siege[p] > 0f) {
            Spacer(Modifier.height(8.dp))
            Text("Cerco ${(g.siege[p] * 100).toInt()}%", style = Type.label, color = Ink.amber)
            Meter(g.siege[p] * 100, Modifier.fillMaxWidth().padding(top = 4.dp), Ink.amber)
        }
        g.battles.firstOrNull { it.province == p }?.let { b ->
            Spacer(Modifier.height(8.dp))
            Text("Batalha: ${c.sim.nation(b.attacker).name} × ${c.sim.nation(b.defender).name}", style = Type.label, color = Ink.coral)
            val tot = (b.attackerPower + b.defenderPower).coerceAtLeast(0.1f)
            Meter(b.attackerPower / tot * 100, Modifier.fillMaxWidth().padding(top = 4.dp), Ink.coral)
        }
        val here = c.sim.armiesAt(p)
        if (here.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (a in here.take(4)) StatPill(GameIcons.domain(a.domain), Fmt.units(a.total()), if (a.owner == me) Ink.brass else if (c.sim.atWar(me, a.owner)) Ink.coral else Ink.text) {
                    c.selArmy = a.id; c.refresh()
                }
            }
        }
        // recursos que a província produz
        val res = com.dmwnezes.soberano.sim.Resource.entries.map { it to c.sim.dev.provRes[it.ordinal][p] }.filter { it.second >= 0.5f }
        if (res.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((r, v) in res) StatPill(GameIcons.of(r.icon), "${r.label} ${Fmt.money(v.toDouble()).removePrefix("US$ ")}", Ink.text)
            }
        }
        FortRow(c, p)
        ProvinceExtras(c, p)
        if (g.owner[p] == me && ctrl == me) {
            val isRally = g.rallyPoint == p
            Text(if (isRally) "Ponto de reunião · toque para treinar em todos os estados" else "Concentrar reservistas aqui",
                style = Type.small, color = if (isRally) Ink.good else Ink.verdigris,
                modifier = Modifier.padding(top = 8.dp).clip(Shapes.chip).clickable {
                    if (isRally) { g.rallyPoint = -1; c.say("Reservistas treinam em todos os estados", 1, "flag") }
                    else { g.rallyPoint = p; c.say("Reservistas vão chegar em ${pr.name}", 1, "flag") }
                    c.refresh()
                }.padding(vertical = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g.owner[p] == me && ctrl == me) {
                Btn("Recrutar", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.Add) { c.buildProvince = p; c.subTab[Tab.MILITARY] = 1; c.openTab(Tab.MILITARY) }
                Btn("Reunir aqui", Modifier.weight(1f), icon = Icons.Rounded.CallMerge) {
                    val n = c.sim.military.gatherAt(me, p); g.autoMerge = true
                    c.say(if (n > 0) "$n exércitos a caminho" else "Nenhum exército disponível", if (n > 0) 1 else 2, "flag", p)
                }
            } else if (owner.id != me) {
                Btn("Diplomacia", Modifier.weight(1f), BtnKind.SECONDARY, Icons.Rounded.Handshake) { c.focusNation = owner.id; c.tab = Tab.DIPLOMACY; c.refresh() }
                if (ctrl != owner.id && ctrl != me) Btn(c.sim.nation(ctrl).name, Modifier.weight(1f), BtnKind.GHOST) { c.focusNation = ctrl; c.tab = Tab.DIPLOMACY; c.refresh() }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArmyCard(c: GameController, a: Army) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val mine = a.owner == g.player
    val owner = c.sim.nation(a.owner)
    val w = c.sim.world
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(owner.mapColor.toInt())), contentAlignment = Alignment.Center) {
                Icon(GameIcons.domain(a.domain), null, tint = Ink.oceanDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name.ifEmpty { "Exército" }, style = Type.heading, color = Ink.text)
                val status = when {
                    a.battle -> "Em combate em ${w.provinces[a.province].name}"
                    a.moving -> "Indo para ${w.provinces[a.path.last()].name}, ${etaDays(c, a)} dias"
                    g.siege[a.province] > 0f && c.sim.atWar(a.owner, g.controller[a.province]) -> "Cercando ${w.provinces[a.province].name}"
                    else -> "Parado em ${w.provinces[a.province].name}"
                }
                Text(if (mine) status else "${owner.name}, $status", style = Type.small, color = if (a.battle) Ink.coral else Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Icons.Rounded.Close, size = 32.dp) { c.selArmy = -1; c.clearTarget() }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in UnitType.entries) {
                val n = a.units[t.ordinal]
                if (n < 0.05f) continue
                Text("${t.label} ${Fmt.units((n * 2).toInt() / 2f)}", style = Type.label, color = Ink.text,
                    modifier = Modifier.clip(Shapes.chip).background(Ink.raised).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Moral", style = Type.label, color = Ink.muted, modifier = Modifier.width(54.dp))
            Meter(a.morale * 100, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${(a.morale * 100).toInt()}%", style = Type.number.copy(fontSize = 14.sp), color = Ink.text)
        }
        GeneralRow(c, a)
        if (mine) {
            Spacer(Modifier.height(10.dp))
            Text(
                when (a.domain) {
                    Domain.LAND -> "Toque numa província para ver as opções: mover ou atacar"
                    Domain.SEA -> "Toque numa província costeira para navegar"
                    Domain.AIR -> "Toque numa base sua ou aliada; os aviões apoiam batalhas no alcance"
                }, style = Type.small, color = Ink.brass,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.moving) Btn("Parar", Modifier.weight(1f), icon = Icons.Rounded.Stop) { c.sim.military.stop(a); c.refresh() }
                Btn("Dividir", Modifier.weight(1f), icon = Icons.Rounded.CallSplit) {
                    c.sim.military.split(a)?.let { c.say("Exército dividido", 1, "flag") } ?: c.say("Pequeno demais para dividir", 2); c.refresh()
                }
                val others = g.armies.count { it.owner == a.owner && it.province == a.province && it.domain == a.domain && it.id != a.id && !it.moving }
                if (others > 0) Btn("Juntar", Modifier.weight(1f), icon = Icons.Rounded.MergeType) { c.sim.military.mergeAllAt(a); c.refresh() }
                if (!a.battle) IconBtn(Icons.Rounded.PersonRemove, size = 44.dp, tint = Ink.coral) { c.dialog = "disband" }
            }
        }
    }
}

@Composable
private fun DisbandDialog(c: GameController, a: Army) {
    Overlay({ c.dialog = null }) {
        Text("Desmobilizar ${a.name}?", style = Type.heading, color = Ink.text)
        Text(if (a.domain == Domain.LAND) "As tropas voltam para casa e param de custar manutenção. Metade da infantaria vira reservista neste estado."
            else "As unidades são aposentadas e param de custar manutenção.", style = Type.small, color = Ink.muted)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { c.dialog = null }
            Btn("Desmobilizar", Modifier.weight(1f), BtnKind.DANGER) {
                c.dialog = null
                c.act("${a.name} desmobilizado") { c.sim.policy.disband(a) }
                c.selArmy = -1; c.refresh()
            }
        }
    }
}

/** Cartão que aparece ao tocar numa província com um exército seu selecionado. */
@Composable
private fun OrderCard(c: GameController, a: Army, p: Int, onDeclare: (Int) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick
    val g = c.game
    val sim = c.sim
    val me = g.player
    val pr = sim.world.provinces[p]
    val ctrl = g.controller[p]
    val owner = sim.nation(g.owner[p])
    val ctrlN = sim.nation(ctrl)
    val atWar = sim.atWar(me, ctrl)
    val enemyArmies = g.armies.any { it.province == p && it.domain == a.domain && sim.atWar(me, it.owner) }
    val friendly = ctrl == me || sim.allied(me, ctrl) || sim.hasTreaty(TreatyType.PASSAGE, me, ctrl)
    val neutral = !friendly && !atWar
    val mine = if (a.domain == Domain.LAND) sim.military.landStrength(a) else sim.military.navalPower(a)
    val theirs = if (a.domain == Domain.LAND) sim.military.enemyPowerAt(me, p)
        else g.armies.filter { it.province == p && it.domain == Domain.SEA && it.owner != me && !sim.allied(me, it.owner) }.sumOf { sim.military.navalPower(it).toDouble() }.toFloat()
    val noRoute = c.previewDays < 0
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlagView(ctrlN, 22.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pr.name, style = Type.heading, color = Ink.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val who = if (ctrl != owner.id) "${owner.name}, ocupada por ${ctrlN.name}" else owner.name
                Text(who, style = Type.small, color = Ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconBtn(Icons.Rounded.Close, size = 32.dp) { c.clearTarget() }
        }
        Spacer(Modifier.height(10.dp))
        val (stateText, stateColor) = when {
            ctrl == me -> "Território seu" to Ink.good
            atWar -> "Em guerra com ${ctrlN.name}" to Ink.coral
            friendly -> "Território amigo: pode passar" to Ink.verdigris
            else -> "Neutro: atacar exige declarar guerra" to Ink.amber
        }
        Text(stateText, style = Type.label, color = stateColor)
        if ((atWar || neutral || enemyArmies) && a.domain != Domain.AIR) {
            Spacer(Modifier.height(10.dp))
            val total = (mine + theirs).coerceAtLeast(0.1f)
            Row(Modifier.fillMaxWidth().height(8.dp).clip(Shapes.chip)) {
                Box(Modifier.weight((mine / total).coerceAtLeast(0.02f)).height(8.dp).background(Ink.brass))
                Box(Modifier.weight((theirs / total).coerceAtLeast(0.02f)).height(8.dp).background(Ink.coral))
            }
            Spacer(Modifier.height(4.dp))
            val ratio = if (theirs <= 0f) 99f else mine / theirs
            val verdict = when {
                theirs <= 0f && a.domain == Domain.LAND -> "Sem defesa: cerco de ${sim.military.siegeDays(p).toInt()} dias"
                theirs <= 0f -> "Nenhuma frota inimiga"
                ratio > 1.6f -> "Vantagem clara"
                ratio > 1.1f -> "Vantagem"
                ratio > 0.85f -> "Equilibrado"
                else -> "Desvantagem: risco de derrota"
            }
            Row {
                Text("Você ${mine.toInt()}", style = Type.small, color = Ink.brass, modifier = Modifier.weight(1f))
                Text(verdict, style = Type.small, color = if (ratio > 1.1f || theirs <= 0f) Ink.good else if (ratio > 0.85f) Ink.amber else Ink.coral)
                Spacer(Modifier.weight(1f))
                Text("Defesa ${theirs.toInt()}", style = Type.small, color = Ink.coral)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (noRoute) "Sem caminho até lá" else "Chega em ${c.previewDays} dias", style = Type.small, color = if (noRoute) Ink.coral else Ink.muted)
        // rota além-mar: avisa que vai de navio e quanto custa o frete
        val viaSea = remember(a.id, p, c.tick) {
            a.domain == Domain.LAND && sim.military.preview(a, p)?.first?.let { path ->
                var prev = a.province; var sea = false
                for (q in path) { if (!sim.military.isLandNeighbor(prev, q)) sea = true; prev = q }
                sea
            } == true
        }
        if (viaSea && !noRoute) {
            val freight = sim.military.shippingCost(a)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.DirectionsBoat, null, tint = Ink.verdigris, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (freight > 0) "Cruza o mar · frete de navios ${Fmt.money(freight)}" else "Cruza o mar com seus transportes",
                    style = Type.small, color = Ink.verdigris)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { c.clearTarget() }
            fun go(verb: String) {
                val why = sim.military.order(a, p)
                if (why != null) c.say(why, 2) else c.say("${a.name} $verb ${pr.name}, ${etaDays(c, a)} dias", 0, if (verb.startsWith("ataca")) "battle" else "flag")
                c.clearTarget()
            }
            when {
                a.domain == Domain.AIR -> Btn("Mudar base", Modifier.weight(1.4f), BtnKind.PRIMARY, Icons.Rounded.Flight, enabled = friendly) { go("muda para") }
                atWar || enemyArmies -> Btn(if (viaSea) "Invadir" else "Atacar", Modifier.weight(1.4f), BtnKind.DANGER,
                    if (viaSea) Icons.Rounded.DirectionsBoat else Icons.Rounded.Whatshot, enabled = !noRoute) { go(if (viaSea) "invade" else "ataca") }
                neutral -> Btn(if (viaSea) "Declarar guerra e invadir" else "Declarar guerra e atacar", Modifier.weight(1.6f), BtnKind.DANGER,
                    if (viaSea) Icons.Rounded.DirectionsBoat else Icons.Rounded.Whatshot, enabled = !noRoute) { onDeclare(ctrl) }
                else -> Btn(if (a.domain == Domain.SEA) "Navegar" else "Mover para cá", Modifier.weight(1.4f), BtnKind.PRIMARY, Icons.Rounded.Flag, enabled = !noRoute) { go("vai para") }
            }
        }
    }
}

@Composable
private fun ProposalBanner(c: GameController) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val p = c.game.proposals.first()
    val from = c.sim.nation(p.from)
    val demand = p.kind == "DEMAND"
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(Shapes.card)
            .background(if (demand) Ink.coral.copy(alpha = 0.25f) else Ink.panel).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagView(from, 20.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(from.name, style = Type.label.copy(fontWeight = FontWeight.SemiBold), color = Ink.text, maxLines = 1)
            Text(proposalText(c, p), style = Type.small, color = if (demand) Ink.coral else Ink.muted, maxLines = 2)
        }
        if (c.game.proposals.size > 1) Text("+${c.game.proposals.size - 1}", style = Type.small, color = Ink.muted, modifier = Modifier.padding(end = 6.dp))
        IconBtn(Icons.Rounded.Close, size = 34.dp, tint = Ink.coral) { declineProposal(c, p); c.refresh() }
        Spacer(Modifier.width(6.dp))
        IconBtn(Icons.Rounded.Check, size = 34.dp, bg = Ink.brass, tint = Ink.onBrass) { acceptProposal(c, p); c.refresh() }
    }
}

// ---------------------------------------------------------------- evento

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(c: GameController, pe: PendingEvent) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    val def = Content.eventById[pe.def] ?: return
    Overlay({}, dismissable = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Ink.brass.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(GameIcons.of(def.icon), null, tint = Ink.brass, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(def.cat.label, style = Type.small, color = Ink.muted)
                Text(def.title, style = Type.title.copy(fontSize = 22.sp), color = Ink.text)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(c.sim.events.text(pe), style = Type.body, color = Ink.text)
        if (pe.province >= 0) {
            Text("Ver no mapa", style = Type.label, color = Ink.verdigris, modifier = Modifier.padding(top = 6.dp).clickable { c.focusRequest = pe.province; c.selectProvince(pe.province) })
        }
        Spacer(Modifier.height(16.dp))
        def.choices.forEachIndexed { i, ch ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.raised)
                    .clickable { c.sim.events.resolve(pe, i); c.refresh() }.padding(14.dp),
            ) {
                Text(ch.label, style = Type.body.copy(fontWeight = FontWeight.SemiBold), color = Ink.text)
                if (ch.effects.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (e in ch.effects) EffectChip(c.sim.events.label(e, c.me), c.sim.events.good(e))
                    }
                }
            }
        }
    }
}

@Composable
private fun NukeConfirm(c: GameController, p: Int, onClose: () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var stage by remember { mutableStateOf(0) }
    val target = c.sim.nation(c.game.controller[p])
    Overlay(onClose) {
        Icon(Icons.Rounded.FlashOn, null, tint = Ink.coral, modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
        Text(if (stage == 0) "Lançar míssil nuclear?" else "Última confirmação", style = Type.title.copy(fontSize = 22.sp), color = Ink.text, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text("Alvo: ${c.sim.world.provinces[p].name} (${target.name}). O mundo inteiro vai reagir.", style = Type.body, color = Ink.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Cancelar", Modifier.weight(1f), BtnKind.GHOST) { onClose() }
            Btn(if (stage == 0) "Continuar" else "Lançar", Modifier.weight(1f), BtnKind.DANGER) {
                if (stage == 0) stage = 1 else { c.act { c.sim.military.nuke(c.game.player, p) }; onClose() }
            }
        }
    }
}

// ---------------------------------------------------------------- menu

@Composable
private fun SettingsOverlay(c: GameController, store: SaveStore, onExit: () -> Unit, onLoad: (String) -> Unit) {
    @Suppress("UNUSED_EXPRESSION") c.tick // relê quando o jogo avança (evita pular a recomposição)
    var slots by remember { mutableStateOf<Boolean?>(null) }
    var rules by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf(false) }
    Overlay({ c.showSettings = false }) {
        Text("Menu", style = Type.title, color = Ink.text)
        Spacer(Modifier.height(14.dp))
        Btn("Voltar ao jogo", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.PlayArrow) { c.showSettings = false }
        Spacer(Modifier.height(8.dp))
        Btn("Salvar", Modifier.fillMaxWidth(), icon = Icons.Rounded.Save) { slots = true }
        Spacer(Modifier.height(8.dp))
        Btn("Carregar", Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen) { slots = false }
        Spacer(Modifier.height(8.dp))
        Btn("Regras da partida", Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune) { rules = true }
        Spacer(Modifier.height(8.dp))
        Btn("Buscar atualização", Modifier.fillMaxWidth(), icon = Icons.Rounded.SystemUpdate) { update = true }
        if (c.game.paper != null) {
            Spacer(Modifier.height(8.dp))
            Btn("Ler o último jornal", Modifier.fillMaxWidth(), icon = Icons.Rounded.Newspaper) { c.showSettings = false; c.showPaper = true }
        }
        Spacer(Modifier.height(4.dp))
        SoundToggles(c)
        Spacer(Modifier.height(8.dp))
        Btn("Sair para o menu", Modifier.fillMaxWidth(), BtnKind.GHOST, Icons.Rounded.Logout) { c.showSettings = false; onExit() }
    }
    slots?.let { saving ->
        SlotsDialog(store, saving, onDismiss = { slots = null }) { slot ->
            slots = null
            if (saving) { c.save(slot); c.say("Jogo salvo no espaço $slot", 1, "flag"); c.showSettings = false }
            else { c.showSettings = false; onLoad(slot) }
        }
    }
    if (update) com.dmwnezes.soberano.update.UpdateDialog { update = false }
    if (rules) {
        Box(Modifier.fillMaxSize().background(Ink.panelSolid).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Regras da partida", style = Type.title, color = Ink.text, modifier = Modifier.weight(1f))
                    IconBtn(Icons.Rounded.Close) { rules = false; c.refresh() }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RulesEditor(c.game.settings, inGame = true)
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

/** Texto da previsão: quem luta, quem só ajuda, quem sanciona. */
fun allyText(c: GameController, r: com.dmwnezes.soberano.sim.Diplomacy.AllyResponse): String {
    fun names(l: List<Int>) = l.take(4).joinToString { c.sim.nation(it).name } + if (l.size > 4) " e mais ${l.size - 4}" else ""
    val parts = ArrayList<String>()
    parts += if (r.fight.isEmpty()) "Nenhum aliado deles deve lutar." else "Devem lutar: ${names(r.fight)}."
    if (r.aid.isNotEmpty()) parts += "${r.aid.size} só mandam dinheiro."
    if (r.sanction.isNotEmpty()) parts += "${r.sanction.size} vão sancionar você."
    return parts.joinToString(" ")
}
