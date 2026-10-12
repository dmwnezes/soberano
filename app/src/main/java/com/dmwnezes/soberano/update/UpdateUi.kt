package com.dmwnezes.soberano.update

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dmwnezes.soberano.ui.Btn
import com.dmwnezes.soberano.ui.BtnKind
import com.dmwnezes.soberano.ui.Ink
import com.dmwnezes.soberano.ui.Meter
import com.dmwnezes.soberano.ui.Overlay
import com.dmwnezes.soberano.ui.Shapes
import com.dmwnezes.soberano.ui.ToggleRow
import com.dmwnezes.soberano.ui.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** Estado global da atualização (checagem ao abrir o app). */
object UpdateState {
    var available by mutableStateOf<Release?>(null)
    var downloaded by mutableStateOf<File?>(null)
    var checking by mutableStateOf(false)

    fun auto(ctx: Context) = ctx.getSharedPreferences("soberano", Context.MODE_PRIVATE).getBoolean("auto_update", true)
    fun setAuto(ctx: Context, v: Boolean) = ctx.getSharedPreferences("soberano", Context.MODE_PRIVATE).edit().putBoolean("auto_update", v).apply()

    /** Ao abrir: procura versão nova e, com o automático ligado, já baixa. */
    suspend fun checkOnStart(ctx: Context) {
        if (!auto(ctx)) return
        val r = runCatching { Updater.latest() }.getOrNull() ?: return
        if (!Updater.isNewer(r)) return
        available = r
        // nos dados móveis só avisa; o download de ~12 MB espera o Wi-Fi ou o toque em "Baixar"
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        if (cm != null && cm.isActiveNetworkMetered) return
        downloaded = runCatching { Updater.download(ctx, r) {} }.getOrNull()
    }
}

/** Pílula "Nova versão": aparece no menu inicial quando há atualização. */
@Composable
fun UpdatePill(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val r = UpdateState.available ?: return
    Row(
        modifier.clip(Shapes.chip).background(Ink.raised).clickable(onClick = onOpen).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Ink.brass, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (UpdateState.downloaded != null) "Versão ${r.tag.removePrefix("v")} pronta" else "Versão ${r.tag.removePrefix("v")} disponível",
            style = Type.label, color = Ink.text)
        Spacer(Modifier.width(10.dp))
        Text("Atualizar", style = Type.label, color = Ink.onBrass,
            modifier = Modifier.clip(Shapes.chip).background(Ink.brass).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** Janela de atualização: verifica, baixa e instala. */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf(UpdateState.available) }
    var file by remember { mutableStateOf(UpdateState.downloaded) }
    var status by remember { mutableStateOf(if (release == null) "Procurando…" else "") }
    var progress by remember { mutableStateOf(-1f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var auto by remember { mutableStateOf(UpdateState.auto(ctx)) }

    fun check() {
        status = "Procurando…"
        job = scope.launch {
            runCatching { Updater.latest() }.fold(
                { r -> if (Updater.isNewer(r)) { release = r; UpdateState.available = r; status = "" } else status = "Você já está na versão mais nova" },
                { e -> status = "Não consegui verificar. " + (e.message?.takeIf { it.startsWith("O GitHub") } ?: "Confira a internet.") },
            )
        }
    }
    fun download(r: Release) {
        progress = 0f; status = "Baixando…"
        job = scope.launch {
            runCatching { Updater.download(ctx, r) { progress = it } }.fold(
                { f -> file = f; UpdateState.downloaded = f; status = ""; progress = -1f },
                { e -> status = "O download falhou. " + (e.message ?: "Tente de novo."); progress = -1f },
            )
        }
    }
    LaunchedEffect(Unit) { if (release == null) check() }
    // ao voltar da tela "instalar apps desconhecidos", a permissão é relida
    var canInstall by remember { mutableStateOf(Updater.canInstall(ctx)) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) canInstall = Updater.canInstall(ctx) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    // durante o download, tocar fora não cancela
    Overlay({ job?.cancel(); onDismiss() }, dismissable = progress < 0f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null, tint = Ink.brass, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Atualização", style = Type.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Ink.text)
        }
        Spacer(Modifier.height(4.dp))
        Text("Instalada: ${Updater.currentName}", style = Type.small, color = Ink.muted)
        val r = release
        if (r != null) {
            Spacer(Modifier.height(10.dp))
            Text("Nova: ${r.tag.removePrefix("v")}", style = Type.heading, color = Ink.text)
            if (r.notes.isNotBlank()) Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                Text(r.notes, style = Type.body, color = Ink.muted)
            }
        }
        if (status.isNotEmpty()) Text(status, style = Type.label, color = Ink.brass, modifier = Modifier.padding(top = 10.dp))
        if (progress >= 0f) Meter(progress * 100, Modifier.fillMaxWidth().padding(top = 8.dp), Ink.brass)
        Spacer(Modifier.height(10.dp))
        ToggleRow("Atualizar automaticamente", auto, sub = "Procura e baixa sozinho ao abrir o jogo") { auto = it; UpdateState.setAuto(ctx, it) }
        Spacer(Modifier.height(10.dp))
        val f = file
        when {
            f != null -> {
                if (!canInstall) Text("Na primeira vez o Android pede para permitir que o Soberano instale atualizações. Permita e volte aqui.",
                    style = Type.small, color = Ink.muted, modifier = Modifier.padding(bottom = 8.dp))
                Btn(if (canInstall) "Instalar" else "Permitir instalação", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.SystemUpdate) {
                    if (!f.exists()) { file = null; UpdateState.downloaded = null; status = "O arquivo sumiu. Baixe de novo." }
                    else runCatching { Updater.install(ctx, f) }.onFailure { status = "Não consegui abrir o instalador." }
                }
            }
            r != null && progress < 0f -> Btn(if (status.startsWith("O download")) "Tentar de novo" else "Baixar (${r.sizeBytes / 1_048_576} MB)", Modifier.fillMaxWidth(), BtnKind.PRIMARY, Icons.Rounded.Download) { download(r) }
            r == null && status.startsWith("Não") -> Btn("Tentar de novo", Modifier.fillMaxWidth()) { check() }
        }
        Spacer(Modifier.height(8.dp))
        Btn("Fechar", Modifier.fillMaxWidth(), BtnKind.GHOST) { job?.cancel(); onDismiss() }
    }
}
