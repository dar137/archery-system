package com.example.archerytimer.music

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Network-only PCM sender. Encoding can be inserted before [send] without changing capture code. */
class AudioSender(context: Context, private val onDisconnected: () -> Unit) {
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private val lock = Any()
    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var connectedOnce = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "audio-display-discovery") {
            while (running.get() && socket == null) {
                discoverAndConnect()
                if (socket == null) Thread.sleep(1_000)
            }
        }
    }

    fun send(pcm: ByteArray, length: Int): Boolean = synchronized(lock) {
        val stream = output ?: return true // Drop live audio until discovery completes.
        try {
            stream.writeInt(length)
            stream.write(pcm, 0, length)
            stream.flush()
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Display audio connection lost", error)
            closeSocket()
            if (connectedOnce) onDisconnected()
            false
        }
    }

    fun stop() {
        running.set(false)
        synchronized(lock) { closeSocket() }
    }

    private fun discoverAndConnect() {
        val multicastLock = (appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createMulticastLock("archery-audio-discovery").apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { udp ->
                udp.broadcast = true
                udp.soTimeout = 1_200
                val request = AudioProtocol.DISCOVER.toByteArray()
                udp.send(DatagramPacket(request, request.size, InetAddress.getByName("255.255.255.255"), AudioProtocol.DISCOVERY_PORT))
                val response = DatagramPacket(ByteArray(128), 128)
                udp.receive(response)
                if (String(response.data, 0, response.length) != AudioProtocol.AVAILABLE) return
                val newSocket = Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(response.address, AudioProtocol.AUDIO_PORT), 2_000) }
                val stream = DataOutputStream(BufferedOutputStream(newSocket.getOutputStream())).apply {
                    writeInt(AudioProtocol.MAGIC); writeInt(AudioProtocol.SAMPLE_RATE); writeInt(AudioProtocol.CHANNELS); flush()
                }
                synchronized(lock) { socket = newSocket; output = stream; connectedOnce = true }
                Log.i(TAG, "Connected to display ${response.address.hostAddress}")
            }
        } catch (_: SocketTimeoutException) {
        } catch (error: Throwable) {
            Log.w(TAG, "Audio display discovery failed", error)
        } finally {
            if (multicastLock.isHeld) multicastLock.release()
        }
    }

    private fun closeSocket() {
        runCatching { output?.close() }; output = null
        runCatching { socket?.close() }; socket = null
    }

    private companion object { const val TAG = "AudioSender" }
}
