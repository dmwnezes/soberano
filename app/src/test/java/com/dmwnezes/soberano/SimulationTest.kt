package com.dmwnezes.soberano

import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Area
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.Fmt
import com.dmwnezes.soberano.sim.Game
import com.dmwnezes.soberano.sim.PeaceTerms
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.sim.TreatyType
import com.dmwnezes.soberano.sim.UnitType
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SimulationTest {

    @Test fun mapLoads() {
        val w = TestWorld.world
        assertTrue(w.countries.size > 190)
        assertTrue(w.provinces.size > 1500)
        val bra = w.byCode["BRA"]!!
        assertEquals("Brasil", bra.name)
        assertEquals(27, w.provinces.count { it.country == bra.index })
        // Brasília fica no Distrito Federal
        assertEquals("Distrito Federal", w.provinces[bra.capital].name)
        // toque no meio do Brasil cai numa província brasileira
        val p = w.provinces[bra.capital]
        assertEquals(bra.capital, w.hit(p.cx, p.cy))
        // São Paulo ~ 1.000 km de Brasília
        val sp = w.provinces.first { it.name == "São Paulo" }
        val km = w.distanceKm(bra.capital, sp.id)
        assertTrue("distância $km", km in 600.0..1200.0)
    }

    @Test fun newGameIsSane() {
        val sim = TestWorld.newSim()
        val g = sim.game
        val me = sim.player
        assertEquals("BRA", me.code)
        val gdp = g.natGdp(me.id)
        assertTrue("PIB $gdp", gdp in 1500.0..2500.0)
        assertTrue(sim.armiesOf(me.id).isNotEmpty())
        val usa = g.nations.first { it.code == "USA" }
        assertTrue(sim.power(usa.id) > sim.power(me.id))
        assertTrue(sim.allied(usa.id, g.nations.first { it.code == "GBR" }.id))
        println("Brasil: PIB ${Fmt.money(gdp)}, exércitos ${sim.armiesOf(me.id).size}, " +
            "divisões ${sim.armiesOf(me.id).sumOf { it.divisions().toDouble() }}")
        println("EUA: ${sim.armiesOf(usa.id).joinToString { "${it.name}:${it.total()}" }}")
    }

    @Test fun twoYearsRunWithoutCrashing() {
        val sim = TestWorld.newSim(seed = 7)
        // sem derrota/vitória: a partida roda os 10 anos inteiros
        sim.game.settings.defeats.clear(); sim.game.settings.victories.clear()
        val start = System.nanoTime()
        repeat(3650) { sim.advanceDay() }
        val ms = (System.nanoTime() - start) / 1e6
        val g = sim.game
        val me = sim.player
        println("10 anos em ${ms.toInt()} ms; guerras: ${g.wars.map { it.name }}; vivos: ${g.nations.count { it.alive }}")
        println("Brasil: aprovação ${me.approval}, estabilidade ${me.stability}, PIB ${Fmt.money(g.natGdp(me.id))}, " +
            "dívida ${Fmt.money(me.debt)}, crescimento ${me.growth}, saúde ${me.index[Area.HEALTH.ordinal]}")
        for (n in g.nations.filter { it.alive }.sortedByDescending { g.natGdp(it.id) }.take(8))
            println("  ${n.name}: PIB ${Fmt.money(g.natGdp(n.id))} cresc ${Fmt.one(n.growth * 100)}% aprov ${n.approval.toInt()} " +
                "estab ${n.stability.toInt()} dívida/PIB ${Fmt.one((n.debt / g.natGdp(n.id)).toFloat())} imposto ${n.tax}")
        g.feed.take(15).forEach { println("  feed: ${it.text}") }
        assertTrue(ms < 60_000)
        assertNull(g.gameOver)
        assertTrue("passaram 10 anos", g.day >= 3650)
        assertTrue(me.approval in 0f..100f)
        assertTrue(g.nations.count { it.alive } > 150)
        // ninguém com números absurdos
        for (n in g.nations.filter { it.alive }) {
            assertTrue("${n.name} estab", n.stability in 0f..100f)
            assertTrue("${n.name} PIB", g.natGdp(n.id).isFinite())
        }
    }

    @Test fun warAndConquest() {
        val sim = TestWorld.newSim("BRA", seed = 3)
        val g = sim.game
        val me = sim.player
        val ury = g.nations.first { it.code == "URY" }
        // exército forte o bastante para a conquista não depender da sorte
        sim.armiesOf(me.id).first { it.domain == Domain.LAND }.units[UnitType.ARMOR.ordinal] += 20f
        val w = sim.diplomacy.declareWar(me.id, ury.id)
        assertNotNull(w)
        assertTrue(sim.atWar(me.id, ury.id))
        // manda todos os exércitos para a capital uruguaia
        val target = ury.capital
        var ordered = 0
        for (a in sim.armiesOf(me.id).filter { it.domain == Domain.LAND }) if (sim.military.order(a, target) == null) ordered++
        assertTrue(ordered > 0)
        repeat(200) { sim.advanceDay() }
        val taken = g.owner.indices.count { g.owner[it] == ury.id && g.controller[it] == me.id }
        println("Províncias uruguaias ocupadas: $taken / ${sim.provincesOf(ury.id).size}; batalhas A ${w!!.battlesA} D ${w.battlesD}")
        assertTrue("capital ocupada", g.controller[target] == me.id || taken > 0)
        assertEquals("capital ocupada", me.id, g.controller[target])
        // paz ficando com o ocupado (forçada: a pontuação depende do que o inimigo tomou de volta)
        if (g.wars.contains(w)) sim.diplomacy.makePeace(w, me.id, PeaceTerms.TAKE_OCCUPIED)
        assertTrue(g.owner.indices.any { g.owner[it] == me.id && g.core[it] == ury.id })
    }

    @Test fun productionAndTreaties() {
        val sim = TestWorld.newSim("BRA", seed = 5)
        val me = sim.player
        me.militaryFund = 100.0
        assertNull(sim.military.buy(me, UnitType.ARMOR, me.capital, 2))
        assertEquals(2, me.queue.size)
        repeat(70) { sim.advanceDay() }
        assertEquals(0, me.queue.size)
        val arg = sim.game.nations.first { it.code == "ARG" }
        sim.game.setRel(me.id, arg.id, 80f)
        assertNull(sim.diplomacy.propose(TreatyType.ALLIANCE, me.id, arg.id))
        assertTrue(sim.allied(me.id, arg.id))
        // navio só no litoral
        val inland = sim.provincesOf(me.id).first { !sim.world.provinces[it].coastal }
        assertNotNull(sim.military.buy(me, UnitType.DESTROYER, inland))
    }

    @Test fun saveAndLoadRoundTrip() {
        val sim = TestWorld.newSim(seed = 9)
        repeat(40) { sim.advanceDay() }
        val dir = Files.createTempDirectory("sob").toFile()
        val store = SaveStore(dir)
        store.save("1", sim.game)
        val loaded = store.load("1")
        assertNotNull(loaded)
        assertEquals(sim.game.day, loaded!!.day)
        assertEquals(sim.game.armies.size, loaded.armies.size)
        assertEquals(SaveStore.json.encodeToString(sim.game), SaveStore.json.encodeToString(loaded))
        val sim2 = Sim(TestWorld.world, loaded)
        repeat(40) { sim2.advanceDay() }
        val size = dir.listFiles()!!.sumOf { it.length() }
        println("save: ${size / 1024} KB")
    }

    @Test fun eventsAppearForPlayer() {
        val sim = TestWorld.newSim(seed = 11)
        var seen = 0
        repeat(365) {
            sim.advanceDay()
            while (sim.game.events.isNotEmpty()) { sim.events.resolve(sim.game.events.first(), 0); seen++ }
        }
        println("eventos em 1 ano: $seen")
        assertTrue(seen in 1..60)
    }

    @Test fun customLawWorksAndSurvivesSave() {
        val sim = TestWorld.newSim(seed = 21)
        val me = sim.player
        me.politicalCapital = 100f
        val before = sim.politics.groupTarget(me, com.dmwnezes.soberano.sim.Group.YOUTH)
        val cl = com.dmwnezes.soberano.sim.LawMaker.create(sim.game, "Pacote verde", listOf("desmatamento_zero", "cannabis"), 3)
        val law = com.dmwnezes.soberano.sim.Content.law(cl.id)!!
        val a = com.dmwnezes.soberano.sim.LawMaker.analyze(law, me, sim.game.natGdp(me.id))
        println("resumo: ${a.summary}; ${a.consequences.joinToString { it.text }}")
        assertTrue(a.supporters.contains(com.dmwnezes.soberano.sim.Group.YOUTH))
        assertTrue(a.opponents.contains(com.dmwnezes.soberano.sim.Group.RELIGIOUS))
        assertTrue(sim.politics.toggleLaw(me, cl.id))
        assertTrue(sim.politics.groupTarget(me, com.dmwnezes.soberano.sim.Group.YOUTH) > before + 10)
        // salvar e carregar mantém a lei funcionando
        val dir = Files.createTempDirectory("law").toFile()
        SaveStore(dir).save("1", sim.game)
        com.dmwnezes.soberano.sim.Content.customLaws.clear()
        val sim2 = Sim(TestWorld.world, SaveStore(dir).load("1")!!)
        assertNotNull(com.dmwnezes.soberano.sim.Content.law(cl.id))
        assertTrue(cl.id in sim2.player.laws)
    }

    @Test fun orderPreviewForNeutralTarget() {
        val sim = TestWorld.newSim("BRA", seed = 22)
        val a = sim.armiesOf(sim.player.id).first { it.domain == Domain.LAND }
        val arg = sim.game.nations.first { it.code == "ARG" }
        val pv = sim.military.preview(a, arg.capital)
        assertNotNull(pv)
        println("até Buenos Aires: ${pv!!.second} dias por ${pv.first.size} províncias")
        // sem guerra, a ordem direta é recusada
        assertNotNull(sim.military.order(a, arg.capital))
    }

    @Test fun nuclearStrike() {
        val sim = TestWorld.newSim("USA", seed = 12)
        val g: Game = sim.game
        val me = sim.player
        val prk = g.nations.first { it.code == "PRK" }
        val pop = g.provPop[prk.capital]
        assertNull(sim.military.nuke(me.id, prk.capital))
        assertTrue(g.provPop[prk.capital] < pop * 0.9)
        assertTrue(sim.atWar(me.id, prk.id))
    }
}
