package com.example.resilentnet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ResilientLightColorScheme = lightColorScheme(
    primary = ResilientPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = ResilientSoftGreen,
    onPrimaryContainer = ResilientTextMain,
    secondary = ResilientInfoBlue,
    onSecondary = Color.White,
    error = ResilientErrorRed,
    onError = Color.White,
    background = ResilientMainBg,
    onBackground = ResilientTextMain,
    surface = ResilientCardBg,
    onSurface = ResilientTextMain,
    surfaceVariant = ResilientInputBg,
    onSurfaceVariant = ResilientTextSecondary,
    outline = ResilientCardBorder
)

@Composable
fun ResilentNetTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ResilientLightColorScheme,
        typography = Typography,
        content = content
    )
}