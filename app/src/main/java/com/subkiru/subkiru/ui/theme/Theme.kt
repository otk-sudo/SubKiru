package com.subkiru.subkiru.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SubKiruLightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Accent,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = CardBackground,
    error = Warning,
    onError = OnError,
    errorContainer = ErrorContainer,
    outline = Outline,
)

@Composable
fun SubKiruTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SubKiruLightColorScheme,
        typography = SubKiruTypography,
        content = content,
    )
}
