package com.dmwnezes.soberano.sim

import kotlin.math.max
import kotlin.math.min

/** Decisões dos países controlados pelo computador. */
class Ai(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    fun daily() {
        val day = game.day
        for (n in game.nations) {
            if (!n.alive || n.id == game.player) continue
            if (game.natProvinces(n.id) == 0) continue
            if (n.id % 30 == day % 30) strategic(n)
            if (sim.atWarAny(n.id) && (n.id + day) % 2 == 0) warOrders(n)
        }
    }

    private fun strategic(n: Nation) {
        economy(n)
        build(n)
        diplomacy(n)
        considerWar(n)
        considerPeace(n)
    }

    // ------------------------------------------------------------- economia

    private fun economy(n: Nation) {
        val b = sim.economy.budget(n)
        if (b.gdp <= 0) return
        val ratio = n.debt / b.gdp
        val monthly = b.gdp / 12.0
        if (b.balance < 0 && (ratio > 0.6 || n.treasury < monthly * 0.2)) {
            if (n.tax < 0.36f) sim.economy.setTax(n, n.tax + 0.01f)
            else for (a in listOf(Area.SOCIAL, Area.ENVIRONMENT, Area.INFRA)) n.shares[a.ordinal] *= 0.95f
        } else if (b.balance > monthly * 0.02 && ratio < 0.5) {
            if (n.approval < 45f && n.tax > 0.15f) sim.economy.setTax(n, n.tax - 0.005f)
            else {
                val weakest = Area.entries.filter { it.hasIndex }.minBy { n.index[it.ordinal] }
                n.shares[weakest.ordinal] = min(0.12f, n.shares[weakest.ordinal] * 1.05f + 0.001f)
            }
        }
        // defesa conforme ameaça
        val def = Area.DEFENSE.ordinal
        val threat = threatLevel(n)
        val wanted = when {
            sim.atWarAny(n.id) -> 0.06f
            threat > 1.5f -> 0.035f
            threat > 0.8f -> 0.025f
            else -> 0.015f
        } * (0.7f + n.personality.aggression * 0.4f)
        n.shares[def] += ((wanted - n.shares[def]) * 0.25f)
        // paga dívida com sobra de caixa
        if (n.treasury > monthly * 2) sim.economy.repayDebt(n, (n.treasury - monthly) * 0.5)
        if (n.militaryFund > monthly * 6 && !sim.atWarAny(n.id)) {
            val extra = n.militaryFund - monthly * 6
            n.militaryFund -= extra; n.treasury += extra
        }
    }

    /** Poder dos vizinhos hostis ÷ meu poder. */
    private fun threatLevel(n: Nation): Float {
        val mine = max(1.0, sim.landPower(n.id))
        var worst = 0.0
        for (o in neighborsOf(n.id)) {
            if (game.rel(n.id, o) < -40f) worst = max(worst, sim.landPower(o) / mine)
        }
        return worst.toFloat()
    }

    private val neighborCache = HashMap<Int, Pair<Int, Set<Int>>>()

    fun neighborsOf(id: Int): Set<Int> {
        val c = neighborCache[id]
        if (c != null && c.first == game.day / 30) return c.second
        val out = HashSet<Int>()
        for (p in world.provinces) if (game.owner[p.id] == id) for (q in p.neighbors) {
            val o = game.owner[q]
            if (o != id) out += o
        }
        neighborCache[id] = game.day / 30 to out
        return out
    }

    // ------------------------------------------------------------- produção

    private fun build(n: Nation) {
        val gdp = game.natGdp(n.id)
        val boost = Content.militaryBoost[n.code] ?: 1f
        val desired = gdp * n.shares[Area.DEFENSE.ordinal] * 2.0 * boost * (if (sim.atWarAny(n.id)) 1.5 else 1.0)
        val current = sim.armiesOf(n.id).sumOf { it.value() } + n.queue.sumOf { it.type.cost }
        if (current >= desired) return
        var budget = min(n.militaryFund * 0.7, desired - current)
        if (budget < UnitType.INFANTRY.cost) return
        val provinces = sim.provincesOf(n.id).filter { game.controller[it] == n.id }
        if (provinces.isEmpty()) return
        val front = provinces.filter { p -> world.provinces[p].neighbors.any { q -> sim.atWar(n.id, game.controller[q]) } }
        val spot = (front.maxByOrNull { game.provPop[it] }) ?: n.capital.takeIf { game.controller[it] == n.id } ?: provinces.first()
        val coastal = provinces.filter { world.provinces[it].coastal }
        val navy = sim.armiesOf(n.id).filter { it.domain == Domain.SEA }.sumOf { it.value() }
        val air = sim.armiesOf(n.id).filter { it.domain == Domain.AIR }.sumOf { it.value() }
        var guard = 0
        while (budget > 0 && guard++ < 40 && n.queue.size < 60) {
            val roll = sim.randf()
            val type = when {
                coastal.isNotEmpty() && navy < current * 0.2 && roll < 0.15f -> if (sim.randf() < 0.7f) UnitType.DESTROYER else UnitType.SUBMARINE
                air < current * 0.2 && roll < 0.3f -> if (sim.randf() < 0.6f) UnitType.FIGHTER else UnitType.DRONE
                roll < 0.6f -> UnitType.INFANTRY
                roll < 0.8f -> UnitType.ARMOR
                roll < 0.92f -> UnitType.ARTILLERY
                else -> UnitType.ANTIAIR
            }
            val cost = sim.military.unitCost(n, type)
            if (cost > budget) { if (type == UnitType.INFANTRY) break else continue }
            val where = if (type.domain == Domain.SEA) coastal.first() else spot
            if (sim.military.buy(n, type, where) == null) budget -= cost else break
        }
        // junta exércitos parados no mesmo lugar
        for (a in sim.armiesOf(n.id)) if (game.armies.contains(a) && !a.moving && !a.battle && a.divisions() < 30f) {
            val other = game.armies.firstOrNull { it !== a && it.owner == n.id && it.province == a.province && it.domain == a.domain && !it.moving && !it.battle }
            if (other != null) sim.military.merge(a, other)
        }
    }

    // ------------------------------------------------------------- diplomacia

    private fun diplomacy(n: Nation) {
        val candidates = (neighborsOf(n.id) + List(3) { sim.randInt(game.n) }).filter { it != n.id && game.nations[it].alive }
        for (c in candidates.shuffledBy()) {
            if (sim.atWar(n.id, c)) continue
            val r = game.rel(n.id, c)
            val type = when {
                r >= 55f && !sim.hasTreaty(TreatyType.ALLIANCE, n.id, c) && sim.randf() < 0.15f -> TreatyType.ALLIANCE
                r >= 15f && !sim.hasTreaty(TreatyType.TRADE, n.id, c) -> TreatyType.TRADE
                r in 0f..30f && !sim.hasTreaty(TreatyType.NON_AGGRESSION, n.id, c) && sim.randf() < 0.3f -> TreatyType.NON_AGGRESSION
                else -> null
            } ?: continue
            if (sim.randf() > 0.35f * n.personality.trade) continue
            if (sim.diplomacy.refusal(type, n.id, c) != null) continue
            if (c == game.player) {
                if (game.proposals.none { it.from == n.id } && game.proposals.size < 3)
                    game.proposals += Proposal(n.id, type.name, game.day)
            } else sim.diplomacy.sign(type, n.id, c)
            break
        }
        // relação ruim desfaz aliança
        for (t in game.treaties.filter { it.type == TreatyType.ALLIANCE && (it.a == n.id || it.b == n.id) }) {
            val o = if (t.a == n.id) t.b else t.a
            if (game.rel(n.id, o) < 10f && o != game.player) sim.diplomacy.cancel(TreatyType.ALLIANCE, n.id, o)
        }
        // ódio vira sanção
        for (o in neighborsOf(n.id)) if (game.rel(n.id, o) < -70f && sim.randf() < 0.05f &&
            game.treaties.none { it.type == TreatyType.SANCTION && it.a == n.id && it.b == o }) sim.diplomacy.sanction(n.id, o, false)
    }

    private fun <T> Collection<T>.shuffledBy(): List<T> {
        val l = toMutableList()
        for (i in l.indices.reversed()) { val j = sim.randInt(i + 1); val t = l[i]; l[i] = l[j]; l[j] = t }
        return l
    }

    // ------------------------------------------------------------- guerra e paz

    private fun considerWar(n: Nation) {
        val s = game.settings
        if (s.aiAggression <= 0f || sim.atWarAny(n.id)) return
        if (game.day < 90) return
        val aggr = n.personality.aggression * s.aiAggression * s.difficulty.ai
        if (aggr < 0.2f) return
        val my = sim.landPower(n.id)
        if (my < 10) return
        for (t in neighborsOf(n.id)) {
            val tn = game.nations[t]
            if (!tn.alive || sim.allied(n.id, t)) continue
            val r = game.rel(n.id, t)
            val limit = when (n.personality) { Personality.EXPANSIONIST -> -35f; Personality.OPPORTUNIST -> -42f; else -> -55f }
            if (r > limit) continue
            if (sim.hasTreaty(TreatyType.NON_AGGRESSION, n.id, t) && n.personality != Personality.EXPANSIONIST) continue
            if (t == game.player && game.day < 180) continue
            var theirs = sim.landPower(t)
            for (o in game.nations) if (o.alive && o.id != t && sim.allied(o.id, t)) theirs += sim.landPower(o.id) * 0.5
            if (my < theirs * 1.5 / min(1.5f, aggr)) continue
            if (sim.chance(0.12 * aggr)) {
                sim.diplomacy.declareWar(n.id, t)
                return
            }
        }
    }

    private fun considerPeace(n: Nation) {
        for (w in game.wars.toList()) {
            if (!w.involves(n.id)) continue
            val leader = if (w.side(n.id) == 1) w.leaderA() else w.leaderD()
            if (leader != n.id) continue
            val enemyLeader = if (w.side(n.id) == 1) w.leaderD() else w.leaderA()
            val score = sim.diplomacy.warScore(w, n.id)
            val enemy = game.nations[enemyLeader]
            val long = game.day - w.start > 365 * 2
            if (enemyLeader == game.player) {
                // propõe ao jogador
                if ((score < -25f || n.warExhaustion > 0.6f) && game.proposals.none { it.from == n.id && it.kind.startsWith("PEACE") })
                    game.proposals += Proposal(n.id, "PEACE_WHITE", game.day, w.id.toDouble())
                continue
            }
            val terms = when {
                score >= 50f && sim.diplomacy.capitulated(enemyLeader) -> PeaceTerms.ANNEX
                score >= 25f && (enemy.warExhaustion > 0.4f || long) -> PeaceTerms.TAKE_OCCUPIED
                n.warExhaustion > 0.65f || enemy.warExhaustion > 0.65f || long -> PeaceTerms.WHITE
                else -> null
            } ?: continue
            val winner = if (score >= 0) n.id else enemyLeader
            sim.diplomacy.makePeace(w, winner, if (score < 0 && terms != PeaceTerms.WHITE) PeaceTerms.WHITE else terms)
        }
    }

    // ------------------------------------------------------------- ordens de guerra

    private var enemyPower = HashMap<Int, Float>()

    private fun warOrders(n: Nation) {
        val armies = sim.armiesOf(n.id)
        enemyPower = HashMap()
        for (a in game.armies) if (a.domain == Domain.LAND && sim.atWar(n.id, a.owner))
            enemyPower[a.province] = (enemyPower[a.province] ?: 0f) + armyPower(a) / a.morale.coerceAtLeast(0.1f)
        for (a in armies) {
            if (a.moving || a.battle || !a.alive()) continue
            when (a.domain) {
                Domain.LAND -> landOrders(n, a)
                Domain.SEA -> seaOrders(n, a)
                Domain.AIR -> airOrders(n, a)
            }
        }
    }

    private fun enemyPowerAt(@Suppress("UNUSED_PARAMETER") n: Int, p: Int): Float = enemyPower[p] ?: 0f

    private fun armyPower(a: Army): Float = UnitType.of(Domain.LAND).sumOf { (a.units[it.ordinal] * (it.attack + it.defense)).toDouble() }.toFloat() * a.morale

    private fun landOrders(n: Nation, a: Army) {
        val here = a.province
        // sitiando: fica até tomar
        if (sim.atWar(n.id, game.controller[here])) return
        if (a.morale < 0.5f) return
        val mine = armyPower(a)
        // busca em largura até 10 passos
        val depth = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        depth[here] = 0; queue += here
        var best = -1; var bestScore = -Float.MAX_VALUE
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val d = depth[u]!!
            if (d >= 10) continue
            for (v in world.provinces[u].neighbors) {
                if (depth.containsKey(v)) continue
                if (!sim.canEnter(n.id, v)) continue
                depth[v] = d + 1
                val ctrl = game.controller[v]
                val enemyHere = enemyPowerAt(n.id, v)
                if (sim.atWar(n.id, ctrl) || enemyHere > 0f) {
                    val isCap = sim.nation(ctrl).capital == v
                    val mineOwned = game.owner[v] == n.id
                    var score = (game.provPop[v] / 1e6).toFloat().coerceAtMost(20f) + (if (isCap) 25f else 0f) + (if (mineOwned) 15f else 0f)
                    score -= (d + 1) * 1.2f
                    if (enemyHere > mine * 1.1f) score -= 40f else if (enemyHere > 0f) score += 5f
                    if (score > bestScore) { bestScore = score; best = v }
                    continue // não atravessa linhas inimigas na busca
                }
                queue += v
            }
        }
        if (best >= 0 && bestScore > -30f) {
            sim.military.order(a, best)
            return
        }
        // sem alvo por terra: desembarque
        if (sim.military.transportCapacity(n.id) >= a.divisions() && sim.randf() < 0.3f) {
            val targets = world.coastalIds.filter { sim.atWar(n.id, game.controller[it]) && enemyPowerAt(n.id, it) < mine }
            val t = targets.minByOrNull { world.distanceKm(here, it) } ?: return
            val path = sim.military.landPath(n.id, here, t, true) ?: return
            a.path = path; a.progress = 0f; a.legKm = sim.military.legKm(here, path[0])
        }
    }

    private fun seaOrders(n: Nation, a: Army) {
        if (a.morale < 0.6f) return
        val myPower = sim.military.navalPower(a)
        val enemyFleets = game.armies.filter { it.domain == Domain.SEA && sim.atWar(n.id, it.owner) }
        val target = enemyFleets.filter { sim.military.navalPower(it) < myPower * 0.8f }
            .minByOrNull { world.distanceKm(a.province, it.province) }
        if (target != null && world.distanceKm(a.province, target.province) < 4000) {
            if (sim.canEnter(n.id, target.province)) sim.military.order(a, target.province)
        }
    }

    private fun airOrders(n: Nation, a: Army) {
        val battle = game.battles.filter { it.attacker == n.id || it.defender == n.id }
            .minByOrNull { world.distanceKm(a.province, it.province) } ?: return
        val km = world.distanceKm(a.province, battle.province)
        if (km < 900) return
        val base = sim.provincesOf(n.id).filter { game.controller[it] == n.id }
            .minByOrNull { world.distanceKm(it, battle.province) } ?: return
        if (base != a.province) sim.military.order(a, base)
    }
}
