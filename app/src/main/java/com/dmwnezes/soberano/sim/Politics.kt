package com.dmwnezes.soberano.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class Politics(private val sim: Sim) {
    private val game get() = sim.game
    val campaigns = Campaigns(sim)

    fun lawGroup(n: Nation, g: Group): Float =
        n.laws.sumOf { (Content.law(it)?.groups?.get(g) ?: 0f).toDouble() }.toFloat()

    fun groupTarget(n: Nation, g: Group): Float {
        val idx = { a: Area -> n.index[a.ordinal] }
        val growth = n.growth
        var t = 50f + when (g) {
            Group.WORKERS -> 0.25f * (idx(Area.SOCIAL) - 50f) - 1.6f * (n.unemployment - 7f) - 60f * (n.tax - 0.32f) +
                0.1f * (idx(Area.SECURITY) - 50f)
            Group.BUSINESS -> 300f * (growth - 0.02f) - 90f * (n.tax - 0.32f) - 0.8f * (n.inflation - 3f) - 0.2f * (n.corruption - 40f)
            Group.MILITARY -> 600f * (n.shares[Area.DEFENSE.ordinal] - 0.02f) + (if (n.regime == Regime.DICTATORSHIP) 8f else 0f) +
                (if (n.militaryFund < 0) -15f else 0f) + militaryWarMood(n)
            Group.RELIGIOUS -> 0.1f * (idx(Area.SECURITY) - 50f) + 0.05f * (idx(Area.SOCIAL) - 50f)
            Group.YOUTH -> 0.25f * (idx(Area.EDUCATION) - 50f) + 0.15f * (idx(Area.ENVIRONMENT) - 50f) - 1.2f * (n.unemployment - 7f)
            Group.RETIREES -> 0.35f * (idx(Area.HEALTH) - 50f) - 1.5f * (n.inflation - 3f) + 0.15f * (idx(Area.SECURITY) - 50f)
        }
        t += lawGroup(n, g) + n.groupMods[g.ordinal] - n.warExhaustion * 20f + sim.dev.builtGroups(n, g)
        return t.coerceIn(0f, 100f)
    }

    private fun militaryWarMood(n: Nation): Float {
        val wars = game.wars.filter { it.involves(n.id) }
        if (wars.isEmpty()) return 0f
        return wars.sumOf { w -> (sim.diplomacy.warScore(w, n.id) / 10f).toDouble() }.toFloat().coerceIn(-15f, 15f)
    }

    fun monthly(n: Nation) {
        if (game.natProvinces(n.id) == 0) return
        for (g in Group.entries) {
            val i = g.ordinal
            n.groups[i] += (groupTarget(n, g) - n.groups[i]) * 0.15f
            n.groupMods[i] *= 0.85f
        }
        val wsum = Group.entries.sumOf { it.weight.toDouble() }.toFloat()
        n.approval = Group.entries.sumOf { (n.groups[it.ordinal] * it.weight).toDouble() }.toFloat() / wsum

        val lawStab = n.laws.sumOf { (Content.law(it)?.stability ?: 0f).toDouble() }.toFloat()
        val occ = sim.economy.occupiedFraction(n.id)
        val stTarget = 0.45f * n.approval + 0.25f * n.index[Area.SECURITY.ordinal] + 22f + lawStab + sim.dev.builtStability(n) -
            n.warExhaustion * 25f - occ * 30f - n.corruption * 0.1f
        n.stability += (stTarget.coerceIn(0f, 100f) - n.stability) * 0.12f

        n.politicalCapital = min(100f, n.politicalCapital + n.approval / 20f + (if (n.regime.decree) 1.5f else 0f))
        if (n.regime.congress && !sim.congress.active(n)) n.congress += ((0.7f * n.approval + 18f) - n.congress) * 0.15f

        // cansaço de guerra
        if (sim.atWarAny(n.id)) {
            val divs = max(1f, sim.armiesOf(n.id).sumOf { it.total().toDouble() }.toFloat())
            n.warExhaustion = (n.warExhaustion + n.casualtiesMonth / divs * 0.6f + occ * 0.12f + 0.008f).coerceIn(0f, 1f)
        } else n.warExhaustion *= 0.85f
        n.casualtiesMonth = 0f

        if (n.id == game.player) campaigns.monthly(n)
        elections(n)
        crises(n)
        n.stability = n.stability.coerceIn(0f, 100f)
        n.politicalCapital = n.politicalCapital.coerceIn(0f, 100f)
    }

    private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

    fun electionChance(n: Nation): Float {
        val boost = if (n.id == game.player) game.campaign.boost else 0f
        return sigmoid((n.approval + boost - 45f) / 7f)
    }

    /** Percentual de votos estimado do governo. */
    fun voteShare(n: Nation): Float {
        val boost = if (n.id == game.player) game.campaign.boost else 0f
        return (50f + (n.approval + boost - 45f) * 0.6f).coerceIn(15f, 85f)
    }

    private fun elections(n: Nation) {
        if (!n.regime.elections || n.nextElection < 0) return
        val isPlayer = n.id == game.player
        if (isPlayer && !game.settings.elections) return
        if (game.day < n.nextElection) return
        n.nextElection = game.day + 365 * 4
        val win = sim.chance(electionChance(n).toDouble())
        if (!isPlayer) return
        // votos coerentes com o resultado (a chance decide, a porcentagem acompanha)
        var share = voteShare(n) + (sim.randf() - 0.5f) * 4f
        share = if (win) share.coerceIn(50.3f, 85f) else share.coerceIn(15f, 49.7f)
        val opp = game.campaign.opponent.ifEmpty { campaigns.opponentName() }
        game.election = ElectionResult(game.day, share, win, opp)
        sim.congress.afterElection(share, win)
        campaigns.reset()
        if (win) {
            sim.feed("vote", "Você foi reeleito com ${Fmt.one(share)}% dos votos contra $opp", 1)
            n.politicalCapital = min(100f, n.politicalCapital + 20f)
        } else if (Defeat.ELECTION in game.settings.defeats) {
            game.gameOver = GameOver(false, "Você perdeu a eleição")
        } else {
            sim.feed("vote", "Oposição venceu, mas você segue no comando por acordo", 2)
            n.politicalCapital = 0f
            n.stability -= 10f
        }
    }

    private fun crises(n: Nation) {
        val isPlayer = n.id == game.player
        val d = game.settings.defeats
        // golpe militar
        if (n.groups[Group.MILITARY.ordinal] < 25f && n.approval < 25f) {
            val p = if (n.regime == Regime.DICTATORSHIP) 0.08 else 0.06
            if (sim.chance(p)) {
                if (isPlayer) {
                    if (Defeat.COUP in d) { game.gameOver = GameOver(false, "Golpe militar: os generais tomaram o poder"); return }
                    sim.feed("tank", "Tentativa de golpe fracassou", 3)
                    n.stability -= 10f; n.groups[Group.MILITARY.ordinal] += 15f
                } else {
                    n.regime = Regime.DICTATORSHIP
                    n.nextElection = -1
                    for (g in Group.entries) n.groups[g.ordinal] = 45f
                    n.stability = 45f
                    sim.feed("tank", "Golpe militar em ${n.name}", 2)
                }
            }
        }
        // revolução
        if (n.stability < 12f && sim.chance(0.05)) {
            if (isPlayer) {
                if (Defeat.REVOLUTION in d) { game.gameOver = GameOver(false, "Revolução: o povo derrubou o governo"); return }
                sim.feed("protest", "Levante popular contido a muito custo", 3)
                n.stability = 25f
                for (g in Group.entries) n.groupMods[g.ordinal] += 10f
            } else {
                n.regime = if (n.regime.decree) Regime.PRESIDENTIAL else Regime.DICTATORSHIP
                n.nextElection = if (n.regime.elections) game.day + 365 else -1
                n.stability = 40f
                n.laws.removeAll { Content.law(it)?.decreeOnly == true && !n.regime.decree }
                sim.feed("protest", "Revolução em ${n.name}: novo governo", 2)
            }
        }
        // falência
        val gdp = game.natGdp(n.id)
        if (gdp > 0 && (gdp >= 3.0 || isPlayer) && n.debt > gdp * 2.5) {
            if (isPlayer && Defeat.BANKRUPTCY in d) { game.gameOver = GameOver(false, "Falência: o país não consegue pagar a dívida"); return }
            n.debt *= 0.5
            for (i in game.owner.indices) if (game.owner[i] == n.id) game.provGdp[i] *= 0.92
            n.stability -= 20f
            for (o in game.nations) if (o.id != n.id) game.addRel(n.id, o.id, -6f)
            sim.feed("chart_down", "${n.name} deu calote na dívida", if (isPlayer) 3 else 2)
        }
    }

    fun checkEndConditions() {
        val p = game.player
        if (game.gameOver != null) return
        if (game.natProvinces(p) == 0) {
            game.gameOver = GameOver(false, "Seu país foi anexado")
            return
        }
        val s = game.settings.victories
        val total = game.owner.size
        val controlled = game.controller.count { it == p }
        val newly = mutableListOf<Victory>()
        if (Victory.ECONOMIC in s && Victory.ECONOMIC !in game.achieved &&
            game.nations.filter { it.alive && it.id != p }.all { game.natGdp(it.id) < game.natGdp(p) }) newly += Victory.ECONOMIC
        if (Victory.HEGEMONY in s && Victory.HEGEMONY !in game.achieved && controlled >= total * 0.25) newly += Victory.HEGEMONY
        if (Victory.GLOBAL in s && Victory.GLOBAL !in game.achieved && game.owner.all { it == p }) newly += Victory.GLOBAL
        if (newly.isNotEmpty()) {
            game.achieved += newly
            game.gameOver = GameOver(true, newly.last().label)
        }
    }

    // ------------------------------------------------------------- leis

    fun lawCost(n: Nation, law: Law, enact: Boolean): Float {
        val base = if (n.regime.decree) 6f else 10f
        val congress = if (n.regime.congress && n.congress < 50f) (50f - n.congress) * 1.5f else 0f
        // leis criadas pelo jogador custam mais quanto mais polêmicas forem
        val controversy = if (law.id.startsWith("custom_")) law.groups.values.sumOf { kotlin.math.abs(it).toDouble() }.toFloat() / 6f else 0f
        return (base + congress + controversy) * (if (enact) 1f else 0.5f)
    }

    fun canEnact(n: Nation, law: Law): String? {
        if (law.decreeOnly && !n.regime.decree) return "Só em ditadura ou monarquia absoluta"
        if (n.politicalCapital < lawCost(n, law, law.id !in n.laws)) return "Capital político insuficiente"
        return null
    }

    /** Motivo da última lei que não passou (para a interface). */
    var lastFail: String? = null

    fun toggleLaw(n: Nation, id: String): Boolean {
        lastFail = null
        val law = Content.law(id) ?: return false
        val enact = id !in n.laws
        canEnact(n, law)?.let { if (enact || it.startsWith("Capital")) return false }
        // com partidos, o Congresso vota a lei (decretos não passam por lá)
        if (enact && sim.congress.active(n) && !n.regime.decree) {
            val (yes, no) = sim.congress.tally(law)
            if (yes <= no) {
                n.politicalCapital -= lawCost(n, law, true) * 0.3f
                lastFail = "Congresso rejeitou: $yes a $no"
                sim.feed("law", "Congresso rejeitou ${law.name} ($yes a $no)", 2)
                sim.touch()
                return false
            }
            sim.congress.afterLaw(law)
            sim.feed("vote", "Congresso aprovou ${law.name} ($yes a $no)", 1)
        }
        n.politicalCapital -= lawCost(n, law, enact)
        if (enact) {
            law.excludes?.let { n.laws.remove(it) }
            n.laws += id
            for (e in law.onEnact) sim.events.apply(n, e, -1)
            sim.feed("law", "Lei aprovada: ${law.name}", 1)
        } else {
            n.laws -= id
            sim.feed("law", "Lei revogada: ${law.name}", 0)
        }
        sim.touch()
        return true
    }

    val regimeChangeCost = 60f

    fun changeRegime(n: Nation, r: Regime): String? {
        if (r == n.regime) return null
        if (n.politicalCapital < regimeChangeCost) return "Precisa de ${regimeChangeCost.toInt()} de capital político"
        if (r == Regime.DICTATORSHIP && n.groups[Group.MILITARY.ordinal] < 45f) return "Os militares não apoiam"
        n.politicalCapital -= regimeChangeCost
        val wasDemocratic = n.regime.democratic
        n.regime = r
        if (r.elections) { if (n.nextElection < game.day) n.nextElection = game.day + 365 * 2 } else n.nextElection = -1
        if (!r.decree) n.laws.removeAll { Content.law(it)?.decreeOnly == true }
        if (wasDemocratic && !r.democratic) {
            n.groupMods[Group.YOUTH.ordinal] -= 20f; n.groupMods[Group.WORKERS.ordinal] -= 8f
            n.groupMods[Group.MILITARY.ordinal] += 10f
            for (o in game.nations) if (o.regime.democratic && o.id != n.id) game.addRel(n.id, o.id, -15f)
        } else if (!wasDemocratic && r.democratic) {
            n.groupMods[Group.YOUTH.ordinal] += 15f; n.groupMods[Group.MILITARY.ordinal] -= 10f
            for (o in game.nations) if (o.regime.democratic && o.id != n.id) game.addRel(n.id, o.id, 10f)
        }
        sim.feed("flag", "Novo regime: ${r.label}", 1)
        sim.touch()
        return null
    }
}

/** Campanha eleitoral do jogador nos 6 meses antes da eleição, e as promessas feitas nela. */
class Campaigns(private val sim: Sim) {
    private val game get() = sim.game
    private val c get() = game.campaign

    fun active(n: Nation = sim.player): Boolean =
        n.id == game.player && n.regime.elections && game.settings.elections && n.nextElection > game.day && n.nextElection - game.day <= 180

    fun daysLeft(n: Nation = sim.player) = (n.nextElection - game.day).coerceAtLeast(0)

    private val first = listOf("Ricardo", "Marta", "Fernando", "Luíza", "Antônio", "Cláudia", "Roberto", "Sônia", "Henrique", "Teresa", "Márcio", "Regina")
    private val last = listOf("Valente", "Correia", "Andrade", "Fontes", "Barreto", "Moraes", "Peixoto", "Sales", "Rangel", "Campos")
    fun opponentName() = "${sim.pick(first)} ${sim.pick(last)}"

    fun rallyCost(n: Nation) = game.natGdp(n.id) * 0.0004
    fun adsCost(n: Nation) = game.natGdp(n.id) * 0.0010

    fun monthly(n: Nation) {
        if (active(n) && c.opponent.isEmpty()) {
            c.opponent = opponentName()
            sim.feed("vote", "Campanha aberta: ${c.opponent} é o candidato da oposição", 1)
        }
        // a empolgação da campanha esfria aos poucos
        if (c.boost > 0f) c.boost *= 0.92f
        checkPromises(n)
    }

    fun rally(g: Group): String? {
        val n = sim.player
        if (!active(n)) return "A campanha começa 6 meses antes da eleição"
        if (game.day - c.lastRallyDay < 20) return "Próximo comício em ${20 - (game.day - c.lastRallyDay)} dias"
        val cost = rallyCost(n)
        if (n.treasury < cost) return "Tesouro insuficiente"
        n.treasury -= cost
        c.lastRallyDay = game.day; c.rallies++
        n.groupMods[g.ordinal] += 4f
        c.boost += 1.5f
        sim.feed("people", "Comício para ${g.label.lowercase()} lotou a praça", 1)
        sim.touch(); return null
    }

    fun ads(): String? {
        val n = sim.player
        if (!active(n)) return "A campanha começa 6 meses antes da eleição"
        if (c.adsDone >= 3) return "Limite de 3 campanhas na TV"
        val cost = adsCost(n)
        if (n.treasury < cost) return "Tesouro insuficiente"
        n.treasury -= cost
        c.adsDone++
        c.boost += 2f
        for (g in Group.entries) n.groupMods[g.ordinal] += 1f
        sim.touch(); return null
    }

    /** Debate na TV: arriscado. Retorna o texto do resultado. */
    fun debate(): String {
        val n = sim.player
        if (!active(n)) return "A campanha começa 6 meses antes da eleição"
        if (c.debateDone) return "O debate já aconteceu"
        c.debateDone = true
        val chance = 0.35 + n.approval / 200.0 + n.index[Area.EDUCATION.ordinal] / 500.0
        val won = sim.chance(chance)
        return if (won) {
            c.boost += 4f
            sim.feed("victory", "Você venceu o debate contra ${c.opponent}", 1)
            "Você venceu o debate: +4 na intenção de voto"
        } else {
            c.boost -= 3f
            sim.feed("defeat", "${c.opponent} levou a melhor no debate", 2)
            "Você perdeu o debate: −3 na intenção de voto"
        }.also { sim.touch() }
    }

    fun promise(p: Promise): String? {
        val n = sim.player
        if (!active(n)) return "Promessas só durante a campanha"
        if (c.promises.count { !it.resolved } >= 3) return "No máximo 3 promessas"
        if (c.promises.any { it.promise == p && !it.resolved }) return "Promessa já feita"
        c.promises += PromiseMade(p, game.day, metric(n, p))
        n.groupMods[p.group.ordinal] += 10f
        c.boost += 1f
        sim.feed("vote", "Promessa de campanha: ${p.label}", 1)
        sim.touch(); return null
    }

    fun metric(n: Nation, p: Promise): Float = when (p) {
        Promise.HEALTH -> n.index[Area.HEALTH.ordinal]
        Promise.JOBS -> n.unemployment
        Promise.EDUCATION -> n.index[Area.EDUCATION.ordinal]
        Promise.NO_TAX -> n.tax
        Promise.ARMY -> n.shares[Area.DEFENSE.ordinal]
        Promise.ORDER -> n.index[Area.SECURITY.ordinal]
    }

    fun kept(n: Nation, pm: PromiseMade): Boolean {
        val now = metric(n, pm.promise)
        return when (pm.promise) {
            Promise.HEALTH, Promise.EDUCATION, Promise.ORDER -> now >= pm.baseline + 3f
            Promise.JOBS -> now <= pm.baseline - 1f
            Promise.NO_TAX -> now <= pm.baseline + 0.005f
            Promise.ARMY -> now >= pm.baseline - 0.0005f
        }
    }

    /** Um ano depois de prometer, o povo cobra. */
    private fun checkPromises(n: Nation) {
        for (pm in c.promises) {
            if (pm.resolved || game.day - pm.day < 365) continue
            pm.resolved = true
            if (kept(n, pm)) {
                n.groupMods[pm.promise.group.ordinal] += 6f
                sim.feed("victory", "Promessa cumprida: ${pm.promise.label}", 1)
            } else {
                n.groupMods[pm.promise.group.ordinal] -= 12f
                n.stability -= 3f
                sim.feed("defeat", "Promessa quebrada: ${pm.promise.label}", 3)
            }
        }
        c.promises.removeAll { it.resolved && game.day - it.day > 400 }
    }

    fun reset() {
        c.rallies = 0; c.lastRallyDay = -999; c.debateDone = false; c.adsDone = 0; c.boost = 0f; c.opponent = ""
    }
}
