package com.dmwnezes.soberano.sim

/**
 * Efeito de uma escolha ou lei. Chaves:
 *  money (fração do PIB anual), gdp (fração), pop (fração), stab, corr, rel (todos), morale,
 *  tech (pontos), debt (fração do PIB), infl, all (todos os grupos), g:<GRUPO>, i:<ÁREA>, cap (capital político)
 */
data class Effect(val key: String, val value: Float)

fun fx(key: String, v: Number) = Effect(key, v.toFloat())

class Choice(val label: String, val effects: List<Effect>)

class EventDef(
    val id: String,
    val cat: EventCategory,
    val icon: String,
    val title: String,
    /** texto curto; {p} vira o nome da província, {o} o outro país */
    val text: String,
    val needsProvince: Boolean = false,
    val needsOther: Boolean = false,
    /** peso relativo dentro da categoria para este país */
    val weight: (Game, Nation) -> Float = { _, _ -> 1f },
    val choices: List<Choice>,
)

class Law(
    val id: String,
    val name: String,
    val icon: String,
    val desc: String,
    val groups: Map<Group, Float> = emptyMap(),
    val index: Map<Area, Float> = emptyMap(),
    val growth: Float = 0f,
    val revenue: Float = 0f,
    val corruption: Float = 0f,
    val stability: Float = 0f,
    val inflation: Float = 0f,
    val decreeOnly: Boolean = false,
    val excludes: String? = null,
    val onEnact: List<Effect> = emptyList(),
)

object Content {
    val laws: List<Law> = listOf(
        Law("servico_militar", "Serviço militar obrigatório", "military", "Infantaria −15% de custo, moral +10%",
            groups = mapOf(Group.MILITARY to 10f, Group.YOUTH to -12f), stability = 1f),
        Law("saude_universal", "Saúde universal", "health", "Saúde +8, mais gasto social",
            groups = mapOf(Group.WORKERS to 8f, Group.RETIREES to 10f, Group.BUSINESS to -5f),
            index = mapOf(Area.HEALTH to 8f), growth = -0.002f),
        Law("ensino_integral", "Ensino integral", "school", "Educação +8, crescimento +0,2",
            groups = mapOf(Group.YOUTH to 8f, Group.WORKERS to 3f, Group.BUSINESS to -2f),
            index = mapOf(Area.EDUCATION to 8f), growth = 0.002f),
        Law("imposto_fortunas", "Imposto sobre fortunas", "money", "Receita +6%, crescimento −0,4",
            groups = mapOf(Group.WORKERS to 8f, Group.BUSINESS to -15f), revenue = 0.06f, growth = -0.004f),
        Law("privatizacoes", "Privatizações", "factory", "Caixa +2% do PIB agora, crescimento +0,5",
            groups = mapOf(Group.BUSINESS to 12f, Group.WORKERS to -8f), growth = 0.005f, corruption = -3f,
            excludes = "estatizacoes", onEnact = listOf(fx("money", 0.02))),
        Law("estatizacoes", "Estatizações", "factory", "Receita +4%, crescimento −0,5",
            groups = mapOf(Group.WORKERS to 6f, Group.BUSINESS to -14f), growth = -0.005f, corruption = 5f,
            revenue = 0.04f, excludes = "privatizacoes"),
        Law("policia_militarizada", "Polícia militarizada", "shield", "Segurança +10",
            groups = mapOf(Group.RETIREES to 5f, Group.YOUTH to -8f), index = mapOf(Area.SECURITY to 10f), stability = 3f),
        Law("censura", "Censura à imprensa", "mute", "Estabilidade +8, democracias desconfiam",
            groups = mapOf(Group.YOUTH to -15f, Group.RELIGIOUS to 3f), stability = 8f, corruption = 4f),
        Law("estado_emergencia", "Estado de emergência", "alert", "Estabilidade +10, crescimento −0,5",
            groups = Group.entries.associateWith { -4f }, stability = 10f, growth = -0.005f),
        Law("lei_marcial", "Lei marcial", "military", "Segurança +15, estabilidade +15",
            groups = mapOf(Group.MILITARY to 10f, Group.YOUTH to -10f, Group.WORKERS to -6f),
            index = mapOf(Area.SECURITY to 15f), stability = 15f, growth = -0.01f, decreeOnly = true),
        Law("controle_fronteiras", "Controle de fronteiras", "border", "Segurança +5",
            groups = mapOf(Group.RELIGIOUS to 5f, Group.RETIREES to 3f, Group.YOUTH to -4f, Group.BUSINESS to -3f),
            index = mapOf(Area.SECURITY to 5f), growth = -0.002f),
        Law("energia_verde", "Energia verde", "leaf", "Ambiente +15",
            groups = mapOf(Group.YOUTH to 8f, Group.BUSINESS to -4f), index = mapOf(Area.ENVIRONMENT to 15f), growth = -0.002f),
        Law("estado_laico", "Estado laico", "balance", "Jovens apoiam, religiosos não",
            groups = mapOf(Group.RELIGIOUS to -8f, Group.YOUTH to 6f), excludes = "religiao_oficial"),
        Law("religiao_oficial", "Religião oficial", "temple", "Religiosos apoiam, jovens não",
            groups = mapOf(Group.RELIGIOUS to 12f, Group.YOUTH to -8f), stability = 2f, excludes = "estado_laico"),
        Law("reforma_agraria", "Reforma agrária", "field", "Trabalhadores +8, empresários −8",
            groups = mapOf(Group.WORKERS to 8f, Group.BUSINESS to -8f)),
        Law("teto_gastos", "Teto de gastos", "lock", "Inflação −2, menos apoio popular",
            groups = mapOf(Group.BUSINESS to 8f, Group.WORKERS to -6f, Group.RETIREES to -6f), inflation = -2f),
        Law("partido_unico", "Partido único", "flag", "Estabilidade +10, corrupção +8",
            groups = mapOf(Group.YOUTH to -10f, Group.MILITARY to 4f), stability = 10f, corruption = 8f, decreeOnly = true),
    )
    val lawById = laws.associateBy { it.id }

    /** Leis criadas pelo jogador na partida atual (montadas por [LawMaker]). */
    val customLaws = HashMap<String, Law>()
    fun law(id: String): Law? = lawById[id] ?: customLaws[id]

    private fun lowIdx(a: Area): (Game, Nation) -> Float = { _, n -> 0.5f + (100f - n.index[a.ordinal]) / 50f }

    val events: List<EventDef> = listOf(
        // ---------- desastres
        EventDef("terremoto", EventCategory.DISASTER, "quake", "Terremoto", "Um tremor forte atingiu {p}.", needsProvince = true,
            choices = listOf(
                Choice("Reconstruir já", listOf(fx("money", -0.006), fx("all", 3), fx("i:INFRA", -2))),
                Choice("Ajuda mínima", listOf(fx("money", -0.001), fx("all", -6), fx("i:INFRA", -5))),
            )),
        EventDef("enchente", EventCategory.DISASTER, "flood", "Enchentes", "Chuvas alagaram {p}.", needsProvince = true,
            weight = lowIdx(Area.ENVIRONMENT),
            choices = listOf(
                Choice("Defesa civil", listOf(fx("money", -0.003), fx("g:WORKERS", 4))),
                Choice("Esperar baixar", listOf(fx("gdp", -0.003), fx("all", -4))),
            )),
        EventDef("seca", EventCategory.DISASTER, "sun", "Seca severa", "Plantações perdidas em {p}.", needsProvince = true,
            weight = lowIdx(Area.ENVIRONMENT),
            choices = listOf(
                Choice("Subsidiar agricultores", listOf(fx("money", -0.004), fx("g:WORKERS", 5))),
                Choice("Importar comida", listOf(fx("money", -0.002), fx("infl", 1))),
                Choice("Nada", listOf(fx("gdp", -0.006), fx("stab", -5))),
            )),
        EventDef("incendio", EventCategory.DISASTER, "fire", "Incêndio florestal", "Fogo fora de controle em {p}.", needsProvince = true,
            weight = lowIdx(Area.ENVIRONMENT),
            choices = listOf(
                Choice("Mobilizar exército", listOf(fx("money", -0.002), fx("g:YOUTH", 4), fx("i:ENVIRONMENT", -2))),
                Choice("Deixar queimar", listOf(fx("i:ENVIRONMENT", -8), fx("g:YOUTH", -8), fx("rel", -2))),
            )),
        EventDef("furacao", EventCategory.DISASTER, "storm", "Furacão", "Um furacão devastou {p}.", needsProvince = true,
            choices = listOf(
                Choice("Plano de resgate", listOf(fx("money", -0.005), fx("all", 4))),
                Choice("Pedir ajuda externa", listOf(fx("money", -0.001), fx("rel", 1), fx("stab", -3))),
            )),

        // ---------- saúde
        EventDef("epidemia", EventCategory.HEALTH, "virus", "Surto de doença", "Casos explodem em {p}.", needsProvince = true,
            weight = lowIdx(Area.HEALTH),
            choices = listOf(
                Choice("Quarentena", listOf(fx("gdp", -0.006), fx("pop", -0.0002), fx("g:RETIREES", 6))),
                Choice("Vacinação em massa", listOf(fx("money", -0.004), fx("pop", -0.0004), fx("i:HEALTH", 2))),
                Choice("Minimizar", listOf(fx("pop", -0.002), fx("all", -8), fx("stab", -6))),
            )),
        EventDef("pandemia", EventCategory.HEALTH, "virus", "Pandemia mundial", "Um vírus novo cruza fronteiras.",
            weight = { _, _ -> 0.35f },
            choices = listOf(
                Choice("Fechar tudo", listOf(fx("gdp", -0.025), fx("pop", -0.0008), fx("g:BUSINESS", -12), fx("g:RETIREES", 8))),
                Choice("Fechar fronteiras", listOf(fx("gdp", -0.01), fx("pop", -0.002), fx("rel", -2))),
                Choice("Seguir normal", listOf(fx("pop", -0.005), fx("g:RETIREES", -15), fx("stab", -8))),
            )),
        EventDef("vacina", EventCategory.HEALTH, "syringe", "Vacina nacional", "Cientistas criaram uma vacina inédita.",
            weight = { _, n -> n.index[Area.RESEARCH.ordinal] / 60f },
            choices = listOf(
                Choice("Distribuir grátis", listOf(fx("money", -0.002), fx("i:HEALTH", 5), fx("all", 4))),
                Choice("Exportar", listOf(fx("money", 0.004), fx("rel", 3))),
            )),

        // ---------- política
        EventDef("escandalo", EventCategory.POLITICS, "scandal", "Escândalo de corrupção", "Um ministro foi flagrado com malas de dinheiro.",
            weight = { _, n -> 0.4f + n.corruption / 40f },
            choices = listOf(
                Choice("Demitir e investigar", listOf(fx("corr", -6), fx("cap", -5), fx("all", 2))),
                Choice("Abafar o caso", listOf(fx("corr", 5), fx("g:YOUTH", -8), fx("stab", -4))),
            )),
        EventDef("greve", EventCategory.POLITICS, "strike", "Greve geral", "Sindicatos param o país por salários.",
            weight = { _, n -> 0.5f + (60f - n.groups[Group.WORKERS.ordinal]).coerceAtLeast(0f) / 15f },
            choices = listOf(
                Choice("Dar aumento", listOf(fx("money", -0.004), fx("g:WORKERS", 10), fx("infl", 0.8))),
                Choice("Negociar", listOf(fx("cap", -5), fx("g:WORKERS", 4), fx("gdp", -0.002))),
                Choice("Reprimir", listOf(fx("g:WORKERS", -12), fx("stab", -6), fx("g:MILITARY", 3))),
            )),
        EventDef("protesto", EventCategory.POLITICS, "protest", "Protestos nas ruas", "Multidões pedem mudanças.",
            weight = { _, n -> 0.3f + (50f - n.approval).coerceAtLeast(0f) / 10f },
            choices = listOf(
                Choice("Ouvir as demandas", listOf(fx("cap", -8), fx("all", 5))),
                Choice("Ignorar", listOf(fx("stab", -5), fx("g:YOUTH", -4))),
                Choice("Repressão", listOf(fx("stab", 3), fx("g:YOUTH", -12), fx("rel", -3))),
            )),
        EventDef("renuncia", EventCategory.POLITICS, "door", "Ministro renuncia", "Seu ministro da economia saiu batendo a porta.",
            choices = listOf(
                Choice("Nomear técnico", listOf(fx("g:BUSINESS", 5), fx("cap", -3))),
                Choice("Nomear aliado", listOf(fx("cap", 5), fx("corr", 3))),
            )),
        EventDef("golpe_frustrado", EventCategory.POLITICS, "tank", "Conspiração descoberta", "Oficiais planejavam derrubar o governo.",
            weight = { _, n -> 0.2f + (50f - n.groups[Group.MILITARY.ordinal]).coerceAtLeast(0f) / 12f },
            choices = listOf(
                Choice("Expurgo nas forças", listOf(fx("g:MILITARY", -10), fx("stab", 6), fx("morale", -0.1))),
                Choice("Aumentar soldos", listOf(fx("money", -0.003), fx("g:MILITARY", 12))),
            )),

        // ---------- economia
        EventDef("crise", EventCategory.ECONOMY, "chart_down", "Crise financeira", "Bancos quebram e a bolsa despenca.",
            weight = { g, n -> 0.5f + (n.debt / (g.natGdp(n.id) + 1)).toFloat() },
            choices = listOf(
                Choice("Socorrer bancos", listOf(fx("debt", 0.03), fx("gdp", -0.005), fx("g:BUSINESS", 8), fx("g:WORKERS", -6))),
                Choice("Deixar quebrar", listOf(fx("gdp", -0.02), fx("g:BUSINESS", -10))),
            )),
        EventDef("boom", EventCategory.ECONOMY, "chart_up", "Boom das commodities", "O preço do que você exporta disparou.",
            choices = listOf(
                Choice("Poupar no tesouro", listOf(fx("money", 0.01))),
                Choice("Investir em obras", listOf(fx("money", 0.004), fx("i:INFRA", 5), fx("g:WORKERS", 4))),
            )),
        EventDef("petroleo", EventCategory.ECONOMY, "oil", "Petróleo descoberto", "Uma grande reserva foi achada em {p}.", needsProvince = true,
            weight = { _, _ -> 0.5f },
            choices = listOf(
                Choice("Estatal explora", listOf(fx("gdp", 0.015), fx("corr", 3), fx("g:WORKERS", 4))),
                Choice("Leilão privado", listOf(fx("money", 0.012), fx("gdp", 0.008), fx("g:BUSINESS", 6))),
                Choice("Preservar a área", listOf(fx("i:ENVIRONMENT", 6), fx("g:YOUTH", 6))),
            )),
        EventDef("startup", EventCategory.ECONOMY, "rocket", "Unicórnio nacional", "Uma startup local vale bilhões.",
            weight = { _, n -> n.index[Area.EDUCATION.ordinal] / 50f },
            choices = listOf(
                Choice("Incentivos fiscais", listOf(fx("money", -0.001), fx("gdp", 0.004), fx("g:YOUTH", 5))),
                Choice("Taxar os lucros", listOf(fx("money", 0.002), fx("g:BUSINESS", -4))),
            )),

        // ---------- segurança
        EventDef("crimes", EventCategory.SECURITY, "siren", "Onda de crimes", "Assaltos disparam em {p}.", needsProvince = true,
            weight = lowIdx(Area.SECURITY),
            choices = listOf(
                Choice("Mais policiais", listOf(fx("money", -0.002), fx("i:SECURITY", 4), fx("g:RETIREES", 4))),
                Choice("Programas sociais", listOf(fx("money", -0.002), fx("i:SOCIAL", 3), fx("g:WORKERS", 3))),
                Choice("Nada", listOf(fx("all", -5), fx("i:SECURITY", -3))),
            )),
        EventDef("atentado", EventCategory.SECURITY, "bomb", "Atentado", "Uma explosão atingiu o centro de {p}.", needsProvince = true,
            weight = { _, n -> (0.3f + (60f - n.index[Area.SECURITY.ordinal]).coerceAtLeast(0f) / 15f) },
            choices = listOf(
                Choice("Caçar os culpados", listOf(fx("money", -0.002), fx("stab", 3), fx("all", 3))),
                Choice("Endurecer leis", listOf(fx("i:SECURITY", 5), fx("g:YOUTH", -6), fx("stab", 2))),
            )),
        EventDef("fuga", EventCategory.SECURITY, "prison", "Fuga em massa", "Centenas fugiram de um presídio em {p}.", needsProvince = true,
            weight = lowIdx(Area.SECURITY),
            choices = listOf(
                Choice("Recapturar", listOf(fx("money", -0.001), fx("i:SECURITY", 2))),
                Choice("Abafar", listOf(fx("corr", 4), fx("all", -4))),
            )),

        // ---------- ciência
        EventDef("avanco", EventCategory.SCIENCE, "atom", "Avanço científico", "Seus laboratórios fizeram uma descoberta.",
            weight = { _, n -> n.index[Area.RESEARCH.ordinal] / 50f },
            choices = listOf(
                Choice("Uso militar", listOf(fx("tech", 40), fx("g:MILITARY", 4))),
                Choice("Uso civil", listOf(fx("gdp", 0.004), fx("g:YOUTH", 4))),
            )),
        EventDef("cientista", EventCategory.SCIENCE, "person", "Cientista pede asilo", "Um cientista de {o} quer trabalhar aqui.", needsOther = true,
            choices = listOf(
                Choice("Aceitar", listOf(fx("tech", 30), fx("relo", -10))),
                Choice("Recusar", listOf(fx("relo", 4))),
            )),
        EventDef("satelite", EventCategory.SCIENCE, "satellite", "Satélite próprio", "Você pode lançar o primeiro satélite nacional.",
            weight = { _, n -> n.income / 3f },
            choices = listOf(
                Choice("Lançar", listOf(fx("money", -0.003), fx("tech", 25), fx("all", 4))),
                Choice("Adiar", listOf()),
            )),

        // ---------- internacional
        EventDef("refugiados", EventCategory.INTERNATIONAL, "people", "Refugiados na fronteira", "Milhares fogem de {o}.", needsOther = true,
            choices = listOf(
                Choice("Acolher", listOf(fx("money", -0.002), fx("pop", 0.002), fx("rel", 3), fx("g:RELIGIOUS", 3), fx("g:WORKERS", -3))),
                Choice("Fechar fronteira", listOf(fx("rel", -2), fx("g:RETIREES", 3))),
            )),
        EventDef("ajuda_aliado", EventCategory.INTERNATIONAL, "handshake", "Pedido de ajuda", "{o} pede ajuda financeira.", needsOther = true,
            choices = listOf(
                Choice("Enviar ajuda", listOf(fx("money", -0.003), fx("relo", 20))),
                Choice("Negar", listOf(fx("relo", -8))),
            )),
        EventDef("incidente", EventCategory.INTERNATIONAL, "alert", "Incidente de fronteira", "Soldados de {o} cruzaram a fronteira.", needsOther = true,
            choices = listOf(
                Choice("Protesto formal", listOf(fx("relo", -5))),
                Choice("Revidar", listOf(fx("relo", -25), fx("g:MILITARY", 6), fx("all", 2))),
                Choice("Deixar passar", listOf(fx("g:MILITARY", -6), fx("stab", -2))),
            )),
        EventDef("copa", EventCategory.INTERNATIONAL, "trophy", "Sede de grande evento", "Você foi escolhido para sediar a Copa.",
            weight = { _, n -> n.income / 4f },
            choices = listOf(
                Choice("Sediar", listOf(fx("money", -0.008), fx("i:INFRA", 4), fx("all", 6), fx("rel", 2))),
                Choice("Recusar", listOf(fx("g:YOUTH", -3))),
            )),

        // ---------- novos (recursos, sociedade, militar)
        EventDef("choque_petroleo", EventCategory.ECONOMY, "oil", "Choque do petróleo", "Um conflito no Golfo fez o barril disparar.",
            weight = { _, _ -> 0.6f },
            choices = listOf(
                Choice("Subsidiar combustível", listOf(fx("money", -0.004), fx("all", 3), fx("price:OIL", 0.5))),
                Choice("Repassar o preço", listOf(fx("infl", 1.5), fx("g:WORKERS", -6), fx("price:OIL", 0.5))),
            )),
        EventDef("supersafra", EventCategory.ECONOMY, "field", "Supersafra", "O campo colheu como nunca.",
            choices = listOf(
                Choice("Exportar tudo", listOf(fx("money", 0.004), fx("g:BUSINESS", 4), fx("price:FOOD", -0.2))),
                Choice("Estoque regulador", listOf(fx("infl", -0.8), fx("g:WORKERS", 3))),
            )),
        EventDef("mina", EventCategory.DISASTER, "factory", "Barragem rompe", "Uma barragem de rejeitos rompeu em {p}.", needsProvince = true,
            weight = { _, n -> 0.3f + (100f - n.index[Area.ENVIRONMENT.ordinal]) / 80f },
            choices = listOf(
                Choice("Punir a mineradora", listOf(fx("money", 0.002), fx("g:BUSINESS", -6), fx("g:YOUTH", 5), fx("i:ENVIRONMENT", -4))),
                Choice("Acordo discreto", listOf(fx("corr", 4), fx("all", -5), fx("i:ENVIRONMENT", -6))),
            )),
        EventDef("hackers", EventCategory.SECURITY, "shield", "Ataque hacker", "Sistemas do governo foram invadidos por hackers estrangeiros.",
            weight = lowIdx(Area.RESEARCH),
            choices = listOf(
                Choice("Investir em defesa digital", listOf(fx("money", -0.002), fx("i:SECURITY", 3), fx("tech", 10))),
                Choice("Acusar o país de origem", listOf(fx("relo", -15), fx("g:MILITARY", 3))),
            ), needsOther = true),
        EventDef("motim", EventCategory.POLITICS, "military", "Motim no quartel", "Soldados reclamam de salários atrasados.",
            weight = { _, n -> 0.2f + (if (n.militaryFund < 0) 2f else 0f) + (50f - n.groups[Group.MILITARY.ordinal]).coerceAtLeast(0f) / 20f },
            choices = listOf(
                Choice("Pagar os atrasados", listOf(fx("money", -0.003), fx("g:MILITARY", 8), fx("morale", 0.1))),
                Choice("Prender os líderes", listOf(fx("g:MILITARY", -8), fx("morale", -0.15), fx("stab", 2))),
            )),
        EventDef("imigrantes", EventCategory.INTERNATIONAL, "people", "Mão de obra estrangeira", "Empresas pedem vistos para trabalhadores de {o}.", needsOther = true,
            choices = listOf(
                Choice("Abrir vistos", listOf(fx("gdp", 0.004), fx("pop", 0.001), fx("g:WORKERS", -4), fx("relo", 8))),
                Choice("Proteger empregos", listOf(fx("g:WORKERS", 4), fx("g:BUSINESS", -4))),
            )),
        EventDef("descoberta_litio", EventCategory.SCIENCE, "atom", "Reserva de lítio", "Geólogos acharam lítio em {p}.", needsProvince = true,
            weight = { _, _ -> 0.4f },
            choices = listOf(
                Choice("Indústria de baterias", listOf(fx("money", -0.003), fx("gdp", 0.01), fx("tech", 15))),
                Choice("Exportar o minério", listOf(fx("money", 0.006), fx("price:MINERALS", -0.1))),
            )),
        EventDef("eleicao_vizinho", EventCategory.INTERNATIONAL, "vote", "Eleição tensa em {o}", "A oposição de {o} pede seu apoio.", needsOther = true,
            choices = listOf(
                Choice("Apoiar a oposição", listOf(fx("relo", -20), fx("cap", 3))),
                Choice("Ficar neutro", listOf(fx("relo", 3))),
            )),
        EventDef("greve_caminhoneiros", EventCategory.POLITICS, "strike", "Greve dos caminhoneiros", "Estradas bloqueadas e prateleiras vazias.",
            weight = { _, n -> 0.4f + (n.inflation - 4f).coerceAtLeast(0f) / 4f },
            choices = listOf(
                Choice("Baixar o diesel", listOf(fx("money", -0.003), fx("g:WORKERS", 5))),
                Choice("Liberar à força", listOf(fx("stab", -4), fx("g:WORKERS", -6), fx("gdp", -0.002))),
            )),
        EventDef("ponte", EventCategory.DISASTER, "alert", "Ponte desaba", "Uma ponte importante caiu em {p}.", needsProvince = true,
            weight = lowIdx(Area.INFRA),
            choices = listOf(
                Choice("Reconstruir já", listOf(fx("money", -0.003), fx("i:INFRA", 2))),
                Choice("Desvio provisório", listOf(fx("i:INFRA", -4), fx("gdp", -0.002))),
            )),
        EventDef("medalha", EventCategory.HUMOR, "trophy", "Ouro inesperado", "Um atleta de {p} ganhou ouro no curling.", needsProvince = true,
            choices = listOf(
                Choice("Feriado regional", listOf(fx("all", 2), fx("gdp", -0.0005))),
                Choice("Parabéns nas redes", listOf(fx("g:YOUTH", 2))),
            )),
        EventDef("papagaio", EventCategory.HUMOR, "goat", "Papagaio patriota", "Um papagaio que canta o hino virou celebridade nacional.",
            choices = listOf(
                Choice("Nomear mascote oficial", listOf(fx("g:YOUTH", 4), fx("rel", 1))),
                Choice("Ignorar", listOf())
            )),

        // ---------- humor
        EventDef("cabra", EventCategory.HUMOR, "goat", "Cabra eleita", "Uma cabra venceu a eleição para vereadora em {p}.", needsProvince = true,
            choices = listOf(
                Choice("Dar posse", listOf(fx("g:YOUTH", 6), fx("rel", 1))),
                Choice("Anular", listOf(fx("g:YOUTH", -3))),
            )),
        EventDef("selfie", EventCategory.HUMOR, "camera", "Imposto da selfie", "Um prefeito quer taxar selfies em pontos turísticos.",
            choices = listOf(
                Choice("Liberar", listOf(fx("money", 0.0005), fx("g:YOUTH", -6))),
                Choice("Proibir", listOf(fx("g:YOUTH", 4))),
            )),
        EventDef("ovni", EventCategory.HUMOR, "ufo", "Luzes no céu", "Milhares juram ter visto um disco voador em {p}.", needsProvince = true,
            choices = listOf(
                Choice("Abrir os arquivos", listOf(fx("g:YOUTH", 5), fx("g:MILITARY", -3))),
                Choice("Era um balão", listOf(fx("g:MILITARY", 2))),
            )),
        EventDef("feriado", EventCategory.HUMOR, "party", "Feriado do pastel", "O Congresso propõe um feriado nacional do pastel.",
            choices = listOf(
                Choice("Sancionar", listOf(fx("all", 3), fx("gdp", -0.001))),
                Choice("Vetar", listOf(fx("g:BUSINESS", 3), fx("g:WORKERS", -3))),
            )),
    )
    val eventById = events.associateBy { it.id }

    // ---------- blocos reais de partida (códigos do mapa)
    data class BlocSeed(val name: String, val military: Boolean, val economic: Boolean, val color: Long, val codes: List<String>)

    val blocSeeds = listOf(
        BlocSeed("OTAN", true, false, 0xFF5B8DEF, "USA CAN GBR FRA DEU ITA ESP PRT NLD BEL LUX NOR DNK ISL TUR GRC POL CZE SVK HUN ROU BGR HRV SVN ALB MNE MKD EST LVA LTU FIN SWE".split(" ")),
        BlocSeed("União Europeia", false, true, 0xFF3F6FD8, "AUT BEL BGR HRV CYP CZE DNK EST FIN FRA DEU GRC HUN IRL ITA LVA LTU LUX MLT NLD POL PRT ROU SVK SVN ESP SWE".split(" ")),
        BlocSeed("Mercosul", false, true, 0xFF3CB371, "BRA ARG URY PRY BOL".split(" ")),
        BlocSeed("BRICS", false, true, 0xFFE0A030, "BRA RUS IND CHN ZAF EGY ETH IRN ARE IDN".split(" ")),
        BlocSeed("ASEAN", false, true, 0xFF33B5A5, "IDN MYS PHL SGP THA BRN VNM LAO MMR KHM".split(" ")),
        BlocSeed("Liga Árabe", false, true, 0xFF4CAF50, "SAU EGY IRQ JOR LBN YEM KWT BHR QAT ARE OMN LBY TUN DZA MAR SDN SOM DJI MRT COM SYR".split(" ")),
        BlocSeed("OTSC", true, false, 0xFFC0504D, "RUS BLR KAZ KGZ TJK ARM".split(" ")),
    )

    // ---------- relações especiais de partida
    val rivalries = listOf(
        Triple("USA", "RUS", -55f), Triple("USA", "CHN", -30f), Triple("USA", "IRN", -75f), Triple("USA", "PRK", -85f),
        Triple("USA", "CUB", -45f), Triple("USA", "VEN", -45f), Triple("ISR", "IRN", -95f), Triple("IND", "PAK", -70f),
        Triple("KOR", "PRK", -80f), Triple("JPN", "PRK", -60f), Triple("RUS", "UKR", -95f), Triple("CHN", "TWN", -75f),
        Triple("ARM", "AZE", -65f), Triple("GRC", "TUR", -20f), Triple("SAU", "IRN", -55f), Triple("MAR", "DZA", -45f),
        Triple("ETH", "ERI", -40f), Triple("CHN", "IND", -30f), Triple("CHN", "JPN", -25f), Triple("RUS", "GBR", -50f),
        Triple("RUS", "POL", -60f), Triple("RUS", "EST", -55f), Triple("RUS", "LVA", -55f), Triple("RUS", "LTU", -55f),
        Triple("SRB", "KOS", -60f), Triple("CYP", "CYN", -60f), Triple("SDN", "SDS", -40f), Triple("SOM", "SOL", -40f),
        Triple("ISR", "SYR", -70f), Triple("ISR", "LBN", -60f), Triple("VEN", "GUY", -45f), Triple("AFG", "PAK", -30f),
        Triple("COD", "RWA", -45f), Triple("EGY", "ETH", -25f), Triple("CHN", "PHL", -30f), Triple("CHN", "VNM", -15f),
    )
    val friendships = listOf(
        Triple("USA", "GBR", 85f), Triple("USA", "CAN", 75f), Triple("USA", "ISR", 75f), Triple("USA", "JPN", 70f),
        Triple("USA", "KOR", 70f), Triple("USA", "AUS", 75f), Triple("GBR", "AUS", 70f), Triple("GBR", "CAN", 70f),
        Triple("FRA", "DEU", 75f), Triple("RUS", "BLR", 85f), Triple("CHN", "PAK", 65f), Triple("CHN", "PRK", 50f),
        Triple("RUS", "CHN", 45f), Triple("RUS", "IRN", 40f), Triple("BRA", "ARG", 45f), Triple("BRA", "PRT", 55f),
        Triple("AUS", "NZL", 85f), Triple("SAU", "ARE", 70f), Triple("USA", "TWN", 55f), Triple("IND", "RUS", 35f),
        Triple("CUB", "VEN", 60f), Triple("TUR", "AZE", 75f), Triple("ARM", "RUS", 30f), Triple("USA", "MEX", 40f),
        Triple("ESP", "PRT", 65f), Triple("SWE", "FIN", 80f), Triple("NOR", "SWE", 75f), Triple("DNK", "NOR", 70f),
        Triple("RUS", "SYR", 40f), Triple("UKR", "POL", 60f), Triple("GBR", "UKR", 55f), Triple("USA", "UKR", 45f),
    )

    val absoluteMonarchies = setOf("SAU", "BRN", "OMN", "SWZ", "QAT", "ARE", "KWT", "BHR", "VAT")
    val constMonarchies = setOf("GBR", "ESP", "SWE", "NOR", "DNK", "NLD", "BEL", "LUX", "JPN", "THA", "MYS", "CAN", "AUS",
        "NZL", "JOR", "MAR", "KHM", "BTN", "LSO", "TON", "MCO", "LIE", "AND", "JAM", "BHS", "BLZ", "PNG", "SLB", "TUV",
        "ATG", "GRD", "KNA", "LCA", "VCT")
    val dictatorships = setOf("CHN", "RUS", "PRK", "IRN", "VNM", "LAO", "CUB", "ERI", "TKM", "TJK", "UZB", "BLR", "SYR",
        "VEN", "NIC", "AZE", "EGY", "TCD", "MLI", "BFA", "NER", "GIN", "SDN", "MMR", "AFG", "GNQ", "CMR", "RWA", "CAF",
        "KAZ", "DJI", "COG", "GAB", "BDI", "SDS", "LBY", "YEM", "TGO", "UGA", "ZWE")
    val parliamentary = setOf("DEU", "ITA", "IND", "ISR", "GRC", "PRT", "AUT", "CZE", "HUN", "IRL", "FIN", "EST", "LVA",
        "LTU", "POL", "SVK", "SVN", "HRV", "PAK", "BGD", "IRQ", "ALB", "MLT", "ISL", "SRB", "BGR", "MNE", "MKD", "MDA",
        "ARM", "GEO", "NPL", "TUN", "KOS", "TTO", "BRB", "DMA", "MUS", "SGP", "LBN", "ETH", "SOM", "FJI", "VUT", "SMR")
    val expansionist = setOf("RUS", "CHN", "PRK", "IRN", "TUR", "AZE", "ISR", "SAU")
    val opportunist = setOf("USA", "IND", "PAK", "VEN", "ETH", "RWA", "EGY", "MAR", "DZA", "SRB", "ERI", "BLR", "SYR")
    val pacific = setOf("CHE", "IRL", "ISL", "NZL", "CRI", "URY", "PRT", "AUT", "LUX", "MLT", "BTN", "MUS", "SYC", "CPV",
        "VAT", "SMR", "MCO", "LIE", "AND", "NOR", "SWE", "FIN", "DNK", "CAN")
    /** Potências nucleares de partida e ogivas no jogo. */
    val nuclear = mapOf("USA" to 12, "RUS" to 12, "CHN" to 6, "FRA" to 3, "GBR" to 3, "IND" to 2, "PAK" to 2, "ISR" to 2, "PRK" to 1)
    /** Multiplicador de força militar inicial (países muito armados em relação ao PIB). */
    val militaryBoost = mapOf("USA" to 1.6f, "RUS" to 3.0f, "CHN" to 1.3f, "PRK" to 8f, "ISR" to 3f, "IRN" to 2.5f,
        "SAU" to 2f, "PAK" to 2.5f, "IND" to 1.5f, "UKR" to 3f, "TUR" to 1.6f, "EGY" to 2f, "KOR" to 1.8f,
        "TWN" to 2.2f, "GRC" to 1.6f, "DZA" to 2f, "SYR" to 3f, "ARM" to 2f, "AZE" to 2f, "ERI" to 4f, "CUB" to 2f,
        "VEN" to 1.5f, "BLR" to 1.8f, "MAR" to 1.5f, "POL" to 1.4f, "FRA" to 1.2f, "GBR" to 1.2f)
}
