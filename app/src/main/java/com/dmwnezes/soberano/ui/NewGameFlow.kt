package com.dmwnezes.soberano.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmwnezes.soberano.sim.CustomNation
import com.dmwnezes.soberano.sim.Defeat
import com.dmwnezes.soberano.sim.Difficulty
import com.dmwnezes.soberano.sim.EconomyDepth
import com.dmwnezes.soberano.sim.EventCategory
import com.dmwnezes.soberano.sim.EventFrequency
import com.dmwnezes.soberano.sim.Flag
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Settings
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.sim.Title
import com.dmwnezes.soberano.sim.Victory

private enum class Step { COUNTRY, CREATE, TITLE, RULES }

@Composable
fun NewGameFlow(
    preview: Sim,
    onBack: () -> Unit,
    onStart: (Settings, String, Title, CustomNation?) -> Unit,
) {
    var step by remember { mutableStateOf(Step.COUNTRY) }
    var country by remember { mutableIntStateOf(preview.world.byCode["BRA"]!!.index) }
    var custom by remember { mutableStateOf<CustomNation?>(null) }
    var title by remember { mutableStateOf(Title.PRESIDENT) }
    val settings = remember { Settings() }

    AnimatedContent(step, label = "passo") { s ->
        when (s) {
            Step.COUNTRY -> CountryStep(preview, country, onSelect = { country = it }, onBack = onBack,
                onNext = { custom = null; step = Step.TITLE }, onCreate = { step = Step.CREATE })
            Step.CREATE -> CreateStep(preview, country, onBack = { step = Step.COUNTRY }) { c -> custom = c; step = Step.TITLE }
            Step.TITLE -> TitleStep(title, onBack = { step = if (custom != null) Step.CREATE else Step.COUNTRY }) { title = it; step = Step.RULES }
            Step.RULES -> RulesStep(settings, onBack = { step = Step.TITLE }) {
                settings.seed = System.currentTimeMillis()
                onStart(settings, preview.world.countries[country].code, title, custom)
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, sub: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Type.title, color = Ink.text)
            if (sub != null) Text(sub, style = Type.label, color = Ink.muted)
        }
    }
}

// ---------------------------------------------------------------- país

@Composable
private fun CountryStep(preview: Sim, selected: Int, onSelect: (Int) -> Unit, onBack: () -> Unit, onNext: () -> Unit, onCreate: () -> Unit) {
    val camera = remember { MapCamera() }
    var search by remember { mutableStateOf(false) }
    val g = preview.game
    val n = g.nations[selected]
    Box(Modifier.fillMaxSize()) {
        WorldMapView(
            preview, camera, null, Modifier.fillMaxSize(),
            outlineNation = selected, initialCenter = n.capital,
            onTapProvince = { p -> if (p >= 0) onSelect(g.owner[p]) },
        )
        Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Ink.oceanDeep, Color.Transparent)))) {
            StepHeader("Escolha seu país", "Toque no mapa ou busque pelo nome", onBack)
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panel).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagView(n, 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.name, style = Type.heading.copy(fontWeight = FontWeight.ExtraBold), color = Ink.text)
                    Text(n.regime.label, style = Type.small, color = Ink.muted)
                }
                IconBtn(Icons.Rounded.Search, onClick = { search = true })
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Cell("População", Fmt.pop(g.natPop(selected)))
                Cell("PIB", Fmt.money(g.natGdp(selected)))
                Cell("Exército", preview.landPower(selected).toInt().toString())
                Cell("Províncias", g.natProvinces(selected).toString())
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Criar meu país", Modifier.weight(1f), BtnKind.GHOST, Icons.Rounded.Brush) { onCreate() }
                Btn("Governar", Modifier.weight(1f), BtnKind.PRIMARY, Icons.Rounded.Check) { onNext() }
            }
        }
        if (search) CountrySearch(preview, onDismiss = { search = false }) { id ->
            onSelect(id); search = false
            fitNation(preview, camera, id, g.nations[id].capital)
        }
    }
}

@Composable
fun CountrySearch(sim: Sim, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = remember(q) {
        val norm = q.trim().lowercase()
        sim.game.nations.filter { it.alive && (norm.isEmpty() || strip(it.name.lowercase()).contains(strip(norm))) }
            .sortedByDescending { sim.game.natGdp(it.id) }
    }
    Box(Modifier.fillMaxSize().background(Color(0xE6061218)).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, onClick = onDismiss)
                Spacer(Modifier.width(12.dp))
                SearchField(q, "Buscar país", Modifier.weight(1f)) { q = it }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(Shapes.tile).clickable { onPick(n.id) }.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FlagView(n, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(n.name, style = Type.body, color = Ink.text, modifier = Modifier.weight(1f))
                        Text(Fmt.money(sim.game.natGdp(n.id)), style = Type.small, color = Ink.muted)
                    }
                }
            }
        }
    }
}

fun strip(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

@Composable
fun SearchField(value: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Box(modifier.clip(Shapes.chip).background(Ink.raised).padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(hint, style = Type.body, color = Ink.faint)
        BasicTextField(value, onChange, singleLine = true, textStyle = Type.body.copy(color = Ink.text), cursorBrush = SolidColor(Ink.brass), modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- criar país

val FLAG_COLORS = listOf(
    0xFFC62828L, 0xFFE53935L, 0xFFF9A825L, 0xFFFFD54FL, 0xFF2E7D32L, 0xFF43A047L, 0xFF00897BL, 0xFF1565C0L,
    0xFF42A5F5L, 0xFF283593L, 0xFF6A1B9AL, 0xFFFFFFFFL, 0xFF212121L, 0xFF8D6E63L, 0xFFEF6C00L, 0xFF00ACC1L,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateStep(preview: Sim, territory: Int, onBack: () -> Unit, onDone: (CustomNation) -> Unit) {
    var name by remember { mutableStateOf("") }
    var layout by remember { mutableIntStateOf(0) }
    var emblem by remember { mutableIntStateOf(1) }
    val colors = remember { mutableStateOf(listOf(0xFF1565C0L, 0xFFFFFFFFL, 0xFFC62828L, 0xFFFFD54FL)) }
    var editing by remember { mutableIntStateOf(0) }
    var mapColor by remember { mutableStateOf(0xFFC9A04FL) }
    val flag = Flag(layout, colors.value, emblem)
    val base = preview.game.nations[territory]
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        StepHeader("Criar meu país", "Ele nasce no lugar de ${base.name}", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                CustomFlag(flag, Modifier.size(200.dp, 150.dp).clip(Shapes.tile).border(1.dp, Ink.line, Shapes.tile))
            }
            SearchField(name, "Nome do país", Modifier.fillMaxWidth()) { if (it.length <= 28) name = it }
            SectionTitle("Desenho")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in 0..5) {
                    val sel = l == layout
                    CustomFlag(Flag(l, colors.value, 0), Modifier.weight(1f).height(36.dp).clip(Shapes.small)
                        .border(if (sel) 2.dp else 1.dp, if (sel) Ink.brass else Ink.line, Shapes.small).clickable { layout = l })
                }
            }
            SectionTitle("Cores")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..3) {
                    val sel = i == editing
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(Shapes.small).background(Color(colors.value[i].toInt()))
                            .border(if (sel) 3.dp else 1.dp, if (sel) Ink.brass else Ink.line, Shapes.small).clickable { editing = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (i == 3) "Emblema" else "${i + 1}", style = Type.small, color = Color.Black.copy(alpha = 0.6f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in FLAG_COLORS) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c.toInt())).border(1.dp, Ink.line, CircleShape).clickable {
                        colors.value = colors.value.toMutableList().also { it[editing] = c }
                    })
                }
            }
            SectionTitle("Emblema")
            Segmented(listOf(0, 1, 2, 3, 4), emblem, { listOf("Nenhum", "Estrela", "Sol", "Lua", "Escudo")[it] }) { emblem = it }
            SectionTitle("Cor no mapa")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in listOf(0xFFC9A04FL, 0xFF5FA392L, 0xFFB8574DL, 0xFF7E8FC4L, 0xFFA77FC0L, 0xFFD08A5AL, 0xFF8FB08AL, 0xFFD3C27AL)) {
                    val sel = c == mapColor
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c.toInt())).border(if (sel) 3.dp else 1.dp, if (sel) Ink.text else Ink.line, CircleShape).clickable { mapColor = c })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Btn("Continuar", Modifier.fillMaxWidth().padding(18.dp), BtnKind.PRIMARY, enabled = name.isNotBlank()) {
            onDone(CustomNation(name.trim(), flag, mapColor, base.code))
        }
    }
}

// ---------------------------------------------------------------- cargo

@Composable
private fun TitleStep(current: Title, onBack: () -> Unit, onPick: (Title) -> Unit) {
    val info = mapOf(
        Title.PRESIDENT to ("Eleições a cada 4 anos. O Congresso aprova suas leis." to Icons.Rounded.HowToVote),
        Title.PRIME_MINISTER to ("Governa pelo parlamento. Perdeu apoio, perdeu o cargo." to Icons.Rounded.AccountBalance),
        Title.KING to ("Sem eleições e sem Congresso. O povo ainda pode se revoltar." to Icons.Rounded.WorkspacePremium),
        Title.DICTATOR to ("Decretos diretos e controle total. Golpes e revoltas são o risco." to Icons.Rounded.Gavel),
    )
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        StepHeader("Seu cargo", "Define o regime do país", onBack)
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (t in Title.entries) {
                val (txt, icon) = info[t]!!
                val sel = t == current
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(if (sel) Ink.brass.copy(alpha = 0.16f) else Ink.raised)
                        .border(if (sel) 2.dp else 0.dp, if (sel) Ink.brass else Color.Transparent, Shapes.card)
                        .clickable { onPick(t) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Ink.oceanDeep), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Ink.brass, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.label, style = Type.heading, color = Ink.text)
                        Text(txt, style = Type.small, color = Ink.muted)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- regras

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesEditor(settings: Settings, inGame: Boolean = false) {
    var v by remember { mutableIntStateOf(0) }
    fun bump() { v++ }
    @Suppress("UNUSED_EXPRESSION") v
    SectionTitle("Derrotas")
    for (d in Defeat.entries) ToggleRow(d.label, d in settings.defeats) { if (it) settings.defeats += d else settings.defeats -= d; bump() }
    if (!inGame) {
        SectionTitle("Vitória")
        for (w in Victory.entries) ToggleRow(w.label, w in settings.victories) { if (it) settings.victories += w else settings.victories -= w; bump() }
    }
    SectionTitle("Política")
    ToggleRow("Eleições", settings.elections, Icons.Rounded.HowToVote, "Desligado: você governa sem prazo") { settings.elections = it; bump() }
    ToggleRow("Armas nucleares", settings.nuclear, Icons.Rounded.FlashOn) { settings.nuclear = it; bump() }
    SectionTitle("Eventos")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in EventCategory.entries) {
            val on = c in settings.events
            Text(
                c.label, style = Type.label, color = if (on) Ink.onBrass else Ink.muted,
                modifier = Modifier.clip(Shapes.chip).background(if (on) Ink.brass else Ink.raised)
                    .clickable { if (on) settings.events -= c else settings.events += c; bump() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("Frequência", style = Type.label, color = Ink.muted)
    Spacer(Modifier.height(6.dp))
    Segmented(EventFrequency.entries, settings.eventFrequency, { it.label }) { settings.eventFrequency = it; bump() }
    SectionTitle("Mundo")
    Text("Agressividade dos outros países", style = Type.label, color = Ink.muted)
    Segmented(listOf(0f, 0.5f, 1f, 1.6f), settings.aiAggression, { when (it) { 0f -> "Paz"; 0.5f -> "Baixa"; 1f -> "Normal"; else -> "Alta" } }) { settings.aiAggression = it; bump() }
    if (!inGame) {
        Spacer(Modifier.height(10.dp))
        Text("Economia", style = Type.label, color = Ink.muted)
        Segmented(EconomyDepth.entries, settings.economy, { it.label }) { settings.economy = it; bump() }
        Spacer(Modifier.height(10.dp))
        Text("Dificuldade", style = Type.label, color = Ink.muted)
        Segmented(Difficulty.entries, settings.difficulty, { it.label }) { settings.difficulty = it; bump() }
    }
}

@Composable
private fun RulesStep(settings: Settings, onBack: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        StepHeader("Regras", "Tudo ligado por padrão; desligue o que quiser", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            RulesEditor(settings)
            Spacer(Modifier.height(20.dp))
        }
        Btn("Começar", Modifier.fillMaxWidth().padding(18.dp), BtnKind.PRIMARY, Icons.Rounded.PlayArrow) { onStart() }
    }
}

