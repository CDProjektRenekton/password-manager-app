package com.securevault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CE0A5),
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFF9FD0BC),
    background = Color(0xFF0E1513),
    surface = Color(0xFF0E1513),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color.White,
    secondary = Color(0xFF4C6359),
)

/** Strength-meter colours, index = zxcvbn score 0..4. */
val StrengthColors = listOf(
    Color(0xFFD32F2F),
    Color(0xFFF57C00),
    Color(0xFFFBC02D),
    Color(0xFF7CB342),
    Color(0xFF2E7D32),
)

@Composable
fun SecureVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
