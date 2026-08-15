package com.example.archerytimer

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjectionConfig
import android.media.AudioManager
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
import com.example.archerytimer.music.AudioCaptureService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { }
            val controlMusicState = remember { ControlMusicState(applicationContext) }
            val projectionManager = remember {
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }
            val projectionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val data = result.data
                if (result.resultCode == RESULT_OK && data != null) {
                    val qqMusicIntent = qqMusicLaunchIntent()
                    val qqMusicPackage = qqMusicIntent?.component?.packageName
                        ?: qqMusicIntent?.`package`
                        ?: qqMusicIntent?.resolveActivity(packageManager)?.packageName
                    if (qqMusicPackage == null) return@rememberLauncherForActivityResult
                    val audioManager = getSystemService(AudioManager::class.java)
                    val previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val musicWasMuted = audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                    val service = Intent(applicationContext, AudioCaptureService::class.java)
                        .setAction(AudioCaptureService.ACTION_START)
                        .putExtra(AudioCaptureService.EXTRA_CODE, result.resultCode)
                        .putExtra(AudioCaptureService.EXTRA_DATA, data)
                        .putExtra(AudioCaptureService.EXTRA_CAPTURE_PACKAGE, qqMusicPackage)
                        .putExtra(AudioCaptureService.EXTRA_PREVIOUS_VOLUME, previousMusicVolume)
                        .putExtra(AudioCaptureService.EXTRA_WAS_MUTED, musicWasMuted)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
                    qqMusicIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(::startActivity)
                }
            }
            val requestProjection = {
                if (Build.VERSION.SDK_INT >= 29) {
                    val intent = if (Build.VERSION.SDK_INT >= 34) {
                        projectionManager.createScreenCaptureIntent(
                            MediaProjectionConfig.createConfigForDefaultDisplay(),
                        )
                    } else {
                        projectionManager.createScreenCaptureIntent()
                    }
                    projectionLauncher.launch(intent)
                }
            }
            val startMusicSession = requestProjection
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 31) {
                    bluetoothPermissionLauncher.launch(arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.RECORD_AUDIO,
                    ))
                } else {
                    bluetoothPermissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.RECORD_AUDIO,
                    ))
                }
            }
            val bluetoothTransport = remember { BluetoothTransport(applicationContext) }
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
                            onStartMusic = startMusicSession,
                            bluetoothTransport = bluetoothTransport,
                        )

                        is AppScreen.Countdown -> CountdownScreen(
                            config = current.config,
                            controlMusic = controlMusicState,
                            onStartMusic = startMusicSession,
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

    private fun qqMusicLaunchIntent(): Intent? =
        QQ_MUSIC_PACKAGES.firstNotNullOfOrNull(packageManager::getLaunchIntentForPackage)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://")).takeIf {
                it.resolveActivity(packageManager) != null
            }

    private companion object {
        val QQ_MUSIC_PACKAGES = listOf("com.tencent.qqmusic", "com.tencent.qqmusicpad")
    }

}

private sealed interface AppScreen {
    data object RoleSelection : AppScreen
    data object BluetoothMatching : AppScreen
    data object ControlSetup : AppScreen
    data class Countdown(val config: ArcheryConfig) : AppScreen
    data object Display : AppScreen
}
