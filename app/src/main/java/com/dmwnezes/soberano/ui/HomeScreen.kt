package com.dmwnezes.soberano.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.data.SaveMeta
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Sim

@Composable
fun HomeScreen(preview: Sim, store: SaveStore, onNew: () -> Unit, onLoad: (String) -> Unit) {
    val ctx = LocalContext.current
    val camera = remember { MapCamera() }
    var latest by remember { mutableStateOf<SaveMeta?>(null) }
    var showSlots by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { latest = store.latest() }
    // o mapa desliza devagar ao fundo
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { t ->
            if (last != 0L && camera.scale > 0f) {
                camera.ox -= (t - last) / 1e9f * 9f
                if (camera.ox < camera.viewW - camera.worldW * camera.scale) camera.ox = 0f
            }
            last = t
        }
    }
    Box(Modifier.fillMaxSize()) {
        WorldMapView(preview, camera, null, Modifier.fillMaxSize(), outlineNation = -1, initialCenter = preview.world.byCode["BRA"]!!.capital)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.oceanDeep.copy(alpha = 0.55f), Color.Transparent, Ink.oceanDeep.copy(alpha = 0.95f)))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Spacer(Modifier.height(36.dp))
            Text("Soberano", style = Type.title.copy(fontSize = 52.sp, letterSpacing = (-1.5).sp), color = Ink.text)
            Text("O mundo de 2026 em suas mãos.", style = Type.body.copy(fontSize = 17.sp), color = Ink.muted)
            Spacer(Modifier.weight(1f))
            latest?.let { m ->
                Row(
                    Modifier.fillMaxWidth().clip(Shapes.card).background(Ink.brass).clickable { onLoad(m.slot) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Ink.onBrass, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Continuar", style = Type.heading.copy(fontWeight = FontWeight.ExtraBold), color = Ink.onBrass)
                        Text("${m.country} · ${m.dateLabel()}", style = Type.label, color = Ink.onBrass.copy(alpha = 0.75f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Btn("Novo jogo", Modifier.weight(1f), if (latest == null) BtnKind.PRIMARY else BtnKind.SECONDARY, Icons.Rounded.Add) { onNew() }
                Btn("Carregar", Modifier.weight(1f), BtnKind.SECONDARY, Icons.Rounded.FolderOpen) { showSlots = true }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "criado por: @dmwnezes", style = Type.small, color = Ink.faint,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(Shapes.chip).clickable {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/dmwnezes"))) }
                }.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (showSlots) SlotsDialog(store, saving = false, onDismiss = { showSlots = false }, onPick = { showSlots = false; onLoad(it) })
    }
}

/** Lista de saves para carregar ou gravar. */
@Composable
fun SlotsDialog(store: SaveStore, saving: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val slots = remember { (if (saving) SaveStore.SLOTS else listOf("auto") + SaveStore.SLOTS).map { it to store.meta(it) } }
    Overlay(onDismiss) {
        Text(if (saving) "Salvar em" else "Carregar", style = Type.heading, color = Ink.text)
        Spacer(Modifier.height(12.dp))
        for ((slot, m) in slots) {
            val enabled = saving || m != null
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.tile).background(Ink.oceanDeep)
                    .clickable(enabled = enabled) { onPick(slot) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (slot == "auto") "Auto" else slot, style = Type.number, color = Ink.brass, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(m?.country ?: "Vazio", style = Type.body, color = if (m != null) Ink.text else Ink.faint)
                    if (m != null) Text("${m.title} · ${m.dateLabel()}", style = Type.small, color = Ink.muted)
                }
            }
        }
    }
}

/** Caixa centralizada sobre fundo escurecido. */
@Composable
fun Overlay(onDismiss: () -> Unit, dismissable: Boolean = true, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3061218)).clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
        ) { if (dismissable) onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(22.dp).fillMaxWidth().clip(Shapes.card).background(Ink.panelSolid)
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
        ) { content() }
    }
}
