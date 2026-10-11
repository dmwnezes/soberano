package com.dmwnezes.soberano

import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.LawInterpreter
import com.dmwnezes.soberano.sim.LawMaker
import com.dmwnezes.soberano.sim.Resolution
import com.dmwnezes.soberano.sim.ResolutionType
import com.dmwnezes.soberano.sim.Treaty
import com.dmwnezes.soberano.sim.TreatyType
import com.dmwnezes.soberano.sim.UnitType
import com.dmwnezes.soberano.ui.visibleArmiesAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Features3Test {

    @Test fun worldSanctionsHurtButDoNotBankrupt() {
        val sim = TestWorld.newSim("BRA", seed = 70)
        val g = sim.game
        val me = sim.player
        val before = sim.economy.growthRate(me)
        // o mundo inteiro sanciona o jogador
        for (n in g.nations) if (n.alive && n.id != me.id) g.treaties += Treaty(TreatyType.SANCTION, n.id, me.id, g.day, g.day + 365 * 2)
        g.treatyCache = null
        val pen = sim.economy.sanctionPenalty(me.id)
        assertTrue("penalidade com teto: $pen", pen in 0.03f..0.04f)
        val after = sim.economy.growthRate(me)
        assertTrue("antes $before depois $after", before - after <= 0.041f)
        val gdp0 = g.natGdp(me.id)
        repeat(365 * 2) { sim.advanceDay() }
        val gdp1 = g.natGdp(me.id)
        assertTrue("PIB não pode desabar: $gdp0 → $gdp1", gdp1 > gdp0 * 0.85)
        // sanções da ONU expiram
        repeat(40) { sim.advanceDay() }
        assertTrue(g.treaties.none { it.type == TreatyType.SANCTION && it.b == me.id && it.until in 0 until g.day })
    }

    @Test fun microStateSanctionIsTiny() {
        val sim = TestWorld.newSim("BRA", seed = 71)
        val g = sim.game
        val small = g.nations.filter { it.alive && it.id != g.player }.minBy { g.natGdp(it.id) }
        g.treaties += Treaty(TreatyType.SANCTION, small.id, g.player, g.day)
        assertTrue(sim.economy.sanctionPenalty(g.player) < 0.001f)
    }

    @Test fun reservistsTrainInEveryState() {
        val sim = TestWorld.newSim("BRA", seed = 72)
        val g = sim.game
        val me = sim.player
        g.rallyPoint = -1
        // tira as tropas para haver espaço no teto e nenhum estado com guarnição
        g.armies.removeAll { it.owner == me.id && it.domain == Domain.LAND }
        g.invalidate()
        me.shares[Area.DEFENSE.ordinal] = 0.06f
        repeat(365) { sim.advanceDay() }
        val places = sim.armiesOf(me.id).filter { it.domain == Domain.LAND }.map { it.province }.distinct()
        assertTrue("reservistas em vários estados: ${places.size}", places.size >= 4)
        // com ponto de reunião marcado, todos vão para lá
        val spots = sim.dev.recruitSpots(me)
        assertTrue(spots.size > 5)
        g.rallyPoint = me.capital
        assertEquals(listOf(me.capital), sim.dev.recruitSpots(me))
    }

    @Test fun troopFilterHidesArmies() {
        val sim = TestWorld.newSim("BRA", seed = 73)
        val all = visibleArmiesAt(sim, 5f).size
        sim.game.troopFilter = 1
        val mine = visibleArmiesAt(sim, 5f)
        assertTrue(mine.all { it.owner == sim.game.player })
        assertTrue(all >= mine.size)
        sim.game.troopFilter = 2
        assertTrue(visibleArmiesAt(sim, 5f).none { !it.battle })
    }

    @Test fun newUnResolutions() {
        val sim = TestWorld.newSim("BRA", seed = 74)
        val g = sim.game
        val un = sim.unitedNations
        val me = sim.player
        val usa = g.nations.first { it.code == "USA" }
        val rus = g.nations.first { it.code == "RUS" }
        // fim das sanções contra o jogador
        g.treaties += Treaty(TreatyType.SANCTION, usa.id, me.id, g.day)
        g.treaties += Treaty(TreatyType.EMBARGO, rus.id, me.id, g.day)
        un.apply(Resolution(ResolutionType.LIFT_SANCTIONS, me.id, -1, usa.id, g.day), listOf(usa.id))
        assertEquals(0, un.sanctionedBy(me.id))
        // embargo de armas encarece tropas e bloqueia venda
        val cost0 = sim.military.unitCost(rus, UnitType.INFANTRY)
        un.apply(Resolution(ResolutionType.ARMS_EMBARGO, rus.id, -1, usa.id, g.day), emptyList())
        assertTrue(sim.military.unitCost(rus, UnitType.INFANTRY) > cost0)
        assertNotNull(sim.dev.armsRefusal(me.id, rus.id, UnitType.INFANTRY, 1))
        // desarmamento
        rus.nukes = 5
        un.apply(Resolution(ResolutionType.DISARM, rus.id, -1, usa.id, g.day), emptyList())
        assertEquals(0, rus.nukes)
        assertNotNull(sim.military.buildNuke(rus))
        // suspensão: não vota
        un.apply(Resolution(ResolutionType.SUSPEND, rus.id, -1, usa.id, g.day), emptyList())
        assertEquals(0, un.vote(rus, Resolution(ResolutionType.CONDEMN, me.id, -1, usa.id, g.day)))
        // reconstrução aumenta o crescimento
        val gr0 = sim.economy.growthRate(me)
        un.apply(Resolution(ResolutionType.RECONSTRUCTION, me.id, -1, usa.id, g.day), listOf(usa.id))
        assertTrue(sim.economy.growthRate(me) > gr0)
        // efeitos expiram
        repeat(365 * 6) { if (it % 30 == 0) { g.day += 30; un.monthly(); g.un.pending = null } }
        assertTrue(g.un.effects.isEmpty())
        // regras para propor
        me.politicalCapital = 100f
        assertNotNull(un.proposeRefusal(ResolutionType.SANCTIONS, me.id))
        assertNotNull(un.proposeRefusal(ResolutionType.DISARM, g.nations.first { it.code == "URY" }.id))
        assertNull(un.proposeRefusal(ResolutionType.AID, me.id))
    }

    @Test fun economicAndMilitaryPolicies() {
        val sim = TestWorld.newSim("BRA", seed = 75)
        val g = sim.game
        val me = sim.player
        val pol = sim.policy
        val t0 = me.treasury
        assertNull(pol.printMoney(me))
        assertTrue(me.treasury > t0)
        assertTrue(me.printed > 0f)
        val gr0 = sim.economy.growthRate(me)
        assertNull(pol.stimulus(me))
        assertTrue(sim.economy.growthRate(me) > gr0)
        assertNotNull(pol.stimulus(me))
        pol.setPolicyRate(me, 14f)
        assertTrue(sim.economy.growthRate(me) < gr0 + 0.012f)
        me.treasury = 1000.0
        assertNull(pol.depositFund(me))
        assertTrue(me.sovereignFund > 0)
        assertNull(pol.withdrawFund(me))
        assertEquals(0.0, me.sovereignFund, 0.0)
        // militar
        val rate0 = sim.dev.recruitRate(me)
        me.politicalCapital = 50f
        assertNull(pol.toggleMobilization(me))
        assertTrue(sim.dev.recruitRate(me) > rate0 * 2f)
        assertNull(pol.drill(me))
        assertTrue(pol.drillOn(me))
        val armies0 = g.armies.size
        assertNull(pol.hireMercs(me, me.capital))
        assertEquals(armies0 + 1, g.armies.size)
        assertNotNull(pol.hireMercs(me, me.capital))
        val a = sim.armiesOf(me.id).first { it.domain == Domain.LAND && !it.battle }
        assertNull(pol.disband(a))
        assertFalse(g.armies.contains(a))
        // bombardeio só em guerra
        val ury = g.nations.first { it.code == "URY" }
        assertNotNull(pol.bombingRefusal(me, ury.id))
        sim.diplomacy.declareWar(me.id, ury.id)
        // põe uma ala de bombardeiros perto da fronteira
        val base = sim.provincesOf(me.id).first { sim.world.provinces[it].name == "Rio Grande do Sul" }
        val wing = sim.military.newArmy(me.id, base, Domain.AIR)
        wing.units[UnitType.BOMBER.ordinal] = 4f
        run {
            val gdp0 = g.natGdp(ury.id)
            assertNull(pol.bomb(me, ury.id))
            assertTrue(g.natGdp(ury.id) < gdp0)
        }
        repeat(60) { sim.advanceDay() }
    }

    @Test fun lawFromText() {
        val r = LawInterpreter.local("Semana de trabalho de 4 dias para todos")
        assertTrue((r.law.groups[Group.WORKERS] ?: 0f) > 5f)
        assertTrue((r.law.groups[Group.BUSINESS] ?: 0f) < 0f)
        assertTrue(r.law.notes.isNotEmpty())
        val neg = LawInterpreter.local("Proibir o porte de armas")
        assertTrue((neg.law.index[Area.SECURITY] ?: 0f) > 0f)
        val bus = LawInterpreter.local("Transporte público gratuito")
        assertTrue(bus.law.notes.none { "armas" in it })
        assertTrue((bus.law.index[Area.INFRA] ?: 0f) > 0f)
        val phones = LawInterpreter.local("Proibir celular nas escolas")
        assertTrue("educação não pode cair: ${phones.law.index}", (phones.law.index[Area.EDUCATION] ?: 0f) > 0f)
        val tax = LawInterpreter.local("Isenção de imposto para pequenas empresas")
        assertTrue(tax.law.revenue < 0f)
        val unknown = LawInterpreter.local("Todo cidadão deve plantar uma árvore de pitanga")
        assertTrue(unknown.law.notes.isNotEmpty())
        // resposta da IA
        val raw = """Claro! ```json
            {"nome":"Creche nas empresas","grupos":{"trabalhadores":8,"empresários":-6,"jovens":4},
            "indices":{"educação":3,"social":5},"crescimento":-0.2,"arrecadacao":"1,5","corrupcao":0,"estabilidade":1,"inflacao":0.3,
            "efeitos":["Mães voltam ao trabalho mais cedo","Empresas grandes gastam mais"]}```"""
        val ai = LawInterpreter.parseAi(raw, "x")
        assertNotNull(ai)
        ai!!
        assertEquals("Creche nas empresas", ai.name)
        assertEquals(-6f, ai.law.groups[Group.BUSINESS]!!, 0.01f)
        assertEquals(5f, ai.law.index[Area.SOCIAL]!!, 0.01f)
        assertEquals(-0.002f, ai.law.growth, 0.0001f)
        assertEquals(0.015f, ai.law.revenue, 0.0001f)
        assertTrue(ai.law.ai)
        assertNull(LawInterpreter.parseAi("não sei", "x"))
        // vira lei de verdade e pode ser aprovada
        val sim = TestWorld.newSim("BRA", seed = 76)
        val cl = LawMaker.createFree(sim.game, "", "Creche obrigatória nas empresas", ai.law)
        sim.player.politicalCapital = 200f
        assertTrue(sim.politics.toggleLaw(sim.player, cl.id))
        assertTrue(cl.id in sim.player.laws)
    }
}
