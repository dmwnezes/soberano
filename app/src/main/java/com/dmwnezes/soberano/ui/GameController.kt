package com.dmwnezes.soberano.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.FeedItem
import com.dmwnezes.soberano.sim.Sim
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File

enum class Tab(val label: String) { GOVERNMENT("Governo"), ECONOMY("Economia"), DIPLOMACY("Diplomacia"), MILITARY("Militar"), WORLD("Mundo") }

enum class MapMode(val label: String) { POLITICAL("Político"), RELATIONS("Relações"), WAR("Guerra"), APPROVAL("Aprovação"), WEALTH("Riqueza") }

class Toast(val id: Long, val icon: String, val text: String, val tone: Int, val province: Int, val born: Long)

/** Estado da tela de jogo: relógio, seleção, gavetas e avisos. */
class GameController(val sim: Sim, private val store: SaveStore, private val scope: CoroutineScope) {
    /** Muda a cada dia ou ação: a interface relê os números. */
    var tick by mutableLongStateOf(0L)
        private set
    var paused by mutableStateOf(true)
    var speed by mutableIntStateOf(2)
    var tab by mutableStateOf<Tab?>(null)
    var selProvince by mutableIntStateOf(-1)
    var selArmy by mutableIntStateOf(-1)
    var focusNation by mutableIntStateOf(-1)
    var mapMode by mutableStateOf(MapMode.POLITICAL)
    var showSettings by mutableStateOf(false)
    val camera = MapCamera()
    /** sub-aba aberta em cada gaveta */
    val subTab = androidx.compose.runtime.mutableStateMapOf<Tab, Int>()
    var lawEditor by mutableStateOf(false)
    /** lei aberta na explicação detalhada */
    var lawInfo by mutableStateOf<String?>(null)

    /** Destino escolhido para o exército selecionado (cartão de ordem). */
    var orderTarget by mutableIntStateOf(-1)
    var previewPath: IntArray = IntArray(0)
    var previewDays = 0

    fun chooseTarget(p: Int) {
        val a = game.armies.firstOrNull { it.id == selArmy } ?: return
        orderTarget = p
        val pv = sim.military.preview(a, p)
        previewPath = pv?.first ?: IntArray(0)
        previewDays = pv?.second ?: -1
        tick++
    }

    fun clearTarget() { orderTarget = -1; previewPath = IntArray(0); tick++ }

    /** Província escolhida para produzir (gaveta Militar). */
    var buildProvince by mutableIntStateOf(-1)
    val toasts = mutableStateListOf<Toast>()
    private var toastId = 0L
    private var acc = 0.0
    private var lastMonth = sim.date().monthValue
    /** Ao pedir para centralizar o mapa numa província. */
    var focusRequest by mutableIntStateOf(-1)

    init {
        sim.listener = { onFeed(it) }
    }

    val game get() = sim.game
    val me get() = sim.player

    /** Sons e vibração (definido pela tela, que tem o Context). */
    var feedback: Feedback? = null
    private var hadEvent = false
    private var hadVote = false
    private var lastTickMs = 0L
    /** dia do último jornal já aberto */
    var paperSeenDay by mutableIntStateOf(-1)
    var showPaper by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    /** diálogo em tela cheia aberto a partir de uma gaveta ("spread", "gather", "lawai") */
    var dialog by mutableStateOf<String?>(null)

    fun refresh() { tick++ }

    private val dayLength = doubleArrayOf(2.0, 1.0, 0.5, 0.2, 0.07)

    val blocked get() = game.events.isNotEmpty() || game.gameOver != null || game.un.pending != null ||
        (game.election?.seen == false)

    fun onFrame(dtSec: Double, nowMs: Long) {
        // avisos expiram
        if (toasts.isNotEmpty()) toasts.removeAll { nowMs - it.born > 3800 }
        // sons dos cartões que aparecem
        val ev = game.events.isNotEmpty()
        if (ev && !hadEvent) feedback?.play(Sfx.EVENT)
        hadEvent = ev
        val vote = game.un.pending != null
        if (vote && !hadVote) feedback?.play(Sfx.VOTE)
        hadVote = vote
        if (paused || blocked) { acc = 0.0; return }
        acc += dtSec.coerceAtMost(0.25)
        val len = dayLength[(speed - 1).coerceIn(0, 4)]
        var days = 0
        while (acc >= len && days < 3 && !blocked) {
            sim.advanceDay()
            acc -= len
            days++
            val m = sim.date().monthValue
            // salva a cada três meses (gravar a cada mês travava a tela em velocidade alta)
            if (m != lastMonth) { lastMonth = m; if (m % 3 == 1) autosave() }
        }
        // redesenha a interface no máximo ~10 vezes por segundo (economiza bateria em velocidade alta)
        if (days > 0 && nowMs - lastTickMs >= 100) { tick++; lastTickMs = nowMs }
        if (blocked) { paused = true; tick++ }
    }

    fun togglePause() { paused = !paused; tick++ }

    fun setSpeedLevel(v: Int) { speed = v.coerceIn(1, 5); paused = false; tick++ }

    fun say(text: String, tone: Int = 0, icon: String = "alert", province: Int = -1) {
        toasts.add(0, Toast(++toastId, icon, text, tone, province, System.currentTimeMillis()))
        while (toasts.size > 4) toasts.removeAt(toasts.lastIndex)
    }

    private fun onFeed(f: FeedItem) {
        if (f.tone >= 1 || f.province >= 0 && game.owner.getOrNull(f.province) == game.player) say(f.text, f.tone, f.icon, f.province)
        val fx = when {
            f.icon == "nuke" && f.tone >= 2 -> Sfx.NUKE
            f.icon == "war" && f.tone >= 3 -> Sfx.WAR
            f.icon == "protest" && f.tone >= 3 -> Sfx.WAR
            f.icon == "battle" && f.tone >= 2 -> Sfx.BATTLE
            f.icon == "victory" && f.tone == 1 -> Sfx.VICTORY
            f.icon == "defeat" && f.tone >= 2 -> Sfx.DEFEAT
            f.icon == "flag" && f.tone == 1 -> Sfx.CAPTURE
            f.icon == "flag" && f.tone >= 3 -> Sfx.DEFEAT
            f.icon == "factory" && f.tone == 1 -> Sfx.BUILD
            else -> null
        }
        if (fx != null) feedback?.play(fx)
    }

    /** Executa uma ação do jogador; mostra o motivo se ela for recusada. */
    fun act(okText: String? = null, block: () -> String?) {
        val why = block()
        if (why != null) say(why, 2) else if (okText != null) say(okText, 1, "flag")
        tick++
    }

    fun selectProvince(p: Int) { selProvince = p; selArmy = -1; tick++ }

    fun openTab(t: Tab?) { tab = if (tab == t) null else t; tick++ }

    fun autosave() = save("auto")

    fun save(slot: String) {
        val text = runCatching { SaveStore.json.encodeToString(game) }.getOrNull() ?: return
        val meta = me.name
        scope.launch(Dispatchers.IO) {
            runCatching { store.saveText(slot, text, game.day, meta, game.leaderTitle.label) }
        }
    }
}

/** Grava texto já serializado (feito na thread do jogo). */
fun SaveStore.saveText(slot: String, json: String, day: Int, country: String, title: String) {
    val f = dirFile()
    val tmp = File(f, "save_$slot.tmp")
    tmp.writeText(json)
    val target = File(f, "save_$slot.json")
    if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    writeMeta(slot, country, day, title)
}
