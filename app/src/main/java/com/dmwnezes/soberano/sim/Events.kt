package com.dmwnezes.soberano.sim

import kotlin.math.max

class Events(private val sim: Sim) {
    private val game get() = sim.game

    private fun frequency(): Float {
        val f = game.settings.eventFrequency
        if (f != EventFrequency.VARIABLE) return f.mult
        // períodos calmos e agitados que se alternam
        if (game.eventCalm == 0) game.eventCalm = if (sim.chance(0.5)) 4 + sim.randInt(5) else -(3 + sim.randInt(4))
        return if (game.eventCalm > 0) { game.eventCalm--; 0.35f } else { game.eventCalm++; 2.1f }
    }

    fun monthly() {
        val mult = frequency()
        val me = sim.player
        if (me.alive && game.events.size < 3) {
            for (cat in game.settings.events) {
                if (!sim.chance((cat.base * mult).toDouble())) continue
                val def = pickEvent(cat, me) ?: continue
                game.events += create(def, me)
                if (game.events.size >= 3) break
            }
        }
        // países do computador: eventos resolvidos sozinhos
        for (n in game.nations) {
            if (!n.alive || n.id == game.player || !sim.chance(0.12 * mult)) continue
            val cats = game.settings.events.toList()
            if (cats.isEmpty()) break
            val def = pickEvent(sim.pick(cats), n) ?: continue
            val pe = create(def, n)
            val choice = def.choices.maxBy { c -> c.effects.sumOf { score(it).toDouble() } }
            for (e in choice.effects) apply(n, e, pe.other)
        }
    }

    private fun pickEvent(cat: EventCategory, n: Nation): EventDef? {
        val list = Content.events.filter { it.cat == cat }
        val weights = list.map { max(0f, it.weight(game, n)) }
        val total = weights.sum()
        if (total <= 0f) return null
        var r = sim.randf() * total
        for (i in list.indices) { r -= weights[i]; if (r <= 0f) return list[i] }
        return list.last()
    }

    private fun create(def: EventDef, n: Nation): PendingEvent {
        var prov = -1; var other = -1
        if (def.needsProvince) {
            val own = sim.provincesOf(n.id)
            if (own.isNotEmpty()) {
                val tot = own.sumOf { game.provPop[it] }
                var r = sim.rand() * tot
                prov = own.first()
                for (p in own) { r -= game.provPop[p]; if (r <= 0) { prov = p; break } }
            }
        }
        if (def.needsOther) {
            val neighbors = sim.ai.neighborsOf(n.id).filter { game.nations[it].alive }
            val pool = if (def.id == "incidente") neighbors.sortedBy { game.rel(n.id, it) }.take(3) else neighbors
            other = if (pool.isNotEmpty()) sim.pick(pool) else game.nations.filter { it.alive && it.id != n.id }.let { sim.pick(it).id }
        }
        return PendingEvent(def.id, game.day, prov, other)
    }

    fun text(pe: PendingEvent): String {
        val def = Content.eventById[pe.def] ?: return ""
        var t = def.text
        if (pe.province >= 0) t = t.replace("{p}", sim.world.provinces[pe.province].name)
        if (pe.other >= 0) t = t.replace("{o}", sim.nation(pe.other).name)
        return t
    }

    /** O jogador escolheu a opção [choice] da carta. */
    fun resolve(pe: PendingEvent, choice: Int) {
        val def = Content.eventById[pe.def] ?: run { game.events.remove(pe); return }
        val c = def.choices.getOrNull(choice) ?: return
        for (e in c.effects) apply(sim.player, e, pe.other)
        game.events.remove(pe)
        sim.feed(def.icon, "${def.title}: ${c.label}", 0, pe.province)
        sim.touch()
    }

    fun score(e: Effect): Float {
        val v = e.value
        return when {
            e.key == "money" -> v * 500
            e.key == "gdp" -> v * 800
            e.key == "pop" -> v * 1000
            e.key == "stab" -> v
            e.key == "corr" -> -v
            e.key == "rel" -> v
            e.key == "relo" -> v * 0.3f
            e.key == "all" -> v
            e.key.startsWith("g:") -> v * 0.3f
            e.key.startsWith("i:") -> v * 0.5f
            e.key == "tech" -> v * 0.1f
            e.key == "debt" -> -v * 300
            e.key == "infl" -> -v
            e.key == "cap" -> v * 0.3f
            e.key == "morale" -> v * 20
            else -> 0f
        }
    }

    fun apply(n: Nation, e: Effect, other: Int) {
        val v = e.value
        val gdp = game.natGdp(n.id)
        when {
            e.key == "money" -> {
                n.treasury += v * gdp
                if (n.treasury < 0) { n.debt += -n.treasury; n.treasury = 0.0 }
            }
            e.key == "gdp" -> { for (i in game.owner.indices) if (game.owner[i] == n.id) game.provGdp[i] *= (1.0 + v); game.invalidate() }
            e.key == "pop" -> { for (i in game.owner.indices) if (game.owner[i] == n.id) game.provPop[i] *= (1.0 + v); game.invalidate() }
            e.key == "stab" -> n.stability = (n.stability + v).coerceIn(0f, 100f)
            e.key == "corr" -> n.corruption = (n.corruption + v).coerceIn(0f, 100f)
            e.key == "rel" -> for (o in game.nations) if (o.alive && o.id != n.id) game.addRel(n.id, o.id, v)
            e.key == "relo" -> if (other >= 0) game.addRel(n.id, other, v)
            e.key == "morale" -> for (a in game.armies) if (a.owner == n.id) a.morale = (a.morale + v).coerceIn(0.2f, 1f)
            e.key == "tech" -> sim.economy.addTech(n, TechBranch.entries[n.researchFocus], v)
            e.key == "debt" -> n.debt += v * gdp
            e.key == "infl" -> n.inflation = (n.inflation + v).coerceAtLeast(0f)
            e.key == "all" -> for (g in Group.entries) n.groupMods[g.ordinal] += v
            e.key == "cap" -> n.politicalCapital = (n.politicalCapital + v).coerceIn(0f, 100f)
            e.key.startsWith("g:") -> Group.valueOf(e.key.substring(2)).let { n.groupMods[it.ordinal] += v }
            e.key.startsWith("i:") -> Area.valueOf(e.key.substring(2)).let { n.index[it.ordinal] = (n.index[it.ordinal] + v).coerceIn(0f, 100f) }
        }
    }

    /** Rótulo curto de um efeito para chips da interface. */
    fun label(e: Effect, n: Nation): String {
        val v = e.value
        val sign = if (v >= 0) "+" else "−"
        val abs = kotlin.math.abs(v)
        return when {
            e.key == "money" -> "$sign${Fmt.money(abs * game.natGdp(n.id))}"
            e.key == "gdp" -> "${sign}${Fmt.pct(abs)} PIB"
            e.key == "pop" -> "${sign}${Fmt.pct(abs)} pop."
            e.key == "debt" -> "+${Fmt.money(abs * game.natGdp(n.id))} dívida".let { if (v < 0) it.replace("+", "−") else it }
            e.key == "tech" -> "$sign${abs.toInt()} pesquisa"
            e.key == "morale" -> "$sign${(abs * 100).toInt()}% moral"
            e.key == "infl" -> "$sign${Fmt.one(abs)} inflação"
            else -> "$sign${abs.toInt()} ${keyName(e.key)}"
        }
    }

    fun keyName(k: String): String = when {
        k == "stab" -> "estabilidade"
        k == "corr" -> "corrupção"
        k == "rel" -> "relações"
        k == "relo" -> "relação"
        k == "all" -> "todos"
        k == "cap" -> "capital pol."
        k.startsWith("g:") -> Group.valueOf(k.substring(2)).label.lowercase()
        k.startsWith("i:") -> Area.valueOf(k.substring(2)).label.lowercase()
        else -> k
    }

    /** O efeito é bom para quem escolhe? (cor do chip) */
    fun good(e: Effect): Boolean = score(e) >= 0
}

/** Formatação de números em português. */
object Fmt {
    fun money(bi: Double): String {
        val a = kotlin.math.abs(bi)
        val s = when {
            a >= 1000 -> "${one((a / 1000).toFloat())} tri"
            a >= 1 -> "${if (a >= 100) a.toInt().toString() else one(a.toFloat())} bi"
            else -> "${(a * 1000).toInt()} mi"
        }
        return (if (bi < 0) "−" else "") + "US$ " + s
    }
    fun one(v: Float): String = String.format(java.util.Locale("pt", "BR"), "%.1f", v)
    fun pct(v: Float): String = String.format(java.util.Locale("pt", "BR"), if (v < 0.1f) "%.1f%%" else "%.0f%%", v * 100)
    fun pop(p: Double): String = when {
        p >= 1e9 -> "${one((p / 1e9).toFloat())} bi"
        p >= 1e6 -> "${one((p / 1e6).toFloat())} mi"
        p >= 1e3 -> "${(p / 1e3).toInt()} mil"
        else -> p.toInt().toString()
    }
    fun units(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else one(v)
}
