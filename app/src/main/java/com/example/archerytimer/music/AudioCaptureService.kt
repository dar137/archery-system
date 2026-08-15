package com.example.archerytimer.music

import android.app.*
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlin.concurrent.thread

@android.annotation.TargetApi(29)
class AudioCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    private var sender: AudioSender? = null
    private var previousMusicVolume: Int? = null
    private var musicWasMuted = false
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Music transmission", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Archery Timer")
            .setContentText("Music audio transmission is active")
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (projection != null) return START_NOT_STICKY
        val resultData = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java) else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra(EXTRA_DATA)
        } ?: run { stopSelf(); return START_NOT_STICKY }
        val resultCode = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val capturePackage = intent?.getStringExtra(EXTRA_CAPTURE_PACKAGE)
            ?: run { stopSelf(); return START_NOT_STICKY }
        val captureUid = try {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(capturePackage, 0).uid
        } catch (error: PackageManager.NameNotFoundException) {
            Log.e(TAG, "QQ Music package is not installed: $capturePackage", error)
            stopSelf()
            return START_NOT_STICKY
        }
        previousMusicVolume = intent.getIntExtra(EXTRA_PREVIOUS_VOLUME, 0)
        musicWasMuted = intent.getBooleanExtra(EXTRA_WAS_MUTED, false)
        projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() { override fun onStop() = stopCapture() }, null)
        sender = AudioSender(this) { stopCapture() }.also { it.start() }
        enforceLocalMediaMute()
        MusicSessionState.setActive(true)
        thread(name = "playback-capture") { captureLoop(captureUid) }
        return START_NOT_STICKY
    }

    private fun captureLoop(captureUid: Int) {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            val activeProjection = projection ?: return
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(activeProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUid(captureUid)
                .build()
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(AudioProtocol.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val minimum = AudioRecord.getMinBufferSize(AudioProtocol.SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            record = AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum * 2, 19_200))
                .setAudioPlaybackCaptureConfig(captureConfig).build()
            val buffer = ByteArray(19_200)
            record?.startRecording()
            while (!stopping) {
                val count = record?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: break
                if (count < 0 || sender?.send(buffer, count) == false) break
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Playback capture failed", error)
        } finally { stopCapture() }
    }

    @Synchronized private fun stopCapture() {
        if (stopping) return
        stopping = true
        MusicSessionState.setActive(false)
        runCatching { record?.stop() }; record?.release(); record = null
        sender?.stop(); sender = null
        restoreLocalMediaOutput()
        runCatching { projection?.stop() }; projection = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }

    private fun enforceLocalMediaMute() {
        val audioManager = getSystemService(AudioManager::class.java)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
    }

    private fun restoreLocalMediaOutput() {
        val volume = previousMusicVolume ?: return
        previousMusicVolume = null
        val audioManager = getSystemService(AudioManager::class.java)
        if (!musicWasMuted) audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    companion object {
        const val ACTION_START = "com.example.archerytimer.music.START"
        const val EXTRA_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_CAPTURE_PACKAGE = "capture_package"
        const val EXTRA_PREVIOUS_VOLUME = "previous_music_volume"
        const val EXTRA_WAS_MUTED = "music_was_muted"
        private const val CHANNEL = "music_transmission"
        private const val NOTIFICATION_ID = 7321
        private const val TAG = "AudioCaptureService"
    }
}
