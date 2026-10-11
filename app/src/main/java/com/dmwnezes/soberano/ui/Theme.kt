package com.dmwnezes.soberano.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmwnezes.soberano.R

/**
 * Paleta "carta náutica noturna": oceano azul-petróleo com quadrícula,
 * terras em tons de papel antigo, latão para o jogador e verdete para o que é amigo.
 */
object Ink {
    val ocean = Color(0xFF0E2A33)
    val oceanDeep = Color(0xFF0A1F27)
    val grid = Color(0xFF1C4350)
    val panel = Color(0xF2122630)
    val panelSolid = Color(0xFF122630)
    val raised = Color(0xFF1A3440)
    val line = Color(0xFF2A4C59)
    val text = Color(0xFFEAF1EE)
    val muted = Color(0xFF8FA9AE)
    val faint = Color(0xFF5E7A80)
    val brass = Color(0xFFE8B04B)
    val brassDeep = Color(0xFFB9832A)
    val verdigris = Color(0xFF5FC4B0)
    val coral = Color(0xFFE5675B)
    val amber = Color(0xFFF0A23A)
    val good = Color(0xFF7CC68A)
    val onBrass = Color(0xFF2A1C05)

    fun tone(t: Int) = when (t) { 1 -> good; 2 -> amber; 3 -> coral; else -> muted }

    /** 0..100 → vermelho, âmbar, verde */
    fun scale(v: Float): Color = when {
        v < 30f -> coral
        v < 55f -> amber
        else -> good
    }
}

object Shapes {
    val card = RoundedCornerShape(26.dp)
    val sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val chip = RoundedCornerShape(50)
    val tile = RoundedCornerShape(18.dp)
    val small = RoundedCornerShape(12.dp)
}

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
)

object Type {
    val title = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp)
    val heading = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
    val body = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val label = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val small = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val number = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, fontFeatureSettings = "tnum")
    val big = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, fontFeatureSettings = "tnum")
}

@Composable
fun SoberanoTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.o() = copy(fontFamily = Outfit)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.brass, onPrimary = Ink.onBrass, secondary = Ink.verdigris,
            background = Ink.ocean, surface = Ink.panelSolid, onSurface = Ink.text, onBackground = Ink.text,
            error = Ink.coral, surfaceVariant = Ink.raised, outline = Ink.line,
        ),
        typography = Typography(
            bodyLarge = base.bodyLarge.o(), bodyMedium = base.bodyMedium.o(), bodySmall = base.bodySmall.o(),
            titleLarge = base.titleLarge.o(), titleMedium = base.titleMedium.o(), titleSmall = base.titleSmall.o(),
            labelLarge = base.labelLarge.o(), labelMedium = base.labelMedium.o(), labelSmall = base.labelSmall.o(),
            headlineSmall = base.headlineSmall.o(), headlineMedium = base.headlineMedium.o(),
        ),
        content = content,
    )
}
