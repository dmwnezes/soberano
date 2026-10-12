package com.dmwnezes.soberano.sim

import kotlin.math.max
import kotlin.math.min

enum class PeaceTerms(val label: String) {
    WHITE("Paz branca"),
    TAKE_OCCUPIED("Ficar com o ocupado"),
    INDEMNITY("Indenização"),
    ANNEX("Anexar tudo"),
}

enum class SpyMission(val label: String, val cost: Double, val difficulty: Float) {
    STEAL_TECH("Roubar tecnologia", 1.0, 0f),
    SABOTAGE("Sabotar economia", 1.5, 5f),
    INCITE("Incitar protestos", 1.0, 0f),
    COUP("Apoiar golpe", 4.0, 25f),
    REVEAL("Revelar exércitos", 0.5, -15f),
}

class Diplomacy(private val sim: Sim) {
    private val game get() = sim.game

    // ------------------------------------------------------------- pontuação de guerra

    /** −100..100 do ponto de vista de [n]. */
    fun warScore(w: War, n: Int): Float {
        val side = w.side(n)
        if (side == 0) return 0f
        val mine = if (side == 1) w.attackers else w.defenders
        val theirs = if (side == 1) w.defenders else w.attackers
        var theirPop = 0.0; var takenFromThem = 0.0; var myPop = 0.0; var takenFromMe = 0.0
        for (i in game.owner.indices) {
            val o = game.owner[i]; val c = game.controller[i]; val pop = game.provPop[i]
            if (o in theirs) { theirPop += pop; if (c in mine) takenFromThem += pop }
            if (o in mine) { myPop += pop; if (c in theirs) takenFromMe += pop }
        }
        var s = (if (theirPop > 0) takenFromThem / theirPop * 100 else 0.0) - (if (myPop > 0) takenFromMe / myPop * 100 else 0.0)
        val bw = if (side == 1) w.battlesA - w.battlesD else w.battlesD - w.battlesA
        s += bw * 2.0
        return s.toFloat().coerceIn(-100f, 100f)
    }

    fun capitulated(n: Int): Boolean {
        val cap = sim.nation(n).capital
        val capLost = game.controller[cap] != n
        val occ = sim.economy.occupiedFraction(n)
        return capLost && occ >= 0.8f || occ >= 0.95f
    }

    // ------------------------------------------------------------- mensal

    fun monthly() {
        val n = game.n
        val blocsOf = Array(n) { mutableListOf<Bloc>() }
        for (b in game.blocs) for (m in b.members) blocsOf[m] += b
        val pairs = HashMap<Long, MutableList<TreatyType>>()
        for (t in game.treaties) {
            val k = key(t.a, t.b)
            pairs.getOrPut(k) { mutableListOf() } += t.type
        }
        for (a in 0 until n) {
            val na = game.nations[a]
            if (!na.alive) continue
            for (b in a + 1 until n) {
                val nb = game.nations[b]
                if (!nb.alive) continue
                if (sim.atWar(a, b)) {
                    if (game.rel(a, b) > -60f) game.setRel(a, b, game.rel(a, b) - 5f)
                    continue
                }
                var base = 0f
                if (na.regime.democratic == nb.regime.democratic) base += 10f
                val shared = blocsOf[a].count { it in blocsOf[b] }
                base += shared * 15f
                pairs[key(a, b)]?.forEach {
                    base += when (it) {
                        TreatyType.TRADE -> 10f; TreatyType.ALLIANCE -> 25f; TreatyType.PASSAGE -> 8f
                        TreatyType.NON_AGGRESSION -> 5f; TreatyType.SANCTION -> -20f; TreatyType.EMBARGO -> -30f
                        TreatyType.GUARANTEE -> 12f
                    }
                }
                val r = game.rel(a, b)
                // relações fortes ou muito ruins mudam devagar (memória histórica)
                val speed = if (r < -50f || r > 60f) 0.01f else 0.03f
                game.setRel(a, b, r + (base - r) * speed)
            }
        }
        // inimigos dos amigos
        for (w in game.wars) for (x in w.attackers) for (y in w.defenders) {
            for (o in game.nations) if (o.alive && o.id != x && o.id != y) {
                if (sim.allied(o.id, y) && !w.involves(o.id)) game.addRel(o.id, x, -1.5f)
                if (sim.allied(o.id, x) && !w.involves(o.id)) game.addRel(o.id, y, -1.5f)
            }
        }
        // tensão de fronteira: expansionistas e oportunistas implicam com vizinhos mais fracos
        for (na in game.nations) {
            if (!na.alive || na.personality.aggression < 0.7f || na.id == game.player) continue
            val mine = sim.landPower(na.id)
            for (o in sim.ai.neighborsOf(na.id)) {
                if (!game.nations[o].alive || sim.allied(na.id, o) || sim.atWar(na.id, o)) continue
                if (game.rel(na.id, o) < 20f && sim.landPower(o) < mine * 0.8)
                    game.addRel(na.id, o, -sim.randf() * 2.5f * na.personality.aggression * game.settings.aiAggression)
            }
        }
        // ministro das Relações Exteriores melhora devagar as relações do jogador
        val fb = sim.dev.foreignBonus()
        if (fb != 0f) for (o in game.nations) if (o.alive && o.id != game.player && !sim.atWar(game.player, o.id) && game.rel(game.player, o.id) > -50f)
            game.addRel(game.player, o.id, fb * 0.3f)
        game.proposals.removeAll { game.day - it.day > 60 }
    }

    private fun key(a: Int, b: Int): Long = if (a < b) a.toLong() * 100000 + b else b.toLong() * 100000 + a

    fun expireTreaties() {
        if (game.treaties.any { it.until in 0 until game.day }) { game.treaties.removeAll { it.until in 0 until game.day }; game.treatyCache = null }
    }

    // ------------------------------------------------------------- acordos

    /** Explica por que [to] recusaria; null = aceita. */
    fun refusal(type: TreatyType, from: Int, to: Int): String? {
        if (from == to) return "Mesmo país"
        if (sim.atWar(from, to)) return "Vocês estão em guerra"
        if (sim.hasTreaty(type, from, to)) return "Acordo já existe"
        val r = game.rel(from, to)
        val other = sim.nation(to)
        var need = type.minRelation
        if (type == TreatyType.TRADE) need -= (other.personality.trade - 1f) * 15f
        if (type == TreatyType.ALLIANCE) {
            // não se alia a quem está em guerra com aliados dele
            if (game.wars.any { it.involves(from) && game.nations.any { x -> sim.allied(to, x.id) && it.involves(x.id) && it.side(x.id) != it.side(from) } })
                return "Seria guerra contra os aliados deles"
            if (other.regime.democratic != sim.nation(from).regime.democratic) need += 15f
        }
        if (game.treaties.any { (it.type == TreatyType.SANCTION || it.type == TreatyType.EMBARGO) && it.a == from && it.b == to })
            return "Você mantém sanções contra eles"
        if (r < need) return "Relação precisa chegar a ${need.toInt()} (hoje ${r.toInt()})"
        return null
    }

    fun propose(type: TreatyType, from: Int, to: Int): String? {
        val why = refusal(type, from, to)
        if (why != null) {
            if (from == game.player) game.addRel(from, to, -1f)
            return why
        }
        sign(type, from, to)
        return null
    }

    fun sign(type: TreatyType, a: Int, b: Int) {
        val until = if (type == TreatyType.NON_AGGRESSION) game.day + 365 * 5 else -1
        game.treaties += Treaty(type, a, b, game.day, until)
        game.treatyCache = null
        game.addRel(a, b, 5f)
        if (sim.involvesPlayer(a, b)) {
            val other = sim.nation(if (a == game.player) b else a)
            sim.feed("handshake", "${type.label} com ${other.name}", 1)
        }
        sim.touch()
    }

    fun cancel(type: TreatyType, a: Int, b: Int) {
        val removed = game.treaties.removeAll {
            it.type == type && ((it.a == a && it.b == b) || (type.bilateral && it.a == b && it.b == a))
        }
        game.treatyCache = null
        if (removed) {
            game.addRel(a, b, if (type.bilateral) -10f else 8f)
            if (type == TreatyType.NON_AGGRESSION) sim.nation(a).stability -= 5f
        }
        sim.touch()
    }

    fun sanction(from: Int, to: Int, embargo: Boolean) {
        val t = if (embargo) TreatyType.EMBARGO else TreatyType.SANCTION
        if (game.treaties.any { it.type == t && it.a == from && it.b == to }) {
            game.treaties.removeAll { it.type == t && it.a == from && it.b == to }
            game.addRel(from, to, 8f)
        } else {
            game.treaties.removeAll { it.type == TreatyType.TRADE && ((it.a == from && it.b == to) || (it.a == to && it.b == from)) }
            game.treaties += Treaty(t, from, to, game.day)
            game.addRel(from, to, if (embargo) -25f else -15f)
            if (from == game.player) sim.feed("block", "${t.label} contra ${sim.nation(to).name}", 0)
            else if (to == game.player) sim.feed("block", "${sim.nation(from).name} aplicou ${t.label.lowercase()} contra você", 2)
        }
        game.treatyCache = null
        sim.touch()
    }

    /** Missão diplomática: melhora a relação gastando capital político. */
    fun envoy(from: Int, to: Int): String? {
        val n = sim.nation(from)
        if (n.politicalCapital < 3f) return "Capital político insuficiente"
        n.politicalCapital -= 3f
        val r = game.rel(from, to)
        game.addRel(from, to, if (r < -50f) 3f else 6f)
        sim.touch()
        return null
    }

    fun gift(from: Int, to: Int, amount: Double): String? {
        val n = sim.nation(from)
        if (n.treasury < amount) return "Tesouro insuficiente"
        n.treasury -= amount
        sim.nation(to).treasury += amount
        val tg = max(1.0, game.natGdp(to))
        game.addRel(from, to, min(25.0, amount / tg * 600).toFloat().coerceAtLeast(1f))
        sim.touch()
        return null
    }

    // ------------------------------------------------------------- guerra

    fun declareWar(attacker: Int, target: Int): War? {
        if (sim.atWar(attacker, target) || attacker == target) return null
        val a = sim.nation(attacker)
        if (sim.hasTreaty(TreatyType.NON_AGGRESSION, attacker, target)) {
            a.stability -= 10f
            for (o in game.nations) if (o.id != attacker && o.alive) game.addRel(attacker, o.id, -8f)
        }
        game.treaties.removeAll {
            ((it.a == attacker && it.b == target) || (it.a == target && it.b == attacker)) && it.type.bilateral
        }
        game.treatyCache = null
        val attackers = mutableListOf(attacker)
        val defenders = mutableListOf(target)
        // aliados do alvo entram (blocos militares e alianças)
        for (o in game.nations) if (o.alive && o.id != attacker && o.id != target) {
            if (sim.allied(o.id, target) && !sim.allied(o.id, attacker) && game.rel(o.id, target) > 20f) defenders += o.id
        }
        // quem garantiu a independência do alvo entra também
        for (t in game.treaties) if (t.type == TreatyType.GUARANTEE && t.b == target && t.a != attacker && t.a !in defenders &&
            sim.nation(t.a).alive && !sim.allied(t.a, attacker)) {
            defenders += t.a
            if (t.a == game.player) sim.feed("shield", "Você garantiu ${sim.nation(target).name}: entrou na guerra", 3)
        }
        // aliados do agressor só entram se gostarem muito dele e odiarem o alvo
        for (o in game.nations) if (o.alive && o.id !in attackers && o.id !in defenders) {
            if (sim.allied(o.id, attacker) && game.rel(o.id, attacker) > 60f && game.rel(o.id, target) < -30f) attackers += o.id
        }
        val w = War(game.nextWarId++, "Guerra ${a.name}–${sim.nation(target).name}", attackers, defenders, game.day)
        game.wars += w
        game.warCache = null
        for (x in attackers) for (y in defenders) game.setRel(x, y, min(game.rel(x, y), -80f))
        for (o in game.nations) if (o.alive && o.id != attacker && !w.involves(o.id)) game.addRel(attacker, o.id, -3f)
        val tone = if (w.involves(game.player)) 3 else 2
        sim.feed("war", "${a.name} declarou guerra a ${sim.nation(target).name}" +
            (if (defenders.size > 1) " (+${defenders.size - 1} aliados)" else ""), tone, sim.nation(target).capital)
        sim.touch()
        return w
    }

    /** O lado de [from] propõe paz a quem lidera o outro lado. */
    fun peaceRefusal(w: War, from: Int, terms: PeaceTerms): String? {
        val side = w.side(from)
        val enemyLeader = if (side == 1) w.leaderD() else w.leaderA()
        val enemy = sim.nation(enemyLeader)
        val myScore = warScore(w, from)
        val ex = enemy.warExhaustion
        return when (terms) {
            PeaceTerms.WHITE -> if (myScore > -15f || ex > 0.5f) null else "Eles acham que estão vencendo"
            PeaceTerms.TAKE_OCCUPIED -> if (myScore >= 25f || (myScore > 5f && ex > 0.6f)) null else "Precisa de pontuação 25 (hoje ${myScore.toInt()})"
            PeaceTerms.INDEMNITY -> if (myScore >= 15f) null else "Precisa de pontuação 15 (hoje ${myScore.toInt()})"
            PeaceTerms.ANNEX -> if (capitulated(enemyLeader)) null else "Ocupe a capital e 80% do país"
        }
    }

    fun proposePeace(w: War, from: Int, terms: PeaceTerms): String? {
        peaceRefusal(w, from, terms)?.let { return it }
        makePeace(w, from, terms)
        return null
    }

    fun makePeace(w: War, winner: Int, terms: PeaceTerms) {
        val side = w.side(winner)
        val winners = if (side == 1) w.attackers else w.defenders
        val losers = if (side == 1) w.defenders else w.attackers
        game.wars.remove(w)
        game.warCache = null
        when (terms) {
            PeaceTerms.WHITE -> {}
            PeaceTerms.TAKE_OCCUPIED -> {
                for (i in game.owner.indices) if (game.owner[i] in losers && game.controller[i] in winners) transfer(i, game.controller[i])
            }
            PeaceTerms.INDEMNITY -> {
                val loser = sim.nation(losers.first())
                val amount = game.natGdp(loser.id) * 0.05
                loser.debt += amount
                sim.nation(winner).treasury += amount
            }
            PeaceTerms.ANNEX -> {
                val target = losers.first()
                for (i in game.owner.indices) if (game.owner[i] == target) transfer(i, winner)
                for (i in game.owner.indices) if (game.owner[i] in losers && game.controller[i] in winners) transfer(i, game.controller[i])
            }
        }
        // devolve o que continua ocupado de lado a lado
        for (i in game.owner.indices) {
            val o = game.owner[i]; val c = game.controller[i]
            if (o != c && ((o in winners && c in losers) || (o in losers && c in winners))) game.controller[i] = o
            if (o != c && !sim.atWar(o, c) && (o in w.attackers || o in w.defenders)) game.controller[i] = o
            game.siege[i] = 0f
        }
        for (x in winners) for (y in losers) {
            if (!sim.atWar(x, y)) game.treaties += Treaty(TreatyType.NON_AGGRESSION, x, y, game.day, game.day + 365 * 5)
            game.setRel(x, y, max(game.rel(x, y), -55f))
        }
        game.treatyCache = null
        for (x in w.attackers + w.defenders) sim.nation(x).warExhaustion *= 0.6f
        evacuateIllegalArmies()
        cleanupDead()
        game.invalidate()
        val text = "Paz: ${w.name.removePrefix("Guerra ")} — ${terms.label}"
        sim.feed("dove", text, if (w.involves(game.player)) 1 else 0)
        sim.touch()
    }

    /** Muda o dono de uma província (anexação). */
    fun transfer(p: Int, to: Int) {
        val from = game.owner[p]
        game.owner[p] = to
        game.controller[p] = to
        game.siege[p] = 0f
        if (from != to) game.resistance[p] = if (game.core[p] == to) 0f else max(game.resistance[p], 30f)
        val cap = sim.nation(from).capital
        if (cap == p) {
            // nova capital: província mais populosa que sobrou
            val rest = game.owner.indices.filter { game.owner[it] == from }
            if (rest.isNotEmpty()) sim.nation(from).capital = rest.maxBy { game.provPop[it] }
        }
        game.invalidate()
    }

    /** Tropas em território onde não podem ficar voltam para a capital. */
    fun evacuateIllegalArmies() {
        for (a in game.armies) {
            if (!sim.canEnter(a.owner, a.province)) {
                val home = sim.nation(a.owner).capital
                a.province = if (game.owner[home] == a.owner) home else (game.owner.indices.firstOrNull { game.owner[it] == a.owner } ?: a.province)
                a.path = IntArray(0); a.progress = 0f; a.battle = false
            }
        }
    }

    fun cleanupDead() {
        game.invalidate()
        for (n in game.nations) if (n.alive && game.natProvinces(n.id) == 0) {
            n.alive = false
            game.armies.removeAll { it.owner == n.id }
            game.treaties.removeAll { it.a == n.id || it.b == n.id }
            for (b in game.blocs) b.members.remove(n.id)
            for (w in game.wars.toList()) {
                w.attackers.remove(n.id); w.defenders.remove(n.id)
                if (w.attackers.isEmpty() || w.defenders.isEmpty()) game.wars.remove(w)
            }
            game.warCache = null
            for (i in game.controller.indices) if (game.controller[i] == n.id) game.controller[i] = game.owner[i]
            if (n.id != game.player) sim.feed("skull", "${n.name} deixou de existir", 2)
        }
        game.blocs.removeAll { it.members.isEmpty() }
        game.treatyCache = null
    }

    // ------------------------------------------------------------- blocos

    fun createBloc(founder: Int, name: String, military: Boolean, economic: Boolean): Bloc {
        val b = Bloc((game.blocs.maxOfOrNull { it.id } ?: 0) + 1, name, military, economic, mutableListOf(founder), founder, 0xFFE0A030)
        game.blocs += b
        game.treatyCache = null
        sim.feed("bloc", "Bloco criado: $name", 1)
        sim.touch()
        return b
    }

    fun inviteRefusal(b: Bloc, to: Int): String? {
        if (to in b.members) return "Já é membro"
        val avg = b.members.map { game.rel(it, to) }.average().toFloat()
        val need = (if (b.military) 45f else 15f)
        if (b.members.any { sim.atWar(it, to) }) return "Em guerra com um membro"
        if (avg < need) return "Relação média precisa chegar a ${need.toInt()} (hoje ${avg.toInt()})"
        return null
    }

    fun invite(b: Bloc, to: Int): String? {
        inviteRefusal(b, to)?.let { return it }
        b.members += to
        game.treatyCache = null
        for (m in b.members) if (m != to) game.addRel(m, to, 10f)
        sim.feed("bloc", "${sim.nation(to).name} entrou em ${b.name}", if (to == game.player || b.members.contains(game.player)) 1 else 0)
        sim.touch()
        return null
    }

    fun joinRefusal(b: Bloc, n: Int): String? = inviteRefusal(b, n)

    fun leave(b: Bloc, n: Int) {
        b.members.remove(n)
        for (m in b.members) game.addRel(m, n, -12f)
        if (b.members.isEmpty()) game.blocs.remove(b)
        game.treatyCache = null
        sim.touch()
    }

    // ------------------------------------------------------------- espionagem

    fun spyChance(from: Int, to: Int, m: SpyMission): Float {
        val a = sim.nation(from); val b = sim.nation(to)
        return (0.55f + (a.index[Area.RESEARCH.ordinal] - b.index[Area.SECURITY.ordinal]) / 200f - m.difficulty / 100f + sim.space.spyBonus(from) - sim.space.spyBonus(to) / 2f).coerceIn(0.05f, 0.9f)
    }

    /** Retorna o texto do resultado. */
    fun spy(from: Int, to: Int, m: SpyMission): String {
        val a = sim.nation(from); val b = sim.nation(to)
        if (a.treasury < m.cost) return "Tesouro insuficiente"
        a.treasury -= m.cost
        val ok = sim.chance(spyChance(from, to, m).toDouble())
        val caught = !ok && sim.chance(0.6)
        val result = if (ok) {
            when (m) {
                SpyMission.STEAL_TECH -> {
                    val best = TechBranch.entries.maxBy { b.tech[it.ordinal] - a.tech[it.ordinal] }
                    sim.economy.addTech(a, best, sim.economy.techCost(a.tech[best.ordinal] + 1) * 0.5f)
                    "Planos roubados: ${best.label} avançou"
                }
                SpyMission.SABOTAGE -> {
                    for (i in game.owner.indices) if (game.owner[i] == to) game.provGdp[i] *= 0.985
                    game.invalidate()
                    "Sabotagem custou 1,5% do PIB deles"
                }
                SpyMission.INCITE -> { b.stability -= 8f; b.groupMods[Group.YOUTH.ordinal] -= 8f; "Protestos tomaram as ruas deles" }
                SpyMission.COUP -> {
                    if (b.stability < 35f || b.groups[Group.MILITARY.ordinal] < 40f) {
                        b.regime = Regime.DICTATORSHIP; b.nextElection = -1; b.stability = 40f
                        game.addRel(from, to, 30f)
                        sim.feed("tank", "Golpe em ${b.name}: novo governo amigo", 1)
                        "Golpe bem-sucedido: governo amigo no poder"
                    } else { b.stability -= 12f; "O golpe falhou, mas abalou o governo" }
                }
                SpyMission.REVEAL -> "Exércitos revelados: força terrestre ${sim.landPower(to).toInt()}"
            }
        } else if (caught) {
            game.addRel(from, to, -25f)
            "Espiões presos — relação despencou"
        } else "A missão falhou sem deixar rastros"
        sim.touch()
        return result
    }

    // ------------------------------------------------------------- diplomacia avançada

    /** Por que [a] e [b] se gostam ou não: lista de (motivo, peso). */
    fun reasons(a: Int, b: Int): List<Pair<String, Float>> {
        val out = ArrayList<Pair<String, Float>>()
        val na = sim.nation(a); val nb = sim.nation(b)
        if (sim.atWar(a, b)) out += "Estão em guerra" to -60f
        out += (if (na.regime.democratic == nb.regime.democratic) "Regimes parecidos" to 10f else "Regimes diferentes" to -10f)
        for (bl in game.blocs) if (a in bl.members && b in bl.members) out += "Juntos em ${bl.name}" to 15f
        for (t in game.treaties) {
            val pair = (t.a == a && t.b == b) || (t.a == b && t.b == a)
            if (!pair) continue
            val v = when (t.type) {
                TreatyType.TRADE -> 10f; TreatyType.ALLIANCE -> 25f; TreatyType.PASSAGE -> 8f; TreatyType.NON_AGGRESSION -> 5f
                TreatyType.SANCTION -> -20f; TreatyType.EMBARGO -> -30f; TreatyType.GUARANTEE -> 12f
            }
            val who = when {
                !t.type.bilateral && t.a == a -> " (sua)"
                !t.type.bilateral -> " (deles)"
                else -> ""
            }
            out += "${t.type.label}$who" to v
        }
        if (b in sim.ai.neighborsOf(a)) {
            val tension = nb.personality.aggression >= 0.7f && sim.landPower(b) > sim.landPower(a) * 1.25
            out += (if (tension) "Vizinho ambicioso e mais forte" to -10f else "Vizinhos" to -2f)
        }
        // inimigos em comum aproximam
        val common = game.nations.count { o -> o.alive && o.id != a && o.id != b && game.rel(a, o.id) < -50f && game.rel(b, o.id) < -50f }
        if (common > 0) out += "Inimigos em comum ($common)" to 6f * common
        val explained = out.sumOf { it.second.toDouble() }.toFloat()
        val history = game.rel(a, b) - explained
        if (kotlin.math.abs(history) >= 5f) out += (if (history > 0) "Histórico de amizade" else "Histórico de rivalidade") to history
        return out.sortedByDescending { kotlin.math.abs(it.second) }
    }

    /** Exige uma província de [to]; aceitam se forem muito mais fracos. Retorna mensagem; true se aceitou. */
    fun ultimatum(from: Int, to: Int, province: Int): Pair<Boolean, String> {
        if (game.owner[province] != to) return false to "A província não é deles"
        val mine = sim.landPower(from)
        var theirs = sim.landPower(to)
        for (o in game.nations) if (o.alive && o.id != to && sim.allied(o.id, to) && !sim.allied(o.id, from)) theirs += sim.landPower(o.id) * 0.4
        val name = sim.world.provinces[province].name
        return if (mine > theirs * 2.5 && game.rel(from, to) > -70f) {
            transfer(province, from)
            game.addRel(from, to, -25f)
            for (o in game.nations) if (o.alive && o.id != from && o.id != to) game.addRel(from, o.id, -2f)
            evacuateIllegalArmies()
            sim.feed("flag", "${sim.nation(to).name} entregou $name", if (from == game.player) 1 else 2, province)
            sim.touch()
            true to "Aceitaram: $name agora é sua"
        } else {
            game.addRel(from, to, -15f)
            sim.touch()
            false to "Recusaram. Para tomar $name, só pela força"
        }
    }

    /** Pede a um aliado que entre na guerra [w] ao lado de [from]. */
    fun callToArms(w: War, from: Int, ally: Int): String? {
        if (w.involves(ally)) return "Já está na guerra"
        if (!sim.allied(from, ally)) return "Precisa ser aliado"
        val enemies = if (w.side(from) == 1) w.defenders else w.attackers
        if (enemies.any { sim.allied(ally, it) }) return "Também é aliado do inimigo"
        val r = game.rel(from, ally)
        val pers = sim.nation(ally).personality
        val ok = r > 45f + (if (pers == Personality.PACIFIC) 25f else 0f) - (if (pers.aggression > 1f) 15f else 0f)
        if (!ok) { game.addRel(from, ally, -5f); return "Recusaram (relação ${r.toInt()})" }
        joinWar(w, ally, w.side(from))
        return null
    }

    fun joinWar(w: War, n: Int, side: Int) {
        if (w.involves(n)) return
        if (side == 1) w.attackers += n else w.defenders += n
        game.warCache = null
        val enemies = if (side == 1) w.defenders else w.attackers
        for (e in enemies) game.setRel(n, e, min(game.rel(n, e), -70f))
        sim.feed("war", "${sim.nation(n).name} entrou na ${w.name}", if (w.involves(game.player)) 2 else 0)
        sim.touch()
    }

    /** Fronteira de [a] com [b]: províncias de b vizinhas de a. */
    fun borderProvinces(a: Int, b: Int): List<Int> = game.owner.indices.filter { p ->
        game.owner[p] == b && sim.world.provinces[p].neighbors.any { game.owner[it] == a }
    }
}
