package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BilluminatiLightColorScheme = lightColorScheme(
    primary = Graphite900,
    onPrimary = PureWhite,
    primaryContainer = Graphite100,
    onPrimaryContainer = Graphite900,
    secondary = Graphite600,
    onSecondary = PureWhite,
    secondaryContainer = Graphite100,
    onSecondaryContainer = Graphite800,
    tertiary = PaidLight,
    onTertiary = PureWhite,
    tertiaryContainer = PaidContainerLight,
    onTertiaryContainer = PaidLight,
    background = Graphite50,
    onBackground = Graphite900,
    surface = PureWhite,
    onSurface = Graphite900,
    surfaceVariant = Graphite100,
    onSurfaceVariant = Graphite600,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = Graphite50,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = Graphite100,
    surfaceContainerHighest = Graphite200,
    outline = Graphite200,
    outlineVariant = Graphite300,
    error = OverdueLight,
    onError = PureWhite,
    errorContainer = OverdueContainerLight,
    onErrorContainer = OverdueLight
)

private val BilluminatiDarkColorScheme = darkColorScheme(
    primary = Graphite100,
    onPrimary = Graphite950,
    primaryContainer = Graphite800,
    onPrimaryContainer = Graphite100,
    secondary = Graphite400,
    onSecondary = Graphite950,
    secondaryContainer = Graphite850,
    onSecondaryContainer = Graphite200,
    tertiary = PaidDark,
    onTertiary = Graphite950,
    tertiaryContainer = PaidContainerDark,
    onTertiaryContainer = PaidDark,
    background = Graphite950,
    onBackground = Graphite100,
    surface = Graphite900,
    onSurface = Graphite100,
    surfaceVariant = Graphite850,
    onSurfaceVariant = Graphite400,
    surfaceContainerLowest = Graphite950,
    surfaceContainerLow = Graphite900,
    surfaceContainer = Graphite900,
    surfaceContainerHigh = Graphite850,
    surfaceContainerHighest = Graphite800,
    outline = Color(0xFF2A2A2E),
    outlineVariant = Color(0xFF36363D),
    error = OverdueDark,
    onError = Graphite950,
    errorContainer = OverdueContainerDark,
    onErrorContainer = OverdueDark
)

@Composable
fun BilluminatiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BilluminatiDarkColorScheme else BilluminatiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BilluminatiTypography,
        content = content
    )
}

/**
 * Draws Billuminati's subtle 24dp square-ruled ledger grid behind workspace backgrounds.
 */
fun Modifier.squareRuledBackground(isDark: Boolean): Modifier = this.drawBehind {
    val gridColor = if (isDark) {
        Graphite100.copy(alpha = 0.035f)
    } else {
        Graphite900.copy(alpha = 0.038f)
    }
    val step = 24.dp.toPx()
    val stroke = 1f
    var x = 0f
    while (x < size.width) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = stroke)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        y += step
    }
}
