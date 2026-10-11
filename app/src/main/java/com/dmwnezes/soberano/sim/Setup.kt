package com.dmwnezes.soberano.sim

import com.dmwnezes.soberano.world.WorldMap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** País criado pelo jogador: assume o território de [territory]. */
data class CustomNation(val name: String, val flag: Flag, val color: Long, val territory: String)

object Setup {
    private val SHARES = arrayOf(
        floatArrayOf(0.015f, 0.030f, 0.012f, 0.020f, 0.010f, 0.002f, 0.002f, 0.018f),
        floatArrayOf(0.025f, 0.035f, 0.016f, 0.025f, 0.020f, 0.003f, 0.004f, 0.015f),
        floatArrayOf(0.045f, 0.045f, 0.020f, 0.030f, 0.035f, 0.004f, 0.008f, 0.016f),
        floatArrayOf(0.040f, 0.040f, 0.022f, 0.030f, 0.030f, 0.004f, 0.010f, 0.030f),
        floatArrayOf(0.075f, 0.050f, 0.018f, 0.025f, 0.060f, 0.006f, 0.022f, 0.017f),
    )
    private val TAX = floatArrayOf(0.14f, 0.17f, 0.23f, 0.25f, 0.36f)
    private val DEBT = floatArrayOf(0.45f, 0.5f, 0.6f, 0.4f, 0.8f)
    private val BASE_IDX = floatArrayOf(35f, 45f, 55f, 70f, 80f)
    private val CORRUPTION = floatArrayOf(65f, 55f, 45f, 35f, 20f)
    private val DEBT_OVERRIDE = mapOf("USA" to 1.2f, "JPN" to 1.4f, "ITA" to 1.4f, "GRC" to 1.6f, "BRA" to 0.85f,
        "ARG" to 0.9f, "FRA" to 1.1f, "GBR" to 1.0f, "CAN" to 1.0f, "ESP" to 1.1f, "PRT" to 1.1f, "BEL" to 1.05f,
        "CHN" to 0.8f, "IND" to 0.85f, "RUS" to 0.2f, "SAU" to 0.25f, "DEU" to 0.65f)
    private val DEFENSE_OVERRIDE = mapOf("USA" to 0.034f, "RUS" to 0.055f, "ISR" to 0.05f, "SAU" to 0.06f,
        "CHN" to 0.017f, "IND" to 0.024f, "PAK" to 0.035f, "UKR" to 0.15f, "PRK" to 0.2f, "KOR" to 0.027f,
        "IRN" to 0.03f, "TUR" to 0.02f, "POL" to 0.03f, "GRC" to 0.03f, "DZA" to 0.05f, "EGY" to 0.012f,
        "TWN" to 0.025f, "FRA" to 0.02f, "GBR" to 0.022f, "ARM" to 0.045f, "AZE" to 0.045f, "OMN" to 0.08f)

    fun target(area: Area, share: Float): Float =
        if (!area.hasIndex) 0f else (100f * (1f - exp(-share / area.k))).coerceIn(0f, 100f)

    fun newGame(
        world: WorldMap,
        settings: Settings,
        playerCode: String,
        title: Title,
        custom: CustomNation? = null,
    ): Game {
        val nc = world.countries.size
        val np = world.provinces.size
        var seed = settings.seed xor 0x5DEECE66DL
        if (seed == 0L) seed = 1L
        fun r(): Float {
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 7); seed = seed xor (seed shl 17)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
        }

        val nations = world.countries.map { c ->
            val tier = c.income.coerceIn(1, 5)
            val t = tier - 1
            val shares = SHARES[t].copyOf()
            DEFENSE_OVERRIDE[c.code]?.let { shares[Area.DEFENSE.ordinal] = it }
            val n = Nation(
                id = c.index, code = c.code, name = c.name,
                mapColor = PALETTE[(c.mapColor + c.index * 3) % PALETTE.size],
                income = tier, capital = c.capital,
                tax = (TAX[t] + (r() - 0.5f) * 0.04f).coerceIn(0.1f, 0.55f),
                shares = shares,
            )
            n.regime = when (c.code) {
                in Content.absoluteMonarchies -> Regime.ABS_MONARCHY
                in Content.constMonarchies -> Regime.CONST_MONARCHY
                in Content.dictatorships -> Regime.DICTATORSHIP
                in Content.parliamentary -> Regime.PARLIAMENTARY
                else -> Regime.PRESIDENTIAL
            }
            n.personality = when (c.code) {
                in Content.expansionist -> Personality.EXPANSIONIST
                in Content.opportunist -> Personality.OPPORTUNIST
                in Content.pacific -> Personality.PACIFIC
                else -> if (n.regime == Regime.DICTATORSHIP && r() < 0.5f) Personality.OPPORTUNIST
                else if (r() < 0.7f) Personality.TRADER else Personality.PACIFIC
            }
            for (a in Area.entries) if (a.hasIndex) {
                val tg = target(a, shares[a.ordinal])
                n.index[a.ordinal] = (tg * 0.7f + BASE_IDX[t] * 0.3f + (r() - 0.5f) * 8f).coerceIn(5f, 98f)
            }
            n.corruption = (CORRUPTION[t] + (r() - 0.5f) * 16f).coerceIn(5f, 90f)
            if (n.regime == Regime.DICTATORSHIP) n.corruption = min(95f, n.corruption + 10f)
            n.stability = (55f + (tier - 3) * 6f + (r() - 0.5f) * 16f).coerceIn(20f, 90f)
            for (g in Group.entries) n.groups[g.ordinal] = (48f + (tier - 3) * 3f + (r() - 0.5f) * 14f).coerceIn(20f, 80f)
            n.approval = n.groups.average().toFloat()
            n.debt = c.gdp * (DEBT_OVERRIDE[c.code] ?: DEBT[t] * (0.7f + r() * 0.6f))
            n.treasury = c.gdp * 0.03
            n.militaryFund = c.gdp * shares[Area.DEFENSE.ordinal] * 0.25
            n.growth = (if (tier >= 4) 0.018f else 0.035f) + (r() - 0.5f) * 0.015f
            n.nextElection = if (n.regime.elections) 120 + (r() * 365 * 4).toInt() else -1
            n.congress = 45f + r() * 20f
            val techBase = when (tier) { 5 -> 5; 4 -> 4; 3 -> 3; 2 -> 2; else -> 1 }
            for (b in TechBranch.entries) n.tech[b.ordinal] = max(1, techBase + (if (r() < 0.3f) 1 else 0))
            n.nukes = Content.nuclear[c.code] ?: 0
            if (n.nukes > 0) n.tech[TechBranch.NUCLEAR.ordinal] = max(6, n.tech[TechBranch.NUCLEAR.ordinal])
            else n.tech[TechBranch.NUCLEAR.ordinal] = min(3, n.tech[TechBranch.NUCLEAR.ordinal])
            if (c.code in setOf("USA", "CHN", "RUS", "GBR", "FRA", "ISR", "JPN", "KOR", "DEU")) {
                n.tech[TechBranch.LAND.ordinal] += 1; n.tech[TechBranch.AIR.ordinal] += 1
            }
            if (n.regime == Regime.DICTATORSHIP) n.laws += setOf("censura", "partido_unico")
            if (tier >= 4) n.laws += "saude_universal"
            if (c.code in setOf("ISR", "KOR", "PRK", "UKR", "RUS", "TUR", "IRN", "EGY", "BRA", "CHE", "FIN", "SGP", "TWN")) n.laws += "servico_militar"
            n
        }.toMutableList()

        val owner = IntArray(np) { world.provinces[it].country }
        val provPop = DoubleArray(np) { world.provinces[it].population.toDouble() }
        val provGdp = DoubleArray(np)
        // PIB por província proporcional à população (capital um pouco mais rica)
        for (c in world.countries) {
            val ids = (0 until np).filter { owner[it] == c.index }
            val weights = ids.map { provPop[it].coerceAtLeast(1.0) * (if (it == c.capital) 1.6 else 1.0) }
            val tot = weights.sum().coerceAtLeast(1.0)
            ids.forEachIndexed { k, id -> provGdp[id] = c.gdp * weights[k] / tot }
        }

        val relations = FloatArray(nc * nc)
        val game = Game(
            player = 0, leaderTitle = title, settings = settings, nations = nations,
            owner = owner, controller = owner.copyOf(), core = owner.copyOf(),
            siege = FloatArray(np), resistance = FloatArray(np),
            provPop = provPop, provGdp = provGdp, relations = relations,
            rngState = (settings.seed * 6364136223846793005L + 1442695040888963407L).let { if (it == 0L) 7L else it },
        )
        val byCode = world.byCode
        val idOf = { code: String -> byCode[code]?.index }
        game.player = idOf(custom?.territory ?: playerCode) ?: 0

        // ---- relações de partida
        val adj = Array(nc) { HashSet<Int>() }
        for (p in world.provinces) for (q in p.neighbors) {
            val a = owner[p.id]; val b = owner[q]
            if (a != b) { adj[a] += b; adj[b] += a }
        }
        for (a in 0 until nc) for (b in a + 1 until nc) {
            val ca = world.countries[a]; val cb = world.countries[b]
            var v = 5f + (r() - 0.5f) * 20f
            if (ca.continent == cb.continent) v += 8f
            if (b in adj[a]) v -= 4f
            val ra = nations[a].regime; val rb = nations[b].regime
            if (ra.democratic == rb.democratic) v += 8f else v -= 8f
            game.setRel(a, b, v)
        }
        for ((x, y, v) in Content.rivalries + Content.friendships) {
            val a = idOf(x); val b = idOf(y)
            if (a != null && b != null) game.setRel(a, b, v)
        }

        // ---- blocos reais
        Content.blocSeeds.forEachIndexed { i, s ->
            val members = s.codes.mapNotNull(idOf).toMutableList()
            game.blocs += Bloc(i + 1, s.name, s.military, s.economic, members, color = s.color)
            for (x in members) for (y in members) if (x < y) {
                game.addRel(x, y, if (s.military) 25f else 12f)
                if (s.economic && !game.treaties.any { it.type == TreatyType.TRADE && it.a == x && it.b == y })
                    game.treaties += Treaty(TreatyType.TRADE, x, y, 0)
            }
        }
        // alguns acordos comerciais bilaterais entre amigos
        for ((x, y, v) in Content.friendships) {
            val a = idOf(x) ?: continue; val b = idOf(y) ?: continue
            if (game.treaties.none { it.type == TreatyType.TRADE && ((it.a == a && it.b == b) || (it.a == b && it.b == a)) })
                game.treaties += Treaty(TreatyType.TRADE, a, b, 0)
            if (v >= 70f) game.treaties += Treaty(TreatyType.ALLIANCE, a, b, 0)
        }

        // ---- cores do mapa: vizinhos nunca com a mesma cor
        run {
            val order = (0 until nc).sortedByDescending { adj[it].size }
            val used = IntArray(nc) { -1 }
            for (i in order) {
                val taken = adj[i].mapNotNull { j -> used[j].takeIf { it >= 0 } }.toSet()
                val pref = (world.countries[i].mapColor * 5 + i) % PALETTE.size
                var k = pref
                for (step in 0 until PALETTE.size) { val c = (pref + step * 7) % PALETTE.size; if (c !in taken) { k = c; break } }
                used[i] = k
                nations[i].mapColor = PALETTE[k]
            }
        }

        // ---- país personalizado
        if (custom != null) {
            val me = nations[game.player]
            me.name = custom.name
            me.flag = custom.flag
            me.mapColor = custom.color
            me.custom = true
            me.regime = title.regime
        } else {
            nations[game.player].regime = title.regime
        }
        val me = nations[game.player]
        if (!me.regime.elections) me.nextElection = -1
        else if (me.nextElection < 0) me.nextElection = 365 * 2
        if (!me.regime.decree) me.laws.removeAll { Content.lawById[it]?.decreeOnly == true }

        // ---- forças armadas
        val mil = MilitarySeeder(world, game, ::r)
        for (n in nations) mil.seed(n)

        game.invalidate()
        return game
    }

    val PALETTE = listOf(
        0xFFB9A47BL, 0xFF8FB08AL, 0xFFB98A8EL, 0xFF8E9EC2L, 0xFFC9BC7EL, 0xFFA48DBFL, 0xFF7FB0ACL, 0xFFCC9A6EL,
        0xFF9AA8A0L, 0xFFB7C08AL, 0xFFC4A0B8L, 0xFF92B6CCL, 0xFFD0B08CL, 0xFF8FA6B8L, 0xFFAFC4A2L, 0xFFC7928AL,
    )
}

/** Cria os exércitos, frotas e alas aéreas iniciais. */
private class MilitarySeeder(val world: WorldMap, val game: Game, val r: () -> Float) {
    fun seed(n: Nation) {
        val c = world.countries[n.id]
        val boost = Content.militaryBoost[n.code] ?: 1f
        val defense = n.shares[Area.DEFENSE.ordinal]
        var value = c.gdp * defense * 2.0 * boost
        // teto pela população: 1 divisão a cada 250 mil habitantes
        value = min(value, c.population / 250_000.0 * 4.0)
        if (value < 1.5) return // microestados sem forças armadas
        val provinces = (0 until world.provinces.size).filter { game.owner[it] == n.id }
        if (provinces.isEmpty()) return
        val coastal = provinces.filter { world.provinces[it].coastal }
        val tier = n.income
        var landF = if (tier >= 5) 0.45 else 0.62
        var seaF = if (coastal.isEmpty()) 0.0 else if (tier >= 5) 0.30 else 0.18
        val airF = 1.0 - landF - seaF
        if (value < 8) { landF = 1.0; seaF = 0.0 }

        // ---- terra
        val land = value * landF
        val inf = (land * 0.38 / UnitType.INFANTRY.cost)
        val arm = (land * 0.28 / UnitType.ARMOR.cost)
        val art = (land * 0.20 / UnitType.ARTILLERY.cost)
        val aa = (land * 0.14 / UnitType.ANTIAIR.cost)
        val totalDiv = inf + arm + art + aa
        val nArmies = ceil(totalDiv / 18.0).toInt().coerceIn(1, 18)
        val spots = armySpots(n, provinces, nArmies)
        for (k in 0 until nArmies) {
            val a = newArmy(n.id, spots[k % spots.size], Domain.LAND)
            a.units[UnitType.INFANTRY.ordinal] = round1(inf / nArmies).coerceAtLeast(1f)
            a.units[UnitType.ARMOR.ordinal] = round1(arm / nArmies)
            a.units[UnitType.ARTILLERY.ordinal] = round1(art / nArmies)
            a.units[UnitType.ANTIAIR.ordinal] = round1(aa / nArmies)
            a.name = "${k + 1}º Exército"
        }

        // ---- mar
        val sea = value * seaF
        if (sea >= 6 && coastal.isNotEmpty()) {
            val carriers = if (sea > 160) (sea * 0.25 / UnitType.CARRIER.cost).toInt() else 0
            val rest = sea - carriers * UnitType.CARRIER.cost
            val nFleets = when { sea > 400 -> 3; sea > 100 -> 2; else -> 1 }
            val ports = coastal.sortedByDescending { game.provPop[it] }
            for (k in 0 until nFleets) {
                val f = newArmy(n.id, ports[k % ports.size], Domain.SEA)
                f.units[UnitType.DESTROYER.ordinal] = round1(rest * 0.55 / UnitType.DESTROYER.cost / nFleets).coerceAtLeast(1f)
                f.units[UnitType.SUBMARINE.ordinal] = round1(rest * 0.3 / UnitType.SUBMARINE.cost / nFleets)
                f.units[UnitType.TRANSPORT.ordinal] = round1(rest * 0.15 / UnitType.TRANSPORT.cost / nFleets).coerceAtLeast(1f)
                f.units[UnitType.CARRIER.ordinal] = (carriers / nFleets + if (k < carriers % nFleets) 1 else 0).toFloat()
                f.name = "${k + 1}ª Frota"
            }
        }

        // ---- ar
        val air = value * airF
        if (air >= 4) {
            val nWings = if (air > 200) 3 else if (air > 50) 2 else 1
            val bases = armySpots(n, provinces, nWings)
            for (k in 0 until nWings) {
                val w = newArmy(n.id, bases[k % bases.size], Domain.AIR)
                w.units[UnitType.FIGHTER.ordinal] = round1(air * 0.55 / UnitType.FIGHTER.cost / nWings).coerceAtLeast(1f)
                w.units[UnitType.BOMBER.ordinal] = if (tier >= 4 || air > 60) round1(air * 0.25 / UnitType.BOMBER.cost / nWings) else 0f
                w.units[UnitType.DRONE.ordinal] = round1(air * 0.2 / UnitType.DRONE.cost / nWings)
                w.name = "${k + 1}ª Ala Aérea"
            }
        }
    }

    private fun round1(v: Double) = ((v * 2).roundToInt() / 2.0).toFloat()

    /** Capital primeiro, depois províncias de fronteira com rivais, depois as mais populosas. */
    private fun armySpots(n: Nation, provinces: List<Int>, count: Int): List<Int> {
        val out = mutableListOf(n.capital.takeIf { game.owner[it] == n.id } ?: provinces.first())
        val border = provinces.filter { p ->
            world.provinces[p].neighbors.any { q -> game.owner[q] != n.id && game.rel(n.id, game.owner[q]) < -20f }
        }.sortedByDescending { game.provPop[it] }
        val popular = provinces.sortedByDescending { game.provPop[it] }
        for (p in border + popular) {
            if (out.size >= count) break
            if (p !in out) out += p
        }
        return out
    }

    private fun newArmy(owner: Int, province: Int, d: Domain): Army {
        val a = Army(game.nextArmyId++, owner, province, d)
        game.armies += a
        return a
    }
}
