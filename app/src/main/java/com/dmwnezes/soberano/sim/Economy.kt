package com.dmwnezes.soberano.sim

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Resumo do mês para a interface. */
class Budget(
    val gdp: Double,
    val revenue: Double,
    val civil: Double,
    val defense: Double,
    val interest: Double,
    val upkeep: Double,
) {
    val balance get() = revenue - civil - defense - interest
}

class Economy(private val sim: Sim) {
    private val game get() = sim.game

    private val BASE_GROWTH = floatArrayOf(0.04f, 0.035f, 0.025f, 0.018f, 0.012f)
    private val POP_GROWTH = floatArrayOf(0.025f, 0.02f, 0.012f, 0.008f, 0.004f)

    fun upkeep(id: Int): Double = game.armies.filter { it.owner == id }.sumOf { a ->
        UnitType.entries.sumOf { t -> a.units[t.ordinal] * t.upkeep }
    } + sim.nation(id).nukes * NUKE_COST * 0.01

    fun lawSum(n: Nation, f: (Law) -> Float): Float = n.laws.sumOf { (Content.law(it)?.let(f) ?: 0f).toDouble() }.toFloat()

    fun budget(n: Nation): Budget {
        val gdp = game.natGdp(n.id)
        val diff = if (n.id == game.player) game.settings.difficulty.economy else 1f
        val revenue = gdp * n.tax / 12.0 * (1.0 - n.corruption / 100.0 * 0.15) * (1.0 + lawSum(n) { it.revenue }) * diff
        var civil = 0.0
        for (a in Area.entries) if (a != Area.DEFENSE) civil += gdp * n.shares[a.ordinal] / 12.0
        civil *= sim.policy.civilMult(n)
        val defense = gdp * n.shares[Area.DEFENSE.ordinal] / 12.0
        val ratio = if (gdp > 0) min(n.debt / gdp, 2.5) else 0.0
        val interest = n.debt * (0.008 + 0.012 * ratio) / 12.0 * sim.policy.interestMult(n) * sim.currency.interestMult(n)
        return Budget(gdp, revenue, civil, defense, interest, upkeep(n.id))
    }

    fun tradeCount(id: Int) = game.treaties.count { it.type == TreatyType.TRADE && (it.a == id || it.b == id) }
    fun sanctionsOn(id: Int) = game.treaties.count { (it.type == TreatyType.SANCTION || it.type == TreatyType.EMBARGO) && it.b == id }

    /**
     * Quanto as sanções tiram do crescimento anual. Depende do peso econômico de quem sanciona
     * (sanção de micro-países quase não pesa), tem teto e diminui com o tempo, porque a economia se adapta.
     */
    fun sanctionPenalty(id: Int): Float {
        var world = 0.0
        for (n in game.nations) if (n.alive && n.id != id) world += game.natGdp(n.id)
        if (world <= 0.0) return 0f
        var w = 0.0
        for (t in game.treaties) {
            if (t.b != id || (t.type != TreatyType.SANCTION && t.type != TreatyType.EMBARGO)) continue
            val years = (game.day - t.start) / 365f
            val adapt = (1f - years * 0.25f).coerceIn(0.4f, 1f)
            val k = if (t.type == TreatyType.EMBARGO) 1.6 else 1.0
            w += game.natGdp(t.a) / world * k * adapt
        }
        return (0.05 * min(1.0, w)).toFloat().coerceAtMost(0.04f)
    }

    fun occupiedFraction(id: Int): Float {
        val own = game.natProvinces(id)
        return if (own == 0) 0f else game.natOccupied(id).toFloat() / own
    }

    /** Crescimento anual previsto (também mostrado na interface). */
    fun growthRate(n: Nation): Float {
        val t = n.income.coerceIn(1, 5) - 1
        var g = BASE_GROWTH[t]
        g += 0.015f * (n.index[Area.INFRA.ordinal] - 65f) / 50f
        g += 0.010f * (n.index[Area.EDUCATION.ordinal] - 65f) / 50f
        g += 0.010f * (n.stability - 60f) / 50f
        g -= 0.4f * max(0f, n.tax - 0.33f)
        g += 0.0007f * min(8, tradeCount(n.id))
        g -= sanctionPenalty(n.id)
        if (sim.atWarAny(n.id)) g -= 0.01f
        g -= occupiedFraction(n.id) * 0.2f
        if (game.settings.economy != EconomyDepth.SIMPLE) g -= min(0.03f, max(0f, n.inflation - 6f) * 0.002f)
        g += lawSum(n) { it.growth }
        g += sim.policy.growthEffect(n)
        g += sim.trade.growthEffect(n) + sim.currency.growthEffect(n) + sim.buildings.tradeBonus(n)
        g += sim.dev.tradeGrowth(n) + sim.dev.builtGrowth(n)
        if (sim.unitedNations.inClimateDeal(n.id)) g -= 0.002f
        if (sim.unitedNations.has(ResolutionType.RECONSTRUCTION, n.id)) g += 0.01f
        if (n.id == game.player) g += sim.dev.economyBonus()
        g += (n.tech[TechBranch.ECONOMY.ordinal] - 3) * 0.001f
        if (n.id == game.player) g += (game.settings.difficulty.economy - 1f) * 0.02f
        return g.coerceIn(-0.15f, 0.12f)
    }

    fun indexTarget(n: Nation, a: Area): Float {
        if (!a.hasIndex) return 0f
        var t = Setup.target(a, n.shares[a.ordinal])
        t += n.laws.sumOf { (Content.law(it)?.index?.get(a) ?: 0f).toDouble() }.toFloat()
        t += (n.tech[TechBranch.SOCIETY.ordinal] - 3) * 1f
        t += sim.dev.builtIndex(n, a)
        t += sim.buildings.indexBonus(n, a)
        if (a == Area.ENVIRONMENT && sim.unitedNations.inClimateDeal(n.id)) t += 5f
        if (a == Area.SECURITY) t -= occupiedFraction(n.id) * 30f
        return t.coerceIn(0f, 100f)
    }

    fun monthly(n: Nation) {
        if (game.natProvinces(n.id) == 0) return
        val b = budget(n)
        if (b.gdp <= 0) return
        val balance = b.balance
        n.treasury += balance
        // microestados do computador não se endividam (evita calotes em série)
        val micro = n.id != game.player && b.gdp < 3.0
        if (n.treasury < 0) { if (!micro) n.debt += -n.treasury; n.treasury = 0.0 }
        n.lastRevenue = b.revenue
        n.lastExpense = b.civil + b.defense + b.interest
        n.militaryFund += b.defense - b.upkeep
        if (n.militaryFund < 0) {
            // sem verba: moral cai e a dívida militar vira dívida pública
            n.debt += -n.militaryFund * 0.5
            n.militaryFund *= 0.5
            for (a in game.armies) if (a.owner == n.id) a.morale = max(0.3f, a.morale - 0.06f)
        }

        // índices
        val eff = 1f - n.corruption / 250f
        for (a in Area.entries) if (a.hasIndex) {
            val i = a.ordinal
            val mult = if (n.id == game.player) sim.dev.ministerMult(a) else 1f
            n.index[i] += (indexTarget(n, a) - n.index[i]) * 0.08f * eff * mult
            n.index[i] = n.index[i].coerceIn(0f, 100f)
        }
        val corrBase = (60f - n.income * 8f) + (if (n.regime == Regime.DICTATORSHIP) 12f else 0f) + lawSum(n) { it.corruption } +
            (if (n.id == game.player) sim.dev.cabinetCorruption() else 0f)
        n.corruption += (corrBase - n.corruption) * 0.03f
        n.corruption = n.corruption.coerceIn(0f, 100f)

        // crescimento e população
        val g = growthRate(n)
        n.growth = g
        val mg = (1.0 + g).pow(1.0 / 12.0)
        val t = n.income.coerceIn(1, 5) - 1
        val pr = POP_GROWTH[t] * (0.6f + n.index[Area.HEALTH.ordinal] / 125f)
        val mp = (1.0 + pr).pow(1.0 / 12.0)
        for (i in game.owner.indices) if (game.owner[i] == n.id) {
            if (game.controller[i] == n.id) game.provGdp[i] *= mg
            game.provPop[i] *= mp
        }

        // inflação e desemprego
        val deficit = max(0.0, -balance * 12.0) / b.gdp
        val inflTarget = if (game.settings.economy == EconomyDepth.SIMPLE) 3f
        else (3f + 25f * deficit.toFloat() + lawSum(n) { it.inflation } + sim.policy.inflationEffect(n) + sim.currency.inflationEffect(n)).coerceIn(0f, 80f)
        n.inflation += (inflTarget - n.inflation) * 0.2f
        val uTarget = 7f - (g * 100f - 2f) * 1.2f + (if (sim.atWarAny(n.id)) 1.5f else 0f)
        n.unemployment += (uTarget.coerceIn(1.5f, 40f) - n.unemployment) * 0.2f

        // renda sobe ou desce de faixa conforme PIB per capita
        val pc = b.gdp * 1e9 / max(1.0, game.natPop(n.id))
        n.income = when { pc > 30000 -> 5; pc > 13000 -> 4; pc > 4500 -> 3; pc > 1200 -> 2; else -> 1 }.let {
            // muda devagar: no máximo um degrau por mês
            if (it > n.income) n.income + 1 else if (it < n.income) n.income - 1 else n.income
        }

        research(n, b.gdp)
    }

    fun techCost(level: Int): Float = 10f * level.toFloat().pow(1.5f)

    private fun research(n: Nation, gdp: Double) {
        val pts = (2f + n.index[Area.RESEARCH.ordinal] / 20f + log10(gdp + 1).toFloat() * 1.5f + sim.dev.builtResearch(n) + sim.space.researchBonus(n) + sim.buildings.researchBonus(n)) *
            (0.6f + n.index[Area.EDUCATION.ordinal] / 125f)
        for (b in TechBranch.entries) {
            val share = if (b.ordinal == n.researchFocus) 0.6f else 0.08f
            addTech(n, b, pts * share)
        }
    }

    fun addTech(n: Nation, b: TechBranch, pts: Float) {
        val i = b.ordinal
        if (n.tech[i] >= 10) return
        n.techProgress[i] += pts
        val need = techCost(n.tech[i] + 1)
        if (n.techProgress[i] >= need) {
            n.techProgress[i] -= need
            n.tech[i]++
            if (n.id == game.player) sim.feed("atom", "${b.label} chegou ao nível ${n.tech[i]}", 1)
        }
    }

    // ------------------------------------------------------------- ações do jogador

    fun setTax(n: Nation, value: Float) {
        val v = value.coerceIn(0.05f, 0.65f)
        val d = v - n.tax
        if (d > 0.005f) {
            n.groupMods[Group.WORKERS.ordinal] -= d * 80f
            n.groupMods[Group.BUSINESS.ordinal] -= d * 120f
            n.groupMods[Group.RETIREES.ordinal] -= d * 40f
        } else if (d < -0.005f) {
            n.groupMods[Group.WORKERS.ordinal] -= d * 40f
            n.groupMods[Group.BUSINESS.ordinal] -= d * 60f
        }
        n.tax = v
        sim.touch()
    }

    fun setShare(n: Nation, a: Area, value: Float) {
        n.shares[a.ordinal] = value.coerceIn(0f, if (a == Area.DEFENSE) 0.25f else 0.15f)
        sim.touch()
    }

    fun repayDebt(n: Nation, amount: Double) {
        val v = min(amount, min(n.treasury, n.debt))
        n.treasury -= v; n.debt -= v
        sim.touch()
    }

    fun borrow(n: Nation, amount: Double) {
        n.treasury += amount; n.debt += amount
        sim.touch()
    }

    fun transferToMilitary(n: Nation, amount: Double) {
        val v = min(amount, n.treasury)
        n.treasury -= v; n.militaryFund += v
        sim.touch()
    }
}
