package com.dmwnezes.soberano

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
        for ((i, t) in Tab.entries.withIndex()) {
            rule.runOnIdle { c.selArmy = -1; c.selProvince = -1; c.tab = t; c.refresh() }
            shot("1${i}_${t.name.lowercase()}")
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
}
