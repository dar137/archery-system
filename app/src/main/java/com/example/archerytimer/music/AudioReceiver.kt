package com.example.archerytimer.music

import android.util.Log
import java.io.*
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Display-side discovery responder, TCP receiver and PCM player. */
class AudioReceiver {
    private val running = AtomicBoolean(false)
    private val player = RemoteAudioPlayer()
    private var discoverySocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread(name = "audio-discovery-responder") { respondToDiscovery() }
        thread(name = "audio-receiver") { acceptAudio() }
    }

    fun setDucked(ducked: Boolean) = player.setDucked(ducked)

    fun stop() {
        running.set(false)
        runCatching { discoverySocket?.close() }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        player.stop()
    }

    private fun respondToDiscovery() {
        try {
            discoverySocket = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(AudioProtocol.DISCOVERY_PORT)) }
            val buffer = ByteArray(128)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                discoverySocket?.receive(packet)
                if (String(packet.data, 0, packet.length) == AudioProtocol.DISCOVER) {
                    val response = AudioProtocol.AVAILABLE.toByteArray()
                    discoverySocket?.send(DatagramPacket(response, response.size, packet.address, packet.port))
                }
            }
        } catch (error: Throwable) { if (running.get()) Log.e(TAG, "Discovery responder failed", error) }
    }

    private fun acceptAudio() {
        try {
            serverSocket = ServerSocket(AudioProtocol.AUDIO_PORT)
            while (running.get()) {
                clientSocket = serverSocket?.accept()?.apply { tcpNoDelay = true }
                receiveClient(clientSocket ?: continue)
            }
        } catch (error: Throwable) { if (running.get()) Log.e(TAG, "Audio receiver failed", error) }
        finally { player.stop() }
    }

    private fun receiveClient(socket: Socket) {
        try {
            DataInputStream(BufferedInputStream(socket.getInputStream())).use { input ->
                if (input.readInt() != AudioProtocol.MAGIC) return
                val rate = input.readInt(); val channels = input.readInt()
                player.start(rate, channels)
                val buffer = ByteArray(64 * 1024)
                while (running.get()) {
                    val length = input.readInt()
                    if (length !in 1..buffer.size) throw IOException("Invalid PCM frame: $length")
                    input.readFully(buffer, 0, length)
                    player.write(buffer, length)
                }
            }
        } catch (_: EOFException) {
        } catch (error: Throwable) { if (running.get()) Log.w(TAG, "Audio client disconnected", error) }
        finally { player.stop(); runCatching { socket.close() }; clientSocket = null }
    }

    private companion object { const val TAG = "AudioReceiver" }
}
