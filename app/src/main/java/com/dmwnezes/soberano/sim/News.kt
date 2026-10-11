package com.dmwnezes.soberano.sim

import kotlin.math.abs

/** Monta o jornal do mês a partir dos avisos e dos números. */
class News(private val sim: Sim) {
    private val game get() = sim.game

    /** Peso de cada tipo de notícia para escolher a manchete. */
    private fun weight(f: FeedItem): Int {
        val mine = f.tone >= 1
        val base = when (f.icon) {
            "nuke" -> 120
            "protest" -> if (f.text.startsWith("Guerra civil")) 110 else 60
            "war" -> 90
            "vote" -> 85
            "dove" -> 80
            "flag" -> if (f.text.startsWith("Capital")) 85 else 55
            "tank" -> 75
            "skull" -> 70
            "bloc", "block" -> 55
            "victory", "defeat" -> 50
            "chart_down", "chart_up" -> 45
            else -> 20
        }
        return base + (if (f.tone >= 3) 30 else if (mine) 10 else 0)
    }

    /** Guarda o aviso se for notícia. */
    fun collect(f: FeedItem) {
        val relevant = f.tone >= 1 || f.icon in setOf("war", "dove", "flag", "tank", "protest", "skull", "nuke", "bloc", "vote", "chart_down")
        if (!relevant) return
        game.newsBuffer += f
        while (game.newsBuffer.size > 80) game.newsBuffer.removeAt(0)
    }

    fun publish() {
        val me = sim.player
        val items = game.newsBuffer.distinctBy { it.text }.sortedByDescending { weight(it) }
        game.newsBuffer.clear()
        val myName = me.name
        val nationalItems = items.filter { it.tone >= 1 || it.text.contains(myName) || (it.province >= 0 && game.owner.getOrNull(it.province) == me.id) }
        val worldItems = items.filter { it !in nationalItems }
        val h = game.history
        val last = h.lastOrNull()
        val prev = if (h.size >= 2) h[h.size - 2] else null

        val economy = ArrayList<String>()
        if (last != null && prev != null && prev.gdp > 0) {
            val g = (last.gdp / prev.gdp - 1) * 100
            economy += "PIB ${if (g >= 0) "cresce" else "recua"} ${Fmt.one(abs(g).toFloat())}% no mês"
        }
        economy += "Inflação em ${Fmt.one(me.inflation)}%, desemprego em ${Fmt.one(me.unemployment)}%"
        val oil = game.prices[Resource.OIL.ordinal]
        if (abs(oil - 1f) > 0.15f) economy += "Petróleo ${if (oil > 1f) "sobe" else "cai"}: ${(oil * 100).toInt()}% do preço normal"
        val bal = sim.dev.tradeBalance(me.id)
        economy += if (bal >= 0) "Exportações de recursos rendem ${Fmt.money(bal.toDouble())} ao ano" else "País importa ${Fmt.money(-bal.toDouble())} em recursos por ano"

        val top = items.firstOrNull()
        val headline = top?.text ?: when {
            me.approval >= 60f -> "Governo vive lua de mel com ${me.approval.toInt()}% de aprovação"
            me.approval < 35f -> "Aprovação do governo despenca para ${me.approval.toInt()}%"
            sim.atWarAny(me.id) -> "País segue em guerra"
            else -> "Mês tranquilo em ${myName}"
        }
        val sub = buildString {
            append("Aprovação ${me.approval.toInt()}%")
            append(" · estabilidade ${me.stability.toInt()}")
            if (sim.atWarAny(me.id)) append(" · ${game.wars.count { it.involves(me.id) }} guerra(s)")
        }
        game.paper = Newspaper(
            game.day, headline, sub,
            nationalItems.filter { it !== top }.take(4).map { it.text },
            worldItems.filter { it !== top }.take(4).map { it.text },
            economy.take(4),
        )
    }
}
