package com.dmwnezes.soberano

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.dmwnezes.soberano.data.SaveStore
import com.dmwnezes.soberano.sim.Game
import com.dmwnezes.soberano.sim.Settings
import com.dmwnezes.soberano.sim.Setup
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.sim.Title
import com.dmwnezes.soberano.ui.GameController
import com.dmwnezes.soberano.ui.GameScreen
import com.dmwnezes.soberano.ui.HomeScreen
import com.dmwnezes.soberano.ui.Ink
import com.dmwnezes.soberano.ui.NewGameFlow
import com.dmwnezes.soberano.ui.SoberanoTheme
import com.dmwnezes.soberano.ui.Type
import com.dmwnezes.soberano.world.WorldMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val store = SaveStore(File(filesDir, "saves"))
        setContent {
            SoberanoTheme { App(store) }
        }
    }
}

private sealed interface Screen {
    data object Loading : Screen
    data object Home : Screen
    data object NewGame : Screen
    data object Playing : Screen
}

/** Mapa carregado uma vez por processo. */
private object WorldHolder { var world: WorldMap? = null; var preview: Sim? = null }

@Composable
private fun App(store: SaveStore) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Loading) }
    var controller by remember { mutableStateOf<GameController?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (WorldHolder.world == null) {
            withContext(Dispatchers.Default) {
                val w = ctx.assets.open("world.bin").use { WorldMap.load(it) }
                WorldHolder.world = w
                WorldHolder.preview = Sim(w, Setup.newGame(w, Settings(seed = 2026), "BRA", Title.PRESIDENT))
            }
        }
        screen = if (controller != null) Screen.Playing else Screen.Home
    }

    // salva ao sair do app
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, controller) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) controller?.let { if (it.game.gameOver == null) it.autosave(); it.paused = true }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun start(game: Game) {
        val sim = Sim(WorldHolder.world!!, game)
        controller = GameController(sim, store, scope)
        screen = Screen.Playing
    }

    Box(Modifier.fillMaxSize().background(Ink.ocean)) {
        when (screen) {
            Screen.Loading -> Text("Desenhando o mundo…", style = Type.heading, color = Ink.muted, modifier = Modifier.align(Alignment.Center))
            Screen.Home -> HomeScreen(
                preview = WorldHolder.preview!!, store = store,
                onNew = { screen = Screen.NewGame },
                onLoad = { slot -> store.load(slot)?.let { start(it) } },
            )
            Screen.NewGame -> {
                BackHandler { screen = Screen.Home }
                NewGameFlow(
                    preview = WorldHolder.preview!!,
                    onBack = { screen = Screen.Home },
                    onStart = { settings, code, title, custom ->
                        val g = Setup.newGame(WorldHolder.world!!, settings, code, title, custom)
                        start(g)
                        controller?.autosave()
                    },
                )
            }
            Screen.Playing -> controller?.let { c ->
                GameScreen(
                    c,
                    onExit = { c.autosave(); controller = null; screen = Screen.Home },
                    onLoad = { slot -> store.load(slot)?.let { start(it) } },
                    onSwitchCountry = { id ->
                        val g = c.game
                        g.player = id; g.gameOver = null
                        g.nations[id].let { n -> if (!n.regime.elections) n.nextElection = -1 }
                        start(g)
                    },
                    store = store,
                )
            }
        }
    }
}
