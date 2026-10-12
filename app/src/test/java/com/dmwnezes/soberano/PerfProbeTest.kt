package com.dmwnezes.soberano

import com.dmwnezes.soberano.data.SaveStore
import org.junit.Test

class PerfProbeTest {
    @Test fun probe() {
        val sim = TestWorld.newSim("BRA", seed = 90)
        repeat(60) { sim.advanceDay() }
        val times = ArrayList<Pair<Int, Double>>()
        for (i in 0 until 720) {
            val t = System.nanoTime()
            sim.advanceDay()
            times += sim.game.day to (System.nanoTime() - t) / 1e6
            sim.game.events.clear(); sim.game.un.pending = null; sim.game.election?.seen = true
        }
        val sorted = times.map { it.second }.sorted()
        println("PERF median=${sorted[sorted.size / 2]} p90=${sorted[(sorted.size * 0.9).toInt()]} max=${sorted.last()}")
        println("PERF top=" + times.sortedByDescending { it.second }.take(12).joinToString { "d${it.first}:${"%.1f".format(it.second)}" })
        val t = System.nanoTime()
        val s = SaveStore.json.encodeToString(com.dmwnezes.soberano.sim.Game.serializer(), sim.game)
        println("PERF save=${(System.nanoTime() - t) / 1e6}ms size=${s.length / 1024}KB wars=${sim.game.wars.size} armies=${sim.game.armies.size}")
    }
}
