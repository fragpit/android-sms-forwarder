package dev.local.smsforwarder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B56),
    secondary = Color(0xFF4B635B),
    tertiary = Color(0xFF3F6375),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF69DDBF),
    secondary = Color(0xFFB3CCC2),
    tertiary = Color(0xFFA8CDE1),
)

/** Material 3 theme with system dark-mode support. */
@Composable
fun SmsForwarderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
