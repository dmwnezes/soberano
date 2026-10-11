package com.dmwnezes.soberano

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Domain
import com.dmwnezes.soberano.ui.GameController
import com.dmwnezes.soberano.ui.GameScreen
import com.dmwnezes.soberano.ui.SoberanoTheme
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.file.Files

/** Toques de verdade no mapa: selecionar exército, tocar no alvo, atacar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi", sdk = [34])
class AttackTapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun step() { rule.mainClock.advanceTimeBy(500); rule.waitForIdle() }

    @Test fun tapProvinceWithArmySelectedThenAttack() {
        val sim = TestWorld.newSim("BRA", seed = 30)
        val c = GameController(sim, SaveStore(Files.createTempDirectory("t").toFile()), MainScope())
        rule.mainClock.autoAdvance = false
        rule.setContent { SoberanoTheme { GameScreen(c, {}, {}, {}, SaveStore(Files.createTempDirectory("g").toFile())) } }
        step()
        val army = sim.armiesOf(sim.player.id).first { it.domain == Domain.LAND }
        rule.runOnIdle { c.selArmy = army.id; c.refresh() }
        step()
        // toca no meio da capital do Paraguai, pela tela
        val pry = sim.game.nations.first { it.code == "PRY" }
        val target = sim.world.provinces[pry.capital]
        val cam = c.camera
        val pos = Offset(target.cx * cam.scale + cam.ox, target.cy * cam.scale + cam.oy)
        rule.onRoot().performTouchInput { click(pos) }
        step(); step()
        assertEquals("o toque deve escolher o alvo", pry.capital, c.orderTarget)
        rule.onNodeWithText("Declarar guerra e atacar").performClick(); step()
        rule.onNodeWithText("Declarar e atacar").performClick(); step()
        assertTrue(sim.atWar(sim.player.id, pry.id))
        assertTrue("exército marchando", army.path.isNotEmpty() && army.path.last() == pry.capital)

        // agora em guerra: novo alvo inimigo mostra "Atacar"
        val other = sim.provincesOf(pry.id).first { it != pry.capital }
        rule.runOnIdle { sim.military.stop(army); c.selArmy = army.id; c.refresh() }
        step()
        val p2 = sim.world.provinces[other]
        rule.onRoot().performTouchInput { click(Offset(p2.cx * cam.scale + cam.ox, p2.cy * cam.scale + cam.oy)) }
        step(); step()
        assertEquals(other, c.orderTarget)
        rule.onNodeWithText("Atacar").performClick(); step()
        assertTrue(army.path.isNotEmpty() && army.path.last() == other)
    }
}
