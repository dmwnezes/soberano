package com.dmwnezes.soberano.sim

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Projeto nacional: obra cara e demorada com bônus permanente. */
class ProjectDef(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    /** custo em fração do PIB anual */
    val cost: Float,
    val months: Int,
    val index: Map<Area, Float> = emptyMap(),
    val growth: Float = 0f,
    val stability: Float = 0f,
    val groups: Map<Group, Float> = emptyMap(),
    val unitDiscount: Float = 0f,
    val research: Float = 0f,
    val needsCoast: Boolean = false,
    val needsTech: Pair<TechBranch, Int>? = null,
)

/**
 * Sistemas de desenvolvimento: recursos naturais, ministros, projetos nacionais,
 * reservistas automáticos e histórico mensal.
 */
class Development(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    // ============================================================ recursos naturais

    companion object {
        /** Produção anual aproximada em US$ bi (preço normal) dos maiores produtores. */
        val OIL = mapOf(
            "USA" to 260f, "SAU" to 230f, "RUS" to 220f, "CAN" to 110f, "IRQ" to 95f, "CHN" to 90f, "ARE" to 80f,
            "IRN" to 75f, "BRA" to 70f, "KWT" to 60f, "KAZ" to 40f, "NOR" to 40f, "MEX" to 38f, "QAT" to 35f,
            "NGA" to 32f, "DZA" to 30f, "LBY" to 25f, "AGO" to 24f, "OMN" to 22f, "VEN" to 20f, "GBR" to 18f,
            "AZE" to 15f, "COL" to 15f, "IDN" to 14f, "MYS" to 12f, "EGY" to 12f, "ARG" to 12f, "ECU" to 10f,
            "GUY" to 10f, "IND" to 15f, "TKM" to 10f, "SDS" to 4f, "GAB" to 4f, "COG" to 6f, "TCD" to 3f,
        )
        val MINERALS = mapOf(
            "AUS" to 150f, "CHN" to 160f, "BRA" to 70f, "CHL" to 55f, "RUS" to 60f, "ZAF" to 45f, "PER" to 35f,
            "CAN" to 45f, "USA" to 55f, "COD" to 30f, "IND" to 40f, "IDN" to 35f, "KAZ" to 20f, "MEX" to 20f,
            "ZMB" to 12f, "GIN" to 8f, "MNG" to 10f, "UKR" to 12f, "SWE" to 8f, "BOL" to 6f, "PHL" to 8f,
            "NAM" to 4f, "BWA" to 6f, "GHA" to 8f, "TUR" to 8f, "IRN" to 8f, "MRT" to 3f, "NCL" to 2f,
        )
        val FOOD = mapOf(
            "CHN" to 230f, "IND" to 160f, "USA" to 190f, "BRA" to 120f, "IDN" to 60f, "RUS" to 55f, "ARG" to 45f,
            "FRA" to 45f, "UKR" to 30f, "CAN" to 40f, "AUS" to 30f, "THA" to 28f, "VNM" to 25f, "MEX" to 30f,
            "TUR" to 30f, "NGA" to 35f, "PAK" to 30f, "DEU" to 30f, "ESP" to 28f, "ITA" to 28f, "EGY" to 20f,
            "BGD" to 22f, "ETH" to 15f, "PHL" to 15f, "IRN" to 15f, "POL" to 15f, "NLD" to 15f, "MYS" to 10f,
            "NZL" to 12f, "COL" to 12f, "PRY" to 8f, "URY" to 6f, "KAZ" to 8f, "ROU" to 8f, "HUN" to 6f,
        )
    }

    /** Consumo anual por US$ bi de PIB, calibrado na partida para a produção mundial bater com o consumo. */
    private val intensity: FloatArray by lazy {
        val worldGdp = game.nations.sumOf { game.natGdp(it.id) }.coerceAtLeast(1.0)
        FloatArray(Resource.entries.size) { r -> (provRes[r].sum() / worldGdp).toFloat() }
    }

    /** Produção anual de cada recurso por província (fixa: muda de dono com a província). */
    val provRes: Array<FloatArray> by lazy {
        val tables = listOf(OIL, MINERALS, FOOD)
        val out = Array(Resource.entries.size) { FloatArray(world.provinces.size) }
        for ((r, table) in tables.withIndex()) {
            for ((code, total) in table) {
                val c = world.byCode[code] ?: continue
                val ids = world.provinces.filter { it.country == c.index }
                // alimentos seguem área e população; petróleo e minérios seguem a área
                val w = ids.map { p -> if (r == 2) p.areaKm2 * 0.5 + p.population * 0.002 else p.areaKm2.toDouble() }
                val tot = w.sum().coerceAtLeast(1.0)
                ids.forEachIndexed { k, p -> out[r][p.id] = (total * w[k] / tot).toFloat() }
            }
        }
        out
    }

    fun production(n: Int, r: Resource): Float {
        var s = 0f
        val arr = provRes[r.ordinal]
        for (i in game.owner.indices) if (game.owner[i] == n && game.controller[i] == n) s += arr[i]
        return s
    }

    fun consumption(n: Int, r: Resource): Float = (game.natGdp(n) * intensity[r.ordinal]).toFloat()

    /** Saldo anual de comércio de um recurso, em US$ bi ao preço atual (positivo = exporta). Contratos já contam. */
    fun balance(n: Int, r: Resource): Float = (production(n, r) - consumption(n, r) - contracted(n, r)) * game.prices[r.ordinal]

    /** Quanto [n] vende (positivo) ou compra (negativo) por contrato, por ano. */
    fun contracted(n: Int, r: Resource): Float {
        var s = 0f
        for (d in game.deals) if (d.resource == r) { if (d.seller == n) s += d.amount; if (d.buyer == n) s -= d.amount }
        return s
    }

    /** Sobra exportável de [n] em [r] (sem contar contratos já fechados). */
    fun surplus(n: Int, r: Resource): Float = production(n, r) - consumption(n, r) - contracted(n, r)

    // ============================================================ venda de recursos e armas

    val dealYears = 2
    /** Preço do contrato: 15% acima do mercado, garantido. */
    val dealPremium = 1.15f

    fun dealRefusal(seller: Int, buyer: Int, r: Resource, amount: Float): String? {
        if (seller == buyer) return "Mesmo país"
        if (sim.atWar(seller, buyer)) return "Estão em guerra"
        if (game.treaties.any { it.type == TreatyType.EMBARGO && ((it.a == seller && it.b == buyer) || (it.a == buyer && it.b == seller)) }) return "Há embargo entre vocês"
        if (amount <= 0f) return "Quantidade inválida"
        if (surplus(seller, r) < amount) return "Sobra de ${r.label.lowercase()} insuficiente"
        val need = -surplus(buyer, r)
        if (need < amount * 0.5f) return "Eles não precisam de tanto ${r.label.lowercase()}"
        if (game.rel(seller, buyer) < 0f) return "Relação precisa ser positiva"
        val yearly = amount * game.prices[r.ordinal] * dealPremium
        if (sim.nation(buyer).treasury < yearly / 12 * 3) return "Eles não têm caixa para pagar"
        return null
    }

    fun sellResource(seller: Int, buyer: Int, r: Resource, amount: Float): String? {
        dealRefusal(seller, buyer, r, amount)?.let { return it }
        game.deals += ResourceDeal(seller, buyer, r, amount, game.day, game.day + 365 * dealYears)
        game.addRel(seller, buyer, 6f)
        if (sim.involvesPlayer(seller, buyer)) sim.feed(r.icon, "Contrato: ${sim.nation(seller).name} vende ${r.label.lowercase()} para ${sim.nation(buyer).name}", 1)
        sim.touch()
        return null
    }

    /** Pagamentos mensais dos contratos. */
    private fun monthlyDeals() {
        game.deals.removeAll { d -> d.until < game.day || !sim.nation(d.seller).alive || !sim.nation(d.buyer).alive || sim.atWar(d.seller, d.buyer) }
        for (d in game.deals) {
            val value = d.amount * game.prices[d.resource.ordinal] * dealPremium / 12.0
            val buyer = sim.nation(d.buyer); val seller = sim.nation(d.seller)
            seller.treasury += value * 0.5
            buyer.treasury -= value * 0.5
            if (buyer.treasury < 0) { buyer.debt -= buyer.treasury; buyer.treasury = 0.0 }
        }
    }

    fun armsPrice(t: UnitType, qty: Int) = t.cost * qty * 1.2

    /** Unidades de [t] que [n] pode vender (em tropas paradas e fora de combate). */
    fun armsAvailable(n: Int, t: UnitType): Int =
        game.armies.filter { it.owner == n && !it.battle && !it.moving }.sumOf { it.units[t.ordinal].toDouble() }.toInt()

    fun armsRefusal(seller: Int, buyer: Int, t: UnitType, qty: Int): String? {
        if (sim.atWar(seller, buyer)) return "Estão em guerra"
        if (sim.unitedNations.has(ResolutionType.ARMS_EMBARGO, buyer)) return "A ONU proibiu vender armas a eles"
        if (sim.unitedNations.has(ResolutionType.ARMS_EMBARGO, seller)) return "Você está sob embargo de armas da ONU"
        if (game.rel(seller, buyer) < 10f) return "Relação precisa chegar a 10"
        if (armsAvailable(seller, t) < qty) return "Você não tem $qty ${t.label.lowercase()} paradas"
        if (sim.nation(buyer).treasury < armsPrice(t, qty)) return "Eles não têm ${Fmt.money(armsPrice(t, qty))}"
        if (t.domain == Domain.SEA && sim.provincesOf(buyer).none { world.provinces[it].coastal }) return "Eles não têm litoral"
        return null
    }

    fun sellArms(seller: Int, buyer: Int, t: UnitType, qty: Int): String? {
        armsRefusal(seller, buyer, t, qty)?.let { return it }
        var left = qty.toFloat()
        for (a in game.armies.filter { it.owner == seller && !it.battle && !it.moving }.sortedByDescending { it.units[t.ordinal] }) {
            if (left <= 0f) break
            val take = min(left, kotlin.math.floor(a.units[t.ordinal]))
            a.units[t.ordinal] -= take; left -= take
        }
        game.armies.removeAll { !it.alive() }
        val b = sim.nation(buyer)
        val price = armsPrice(t, qty)
        b.treasury -= price
        sim.nation(seller).treasury += price
        val spot = if (t.domain == Domain.SEA) sim.provincesOf(buyer).filter { world.provinces[it].coastal && game.controller[it] == buyer }.maxByOrNull { game.provPop[it] }
            else b.capital.takeIf { game.controller[it] == buyer } ?: sim.provincesOf(buyer).firstOrNull { game.controller[it] == buyer }
        if (spot != null) {
            val army = game.armies.firstOrNull { it.owner == buyer && it.province == spot && it.domain == t.domain && !it.moving }
                ?: sim.military.newArmy(buyer, spot, t.domain)
            army.units[t.ordinal] += qty.toFloat()
        }
        game.addRel(seller, buyer, 8f)
        // os inimigos do comprador não gostam
        for (o in game.nations) if (o.alive && o.id != seller && o.id != buyer && (sim.atWar(o.id, buyer) || game.rel(o.id, buyer) < -50f)) game.addRel(seller, o.id, -4f)
        if (sim.involvesPlayer(seller, buyer)) sim.feed("factory", "${sim.nation(seller).name} vendeu $qty ${t.label.lowercase()} para ${b.name} por ${Fmt.money(price)}", 1)
        sim.touch()
        return null
    }

    /** Países do computador pedem para comprar do jogador. */
    private fun offersToPlayer() {
        val p = game.player
        if (!sim.player.alive || game.proposals.size >= 4 || !sim.chance(0.35)) return
        val buyers = game.nations.filter { it.alive && it.id != p && !sim.atWar(it.id, p) && game.rel(it.id, p) >= 15f && game.proposals.none { q -> q.from == it.id } }
        if (buyers.isEmpty()) return
        val b = sim.pick(buyers)
        // recurso que falta para eles e sobra para você
        val res = Resource.entries.filter { surplus(p, it) > 3f && surplus(b.id, it) < -3f }
        if (res.isNotEmpty() && sim.chance(0.6)) {
            val r = sim.pick(res)
            val amount = min(surplus(p, r) * 0.5f, -surplus(b.id, r)).coerceAtLeast(1f)
            if (dealRefusal(p, b.id, r, amount) == null) game.proposals += Proposal(b.id, "BUY_RES:${r.name}", game.day, amount.toDouble())
            return
        }
        // em guerra, compram armas
        if (sim.atWarAny(b.id)) {
            val t = sim.pick(listOf(UnitType.INFANTRY, UnitType.ARMOR, UnitType.ARTILLERY, UnitType.FIGHTER))
            val qty = (2 + sim.randInt(4)).coerceAtMost(armsAvailable(p, t) / 3)
            if (qty >= 1 && armsRefusal(p, b.id, t, qty) == null) game.proposals += Proposal(b.id, "BUY_ARMS:${t.name}", game.day, qty.toDouble())
        }
    }

    fun tradeBalance(n: Int): Float = Resource.entries.sumOf { balance(n, it).toDouble() }.toFloat()

    /** Preços andam um pouco todo mês e voltam devagar ao normal. */
    fun monthlyPrices() {
        for (r in Resource.entries) {
            val i = r.ordinal
            val drift = (1f - game.prices[i]) * 0.08f + (sim.randf() - 0.5f) * 0.08f
            game.prices[i] = (game.prices[i] + drift).coerceIn(0.4f, 2.5f)
        }
    }

    /** Efeito mensal do comércio de recursos no caixa e no crescimento de [n]. */
    fun monthlyTrade(n: Nation) {
        val gdp = game.natGdp(n.id)
        if (gdp <= 0) return
        var bal = tradeBalance(n.id)
        val embargoed = game.treaties.any { it.type == TreatyType.EMBARGO && it.b == n.id }
        if (embargoed && bal > 0) bal *= 0.5f
        // o governo fica com parte do saldo (royalties) ou paga parte do déficit (subsídios)
        n.treasury += bal * 0.12 / 12.0
        if (n.treasury < 0) { n.debt += -n.treasury; n.treasury = 0.0 }
    }

    fun tradeGrowth(n: Nation): Float {
        val gdp = game.natGdp(n.id)
        if (gdp <= 0) return 0f
        return (tradeBalance(n.id) / gdp.toFloat() * 0.08f).coerceIn(-0.015f, 0.02f)
    }

    // ============================================================ ministros

    private val firstNames = listOf("Ana", "Bruno", "Carla", "Diego", "Elisa", "Fábio", "Gabriela", "Heitor", "Isabela", "João",
        "Karina", "Lucas", "Marina", "Nelson", "Olívia", "Paulo", "Raquel", "Sérgio", "Tatiana", "Vítor", "Helena", "Ricardo",
        "Beatriz", "Rodrigo", "Juliana", "Marcos", "Patrícia", "Eduardo", "Camila", "Otávio")
    private val lastNames = listOf("Almeida", "Barros", "Cardoso", "Duarte", "Esteves", "Ferraz", "Gomes", "Holanda", "Lima",
        "Machado", "Nogueira", "Pacheco", "Queiroz", "Rocha", "Sampaio", "Teixeira", "Vasconcelos", "Xavier", "Moura", "Freitas",
        "Bezerra", "Carvalho", "Monteiro", "Pires", "Ribeiro")

    private fun randomMinister(slot: MinisterSlot): Minister {
        val skill = 1 + sim.randInt(5)
        val loyalty = 1 + sim.randInt(5)
        // os muito competentes às vezes são mais corruptos
        val corruption = (1 + sim.randInt(5) + if (skill >= 4 && sim.chance(0.3)) 1 else 0).coerceAtMost(5)
        return Minister("${sim.pick(firstNames)} ${sim.pick(lastNames)}", slot, skill, loyalty, corruption, game.day)
    }

    fun ensureCabinet() {
        for (slot in MinisterSlot.entries) if (game.ministers.none { it.slot == slot }) game.ministers += randomMinister(slot)
        if (game.candidates.isEmpty()) refreshCandidates()
    }

    fun refreshCandidates() {
        game.candidates.clear()
        for (slot in MinisterSlot.entries) repeat(2) { game.candidates += randomMinister(slot) }
    }

    fun minister(slot: MinisterSlot): Minister? = game.ministers.firstOrNull { it.slot == slot }

    /** Multiplicador da eficiência de uma área pelo ministro responsável (0,7 a 1,3). */
    fun ministerMult(a: Area): Float {
        val slot = when (a) {
            Area.HEALTH -> MinisterSlot.HEALTH
            Area.EDUCATION, Area.RESEARCH -> MinisterSlot.EDUCATION
            Area.SECURITY -> MinisterSlot.SECURITY
            Area.INFRA, Area.SOCIAL, Area.ENVIRONMENT -> MinisterSlot.ECONOMY
            Area.DEFENSE -> MinisterSlot.DEFENSE
        }
        val m = minister(slot) ?: return 1f
        return 0.7f + (m.skill - 1) * 0.15f
    }

    fun cabinetCorruption(): Float = game.ministers.sumOf { (it.corruption - 3).toDouble() }.toFloat() * 2f
    fun economyBonus(): Float = (minister(MinisterSlot.ECONOMY)?.skill ?: 3).minus(3) * 0.002f
    fun defenseDiscount(): Float = (minister(MinisterSlot.DEFENSE)?.skill ?: 3).minus(3) * 0.03f
    fun foreignBonus(): Float = (minister(MinisterSlot.FOREIGN)?.skill ?: 3).minus(3) * 0.5f

    val hireCost = 5f

    fun hire(c: Minister): String? {
        val me = sim.player
        if (me.politicalCapital < hireCost) return "Precisa de ${hireCost.toInt()} de capital político"
        me.politicalCapital -= hireCost
        val old = minister(c.slot)
        game.ministers.removeAll { it.slot == c.slot }
        game.ministers += Minister(c.name, c.slot, c.skill, c.loyalty, c.corruption, game.day)
        game.candidates.remove(c)
        // demitir alguém leal irrita a base
        if (old != null && old.loyalty >= 4) me.groupMods[Group.WORKERS.ordinal] -= 2f
        sim.feed("person", "${c.name} assumiu ${c.slot.label}", 1)
        sim.touch()
        return null
    }

    /** Ministros desleais com governo fraco podem sair ou causar escândalo. */
    private fun monthlyCabinet() {
        val me = sim.player
        for (m in game.ministers.toList()) {
            if (m.loyalty <= 2 && me.approval < 40f && sim.chance(0.03)) {
                game.ministers.remove(m)
                game.ministers += randomMinister(m.slot)
                me.stability -= 3f
                sim.feed("door", "${m.name} (${m.slot.label}) renunciou criticando o governo", 2)
            } else if (m.corruption >= 4 && sim.chance(0.015 * m.corruption)) {
                me.groupMods[Group.YOUTH.ordinal] -= 4f
                me.corruption += 2f
                sim.feed("scandal", "Escândalo envolve ${m.name}, de ${m.slot.label}", 2)
            }
        }
        if (game.day % 180 < 31) refreshCandidates()
    }

    // ============================================================ projetos nacionais

    val projects: List<ProjectDef> = listOf(
        ProjectDef("ferrovia", "Ferrovia nacional", "factory", "Trilhos ligando as capitais: comércio e tropas mais rápidas",
            0.012f, 30, index = mapOf(Area.INFRA to 8f), growth = 0.003f),
        ProjectDef("hospitais", "Rede de hospitais", "health", "Hospitais regionais em todo o país",
            0.010f, 24, index = mapOf(Area.HEALTH to 8f), groups = mapOf(Group.RETIREES to 4f)),
        ProjectDef("universidades", "Universidades federais", "school", "Novos campi e bolsas de pesquisa",
            0.010f, 30, index = mapOf(Area.EDUCATION to 7f, Area.RESEARCH to 5f), groups = mapOf(Group.YOUTH to 4f)),
        ProjectDef("usina_nuclear", "Usina nuclear", "atom", "Energia barata e limpa para a indústria",
            0.015f, 36, index = mapOf(Area.ENVIRONMENT to 3f), growth = 0.003f, needsTech = TechBranch.NUCLEAR to 4),
        ProjectDef("espacial", "Programa espacial", "rocket", "Satélites e foguetes próprios",
            0.014f, 40, index = mapOf(Area.RESEARCH to 8f), research = 6f, groups = mapOf(Group.YOUTH to 3f)),
        ProjectDef("porto", "Porto moderno", "ship", "Terminal de contêineres de classe mundial",
            0.008f, 24, index = mapOf(Area.INFRA to 4f), growth = 0.002f, needsCoast = true),
        ProjectDef("ciberseguranca", "Centro de cibersegurança", "shield", "Proteção contra espionagem e crimes digitais",
            0.006f, 18, index = mapOf(Area.SECURITY to 6f)),
        ProjectDef("defesa", "Polo da indústria de defesa", "military", "Fábricas nacionais de armamento",
            0.012f, 30, unitDiscount = 0.10f, groups = mapOf(Group.MILITARY to 6f)),
        ProjectDef("parques", "Parques nacionais", "leaf", "Grandes áreas de preservação",
            0.004f, 18, index = mapOf(Area.ENVIRONMENT to 10f), groups = mapOf(Group.YOUTH to 3f)),
        ProjectDef("habitacao", "Programa de moradia", "people", "Milhões de casas populares",
            0.012f, 30, index = mapOf(Area.SOCIAL to 8f), groups = mapOf(Group.WORKERS to 6f), stability = 3f),
        ProjectDef("olimpiadas", "Sediar as Olimpíadas", "trophy", "Vitrine mundial e orgulho nacional",
            0.010f, 24, index = mapOf(Area.INFRA to 3f), groups = Group.entries.associateWith { 3f }),
        ProjectDef("irrigacao", "Irrigação no semiárido", "flood", "Canais e cisternas para o campo",
            0.008f, 24, index = mapOf(Area.SOCIAL to 3f), growth = 0.002f, groups = mapOf(Group.WORKERS to 3f)),
    )
    val projectById = projects.associateBy { it.id }

    fun projectCost(n: Nation, p: ProjectDef): Double = game.natGdp(n.id) * p.cost

    fun canStart(n: Nation, p: ProjectDef): String? {
        if (p.id in n.built) return "Já construído"
        if (n.projects.any { it.id == p.id }) return "Em obras"
        if (n.projects.size >= 3) return "No máximo 3 obras ao mesmo tempo"
        if (p.needsCoast && sim.provincesOf(n.id).none { world.provinces[it].coastal }) return "Precisa de litoral"
        p.needsTech?.let { (b, l) -> if (n.tech[b.ordinal] < l) return "Precisa de ${b.label} nível $l" }
        if (n.treasury < projectCost(n, p)) return "Tesouro insuficiente (${Fmt.money(projectCost(n, p))})"
        return null
    }

    fun start(n: Nation, p: ProjectDef): String? {
        canStart(n, p)?.let { return it }
        n.treasury -= projectCost(n, p)
        n.projects += ProjectBuild(p.id, p.months, p.months)
        if (n.id == game.player) sim.feed(p.icon, "Obra iniciada: ${p.name}", 1)
        sim.touch()
        return null
    }

    private fun monthlyProjects(n: Nation) {
        for (b in n.projects.toList()) {
            b.monthsLeft--
            if (b.monthsLeft <= 0) {
                n.projects.remove(b)
                n.built += b.id
                val p = projectById[b.id] ?: continue
                for ((g, v) in p.groups) n.groupMods[g.ordinal] += v * 2
                if (n.id == game.player) sim.feed(p.icon, "Obra concluída: ${p.name}", 1)
            }
        }
    }

    fun builtIndex(n: Nation, a: Area): Float = n.built.sumOf { (projectById[it]?.index?.get(a) ?: 0f).toDouble() }.toFloat()
    fun builtGrowth(n: Nation): Float = n.built.sumOf { (projectById[it]?.growth ?: 0f).toDouble() }.toFloat()
    fun builtStability(n: Nation): Float = n.built.sumOf { (projectById[it]?.stability ?: 0f).toDouble() }.toFloat()
    fun builtGroups(n: Nation, g: Group): Float = n.built.sumOf { (projectById[it]?.groups?.get(g) ?: 0f).toDouble() }.toFloat()
    fun builtDiscount(n: Nation): Float = n.built.sumOf { (projectById[it]?.unitDiscount ?: 0f).toDouble() }.toFloat()
    fun builtResearch(n: Nation): Float = n.built.sumOf { (projectById[it]?.research ?: 0f).toDouble() }.toFloat()

    // ============================================================ reservistas automáticos

    /** Teto de divisões terrestres sustentadas pela população. */
    fun manpowerCap(n: Int): Float = (sqrt(game.natPop(n) / 1e6) * 10.0).toFloat().coerceAtLeast(4f) * (if (sim.nation(n).mobilized) 1.5f else 1f)

    /** Divisões novas por mês que os estados do país treinam sozinhos. */
    fun recruitRate(n: Nation): Float {
        val pop = game.natPop(n.id)
        val def = (n.shares[Area.DEFENSE.ordinal] / 0.02f).coerceIn(0.3f, 3f)
        val stab = 0.6f + n.stability / 250f
        val war = if (sim.atWarAny(n.id)) 1.6f else 1f
        val law = if ("servico_militar" in n.laws) 1.4f else 1f
        val mob = if (n.mobilized) 2.5f else 1f
        return (pop / 120e6).toFloat().coerceAtLeast(0.15f) * def * stab * war * law * mob
    }

    fun landDivisions(n: Int): Float = game.armies.filter { it.owner == n && it.domain == Domain.LAND }.sumOf { it.divisions().toDouble() }.toFloat()

    private fun monthlyRecruits(n: Nation) {
        if (game.natProvinces(n.id) == 0) return
        game.ensureArrays()
        val cap = manpowerCap(n.id)
        var room = cap - landDivisions(n.id)
        if (room <= 0f) return
        val monthRate = recruitRate(n) * (if (n.militaryFund < 0) 0.5f else 1f)
        val spots = recruitSpots(n)
        if (spots.isEmpty()) return
        if (n.id != game.player || spots.size == 1) {
            // computador (ou ponto de reunião): reserva nacional, divisões inteiras repartidas entre os pontos
            n.reserves += monthRate
            val count = min(n.reserves.toInt(), room.toInt())
            if (count < 1) return
            n.reserves -= count
            for (i in 0 until count) {
                val p = spots[i % spots.size]
                val a = game.armies.firstOrNull { it.owner == n.id && it.province == p && it.domain == Domain.LAND && !it.moving && !it.battle }
                    ?: sim.military.newArmy(n.id, p, Domain.LAND)
                a.units[UnitType.INFANTRY.ordinal] += 1f
            }
            if (n.id == game.player) sim.feed("military", "+$count ${if (count == 1) "divisão" else "divisões"} de reservistas em ${world.provinces[spots[0]].name}", 0)
            return
        }
        n.reserves = 0f // o treino do jogador acontece em cada estado (reserveAcc)
        // peso: raiz da população, para estados pequenos também treinarem
        val w = FloatArray(spots.size) { sqrt(game.provPop[spots[it]].coerceAtLeast(1.0)).toFloat() }
        val total = w.sum().coerceAtLeast(1e-6f)
        var made = 0
        var places = 0
        var last = -1
        for ((i, p) in spots.withIndex()) {
            if (room <= 0f) break
            val share = monthRate * w[i] / total
            val here = game.armies.firstOrNull { it.owner == n.id && it.province == p && it.domain == Domain.LAND && !it.moving && !it.battle }
            if (here != null) {
                // há tropa parada no estado: o reforço entra direto, todo mês, em todos os estados
                val add = min(share + game.reserveAcc[p], room)
                here.units[UnitType.INFANTRY.ordinal] += add
                game.reserveAcc[p] = 0f
                room -= add
                if (add >= 0.05f) { places++; last = p }
                continue
            }
            game.reserveAcc[p] += share
            if (game.reserveAcc[p] >= 1f) {
                val k = min(game.reserveAcc[p].toInt().toFloat(), room)
                if (k < 1f) continue
                game.reserveAcc[p] -= k
                room -= k
                val a = sim.military.newArmy(n.id, p, Domain.LAND)
                a.units[UnitType.INFANTRY.ordinal] += k
                made += k.toInt(); places++; last = p
            }
        }
        if (n.id == game.player && places > 0) {
            val text = if (places == 1) "Reservistas chegaram em ${world.provinces[last].name}"
            else "Reservistas reforçaram $places estados" + if (made > 0) " (+$made ${if (made == 1) "divisão nova" else "divisões novas"})" else ""
            sim.feed("military", text, 0)
        }
    }

    /** Divisões em treinamento somadas em todos os estados (para a interface). */
    fun reservesInTraining(n: Int): Float {
        game.ensureArrays()
        var s = 0f
        for (p in sim.provincesOf(n)) s += game.reserveAcc[p]
        return s
    }

    /**
     * Onde os reservistas se formam.
     * Jogador: todos os estados que controla (ou só o ponto de reunião, se marcado).
     * Computador: até 8 estados — a frente de batalha primeiro, depois os mais populosos.
     */
    fun recruitSpots(n: Nation): List<Int> {
        val mine = sim.provincesOf(n.id).filter { game.controller[it] == n.id }
        if (mine.isEmpty()) return emptyList()
        if (n.id == game.player) {
            val rp = game.rallyPoint
            if (rp >= 0 && game.controller[rp] == n.id) return listOf(rp)
            return mine
        }
        val front = mine.filter { p -> world.provinces[p].neighbors.any { q -> sim.atWar(n.id, game.controller[q]) } }
        val byPop = mine.sortedByDescending { game.provPop[it] }
        return (front.sortedByDescending { game.provPop[it] } + byPop).distinct().take(8)
    }

    /** Jogador: ponto de reunião ou capital. Computador: frente de batalha, fronteira tensa ou capital. */
    fun recruitSpot(n: Nation): Int? {
        val mine = sim.provincesOf(n.id).filter { game.controller[it] == n.id }
        if (mine.isEmpty()) return null
        if (n.id == game.player) {
            val rp = game.rallyPoint
            if (rp >= 0 && game.controller[rp] == n.id) return rp
            return n.capital.takeIf { game.controller[it] == n.id } ?: mine.maxBy { game.provPop[it] }
        }
        val front = mine.filter { p -> world.provinces[p].neighbors.any { q -> sim.atWar(n.id, game.controller[q]) } }
        if (front.isNotEmpty()) return front.maxBy { game.provPop[it] }
        return n.capital.takeIf { game.controller[it] == n.id } ?: mine.maxBy { game.provPop[it] }
    }

    // ============================================================ histórico

    private fun record() {
        val me = sim.player
        val gdp = game.natGdp(me.id)
        game.history += HistoryPoint(game.day, gdp, me.approval, me.stability, me.treasury,
            if (gdp > 0) (me.debt / gdp).toFloat() else 0f, sim.landPower(me.id).toFloat())
        while (game.history.size > 120) game.history.removeAt(0)
    }

    // ============================================================ mês

    fun monthly() {
        monthlyPrices()
        ensureCabinet()
        monthlyCabinet()
        for (n in game.nations) if (n.alive) {
            monthlyTrade(n)
            monthlyProjects(n)
            monthlyRecruits(n)
        }
        monthlyDeals()
        offersToPlayer()
        aiDeals()
        aiProjects()
        record()
    }

    /** Contratos entre países do computador. */
    private fun aiDeals() {
        if (!sim.chance(0.5)) return
        val r = sim.pick(Resource.entries)
        val sellers = game.nations.filter { it.alive && it.id != game.player && surplus(it.id, r) > 5f }
        val buyers = game.nations.filter { it.alive && it.id != game.player && surplus(it.id, r) < -5f }
        if (sellers.isEmpty() || buyers.isEmpty()) return
        val s = sim.pick(sellers); val b = buyers.maxBy { game.rel(s.id, it.id) }
        val amount = min(surplus(s.id, r), -surplus(b.id, r)) * 0.5f
        if (game.deals.none { it.seller == s.id && it.buyer == b.id && it.resource == r }) sellResource(s.id, b.id, r, amount)
    }

    /** Países do computador também constroem, devagar. */
    private fun aiProjects() {
        val n = sim.pick(game.nations.filter { it.alive && it.id != game.player })
        if (n.projects.isNotEmpty() || !sim.chance(0.5)) return
        val options = projects.filter { canStart(n, it) == null }
        if (options.isNotEmpty() && n.treasury > game.natGdp(n.id) * 0.03) start(n, sim.pick(options))
    }

    @Suppress("unused") private fun clamp(v: Float) = max(0f, v)
}
