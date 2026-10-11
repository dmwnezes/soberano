package com.dmwnezes.soberano

import com.dmwnezes.soberano.sim.Game
import com.dmwnezes.soberano.sim.Settings
import com.dmwnezes.soberano.sim.Setup
import com.dmwnezes.soberano.sim.Sim
import com.dmwnezes.soberano.sim.Title
import com.dmwnezes.soberano.world.WorldMap
import java.io.File

object TestWorld {
    val world: WorldMap by lazy {
        val f = listOf(File("src/main/assets/world.bin"), File("app/src/main/assets/world.bin")).first { it.exists() }
        f.inputStream().use { WorldMap.load(it) }
    }

    fun newSim(code: String = "BRA", seed: Long = 42L, title: Title = Title.PRESIDENT, settings: Settings = Settings(seed = seed)): Sim {
        val g: Game = Setup.newGame(world, settings, code, title)
        return Sim(world, g)
    }
}
