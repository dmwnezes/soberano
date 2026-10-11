package com.dmwnezes.soberano.sim

import com.dmwnezes.soberano.world.WorldMap
import java.time.LocalDate

/** Data inicial da partida. */
val START_DATE: LocalDate = LocalDate.of(2026, 1, 1)

/**
 * O jogo em execução: estado salvo ([game]) + mapa fixo ([world]).
 * Tudo roda na thread que chama [advanceDay]; a interface lê o estado entre um dia e outro.
 */
class Sim(val world: WorldMap, val game: Game) {
    val economy = Economy(this)
    val politics = Politics(this)
    val diplomacy = Diplomacy(this)
    val military = Military(this)
    val ai = Ai(this)
    val events = Events(this)
    val dev = Development(this)
    val civil = CivilWars(this)
    val unitedNations = UnitedNations(this)
    val news = News(this)
    val policy = Policies(this)

    init {
        LawMaker.register(game)
        game.ensureArrays()
        if (game.generalPool.isEmpty()) military.refreshGeneralPool()
        dev.ensureCabinet()
        // saves da versão 0.1 usavam outra paleta
        for (n in game.nations) {
            val k = Setup.OLD_PALETTE.indexOf(n.mapColor)
            if (k >= 0) n.mapColor = Setup.PALETTE[k]
        }
    }

    /** Muda a cada dia simulado ou ação; a interface usa para redesenhar. */
    var revision = 0L
        private set

    fun touch() { revision++ }

    val player get() = game.nations[game.player]
    fun nation(id: Int) = game.nations[id]
    fun date(): LocalDate = START_DATE.plusDays(game.day.toLong())

    // ------------------------------------------------------------- sorteio determinístico

    fun rand(): Double {
        var x = game.rngState
        x = x xor (x shl 13); x = x xor (x ushr 7); x = x xor (x shl 17)
        game.rngState = x
        return ((x ushr 11).toDouble() / (1L shl 53).toDouble())
    }
    fun randf() = rand().toFloat()
    fun chance(p: Double) = rand() < p
    fun randInt(n: Int) = (rand() * n).toInt().coerceIn(0, maxOf(0, n - 1))
    fun <T> pick(list: List<T>): T = list[randInt(list.size)]

    // ------------------------------------------------------------- relações e guerra

    fun atWar(a: Int, b: Int): Boolean {
        if (a == b || a < 0 || b < 0) return false
        var c = game.warCache
        val n = game.n
        if (c == null) {
            c = BooleanArray(n * n)
            for (w in game.wars) for (x in w.attackers) for (y in w.defenders) { c[x * n + y] = true; c[y * n + x] = true }
            game.warCache = c
        }
        return c[a * n + b]
    }

    fun warBetween(a: Int, b: Int): War? = game.wars.firstOrNull {
        (a in it.attackers && b in it.defenders) || (b in it.attackers && a in it.defenders)
    }

    fun atWarAny(a: Int) = game.wars.any { it.involves(a) }

    private fun treaties(): TreatyCache {
        game.treatyCache?.let { return it }
        val n = game.n
        val keys = HashSet<Long>(game.treaties.size * 2 + 16)
        val allied = BooleanArray(n * n)
        for (t in game.treaties) {
            keys += TreatyCache.key(t.type, t.a, t.b)
            if (t.type.bilateral) keys += TreatyCache.key(t.type, t.b, t.a)
            if (t.type == TreatyType.ALLIANCE) { allied[t.a * n + t.b] = true; allied[t.b * n + t.a] = true }
        }
        for (b in game.blocs) if (b.military) for (x in b.members) for (y in b.members) allied[x * n + y] = true
        for (i in 0 until n) allied[i * n + i] = true
        return TreatyCache(keys, allied).also { game.treatyCache = it }
    }

    /** Acordo bilateral em qualquer direção (sanção/embargo: só a→b). */
    fun hasTreaty(t: TreatyType, a: Int, b: Int): Boolean = TreatyCache.key(t, a, b) in treaties().keys

    fun allied(a: Int, b: Int): Boolean {
        if (a < 0 || b < 0) return false
        return treaties().allied[a * game.n + b]
    }

    /** Pode haver tropas de [n] dentro da província [p]? */
    fun canEnter(n: Int, p: Int): Boolean {
        val c = game.controller[p]
        if (c == n) return true
        if (atWar(n, c)) return true
        if (allied(n, c) || hasTreaty(TreatyType.PASSAGE, n, c)) return true
        return false
    }

    /** Dois lados se enfrentam numa província? */
    fun hostile(a: Int, b: Int) = atWar(a, b)

    // ------------------------------------------------------------- avisos

    fun feed(icon: String, text: String, tone: Int = 0, province: Int = -1) {
        game.feed.add(0, FeedItem(game.day, icon, text, tone, province))
        if (game.feed.size > 120) game.feed.removeAt(game.feed.lastIndex)
        news.collect(game.feed[0])
        listener?.invoke(game.feed[0])
    }

    /** A interface recebe avisos novos para mostrar como toast. */
    var listener: ((FeedItem) -> Unit)? = null

    fun involvesPlayer(vararg ids: Int) = ids.any { it == game.player }

    // ------------------------------------------------------------- passo do tempo

    /** Simula um dia. */
    fun advanceDay() {
        if (game.gameOver != null) return
        val before = date()
        game.day++
        val today = date()

        military.dailyProduction()
        military.dailyForts()
        military.dailyMovement()
        military.dailyCombat()
        military.dailyAutoMerge()
        military.dailySiege()
        ai.daily()

        if (today.month != before.month) monthly()
        diplomacy.expireTreaties()
        touch()
    }

    private fun monthly() {
        game.invalidate()
        for (n in game.nations) if (n.alive) {
            economy.monthly(n)
            policy.monthly(n)
            politics.monthly(n)
        }
        military.monthlyResistance()
        diplomacy.monthly()
        dev.monthly()
        civil.monthly()
        unitedNations.monthly()
        events.monthly()
        politics.checkEndConditions()
        game.invalidate()
        news.publish()
    }

    // ------------------------------------------------------------- consultas para a interface

    fun provincesOf(n: Int): List<Int> = game.owner.indices.filter { game.owner[it] == n }
    fun armiesOf(n: Int) = game.armies.filter { it.owner == n }
    fun armiesAt(p: Int) = game.armies.filter { it.province == p }

    fun power(n: Int): Double = game.armies.filter { it.owner == n }.sumOf { a ->
        UnitType.entries.sumOf { t -> a.units[t.ordinal] * (t.attack + t.defense + t.antiShip * 0.5 + t.antiAir * 0.3) }
    } * (1 + 0.05 * nation(n).tech[TechBranch.LAND.ordinal])

    private var lpDay = -1
    private var lpCache = DoubleArray(0)

    /** Força terrestre (atualizada uma vez por dia). */
    fun landPower(n: Int): Double {
        if (lpDay != game.day || lpCache.size != game.n) {
            val c = DoubleArray(game.n)
            for (a in game.armies) if (a.domain == Domain.LAND) {
                var s = 0.0
                for (t in UnitType.of(Domain.LAND)) s += a.units[t.ordinal] * (t.attack + t.defense)
                c[a.owner] += s
            }
            lpCache = c; lpDay = game.day
        }
        return lpCache[n]
    }

    fun rankByGdp(): List<Nation> = game.nations.filter { it.alive }.sortedByDescending { game.natGdp(it.id) }
}
