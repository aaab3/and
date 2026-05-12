package com.runtime.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = White,
    primaryContainer = Blue80,
    surface = BlueGrey95,
    surfaceVariant = BlueGrey90,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = BlueGrey10,
    primaryContainer = Blue40,
    surface = BlueGrey10,
    surfaceVariant = BlueGrey20,
    error = ErrorRed
)

@Composable
fun RuntimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
