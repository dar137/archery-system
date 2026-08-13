package com.example.archerytimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.archerytimer.model.ArcheryConfig
import com.example.archerytimer.ui.ControlSetupScreen
import com.example.archerytimer.ui.CountdownScreen
import com.example.archerytimer.ui.DisplayScreen
import com.example.archerytimer.ui.RoleSelectionScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    var screen by remember { mutableStateOf<AppScreen>(AppScreen.RoleSelection) }

                    when (val current = screen) {
                        AppScreen.RoleSelection -> RoleSelectionScreen(
                            onControlSelected = { screen = AppScreen.ControlSetup },
                            onDisplaySelected = { screen = AppScreen.Display },
                        )

                        AppScreen.ControlSetup -> ControlSetupScreen(
                            onConfirmed = { screen = AppScreen.Countdown(it) },
                        )

                        is AppScreen.Countdown -> CountdownScreen(config = current.config)
                        AppScreen.Display -> DisplayScreen()
                    }
                }
            }
        }
    }
}

private sealed interface AppScreen {
    data object RoleSelection : AppScreen
    data object ControlSetup : AppScreen
    data class Countdown(val config: ArcheryConfig) : AppScreen
    data object Display : AppScreen
}
