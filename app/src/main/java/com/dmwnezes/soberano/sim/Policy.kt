package com.dmwnezes.soberano.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Ferramentas extras de governo: política monetária, fundo soberano, estímulo e austeridade;
 * mobilização, treinamento, bombardeio estratégico, desmobilização e mercenários.
 * Todas devolvem null quando dão certo ou o motivo de não poder.
 */
class Policies(private val sim: Sim) {
    private val game get() = sim.game

    // ============================================================ economia

    fun stimulusOn(n: Nation) = n.stimulusUntil >= game.day
    fun austerityOn(n: Nation) = n.austerityUntil >= game.day
    fun drillOn(n: Nation) = n.drillUntil >= game.day

    /** Efeito no crescimento anual (somado em Economy.growthRate). */
    fun growthEffect(n: Nation): Float {
        var g = -(n.policyRate - 6f) * 0.0025f
        if (stimulusOn(n)) g += 0.012f
        if (austerityOn(n)) g -= 0.008f
        if (n.mobilized) g -= 0.015f
        return g
    }

    /** Efeito na meta de inflação (pontos percentuais). */
    fun inflationEffect(n: Nation): Float {
        var i = -(n.policyRate - 6f) * 0.8f + n.printed * 2.5f
        if (stimulusOn(n)) i += 1.5f
        if (austerityOn(n)) i -= 1f
        return i
    }

    /** Multiplicador dos juros da dívida. */
    fun interestMult(n: Nation) = 0.75f + n.policyRate / 24f

    /** Multiplicador dos gastos civis. */
    fun civilMult(n: Nation) = if (austerityOn(n)) 0.85 else 1.0

    fun setPolicyRate(n: Nation, v: Float) {
        val nv = v.coerceIn(2f, 20f)
        val d = nv - n.policyRate
        // juros altos irritam empresários; baixos agradam no curto prazo
        n.groupMods[Group.BUSINESS.ordinal] -= d * 0.6f
        n.policyRate = nv
        sim.touch()
    }

    fun printAmount(n: Nation) = game.natGdp(n.id) * 0.02

    fun printMoney(n: Nation): String? {
        if (n.printed >= 8f) return "A inflação já está fora de controle"
        n.treasury += printAmount(n)
        n.printed += 2f
        n.inflation += 1.5f
        n.groupMods[Group.BUSINESS.ordinal] -= 3f
        n.groupMods[Group.RETIREES.ordinal] -= 2f
        if (n.id == game.player) sim.feed("money", "Banco central imprimiu ${Fmt.money(printAmount(n))}", 0)
        sim.touch()
        return null
    }

    fun fundDepositAmount(n: Nation) = game.natGdp(n.id) / 12.0 * 0.1

    fun depositFund(n: Nation): String? {
        val v = fundDepositAmount(n)
        if (n.treasury < v) return "Tesouro sem ${Fmt.money(v)}"
        n.treasury -= v; n.sovereignFund += v
        sim.touch()
        return null
    }

    fun withdrawFund(n: Nation): String? {
        if (n.sovereignFund <= 0.0) return "O fundo está vazio"
        n.treasury += n.sovereignFund; n.sovereignFund = 0.0
        sim.touch()
        return null
    }

    fun stimulusCost(n: Nation) = game.natGdp(n.id) * 0.015

    fun stimulus(n: Nation): String? {
        if (stimulusOn(n)) return "Já há um pacote em andamento"
        val cost = stimulusCost(n)
        n.treasury -= cost
        if (n.treasury < 0) { n.debt += -n.treasury; n.treasury = 0.0 }
        n.stimulusUntil = game.day + 365
        n.groupMods[Group.WORKERS.ordinal] += 4f
        n.groupMods[Group.BUSINESS.ordinal] += 3f
        if (n.austerityUntil >= game.day) n.austerityUntil = -1
        sim.touch()
        return null
    }

    fun austerity(n: Nation): String? {
        if (austerityOn(n)) { n.austerityUntil = -1; sim.touch(); return null }
        n.austerityUntil = game.day + 365
        n.groupMods[Group.WORKERS.ordinal] -= 6f
        n.groupMods[Group.RETIREES.ordinal] -= 6f
        n.groupMods[Group.BUSINESS.ordinal] += 3f
        n.stability -= 2f
        if (n.stimulusUntil >= game.day) n.stimulusUntil = -1
        sim.touch()
        return null
    }

    fun monthly(n: Nation) {
        n.printed *= 0.85f
        if (n.printed < 0.05f) n.printed = 0f
        if (n.sovereignFund > 0) n.sovereignFund *= 1.0 + 0.05 / 12.0
        if (n.mobilized) n.groupMods[Group.WORKERS.ordinal] -= 0.3f
    }

    // ============================================================ militar

    val mobilizeCost = 10f

    fun toggleMobilization(n: Nation): String? {
        if (!n.mobilized) {
            if (n.politicalCapital < mobilizeCost) return "Precisa de ${mobilizeCost.toInt()} de capital político"
            n.politicalCapital -= mobilizeCost
            n.mobilized = true
            n.groupMods[Group.WORKERS.ordinal] -= 5f
            n.groupMods[Group.MILITARY.ordinal] += 6f
            if (n.id == game.player) sim.feed("military", "Mobilização geral decretada", 0)
        } else n.mobilized = false
        sim.touch()
        return null
    }

    fun drillCost(n: Nation) = game.natGdp(n.id) / 12.0 * 0.03

    fun drill(n: Nation): String? {
        if (drillOn(n)) return "Os exercícios já estão em andamento"
        val cost = drillCost(n)
        if (n.militaryFund + n.treasury < cost) return "Dinheiro insuficiente"
        val fromFund = min(cost, max(0.0, n.militaryFund))
        n.militaryFund -= fromFund; n.treasury -= cost - fromFund
        n.drillUntil = game.day + 180
        for (a in game.armies) if (a.owner == n.id) a.morale = min(1f, a.morale + 0.2f)
        n.groupMods[Group.MILITARY.ordinal] += 3f
        sim.touch()
        return null
    }

    /** Bônus de combate do treinamento. */
    fun combatMult(owner: Int): Float = if (drillOn(sim.nation(owner))) 1.1f else 1f

    fun bombingRefusal(n: Nation, target: Int): String? {
        if (!sim.atWar(n.id, target)) return "Só contra quem está em guerra com você"
        if (game.day - n.lastBombing < 30) return "Aviões ainda voltando (${30 - (game.day - n.lastBombing)} dias)"
        if (sim.unitedNations.has(ResolutionType.NO_FLY, n.id)) return "A ONU proibiu seus voos"
        if (bombingTargets(n, target).isEmpty()) return "Nenhum alvo ao alcance da sua aviação"
        return null
    }

    /** Províncias inimigas ao alcance das alas aéreas paradas. */
    fun bombingTargets(n: Nation, target: Int): List<Int> {
        val wings = game.armies.filter { it.owner == n.id && it.domain == Domain.AIR && !it.moving && it.total() >= 0.5f }
        if (wings.isEmpty()) return emptyList()
        fun range(a: Army) = UnitType.of(Domain.AIR).filter { a.units[it.ordinal] >= 0.5f }.maxOfOrNull { it.speed } ?: 0f
        return sim.provincesOf(target).filter { p -> game.controller[p] == target && wings.any { sim.world.distanceKm(it.province, p) <= range(it) } }
            .sortedByDescending { game.provGdp[it] }.take(6)
    }

    fun bomb(n: Nation, target: Int): String? {
        bombingRefusal(n, target)?.let { return it }
        val hits = bombingTargets(n, target)
        val enemy = sim.nation(target)
        for (p in hits) game.provGdp[p] *= 0.97
        enemy.index[Area.INFRA.ordinal] = max(0f, enemy.index[Area.INFRA.ordinal] - 2f)
        enemy.stability -= 1f
        // perdas para a antiaérea inimiga
        val aa = game.armies.filter { it.owner == target }.sumOf { (it.units[UnitType.ANTIAIR.ordinal] * UnitType.ANTIAIR.antiAir + it.units[UnitType.FIGHTER.ordinal] * UnitType.FIGHTER.antiAir).toDouble() }.toFloat()
        val loss = (0.03f + aa / 400f).coerceAtMost(0.15f)
        for (a in game.armies) if (a.owner == n.id && a.domain == Domain.AIR) for (t in UnitType.of(Domain.AIR)) a.units[t.ordinal] *= 1f - loss
        n.lastBombing = game.day
        game.invalidate()
        for (o in game.nations) if (o.alive && o.id != n.id && o.id != target && !sim.allied(o.id, n.id)) game.addRel(o.id, n.id, -1f)
        if (n.id == game.player) sim.feed("bomb", "Bombardeio atingiu ${hits.size} províncias de ${enemy.name} (perdemos ${(loss * 100).toInt()}% dos aviões)", 0)
        else if (target == game.player) sim.feed("bomb", "${n.name} bombardeou ${hits.size} províncias suas", 2)
        sim.touch()
        return null
    }

    /** Desfaz um exército: metade da infantaria volta a ser reservista naquele estado. */
    fun disband(a: Army): String? {
        if (a.battle) return "Está em combate"
        if (a.domain == Domain.LAND) {
            game.ensureArrays()
            game.reserveAcc[a.province] += a.units[UnitType.INFANTRY.ordinal] * 0.5f
        }
        game.armies.remove(a)
        game.invalidate()
        sim.touch()
        return null
    }

    val mercsCount = 3

    fun mercsCost(n: Nation) = sim.military.unitCost(n, UnitType.INFANTRY) * mercsCount * 2.0

    fun mercsRefusal(n: Nation): String? {
        if (game.day - n.lastMercs < 90) return "Novo contrato em ${90 - (game.day - n.lastMercs)} dias"
        if (sim.unitedNations.has(ResolutionType.ARMS_EMBARGO, n.id)) return "Embargo de armas da ONU"
        if (n.treasury + n.militaryFund < mercsCost(n)) return "Precisa de ${Fmt.money(mercsCost(n))}"
        return null
    }

    fun hireMercs(n: Nation, p: Int): String? {
        mercsRefusal(n)?.let { return it }
        if (game.owner[p] != n.id || game.controller[p] != n.id) return "Precisa ser uma província sua"
        val cost = mercsCost(n)
        val fromFund = min(cost, max(0.0, n.militaryFund))
        n.militaryFund -= fromFund; n.treasury -= cost - fromFund
        val a = sim.military.newArmy(n.id, p, Domain.LAND)
        a.units[UnitType.INFANTRY.ordinal] = mercsCount.toFloat()
        a.morale = 0.8f
        n.lastMercs = game.day
        n.groupMods[Group.MILITARY.ordinal] -= 2f
        if (n.id == game.player) sim.feed("military", "$mercsCount divisões de mercenários chegaram em ${sim.world.provinces[p].name}", 1, p)
        sim.touch()
        return null
    }
}
