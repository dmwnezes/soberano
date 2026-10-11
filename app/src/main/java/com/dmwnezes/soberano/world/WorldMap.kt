package com.dmwnezes.soberano.world

import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** País como está no mapa no começo de 2026 (dados de partida). */
class CountryBase(
    val index: Int,
    val code: String,
    val name: String,
    val continent: String,
    val subregion: String,
    val population: Long,
    /** PIB anual em US$ bilhões. */
    val gdp: Double,
    /** 1 (renda baixa) a 5 (renda alta OCDE). */
    val income: Int,
    val mapColor: Int,
    val capital: Int,
)

class Province(
    val id: Int,
    val name: String,
    /** País dono no início do jogo. */
    val country: Int,
    val population: Int,
    val areaKm2: Float,
    /** Ponto de referência dentro da província (coordenadas do mapa). */
    val cx: Float,
    val cy: Float,
    val mapArea: Float,
    val coastal: Boolean,
    val neighbors: IntArray,
    /** Anéis do contorno: x0,y0,x1,y1… em coordenadas do mapa. */
    val rings: Array<FloatArray>,
) {
    val minX: Float; val minY: Float; val maxX: Float; val maxY: Float
    val lat: Double
    val lon: Double

    init {
        var a = Float.MAX_VALUE; var b = Float.MAX_VALUE; var c = -Float.MAX_VALUE; var d = -Float.MAX_VALUE
        for (r in rings) {
            var i = 0
            while (i < r.size) {
                val x = r[i]; val y = r[i + 1]
                if (x < a) a = x; if (x > c) c = x
                if (y < b) b = y; if (y > d) d = y
                i += 2
            }
        }
        minX = a; minY = b; maxX = c; maxY = d
        lon = Projection.lon(cx)
        lat = Projection.lat(cy)
    }

    fun contains(x: Float, y: Float): Boolean {
        if (x < minX || x > maxX || y < minY || y > maxY) return false
        for (r in rings) if (pointInRing(r, x, y)) return true
        return false
    }

    private fun pointInRing(r: FloatArray, x: Float, y: Float): Boolean {
        var inside = false
        val n = r.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = r[2 * i]; val yi = r[2 * i + 1]
            val xj = r[2 * j]; val yj = r[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/** Projeção de Miller usada para gerar o mapa (ver tools/build_world.py). */
object Projection {
    const val W = 4000.0
    private const val LAT_MAX = 84.0
    private val S = W / (2 * PI)
    private val YMAX = millerY(LAT_MAX)

    private fun millerY(latDeg: Double): Double {
        val r = Math.toRadians(latDeg)
        return 1.25 * ln(tan(PI / 4 + 0.4 * r))
    }

    fun lon(x: Float): Double = x / W * 360.0 - 180.0
    fun x(lon: Double): Float = ((lon + 180.0) / 360.0 * W).toFloat()
    fun y(lat: Double): Float = ((YMAX - millerY(lat.coerceIn(-58.0, LAT_MAX))) * S).toFloat()
    fun lat(y: Float): Double {
        val ym = YMAX - y / S
        return Math.toDegrees(2.5 * atan(exp(0.8 * ym)) - 5 * PI / 8)
    }
}

class WorldMap(
    val width: Float,
    val height: Float,
    val countries: List<CountryBase>,
    val provinces: List<Province>,
) {
    val byCode: Map<String, CountryBase> = countries.associateBy { it.code }
    val coastalIds: IntArray = provinces.filter { it.coastal }.map { it.id }.toIntArray()

    /** Província sob o ponto do mapa, ou -1 (mar). */
    fun hit(x: Float, y: Float): Int {
        // províncias pequenas dentro de grandes (enclaves): vence a menor
        var best = -1; var bestArea = Float.MAX_VALUE
        for (p in provinces) if (p.contains(x, y) && p.mapArea < bestArea) { best = p.id; bestArea = p.mapArea }
        return best
    }

    /** Província mais próxima de um ponto (para toques no mar perto da costa). */
    fun nearest(x: Float, y: Float, maxDist: Float): Int {
        var best = -1; var bd = maxDist * maxDist
        for (p in provinces) {
            val dx = p.cx - x; val dy = p.cy - y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = p.id }
        }
        return best
    }

    fun distanceKm(a: Int, b: Int): Double {
        val pa = provinces[a]; val pb = provinces[b]
        return haversine(pa.lat, pa.lon, pb.lat, pb.lon)
    }

    companion object {
        fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }

        fun load(input: InputStream): WorldMap {
            val d = DataInputStream(input.buffered(1 shl 16))
            val magic = ByteArray(4).also { d.readFully(it) }
            require(String(magic) == "SOBW") { "mapa inválido" }
            d.readInt()
            val w = d.readFloat(); val h = d.readFloat()
            val nc = d.readInt()
            val countries = ArrayList<CountryBase>(nc)
            for (i in 0 until nc) {
                val code = d.readUTF(); val name = d.readUTF(); val cont = d.readUTF(); val sub = d.readUTF()
                val pop = d.readLong(); val gdp = d.readDouble(); val inc = d.readByte().toInt()
                val color = d.readByte().toInt(); val cap = d.readInt()
                countries += CountryBase(i, code, name, cont, sub, pop, gdp, inc, color, cap)
            }
            val np = d.readInt()
            val provinces = ArrayList<Province>(np)
            for (i in 0 until np) {
                val name = d.readUTF()
                val c = d.readShort().toInt(); val pop = d.readInt(); val area = d.readFloat()
                val cx = d.readFloat(); val cy = d.readFloat(); val marea = d.readFloat()
                val coastal = d.readByte().toInt() != 0
                val nn = d.readShort().toInt()
                val nb = IntArray(nn) { d.readShort().toInt() }
                val nr = d.readShort().toInt()
                val rings = Array(nr) {
                    val k = d.readShort().toInt()
                    FloatArray(k * 2) { d.readShort() / 8f }
                }
                provinces += Province(i, name, c, pop, area, cx, cy, marea, coastal, nb, rings)
            }
            return WorldMap(w, h, countries, provinces)
        }
    }
}
