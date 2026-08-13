package com.example.archerytimer.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

data class NearbyBluetoothDevice(val name: String, val address: String, val bonded: Boolean)

data class BluetoothUiState(
    val connectionState: ConnectionState = ConnectionState.MATCHING,
    val devices: List<NearbyBluetoothDevice> = emptyList(),
    val scanning: Boolean = false,
    val error: String? = null,
)

class BluetoothTransport(context: Context) : DisplayTransport, MusicControlTransport {
    private val appContext = context.applicationContext
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val displayFlow = MutableSharedFlow<DisplayMessage>(extraBufferCapacity = 64)
    private val responseFlow = MutableSharedFlow<MusicResponse>(extraBufferCapacity = 32)
    override val responses: Flow<MusicResponse> = responseFlow.asSharedFlow()
    private var socket: BluetoothSocket? = null
    private var serverSocket: BluetoothServerSocket? = null
    private var readerJob: Job? = null
    private val stateFlow = MutableStateFlow(BluetoothUiState())
    val uiState: StateFlow<BluetoothUiState> = stateFlow.asStateFlow()
    private var pendingConnectAddress: String? = null
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDevice() ?: return
                    addDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    stateFlow.value = stateFlow.value.copy(scanning = false)
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.bluetoothDevice() ?: return
                    addDevice(device)
                    if (device.bondState == BluetoothDevice.BOND_BONDED &&
                        pendingConnectAddress == device.address
                    ) {
                        pendingConnectAddress = null
                        connect(device.address, waitForService = true)
                    }
                }
            }
        }
    }

    init {
        appContext.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            },
        )
    }

    override fun messages(): Flow<DisplayMessage> = displayFlow.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val bluetoothAdapter = adapter ?: return updateError("此设备不支持蓝牙")
        if (!bluetoothAdapter.isEnabled) return updateError("请先开启蓝牙")
        val paired = bluetoothAdapter.bondedDevices.orEmpty().map {
            NearbyBluetoothDevice(it.name ?: "未命名设备", it.address, true)
        }
        stateFlow.value = BluetoothUiState(devices = paired.sortedBy { it.name }, scanning = true)
        bluetoothAdapter.cancelDiscovery()
        if (!bluetoothAdapter.startDiscovery()) updateError("无法开始搜索附近设备")
    }

    @SuppressLint("MissingPermission")
    fun pairAndConnect(address: String) {
        val device = adapter?.getRemoteDevice(address) ?: return updateError("找不到蓝牙设备")
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connect(address, waitForService = true)
        } else {
            pendingConnectAddress = address
            stateFlow.value = stateFlow.value.copy(error = "请在系统配对弹窗中确认")
            if (!device.createBond()) updateError("无法发起配对")
        }
    }

    @SuppressLint("MissingPermission")
    fun startDisplayServer() {
        disconnect()
        displayFlow.tryEmit(DisplayMessage.ConnectionChanged(ConnectionState.MATCHING))
        stateFlow.value = stateFlow.value.copy(connectionState = ConnectionState.MATCHING, error = null)
        scope.launch {
            runCatching {
                serverSocket = adapter?.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                attach(serverSocket?.accept() ?: error("蓝牙不可用"))
            }.onFailure { updateError("等待连接失败：${it.message ?: "未知错误"}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, waitForService: Boolean = false) {
        disconnect()
        stateFlow.value = stateFlow.value.copy(error = null)
        scope.launch {
            if (waitForService) delay(800)
            runCatching {
                val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: error("蓝牙不可用")
                adapter.cancelDiscovery()
                var lastError: Throwable? = null
                repeat(2) { attempt ->
                    val newSocket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID)
                    try {
                        newSocket.connect()
                        attach(newSocket)
                        return@runCatching
                    } catch (error: Throwable) {
                        lastError = error
                        runCatching { newSocket.close() }
                        if (attempt == 0) delay(1_000)
                    }
                }
                throw lastError ?: error("连接失败")
            }.onFailure {
                updateError(
                    "连接失败：${it.message ?: "Socket 超时"}。请让显示端停留在匹配中并允许被发现后重试",
                )
            }
        }
    }

    private fun attach(newSocket: BluetoothSocket) {
        socket = newSocket
        serverSocket?.close(); serverSocket = null
        displayFlow.tryEmit(DisplayMessage.ConnectionChanged(ConnectionState.MATCHED))
        displayFlow.tryEmit(DisplayMessage.ConnectionChanged(ConnectionState.CONNECTED))
        stateFlow.value = stateFlow.value.copy(connectionState = ConnectionState.CONNECTED, scanning = false, error = null)
        readerJob = scope.launch {
            val reader = BufferedReader(InputStreamReader(newSocket.inputStream, Charsets.UTF_8))
            while (true) {
                val line = reader.readLine() ?: break
                BluetoothMessageCodec.decodeDisplay(line)?.let { displayFlow.emit(it) }
                    ?: BluetoothMessageCodec.decodeResponse(line)?.let { responseFlow.emit(it) }
            }
            disconnect()
        }
    }

    fun sendMatch(state: RemoteMatchState) = write(BluetoothMessageCodec.encodeMatch(state))
    override fun send(command: MusicCommand) = write(BluetoothMessageCodec.encodeCommand(command))
    override fun send(response: MusicResponse) = write(BluetoothMessageCodec.encodeResponse(response))

    @Synchronized
    private fun write(line: String) {
        val activeSocket = socket ?: return
        scope.launch {
            runCatching {
                BufferedWriter(OutputStreamWriter(activeSocket.outputStream, Charsets.UTF_8)).apply {
                    write(line); newLine(); flush()
                }
            }
        }
    }

    override fun disconnect() {
        readerJob?.cancel(); readerJob = null
        runCatching { socket?.close() }; socket = null
        runCatching { serverSocket?.close() }; serverSocket = null
        stateFlow.value = stateFlow.value.copy(connectionState = ConnectionState.MATCHING)
    }

    override fun release() {
        disconnect()
        runCatching { appContext.unregisterReceiver(receiver) }
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        val item = NearbyBluetoothDevice(
            device.name ?: "未命名设备",
            device.address,
            device.bondState == BluetoothDevice.BOND_BONDED,
        )
        val devices = stateFlow.value.devices.filterNot { it.address == item.address } + item
        stateFlow.value = stateFlow.value.copy(devices = devices.sortedBy { it.name })
    }

    private fun updateError(message: String) {
        stateFlow.value = stateFlow.value.copy(scanning = false, error = message)
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? =
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private companion object {
        const val SERVICE_NAME = "ArcheryTimer"
        val SERVICE_UUID: UUID = UUID.fromString("8d6f2b8e-63d5-4f2a-9b6c-7b8e2a8d1370")
    }
}
