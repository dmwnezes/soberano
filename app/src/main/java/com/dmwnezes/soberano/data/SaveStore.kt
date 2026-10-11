package com.dmwnezes.soberano.data

import com.dmwnezes.soberano.sim.Game
import com.dmwnezes.soberano.sim.START_DATE
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SaveMeta(val slot: String, val country: String, val day: Int, val savedAt: Long, val title: String) {
    fun dateLabel(): String {
        val d = START_DATE.plusDays(day.toLong())
        val m = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")[d.monthValue - 1]
        return "${d.dayOfMonth} $m ${d.year}"
    }
}

/** Saves em JSON: "auto" (automático) e "1".."5" (manuais). */
class SaveStore(private val dir: File) {
    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
            encodeDefaults = true
        }
        val SLOTS = listOf("1", "2", "3", "4", "5")
    }

    init { dir.mkdirs() }

    private fun file(slot: String) = File(dir, "save_$slot.json")
    private fun metaFile(slot: String) = File(dir, "save_$slot.meta")

    fun dirFile(): File = dir

    fun save(slot: String, game: Game) {
        val tmp = File(dir, "save_$slot.tmp")
        tmp.writeText(json.encodeToString(game))
        tmp.renameTo(file(slot)).let { if (!it) { file(slot).delete(); tmp.renameTo(file(slot)) } }
        val me = game.nations[game.player]
        writeMeta(slot, me.name, game.day, game.leaderTitle.label)
    }

    fun writeMeta(slot: String, country: String, day: Int, title: String) {
        val meta = SaveMeta(slot, country, day, System.currentTimeMillis(), title)
        metaFile(slot).writeText(json.encodeToString(meta))
    }

    fun load(slot: String): Game? = runCatching {
        json.decodeFromString<Game>(file(slot).readText())
    }.getOrNull()

    fun meta(slot: String): SaveMeta? = runCatching {
        if (!file(slot).exists()) null else json.decodeFromString<SaveMeta>(metaFile(slot).readText())
    }.getOrNull()

    fun delete(slot: String) { file(slot).delete(); metaFile(slot).delete() }

    fun latest(): SaveMeta? = (listOf("auto") + SLOTS).mapNotNull { meta(it) }.maxByOrNull { it.savedAt }
}
