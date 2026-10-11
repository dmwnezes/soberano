package com.dmwnezes.soberano.sim

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// ---------------------------------------------------------------- enums e tabelas fixas

enum class Regime(val label: String, val elections: Boolean, val congress: Boolean, val decree: Boolean) {
    PRESIDENTIAL("Democracia presidencial", true, true, false),
    PARLIAMENTARY("Parlamentarismo", true, true, false),
    CONST_MONARCHY("Monarquia constitucional", true, true, false),
    ABS_MONARCHY("Monarquia absoluta", false, false, true),
    DICTATORSHIP("Ditadura", false, false, true);

    val democratic get() = elections
}

enum class Title(val label: String, val regime: Regime) {
    PRESIDENT("Presidente", Regime.PRESIDENTIAL),
    PRIME_MINISTER("Primeiro-ministro", Regime.PARLIAMENTARY),
    KING("Rei", Regime.ABS_MONARCHY),
    DICTATOR("Ditador", Regime.DICTATORSHIP),
}

enum class Personality(val label: String, val aggression: Float, val trade: Float) {
    PACIFIC("Pacífico", 0.1f, 1.0f),
    TRADER("Comerciante", 0.3f, 1.6f),
    OPPORTUNIST("Oportunista", 0.8f, 1.0f),
    EXPANSIONIST("Expansionista", 1.4f, 0.7f),
}

/** Áreas do orçamento, em fração do PIB anual. */
enum class Area(val label: String, val k: Float, val typical: Float) {
    HEALTH("Saúde", 0.030f, 0.060f),
    EDUCATION("Educação", 0.025f, 0.050f),
    SECURITY("Segurança", 0.012f, 0.020f),
    INFRA("Infraestrutura", 0.020f, 0.030f),
    SOCIAL("Social", 0.030f, 0.040f),
    ENVIRONMENT("Meio ambiente", 0.006f, 0.005f),
    RESEARCH("Pesquisa", 0.008f, 0.010f),
    DEFENSE("Defesa", 0f, 0.020f);

    val hasIndex get() = this != DEFENSE
}

enum class Group(val label: String, val weight: Float) {
    WORKERS("Trabalhadores", 30f),
    BUSINESS("Empresários", 10f),
    MILITARY("Militares", 8f),
    RELIGIOUS("Religiosos", 15f),
    YOUTH("Jovens", 20f),
    RETIREES("Aposentados", 17f),
}

enum class Domain { LAND, SEA, AIR }

enum class UnitType(
    val label: String,
    val short: String,
    val domain: Domain,
    /** ataque contra forças terrestres */
    val attack: Float,
    val defense: Float,
    /** ataque contra aviões */
    val antiAir: Float,
    /** ataque contra navios */
    val antiShip: Float,
    /** km por dia (aviões: alcance de ação em km) */
    val speed: Float,
    /** US$ bilhões */
    val cost: Double,
    val buildDays: Int,
) {
    INFANTRY("Infantaria", "INF", Domain.LAND, 2f, 4f, 0.5f, 0f, 25f, 2.0, 30),
    ARMOR("Blindados", "BLD", Domain.LAND, 6f, 4f, 0.3f, 0f, 35f, 6.0, 60),
    ARTILLERY("Artilharia", "ART", Domain.LAND, 5f, 1f, 0f, 0f, 22f, 3.0, 45),
    ANTIAIR("Antiaérea", "AA", Domain.LAND, 1f, 2f, 6f, 0f, 25f, 2.5, 40),
    DESTROYER("Destróier", "DST", Domain.SEA, 2f, 4f, 2f, 4f, 600f, 6.0, 120),
    SUBMARINE("Submarino", "SUB", Domain.SEA, 0.5f, 2f, 0f, 6f, 500f, 7.0, 150),
    CARRIER("Porta-aviões", "PA", Domain.SEA, 4f, 6f, 4f, 3f, 500f, 40.0, 365),
    TRANSPORT("Transporte", "TRP", Domain.SEA, 0f, 1f, 0f, 0f, 550f, 2.0, 60),
    FIGHTER("Caças", "CAÇ", Domain.AIR, 2f, 3f, 5f, 1f, 1500f, 4.0, 90),
    BOMBER("Bombardeiros", "BMB", Domain.AIR, 6f, 2f, 0.5f, 3f, 4000f, 8.0, 120),
    DRONE("Drones", "DRN", Domain.AIR, 3f, 1f, 0f, 1f, 1000f, 1.0, 30);

    val hp get() = defense * 3f
    /** manutenção mensal */
    val upkeep get() = cost * 0.025

    companion object {
        val count = entries.size
        fun of(domain: Domain) = entries.filter { it.domain == domain }
    }
}

const val NUKE_COST = 20.0
const val NUKE_DAYS = 180

enum class TreatyType(val label: String, val minRelation: Float) {
    TRADE("Comércio", 10f),
    NON_AGGRESSION("Não agressão", 0f),
    PASSAGE("Passagem de tropas", 30f),
    ALLIANCE("Aliança militar", 50f),
    /** a sanciona b (direcional) */
    SANCTION("Sanção", 999f),
    EMBARGO("Embargo", 999f);

    val bilateral get() = this != SANCTION && this != EMBARGO
}

enum class TechBranch(val label: String) {
    LAND("Terra"), SEA("Mar"), AIR("Ar"), ECONOMY("Economia"), SOCIETY("Sociedade"), NUCLEAR("Nuclear")
}

enum class EconomyDepth(val label: String) { SIMPLE("Simples"), MEDIUM("Média"), FULL("Completa") }
enum class EventFrequency(val label: String, val mult: Float) {
    RARE("Rara", 0.45f), NORMAL("Normal", 1f), FREQUENT("Frequente", 1.9f), VARIABLE("Variável", 1f)
}
enum class EventCategory(val label: String, val base: Float) {
    DISASTER("Desastres", 0.05f),
    HEALTH("Saúde", 0.03f),
    POLITICS("Política", 0.06f),
    ECONOMY("Economia", 0.05f),
    SECURITY("Segurança", 0.045f),
    SCIENCE("Ciência", 0.03f),
    INTERNATIONAL("Internacional", 0.04f),
    HUMOR("Humor", 0.035f),
}

enum class Defeat(val label: String) {
    COUP("Golpe militar"), REVOLUTION("Revolução"), BANKRUPTCY("Falência"), ELECTION("Perder eleição")
}

enum class Victory(val label: String) {
    ECONOMIC("Maior PIB do mundo"), HEGEMONY("Controlar 25% das províncias"), GLOBAL("Dominação global")
}

enum class Difficulty(val label: String, val ai: Float, val economy: Float) {
    EASY("Fácil", 0.7f, 1.15f), NORMAL("Normal", 1f, 1f), HARD("Difícil", 1.3f, 0.9f)
}

// ---------------------------------------------------------------- configuração da partida

@Serializable
data class Settings(
    var elections: Boolean = true,
    var defeats: MutableSet<Defeat> = Defeat.entries.toMutableSet(),
    var victories: MutableSet<Victory> = Victory.entries.toMutableSet(),
    var events: MutableSet<EventCategory> = EventCategory.entries.toMutableSet(),
    var eventFrequency: EventFrequency = EventFrequency.NORMAL,
    var nuclear: Boolean = true,
    var economy: EconomyDepth = EconomyDepth.MEDIUM,
    var difficulty: Difficulty = Difficulty.NORMAL,
    var aiAggression: Float = 1f,
    var seed: Long = System.currentTimeMillis(),
)

@Serializable
data class Flag(
    /** 0 faixas horizontais, 1 faixas verticais, 2 cruz nórdica, 3 diagonal, 4 cantão, 5 círculo central */
    val layout: Int = 0,
    val colors: List<Long> = listOf(0xFF2E7D32, 0xFFFFD54F, 0xFF1565C0),
    /** 0 nada, 1 estrela, 2 sol, 3 lua, 4 escudo */
    val emblem: Int = 1,
)

// ---------------------------------------------------------------- estado

@Serializable
class Nation(
    val id: Int,
    val code: String,
    var name: String,
    var mapColor: Long,
    var custom: Boolean = false,
    var flag: Flag? = null,
    var alive: Boolean = true,
    var regime: Regime = Regime.PRESIDENTIAL,
    var personality: Personality = Personality.TRADER,
    var income: Int = 3,
    var capital: Int = 0,

    // economia (US$ bilhões)
    var treasury: Double = 0.0,
    var debt: Double = 0.0,
    var militaryFund: Double = 0.0,
    var tax: Float = 0.25f,
    val shares: FloatArray = FloatArray(Area.entries.size) { Area.entries[it].typical },
    var growth: Float = 0.02f,
    var inflation: Float = 3f,
    var unemployment: Float = 7f,
    var lastRevenue: Double = 0.0,
    var lastExpense: Double = 0.0,

    // índices 0..100 (um por área com índice; DEFENSE não usa)
    val index: FloatArray = FloatArray(Area.entries.size) { 50f },
    var corruption: Float = 40f,
    var stability: Float = 60f,
    var approval: Float = 50f,
    val groups: FloatArray = FloatArray(Group.entries.size) { 50f },
    val groupMods: FloatArray = FloatArray(Group.entries.size),
    var politicalCapital: Float = 20f,
    var congress: Float = 55f,
    var nextElection: Int = 0,
    val laws: MutableSet<String> = mutableSetOf(),
    var warExhaustion: Float = 0f,
    var lawMonthsNoChange: Int = 0,

    // tecnologia
    val tech: IntArray = IntArray(TechBranch.entries.size) { 1 },
    val techProgress: FloatArray = FloatArray(TechBranch.entries.size),
    var researchFocus: Int = 0,
    var nukes: Int = 0,
    var nukeBuild: Int = 0,

    val queue: MutableList<BuildOrder> = mutableListOf(),
    var casualtiesMonth: Float = 0f,
)

@Serializable
class BuildOrder(val type: UnitType, val province: Int, var daysLeft: Int, val total: Int)

@Serializable
class Army(
    val id: Int,
    var owner: Int,
    var province: Int,
    val domain: Domain,
    val units: FloatArray = FloatArray(UnitType.count),
    var morale: Float = 1f,
    var path: IntArray = IntArray(0),
    /** km já andados no trecho atual */
    var progress: Float = 0f,
    var legKm: Float = 0f,
    var name: String = "",
    var battle: Boolean = false,
    /** alvo de ataque aéreo/naval */
    var target: Int = -1,
) {
    fun total(): Float = units.sum()
    fun alive() = total() > 0.05f
    val moving get() = path.isNotEmpty()
    fun count(t: UnitType) = units[t.ordinal]
    fun divisions(): Float = UnitType.of(Domain.LAND).sumOf { units[it.ordinal].toDouble() }.toFloat()
    fun value(): Double = UnitType.entries.sumOf { units[it.ordinal] * it.cost }
}

@Serializable
class War(
    val id: Int,
    val name: String,
    val attackers: MutableList<Int>,
    val defenders: MutableList<Int>,
    val start: Int,
    var battlesA: Int = 0,
    var battlesD: Int = 0,
) {
    fun side(n: Int): Int = when (n) { in attackers -> 1; in defenders -> 2; else -> 0 }
    fun leaderA() = attackers.first()
    fun leaderD() = defenders.first()
    fun involves(n: Int) = n in attackers || n in defenders
}

@Serializable
class Treaty(val type: TreatyType, val a: Int, val b: Int, val start: Int, val until: Int = -1)

@Serializable
class Bloc(
    val id: Int,
    val name: String,
    val military: Boolean,
    val economic: Boolean,
    val members: MutableList<Int>,
    val founder: Int = -1,
    val color: Long = 0xFF7FB3D5,
)

@Serializable
class Battle(val province: Int, val start: Int, var attackerPower: Float, var defenderPower: Float, var attacker: Int, var defender: Int, var lastDay: Int)

@Serializable
class FeedItem(val day: Int, val icon: String, val text: String, val tone: Int = 0, val province: Int = -1)

@Serializable
class PendingEvent(val def: String, val day: Int, val province: Int = -1, val other: Int = -1)

/** Proposta de outro país para o jogador. */
@Serializable
class Proposal(val from: Int, val kind: String, val day: Int, val value: Double = 0.0)

@Serializable
class GameOver(val win: Boolean, val reason: String)

@Serializable
class Game(
    val version: Int = 1,
    var day: Int = 0,
    var player: Int,
    var leaderTitle: Title = Title.PRESIDENT,
    val settings: Settings,
    val nations: MutableList<Nation>,
    val owner: IntArray,
    val controller: IntArray,
    /** dono original (para resistência e libertação) */
    val core: IntArray,
    val siege: FloatArray,
    val resistance: FloatArray,
    val provPop: DoubleArray,
    val provGdp: DoubleArray,
    val armies: MutableList<Army> = mutableListOf(),
    var nextArmyId: Int = 1,
    var nextWarId: Int = 1,
    val relations: FloatArray,
    val treaties: MutableList<Treaty> = mutableListOf(),
    val wars: MutableList<War> = mutableListOf(),
    val blocs: MutableList<Bloc> = mutableListOf(),
    val battles: MutableList<Battle> = mutableListOf(),
    val feed: MutableList<FeedItem> = mutableListOf(),
    val events: MutableList<PendingEvent> = mutableListOf(),
    val proposals: MutableList<Proposal> = mutableListOf(),
    var gameOver: GameOver? = null,
    val achieved: MutableSet<Victory> = mutableSetOf(),
    var rngState: Long = 1L,
    var eventCalm: Int = 0,
) {
    @Transient var warCache: BooleanArray? = null
    @Transient var treatyCache: TreatyCache? = null
    @Transient var gdpCache: DoubleArray? = null
    @Transient var popCache: DoubleArray? = null
    @Transient var provCount: IntArray? = null

    fun invalidate() { gdpCache = null; popCache = null; provCount = null }
    private fun ensureTotals() {
        if (gdpCache != null) return
        val g = DoubleArray(nations.size); val p = DoubleArray(nations.size); val c = IntArray(nations.size)
        for (i in owner.indices) {
            val o = owner[i]
            // províncias ocupadas rendem pouco para o dono
            val f = if (controller[i] == o) 1.0 else 0.3
            g[o] += provGdp[i] * f; p[o] += provPop[i]; c[o]++
        }
        gdpCache = g; popCache = p; provCount = c
    }
    fun natGdp(id: Int): Double { ensureTotals(); return gdpCache!![id] }
    fun natPop(id: Int): Double { ensureTotals(); return popCache!![id] }
    fun natProvinces(id: Int): Int { ensureTotals(); return provCount!![id] }

    val n get() = nations.size
    fun rel(a: Int, b: Int) = relations[a * n + b]
    fun setRel(a: Int, b: Int, v: Float) {
        val x = v.coerceIn(-100f, 100f)
        relations[a * n + b] = x; relations[b * n + a] = x
    }
    fun addRel(a: Int, b: Int, d: Float) = setRel(a, b, rel(a, b) + d)
}

/** Índice de acordos para consultas rápidas (refeito quando acordos ou blocos mudam). */
class TreatyCache(val keys: HashSet<Long>, val allied: BooleanArray) {
    companion object {
        fun key(t: TreatyType, a: Int, b: Int): Long = t.ordinal * 10_000_000_000L + a * 100_000L + b
    }
}
