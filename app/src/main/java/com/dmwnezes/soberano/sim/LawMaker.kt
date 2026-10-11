package com.dmwnezes.soberano.sim

import kotlin.math.abs

/** Uma medida que pode entrar numa lei criada pelo jogador. */
class Measure(
    val id: String,
    val category: String,
    val name: String,
    val icon: String,
    /** o que a medida faz, em uma frase */
    val does: String,
    val groups: Map<Group, Float> = emptyMap(),
    val index: Map<Area, Float> = emptyMap(),
    val growth: Float = 0f,
    val revenue: Float = 0f,
    val corruption: Float = 0f,
    val stability: Float = 0f,
    val inflation: Float = 0f,
)

/** Uma consequência pronta para mostrar: texto, tamanho e se é boa. */
class Consequence(val text: String, val magnitude: Float, val good: Boolean)

class LawAnalysis(
    val law: Law,
    val consequences: List<Consequence>,
    val supporters: List<Group>,
    val opponents: List<Group>,
    /** variação estimada da aprovação quando a lei estiver em vigor */
    val approvalDelta: Float,
    val summary: String,
)

object LawMaker {
    val categories = listOf("Economia", "Sociedade", "Segurança", "Saúde e ambiente", "Militar", "Política")

    val measures: List<Measure> = listOf(
        // ---------------- economia
        Measure("corte_impostos_empresas", "Economia", "Cortar imposto das empresas", "money",
            "Empresas pagam menos imposto e investem mais",
            groups = mapOf(Group.BUSINESS to 10f, Group.WORKERS to -3f), growth = 0.004f, revenue = -0.05f),
        Measure("salario_minimo", "Economia", "Aumentar o salário mínimo", "strike",
            "Trabalhadores ganham mais; empresas pagam a conta",
            groups = mapOf(Group.WORKERS to 10f, Group.BUSINESS to -8f, Group.RETIREES to 3f), growth = -0.002f, inflation = 0.8f),
        Measure("incentivo_industria", "Economia", "Incentivo à indústria", "factory",
            "Crédito barato e isenções para fábricas nacionais",
            groups = mapOf(Group.BUSINESS to 6f, Group.WORKERS to 4f), growth = 0.004f, revenue = -0.03f, corruption = 3f),
        Measure("abertura_comercial", "Economia", "Abertura comercial", "handshake",
            "Derruba tarifas de importação",
            groups = mapOf(Group.BUSINESS to 5f, Group.WORKERS to -5f, Group.YOUTH to 2f), growth = 0.005f, inflation = -0.6f),
        Measure("protecionismo", "Economia", "Protecionismo", "lock",
            "Tarifas altas para proteger a produção local",
            groups = mapOf(Group.WORKERS to 6f, Group.BUSINESS to -2f), growth = -0.003f, revenue = 0.02f, inflation = 0.6f),
        Measure("renda_basica", "Economia", "Renda básica", "money",
            "Um valor mensal para cada família pobre",
            groups = mapOf(Group.WORKERS to 9f, Group.YOUTH to 5f, Group.BUSINESS to -6f), index = mapOf(Area.SOCIAL to 8f), revenue = -0.06f, inflation = 0.5f),
        Measure("reforma_previdencia", "Economia", "Reforma da previdência", "balance",
            "Aposentadoria mais tarde para equilibrar as contas",
            groups = mapOf(Group.RETIREES to -12f, Group.WORKERS to -5f, Group.BUSINESS to 8f), revenue = 0.05f),
        Measure("imposto_heranca", "Economia", "Imposto sobre herança", "money",
            "Grandes heranças pagam imposto maior",
            groups = mapOf(Group.BUSINESS to -9f, Group.WORKERS to 4f), revenue = 0.03f),
        // ---------------- sociedade
        Measure("cotas", "Sociedade", "Cotas nas universidades", "school",
            "Reserva de vagas para estudantes de escola pública",
            groups = mapOf(Group.YOUTH to 7f, Group.WORKERS to 3f, Group.BUSINESS to -2f), index = mapOf(Area.EDUCATION to 3f)),
        Measure("cannabis", "Sociedade", "Legalizar a cannabis", "leaf",
            "Venda regulada e tributada",
            groups = mapOf(Group.YOUTH to 10f, Group.RELIGIOUS to -12f, Group.RETIREES to -4f), revenue = 0.01f, index = mapOf(Area.SECURITY to 2f)),
        Measure("casamento_igualitario", "Sociedade", "Casamento igualitário", "people",
            "Qualquer casal pode se casar no civil",
            groups = mapOf(Group.YOUTH to 8f, Group.RELIGIOUS to -10f)),
        Measure("ensino_religioso", "Sociedade", "Ensino religioso nas escolas", "temple",
            "Aulas de religião obrigatórias",
            groups = mapOf(Group.RELIGIOUS to 10f, Group.YOUTH to -7f), index = mapOf(Area.EDUCATION to -2f)),
        Measure("liberdade_imprensa", "Sociedade", "Proteção à imprensa", "camera",
            "Jornalistas protegidos por lei contra censura",
            groups = mapOf(Group.YOUTH to 6f), corruption = -4f, stability = -3f),
        Measure("anticorrupcao", "Sociedade", "Pacote anticorrupção", "scandal",
            "Auditorias, ficha limpa e prisão para corruptos",
            groups = mapOf(Group.YOUTH to 5f, Group.WORKERS to 4f, Group.BUSINESS to -3f), corruption = -10f, stability = -2f),
        // ---------------- segurança
        Measure("porte_armas", "Segurança", "Liberar porte de armas", "shield",
            "Cidadãos podem andar armados",
            groups = mapOf(Group.RELIGIOUS to 5f, Group.MILITARY to 4f, Group.YOUTH to -6f, Group.RETIREES to -3f), index = mapOf(Area.SECURITY to -4f)),
        Measure("penas_duras", "Segurança", "Penas mais duras", "prison",
            "Mais tempo de prisão para crimes violentos",
            groups = mapOf(Group.RETIREES to 6f, Group.RELIGIOUS to 4f, Group.YOUTH to -5f), index = mapOf(Area.SECURITY to 5f)),
        Measure("policia_comunitaria", "Segurança", "Polícia comunitária", "siren",
            "Policiais fixos nos bairros, perto da população",
            groups = mapOf(Group.WORKERS to 4f, Group.RETIREES to 4f), index = mapOf(Area.SECURITY to 6f), revenue = -0.01f),
        Measure("vigilancia", "Segurança", "Vigilância digital", "satellite",
            "Câmeras e monitoramento de comunicações",
            groups = mapOf(Group.YOUTH to -9f, Group.MILITARY to 3f), index = mapOf(Area.SECURITY to 7f), stability = 4f),
        // ---------------- saúde e ambiente
        Measure("reforco_saude", "Saúde e ambiente", "Mais médicos e hospitais", "health",
            "Abre hospitais e contrata médicos no interior",
            groups = mapOf(Group.RETIREES to 7f, Group.WORKERS to 4f), index = mapOf(Area.HEALTH to 7f), revenue = -0.03f),
        Measure("vacina_obrigatoria", "Saúde e ambiente", "Vacinação obrigatória", "syringe",
            "Vacinas exigidas para escola e emprego público",
            groups = mapOf(Group.RELIGIOUS to -6f, Group.RETIREES to 4f), index = mapOf(Area.HEALTH to 5f)),
        Measure("desmatamento_zero", "Saúde e ambiente", "Desmatamento zero", "leaf",
            "Proíbe derrubar florestas nativas",
            groups = mapOf(Group.YOUTH to 8f, Group.BUSINESS to -7f), index = mapOf(Area.ENVIRONMENT to 12f), growth = -0.003f),
        Measure("carros_eletricos", "Saúde e ambiente", "Carros elétricos", "rocket",
            "Isenção para elétricos e fim gradual dos motores a combustão",
            groups = mapOf(Group.YOUTH to 5f, Group.BUSINESS to -3f, Group.WORKERS to -2f), index = mapOf(Area.ENVIRONMENT to 6f), revenue = -0.01f),
        // ---------------- militar
        Measure("reservistas", "Militar", "Treinamento de reservistas", "military",
            "Civis treinam fins de semana para defender o país",
            groups = mapOf(Group.MILITARY to 6f, Group.YOUTH to -4f), stability = 2f),
        Measure("industria_belica", "Militar", "Indústria bélica nacional", "factory",
            "Armas fabricadas no país, com dinheiro público",
            groups = mapOf(Group.MILITARY to 8f, Group.WORKERS to 3f), growth = 0.002f, corruption = 4f, revenue = -0.02f),
        Measure("soldos", "Militar", "Aumento dos soldos", "money",
            "Salário maior para os militares",
            groups = mapOf(Group.MILITARY to 10f, Group.WORKERS to -2f), revenue = -0.02f),
        // ---------------- política
        Measure("voto_obrigatorio", "Política", "Voto obrigatório", "vote",
            "Todo cidadão precisa votar",
            groups = mapOf(Group.YOUTH to -3f), stability = 3f),
        Measure("descentralizacao", "Política", "Mais poder aos estados", "bloc",
            "Estados ganham verba e autonomia",
            groups = mapOf(Group.WORKERS to 2f, Group.BUSINESS to 2f), corruption = 2f, stability = 3f),
        Measure("reduzir_ministerios", "Política", "Cortar ministérios", "lock",
            "Menos cargos e gastos com a máquina pública",
            groups = mapOf(Group.BUSINESS to 6f, Group.WORKERS to -3f), revenue = 0.02f, corruption = -3f),
    )
    val byId = measures.associateBy { it.id }

    private fun factor(intensity: Int) = when (intensity) { 1 -> 0.6f; 3 -> 1.5f; else -> 1f }
    val intensities = listOf(1 to "Branda", 2 to "Moderada", 3 to "Dura")

    fun build(cl: CustomLaw): Law {
        cl.free?.let { fl ->
            return Law(
                id = cl.id, name = cl.name, icon = fl.icon, desc = cl.prompt.ifBlank { fl.notes.joinToString(". ") },
                groups = fl.groups, index = fl.index, growth = fl.growth, revenue = fl.revenue,
                corruption = fl.corruption, stability = fl.stability, inflation = fl.inflation,
            )
        }
        val f = factor(cl.intensity)
        val ms = cl.measures.mapNotNull { byId[it] }
        val groups = HashMap<Group, Float>()
        val index = HashMap<Area, Float>()
        for (m in ms) {
            for ((g, v) in m.groups) groups[g] = (groups[g] ?: 0f) + v * f
            for ((a, v) in m.index) index[a] = (index[a] ?: 0f) + v * f
        }
        val desc = ms.joinToString(", ") { it.name.lowercase() }.replaceFirstChar { it.uppercase() }
        return Law(
            id = cl.id, name = cl.name, icon = ms.firstOrNull()?.icon ?: "law", desc = desc,
            groups = groups, index = index,
            growth = ms.sumOf { it.growth.toDouble() }.toFloat() * f,
            revenue = ms.sumOf { it.revenue.toDouble() }.toFloat() * f,
            corruption = ms.sumOf { it.corruption.toDouble() }.toFloat() * f,
            stability = ms.sumOf { it.stability.toDouble() }.toFloat() * f,
            inflation = ms.sumOf { it.inflation.toDouble() }.toFloat() * f,
        )
    }

    /** Explica em palavras o que a lei faz e o que vai acontecer. */
    fun analyze(law: Law, n: Nation, gdp: Double): LawAnalysis {
        val out = ArrayList<Consequence>()
        for ((g, v) in law.groups) if (abs(v) >= 0.5f)
            out += Consequence("${g.label}: ${signed(v)}", abs(v), v > 0)
        for ((a, v) in law.index) if (abs(v) >= 0.5f)
            out += Consequence("${a.label}: ${signed(v)} no índice", abs(v), v > 0)
        if (abs(law.growth) >= 0.0005f)
            out += Consequence("Crescimento ${signedOne(law.growth * 100)} ponto ao ano", abs(law.growth) * 1500, law.growth > 0)
        if (abs(law.revenue) >= 0.002f) {
            val money = gdp * n.tax / 12 * law.revenue
            out += Consequence("Arrecadação ${signedOne(law.revenue * 100)}% (${if (money >= 0) "+" else "−"}${Fmt.money(abs(money))}/mês)", abs(law.revenue) * 200, law.revenue > 0)
        }
        if (abs(law.corruption) >= 0.5f) out += Consequence("Corrupção ${signed(law.corruption)}", abs(law.corruption), law.corruption < 0)
        if (abs(law.stability) >= 0.5f) out += Consequence("Estabilidade ${signed(law.stability)}", abs(law.stability), law.stability > 0)
        if (abs(law.inflation) >= 0.1f) out += Consequence("Inflação ${signedOne(law.inflation)} ponto", abs(law.inflation) * 6, law.inflation < 0)
        out.sortByDescending { it.magnitude }

        val supporters = law.groups.filter { it.value >= 3f }.keys.sortedByDescending { law.groups[it] }
        val opponents = law.groups.filter { it.value <= -3f }.keys.sortedBy { law.groups[it] }
        val wsum = Group.entries.sumOf { it.weight.toDouble() }.toFloat()
        val approval = law.groups.entries.sumOf { (it.value * it.key.weight).toDouble() }.toFloat() / wsum

        val parts = ArrayList<String>()
        if (supporters.isNotEmpty()) parts += "Agrada ${supporters.joinToString(" e ") { it.label.lowercase() }}"
        if (opponents.isNotEmpty()) parts += "irrita ${opponents.joinToString(" e ") { it.label.lowercase() }}"
        val money = when {
            law.revenue <= -0.02f -> "pesa no orçamento"
            law.revenue >= 0.02f -> "reforça o caixa"
            else -> null
        }
        if (money != null) parts += money
        val summary = if (parts.isEmpty()) "Efeito pequeno: quase ninguém vai notar." else parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
        return LawAnalysis(law, out, supporters, opponents, approval, summary)
    }

    private fun signed(v: Float) = (if (v >= 0) "+" else "−") + abs(v).let { if (it >= 10 || it == it.toInt().toFloat()) it.toInt().toString() else Fmt.one(it) }
    private fun signedOne(v: Float) = (if (v >= 0) "+" else "−") + Fmt.one(abs(v))

    /** Nome sugerido a partir das medidas. */
    fun suggestName(ids: List<String>): String {
        val ms = ids.mapNotNull { byId[it] }
        return when (ms.size) {
            0 -> ""
            1 -> "Lei: ${ms[0].name}"
            else -> "Pacote ${ms.first().category.split(" ").first().lowercase()}" + if (ms.map { it.category }.distinct().size > 1) " e mais" else ""
        }
    }

    // ------------------------------------------------------------- partida

    fun register(game: Game) {
        Content.customLaws.clear()
        for (cl in game.customLaws) Content.customLaws[cl.id] = build(cl)
    }

    fun create(game: Game, name: String, measures: List<String>, intensity: Int): CustomLaw {
        val id = "custom_${System.currentTimeMillis().toString(36)}_${game.customLaws.size}"
        val cl = CustomLaw(id, name.trim().ifEmpty { suggestName(measures) }, measures, intensity)
        game.customLaws += cl
        Content.customLaws[id] = build(cl)
        return cl
    }

    /** Cria uma lei escrita em texto livre, já com os efeitos estimados. */
    fun createFree(game: Game, name: String, prompt: String, law: FreeLaw): CustomLaw {
        val id = "custom_${System.currentTimeMillis().toString(36)}_${game.customLaws.size}"
        val cl = CustomLaw(id, name.trim().ifEmpty { LawInterpreter.nameFrom(prompt) }, emptyList(), 2, prompt.trim().take(600), law.clamped())
        game.customLaws += cl
        Content.customLaws[id] = build(cl)
        return cl
    }

    fun delete(game: Game, id: String) {
        game.customLaws.removeAll { it.id == id }
        for (n in game.nations) n.laws.remove(id)
        Content.customLaws.remove(id)
    }
}
