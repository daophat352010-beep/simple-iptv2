package com.example.iptv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF101010)
val Panel = Color(0xFF1C1C1E)
val PanelHi = Color(0xFF2A2A2D)

@Composable
fun IptvTheme(accent: String, content: @Composable () -> Unit) {
    val primary = if (accent == "red") Color(0xFFE53935) else Color(0xFF3D8BFF)
    val scheme = darkColorScheme(
        primary = primary, onPrimary = Color.White,
        background = Bg, onBackground = Color.White,
        surface = Panel, onSurface = Color.White,
        surfaceVariant = PanelHi, onSurfaceVariant = Color(0xFFBDBDBD)
    )
    // Chữ lớn, dễ đọc từ xa
    val typo = Typography(
        headlineMedium = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(fontSize = 24.sp),
        bodyMedium = TextStyle(fontSize = 20.sp),
        bodySmall = TextStyle(fontSize = 17.sp),
        labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium)
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typo,
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp))
    ) {
        ProvideTextStyle(typo.bodyLarge) { content() }
    }
}
