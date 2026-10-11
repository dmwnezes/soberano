package com.dmwnezes.soberano

import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.Promise
import com.dmwnezes.soberano.sim.Resource
import com.dmwnezes.soberano.sim.ResolutionType
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.sim.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class Features2Test {

    @Test fun generalsLevelUpAndFortsSlowSiege() {
        val sim = TestWorld.newSim("BRA", seed = 50)
        val g = sim.game
        val me = sim.player
        val a = sim.armiesOf(me.id).first { it.domain == Domain.LAND }
        me.politicalCapital = 50f
        val gen = g.generalPool.first()
        assertNull(sim.military.appoint(a, gen))
        assertEquals(gen, a.general)
        // fortificação
        me.treasury = 500.0
        val ury = g.nations.first { it.code == "URY" }
        assertNull(sim.military.fortify(me, me.capital))
        repeat(91) { sim.advanceDay() }
        assertEquals(1, g.fort[me.capital])
        // guerra: general ganha experiência
        a.units[UnitType.ARMOR.ordinal] += 20f
        sim.diplomacy.declareWar(me.id, ury.id)
        sim.military.order(a, ury.capital)
        repeat(200) { sim.advanceDay() }
        println("general ${gen.name}: nível ${gen.level}, xp ${gen.xp}, vitórias ${gen.wins}")
        assertTrue(gen.level > 1 || gen.xp > 0f || gen.wins > 0)
    }

    @Test fun seaLandingAndEvacuation() {
        val sim = TestWorld.newSim("USA", seed = 51)
        val g = sim.game
        val me = sim.player
        val cub = g.nations.first { it.code == "CUB" }
        sim.diplomacy.declareWar(me.id, cub.id)
        val a = sim.armiesOf(me.id).first { it.domain == Domain.LAND }
        val beach = sim.provincesOf(cub.id).first { sim.world.provinces[it].coastal }
        val pv = sim.military.previewSea(a, beach)
        assertNotNull(pv)
        println("desembarque em ${sim.world.provinces[beach].name}: ${pv!!.second} dias, ${pv.first.size} passos")
        assertNull(sim.military.orderBySea(a, beach))
        assertEquals(beach, a.path.last())
    }

    @Test fun civilWarCreatesNewCountryAndSaves() {
        val sim = TestWorld.newSim("BRA", seed = 52)
        val g = sim.game
        val before = g.nations.size
        val parent = g.nations.first { it.code == "ARG" }
        val rebel = sim.civil.secession(parent)
        assertNotNull(rebel)
        assertEquals(before + 1, g.nations.size)
        assertTrue(sim.atWar(rebel!!.id, parent.id))
        assertTrue(g.natProvinces(rebel.id) >= 2)
        println("país novo: ${rebel.name} com ${g.natProvinces(rebel.id)} províncias")
        repeat(120) { sim.advanceDay() }
        // salvar e carregar com o país novo
        val dir = Files.createTempDirectory("cw").toFile()
        SaveStore(dir).save("1", g)
        val sim2 = Sim(TestWorld.world, SaveStore(dir).load("1")!!)
        assertEquals(g.nations.size, sim2.game.nations.size)
        repeat(60) { sim2.advanceDay() }
    }

    @Test fun liberationRevivesAnnexedCountry() {
        val sim = TestWorld.newSim("BRA", seed = 53)
        val g = sim.game
        val me = sim.player
        val ury = g.nations.first { it.code == "URY" }
        for (p in sim.provincesOf(ury.id)) sim.diplomacy.transfer(p, me.id)
        sim.diplomacy.cleanupDead()
        assertTrue(!ury.alive)
        val p = g.owner.indices.first { g.core[it] == ury.id }
        assertTrue(sim.civil.liberate(ury.id, p))
        assertTrue(ury.alive)
        assertTrue(sim.atWar(ury.id, me.id))
    }

    @Test fun unVoteResolves() {
        val sim = TestWorld.newSim("BRA", seed = 54)
        val g = sim.game
        val me = sim.player
        me.politicalCapital = 40f
        val prk = g.nations.first { it.code == "PRK" }
        assertNull(sim.unitedNations.propose(ResolutionType.CONDEMN, prk.id))
        assertNotNull(g.un.pending)
        val (y, n, a) = sim.unitedNations.forecast(g.un.pending!!)
        println("previsão: $y sim, $n não, $a abstenções; veto ${sim.unitedNations.vetoThreat(g.un.pending!!)}")
        val rec = sim.unitedNations.resolve(1)!!
        println("resultado: ${rec.yes} sim, ${rec.no} não, veto ${rec.vetoBy}, aprovada ${rec.passed}")
        assertNull(g.un.pending)
        assertTrue(rec.yes + rec.no + rec.abstain > 150)
        // sessões automáticas acontecem
        g.un.nextSession = g.day + 1
        repeat(40) { sim.advanceDay() }
        assertTrue(g.un.pending != null || g.un.history.size > 1)
    }

    @Test fun campaignAndPromises() {
        val sim = TestWorld.newSim("BRA", seed = 55)
        val g = sim.game
        val me = sim.player
        me.nextElection = g.day + 100
        me.treasury = 500.0
        val before = sim.politics.electionChance(me)
        repeat(31) { sim.advanceDay() }
        assertTrue(g.campaign.opponent.isNotEmpty())
        assertNull(sim.politics.campaigns.rally(Group.WORKERS))
        assertNull(sim.politics.campaigns.ads())
        assertNull(sim.politics.campaigns.promise(Promise.HEALTH))
        println("chance antes ${before}, depois ${sim.politics.electionChance(me)}; debate: ${sim.politics.campaigns.debate()}")
        repeat(120) { sim.advanceDay() }
        assertNotNull(g.election)
        println("eleição: ${g.election!!.myShare}% contra ${g.election!!.opponent}, venceu ${g.election!!.won}")
    }

    @Test fun resourceDealsAndArms() {
        val sim = TestWorld.newSim("SAU", seed = 56)
        val g = sim.game
        val me = sim.player
        val jpn = g.nations.first { it.code == "JPN" }
        g.setRel(me.id, jpn.id, 40f)
        val amount = (sim.dev.surplus(me.id, Resource.OIL) * 0.3f)
        val t0 = me.treasury
        assertNull(sim.dev.sellResource(me.id, jpn.id, Resource.OIL, amount))
        repeat(62) { sim.advanceDay() }
        println("petróleo: contrato de ${amount}/ano; tesouro ${t0} → ${me.treasury}")
        assertEquals(1, g.deals.count { it.seller == me.id })
        // venda de armas
        val inf = sim.dev.armsAvailable(me.id, UnitType.INFANTRY)
        jpn.treasury = 1000.0
        assertNull(sim.dev.sellArms(me.id, jpn.id, UnitType.INFANTRY, 2))
        assertEquals(inf - 2, sim.dev.armsAvailable(me.id, UnitType.INFANTRY))
    }

    @Test fun newspaperPublishedMonthly() {
        val sim = TestWorld.newSim("BRA", seed = 57)
        repeat(65) { sim.advanceDay() }
        val p = sim.game.paper
        assertNotNull(p)
        println("jornal: ${p!!.headline} | ${p.sub} | ${p.national} | ${p.world} | ${p.economy}")
        assertTrue(p.economy.isNotEmpty())
    }

    @Test fun tenYearsWithEverything() {
        val sim = TestWorld.newSim("BRA", seed = 58)
        sim.game.settings.defeats.clear(); sim.game.settings.victories.clear()
        repeat(3650) {
            sim.advanceDay()
            sim.game.events.clear()
            if (sim.game.un.pending != null) sim.unitedNations.resolve(0)
        }
        val g = sim.game
        println("10 anos: ${g.nations.size} países (${g.nations.count { it.alive }} vivos), ${g.deals.size} contratos, " +
            "${g.un.history.size} resoluções, fortes ${g.fort.count { it > 0 }}, generais ${g.armies.count { it.general != null }}")
        g.feed.take(12).forEach { println("  ${it.text}") }
        for (n in g.nations.filter { it.alive }) assertTrue(n.name, g.natGdp(n.id).isFinite() && n.stability in 0f..100f)
        assertTrue(g.nations[g.player].index[Area.HEALTH.ordinal] in 0f..100f)
    }
}
