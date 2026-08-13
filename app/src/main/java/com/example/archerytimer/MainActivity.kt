package com.example.archerytimer

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.archerytimer.model.ArcheryConfig
import com.example.archerytimer.ui.ControlSetupScreen
import com.example.archerytimer.ui.CountdownScreen
import com.example.archerytimer.ui.DisplayScreen
import com.example.archerytimer.ui.RoleSelectionScreen
import com.example.archerytimer.ui.ControlMusicState
import com.example.archerytimer.ui.BluetoothMatchingScreen
import com.example.archerytimer.communication.BluetoothTransport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 31) {
                    bluetoothPermissionLauncher.launch(arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                    ))
                } else {
                    bluetoothPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }
            val bluetoothTransport = remember { BluetoothTransport(applicationContext) }
            val controlMusicState = remember(bluetoothTransport) { ControlMusicState(bluetoothTransport) }
            DisposableEffect(bluetoothTransport) {
                onDispose { bluetoothTransport.release() }
            }
            MaterialTheme {
                Surface {
                    var screen by remember { mutableStateOf<AppScreen>(AppScreen.RoleSelection) }

                    when (val current = screen) {
                        AppScreen.RoleSelection -> RoleSelectionScreen(
                            onControlSelected = { screen = AppScreen.BluetoothMatching },
                            onDisplaySelected = { screen = AppScreen.Display },
                        )

                        AppScreen.BluetoothMatching -> BluetoothMatchingScreen(
                            bluetoothTransport = bluetoothTransport,
                            onConnected = { screen = AppScreen.ControlSetup },
                            onBack = { screen = AppScreen.RoleSelection },
                        )

                        AppScreen.ControlSetup -> ControlSetupScreen(
                            onConfirmed = { screen = AppScreen.Countdown(it) },
                            onBack = {
                                controlMusicState.disconnect()
                                screen = AppScreen.RoleSelection
                            },
                            controlMusic = controlMusicState,
                            bluetoothTransport = bluetoothTransport,
                        )

                        is AppScreen.Countdown -> CountdownScreen(
                            config = current.config,
                            controlMusic = controlMusicState,
                            bluetoothTransport = bluetoothTransport,
                            onExitConfirmed = { screen = AppScreen.ControlSetup },
                        )
                        AppScreen.Display -> DisplayScreen(
                            bluetoothTransport = bluetoothTransport,
                            onBack = { screen = AppScreen.RoleSelection },
                        )
                    }
                }
            }
        }
    }

}

private sealed interface AppScreen {
    data object RoleSelection : AppScreen
    data object BluetoothMatching : AppScreen
    data object ControlSetup : AppScreen
    data class Countdown(val config: ArcheryConfig) : AppScreen
    data object Display : AppScreen
}
