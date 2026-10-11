package com.dmwnezes.soberano.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import java.text.Normalizer
import kotlin.math.abs

/** Efeitos de uma lei escrita em texto livre (pela IA do aparelho ou pelo interpretador local). */
@Serializable
class FreeLaw(
    val groups: Map<Group, Float> = emptyMap(),
    val index: Map<Area, Float> = emptyMap(),
    /** fração ao ano (0,003 = +0,3 ponto) */
    val growth: Float = 0f,
    /** fração da arrecadação (0,03 = +3%) */
    val revenue: Float = 0f,
    val corruption: Float = 0f,
    val stability: Float = 0f,
    val inflation: Float = 0f,
    /** efeitos explicados em frases curtas */
    val notes: List<String> = emptyList(),
    /** true quando veio da IA do aparelho */
    val ai: Boolean = false,
    val icon: String = "law",
) {
    fun isEmpty() = groups.values.all { abs(it) < 0.5f } && index.values.all { abs(it) < 0.5f } &&
        abs(growth) < 0.0005f && abs(revenue) < 0.002f && abs(corruption) < 0.5f && abs(stability) < 0.5f && abs(inflation) < 0.1f

    /** Mantém os números num intervalo razoável para não quebrar o jogo. */
    fun clamped() = FreeLaw(
        groups.mapValues { it.value.coerceIn(-15f, 15f) }.filterValues { abs(it) >= 0.5f },
        index.filterKeys { it.hasIndex }.mapValues { it.value.coerceIn(-12f, 12f) }.filterValues { abs(it) >= 0.5f },
        growth.coerceIn(-0.01f, 0.01f), revenue.coerceIn(-0.08f, 0.08f), corruption.coerceIn(-10f, 10f),
        stability.coerceIn(-8f, 8f), inflation.coerceIn(-2f, 2f), notes.take(6), ai, icon,
    )
}

/** Resultado da interpretação: nome sugerido e efeitos. */
class Interpretation(val name: String, val law: FreeLaw)

object LawInterpreter {

    // --------------------------------------------------------------- prompt para a IA

    fun prompt(text: String, country: String): String = """
Você é o analista de um jogo de governar países. O presidente de $country propôs esta lei:
"${text.trim().take(600)}"

Estime os efeitos realistas e responda SOMENTE com um JSON, sem nenhum texto fora dele, neste formato:
{"nome":"nome curto da lei (até 5 palavras)",
"grupos":{"trabalhadores":0,"empresarios":0,"militares":0,"religiosos":0,"jovens":0,"aposentados":0},
"indices":{"saude":0,"educacao":0,"seguranca":0,"infraestrutura":0,"social":0,"ambiente":0,"pesquisa":0},
"crescimento":0,"arrecadacao":0,"corrupcao":0,"estabilidade":0,"inflacao":0,
"efeitos":["frase curta","frase curta","frase curta"]}

Regras: grupos de -15 a 15 (apoio de cada grupo); indices de -12 a 12; crescimento do PIB em pontos percentuais de -1 a 1;
arrecadacao em % de -8 a 8 (negativo se a lei custa dinheiro); corrupcao de -10 a 10; estabilidade de -8 a 8; inflacao de -2 a 2.
Use 0 quando não houver efeito. "efeitos" são de 2 a 4 frases curtas em português explicando o que muda no país.
""".trimIndent()

    private val groupKeys = mapOf(
        "trabalhadores" to Group.WORKERS, "empresarios" to Group.BUSINESS, "militares" to Group.MILITARY,
        "religiosos" to Group.RELIGIOUS, "jovens" to Group.YOUTH, "aposentados" to Group.RETIREES,
    )
    private val areaKeys = mapOf(
        "saude" to Area.HEALTH, "educacao" to Area.EDUCATION, "seguranca" to Area.SECURITY, "infraestrutura" to Area.INFRA,
        "social" to Area.SOCIAL, "ambiente" to Area.ENVIRONMENT, "meioambiente" to Area.ENVIRONMENT, "pesquisa" to Area.RESEARCH,
    )

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** Lê a resposta da IA. Devolve null se não der para entender. */
    fun parseAi(raw: String, fallbackName: String): Interpretation? {
        val start = raw.indexOf('{'); val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = try { json.parseToJsonElement(raw.substring(start, end + 1)) as? JsonObject } catch (_: Exception) { null } ?: return null
        fun num(e: JsonElement?): Float = when (e) {
            is JsonPrimitive -> e.floatOrNull ?: e.contentOrNull?.replace(",", ".")?.replace("+", "")?.trim()?.toFloatOrNull() ?: 0f
            else -> 0f
        }
        fun key(k: String) = strip(k).replace(" ", "").replace("_", "")
        val groups = HashMap<Group, Float>()
        (obj["grupos"] as? JsonObject)?.forEach { (k, v) -> groupKeys[key(k)]?.let { groups[it] = num(v) } }
        val index = HashMap<Area, Float>()
        (obj["indices"] as? JsonObject)?.forEach { (k, v) -> areaKeys[key(k)]?.let { index[it] = num(v) } }
        val notes = (obj["efeitos"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.length in 3..160 } } ?: emptyList()
        var growth = num(obj["crescimento"])
        var revenue = num(obj["arrecadacao"])
        // a IA responde em pontos percentuais
        growth /= 100f
        revenue /= 100f
        val law = FreeLaw(groups, index, growth, revenue, num(obj["corrupcao"]), num(obj["estabilidade"]), num(obj["inflacao"]),
            notes, ai = true, icon = guessIcon(groups, index)).clamped()
        if (law.isEmpty() && notes.isEmpty()) return null
        val name = (obj["nome"] as? JsonPrimitive)?.contentOrNull?.trim()?.take(40)?.takeIf { it.isNotBlank() } ?: fallbackName
        return Interpretation(name, law)
    }

    // --------------------------------------------------------------- interpretador local

    private class Rule(
        val keys: List<String>,
        val note: String,
        val groups: Map<Group, Float> = emptyMap(),
        val index: Map<Area, Float> = emptyMap(),
        val growth: Float = 0f,
        val revenue: Float = 0f,
        val corruption: Float = 0f,
        val stability: Float = 0f,
        val inflation: Float = 0f,
        /** "proibir X" inverte o efeito */
        val flip: Boolean = true,
        val icon: String = "law",
        /** nota quando a regra aparece invertida */
        val noteOff: String? = null,
    )

    private val W = Group.WORKERS; private val B = Group.BUSINESS; private val M = Group.MILITARY
    private val R = Group.RELIGIOUS; private val Y = Group.YOUTH; private val A = Group.RETIREES

    private val rules = listOf(
        // economia
        Rule(listOf("imposto", "tribut", "taxa sobre", "taxar", "taxacao"), "Mais impostos: o caixa melhora, mas quem paga reclama",
            groups = mapOf(B to -6f, W to -2f), revenue = 0.03f, growth = -0.002f, icon = "money", noteOff = "Menos impostos: a economia anima e o caixa perde"),
        Rule(listOf("fortuna", "milionario", "bilionario", "super-rico", "ricos"), "Os mais ricos passam a pagar mais",
            groups = mapOf(B to -9f, W to 5f, Y to 3f), revenue = 0.025f, growth = -0.001f, icon = "money"),
        Rule(listOf("salario minimo", "piso salarial", "aumento salarial", "salario"), "Salários sobem; empresas pagam a conta",
            groups = mapOf(W to 9f, B to -7f, A to 2f), inflation = 0.7f, growth = -0.001f, icon = "strike"),
        Rule(listOf("jornada", "4 dias", "quatro dias", "semana de", "horas de trabalho", "escala 6x1", "6x1"), "Menos horas de trabalho: mais qualidade de vida, menos produção",
            groups = mapOf(W to 10f, Y to 6f, B to -10f), growth = -0.003f, index = mapOf(Area.SOCIAL to 3f), icon = "strike"),
        Rule(listOf("privatiz"), "Estatais vendidas: dinheiro agora, sindicatos contra",
            groups = mapOf(B to 9f, W to -7f), growth = 0.003f, revenue = 0.02f, corruption = 2f, flip = false, icon = "factory"),
        Rule(listOf("estatiz", "nacionaliz"), "O Estado assume empresas: mais controle, menos investimento",
            groups = mapOf(W to 5f, B to -12f), growth = -0.004f, corruption = 3f, revenue = -0.01f, flip = false, icon = "factory"),
        Rule(listOf("renda basica", "bolsa", "auxilio", "transferencia de renda", "beneficio social"), "Dinheiro direto para as famílias pobres",
            groups = mapOf(W to 8f, Y to 4f, B to -5f), index = mapOf(Area.SOCIAL to 7f), revenue = -0.05f, inflation = 0.4f, icon = "money"),
        Rule(listOf("aposentadoria", "previdencia", "pensao"), "Aposentados são afetados diretamente",
            groups = mapOf(A to 10f, W to 2f, B to -3f), revenue = -0.03f, index = mapOf(Area.SOCIAL to 3f), icon = "balance",
            noteOff = "Aposentadoria mais dura: contas melhoram, aposentados se revoltam"),
        Rule(listOf("tarifa", "importacao", "protecion"), "Tarifas protegem a indústria local e encarecem produtos",
            groups = mapOf(W to 5f, B to -2f), growth = -0.002f, revenue = 0.015f, inflation = 0.5f, icon = "lock",
            noteOff = "Importar fica mais barato: preços caem, fábricas locais sofrem"),
        Rule(listOf("livre comercio", "abertura comercial", "acordo comercial"), "Mais comércio com o mundo", groups = mapOf(B to 6f, W to -3f),
            growth = 0.004f, inflation = -0.4f, icon = "handshake"),
        Rule(listOf("industria", "fabrica", "manufatura"), "Incentivo às fábricas nacionais", groups = mapOf(B to 6f, W to 4f),
            growth = 0.003f, revenue = -0.02f, corruption = 2f, icon = "factory"),
        Rule(listOf("agro", "agricult", "fazend", "rural", "campo"), "O campo é afetado", groups = mapOf(B to 5f, R to 2f),
            growth = 0.002f, index = mapOf(Area.ENVIRONMENT to -2f), revenue = -0.01f, icon = "field"),
        Rule(listOf("startup", "tecnologia", "inteligencia artificial", "inovacao", "digital"), "Aposta em tecnologia e inovação",
            groups = mapOf(Y to 6f, B to 4f), index = mapOf(Area.RESEARCH to 6f), growth = 0.002f, revenue = -0.015f, icon = "rocket"),
        Rule(listOf("turismo"), "Mais turistas, mais empregos em serviços", groups = mapOf(B to 4f, W to 3f), growth = 0.002f, icon = "sun"),
        Rule(listOf("moradia", "habitac", "casa propria", "aluguel"), "Moradia mais acessível", groups = mapOf(W to 7f, Y to 6f, B to -2f),
            index = mapOf(Area.SOCIAL to 5f, Area.INFRA to 2f), revenue = -0.03f, icon = "door"),
        Rule(listOf("obra", "infraestrutura", "estrada", "ferrovia", "rodovia", "ponte", "porto", "aeroporto", "metro", "transporte publico", "onibus"),
            "Obras e transporte: empregos agora, país mais eficiente depois", groups = mapOf(W to 5f, B to 4f),
            index = mapOf(Area.INFRA to 7f), growth = 0.002f, revenue = -0.03f, corruption = 2f, icon = "factory"),
        Rule(listOf("gasto publico", "teto de gastos", "austeridade", "corte de gastos", "enxugar"), "Corte de gastos: contas melhoram, serviços pioram",
            groups = mapOf(B to 6f, W to -5f, A to -4f), revenue = 0.03f, index = mapOf(Area.SOCIAL to -3f), growth = -0.001f, flip = false, icon = "balance"),
        // sociedade
        Rule(listOf("escola", "educacao", "ensino", "professor", "universidade", "faculdade"), "Educação recebe mais atenção",
            groups = mapOf(Y to 8f, W to 3f), index = mapOf(Area.EDUCATION to 7f), revenue = -0.03f, icon = "school"),
        Rule(listOf("cota"), "Reserva de vagas para grupos desfavorecidos", groups = mapOf(Y to 6f, W to 3f, B to -2f),
            index = mapOf(Area.EDUCATION to 2f, Area.SOCIAL to 2f), icon = "school"),
        Rule(listOf("aborto"), "Tema que divide o país: religiosos e jovens em lados opostos",
            groups = mapOf(Y to 7f, R to -14f, A to -3f), stability = -2f, index = mapOf(Area.HEALTH to 2f), icon = "health",
            noteOff = "Restrição ao aborto: religiosos aplaudem, jovens protestam"),
        Rule(listOf("casamento", "lgbt", "uniao civil"), "Direitos civis ampliados", groups = mapOf(Y to 8f, R to -10f), icon = "people"),
        Rule(listOf("igreja", "religi", "culto", "biblia", "templo"), "Religião ganha espaço no Estado", groups = mapOf(R to 10f, Y to -6f),
            stability = 1f, icon = "temple", noteOff = "Estado mais laico: religiosos perdem espaço"),
        Rule(listOf("droga", "maconha", "cannabis"), "Mudança na política de drogas", groups = mapOf(Y to 9f, R to -10f, A to -4f),
            revenue = 0.01f, index = mapOf(Area.SECURITY to 2f), icon = "leaf", noteOff = "Guerra às drogas: mais polícia, mais prisões"),
        Rule(listOf("imigra", "refugiad", "estrangeir"), "Imigração afeta o mercado de trabalho e a cultura",
            groups = mapOf(B to 5f, W to -4f, R to -2f), growth = 0.002f, stability = -1f, icon = "door",
            noteOff = "Fronteiras fechadas: nacionalistas aplaudem, empresas perdem mão de obra"),
        Rule(listOf("cultura", "arte", "cinema", "museu", "esporte", "futebol", "olimpi"), "Cultura e esporte recebem apoio",
            groups = mapOf(Y to 5f, W to 2f), stability = 1f, revenue = -0.01f, icon = "party"),
        Rule(listOf("feriado"), "Um feriado a mais: o povo gosta, o comércio não", groups = mapOf(W to 6f, Y to 4f, B to -6f), growth = -0.001f, icon = "party"),
        Rule(listOf("rede social", "redes sociais", "internet", "celular"), "Mudança no mundo digital", groups = mapOf(Y to 6f, B to 2f),
            index = mapOf(Area.RESEARCH to 1f), icon = "camera", noteOff = "Controle da internet: jovens revoltados, governo com mais controle"),
        Rule(listOf("censura", "fake news", "desinforma"), "Controle sobre o que se publica", groups = mapOf(Y to -8f, M to 2f),
            stability = 3f, corruption = 3f, flip = false, icon = "mute"),
        Rule(listOf("imprensa", "jornalis", "liberdade de expressao", "transparencia"), "Mais liberdade e transparência",
            groups = mapOf(Y to 6f), corruption = -4f, stability = -2f, icon = "camera"),
        Rule(listOf("corrupc", "ficha limpa", "propina", "lavagem"), "Combate à corrupção", groups = mapOf(Y to 5f, W to 4f, B to -3f),
            corruption = -8f, stability = -1f, flip = false, icon = "scandal"),
        // segurança
        Rule(listOf("arma", "porte", "armamento civil"), "Mais armas nas mãos dos cidadãos", groups = mapOf(R to 4f, M to 4f, Y to -6f, A to -3f),
            index = mapOf(Area.SECURITY to -3f), icon = "shield", noteOff = "Desarmamento: menos armas nas ruas"),
        Rule(listOf("policia", "policiamento", "seguranca publica", "guarda"), "Mais polícia nas ruas", groups = mapOf(A to 6f, W to 3f, Y to -2f),
            index = mapOf(Area.SECURITY to 6f), revenue = -0.015f, icon = "siren"),
        Rule(listOf("prisao", "pena", "presidio", "cadeia", "maioridade penal", "pena de morte", "prisao perpetua"), "Punições mais duras para criminosos",
            groups = mapOf(A to 6f, R to 5f, Y to -6f), index = mapOf(Area.SECURITY to 5f), stability = 1f, icon = "prison",
            noteOff = "Penas mais leves: presídios esvaziam, parte da população teme"),
        Rule(listOf("toque de recolher", "estado de sitio", "lei marcial", "estado de emergencia"), "Liberdades suspensas em nome da ordem",
            groups = mapOf(M to 8f, Y to -12f, W to -6f, B to -4f), index = mapOf(Area.SECURITY to 6f), stability = 4f, growth = -0.003f, flip = false, icon = "siren"),
        Rule(listOf("vigilancia", "camera", "reconhecimento facial", "monitoramento"), "Mais vigilância sobre a população",
            groups = mapOf(Y to -8f, M to 3f, A to 3f), index = mapOf(Area.SECURITY to 6f), stability = 2f, icon = "satellite"),
        // saúde e ambiente
        Rule(listOf("saude", "hospital", "medico", "sus", "remedio", "medicament", "vacina", "saneamento"), "Saúde pública reforçada",
            groups = mapOf(A to 8f, W to 5f), index = mapOf(Area.HEALTH to 7f), revenue = -0.03f, icon = "health"),
        Rule(listOf("desmatamento", "floresta", "amazonia", "meio ambiente", "ambiental", "poluicao", "reciclag", "lixo"), "Proteção ambiental",
            groups = mapOf(Y to 7f, B to -6f), index = mapOf(Area.ENVIRONMENT to 9f), growth = -0.002f, icon = "leaf"),
        Rule(listOf("carbono", "clima", "emissao"), "Corte de emissões: indústria paga, clima agradece", groups = mapOf(Y to 6f, B to -7f),
            index = mapOf(Area.ENVIRONMENT to 8f), growth = -0.002f, revenue = 0.01f, icon = "leaf"),
        Rule(listOf("solar", "eolica", "energia limpa", "renovav", "eletric"), "Energia limpa", groups = mapOf(Y to 6f, B to 2f),
            index = mapOf(Area.ENVIRONMENT to 6f, Area.INFRA to 2f), revenue = -0.015f, icon = "sun"),
        Rule(listOf("petroleo", "mineracao", "garimpo", "carvao", "gas natural"), "Exploração de recursos naturais", groups = mapOf(B to 7f, W to 3f, Y to -5f),
            growth = 0.003f, revenue = 0.015f, index = mapOf(Area.ENVIRONMENT to -6f), icon = "oil"),
        Rule(listOf("nuclear", "usina"), "Energia nuclear", groups = mapOf(B to 4f, Y to -3f), index = mapOf(Area.RESEARCH to 4f, Area.INFRA to 3f),
            revenue = -0.02f, icon = "atom"),
        // militar e política
        Rule(listOf("servico militar", "alistamento", "conscri"), "Jovens obrigados a servir", groups = mapOf(M to 8f, Y to -10f), stability = 1f, icon = "military"),
        Rule(listOf("militar", "exercito", "forcas armadas", "defesa", "soldado"), "Forças armadas fortalecidas", groups = mapOf(M to 10f, Y to -2f),
            revenue = -0.02f, stability = 1f, icon = "military"),
        Rule(listOf("eleic", "voto", "mandato", "reeleic"), "Mudança nas regras do jogo político", groups = mapOf(Y to 2f), stability = -2f, icon = "vote"),
        Rule(listOf("estados", "municip", "descentraliz", "autonomia"), "Mais poder para estados e cidades", groups = mapOf(W to 2f, B to 2f),
            corruption = 2f, stability = 2f, icon = "bloc"),
        Rule(listOf("ministerio", "cargos", "funcionalismo", "servidor"), "Mudança na máquina pública", groups = mapOf(B to 4f, W to -3f),
            revenue = 0.015f, corruption = -2f, icon = "lock"),
    )

    private val negations = listOf("proib", "banir", "bane ", "acabar", "acaba com", "extingu", "reduz", "cortar", "corta ", "fim d", "fim a", "revog",
        "nao ", "contra ", "diminu", "limitar", "limita ", "restring", "vetar", "abolir", "aboli", "menos ", "zerar", "isen", "baixar")
    private val strong = listOf("total", "radical", "todos", "todas", "obrigatori", "imediat", "100%", "dobrar", "triplicar")
    private val mild = listOf("parcial", "gradual", "piloto", "teste", "pequen", "leve", "aos poucos")

    fun strip(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Lê o texto da lei e estima os efeitos sem internet nem IA. */
    fun local(text: String): Interpretation {
        val t = " " + strip(text).replace(Regex("\\s+"), " ") + " "
        val groups = HashMap<Group, Float>(); val index = HashMap<Area, Float>()
        var growth = 0f; var revenue = 0f; var corruption = 0f; var stability = 0f; var inflation = 0f
        val notes = ArrayList<String>()
        var icon = "law"
        val f = when { strong.any { it in t } -> 1.3f; mild.any { it in t } -> 0.7f; else -> 1f }
        // posição de cada tema no texto (palavra começando ali, não no meio de outra: "porte" ≠ "transporte")
        val found = rules.mapNotNull { r -> r.keys.map { t.indexOf(" $it") }.filter { it >= 0 }.minOrNull()?.let { r to it + 1 } }
            .sortedBy { it.second }.take(5)
        // "proibir X": a negação vale para o primeiro tema logo depois dela
        val negated = HashSet<Rule>()
        for (neg in negations) {
            var i = t.indexOf(neg)
            while (i >= 0) {
                found.firstOrNull { it.second >= i + neg.trimEnd().length - 1 && it.second - i <= 40 }?.let { if (it.first.flip) negated += it.first }
                i = t.indexOf(neg, i + 1)
            }
        }
        var hits = 0
        for ((r, _) in found) {
            val neg = r in negated
            val k = (if (neg) -1f else 1f) * f * (if (hits >= 3) 0.6f else 1f)
            for ((g, v) in r.groups) groups[g] = (groups[g] ?: 0f) + v * k
            for ((a, v) in r.index) index[a] = (index[a] ?: 0f) + v * k
            growth += r.growth * k; revenue += r.revenue * k; corruption += r.corruption * k
            stability += r.stability * k; inflation += r.inflation * k
            notes += if (neg) (r.noteOff ?: "Medida contrária: ${r.note.replaceFirstChar { it.lowercase() }}") else r.note
            if (hits == 0) icon = r.icon
            hits++
        }
        if (("gratuit" in t || "de graca" in t || "subsid" in t) && revenue > -0.02f) { revenue -= 0.02f; notes += "Ser de graça custa caro ao orçamento" }
        if (hits == 0) {
            notes += "Tema pouco comum: efeito pequeno e incerto"
            stability -= 1f
            groups[Group.YOUTH] = (groups[Group.YOUTH] ?: 0f) + if ("proib" in t) -2f else 1f
        }
        val law = FreeLaw(groups, index, growth, revenue, corruption, stability, inflation, notes.distinct(), ai = false, icon = icon).clamped()
        return Interpretation(nameFrom(text), law)
    }

    /** Nome curto a partir do texto digitado. */
    fun nameFrom(text: String): String {
        val words = text.trim().replace(Regex("[\\n\\r]+"), " ").split(" ").filter { it.isNotBlank() }
        val s = words.take(5).joinToString(" ").trimEnd('.', ',', ';', ':')
        return (if (words.size > 5) "$s…" else s).replaceFirstChar { it.uppercase() }.take(40).ifBlank { "Lei nova" }
    }

    private fun guessIcon(groups: Map<Group, Float>, index: Map<Area, Float>): String {
        val topArea = index.maxByOrNull { abs(it.value) }
        val topGroup = groups.maxByOrNull { abs(it.value) }
        if (topArea != null && abs(topArea.value) >= (topGroup?.value?.let { abs(it) } ?: 0f) * 0.6f) return when (topArea.key) {
            Area.HEALTH -> "health"; Area.EDUCATION -> "school"; Area.SECURITY -> "siren"; Area.INFRA -> "factory"
            Area.SOCIAL -> "people"; Area.ENVIRONMENT -> "leaf"; Area.RESEARCH -> "atom"; Area.DEFENSE -> "military"
        }
        return when (topGroup?.key) {
            Group.WORKERS -> "strike"; Group.BUSINESS -> "money"; Group.MILITARY -> "military"; Group.RELIGIOUS -> "temple"
            Group.YOUTH -> "people"; Group.RETIREES -> "balance"; null -> "law"
        }
    }
}
