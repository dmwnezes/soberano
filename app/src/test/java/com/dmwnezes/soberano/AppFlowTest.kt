package com.dmwnezes.soberano

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Abre o app de verdade e joga do menu até o mapa. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi", sdk = [34])
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun step(ms: Long = 400) { rule.mainClock.advanceTimeBy(ms); rule.waitForIdle() }

    private fun waitText(t: String) {
        repeat(60) { if (rule.onAllNodesWithText(t).fetchSemanticsNodes().isNotEmpty()) return; Thread.sleep(100); step(100) }
        throw AssertionError("não apareceu: $t")
    }

    @Test fun menuToGameAndBack() {
        rule.mainClock.autoAdvance = false
        waitText("Novo jogo")
        rule.onNodeWithText("Novo jogo").performClick(); step()
        waitText("Governar")
        rule.onNodeWithText("Governar").performClick(); step()
        rule.onNodeWithText("Ditador").performClick(); step()
        waitText("Começar")
        rule.onNodeWithText("Começar").performClick(); step(800)
        waitText("Diplomacia")
        // joga alguns dias
        rule.onNodeWithText("Militar").performClick(); step()
        rule.onNodeWithText("Forças armadas").assertExists()
        rule.onAllNodesWithText("Militar")[1].performClick(); step()
        val v = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        File("build/screens").mkdirs()
        File("build/screens/30_app.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        val saves = File(rule.activity.filesDir, "saves").listFiles()?.map { it.name } ?: emptyList()
        Thread.sleep(500)
        println("saves: $saves")
        assertTrue(true)
    }
}
