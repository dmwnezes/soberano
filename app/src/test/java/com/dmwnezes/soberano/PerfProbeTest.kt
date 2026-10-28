package com.dmwnezes.soberano

import com.dmwnezes.soberano.sim.Defeat
import com.dmwnezes.soberano.sim.Settings
import com.dmwnezes.soberano.sim.Title
import org.junit.Test

class PerfProbeTest {
    @Test fun probe() {
        val sim = TestWorld.newSim("BRA", seed = 90, settings = Settings(seed = 90, defeats = mutableSetOf()))
        val g = sim.game
        for (year in 0 until 5) {
            var total = 0.0; var max = 0.0
            repeat(365) {
                val t = System.nanoTime()
                sim.advanceDay()
                val ms = (System.nanoTime() - t) / 1e6
                total += ms; if (ms > max) max = ms
                g.events.clear(); g.un.pending = null; g.election?.seen = true; g.proposals.clear()
            }
            println("PERF y$year avg=${"%.2f".format(total / 365)} max=${"%.1f".format(max)} armies=${g.armies.size} wars=${g.wars.size} battles=${g.battles.size} treaties=${g.treaties.size} deals=${g.deals.size} feed=${g.feed.size} ins=${g.insurgents.size} bld=${g.buildings.size} nations=${g.nations.size} alive=${g.nations.count { it.alive }} queue=${g.nations.sumOf { it.queue.size }} gameOver=${g.gameOver} USA=${sim.dev.landDivisions(g.nations.first { it.code == "USA" }.id).toInt()}/${sim.dev.manpowerCap(g.nations.first { it.code == "USA" }.id).toInt()} CHN=${sim.dev.landDivisions(g.nations.first { it.code == "CHN" }.id).toInt()}/${sim.dev.manpowerCap(g.nations.first { it.code == "CHN" }.id).toInt()} BRA=${sim.dev.landDivisions(g.player).toInt()}/${sim.dev.manpowerCap(g.player).toInt()}")
        }
        println("PERF treaty types " + g.treaties.groupingBy { it.type }.eachCount())
        println("PERF distinct " + g.treaties.map { Triple(it.type, minOf(it.a, it.b), maxOf(it.a, it.b)) }.toSet().size)
        println("PERF bld by nation " + g.buildings.groupingBy { sim.nation(g.owner[it.province]).code }.eachCount().entries.sortedByDescending { it.value }.take(5))
        val counts = g.armies.groupingBy { sim.nation(it.owner).code }.eachCount().entries.sortedByDescending { it.value }.take(8)
        println("PERF top armies $counts")
    }
}
