package com.dmwnezes.soberano.sim

import kotlin.math.max
import kotlin.math.min

/** Guerra civil, separatismo e libertação de países anexados. */
class CivilWars(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    fun monthly() {
        if (!game.settings.civilWar) return
        for (n in game.nations.toList()) {
            if (!n.alive) continue
            val count = game.natProvinces(n.id)
            if (count < 6 || n.stability >= 18f || sim.atWarAny(n.id) && n.stability > 10f) continue
            val p = if (n.id == game.player) 0.03 else 0.04
            if (sim.chance(p)) secession(n)
        }
    }

    /** Uma região longe da capital se declara independente. */
    fun secession(parent: Nation): Nation? {
        val own = game.owner.indices.filter { game.owner[it] == parent.id && game.controller[it] == parent.id }
        if (own.size < 4) return null
        val cap = parent.capital
        // a região rebelde precisa ter vizinhos do mesmo país (ilhas isoladas não bastam)
        val connected = own.filter { p -> p != cap && world.provinces[p].neighbors.any { game.owner[it] == parent.id && it != cap } }
        val seed = (connected.ifEmpty { own.filter { it != cap } }).maxByOrNull { world.distanceKm(cap, it) * (1.0 + game.resistance[it] / 50.0) } ?: return null
        val want = max(2, own.size / 4)
        val region = ArrayList<Int>()
        val seen = HashSet<Int>(); val queue = ArrayDeque<Int>()
        queue += seed; seen += seed
        while (queue.isNotEmpty() && region.size < want) {
            val u = queue.removeFirst()
            if (u == cap) continue
            region += u
            for (v in world.provinces[u].neighbors.sortedBy { world.distanceKm(seed, it) })
                if (v !in seen && game.owner[v] == parent.id && game.controller[v] == parent.id) { seen += v; queue += v }
        }
        if (region.isEmpty()) return null
        val name = if (sim.chance(0.5)) "República de ${world.provinces[seed].name}" else "Frente de ${world.provinces[seed].name}"
        val regime = if (parent.regime.democratic) (if (sim.chance(0.5)) Regime.DICTATORSHIP else Regime.PRESIDENTIAL) else Regime.PRESIDENTIAL
        val rebel = createNation(name, parent, region, regime)
        // parte das tropas da região muda de lado
        for (a in game.armies.filter { it.owner == parent.id && it.province in region }) {
            if (sim.chance(0.5)) { a.owner = rebel.id; a.path = IntArray(0); a.general = null }
            else {
                a.province = cap.takeIf { game.controller[it] == parent.id } ?: a.province
                a.path = IntArray(0)
            }
        }
        // milícia rebelde
        val pop = region.sumOf { game.provPop[it] }
        val milicia = (2.0 + pop / 4e6).coerceAtMost(40.0).toFloat()
        val army = sim.military.newArmy(rebel.id, rebel.capital, Domain.LAND)
        army.name = "Milícia"
        army.units[UnitType.INFANTRY.ordinal] = milicia
        army.units[UnitType.ARTILLERY.ordinal] = (milicia / 6f)
        startCivilWar(rebel, parent)
        val tone = if (parent.id == game.player) 3 else 2
        sim.feed("protest", "Guerra civil: $name declarou independência de ${parent.name}", tone, rebel.capital)
        parent.stability = max(parent.stability, 25f)
        return rebel
    }

    /** Revolta numa província cujo país foi anexado: o país volta a existir. */
    fun liberate(core: Int, p: Int): Boolean {
        if (!game.settings.civilWar) return false
        val old = sim.nation(core)
        if (old.alive) return false
        val occupier = game.controller[p]
        val region = game.owner.indices.filter { q ->
            game.core[q] == core && game.controller[q] == occupier && (q == p || game.resistance[q] > 50f)
        }
        if (region.isEmpty()) return false
        old.alive = true
        for (q in region) {
            game.owner[q] = core; game.controller[q] = core; game.siege[q] = 0f; game.resistance[q] = 0f
        }
        old.capital = region.maxBy { game.provPop[it] }
        old.stability = 45f; old.approval = 55f; old.warExhaustion = 0f
        old.treasury = game.provGdp.let { g -> region.sumOf { g[it] } } * 0.02
        old.debt = 0.0; old.militaryFund = 0.0
        for (gr in Group.entries) old.groups[gr.ordinal] = 60f
        for (a in game.armies) if (a.province in region && a.owner == occupier) {
            a.province = sim.nation(occupier).capital.takeIf { game.controller[it] == occupier } ?: a.province
            a.path = IntArray(0)
        }
        val army = sim.military.newArmy(core, old.capital, Domain.LAND)
        army.name = "Exército de Libertação"
        army.units[UnitType.INFANTRY.ordinal] = (2.0 + region.sumOf { game.provPop[it] } / 3e6).coerceAtMost(30.0).toFloat()
        game.invalidate()
        game.treatyCache = null
        startCivilWar(old, sim.nation(occupier), "Guerra de libertação de ${old.name}")
        sim.feed("flag", "${old.name} voltou a existir e luta contra ${sim.nation(occupier).name}",
            if (occupier == game.player) 3 else 2, old.capital)
        return true
    }

    private fun startCivilWar(rebel: Nation, parent: Nation, title: String = "Guerra civil em ${parent.name}") {
        val w = War(game.nextWarId++, title, mutableListOf(rebel.id), mutableListOf(parent.id), game.day)
        game.wars += w
        game.warCache = null
        game.setRel(rebel.id, parent.id, -90f)
        // inimigos do governo simpatizam com os rebeldes
        for (o in game.nations) if (o.alive && o.id != rebel.id && o.id != parent.id) {
            val r = game.rel(o.id, parent.id)
            game.setRel(o.id, rebel.id, (-r * 0.5f).coerceIn(-40f, 40f))
        }
        sim.touch()
    }

    /** Cria um país novo com as províncias [region], herdando o jeito do país de origem. */
    fun createNation(name: String, parent: Nation, region: List<Int>, regime: Regime): Nation {
        val id = game.nations.size
        game.growRelations(id + 1)
        val palette = Setup.PALETTE
        val neighborColors = region.flatMap { world.provinces[it].neighbors.toList() }.map { sim.nation(game.owner[it]).mapColor }.toSet()
        val color = palette.firstOrNull { it !in neighborColors && it != parent.mapColor } ?: palette[id % palette.size]
        val flagColors = listOf(0xFFC62828L, 0xFF2E7D32L, 0xFF1565C0L, 0xFFF9A825L, 0xFFFFFFFFL, 0xFF212121L, 0xFF6A1B9AL)
        val c1 = sim.pick(flagColors); val c2 = sim.pick(flagColors.filter { it != c1 }); val c3 = sim.pick(flagColors.filter { it != c2 })
        val n = Nation(
            id = id, code = "N$id", name = name, mapColor = color, custom = true,
            flag = Flag(sim.randInt(6), listOf(c1, c2, c3, 0xFFFFFFFFL), 1 + sim.randInt(4)),
            regime = regime, personality = if (sim.chance(0.5)) Personality.OPPORTUNIST else Personality.TRADER,
            income = parent.income, capital = region.maxBy { game.provPop[it] },
            tax = parent.tax, shares = parent.shares.copyOf(), index = FloatArray(Area.entries.size) { parent.index[it] * 0.9f },
            tech = parent.tech.copyOf(),
        )
        n.stability = 45f; n.approval = 60f; n.corruption = parent.corruption
        for (g in Group.entries) n.groups[g.ordinal] = 60f
        n.nextElection = if (regime.elections) game.day + 365 * 2 else -1
        val gdp = region.sumOf { game.provGdp[it] }
        n.treasury = gdp * 0.03
        n.militaryFund = gdp * 0.01
        game.nations += n
        for (q in region) {
            game.owner[q] = id; game.controller[q] = id; game.core[q] = id
            game.siege[q] = 0f; game.resistance[q] = 0f
        }
        for (o in game.nations) if (o.id != id) game.setRel(id, o.id, game.rel(parent.id, o.id) * 0.5f)
        game.invalidate()
        game.treatyCache = null
        return n
    }
}

/** Organização das Nações Unidas: sessões com resoluções votadas por todos os países. */
class UnitedNations(private val sim: Sim) {
    private val game get() = sim.game
    val permanent = setOf("USA", "RUS", "CHN", "GBR", "FRA")
    val proposeCost = 15f

    fun isPermanent(n: Int) = sim.nation(n).code in permanent && sim.nation(n).alive

    fun monthly() {
        val un = game.un
        if (un.climateUntil in 0 until game.day) { un.climateUntil = -1; un.climateMembers.clear() }
        if (un.effects.removeAll { it.until < game.day || !sim.nation(it.target).alive }) sim.touch()
        if (un.pending != null || game.day < un.nextSession) return
        un.nextSession = game.day + 150 + sim.randInt(90)
        auto()?.let { open(it) }
    }

    fun has(type: ResolutionType, n: Int) = game.un.effects.any { it.type == type && it.target == n && it.until >= game.day }
    fun until(type: ResolutionType, n: Int) = game.un.effects.filter { it.type == type && it.target == n }.maxOfOrNull { it.until } ?: -1
    fun suspended(n: Int) = has(ResolutionType.SUSPEND, n)
    fun sanctionedBy(n: Int) = game.treaties.count { (it.type == TreatyType.SANCTION || it.type == TreatyType.EMBARGO) && it.b == n }
    private fun hasNukes(n: Nation) = n.nukes > 0 || n.nukeBuild > 0

    /** Escolhe o tema da sessão. */
    private fun auto(): Resolution? {
        val options = ArrayList<Resolution>()
        val proposer = game.nations.filter { it.alive && it.id != game.player && it.regime.democratic && !suspended(it.id) }
            .maxByOrNull { game.natGdp(it.id) }?.id ?: return null
        fun add(t: ResolutionType, target: Int, other: Int = -1) { options += Resolution(t, target, other, proposer, game.day) }
        // guerras de agressão viram sanção, embargo de armas, zona de exclusão ou missão de paz
        for (w in game.wars) {
            if (game.day - w.start < 60) continue
            val a = w.leaderA(); val d = w.leaderD()
            if (a == proposer || d == proposer) continue
            add(ResolutionType.PEACEKEEPING, a, d)
            if (sim.diplomacy.warScore(w, a) > 10f) add(ResolutionType.SANCTIONS, a)
            if (!has(ResolutionType.ARMS_EMBARGO, a)) add(ResolutionType.ARMS_EMBARGO, a)
            if (!has(ResolutionType.NO_FLY, a) && game.armies.any { it.owner == a && it.domain == Domain.AIR }) add(ResolutionType.NO_FLY, a)
            if (game.day - w.start > 365 && !suspended(a)) add(ResolutionType.SUSPEND, a)
        }
        // regime mais instável e repressivo é condenado (ou vai ao tribunal)
        game.nations.filter { it.alive && !it.regime.democratic && it.stability < 35f && it.id != proposer }
            .minByOrNull { it.stability }?.let {
                add(ResolutionType.CONDEMN, it.id)
                if (it.stability < 25f) add(ResolutionType.TRIBUNAL, it.id)
            }
        // ditaduras com bomba
        game.nations.firstOrNull { it.alive && it.id != proposer && !it.regime.democratic && hasNukes(it) && !has(ResolutionType.DISARM, it.id) && game.rel(proposer, it.id) < 0f }
            ?.let { add(ResolutionType.DISARM, it.id) }
        // país pobre com saúde ruim recebe ajuda
        game.nations.filter { it.alive && it.income <= 2 }.minByOrNull { it.index[Area.HEALTH.ordinal] }
            ?.let { add(ResolutionType.AID, it.id) }
        // país em paz que saiu destruído recebe fundo de reconstrução
        game.nations.filter { it.alive && !sim.atWarAny(it.id) && it.index[Area.INFRA.ordinal] < 40f && !has(ResolutionType.RECONSTRUCTION, it.id) }
            .minByOrNull { it.index[Area.INFRA.ordinal] }?.let { add(ResolutionType.RECONSTRUCTION, it.id) }
        // sanções antigas contra quem já está em paz podem ser suspensas
        game.nations.filter { it.alive && it.id != proposer && !sim.atWarAny(it.id) && sanctionedBy(it.id) >= 3 &&
            game.treaties.any { t -> t.type == TreatyType.SANCTION && t.b == it.id && game.day - t.start > 365 } }
            .maxByOrNull { sanctionedBy(it.id) }?.let { add(ResolutionType.LIFT_SANCTIONS, it.id) }
        if (game.un.climateUntil < 0) add(ResolutionType.CLIMATE, -1)
        return if (options.isEmpty()) null else sim.pick(options)
    }

    /** Abre a votação. Se o jogador não puder votar, resolve na hora. */
    fun open(r: Resolution) {
        game.un.pending = r
        if (!sim.player.alive || suspended(game.player)) resolve(0)
        else sim.feed("bloc", "ONU vota: ${title(r)}", 1)
        sim.touch()
    }

    /** Motivo para não poder propor (null = pode). */
    fun proposeRefusal(type: ResolutionType, target: Int, other: Int = -1): String? {
        val me = sim.player
        if (game.un.pending != null) return "Já há uma votação aberta"
        if (suspended(me.id)) return "Seu país está suspenso da ONU"
        if (me.politicalCapital < proposeCost) return "Precisa de ${proposeCost.toInt()} de capital político"
        if (type.hostile && target == me.id) return "Não dá para propor isso contra você mesmo"
        when (type) {
            ResolutionType.PEACEKEEPING -> if (sim.warBetween(target, other) == null) return "Esses países não estão em guerra"
            ResolutionType.CLIMATE -> if (game.un.climateUntil >= 0) return "Já há um acordo do clima em vigor"
            ResolutionType.NO_FLY -> if (!sim.atWarAny(target)) return "Só vale para país em guerra"
            ResolutionType.DISARM -> if (!hasNukes(sim.nation(target))) return "Esse país não tem armas nucleares"
            ResolutionType.LIFT_SANCTIONS -> if (sanctionedBy(target) == 0) return "Ninguém sanciona esse país"
            else -> if (target >= 0 && has(type, target)) return "Já está em vigor"
        }
        return null
    }

    /** O jogador propõe uma resolução (custa capital político). */
    fun propose(type: ResolutionType, target: Int, other: Int = -1): String? {
        proposeRefusal(type, target, other)?.let { return it }
        sim.player.politicalCapital -= proposeCost
        open(Resolution(type, target, other, game.player, game.day))
        return null
    }

    fun title(r: Resolution): String {
        val t = if (r.target >= 0) sim.nation(r.target).name else ""
        return when (r.type) {
            ResolutionType.SANCTIONS -> "sanções contra $t"
            ResolutionType.PEACEKEEPING -> "missão de paz entre $t e ${sim.nation(r.other).name}"
            ResolutionType.CONDEMN -> "condenar o governo de $t"
            ResolutionType.AID -> "ajuda humanitária para $t"
            ResolutionType.CLIMATE -> "acordo do clima (3 anos)"
            ResolutionType.LIFT_SANCTIONS -> "suspender as sanções contra $t"
            ResolutionType.ARMS_EMBARGO -> "embargo de armas contra $t"
            ResolutionType.NO_FLY -> "zona de exclusão aérea sobre $t"
            ResolutionType.DISARM -> "desarmamento nuclear de $t"
            ResolutionType.RECONSTRUCTION -> "fundo de reconstrução para $t"
            ResolutionType.SUSPEND -> "suspender $t da ONU"
            ResolutionType.TRIBUNAL -> "levar o governo de $t ao tribunal internacional"
        }
    }

    fun explain(r: Resolution): String = explain(r.type)

    fun explain(t: ResolutionType): String = when (t) {
        ResolutionType.SANCTIONS -> "Quem votar sim aplica sanções por 2 anos. Membros permanentes podem vetar."
        ResolutionType.PEACEKEEPING -> "Se aprovada, a guerra acaba em paz branca. Membros permanentes podem vetar."
        ResolutionType.CONDEMN -> "O mundo piora a relação com eles e o governo perde estabilidade."
        ResolutionType.AID -> "Quem votar sim paga uma pequena parte do PIB e ganha a simpatia deles."
        ResolutionType.CLIMATE -> "Quem votar sim melhora o meio ambiente, mas cresce um pouco menos por 3 anos."
        ResolutionType.LIFT_SANCTIONS -> "Todas as sanções contra o país acabam. Membros permanentes podem vetar."
        ResolutionType.ARMS_EMBARGO -> "Por 2 anos ninguém vende armas a eles e suas tropas custam 30% mais."
        ResolutionType.NO_FLY -> "Por 1 ano a aviação deles não pode atacar e perde parte dos aviões."
        ResolutionType.DISARM -> "Os mísseis nucleares deles são desmontados e ficam proibidos por 5 anos."
        ResolutionType.RECONSTRUCTION -> "Quem votar sim doa; o país ganha infraestrutura e cresce mais por 2 anos."
        ResolutionType.SUSPEND -> "Por 3 anos o país não vota nem propõe na ONU, e o mundo se afasta."
        ResolutionType.TRIBUNAL -> "O governo é julgado: estabilidade, aprovação e capital político despencam."
    }

    private fun vetoable(t: ResolutionType) = t.hostile || t == ResolutionType.PEACEKEEPING || t == ResolutionType.LIFT_SANCTIONS

    /** Voto de um país do computador: 1 sim, -1 não, 0 abstenção. */
    fun vote(n: Nation, r: Resolution): Int {
        val id = n.id
        if (suspended(id)) return 0
        val relP = if (r.proposer != id) game.rel(id, r.proposer) else 100f
        var lean = relP / 100f * 0.6f
        when {
            r.type.hostile -> {
                if (id == r.target) return -1
                val rt = game.rel(id, r.target)
                if (sim.allied(id, r.target)) return -1
                if (sim.atWar(id, r.target)) return 1
                lean += -rt / 50f
                if (n.regime.democratic && !sim.nation(r.target).regime.democratic) lean += 0.4f
                if (r.type == ResolutionType.DISARM && hasNukes(n)) lean -= 0.5f
                if (r.type == ResolutionType.NO_FLY || r.type == ResolutionType.ARMS_EMBARGO) lean += if (n.personality == Personality.PACIFIC) 0.3f else 0f
            }
            r.type == ResolutionType.PEACEKEEPING -> {
                val w = sim.warBetween(r.target, r.other) ?: return 0
                if (w.involves(id)) return if (sim.diplomacy.warScore(w, id) > 10f) -1 else 1
                for (side in listOf(r.target, r.other)) if (sim.allied(id, side)) {
                    return if (sim.diplomacy.warScore(w, side) > 10f) -1 else 1
                }
                lean += when (n.personality) { Personality.PACIFIC -> 0.8f; Personality.TRADER -> 0.5f; Personality.OPPORTUNIST -> 0f; Personality.EXPANSIONIST -> -0.4f }
            }
            r.type == ResolutionType.AID || r.type == ResolutionType.RECONSTRUCTION -> {
                if (id == r.target) return 1
                if (sim.atWar(id, r.target)) return -1
                lean += 0.3f + game.rel(id, r.target) / 100f + (n.income - 3) * 0.1f
            }
            r.type == ResolutionType.LIFT_SANCTIONS -> {
                if (id == r.target) return 1
                if (sim.atWar(id, r.target)) return -1
                lean += game.rel(id, r.target) / 60f - 0.1f
                if (game.treaties.any { it.type == TreatyType.SANCTION && it.a == id && it.b == r.target }) lean -= 0.3f
                if (sim.atWarAny(r.target)) lean -= 0.5f
                if (n.personality == Personality.TRADER) lean += 0.3f
            }
            r.type == ResolutionType.CLIMATE -> {
                val oil = sim.dev.balance(id, Resource.OIL)
                lean += (n.income - 3) * 0.2f + (if (n.personality == Personality.PACIFIC) 0.4f else 0f) - (if (oil > 20f) 0.8f else 0f)
            }
        }
        lean += (sim.randf() - 0.5f) * 0.5f
        return when { lean > 0.25f -> 1; lean < -0.25f -> -1; else -> 0 }
    }

    /** Conta os votos com o voto do jogador (1, -1 ou 0) e aplica o resultado. */
    fun resolve(playerVote: Int): ResolutionRecord? {
        val r = game.un.pending ?: return null
        game.un.pending = null
        var yes = 0; var no = 0; var abst = 0; var veto = -1
        val yesVoters = ArrayList<Int>()
        val pv = if (suspended(game.player)) 0 else playerVote
        for (n in game.nations) {
            if (!n.alive) continue
            val v = if (n.id == game.player) pv else vote(n, r)
            when (v) { 1 -> { yes++; yesVoters += n.id }; -1 -> no++; else -> abst++ }
            if (v == -1 && vetoable(r.type) && veto < 0 && isPermanent(n.id)) veto = n.id
            // quem vota contra o amigo esfria a relação
            if (n.id == game.player && r.target >= 0 && r.target != n.id) {
                if (r.type.hostile && v == 1) game.addRel(n.id, r.target, -10f)
                if ((r.type == ResolutionType.AID || r.type == ResolutionType.RECONSTRUCTION || r.type == ResolutionType.LIFT_SANCTIONS) && v == 1) game.addRel(n.id, r.target, 6f)
            }
        }
        val passed = veto < 0 && yes > no
        if (passed) apply(r, yesVoters)
        val rec = ResolutionRecord(r.type, r.target, r.other, r.proposer, game.day, yes, no, abst, veto, passed, pv)
        game.un.history.add(0, rec)
        while (game.un.history.size > 20) game.un.history.removeAt(game.un.history.lastIndex)
        if (r.proposer == game.player) {
            if (passed) sim.player.politicalCapital += 5f
        }
        val involves = r.proposer == game.player || r.target == game.player || r.other == game.player
        val text = "ONU: ${title(r)} " + when {
            veto >= 0 -> "vetada por ${sim.nation(veto).name}"
            passed -> "aprovada ($yes a $no)"
            else -> "rejeitada ($yes a $no)"
        }
        val goodForMe = if (r.target == game.player) !r.type.hostile else true
        sim.feed(if (passed) "bloc" else "block", text, if (involves) (if (passed == goodForMe) 1 else 3) else 0)
        sim.touch()
        return rec
    }

    private fun effect(t: ResolutionType, target: Int, days: Int) {
        game.un.effects.removeAll { it.type == t && it.target == target }
        game.un.effects += UnEffect(t, target, game.day + days)
    }

    internal fun apply(r: Resolution, yes: List<Int>) {
        when (r.type) {
            ResolutionType.SANCTIONS -> {
                for (v in yes) if (v != r.target && game.treaties.none { it.type == TreatyType.SANCTION && it.a == v && it.b == r.target })
                    game.treaties += Treaty(TreatyType.SANCTION, v, r.target, game.day, game.day + 365 * 2)
                game.treatyCache = null
                sim.nation(r.target).stability -= 5f
            }
            ResolutionType.PEACEKEEPING -> sim.warBetween(r.target, r.other)?.let { w ->
                sim.diplomacy.makePeace(w, w.leaderA(), PeaceTerms.WHITE)
            }
            ResolutionType.CONDEMN -> {
                val t = sim.nation(r.target)
                for (o in game.nations) if (o.alive && o.id != t.id) game.addRel(o.id, t.id, -8f)
                t.stability -= 4f
                for (g in Group.entries) t.groupMods[g.ordinal] -= 3f
            }
            ResolutionType.AID -> {
                val t = sim.nation(r.target)
                for (v in yes) if (v != t.id) {
                    val amount = game.natGdp(v) * 0.0005
                    val donor = sim.nation(v)
                    donor.treasury -= amount
                    if (donor.treasury < 0) { donor.debt -= donor.treasury; donor.treasury = 0.0 }
                    t.treasury += amount
                    game.addRel(v, t.id, 5f)
                }
                t.index[Area.HEALTH.ordinal] = min(100f, t.index[Area.HEALTH.ordinal] + 4f)
            }
            ResolutionType.CLIMATE -> {
                game.un.climateUntil = game.day + 365 * 3
                game.un.climateMembers.clear(); game.un.climateMembers += yes
                for (v in yes) sim.nation(v).index[Area.ENVIRONMENT.ordinal] = min(100f, sim.nation(v).index[Area.ENVIRONMENT.ordinal] + 5f)
            }
            ResolutionType.LIFT_SANCTIONS -> {
                val removed = game.treaties.count { (it.type == TreatyType.SANCTION || it.type == TreatyType.EMBARGO) && it.b == r.target }
                game.treaties.removeAll { (it.type == TreatyType.SANCTION || it.type == TreatyType.EMBARGO) && it.b == r.target }
                game.treatyCache = null
                sim.nation(r.target).stability += 3f
                if (r.target == game.player && removed > 0) sim.feed("handshake", "$removed sanções contra você foram retiradas", 1)
            }
            ResolutionType.ARMS_EMBARGO -> effect(r.type, r.target, 365 * 2)
            ResolutionType.NO_FLY -> {
                effect(r.type, r.target, 365)
                for (a in game.armies) if (a.owner == r.target && a.domain == Domain.AIR)
                    for (t in UnitType.of(Domain.AIR)) a.units[t.ordinal] *= 0.7f
            }
            ResolutionType.DISARM -> {
                val t = sim.nation(r.target)
                t.nukes = 0; t.nukeBuild = 0
                effect(r.type, r.target, 365 * 5)
                t.stability -= 3f
            }
            ResolutionType.RECONSTRUCTION -> {
                val t = sim.nation(r.target)
                for (v in yes) if (v != t.id) {
                    val amount = game.natGdp(v) * 0.0008
                    val donor = sim.nation(v)
                    donor.treasury -= amount
                    if (donor.treasury < 0) { donor.debt -= donor.treasury; donor.treasury = 0.0 }
                    t.treasury += amount
                    game.addRel(v, t.id, 4f)
                }
                t.index[Area.INFRA.ordinal] = min(100f, t.index[Area.INFRA.ordinal] + 6f)
                effect(r.type, r.target, 365 * 2)
            }
            ResolutionType.SUSPEND -> {
                effect(r.type, r.target, 365 * 3)
                for (o in game.nations) if (o.alive && o.id != r.target) game.addRel(o.id, r.target, -5f)
                sim.nation(r.target).stability -= 3f
            }
            ResolutionType.TRIBUNAL -> {
                val t = sim.nation(r.target)
                t.stability -= 8f; t.approval -= 6f; t.politicalCapital = 0f
                for (g in Group.entries) t.groupMods[g.ordinal] -= 4f
                for (o in game.nations) if (o.alive && o.id != t.id) game.addRel(o.id, t.id, -4f)
            }
        }
    }

    fun inClimateDeal(n: Int) = game.un.climateUntil >= game.day && n in game.un.climateMembers

    /** Previsão de votos (sem o jogador) para mostrar no cartão. */
    fun forecast(r: Resolution): Triple<Int, Int, Int> {
        var y = 0; var no = 0; var a = 0
        val saved = game.rngState
        for (n in game.nations) if (n.alive && n.id != game.player) when (vote(n, r)) { 1 -> y++; -1 -> no++; else -> a++ }
        game.rngState = saved
        return Triple(y, no, a)
    }

    fun vetoThreat(r: Resolution): Int? {
        if (!vetoable(r.type)) return null
        val saved = game.rngState
        val v = game.nations.firstOrNull { it.alive && it.id != game.player && isPermanent(it.id) && vote(it, r) == -1 }?.id
        game.rngState = saved
        return v
    }
}
