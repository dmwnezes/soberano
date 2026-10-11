package com.dmwnezes.soberano

import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Resource
import com.dmwnezes.soberano.sim.TreatyType
import com.dmwnezes.soberano.sim.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    @Test fun autoWarAttacksTarget() {
        val sim = TestWorld.newSim("BRA", seed = 40)
        val g = sim.game
        val me = sim.player
        val pry = g.nations.first { it.code == "PRY" }
        sim.diplomacy.declareWar(me.id, pry.id)
        g.autoWar = true; g.autoTarget = pry.id
        me.militaryFund = 60.0
        repeat(240) { sim.advanceDay() }
        val taken = g.controller.indices.count { g.owner[it] == pry.id && g.controller[it] == me.id }
        println("piloto automático: ${taken} províncias paraguaias tomadas; fila ${me.queue.size}; guerras ${g.wars.map { it.name }}")
        assertTrue("tomou algo ou fez paz", taken > 0 || !sim.atWar(me.id, pry.id))
    }

    @Test fun everyCountryRecruitsAutomatically() {
        val sim = TestWorld.newSim("BRA", seed = 41)
        val g = sim.game
        val before = g.nations.associate { it.id to sim.dev.landDivisions(it.id) }
        repeat(365) { sim.advanceDay() }
        val grew = g.nations.count { it.alive && sim.dev.landDivisions(it.id) > (before[it.id] ?: 0f) + 0.5f }
        val bra = sim.player
        println("países que cresceram: $grew; Brasil ${before[bra.id]} → ${sim.dev.landDivisions(bra.id)} (teto ${sim.dev.manpowerCap(bra.id)})")
        assertTrue("cresceram $grew", grew > 100)
        // quem começou abaixo do teto não passa dele só com reservistas
        for (n in g.nations.filter { it.alive && (before[it.id] ?: 0f) < sim.dev.manpowerCap(it.id) && it.queue.isEmpty() })
            assertTrue(n.name, sim.dev.landDivisions(n.id) <= sim.dev.manpowerCap(n.id) * 1.6f + 10f)
    }

    @Test fun gatherAndMerge() {
        val sim = TestWorld.newSim("USA", seed = 42)
        val me = sim.player
        val lands = sim.armiesOf(me.id).filter { it.domain == Domain.LAND }
        assertTrue(lands.size > 3)
        val n = sim.military.gatherAt(me.id, me.capital)
        assertTrue(n > 0)
        repeat(200) { sim.advanceDay() }
        val atCap = sim.armiesOf(me.id).filter { it.domain == Domain.LAND && it.province == me.capital }
        println("exércitos na capital: ${atCap.size}, divisões ${atCap.sumOf { it.divisions().toDouble() }}")
        assertEquals("fundidos num só", 1, atCap.size)
    }

    @Test fun resourcesAndMinisters() {
        val sim = TestWorld.newSim("SAU", seed = 43)
        val me = sim.player
        val bal = sim.dev.tradeBalance(me.id)
        println("Arábia: saldo de recursos ${Fmt.money(bal.toDouble())}/ano; petróleo ${sim.dev.production(me.id, Resource.OIL)}")
        assertTrue(bal > 50f)
        val jpn = sim.game.nations.first { it.code == "JPN" }
        assertTrue(sim.dev.tradeBalance(jpn.id) < 0f)
        assertEquals(6, sim.game.ministers.size)
        me.politicalCapital = 50f
        assertNull(sim.dev.hire(sim.game.candidates.first()))
        me.treasury = 1000.0
        assertNull(sim.dev.start(me, sim.dev.projectById["hospitais"]!!))
        repeat(800) { sim.advanceDay() }
        assertTrue("hospitais" in me.built)
    }

    @Test fun ultimatumAndGuarantee() {
        val sim = TestWorld.newSim("USA", seed = 44)
        val g = sim.game
        val me = sim.player
        val mex = g.nations.first { it.code == "MEX" }
        g.setRel(me.id, mex.id, 0f)
        val prov = sim.diplomacy.borderProvinces(me.id, mex.id).first()
        val (ok, msg) = sim.diplomacy.ultimatum(me.id, mex.id, prov)
        println("ultimato: $ok $msg")
        assertTrue(ok)
        assertEquals(me.id, g.owner[prov])
        // garantia: o garantidor entra na guerra
        val gtm = g.nations.first { it.code == "GTM" }
        val bra = g.nations.first { it.code == "BRA" }
        g.treaties += com.dmwnezes.soberano.sim.Treaty(TreatyType.GUARANTEE, bra.id, gtm.id, 0)
        g.treatyCache = null
        val w = sim.diplomacy.declareWar(mex.id, gtm.id)!!
        assertTrue(bra.id in w.defenders)
        assertTrue(sim.diplomacy.reasons(me.id, mex.id).isNotEmpty())
    }

    @Test fun distributeEvenlyAcrossProvinces() {
        val sim = TestWorld.newSim("BRA", seed = 45)
        val g = sim.game
        val me = sim.player
        g.autoMerge = true
        // 100 divisões de infantaria numa tropa só
        val main = sim.armiesOf(me.id).first { it.domain == Domain.LAND }
        for (t in UnitType.of(Domain.LAND)) main.units[t.ordinal] = 0f
        main.units[UnitType.INFANTRY.ordinal] = 100f
        for (a in sim.armiesOf(me.id).filter { it.domain == Domain.LAND && it !== main }) g.armies.remove(a)
        val (divs, provs) = sim.military.distributeEvenly(me.id, borderOnly = false)
        println("dividiu $divs divisões em $provs províncias")
        assertEquals(100, divs)
        assertTrue(provs >= 20)
        repeat(400) { sim.advanceDay() }
        g.events.clear()
        val targets = sim.military.distributionTargets(me.id, false).take(provs)
        val per = targets.map { p -> sim.armiesAt(p).filter { it.owner == me.id && it.domain == Domain.LAND }.sumOf { it.count(UnitType.INFANTRY).toDouble() } }
        println("por província: $per")
        val base = 100 / provs
        // reservistas podem somar na capital; o resto fica com a cota
        assertTrue(per.count { it >= base } >= provs - 1)
    }
}
