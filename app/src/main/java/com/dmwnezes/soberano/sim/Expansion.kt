package com.dmwnezes.soberano.sim

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// =====================================================================================================
// Corrida espacial: satélites (ataque, pesquisa e espionagem melhores), estação espacial e missão à Lua.
// =====================================================================================================
class Space(private val sim: Sim) {
    private val game get() = sim.game

    val maxSatellites = 8
    val satDays = 120
    fun satCost(n: Nation) = 5.0 + n.satellites * 1.0
    val stationCost = 35.0
    val stationDays = 365
    val moonCost = 70.0
    val moonDays = 540

    private fun pay(n: Nation, cost: Double): Boolean {
        if (n.treasury + n.militaryFund < cost) return false
        val fromTreasury = min(cost, max(0.0, n.treasury))
        n.treasury -= fromTreasury; n.militaryFund -= cost - fromTreasury
        return true
    }

    fun satRefusal(n: Nation): String? = when {
        n.tech[TechBranch.AIR.ordinal] < 3 -> "Precisa de tecnologia aérea nível 3"
        n.satBuild > 0 -> "Já há um foguete sendo montado"
        n.satellites >= maxSatellites -> "Órbita cheia: máximo de $maxSatellites satélites"
        n.treasury + n.militaryFund < satCost(n) -> "Precisa de ${Fmt.money(satCost(n))}"
        else -> null
    }

    fun launchSatellite(n: Nation): String? {
        satRefusal(n)?.let { return it }
        pay(n, satCost(n))
        n.satBuild = satDays
        sim.touch()
        return null
    }

    /** Próxima etapa: 1 estação espacial, 2 Lua. */
    fun stageRefusal(n: Nation): String? = when {
        n.spaceStage >= 2 -> "Você já foi à Lua"
        n.spaceBuild > 0 -> "Missão em andamento"
        n.spaceStage == 0 && n.satellites < 2 -> "Precisa de 2 satélites em órbita"
        n.spaceStage == 1 && n.tech[TechBranch.AIR.ordinal] < 6 -> "Precisa de tecnologia aérea nível 6"
        n.spaceStage == 0 && n.tech[TechBranch.AIR.ordinal] < 4 -> "Precisa de tecnologia aérea nível 4"
        n.treasury + n.militaryFund < stageCost(n) -> "Precisa de ${Fmt.money(stageCost(n))}"
        else -> null
    }

    fun stageCost(n: Nation) = if (n.spaceStage == 0) stationCost else moonCost
    fun stageName(stage: Int) = if (stage == 0) "Estação espacial" else "Missão à Lua"

    fun startStage(n: Nation): String? {
        stageRefusal(n)?.let { return it }
        pay(n, stageCost(n))
        n.spaceBuild = if (n.spaceStage == 0) stationDays else moonDays
        n.groupMods[Group.YOUTH.ordinal] += 3f
        sim.touch()
        return null
    }

    fun daily() {
        for (n in game.nations) {
            if (!n.alive) continue
            if (n.satBuild > 0 && --n.satBuild == 0) {
                n.satellites++
                if (n.id == game.player) sim.feed("satellite", "Satélite em órbita (${n.satellites} no total)", 1)
            }
            if (n.spaceBuild > 0 && --n.spaceBuild == 0) {
                n.spaceStage++
                val first = if (n.spaceStage == 1) game.stationFirst < 0 else game.moonFirst < 0
                if (n.spaceStage == 1 && first) game.stationFirst = n.id
                if (n.spaceStage == 2 && first) game.moonFirst = n.id
                val what = if (n.spaceStage == 1) "estação espacial" else "missão à Lua"
                for (g in Group.entries) n.groupMods[g.ordinal] += if (first) 6f else 3f
                n.stability += if (first) 5f else 2f
                for (o in game.nations) if (o.alive && o.id != n.id) game.addRel(o.id, n.id, if (first) 4f else 1f)
                val text = if (first) "${n.name} é o primeiro país com $what!" else "${n.name} concluiu a $what"
                sim.feed("rocket", text, if (n.id == game.player) 1 else 0)
            }
        }
    }

    /** Satélites dão reconhecimento: +3% em combate cada (até +15%). */
    fun combatMult(owner: Int): Float = 1f + min(0.15f, sim.nation(owner).satellites * 0.03f) + sim.nation(owner).spaceStage * 0.01f
    fun researchBonus(n: Nation): Float = n.satellites * 0.4f + n.spaceStage * 2f
    fun spyBonus(n: Int): Float = min(0.2f, sim.nation(n).satellites * 0.04f)

    fun ai(n: Nation) {
        val gdp = game.natGdp(n.id)
        if (gdp < 600) return
        if (n.satellites < 2 + (gdp / 4000).toInt() && satRefusal(n) == null && sim.randf() < 0.3f) launchSatellite(n)
        else if (gdp > 3000 && stageRefusal(n) == null && sim.randf() < 0.05f) startStage(n)
    }
}

// =====================================================================================================
// Rotas comerciais, canais estratégicos e bloqueio naval.
// =====================================================================================================
class Canal(val name: String, val province: Int)

class TradeRoutes(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    val canals: List<Canal> by lazy {
        fun find(name: String, code: String): Int =
            world.provinces.firstOrNull { it.name.equals(name, true) && it.coastal }?.id ?: world.byCode[code]?.capital ?: -1
        listOf(Canal("Canal de Suez", find("Suez", "EGY")), Canal("Canal do Panamá", find("Colón", "PAN")), Canal("Estreito de Malaca", find("Singapore", "SGP")))
            .filter { it.province >= 0 }
    }

    /** O canal está fechado para [n]: quem controla está em guerra com ele ou o embargou. */
    fun canalClosed(c: Canal, n: Int): Boolean {
        val ctrl = game.controller[c.province]
        if (ctrl == n) return false
        return sim.atWar(ctrl, n) || game.treaties.any { it.type == TreatyType.EMBARGO && it.a == ctrl && it.b == n }
    }

    fun canalsControlled(n: Int) = canals.count { game.controller[it.province] == n }

    fun blockaded(n: Int) = game.blockades.any { it.target == n }
    fun blockading(by: Int, target: Int) = game.blockades.any { it.by == by && it.target == target }

    /** Efeito no crescimento: canais fechados atrapalham quem depende de comércio; bloqueio sufoca. */
    fun growthEffect(n: Nation): Float {
        var g = canalsControlled(n.id) * 0.002f
        if (n.income >= 3) g -= canals.count { canalClosed(it, n.id) } * 0.003f
        if (blockaded(n.id)) g -= 0.015f
        return g
    }

    fun blockadeRefusal(by: Nation, target: Int): String? {
        if (!sim.atWar(by.id, target)) return "Só contra quem está em guerra com você"
        if (blockading(by.id, target)) return null
        if (sim.provincesOf(target).none { world.provinces[it].coastal }) return "Eles não têm litoral"
        val mine = navy(by.id); val theirs = navy(target)
        if (mine <= 0f) return "Você não tem frota"
        if (mine < theirs * 1.2f) return "Sua marinha precisa ser mais forte que a deles"
        return null
    }

    fun navy(n: Int) = game.armies.filter { it.owner == n && it.domain == Domain.SEA }.sumOf { sim.military.navalPower(it).toDouble() }.toFloat()

    /** Liga ou desliga o bloqueio. */
    fun toggleBlockade(by: Nation, target: Int): String? {
        if (blockading(by.id, target)) {
            game.blockades.removeAll { it.by == by.id && it.target == target }
            sim.touch(); return null
        }
        blockadeRefusal(by, target)?.let { return it }
        game.blockades += Blockade(by.id, target, game.day)
        sim.nation(target).stability -= 2f
        if (target == game.player) sim.feed("ship", "${by.name} bloqueou seus portos", 3)
        else if (by.id == game.player) sim.feed("ship", "Bloqueio naval contra ${sim.nation(target).name}", 1)
        sim.touch()
        return null
    }

    fun monthly() {
        val it = game.blockades.iterator()
        while (it.hasNext()) {
            val b = it.next()
            val lost = !sim.atWar(b.by, b.target) || !sim.nation(b.by).alive || !sim.nation(b.target).alive || navy(b.by) < navy(b.target) * 0.9f
            if (lost) {
                it.remove()
                if (b.target == game.player) sim.feed("ship", "O bloqueio aos seus portos acabou", 1)
                else if (b.by == game.player) sim.feed("ship", "Seu bloqueio a ${sim.nation(b.target).name} acabou", 2)
            } else sim.nation(b.target).stability -= 0.5f
        }
    }

    fun ai(n: Nation) {
        if (!sim.atWarAny(n.id)) return
        for (w in game.wars) if (w.involves(n.id)) {
            val enemy = if (n.id in w.attackers) w.leaderD() else w.leaderA()
            if (!blockading(n.id, enemy) && blockadeRefusal(n, enemy) == null && sim.randf() < 0.4f) toggleBlockade(n, enemy)
        }
    }

    /** Rotas para desenhar: acordos de comércio e contratos de recursos (de/para, tipo 0 acordo, 1 contrato). */
    fun routes(focus: Int): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        val big = game.nations.filter { it.alive }.sortedByDescending { game.natGdp(it.id) }.take(25).map { it.id }.toSet()
        for (t in game.treaties) if (t.type == TreatyType.TRADE) {
            if (t.a == focus || t.b == focus || (t.a in big && t.b in big)) out += Triple(t.a, t.b, 0)
        }
        for (d in game.deals) if (d.seller == focus || d.buyer == focus) out += Triple(d.seller, d.buyer, 1)
        return out
    }
}

// =====================================================================================================
// Guerrilhas, milícias e terrorismo em estados instáveis.
// =====================================================================================================
class Insurgencies(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    private val prefixes = mapOf(
        InsurgentKind.GUERRILLA to listOf("Frente de Libertação de", "Exército Popular de", "Movimento Armado de"),
        InsurgentKind.TERROR to listOf("Célula Negra de", "Brigada Sombria de", "Irmandade de"),
        InsurgentKind.MILITIA to listOf("Milícia de", "Comando de", "Facção de"),
    )

    fun at(p: Int) = game.insurgents.firstOrNull { it.province == p }
    fun of(n: Int) = game.insurgents.filter { game.controller[it.province] == n }

    fun monthly() {
        // nascem onde o Estado é fraco
        if (game.insurgents.size < 45) for (n in game.nations) {
            if (!n.alive || game.natProvinces(n.id) == 0) continue
            val weak = max(0f, 38f - n.stability) / 100f + max(0f, 35f - n.index[Area.SECURITY.ordinal]) / 120f
            if (weak <= 0f || of(n.id).size >= 3) continue
            if (sim.randf() < weak * 0.25f) spawn(n)
        }
        for (g in game.insurgents.toList()) {
            if (g !in game.insurgents) continue
            val ctrl = game.controller[g.province]
            val n = sim.nation(ctrl)
            val sec = n.index[Area.SECURITY.ordinal]
            g.strength += if (sec < 40f || n.stability < 30f) 0.3f else -0.2f
            // tropas paradas no estado combatem o grupo sozinhas
            val garrison = game.armies.filter { a -> a.owner == ctrl && a.province == g.province && a.domain == Domain.LAND && !a.moving && !a.battle }
                .sumOf { a -> sim.military.landStrength(a).toDouble() }.toFloat()
            g.strength -= min(2f, garrison / 40f)
            if (g.strength <= 0f) {
                game.insurgents.remove(g)
                if (ctrl == game.player) sim.feed("shield", "${g.name} foi desarticulado", 1, g.province)
                continue
            }
            g.strength = min(g.strength, 10f)
            // estragos
            game.provGdp[g.province] *= 1.0 - 0.01 * g.strength
            n.stability -= 0.25f * g.strength
            if (g.kind == InsurgentKind.TERROR && sim.randf() < 0.12f * g.strength / 3f) {
                n.stability -= 2f
                n.groupMods[Group.RETIREES.ordinal] -= 2f; n.groupMods[Group.WORKERS.ordinal] -= 1f
                if (ctrl == game.player) sim.feed("bomb", "Atentado em ${world.provinces[g.province].name} (${g.name})", 3, g.province)
            }
            if (g.kind == InsurgentKind.MILITIA) n.corruption = min(100f, n.corruption + 0.1f * g.strength)
            // computador combate com operações
            if (ctrl != game.player && sim.randf() < 0.3f) operation(n, g, silent = true)
        }
    }

    private fun spawn(n: Nation) {
        val own = sim.provincesOf(n.id).filter { game.controller[it] == n.id && at(it) == null }
        if (own.isEmpty()) return
        val p = own[sim.randInt(own.size)]
        val kind = sim.pick(InsurgentKind.entries)
        val name = "${sim.pick(prefixes.getValue(kind))} ${world.provinces[p].name}"
        val g = Insurgency(game.nextInsurgentId++, p, n.id, kind, name, 1f + sim.randf() * 2f, game.day)
        game.insurgents += g
        if (n.id == game.player) sim.feed(kind.icon, "${kind.label} surgiu em ${world.provinces[p].name}", 2, p)
    }

    fun operationCost(n: Nation) = game.natGdp(n.id) / 12.0 * 0.01 + 0.5

    /** Operação policial/militar contra o grupo. */
    fun operation(n: Nation, g: Insurgency, silent: Boolean = false): String? {
        val cost = operationCost(n)
        if (n.treasury + n.militaryFund < cost) return "Precisa de ${Fmt.money(cost)}"
        val fromFund = min(cost, max(0.0, n.militaryFund))
        n.militaryFund -= fromFund; n.treasury -= cost - fromFund
        val here = game.armies.any { it.owner == n.id && it.province == g.province && it.domain == Domain.LAND }
        val power = 1.5f + n.index[Area.SECURITY.ordinal] / 30f + (if (here) 2f else 0f) + sim.space.spyBonus(n.id) * 5f
        val hit = power * (0.6f + sim.randf() * 0.8f)
        g.strength -= hit
        if (g.strength <= 0f) {
            game.insurgents.remove(g)
            if (!silent) sim.feed("shield", "Operação desmontou ${g.name}", 1, g.province)
            n.groupMods[Group.RETIREES.ordinal] += 2f
        } else {
            if (sim.randf() < 0.3f) { n.groupMods[Group.YOUTH.ordinal] -= 2f; if (!silent) sim.feed("alert", "Operação com mortes de civis em ${world.provinces[g.province].name}", 2, g.province) }
            else if (!silent) sim.feed("shield", "${g.name} perdeu força (resta ${Fmt.one(g.strength)})", 0, g.province)
        }
        sim.touch()
        return null
    }

    val negotiateCost = 8f

    /** Acordo de desarmamento: custa capital político; dá certo mais vezes com grupos pequenos. */
    fun negotiate(n: Nation, g: Insurgency): String? {
        if (g.kind == InsurgentKind.TERROR) return "Terroristas não negociam"
        if (n.politicalCapital < negotiateCost) return "Precisa de ${negotiateCost.toInt()} de capital político"
        n.politicalCapital -= negotiateCost
        if (sim.randf() < 0.75f - g.strength * 0.05f) {
            game.insurgents.remove(g)
            n.stability += 2f
            n.groupMods[Group.MILITARY.ordinal] -= 2f
            sim.feed("handshake", "${g.name} aceitou depor as armas", 1, g.province)
        } else sim.feed("alert", "${g.name} recusou o acordo", 2, g.province)
        sim.touch()
        return null
    }
}

// =====================================================================================================
// Congresso: partidos, coalizão do governo e votação nominal das leis.
// =====================================================================================================
class Congress(private val sim: Sim) {
    private val game get() = sim.game
    val totalSeats = 500

    private val names = mapOf(
        Ideology.LEFT to listOf("Partido Popular dos Trabalhadores" to "PPT", "Frente Socialista" to "FS", "União Progressista" to "UP"),
        Ideology.GREEN to listOf("Rede Verde" to "RV", "Partido Ecologista" to "PE"),
        Ideology.CENTER to listOf("Movimento Democrático" to "MD", "Aliança Nacional de Centro" to "ANC", "Partido Social Unido" to "PSU"),
        Ideology.RIGHT to listOf("Partido Liberal Republicano" to "PLR", "Novo Rumo" to "NR", "Renovação Democrática" to "RD"),
        Ideology.RELIGIOUS to listOf("Partido da Família" to "PF", "Aliança Cristã" to "AC"),
        Ideology.NATIONALIST to listOf("Pátria Forte" to "PTF", "Frente Nacional" to "FN"),
    )

    fun active(n: Nation = sim.player) = n.id == game.player && n.regime.congress && game.parties.isNotEmpty()

    /** Cria os partidos do país do jogador (uma vez, ou ao voltar à democracia). */
    fun ensure() {
        val me = sim.player
        if (!me.regime.congress || game.parties.isNotEmpty()) return
        val ids = Ideology.entries.shuffledWith().take(5)
        val raw = ids.map { 0.5f + sim.randf() }
        val sum = raw.sum()
        var left = totalSeats
        ids.forEachIndexed { i, ide ->
            val seats = if (i == ids.lastIndex) left else (raw[i] / sum * totalSeats).toInt()
            left -= seats
            val (nm, sh) = sim.pick(names.getValue(ide))
            game.parties += Party(i, nm, sh, ide, seats)
        }
        formCoalition(me.congress)
    }

    private fun <T> List<T>.shuffledWith(): List<T> {
        val l = toMutableList()
        for (i in l.indices.reversed()) { val j = sim.randInt(i + 1); val t = l[i]; l[i] = l[j]; l[j] = t }
        return l
    }

    /** Monta uma coalizão com cerca de [targetPct]% das cadeiras, começando pelo centro. */
    private fun formCoalition(targetPct: Float) {
        for (p in game.parties) p.inCoalition = false
        val order = game.parties.sortedBy { if (it.ideology == Ideology.CENTER) 0 else 1 }
        var share = 0
        for (p in order) {
            if (share * 100f / totalSeats >= targetPct.coerceIn(40f, 75f)) break
            p.inCoalition = true; share += p.seats
        }
    }

    fun coalitionSeats() = game.parties.filter { it.inCoalition }.sumOf { it.seats }
    fun coalitionPct() = coalitionSeats() * 100f / totalSeats

    /** Apoio no Congresso (substitui o número simples quando há partidos). */
    fun support(n: Nation): Float {
        val base = coalitionPct()
        val mood = game.parties.filter { it.inCoalition }.let { l -> if (l.isEmpty()) 50f else l.map { it.mood }.average().toFloat() }
        return (base + (mood - 50f) * 0.2f + (n.approval - 50f) * 0.15f).coerceIn(5f, 95f)
    }

    /** Quanto a lei agrada ao partido (positivo = gosta). */
    private fun affinity(p: Party, law: Law): Float {
        var s = 0f
        for ((g, v) in law.groups) s += v * (p.ideology.weights[g] ?: 0f)
        if (p.ideology == Ideology.GREEN) s += (law.index[Area.ENVIRONMENT] ?: 0f) * 0.8f
        if (p.ideology == Ideology.RIGHT) s += law.growth * 600f - law.revenue * 40f
        if (p.ideology == Ideology.LEFT) s += (law.index[Area.SOCIAL] ?: 0f) * 0.6f
        if (p.ideology == Ideology.NATIONALIST) s += (law.index[Area.SECURITY] ?: 0f) * 0.5f
        return s
    }

    /** Votos de cada partido: (partido, sim, não). Os partidos racham conforme a afinidade. */
    fun vote(law: Law): List<Triple<Party, Int, Int>> = game.parties.map { p ->
        val loyalty = if (p.inCoalition) 4f + (p.mood - 50f) / 10f else -2f + (p.mood - 50f) / 20f
        val score = affinity(p, law) / 3f + loyalty
        val yesFrac = (0.5f + score / 12f).coerceIn(0.03f, 0.97f)
        val yes = (p.seats * yesFrac).toInt()
        Triple(p, yes, p.seats - yes)
    }

    fun tally(law: Law): Pair<Int, Int> { val v = vote(law); return v.sumOf { it.second } to v.sumOf { it.third } }
    fun passes(law: Law) = tally(law).let { it.first > it.second }

    /** Depois de uma lei aprovada, quem gostou fica mais feliz. */
    fun afterLaw(law: Law) {
        for (p in game.parties) p.mood = (p.mood + affinity(p, law) / 2f).coerceIn(0f, 100f)
    }

    val postsCost = 8f

    /** Oferece ministérios e cargos: o partido entra na base (custa capital e aumenta a corrupção). */
    fun offerPosts(p: Party): String? {
        val me = sim.player
        if (p.inCoalition) return "Já está na base"
        if (me.politicalCapital < postsCost) return "Precisa de ${postsCost.toInt()} de capital político"
        me.politicalCapital -= postsCost
        if (p.mood < 25f && sim.randf() < 0.6f) { sim.touch(); return "${p.short} recusou os cargos" }
        p.inCoalition = true
        p.mood = min(100f, p.mood + 15f)
        me.corruption = min(100f, me.corruption + 1.5f)
        sim.feed("handshake", "${p.name} entrou na base do governo", 1)
        sim.touch()
        return null
    }

    fun dropParty(p: Party): String? {
        if (!p.inCoalition) return null
        p.inCoalition = false
        p.mood = max(0f, p.mood - 15f)
        sim.feed("alert", "${p.name} saiu da base do governo", 2)
        sim.touch()
        return null
    }

    fun monthly() {
        val me = sim.player
        if (!me.regime.congress) { game.parties.clear(); return }
        ensure()
        for (p in game.parties) {
            val target = 50f + (me.approval - 50f) * (if (p.inCoalition) 0.5f else -0.3f)
            p.mood += (target - p.mood) * 0.08f
            if (p.inCoalition && p.mood < 18f && sim.randf() < 0.3f) dropParty(p)
        }
        me.congress = support(me)
    }

    /** Depois da eleição o Congresso muda com o resultado. */
    fun afterElection(share: Float, won: Boolean) {
        if (game.parties.isEmpty()) return
        val delta = ((share - 50f) / 100f * 0.6f)
        val coal = game.parties.filter { it.inCoalition }
        val opp = game.parties.filter { !it.inCoalition }
        if (coal.isEmpty() || opp.isEmpty()) return
        val move = (abs(delta) * totalSeats).toInt().coerceAtMost(80)
        val (gain, lose) = if (delta >= 0) coal to opp else opp to coal
        var moved = 0
        for (p in lose) { val m = min(p.seats - 5, move / lose.size); if (m > 0) { p.seats -= m; moved += m } }
        for ((i, p) in gain.withIndex()) p.seats += moved / gain.size + if (i == 0) moved % gain.size else 0
        if (!won) for (p in coal) p.mood = max(0f, p.mood - 10f)
    }
}

// =====================================================================================================
// Pronunciamento à nação: tema + tom → reação do país.
// =====================================================================================================
enum class SpeechTopic(val label: String, val icon: String) {
    ECONOMY("Economia", "money"), SECURITY("Segurança", "siren"), WAR("Guerra", "war"), CRISIS("Crise", "alert"), UNITY("Unidade nacional", "flag"),
}

enum class SpeechTone(val label: String, val desc: String) {
    CALM("Calmo", "Tranquiliza e passa confiança"), FIRM("Firme", "Mostra pulso e autoridade"), POPULIST("Populista", "Promete muito e inflama o povo"),
}

class SpeechResult(val headline: String, val reception: Int, val effects: List<Pair<String, Boolean>>)

class Speeches(private val sim: Sim) {
    private val game get() = sim.game
    val cooldown = 45

    fun waitDays(n: Nation) = max(0, cooldown - (game.day - n.lastSpeech))

    fun give(n: Nation, topic: SpeechTopic, tone: SpeechTone): SpeechResult? {
        if (waitDays(n) > 0) return null
        val fatigue = if (game.day - n.lastSpeech < 120) 0.6f else 1f
        n.lastSpeech = game.day
        val atWar = sim.atWarAny(n.id)
        // recepção: aprovação, tema adequado ao momento e sorte
        val fits = when (topic) {
            SpeechTopic.ECONOMY -> n.growth < 0.015f || n.inflation > 7f || n.unemployment > 9f
            SpeechTopic.SECURITY -> n.index[Area.SECURITY.ordinal] < 50f || sim.insurgency.of(n.id).isNotEmpty()
            SpeechTopic.WAR -> atWar
            SpeechTopic.CRISIS -> n.stability < 50f
            SpeechTopic.UNITY -> true
        }
        val roll = sim.randf()
        val q = ((n.approval - 40f) / 40f + (if (fits) 0.4f else -0.3f) + (roll - 0.5f)).coerceIn(-1f, 1.2f) * fatigue
        val reception = when { q > 0.45f -> 2; q > -0.05f -> 1; else -> 0 }
        val k = when (reception) { 2 -> 1.3f; 1 -> 0.7f; else -> -0.6f } * fatigue
        val effects = ArrayList<Pair<String, Boolean>>()
        val sums = LinkedHashMap<String, Float>()
        fun group(g: Group, v: Float) { val d = v * k; n.groupMods[g.ordinal] += d; sums[g.label] = (sums[g.label] ?: 0f) + d }
        fun stab(v: Float) { val d = v * k; n.stability = (n.stability + d).coerceIn(0f, 100f); sums["Estabilidade"] = (sums["Estabilidade"] ?: 0f) + d }
        when (tone) {
            SpeechTone.CALM -> { stab(2f); group(Group.BUSINESS, 2f); group(Group.RETIREES, 2f) }
            SpeechTone.FIRM -> { stab(1f); group(Group.MILITARY, 3f); group(Group.RELIGIOUS, 2f); group(Group.YOUTH, -2f) }
            SpeechTone.POPULIST -> { group(Group.WORKERS, 5f); group(Group.YOUTH, 2f); group(Group.BUSINESS, -4f) }
        }
        when (topic) {
            SpeechTopic.ECONOMY -> {
                if (tone == SpeechTone.CALM && reception > 0) { n.currency = min(1.8f, n.currency + 0.03f); effects += "Moeda se valoriza" to true }
                if (tone == SpeechTone.POPULIST) { n.inflation += 0.4f; effects += "Mercado teme inflação" to false }
            }
            SpeechTopic.SECURITY -> if (tone == SpeechTone.FIRM) { group(Group.RETIREES, 3f); stab(1f) }
            SpeechTopic.WAR -> if (atWar) {
                val cut = (if (tone == SpeechTone.FIRM) 0.08f else 0.04f) * max(0f, k)
                if (cut > 0f) { n.warExhaustion = max(0f, n.warExhaustion - cut); effects += "Cansaço de guerra diminui" to true }
                if (tone == SpeechTone.FIRM) group(Group.MILITARY, 3f)
            } else if (tone == SpeechTone.FIRM) {
                for (o in sim.ai.neighborsOf(n.id)) game.addRel(n.id, o, -3f)
                effects += "Vizinhos veem ameaça" to false
            }
            SpeechTopic.CRISIS -> if (tone == SpeechTone.CALM) stab(3f) else if (tone == SpeechTone.POPULIST) stab(-1f)
            SpeechTopic.UNITY -> { for (g in Group.entries) group(g, 0.8f); stab(1.5f) }
        }
        if (tone == SpeechTone.POPULIST) { n.politicalCapital = min(100f, n.politicalCapital + 3f * max(0f, k)); if (k > 0) effects += "Capital político +${(3f * k).toInt()}" to true }
        val merged = sums.filter { abs(it.value) >= 0.5f }.map { "${it.key} ${sign(it.value)}" to (it.value > 0) }
        effects.addAll(0, merged)
        val headline = when (reception) {
            2 -> listOf("Discurso emociona o país", "Pronunciamento é aplaudido", "Fala do governo conquista a nação").let { it[sim.randInt(it.size)] }
            1 -> listOf("Discurso recebido com cautela", "Pronunciamento divide opiniões", "Fala morna, efeito pequeno").let { it[sim.randInt(it.size)] }
            else -> listOf("Panelaço durante o pronunciamento", "Discurso é vaiado nas redes", "Fala do governo vira piada").let { it[sim.randInt(it.size)] }
        }
        sim.feed("vote", headline, if (reception == 0) 2 else 1)
        sim.touch()
        return SpeechResult(headline, reception, effects)
    }

    private fun sign(v: Float) = (if (v >= 0) "+" else "−") + Fmt.one(abs(v))
}

// =====================================================================================================
// Moeda e câmbio.
// =====================================================================================================
class Currency(private val sim: Sim) {
    private val game get() = sim.game

    /** (símbolo, unidades por US$ no início) para as moedas mais conhecidas. */
    private val known = mapOf(
        "BRA" to ("R$" to 5.4), "ARG" to ("$" to 1000.0), "MEX" to ("MX$" to 17.5), "USA" to ("US$" to 1.0), "CAN" to ("C$" to 1.36),
        "GBR" to ("£" to 0.78), "JPN" to ("¥" to 150.0), "CHN" to ("¥" to 7.2), "RUS" to ("₽" to 90.0), "IND" to ("₹" to 83.0),
        "CHL" to ("CLP" to 930.0), "COL" to ("COP" to 4000.0), "PER" to ("S/" to 3.75), "URY" to ("UY$" to 39.0), "PRY" to ("₲" to 7400.0),
        "BOL" to ("Bs" to 6.9), "VEN" to ("Bs" to 36.0), "ZAF" to ("R" to 18.5), "TUR" to ("₺" to 32.0), "KOR" to ("₩" to 1350.0),
        "AUS" to ("A$" to 1.52), "CHE" to ("CHF" to 0.88), "SAU" to ("SAR" to 3.75), "EGY" to ("E£" to 48.0), "NGA" to ("₦" to 1500.0),
        "ISR" to ("₪" to 3.7), "IDN" to ("Rp" to 15800.0), "PAK" to ("Rs" to 280.0), "UKR" to ("₴" to 40.0), "IRN" to ("IRR" to 42000.0),
    )
    private val euro = setOf("DEU", "FRA", "ITA", "ESP", "PRT", "NLD", "BEL", "AUT", "IRL", "FIN", "GRC", "SVK", "SVN", "EST", "LVA", "LTU", "LUX", "MLT", "CYP", "HRV")

    fun symbolAndBase(n: Nation): Pair<String, Double> = known[n.code] ?: if (n.code in euro) "€" to 0.92 else "¤" to 10.0

    /** "US$ 1 = R$ 5,40" */
    fun rateText(n: Nation): String {
        if (n.code == "USA") return "Dólar é a referência"
        val (sym, base) = symbolAndBase(n)
        val v = base / n.currency
        val shown = if (v >= 100) "%,.0f".format(v).replace(',', '.') else "%.2f".format(v).replace('.', ',')
        return "US$ 1 = $sym $shown"
    }

    fun target(n: Nation): Float {
        val gdp = game.natGdp(n.id).coerceAtLeast(0.1)
        val debt = (n.debt / gdp).toFloat()
        var t = 1f + (n.policyRate - n.inflation - 2f) * 0.025f - max(0f, n.inflation - 5f) * 0.015f - max(0f, debt - 0.6f) * 0.15f +
            (n.stability - 50f) * 0.004f + (sim.dev.tradeBalance(n.id) / gdp.toFloat()) * 1.5f - sim.economy.sanctionPenalty(n.id) * 6f - n.printed * 0.03f
        if (sim.trade.blockaded(n.id)) t -= 0.1f
        if (n.code == "USA") t = 1f
        return t.coerceIn(0.3f, 1.8f)
    }

    fun monthly(n: Nation) {
        val before = n.currency
        n.currency += (target(n) - n.currency) * 0.15f + (sim.randf() - 0.5f) * 0.02f
        n.currency = n.currency.coerceIn(0.25f, 2f)
        if (before >= 0.6f && n.currency < 0.6f) {
            n.stability -= 5f
            n.groupMods[Group.WORKERS.ordinal] -= 4f; n.groupMods[Group.RETIREES.ordinal] -= 4f
            if (n.id == game.player) sim.feed("chart_down", "Crise cambial: a moeda despencou", 3)
        }
    }

    /** Moeda fraca: exporta mais (cresce) mas importa inflação; forte: o contrário. */
    fun growthEffect(n: Nation): Float = ((1f - n.currency) * 0.012f).coerceIn(-0.01f, 0.008f)
    fun inflationEffect(n: Nation): Float = ((1f - n.currency) * 5f).coerceIn(-2f, 6f)
    /** Parte da dívida é em dólar: moeda fraca encarece os juros. */
    fun interestMult(n: Nation): Float = 0.7f + 0.3f / n.currency.coerceAtLeast(0.3f)

    fun interventionCost(n: Nation) = game.natGdp(n.id) * 0.01

    /** O banco central vende reservas para segurar a moeda. */
    fun intervene(n: Nation): String? {
        if (game.day - n.lastIntervention < 30) return "Espere ${30 - (game.day - n.lastIntervention)} dias"
        val cost = interventionCost(n)
        if (n.treasury < cost) return "Tesouro sem ${Fmt.money(cost)}"
        n.treasury -= cost
        n.currency = min(2f, n.currency + 0.06f)
        n.lastIntervention = game.day
        sim.touch()
        return null
    }
}

// =====================================================================================================
// Obras por província: fábricas, usinas, portos, aeroportos, universidades, hospitais e bases.
// =====================================================================================================
class Buildings(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    fun at(p: Int) = game.buildings.filter { it.province == p }
    fun has(p: Int, t: BuildingType) = game.buildings.any { it.province == p && it.type == t && it.daysLeft <= 0 }
    fun built(n: Int, t: BuildingType) = game.buildings.count { it.type == t && it.daysLeft <= 0 && game.controller[it.province] == n }

    fun cost(n: Nation, t: BuildingType) = t.baseCost * (game.natGdp(n.id) / 2000.0).coerceIn(0.2, 1.5)

    fun refusal(n: Nation, p: Int, t: BuildingType): String? = when {
        game.owner[p] != n.id || game.controller[p] != n.id -> "Precisa ser um estado seu e livre"
        game.buildings.any { it.province == p && it.type == t } -> "Já existe (ou está em obra)"
        t.coastal && !world.provinces[p].coastal -> "Precisa de litoral"
        game.buildings.count { it.daysLeft > 0 && game.controller[it.province] == n.id } >= 4 -> "Máximo de 4 obras ao mesmo tempo"
        n.treasury < cost(n, t) -> "Tesouro sem ${Fmt.money(cost(n, t))}"
        else -> null
    }

    fun build(n: Nation, p: Int, t: BuildingType): String? {
        refusal(n, p, t)?.let { return it }
        n.treasury -= cost(n, t)
        game.buildings += ProvBuilding(p, t, t.days)
        sim.touch()
        return null
    }

    fun daily() {
        for (b in game.buildings) if (b.daysLeft > 0) {
            if (game.controller[b.province] != game.owner[b.province]) continue // obra parada sob ocupação
            if (--b.daysLeft == 0) {
                game.provGdp[b.province] *= 1.0 + b.type.gdpBoost
                game.invalidate()
                if (game.controller[b.province] == game.player) sim.feed("factory", "${b.type.label} inaugurado em ${world.provinces[b.province].name}", 1, b.province)
            }
        }
    }

    fun indexBonus(n: Nation, a: Area): Float = when (a) {
        Area.HEALTH -> min(8f, built(n.id, BuildingType.HOSPITAL) * 1.5f)
        Area.EDUCATION -> min(8f, built(n.id, BuildingType.UNIVERSITY) * 1.5f)
        Area.INFRA -> min(8f, (built(n.id, BuildingType.POWER) + built(n.id, BuildingType.PORT) + built(n.id, BuildingType.AIRPORT)) * 1f)
        Area.ENVIRONMENT -> -min(8f, built(n.id, BuildingType.FACTORY) * 1f + built(n.id, BuildingType.POWER) * 0.5f)
        else -> 0f
    }

    fun researchBonus(n: Nation) = built(n.id, BuildingType.UNIVERSITY) * 0.8f
    fun tradeBonus(n: Nation) = min(0.006f, built(n.id, BuildingType.PORT) * 0.0015f)
    /** Defesa extra para quem defende um estado com base militar. */
    fun defenseMult(p: Int, defender: Int) = if (has(p, BuildingType.BASE) && game.controller[p] == defender) 1.25f else 1f

    fun ai(n: Nation) {
        if (n.treasury < 5 || sim.randf() > 0.15f) return
        val own = sim.provincesOf(n.id).filter { game.controller[it] == n.id }.sortedByDescending { game.provPop[it] }.take(6)
        if (own.isEmpty()) return
        val t = sim.pick(BuildingType.entries)
        val p = own[sim.randInt(own.size)]
        if (refusal(n, p, t) == null) build(n, p, t)
    }
}
