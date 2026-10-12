package com.dmwnezes.soberano

import com.dmwnezes.soberano.sim.BuildingType
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Insurgency
import com.dmwnezes.soberano.sim.InsurgentKind
import com.dmwnezes.soberano.sim.Law
import com.dmwnezes.soberano.sim.Group
import com.dmwnezes.soberano.sim.SpeechTone
import com.dmwnezes.soberano.sim.SpeechTopic
import com.dmwnezes.soberano.sim.TechBranch
import com.dmwnezes.soberano.sim.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Features4Test {

    @Test fun attackAcrossTheOcean() {
        val sim = TestWorld.newSim("BRA", seed = 80)
        val g = sim.game
        val me = sim.player
        val ago = g.nations.first { it.code == "AGO" }
        sim.diplomacy.declareWar(me.id, ago.id)
        val a = sim.armiesOf(me.id).first { it.domain == Domain.LAND }
        a.units[UnitType.INFANTRY.ordinal] += 20f
        me.militaryFund = 200.0
        // sem nenhum transporte: a rota sai sozinha e o frete é cobrado
        g.armies.filter { it.owner == me.id && it.domain == Domain.SEA }.forEach { it.units[UnitType.TRANSPORT.ordinal] = 0f }
        val target = ago.capital
        assertNotNull("prévia", sim.military.preview(a, target))
        val fund0 = me.militaryFund + me.treasury
        assertNull(sim.military.order(a, target))
        assertTrue("cobrou frete", me.militaryFund + me.treasury < fund0)
        repeat(120) { sim.advanceDay(); g.events.clear(); g.un.pending = null }
        val inAngola = g.armies.any { it.owner == me.id && g.owner[it.province] == ago.id }
        assertTrue("chegou à Angola ou tomou a capital", inAngola || g.controller[target] == me.id)
    }

    @Test fun neutralOverseasPreviewWorks() {
        val sim = TestWorld.newSim("BRA", seed = 81)
        val a = sim.armiesOf(sim.player.id).first { it.domain == Domain.LAND }
        val nga = sim.game.nations.first { it.code == "NGA" }
        // ainda sem guerra: a prévia (para "Declarar guerra e atacar") precisa achar o caminho
        assertNotNull(sim.military.preview(a, nga.capital))
    }

    @Test fun spaceRace() {
        val sim = TestWorld.newSim("BRA", seed = 82)
        val me = sim.player
        me.tech[TechBranch.AIR.ordinal] = 6
        me.treasury = 500.0
        val m0 = sim.space.combatMult(me.id)
        assertNull(sim.space.launchSatellite(me))
        assertNotNull(sim.space.launchSatellite(me))
        repeat(sim.space.satDays) { sim.space.daily() }
        assertEquals(1, me.satellites)
        assertNull(sim.space.launchSatellite(me)); repeat(sim.space.satDays) { sim.space.daily() }
        assertTrue(sim.space.combatMult(me.id) > m0)
        assertNull(sim.space.startStage(me))
        repeat(sim.space.stationDays) { sim.space.daily() }
        assertEquals(1, me.spaceStage)
        assertEquals(me.id, sim.game.stationFirst)
    }

    @Test fun navalBlockade() {
        val sim = TestWorld.newSim("BRA", seed = 83)
        val g = sim.game
        val me = sim.player
        val ury = g.nations.first { it.code == "URY" }
        assertNotNull(sim.trade.blockadeRefusal(me, ury.id))
        sim.diplomacy.declareWar(me.id, ury.id)
        val gr0 = sim.economy.growthRate(ury)
        assertNull(sim.trade.toggleBlockade(me, ury.id))
        assertTrue(sim.trade.blockaded(ury.id))
        assertTrue(sim.economy.growthRate(ury) < gr0)
        assertTrue(sim.trade.canals.size >= 2)
        assertTrue(sim.trade.routes(me.id).isNotEmpty() || g.treaties.none { it.type == com.dmwnezes.soberano.sim.TreatyType.TRADE && (it.a == me.id || it.b == me.id) })
    }

    @Test fun insurgentsAppearAndCanBeFought() {
        val sim = TestWorld.newSim("BRA", seed = 84)
        val g = sim.game
        val me = sim.player
        val p = sim.provincesOf(me.id).first { it != me.capital }
        val ins = Insurgency(g.nextInsurgentId++, p, me.id, InsurgentKind.GUERRILLA, "Teste", 3f, g.day)
        g.insurgents += ins
        me.treasury = 100.0
        repeat(5) { if (ins in g.insurgents) sim.insurgency.operation(me, ins) }
        assertFalse(ins in g.insurgents)
        // em país muito instável eles aparecem sozinhos
        val weak = g.nations.first { it.code == "HTI" }
        weak.stability = 5f; weak.index[com.dmwnezes.soberano.sim.Area.SECURITY.ordinal] = 5f
        repeat(36) { sim.insurgency.monthly(); weak.stability = 5f }
        assertTrue(g.insurgents.isNotEmpty())
    }

    @Test fun congressVotesLaws() {
        val sim = TestWorld.newSim("BRA", seed = 85)
        val g = sim.game
        val me = sim.player
        assertTrue(g.parties.size >= 4)
        assertEquals(sim.congress.totalSeats, g.parties.sumOf { it.seats })
        val friendly = Law("t1", "Teste", "law", "", groups = mapOf(Group.WORKERS to 6f, Group.RETIREES to 4f))
        val (y, n) = sim.congress.tally(friendly)
        assertEquals(sim.congress.totalSeats, y + n)
        // base fraca: oferecer cargos traz partidos
        for (p in g.parties) p.inCoalition = false
        me.politicalCapital = 100f
        val p = g.parties.maxBy { it.seats }
        p.mood = 60f
        assertNull(sim.congress.offerPosts(p))
        assertTrue(p.inCoalition)
        // lei muito impopular com todos é rejeitada
        for (q in g.parties) { q.inCoalition = false; q.mood = 10f }
        val hated = Law("custom_hate", "Ódio", "law", "", groups = Group.entries.associateWith { -12f })
        com.dmwnezes.soberano.sim.Content.customLaws[hated.id] = hated
        assertFalse(sim.politics.toggleLaw(me, hated.id))
        assertNotNull(sim.politics.lastFail)
    }

    @Test fun speechChangesTheCountry() {
        val sim = TestWorld.newSim("BRA", seed = 86)
        val me = sim.player
        val r = sim.speeches.give(me, SpeechTopic.UNITY, SpeechTone.CALM)
        assertNotNull(r)
        assertTrue(r!!.headline.isNotBlank())
        assertNull(sim.speeches.give(me, SpeechTopic.UNITY, SpeechTone.CALM))
        assertTrue(sim.speeches.waitDays(me) > 0)
    }

    @Test fun currencyReactsToPolicy() {
        val sim = TestWorld.newSim("BRA", seed = 87)
        val me = sim.player
        assertTrue(sim.currency.rateText(me).contains("R$"))
        me.inflation = 30f; me.printed = 8f; me.policyRate = 2f
        repeat(12) { sim.currency.monthly(me) }
        assertTrue("moeda caiu: ${me.currency}", me.currency < 0.9f)
        val c0 = me.currency
        me.treasury = 500.0
        assertNull(sim.currency.intervene(me))
        assertTrue(me.currency > c0)
        assertTrue(sim.currency.inflationEffect(me) > 0f)
    }

    @Test fun provinceBuildings() {
        val sim = TestWorld.newSim("BRA", seed = 88)
        val g = sim.game
        val me = sim.player
        me.treasury = 500.0
        val p = me.capital
        val inland = !sim.world.provinces[p].coastal
        if (inland) assertNotNull(sim.buildings.refusal(me, p, BuildingType.PORT))
        val gdp0 = g.provGdp[p]
        assertNull(sim.buildings.build(me, p, BuildingType.FACTORY))
        assertNotNull(sim.buildings.build(me, p, BuildingType.FACTORY))
        repeat(BuildingType.FACTORY.days) { sim.buildings.daily() }
        assertTrue(sim.buildings.has(p, BuildingType.FACTORY))
        assertTrue(g.provGdp[p] > gdp0)
        assertNull(sim.buildings.build(me, p, BuildingType.HOSPITAL))
        repeat(BuildingType.HOSPITAL.days) { sim.buildings.daily() }
        assertTrue(sim.buildings.indexBonus(me, com.dmwnezes.soberano.sim.Area.HEALTH) > 0f)
        assertNull(sim.buildings.build(me, p, BuildingType.BASE))
        repeat(BuildingType.BASE.days) { sim.buildings.daily() }
        assertEquals(1.25f, sim.buildings.defenseMult(p, me.id), 0.001f)
    }
}
