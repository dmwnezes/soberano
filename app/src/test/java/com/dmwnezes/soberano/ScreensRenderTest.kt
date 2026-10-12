package com.dmwnezes.soberano

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.sim.PendingEvent
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.ui.GameController
import com.dmwnezes.soberano.ui.GameScreen
import com.dmwnezes.soberano.ui.HomeScreen
import com.dmwnezes.soberano.ui.MapMode
import com.dmwnezes.soberano.ui.NewGameFlow
import com.dmwnezes.soberano.ui.SoberanoTheme
import com.dmwnezes.soberano.ui.Tab
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi", sdk = [34])
class ScreensRenderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/screens").also { it.mkdirs() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun controller(sim: Sim) = GameController(sim, SaveStore(Files.createTempDirectory("s").toFile()), MainScope())

    @Test fun home() {
        val sim = TestWorld.newSim()
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { HomeScreen(sim, SaveStore(Files.createTempDirectory("h").toFile()), {}, {}) } }
        shot("01_home")
    }

    @Test fun pickCountry() {
        val sim = TestWorld.newSim()
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { NewGameFlow(sim, {}, { _, _, _, _ -> }) } }
        shot("02_pais")
    }

    @Test fun gameScreens() {
        val sim = TestWorld.newSim("BRA", seed = 4)
        repeat(20) { sim.advanceDay() }
        val c = controller(sim)
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("g").toFile())) } }
        shot("03_jogo")
        // província selecionada
        rule.runOnIdle { c.selectProvince(sim.player.capital) }
        shot("04_provincia")
        // exército selecionado e marchando
        val army = sim.armiesOf(sim.player.id).first { it.domain == Domain.LAND }
        rule.runOnIdle {
            sim.military.order(army, sim.world.provinces.first { it.name == "Rio Grande do Sul" }.id)
            c.selArmy = army.id; c.refresh()
        }
        shot("05_exercito")
        // ordem de ataque: exército selecionado + toque em província vizinha estrangeira
        rule.runOnIdle {
            sim.military.stop(army)
            c.chooseTarget(sim.game.nations.first { it.code == "PRY" }.capital)
        }
        shot("06_ordem_neutro")
        rule.runOnIdle { c.clearTarget(); c.lawEditor = true; c.refresh() }
        shot("07_criar_lei")
        rule.runOnIdle { c.lawEditor = false; c.lawInfo = "imposto_fortunas"; c.refresh() }
        shot("08_lei_info")
        rule.runOnIdle { c.lawInfo = null; c.refresh() }
        rule.runOnIdle { repeat(70) { sim.advanceDay() }; sim.game.events.clear(); sim.game.proposals.clear() }
        for ((i, t) in Tab.entries.withIndex()) {
            val subs = when (t) { Tab.DIPLOMACY -> 1; Tab.ECONOMY, Tab.MILITARY, Tab.GOVERNMENT -> 5; else -> 4 }
            for (k in 0 until subs) {
                rule.runOnIdle { c.selArmy = -1; c.selProvince = -1; c.tab = t; c.subTab[t] = k; c.refresh() }
                shot("1${i}${k}_${t.name.lowercase()}")
            }
        }
        rule.runOnIdle { c.tab = Tab.DIPLOMACY; c.focusNation = sim.game.nations.first { it.code == "ARG" }.id; c.refresh() }
        shot("16_diplomacia_argentina")
        rule.runOnIdle { c.tab = null; c.focusNation = -1; sim.game.events += PendingEvent("greve", sim.game.day); c.refresh() }
        shot("20_evento")
        rule.runOnIdle {
            sim.game.events.clear()
            val ury = sim.game.nations.first { it.code == "URY" }
            sim.diplomacy.declareWar(sim.player.id, ury.id)
            for (a in sim.armiesOf(sim.player.id).filter { it.domain == Domain.LAND }) sim.military.order(a, ury.capital)
            repeat(120) { sim.advanceDay() }
            c.mapMode = MapMode.WAR; c.refresh()
        }
        shot("21_guerra")
    }

    @Test fun newFeatures() {
        val sim = TestWorld.newSim("BRA", seed = 61)
        val c = controller(sim)
        val g = sim.game
        val me = sim.player
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("n").toFile())) } }
        rule.runOnIdle {
            repeat(40) { sim.advanceDay() }
            g.events.clear(); g.proposals.clear(); g.un.pending = null
            me.politicalCapital = 60f; me.treasury = 500.0
            val a = sim.armiesOf(me.id).first { it.domain == com.dmwnezes.soberano.sim.Domain.LAND }
            sim.military.appoint(a, g.generalPool.first())
            sim.military.fortify(me, me.capital)
            repeat(95) { sim.advanceDay() }
            g.events.clear(); g.un.pending = null
            c.selArmy = a.id; c.refresh()
        }
        shot("40_general")
        rule.runOnIdle { c.selArmy = -1; c.selectProvince(me.capital) }
        shot("41_forte")
        // desembarque: guerra com o Uruguai e alvo costeiro
        rule.runOnIdle {
            val ury = g.nations.first { it.code == "URY" }
            sim.diplomacy.declareWar(me.id, ury.id)
            val a = sim.armiesOf(me.id).first { it.domain == com.dmwnezes.soberano.sim.Domain.LAND }
            a.units[com.dmwnezes.soberano.sim.UnitType.INFANTRY.ordinal] = 4f
            for (t in listOf(com.dmwnezes.soberano.sim.UnitType.ARMOR, com.dmwnezes.soberano.sim.UnitType.ARTILLERY, com.dmwnezes.soberano.sim.UnitType.ANTIAIR)) a.units[t.ordinal] = 0f
            c.selProvince = -1; c.selArmy = a.id
            c.chooseTarget(sim.provincesOf(ury.id).first { sim.world.provinces[it].coastal && it != ury.capital })
            g.events.clear(); g.un.pending = null
        }
        shot("42_ordem_mar")
        // ONU
        rule.runOnIdle {
            c.clearTarget(); c.selArmy = -1
            sim.unitedNations.propose(com.dmwnezes.soberano.sim.ResolutionType.CONDEMN, g.nations.first { it.code == "PRK" }.id)
        }
        shot("43_onu")
        rule.runOnIdle { sim.unitedNations.resolve(1); c.tab = Tab.WORLD; c.subTab[Tab.WORLD] = 2; c.refresh() }
        shot("44_onu_aba")
        // campanha
        rule.runOnIdle {
            c.tab = null
            for (w in g.wars.toList()) sim.diplomacy.makePeace(w, me.id, com.dmwnezes.soberano.sim.PeaceTerms.WHITE)
            me.nextElection = g.day + 90
            repeat(31) { sim.advanceDay() }
            g.events.clear(); g.un.pending = null
            sim.politics.campaigns.promise(com.dmwnezes.soberano.sim.Promise.HEALTH)
            c.tab = Tab.GOVERNMENT; c.subTab[Tab.GOVERNMENT] = 0; c.refresh()
        }
        shot("45_campanha")
        // comércio
        rule.runOnIdle { c.tab = Tab.DIPLOMACY; c.focusNation = g.nations.first { it.code == "ARG" }.id; c.refresh() }
        shot("46_comercio")
        // jornal, busca e legenda
        rule.runOnIdle { c.tab = null; c.focusNation = -1; c.showPaper = true; c.refresh() }
        shot("47_jornal")
        rule.runOnIdle { c.showPaper = false; c.paperSeenDay = g.paper?.day ?: -1; c.mapMode = MapMode.RELATIONS; c.refresh() }
        shot("48_legenda")
        rule.runOnIdle { c.mapMode = MapMode.POLITICAL; c.showSearch = true; c.refresh() }
        shot("49_busca")
        rule.runOnIdle {
            c.showSearch = false
            g.election = com.dmwnezes.soberano.sim.ElectionResult(g.day, 54.3f, true, "Marta Fontes"); c.refresh()
        }
        shot("50_eleicao")
        org.junit.Assert.assertEquals(1, rule.onAllNodes(androidx.compose.ui.test.hasText("Reeleito!")).fetchSemanticsNodes().size)
    }

    @Test fun newFeatures3() {
        val sim = TestWorld.newSim("BRA", seed = 63)
        val c = controller(sim)
        val g = sim.game
        val me = sim.player
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("n3").toFile())) } }
        rule.runOnIdle {
            repeat(35) { sim.advanceDay() }
            g.events.clear(); g.proposals.clear(); g.un.pending = null
            for (a in sim.armiesOf(me.id)) if (a.moving) sim.military.stop(a)
            c.dialog = "spread"; c.refresh()
        }
        shot("60_dividir")
        rule.runOnIdle { c.dialog = null; g.troopFilter = 2; c.refresh() }
        shot("61_tropas_ocultas")
        rule.runOnIdle {
            g.troopFilter = 0
            val usa = g.nations.first { it.code == "USA" }
            g.treaties += com.dmwnezes.soberano.sim.Treaty(com.dmwnezes.soberano.sim.TreatyType.SANCTION, usa.id, me.id, g.day, g.day + 700)
            sim.unitedNations.apply(com.dmwnezes.soberano.sim.Resolution(com.dmwnezes.soberano.sim.ResolutionType.ARMS_EMBARGO, g.nations.first { it.code == "RUS" }.id, -1, usa.id, g.day), emptyList())
            c.tab = Tab.WORLD; c.subTab[Tab.WORLD] = 2; c.refresh()
        }
        shot("62_onu_em_vigor")
        rule.runOnIdle { c.tab = Tab.ECONOMY; c.subTab[Tab.ECONOMY] = 0; c.refresh() }
        shot("63_economia_sancao")
        rule.runOnIdle { c.tab = null; c.lawEditor = true; c.refresh() }
        shot("64_lei_ia")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("Semana de trabalho de 4 dias e transporte público gratuito")
        rule.onNodeWithText("Analisar a lei").performClick()
        var tries = 0
        while (rule.onAllNodes(androidx.compose.ui.test.hasText("O que muda")).fetchSemanticsNodes().isEmpty() && tries++ < 150) {
            rule.mainClock.advanceTimeBy(100); Thread.sleep(40)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        shot("65_lei_ia_resultado")
        rule.onNodeWithText("Salvar rascunho").performClick()
        rule.runOnIdle { org.junit.Assert.assertTrue(g.customLaws.any { it.free != null }) }
    }

    @Test fun newFeatures4() {
        val sim = TestWorld.newSim("BRA", seed = 64)
        val c = controller(sim)
        val g = sim.game
        val me = sim.player
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("n4").toFile())) } }
        rule.runOnIdle {
            repeat(20) { sim.advanceDay() }
            g.events.clear(); g.proposals.clear(); g.un.pending = null
            val p = sim.provincesOf(me.id).first { sim.world.provinces[it].name == "Rio de Janeiro" }
            g.insurgents += com.dmwnezes.soberano.sim.Insurgency(g.nextInsurgentId++, p, me.id, com.dmwnezes.soberano.sim.InsurgentKind.MILITIA, "Milícia de Rio de Janeiro", 3.2f, g.day)
            me.treasury = 300.0
            sim.buildings.build(me, p, com.dmwnezes.soberano.sim.BuildingType.PORT)
            c.selectProvince(p)
        }
        shot("70_insurgencia_obras")
        rule.runOnIdle { c.dialog = "build"; c.refresh() }
        shot("71_construir")
        rule.runOnIdle { c.dialog = null; c.selProvince = -1; c.mapMode = MapMode.TRADE; c.refresh() }
        shot("72_mapa_comercio")
        rule.runOnIdle {
            c.mapMode = MapMode.POLITICAL
            val ago = g.nations.first { it.code == "AGO" }
            sim.diplomacy.declareWar(me.id, ago.id)
            val a = sim.armiesOf(me.id).first { it.domain == com.dmwnezes.soberano.sim.Domain.LAND }
            c.selArmy = a.id
            c.chooseTarget(ago.capital)
        }
        shot("73_ataque_alem_mar")
        rule.runOnIdle { c.clearTarget(); c.selArmy = -1; c.dialog = "speech"; c.refresh() }
        shot("74_pronunciamento")
        rule.onNodeWithText("Ir ao ar").performClick()
        shot("75_pronunciamento_resultado")
    }

    @Test fun civilWarOnMap() {
        val sim = TestWorld.newSim("BRA", seed = 62)
        val c = controller(sim)
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("cw").toFile())) } }
        rule.runOnIdle {
            sim.civil.secession(sim.player)
            repeat(20) { sim.advanceDay() }
            sim.game.events.clear(); sim.game.un.pending = null
            c.mapMode = MapMode.WAR; c.refresh()
        }
        shot("51_guerra_civil")
    }
}
