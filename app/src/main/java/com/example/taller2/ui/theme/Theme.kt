package com.example.taller2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary        = Green40,
    onPrimary      = Neutral90,
    secondary      = Orange40,
    onSecondary    = Neutral90,
    background     = Neutral90,
    surface        = Neutral90,
    onBackground   = Neutral10,
    onSurface      = Neutral10,
    error          = ErrorRed,
    errorContainer = ErrorBg,
    onError        = Neutral90,
)

private val DarkColorScheme = darkColorScheme(
    primary        = Green80,
    onPrimary      = Neutral10,
    secondary      = Orange80,
    onSecondary    = Neutral10,
    background     = Neutral10,
    surface        = Neutral10,
    onBackground   = Neutral90,
    onSurface      = Neutral90,
    error          = ErrorBg,
    errorContainer = ErrorRed,
    onError        = Neutral10,
)

@Composable
fun Taller2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // dynamicColor desactivado: queremos usar nuestra paleta siempre
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Colorear la barra de estado con el color primario del tema
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}