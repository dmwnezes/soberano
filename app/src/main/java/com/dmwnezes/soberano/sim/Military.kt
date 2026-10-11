package com.dmwnezes.soberano.sim

import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Military(private val sim: Sim) {
    private val game get() = sim.game
    private val world get() = sim.world

    /** Distância em km até cada vizinho, na mesma ordem de neighbors. */
    private val neighborKm: Array<FloatArray> = Array(world.provinces.size) { i ->
        val p = world.provinces[i]
        FloatArray(p.neighbors.size) { k -> max(30.0, world.distanceKm(i, p.neighbors[k])).toFloat() }
    }

    companion object {
        const val SEA_RANGE_KM = 3000.0
        const val SEA_SPEED = 400f
        const val AIR_SPEED = 2500f
        const val DAMAGE_SCALE = 0.35f
    }

    // ------------------------------------------------------------- caminhos

    fun legKm(a: Int, b: Int): Float {
        val p = world.provinces[a]
        val k = p.neighbors.indexOf(b)
        return if (k >= 0) neighborKm[a][k] else (world.distanceKm(a, b) * 1.15).toFloat()
    }

    fun isLandNeighbor(a: Int, b: Int) = world.provinces[a].neighbors.contains(b)

    fun transportCapacity(n: Int): Float =
        game.armies.filter { it.owner == n && it.domain == Domain.SEA }.sumOf { it.count(UnitType.TRANSPORT).toDouble() }.toFloat() * 4f

    /**
     * Caminho para um exército terrestre de [owner] de [from] até [to] (sem incluir [from]).
     * Com [allowSea], cruza o mar entre províncias costeiras (exige transportes).
     */
    fun landPath(owner: Int, from: Int, to: Int, allowSea: Boolean, alsoEnter: Int = -1): IntArray? {
        if (from == to) return IntArray(0)
        val ok = { p: Int -> sim.canEnter(owner, p) || (alsoEnter >= 0 && game.controller[p] == alsoEnter) }
        if (!ok(to)) return null
        val np = world.provinces.size
        val dist = FloatArray(np) { Float.MAX_VALUE }
        val prev = IntArray(np) { -1 }
        val pq = PriorityQueue<Long>(compareBy { java.lang.Float.intBitsToFloat((it ushr 32).toInt()) })
        fun push(d: Float, i: Int) = pq.add((java.lang.Float.floatToIntBits(d).toLong() shl 32) or i.toLong())
        dist[from] = 0f; push(0f, from)
        val coastal = world.coastalIds
        while (pq.isNotEmpty()) {
            val e = pq.poll()
            val d = java.lang.Float.intBitsToFloat((e ushr 32).toInt())
            val u = (e and 0xffffffffL).toInt()
            if (d > dist[u]) continue
            if (u == to) break
            val p = world.provinces[u]
            for (k in p.neighbors.indices) {
                val v = p.neighbors[k]
                if (v != to && !ok(v)) continue
                val nd = d + neighborKm[u][k]
                if (nd < dist[v]) { dist[v] = nd; prev[v] = u; push(nd, v) }
            }
            if (allowSea && p.coastal) {
                for (v in coastal) {
                    if (v == u || isLandNeighbor(u, v)) continue
                    if (v != to && !ok(v)) continue
                    val km = world.distanceKm(u, v)
                    if (km > SEA_RANGE_KM) continue
                    // mar é mais lento que estrada: peso equivalente a 25 km/dia
                    val nd = d + (km * 25.0 / SEA_SPEED).toFloat() + 60f
                    if (nd < dist[v]) { dist[v] = nd; prev[v] = u; push(nd, v) }
                }
            }
        }
        if (prev[to] == -1) return null
        val out = ArrayList<Int>()
        var c = to
        while (c != from) { out += c; c = prev[c] }
        out.reverse()
        return out.toIntArray()
    }

    fun armySpeed(a: Army): Float {
        return when (a.domain) {
            Domain.LAND -> {
                var s = Float.MAX_VALUE
                for (t in UnitType.of(Domain.LAND)) if (a.units[t.ordinal] > 0.05f) s = min(s, t.speed)
                if (s == Float.MAX_VALUE) s = 25f
                val n = sim.nation(a.owner)
                val infra = 0.75f + n.index[Area.INFRA.ordinal] / 200f
                val enemy = if (sim.atWar(a.owner, game.controller[a.province])) 0.8f else 1f
                s * infra * enemy
            }
            Domain.SEA -> {
                var s = Float.MAX_VALUE
                for (t in UnitType.of(Domain.SEA)) if (a.units[t.ordinal] > 0.05f) s = min(s, t.speed)
                if (s == Float.MAX_VALUE) 500f else s
            }
            Domain.AIR -> AIR_SPEED
        }
    }

    /** Ordem de movimento. Retorna motivo da recusa ou null. */
    fun order(a: Army, target: Int): String? {
        if (a.battle && a.domain == Domain.LAND) return "Em combate: só pode recuar para província vizinha amiga"
        when (a.domain) {
            Domain.LAND -> {
                if (!sim.canEnter(a.owner, target)) return "Sem acesso: precisa de guerra, aliança ou passagem"
                var path = landPath(a.owner, a.province, target, false)
                if (path == null) {
                    path = landPath(a.owner, a.province, target, true)
                    if (path == null) return "Sem caminho por terra ou mar"
                    if (transportCapacity(a.owner) < a.divisions()) return "Faltam transportes para cruzar o mar"
                }
                setPath(a, path)
            }
            Domain.SEA -> {
                if (!world.provinces[target].coastal) return "Frotas só vão para províncias costeiras"
                val km = world.distanceKm(a.province, target)
                if (km > 15000) return "Longe demais"
                if (!sim.canEnter(a.owner, target)) return "Sem acesso a esse porto"
                setPath(a, intArrayOf(target))
            }
            Domain.AIR -> {
                val c = game.controller[target]
                if (c != a.owner && !sim.allied(a.owner, c)) return "Aviões só pousam em bases próprias ou aliadas"
                setPath(a, intArrayOf(target))
            }
        }
        sim.touch()
        return null
    }

    private fun setPath(a: Army, path: IntArray) {
        a.path = path
        a.progress = 0f
        a.legKm = if (path.isEmpty()) 0f else legKm(a.province, path[0])
    }

    /** Caminho e dias até [target], para mostrar antes de confirmar (null = sem caminho). */
    fun preview(a: Army, target: Int): Pair<IntArray, Int>? {
        val path: IntArray = when (a.domain) {
            Domain.LAND -> {
                val ctrl = game.controller[target]
                val extra = if (!sim.canEnter(a.owner, target)) ctrl else -1
                landPath(a.owner, a.province, target, false, extra) ?: landPath(a.owner, a.province, target, true, extra) ?: return null
            }
            Domain.SEA -> if (world.provinces[target].coastal) intArrayOf(target) else return null
            Domain.AIR -> intArrayOf(target)
        }
        var km = 0.0; var prev = a.province
        for (q in path) {
            val sea = a.domain == Domain.LAND && !isLandNeighbor(prev, q)
            km += legKm(prev, q) / (if (sea) SEA_SPEED else armySpeed(a))
            prev = q
        }
        return path to kotlin.math.ceil(km).toInt().coerceAtLeast(1)
    }

    /** Força terrestre inimiga de [owner] parada em [p]. */
    fun enemyPowerAt(owner: Int, p: Int): Float = game.armies.filter { it.province == p && it.domain == Domain.LAND && (sim.atWar(owner, it.owner) || it.owner == game.controller[p] && it.owner != owner && !sim.allied(owner, it.owner)) }
        .sumOf { a -> UnitType.of(Domain.LAND).sumOf { (a.units[it.ordinal] * (it.attack + it.defense) * a.morale).toDouble() } }.toFloat()

    fun landStrength(a: Army): Float = UnitType.of(Domain.LAND).sumOf { (a.units[it.ordinal] * (it.attack + it.defense) * a.morale).toDouble() }.toFloat() *
        techMult(a.owner, Domain.LAND)

    fun stop(a: Army) { a.path = IntArray(0); a.progress = 0f; sim.touch() }

    // ------------------------------------------------------------- dia: movimento

    fun dailyMovement() {
        for (a in game.armies) {
            if (a.path.isEmpty()) continue
            if (a.battle && a.domain == Domain.LAND) continue
            val next = a.path[0]
            val seaLeg = a.domain == Domain.LAND && !isLandNeighbor(a.province, next)
            val speed = if (seaLeg) SEA_SPEED else armySpeed(a)
            a.progress += speed
            if (a.progress < a.legKm) continue
            // chegou ao próximo ponto
            if (a.domain == Domain.LAND && !sim.canEnter(a.owner, next)) { stop(a); continue }
            if (seaLeg && !landingAllowed(a.owner, next)) {
                stop(a)
                if (a.owner == game.player) sim.feed("ship", "Desembarque bloqueado por frota inimiga", 2, next)
                continue
            }
            a.province = next
            a.path = a.path.copyOfRange(1, a.path.size)
            a.progress = 0f
            a.legKm = if (a.path.isEmpty()) 0f else legKm(a.province, a.path[0])
            // ao entrar onde há inimigos, a batalha segura o exército (a ordem continua depois)
        }
    }

    private fun landingAllowed(owner: Int, p: Int): Boolean {
        val enemy = game.armies.filter { it.domain == Domain.SEA && it.province == p && sim.atWar(it.owner, owner) }
            .sumOf { navalPower(it).toDouble() }
        if (enemy <= 0) return true
        val mine = game.armies.filter { it.domain == Domain.SEA && it.owner == owner }.sumOf { navalPower(it).toDouble() }
        return mine > enemy
    }

    fun navalPower(a: Army) = UnitType.of(Domain.SEA).sumOf { (a.units[it.ordinal] * (it.antiShip + it.defense)).toDouble() }.toFloat()

    fun enemiesAt(owner: Int, p: Int, d: Domain) = game.armies.any { it.province == p && it.domain == d && it.alive() && sim.atWar(owner, it.owner) }

    // ------------------------------------------------------------- dia: combate

    private fun techMult(n: Int, d: Domain): Float {
        val b = when (d) { Domain.LAND -> TechBranch.LAND; Domain.SEA -> TechBranch.SEA; Domain.AIR -> TechBranch.AIR }
        return 1f + 0.05f * sim.nation(n).tech[b.ordinal]
    }

    fun dailyCombat() {
        for (a in game.armies) a.battle = false
        val byProvince = HashMap<Int, MutableList<Army>>()
        for (a in game.armies) if (a.alive() && a.domain != Domain.AIR) byProvince.getOrPut(a.province) { mutableListOf() } += a
        for ((p, list) in byProvince) {
            val land = list.filter { it.domain == Domain.LAND }
            val sea = list.filter { it.domain == Domain.SEA }
            if (sea.size > 1) navalBattle(p, sea)
            if (land.size > 1 || (land.isNotEmpty())) landBattle(p, land, sea)
        }
        game.armies.removeAll { !it.alive() }
        game.battles.removeAll { it.lastDay < game.day }
        // recuperação de moral
        for (a in game.armies) if (!a.battle) {
            val friendly = !sim.atWar(a.owner, game.controller[a.province])
            a.morale = min(1f, a.morale + if (friendly) 0.025f else 0.012f)
        }
    }

    private fun sides(p: Int, armies: List<Army>): Pair<List<Army>, List<Army>> {
        val ctrl = game.controller[p]
        val def = armies.filter { !sim.atWar(it.owner, ctrl) }
        val att = armies.filter { sim.atWar(it.owner, ctrl) }
        return def to att
    }

    private fun landBattle(p: Int, land: List<Army>, sea: List<Army>) {
        var (def, att) = sides(p, land)
        if (def.isEmpty() || att.isEmpty()) {
            // dois invasores inimigos entre si numa província de terceiro
            if (att.size > 1) {
                val first = att[0].owner
                val a2 = att.filter { sim.atWar(it.owner, first) }
                if (a2.isEmpty()) return
                def = att.filter { !sim.atWar(it.owner, first) }; att = a2
            } else return
        }
        val defOwner = def.maxBy { it.total() }.owner
        val attOwner = att.maxBy { it.total() }.owner
        // apoio aéreo
        val airDef = airSupport(p, defOwner, attOwner)
        val airAtt = airSupport(p, attOwner, defOwner)
        val aaDef = def.sumOf { (it.count(UnitType.ANTIAIR) * UnitType.ANTIAIR.antiAir).toDouble() }.toFloat() + airDef.second
        val aaAtt = att.sumOf { (it.count(UnitType.ANTIAIR) * UnitType.ANTIAIR.antiAir).toDouble() }.toFloat() + airAtt.second
        val supDef = if (aaDef > aaAtt * 1.2f) 1.25f else 1f
        val supAtt = if (aaAtt > aaDef * 1.2f) 1.25f else 1f
        val airDmgDef = airDef.first * (if (aaAtt > aaDef) 0.5f else 1f)
        val airDmgAtt = airAtt.first * (if (aaDef > aaAtt) 0.5f else 1f)
        // bombardeio naval em província costeira
        val navDef = sea.filter { it.owner == defOwner || !sim.atWar(it.owner, defOwner) && sim.atWar(it.owner, attOwner) }.sumOf { navalBombard(it).toDouble() }.toFloat()
        val navAtt = sea.filter { it.owner == attOwner || !sim.atWar(it.owner, attOwner) && sim.atWar(it.owner, defOwner) }.sumOf { navalBombard(it).toDouble() }.toFloat()

        val defAttack = def.sumOf { groundAttack(it).toDouble() }.toFloat() * supDef + airDmgDef + navDef
        val attAttack = att.sumOf { groundAttack(it).toDouble() }.toFloat() * supAtt + airDmgAtt + navAtt
        val dmgToAtt = defAttack * 1.2f * DAMAGE_SCALE * (0.85f + sim.randf() * 0.3f)
        val dmgToDef = attAttack * DAMAGE_SCALE * (0.85f + sim.randf() * 0.3f)

        val hpAtt = att.sumOf { hp(it).toDouble() }.toFloat().coerceAtLeast(0.1f)
        val hpDef = def.sumOf { hp(it).toDouble() }.toFloat().coerceAtLeast(0.1f)
        applyDamage(att, dmgToAtt, hpAtt)
        applyDamage(def, dmgToDef, hpDef)
        for (a in att + def) a.battle = true

        // registro para a interface
        val bt = game.battles.firstOrNull { it.province == p }
        val powA = att.sumOf { hp(it).toDouble() }.toFloat()
        val powD = def.sumOf { hp(it).toDouble() }.toFloat()
        if (bt == null) {
            game.battles += Battle(p, game.day, powA, powD, attOwner, defOwner, game.day)
            if (sim.involvesPlayer(attOwner, defOwner)) sim.feed("battle", "Batalha em ${world.provinces[p].name}", 2, p)
        } else { bt.attackerPower = powA; bt.defenderPower = powD; bt.lastDay = game.day; bt.attacker = attOwner; bt.defender = defOwner }

        // recuo ou rendição
        val attBroken = att.all { it.morale < 0.2f || !it.alive() }
        val defBroken = def.all { it.morale < 0.2f || !it.alive() }
        if (attBroken || defBroken) {
            val losers = if (attBroken) att else def
            val winnerOwner = if (attBroken) defOwner else attOwner
            for (a in losers) retreat(a)
            sim.warBetween(attOwner, defOwner)?.let { w ->
                if (w.side(winnerOwner) == 1) w.battlesA++ else w.battlesD++
            }
            if (sim.involvesPlayer(attOwner, defOwner)) {
                val won = winnerOwner == game.player || sim.allied(winnerOwner, game.player)
                sim.feed(if (won) "victory" else "defeat",
                    (if (won) "Vitória em " else "Derrota em ") + world.provinces[p].name, if (won) 1 else 3, p)
            }
        }
    }

    private fun groundAttack(a: Army): Float {
        var s = 0f
        for (t in UnitType.of(Domain.LAND)) s += a.units[t.ordinal] * t.attack
        return s * a.morale * techMult(a.owner, Domain.LAND)
    }

    private fun navalBombard(a: Army): Float =
        (a.count(UnitType.DESTROYER) * 1f + a.count(UnitType.CARRIER) * 3f) * a.morale * techMult(a.owner, Domain.SEA)

    private fun hp(a: Army): Float {
        var s = 0f
        for (t in UnitType.entries) if (t.domain == a.domain) s += a.units[t.ordinal] * t.hp
        return s
    }

    /** (dano em terra, poder antiaéreo) das alas aéreas de [owner] que alcançam [p]. */
    private fun airSupport(p: Int, owner: Int, enemy: Int): Pair<Float, Float> {
        var dmg = 0f; var aa = 0f
        for (w in game.armies) {
            if (w.domain != Domain.AIR || (w.owner != owner && !(sim.allied(w.owner, owner) && sim.atWar(w.owner, enemy)))) continue
            if (w.moving) continue
            val km = world.distanceKm(w.province, p)
            val tm = techMult(w.owner, Domain.AIR)
            var used = false
            for (t in UnitType.of(Domain.AIR)) {
                val c = w.units[t.ordinal]
                if (c <= 0f || km > t.speed) continue
                dmg += c * t.attack * tm * w.morale
                aa += c * t.antiAir * tm
                used = true
            }
            if (used) {
                // desgaste pela antiaérea inimiga (aproximado)
                for (t in UnitType.of(Domain.AIR)) w.units[t.ordinal] = max(0f, w.units[t.ordinal] * (1f - 0.004f))
            }
        }
        return dmg to aa
    }

    private fun applyDamage(armies: List<Army>, dmg: Float, totalHp: Float) {
        if (dmg <= 0f) return
        for (a in armies) {
            val myHp = hp(a)
            if (myHp <= 0f) continue
            val share = dmg * myHp / totalHp
            var lost = 0f
            for (t in UnitType.entries) if (t.domain == a.domain) {
                val c = a.units[t.ordinal]
                if (c <= 0f) continue
                val tHp = c * t.hp
                val dmgT = share * tHp / myHp
                val units = min(c, dmgT / t.hp)
                a.units[t.ordinal] = c - units
                if (a.units[t.ordinal] < 0.05f) a.units[t.ordinal] = 0f
                lost += units
            }
            a.morale = max(0f, a.morale - share / myHp * 2.5f)
            sim.nation(a.owner).casualtiesMonth += lost
        }
    }

    private fun retreat(a: Army) {
        if (!a.alive()) return
        a.path = IntArray(0); a.progress = 0f
        val p = world.provinces[a.province]
        val options = p.neighbors.filter { q ->
            val c = game.controller[q]
            (c == a.owner || sim.allied(c, a.owner)) && !enemiesAt(a.owner, q, a.domain)
        }
        if (a.domain == Domain.SEA) {
            val home = game.owner.indices.filter { game.controller[it] == a.owner && world.provinces[it].coastal }
            if (home.isEmpty()) { a.units.fill(0f); return }
            a.province = home.minBy { world.distanceKm(a.province, it) }
            a.morale = max(a.morale, 0.25f)
            return
        }
        if (options.isEmpty()) {
            // cercado: rendição
            if (a.owner == game.player) sim.feed("defeat", "${a.name.ifEmpty { "Exército" }} se rendeu cercado", 3, a.province)
            sim.nation(a.owner).casualtiesMonth += a.total()
            a.units.fill(0f)
            return
        }
        a.province = options.maxBy { game.controller[it] == a.owner }
        a.morale = max(a.morale, 0.25f)
    }

    private fun navalBattle(p: Int, fleets: List<Army>) {
        val first = fleets[0].owner
        val enemies = fleets.filter { sim.atWar(it.owner, first) }
        if (enemies.isEmpty()) return
        val friends = fleets.filter { !sim.atWar(it.owner, first) }
        fun power(list: List<Army>) = list.sumOf { f ->
            UnitType.of(Domain.SEA).sumOf { t -> (f.units[t.ordinal] * t.antiShip * f.morale * techMult(f.owner, Domain.SEA)).toDouble() }
        }.toFloat()
        val dmgToEnemies = power(friends) * DAMAGE_SCALE * (0.85f + sim.randf() * 0.3f)
        val dmgToFriends = power(enemies) * DAMAGE_SCALE * (0.85f + sim.randf() * 0.3f)
        val hpF = friends.sumOf { hp(it).toDouble() }.toFloat().coerceAtLeast(0.1f)
        val hpE = enemies.sumOf { hp(it).toDouble() }.toFloat().coerceAtLeast(0.1f)
        applyDamage(enemies, dmgToEnemies, hpE)
        applyDamage(friends, dmgToFriends, hpF)
        for (f in friends + enemies) f.battle = true
        if (sim.involvesPlayer(*(friends + enemies).map { it.owner }.toIntArray()) && game.battles.none { it.province == p })
            sim.feed("ship", "Batalha naval perto de ${world.provinces[p].name}", 2, p)
        val bt = game.battles.firstOrNull { it.province == p }
        if (bt == null) game.battles += Battle(p, game.day, hpE, hpF, enemies[0].owner, first, game.day) else bt.lastDay = game.day
        for (f in friends + enemies) if (f.morale < 0.2f) retreat(f)
    }

    // ------------------------------------------------------------- dia: cerco

    fun siegeDays(p: Int): Float = (5.0 + game.provPop[p] / 2_000_000.0).coerceIn(5.0, 30.0).toFloat()

    fun dailySiege() {
        val byProvince = HashMap<Int, MutableList<Army>>()
        for (a in game.armies) if (a.domain == Domain.LAND && a.alive()) byProvince.getOrPut(a.province) { mutableListOf() } += a
        var changed = false
        for (p in game.siege.indices) {
            val list = byProvince[p]
            val ctrl = game.controller[p]
            if (list == null) { if (game.siege[p] > 0f) game.siege[p] = max(0f, game.siege[p] - 0.1f); continue }
            val attackers = list.filter { sim.atWar(it.owner, ctrl) }
            val defenders = list.filter { !sim.atWar(it.owner, ctrl) }
            if (attackers.isEmpty() || defenders.isNotEmpty()) {
                if (game.siege[p] > 0f) game.siege[p] = max(0f, game.siege[p] - 0.1f)
                continue
            }
            val divs = attackers.sumOf { it.divisions().toDouble() }.toFloat()
            game.siege[p] += min(2f, divs / 2f) / siegeDays(p)
            if (game.siege[p] >= 1f) {
                game.siege[p] = 0f
                val taker = attackers.maxBy { it.total() }.owner
                val owner = game.owner[p]
                // libertação: volta para o dono se o conquistador é aliado dele
                val newCtrl = if (owner == taker || sim.allied(owner, taker) && !sim.atWar(owner, taker)) owner else taker
                game.controller[p] = newCtrl
                changed = true
                val involvesPlayer = sim.involvesPlayer(taker, ctrl, owner)
                if (involvesPlayer) {
                    val mine = taker == game.player || sim.allied(taker, game.player)
                    sim.feed(if (mine) "flag" else "alert",
                        (if (mine) "Tomamos " else "Perdemos ") + world.provinces[p].name, if (mine) 1 else 3, p)
                }
                if (p == sim.nation(owner).capital && newCtrl != owner)
                    sim.feed("flag", "Capital de ${sim.nation(owner).name} caiu", if (involvesPlayer) 3 else 2, p)
            }
        }
        if (changed) game.invalidate()
    }

    // ------------------------------------------------------------- produção

    fun unitCost(n: Nation, t: UnitType): Double {
        var c = t.cost
        if (t == UnitType.INFANTRY && "servico_militar" in n.laws) c *= 0.85
        var discount = sim.dev.builtDiscount(n)
        if (n.id == game.player) discount += sim.dev.defenseDiscount()
        return c * (1.0 - discount.coerceIn(-0.2f, 0.4f))
    }

    fun productionSlots(n: Nation): Int = (1 + game.natGdp(n.id) / 400.0).toInt().coerceIn(1, 30)

    fun canBuildAt(n: Nation, t: UnitType, p: Int): String? {
        if (game.owner[p] != n.id || game.controller[p] != n.id) return "Precisa ser uma província sua e livre"
        if (t.domain == Domain.SEA && !world.provinces[p].coastal) return "Navios precisam de porto (costa)"
        return null
    }

    fun buy(n: Nation, t: UnitType, p: Int, qty: Int = 1): String? {
        canBuildAt(n, t, p)?.let { return it }
        val cost = unitCost(n, t) * qty
        if (n.militaryFund + n.treasury < cost) return "Dinheiro insuficiente"
        val fromFund = min(cost, max(0.0, n.militaryFund))
        n.militaryFund -= fromFund
        n.treasury -= cost - fromFund
        val days = (t.buildDays * (1f - 0.04f * (n.tech[techBranchFor(t).ordinal] - 1))).toInt().coerceAtLeast(7)
        repeat(qty) { n.queue += BuildOrder(t, p, days, days) }
        sim.touch()
        return null
    }

    fun techBranchFor(t: UnitType) = when (t.domain) { Domain.LAND -> TechBranch.LAND; Domain.SEA -> TechBranch.SEA; Domain.AIR -> TechBranch.AIR }

    fun cancelOrder(n: Nation, o: BuildOrder) {
        if (n.queue.remove(o)) n.militaryFund += unitCost(n, o.type) * 0.5
        sim.touch()
    }

    fun buildNuke(n: Nation): String? {
        if (!game.settings.nuclear) return "Armas nucleares estão desligadas"
        if (n.tech[TechBranch.NUCLEAR.ordinal] < 6) return "Precisa de tecnologia nuclear nível 6"
        if (n.nukeBuild > 0) return "Já há um míssil em produção"
        if (n.militaryFund + n.treasury < NUKE_COST) return "Dinheiro insuficiente"
        val fromFund = min(NUKE_COST, max(0.0, n.militaryFund))
        n.militaryFund -= fromFund; n.treasury -= NUKE_COST - fromFund
        n.nukeBuild = NUKE_DAYS
        for (o in game.nations) if (o.alive && o.id != n.id) game.addRel(n.id, o.id, -4f)
        sim.touch()
        return null
    }

    fun dailyProduction() {
        for (n in game.nations) {
            if (!n.alive) continue
            if (n.nukeBuild > 0) {
                n.nukeBuild--
                if (n.nukeBuild == 0) { n.nukes++; if (n.id == game.player) sim.feed("nuke", "Míssil nuclear pronto", 1) }
            }
            if (n.queue.isEmpty()) continue
            val slots = productionSlots(n)
            var i = 0
            val done = mutableListOf<BuildOrder>()
            for (o in n.queue) {
                if (i >= slots) break
                o.daysLeft--
                if (o.daysLeft <= 0) done += o
                i++
            }
            for (o in done) {
                n.queue.remove(o)
                deliver(n, o)
            }
        }
    }

    private fun deliver(n: Nation, o: BuildOrder) {
        var p = o.province
        if (game.controller[p] != n.id) {
            p = sim.provincesOf(n.id).firstOrNull { game.controller[it] == n.id && (o.type.domain != Domain.SEA || world.provinces[it].coastal) } ?: return
        }
        val a = game.armies.firstOrNull { it.owner == n.id && it.province == p && it.domain == o.type.domain && !it.moving && !it.battle }
            ?: newArmy(n.id, p, o.type.domain)
        a.units[o.type.ordinal] += 1f
        if (n.id == game.player && n.queue.none { it.type == o.type && it.province == o.province })
            sim.feed("factory", "${o.type.label} pronta em ${world.provinces[p].name}", 1, p)
    }

    fun newArmy(owner: Int, p: Int, d: Domain): Army {
        val count = game.armies.count { it.owner == owner && it.domain == d } + 1
        val name = when (d) { Domain.LAND -> "${count}º Exército"; Domain.SEA -> "${count}ª Frota"; Domain.AIR -> "${count}ª Ala Aérea" }
        val a = Army(game.nextArmyId++, owner, p, d, name = name)
        game.armies += a
        return a
    }

    // ------------------------------------------------------------- organizar tropas

    fun split(a: Army): Army? {
        if (a.total() < 2f) return null
        val b = newArmy(a.owner, a.province, a.domain)
        for (t in UnitType.entries) {
            val half = (a.units[t.ordinal] / 2f).let { (it * 2).toInt() / 2f }
            a.units[t.ordinal] -= half; b.units[t.ordinal] += half
        }
        b.morale = a.morale
        sim.touch()
        return b
    }

    fun merge(into: Army, other: Army): Boolean {
        if (into.id == other.id || into.owner != other.owner || into.province != other.province || into.domain != other.domain) return false
        val ti = into.total(); val to = other.total()
        for (t in UnitType.entries) into.units[t.ordinal] += other.units[t.ordinal]
        into.morale = (into.morale * ti + other.morale * to) / max(0.1f, ti + to)
        game.armies.remove(other)
        sim.touch()
        return true
    }

    fun mergeAllAt(a: Army) {
        for (o in game.armies.filter { it.owner == a.owner && it.province == a.province && it.domain == a.domain && it.id != a.id }) merge(a, o)
    }

    fun disband(a: Army) { game.armies.remove(a); sim.touch() }

    /** Manda todos os exércitos terrestres parados de [owner] para [p]. Retorna quantos foram. */
    fun gatherAt(owner: Int, p: Int): Int {
        var n = 0
        for (a in game.armies.toList()) {
            if (a.owner != owner || a.domain != Domain.LAND || a.battle || a.province == p) continue
            if (order(a, p) == null) n++
        }
        sim.touch()
        return n
    }

    /** Províncias que recebem tropas na divisão igual (fronteira primeiro, depois as mais populosas). */
    fun distributionTargets(owner: Int, borderOnly: Boolean): List<Int> {
        val mine = game.owner.indices.filter { game.owner[it] == owner && game.controller[it] == owner }
        val border = mine.filter { p -> world.provinces[p].neighbors.any { game.owner[it] != owner } }
        val pool = if (borderOnly) border else border.sortedByDescending { game.provPop[it] } + (mine - border.toSet()).sortedByDescending { game.provPop[it] }
        // só onde dá para chegar por terra a partir da maior tropa parada
        val from = game.armies.filter { it.owner == owner && it.domain == Domain.LAND && !it.battle }.maxByOrNull { it.divisions() }?.province ?: return emptyList()
        return pool.filter { it == from || landPath(owner, from, it, false) != null }
    }

    /**
     * Divide igualmente as tropas terrestres paradas entre as províncias: com 100 divisões e 10 províncias,
     * 10 em cada. Cada destacamento marcha da tropa mais próxima. Retorna (divisões, províncias).
     */
    fun distributeEvenly(owner: Int, borderOnly: Boolean): Pair<Int, Int> {
        val sources = game.armies.filter { it.owner == owner && it.domain == Domain.LAND && !it.battle && !it.moving }.toMutableList()
        val targets = distributionTargets(owner, borderOnly)
        if (sources.isEmpty() || targets.isEmpty()) return 0 to 0
        val landTypes = UnitType.of(Domain.LAND)
        // unidades inteiras por tipo (sobra fracionada fica na tropa de origem)
        val totals = IntArray(UnitType.count)
        for (t in landTypes) totals[t.ordinal] = sources.sumOf { it.units[t.ordinal].toDouble() }.toInt()
        val totalDiv = landTypes.sumOf { totals[it.ordinal] }
        if (totalDiv == 0) return 0 to 0
        val n = minOf(targets.size, totalDiv)
        val dests = targets.take(n)
        // cota de cada província por tipo: divisão inteira, sobras para as primeiras da lista
        val quota = Array(n) { IntArray(UnitType.count) }
        var cursor = 0
        for (t in landTypes) {
            val tot = totals[t.ordinal]
            val base = tot / n
            var rest = tot % n
            for (i in 0 until n) quota[i][t.ordinal] = base
            // as sobras giram pelas províncias para não lotar sempre as mesmas
            while (rest > 0) { quota[cursor % n][t.ordinal]++; cursor++; rest-- }
        }
        for ((i, dest) in dests.withIndex()) {
            for (t in landTypes) {
                var need = quota[i][t.ordinal].toFloat()
                // tira das tropas mais próximas do destino
                for (src in sources.filter { it.units[t.ordinal] >= 1f }.sortedBy { world.distanceKm(it.province, dest) }) {
                    if (need <= 0f) break
                    val take = minOf(need, kotlin.math.floor(src.units[t.ordinal]))
                    if (take <= 0f) continue
                    src.units[t.ordinal] -= take
                    need -= take
                    val det = game.armies.firstOrNull { it.owner == owner && it.province == src.province && it.domain == Domain.LAND && it.target == dest && it !in sources }
                        ?: newArmy(owner, src.province, Domain.LAND).also { it.target = dest; it.morale = src.morale }
                    det.units[t.ordinal] += take
                }
            }
        }
        // destacamentos marcham; os que já estão no destino ficam
        for (a in game.armies.toList()) if (a.owner == owner && a.target >= 0 && a.domain == Domain.LAND) {
            val dest = a.target
            a.target = -1
            if (a.province != dest) order(a, dest)
        }
        game.armies.removeAll { it.owner == owner && it.domain == Domain.LAND && !it.alive() }
        sim.touch()
        return totalDiv to n
    }

    /** Junta exércitos parados do mesmo dono na mesma província (reunião automática do jogador). */
    fun dailyAutoMerge() {
        if (!game.autoMerge) return
        val me = game.player
        val idle = game.armies.filter { it.owner == me && !it.moving && !it.battle }
        val groups = idle.groupBy { it.province to it.domain }
        for ((_, list) in groups) if (list.size > 1) {
            val main = list.maxBy { it.total() }
            for (o in list) if (o !== main) merge(main, o)
        }
    }

    // ------------------------------------------------------------- resistência

    fun garrison(p: Int, owner: Int) = game.armies.filter { it.province == p && it.owner == owner && it.domain == Domain.LAND }
        .sumOf { it.divisions().toDouble() }.toFloat()

    fun monthlyResistance() {
        for (p in game.resistance.indices) {
            val ctrl = game.controller[p]
            val core = game.core[p]
            if (ctrl == core) { game.resistance[p] = max(0f, game.resistance[p] - 5f); continue }
            val n = sim.nation(ctrl)
            val occupied = ctrl != game.owner[p]
            var d = 3f + (if (occupied) 4f else 0f) + (100f - n.stability) / 40f
            d -= garrison(p, ctrl) * 4f + n.index[Area.SECURITY.ordinal] / 25f
            if (!occupied && game.day - 0 > 0 && !sim.nation(core).alive) d -= 2f
            game.resistance[p] = (game.resistance[p] + d).coerceIn(0f, 100f)
            val r = game.resistance[p]
            if (r > 60f) {
                for (a in game.armies) if (a.province == p && a.owner == ctrl && a.domain == Domain.LAND) {
                    for (t in UnitType.of(Domain.LAND)) a.units[t.ordinal] *= 0.97f
                    sim.nation(ctrl).casualtiesMonth += a.total() * 0.03f
                }
                if (ctrl == game.player && sim.chance(0.3)) sim.feed("bomb", "Guerrilha ataca em ${world.provinces[p].name}", 2, p)
            }
            if (r > 90f) revolt(p)
        }
    }

    private fun revolt(p: Int) {
        val ctrl = game.controller[p]
        val owner = game.owner[p]
        val core = game.core[p]
        game.resistance[p] = 40f
        if (ctrl != owner && sim.nation(owner).alive) {
            game.controller[p] = owner
        } else if (sim.nation(core).alive) {
            sim.diplomacy.transfer(p, core)
        } else {
            sim.nation(ctrl).stability -= 5f
            return
        }
        // tropas do antigo controlador saem
        for (a in game.armies) if (a.province == p && a.owner == ctrl) {
            val home = sim.nation(ctrl).capital
            a.province = if (game.controller[home] == ctrl) home else a.province
            a.path = IntArray(0)
        }
        game.invalidate()
        if (sim.involvesPlayer(ctrl, owner, core)) sim.feed("protest", "Revolta em ${world.provinces[p].name}", if (ctrl == game.player) 3 else 1, p)
    }

    // ------------------------------------------------------------- nuclear

    fun nuke(from: Int, p: Int, retaliation: Boolean = false): String? {
        val n = sim.nation(from)
        if (!game.settings.nuclear) return "Armas nucleares estão desligadas"
        if (n.nukes <= 0) return "Sem mísseis nucleares"
        val target = game.controller[p]
        if (target == from) return "Alvo é seu"
        n.nukes--
        val lost = 0.15 + sim.rand() * 0.25
        game.provPop[p] *= (1 - lost)
        game.provGdp[p] *= 0.5
        for (a in game.armies) if (a.province == p) for (t in UnitType.entries) a.units[t.ordinal] *= 0.2f
        game.armies.removeAll { !it.alive() }
        game.resistance[p] = 100f
        val victim = sim.nation(target)
        victim.stability -= 20f
        for (o in game.nations) if (o.alive && o.id != from) game.addRel(from, o.id, -60f)
        n.stability -= 15f
        game.invalidate()
        sim.feed("nuke", "Ataque nuclear de ${n.name} em ${world.provinces[p].name}", 3, p)
        if (!sim.atWar(from, target)) sim.diplomacy.declareWar(from, target)
        // revide
        if (!retaliation && victim.nukes > 0 && target != game.player && game.settings.nuclear) {
            val cap = n.capital
            nuke(target, cap, retaliation = true)
        }
        sim.touch()
        return null
    }
}

private fun sqr(x: Float) = x * x
private fun hyp(a: Float, b: Float) = sqrt(sqr(a) + sqr(b))
