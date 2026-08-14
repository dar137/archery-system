package com.example.archerytimer.ui

import android.app.Activity
import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.archerytimer.model.ArcheryConfig
import com.example.archerytimer.model.ArcheryMatchState
import com.example.archerytimer.model.CountdownPhase
import com.example.archerytimer.model.Lane
import com.example.archerytimer.model.TimerState
import com.example.archerytimer.communication.ConnectionState
import com.example.archerytimer.communication.BluetoothTransport
import com.example.archerytimer.communication.RemoteMatchPhase
import com.example.archerytimer.communication.ShootingGroup
import com.example.archerytimer.audio.BeepPlayer
import com.example.archerytimer.music.DisplayMusicPlayer
import com.example.archerytimer.music.LocalMusicRepository
import com.example.archerytimer.music.MusicCommandHandler
import kotlinx.coroutines.delay

@Composable
fun RoleSelectionScreen(
    onControlSelected: () -> Unit,
    onDisplaySelected: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("请选择操作端", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onControlSelected, modifier = Modifier.fillMaxWidth()) {
            Text("控制端")
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDisplaySelected, modifier = Modifier.fillMaxWidth()) {
            Text("显示端")
        }
    }
}

@Composable
fun BluetoothMatchingScreen(
    bluetoothTransport: BluetoothTransport,
    onConnected: () -> Unit,
    onBack: () -> Unit,
) {
    val state by bluetoothTransport.uiState.collectAsState()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { bluetoothTransport.startDiscovery() }
    LaunchedEffect(state.connectionState) {
        if (state.connectionState == ConnectionState.CONNECTED) onConnected()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("←  返回", modifier = Modifier.align(Alignment.Start).clickable(onClick = onBack)
            .padding(vertical = 10.dp))
        Text("选择显示端", style = MaterialTheme.typography.headlineMedium)
        Text(if (state.scanning) "正在搜索附近设备…" else "搜索完成", modifier = Modifier.padding(12.dp))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
        ) {
            state.devices.forEach { device ->
                Text(
                    text = "${device.name}${if (device.bonded) "（已配对）" else ""}\n${device.address}",
                    modifier = Modifier.fillMaxWidth().clickable {
                        bluetoothTransport.pairAndConnect(device.address)
                    }.padding(vertical = 14.dp),
                )
            }
        }
        Button(onClick = bluetoothTransport::startDiscovery, modifier = Modifier.fillMaxWidth()) {
            Text("重新搜索")
        }
    }
}

@Composable
fun DisplayScreen(bluetoothTransport: BluetoothTransport, onBack: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var permissionResolved by remember {
        mutableStateOf(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionResolved = true
    }
    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    LaunchedEffect(audioPermission) {
        if (!permissionResolved) permissionLauncher.launch(audioPermission)
    }

    if (!permissionResolved) {
        Box(
            modifier = Modifier.fillMaxSize().background(DisplayBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text("正在请求本地音乐权限", color = Color.White, fontSize = 28.sp)
        }
        return
    }

    val transport = bluetoothTransport
    val viewModel = remember { DisplayViewModel(transport) }
    val musicHandler = remember {
        MusicCommandHandler(
            LocalMusicRepository(context.applicationContext),
            DisplayMusicPlayer(context.applicationContext),
            transport,
        )
    }
    val beepPlayer = remember { BeepPlayer() }
    val uiState by viewModel.uiState.collectAsState(initial = DisplayUiState())
    var showExitDialog by remember { mutableStateOf(false) }
    var showMatchedSuccess by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }
    LaunchedEffect(uiState.connectionState) {
        if (uiState.connectionState == ConnectionState.CONNECTED) {
            showMatchedSuccess = true
            delay(1_500)
            showMatchedSuccess = false
        } else {
            showMatchedSuccess = false
        }
    }
    LaunchedEffect(uiState.beepEventId) {
        if (uiState.beepEventId > 0L) {
            musicHandler.setMusicDucked(true)
            try {
                delay(80)
                beepPlayer.play()
                delay(380)
            } finally {
                musicHandler.setMusicDucked(false)
            }
        }
    }
    DisposableEffect(activity) {
        viewModel.onMusicCommand = musicHandler::handle
        bluetoothTransport.startDisplayServer()
        discoverableLauncher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(
                BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                300,
            ),
        )
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            beepPlayer.release()
            viewModel.onMusicCommand = {}
            musicHandler.release()
            bluetoothTransport.disconnect()
        }
    }

    val remoteState = uiState.matchState
    var firstGroupFinished by remember { mutableStateOf(false) }
    var pullArrowsText by remember { mutableStateOf("请拔箭") }
    var previousRemoteState by remember {
        mutableStateOf<com.example.archerytimer.communication.RemoteMatchState?>(null)
    }
    LaunchedEffect(remoteState?.sequence) {
        val current = remoteState ?: return@LaunchedEffect
        val previous = previousRemoteState
        if (current.phase == RemoteMatchPhase.WAITING) {
            // A waiting state starts a fresh match, so discard the previous
            // match's display-only first/second-group marker.
            firstGroupFinished = false
            pullArrowsText = "请拔箭"
        } else if (
            current.phase == RemoteMatchPhase.PULL_ARROWS &&
            previous?.phase == RemoteMatchPhase.SHOOTING
        ) {
            if (firstGroupFinished) {
                pullArrowsText = "请拔箭"
                firstGroupFinished = false
            } else {
                pullArrowsText = if (previous.activeGroup == ShootingGroup.AB) {
                    "CD准备"
                } else {
                    "AB准备"
                }
                firstGroupFinished = true
            }
        }
        previousRemoteState = current
    }
    val activeGroup = if (
        remoteState?.phase == RemoteMatchPhase.PREPARATION ||
        remoteState?.phase == RemoteMatchPhase.SHOOTING
    ) {
        remoteState.activeGroup
    } else {
        ShootingGroup.NONE
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DisplayBackground).padding(12.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(4.3f).offset(y = (-10).dp),
            contentAlignment = Alignment.Center,
        ) {
            DisplayCenterContent(uiState, showMatchedSuccess, pullArrowsText)
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val lightSize = minOf(maxHeight * 0.98f, maxWidth * 0.085f)
            val labelSize = minOf(maxHeight.value * 1.02f, maxWidth.value * 0.095f).sp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GroupIndicator("AB", activeGroup == ShootingGroup.AB, lightSize, labelSize)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    GroupIndicator("CD", activeGroup == ShootingGroup.CD, lightSize, labelSize)
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = DialogBackground,
            titleContentColor = Color.Black,
            title = { Text("是否确认退出", color = Color.Black) },
            confirmButton = { DialogButton("是", onBack) },
            dismissButton = {
                DialogButton("否") { showExitDialog = false }
            },
        )
    }
}

@Composable
private fun DisplayCenterContent(
    uiState: DisplayUiState,
    showMatchedSuccess: Boolean,
    pullArrowsText: String,
) {
    val state = uiState.matchState
    if (showMatchedSuccess || uiState.connectionState != ConnectionState.CONNECTED) {
        DisplayStatusText(
            if (showMatchedSuccess || uiState.connectionState == ConnectionState.MATCHED) {
                "匹配成功"
            } else {
                "匹配中"
            },
        )
        return
    }

    if (state == null) {
        DisplayStatusText("比赛待开始")
        return
    }

    when (state.phase) {
        RemoteMatchPhase.PREPARATION -> RemoteCountdown(state, preparation = true)
        RemoteMatchPhase.SHOOTING -> RemoteCountdown(state, preparation = false)
        RemoteMatchPhase.WAITING -> DisplayStatusText("比赛待开始")
        RemoteMatchPhase.PAUSED -> DisplayStatusText("暂停中")
        RemoteMatchPhase.PULL_ARROWS -> DisplayStatusText(pullArrowsText)
        RemoteMatchPhase.FINISHED -> DisplayStatusText("比赛结束")
    }
}

@Composable
private fun RemoteCountdown(
    state: com.example.archerytimer.communication.RemoteMatchState,
    preparation: Boolean,
) {
    var displayedMillis by remember(state.sequence) { mutableStateOf(state.remainingMillis) }
    LaunchedEffect(state.sequence) {
        val receivedAt = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - receivedAt
            displayedMillis = (state.remainingMillis - elapsed).coerceAtLeast(0L)
            delay(100)
        }
    }
    val seconds = (displayedMillis + 999L) / 1_000L
    val color = if (preparation) PreparationOrange else {
        if (displayedMillis <= 10_000L) DisplayRed else DisplayGreen
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().offset(y = (-34).dp),
        contentAlignment = Alignment.Center,
    ) {
        val countdownSize = minOf(maxHeight.value * 0.98f, maxWidth.value * 0.62f).sp
        Text(
            text = seconds.toString().padStart(3, '0'),
            color = color,
            fontSize = countdownSize,
            fontWeight = FontWeight.Bold,
            lineHeight = countdownSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DisplayStatusText(text: String) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val widthLimitedSize = maxWidth.value / (text.length.coerceAtLeast(2) * 1.12f)
        val statusSize = minOf(maxHeight.value * 0.50f, widthLimitedSize).sp
        Text(
            text = text,
            color = Color.White,
            fontSize = statusSize,
            fontWeight = FontWeight.Bold,
            lineHeight = statusSize,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GroupIndicator(
    label: String,
    active: Boolean,
    lightSize: androidx.compose.ui.unit.Dp = 28.dp,
    labelSize: androidx.compose.ui.unit.TextUnit = 40.sp,
) {
    val color = if (active) DisplayGreen else DisplayInactive
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(lightSize).background(color, CircleShape))
        Text(text = label, color = color, fontSize = labelSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ControlSetupScreen(
    onConfirmed: (ArcheryConfig) -> Unit,
    onBack: () -> Unit,
    controlMusic: ControlMusicState,
    bluetoothTransport: BluetoothTransport,
) {
    var totalArrows by remember { mutableStateOf("72") }
    var arrowsPerRound by remember { mutableStateOf("6") }
    var secondsPerArrow by remember { mutableStateOf("30") }
    var preparationSeconds by remember { mutableStateOf("10") }
    var firstLane by remember { mutableStateOf(Lane.AB) }
    var error by remember { mutableStateOf<String?>(null) }
    val musicState by controlMusic.uiState.collectAsState(initial = ControlMusicUiState())
    var showMusicDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "←  返回",
                color = Color.Black,
                fontSize = 18.sp,
                modifier = Modifier.clickable(onClick = onBack)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            )
            ControlMusicPanel(controlMusic, musicState) { showMusicDialog = true }
        }
        Text("比赛参数设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        NumberField("总箭数", totalArrows) { totalArrows = it }
        NumberField("每轮箭数", arrowsPerRound) { arrowsPerRound = it }
        NumberField("每箭时间（秒）", secondsPerArrow) { secondsPerArrow = it }
        NumberField("准备时间（秒）", preparationSeconds) { preparationSeconds = it }
        Spacer(Modifier.height(12.dp))
        Text("先发靶道", modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth()) {
            Lane.entries.forEach { lane ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = firstLane == lane, onClick = { firstLane = lane })
                    Text(lane.name)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                val arrowsTotal = totalArrows.toIntOrNull()
                val arrows = arrowsPerRound.toIntOrNull()
                val seconds = secondsPerArrow.toIntOrNull()
                val preparation = preparationSeconds.toIntOrNull()
                if (preparation == null || preparation < 0) {
                    error = "准备时间请输入非负整数"
                } else if (arrowsTotal == null || arrowsTotal <= 0 ||
                    arrows == null || arrows <= 0 || seconds == null || seconds <= 0
                ) {
                    error = "请输入大于 0 的正整数"
                } else if (arrowsTotal % arrows != 0) {
                    error = "总箭数必须能够被每轮箭数整除"
                } else if (arrows > Int.MAX_VALUE / seconds) {
                    error = "每轮箭数或每箭时间过大"
                } else {
                    onConfirmed(ArcheryConfig(arrowsTotal, arrows, seconds, preparation, firstLane))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("确定")
        }
    }

    if (showMusicDialog) {
        MusicLibraryDialog(
            musicState = musicState,
            controlMusic = controlMusic,
            onDismiss = { showMusicDialog = false },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}

@Composable
fun CountdownScreen(
    config: ArcheryConfig,
    controlMusic: ControlMusicState,
    bluetoothTransport: BluetoothTransport,
    onExitConfirmed: () -> Unit,
) {
    val match = remember(config) { ArcheryMatchState(config) }
    var showExitDialog by remember { mutableStateOf(false) }
    var resumeAfterDialog by remember { mutableStateOf(false) }
    val musicState by controlMusic.uiState.collectAsState(initial = ControlMusicUiState())
    var showMusicDialog by remember { mutableStateOf(false) }

    fun requestExit() {
        if (showExitDialog) return
        resumeAfterDialog = match.timerState == TimerState.COUNTING
        if (resumeAfterDialog) match.pause()
        showExitDialog = true
    }

    fun cancelExit() {
        showExitDialog = false
        if (resumeAfterDialog) match.resume()
        resumeAfterDialog = false
    }

    BackHandler(onBack = ::requestExit)

    LaunchedEffect(match.timerState) {
        if (match.timerState == TimerState.COUNTING) {
            while (match.timerState == TimerState.COUNTING) {
                delay(1_000)
                match.tick()
            }
        }
    }

    LaunchedEffect(
        match.timerState,
        match.countdownPhase,
        match.remainingSeconds,
        match.currentLane,
    ) {
        val phase = when (match.timerState) {
            TimerState.READY -> RemoteMatchPhase.WAITING
            TimerState.COUNTING -> if (match.countdownPhase == CountdownPhase.PREPARATION) {
                RemoteMatchPhase.PREPARATION
            } else RemoteMatchPhase.SHOOTING
            TimerState.PAUSED -> RemoteMatchPhase.PAUSED
            TimerState.WAITING_FOR_CONTINUE -> RemoteMatchPhase.PULL_ARROWS
            TimerState.FINISHED -> RemoteMatchPhase.FINISHED
        }
        val group = if (phase == RemoteMatchPhase.PREPARATION || phase == RemoteMatchPhase.SHOOTING) {
            if (match.currentLane == Lane.AB) ShootingGroup.AB else ShootingGroup.CD
        } else ShootingGroup.NONE
        bluetoothTransport.sendMatch(
            com.example.archerytimer.communication.RemoteMatchState(
                sequence = 0L,
                phase = phase,
                activeGroup = group,
                remainingMillis = match.remainingSeconds * 1_000L,
            ),
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PageBackground).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "←  返回",
                color = Color.Black,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = ::requestExit)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
            )
            ControlMusicPanel(controlMusic, musicState) { showMusicDialog = true }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            when (match.timerState) {
                TimerState.READY, TimerState.COUNTING -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (match.countdownPhase == CountdownPhase.PREPARATION) {
                            "准备时间"
                        } else {
                            "射箭时间"
                        },
                        color = Color.Black,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TrafficLight(
                        timerState = match.timerState,
                        countdownPhase = match.countdownPhase,
                        remainingSeconds = match.remainingSeconds,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlipNumberDisplay(
                        value = match.remainingSeconds.toString().padStart(3, '0'),
                    )
                }
                TimerState.PAUSED -> StatusWithTrafficLight("暂停中")
                TimerState.WAITING_FOR_CONTINUE -> StatusWithTrafficLight("请拔箭")
                TimerState.FINISHED -> StatusWithTrafficLight("比赛结束")
            }
        }

        Column(modifier = Modifier.fillMaxWidth().weight(0.64f)) {
            MatchInfo(config, match)
            Spacer(Modifier.weight(1f))

            when (match.timerState) {
                TimerState.READY -> ActionButton("开始发射", match::start)
                TimerState.COUNTING -> ActionButton("暂停射击", match::pause)
                TimerState.PAUSED -> {
                    ActionButton("继续射击", match::resume)
                    Spacer(Modifier.height(12.dp))
                    ActionButton("重新开始本次射击", match::restartCurrentShot)
                }
                TimerState.WAITING_FOR_CONTINUE ->
                    ActionButton("继续发射", match::continueMatch)
                TimerState.FINISHED -> Spacer(Modifier.height(48.dp))
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = ::cancelExit,
            containerColor = DialogBackground,
            titleContentColor = Color.Black,
            title = { Text("是否确认退出", color = Color.Black) },
            confirmButton = {
                DialogButton("是", onExitConfirmed)
            },
            dismissButton = {
                DialogButton("否", ::cancelExit)
            },
        )
    }

    if (showMusicDialog) {
        MusicLibraryDialog(
            musicState = musicState,
            controlMusic = controlMusic,
            onDismiss = { showMusicDialog = false },
        )
    }
}

@Composable
private fun ControlMusicPanel(
    controlMusic: ControlMusicState,
    musicState: ControlMusicUiState,
    onOpenLibrary: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(42.dp).clickable {
                onOpenLibrary()
                controlMusic.requestLibrary()
            },
            contentAlignment = Alignment.Center,
        ) {
            Text("♫", color = Color.Black, fontSize = 30.sp)
        }
        if (musicState.trackId != null) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .background(Color(0xFFE8DEF8), RoundedCornerShape(15.dp))
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                CompactMusicButton("|◀", "上一首", controlMusic::previous)
                CompactMusicButton(
                    if (musicState.isPlaying) "Ⅱ" else "▶",
                    if (musicState.isPlaying) "暂停" else "继续",
                ) { controlMusic.togglePlayback(musicState.isPlaying) }
                CompactMusicButton("▶|", "下一首", controlMusic::next)
            }
        }
    }
}

@Composable
private fun MusicLibraryDialog(
    musicState: ControlMusicUiState,
    controlMusic: ControlMusicState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBackground,
        title = { Text("显示端本地音乐", color = Color.Black) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    musicState.error != null -> Text(musicState.error ?: "加载失败", color = Color.Black)
                    !musicState.libraryLoaded -> Text("正在加载…", color = Color.Black)
                    musicState.tracks.isEmpty() -> Text("显示端没有可用的本地音乐", color = Color.Black)
                    else -> musicState.tracks.forEach { track ->
                        Text(
                            text = "${track.title} · ${track.artist}",
                            color = Color.Black,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { controlMusic.play(track.trackId) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { DialogButton("关闭", onDismiss) },
    )
}

@Composable
private fun CompactMusicButton(symbol: String, description: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBackground,
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(31.dp),
        shape = CircleShape,
    ) {
        Text(symbol, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun MatchInfo(config: ArcheryConfig, match: ArcheryMatchState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfoText("总箭数：${config.totalArrows}")
        InfoText("射箭轮数：${config.rounds}")
        InfoText("每轮箭数：${config.arrowsPerRound}")
        InfoText("每箭时间：${config.secondsPerArrow} 秒")
        InfoText("准备时间：${config.preparationSeconds} 秒")
        InfoText("先发靶道：${config.firstLane}")
        Spacer(Modifier.height(24.dp))
        InfoText("当前轮数：${match.currentRound}")
        InfoText("当前发射靶道：${match.currentLane}")
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, color = InfoTextColor, fontSize = 20.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBackground,
            contentColor = Color.White,
        ),
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
private fun StatusDisplay(text: String) {
    Text(
        text = text,
        color = Color.Black,
        fontSize = 48.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusWithTrafficLight(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TrafficLight(
            timerState = TimerState.PAUSED,
            countdownPhase = CountdownPhase.SHOOTING,
            remainingSeconds = 0,
        )
        Spacer(Modifier.height(12.dp))
        StatusDisplay(text)
    }
}

@Composable
private fun TrafficLight(
    timerState: TimerState,
    countdownPhase: CountdownPhase,
    remainingSeconds: Int,
) {
    val activeLight = when {
        timerState != TimerState.COUNTING -> ActiveLight.NONE
        countdownPhase == CountdownPhase.PREPARATION -> ActiveLight.YELLOW
        remainingSeconds <= 10 -> ActiveLight.RED
        else -> ActiveLight.GREEN
    }

    Row(
        modifier = Modifier
            .background(Color(0xFF2C2C2C), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0xFF555555), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalLamp(if (activeLight == ActiveLight.RED) Color(0xFFE53935) else LampOff)
        SignalLamp(if (activeLight == ActiveLight.YELLOW) Color(0xFFFFC107) else LampOff)
        SignalLamp(if (activeLight == ActiveLight.GREEN) Color(0xFF43A047) else LampOff)
    }
}

@Composable
private fun SignalLamp(color: Color) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .background(color, CircleShape)
            .border(1.dp, Color(0xFF777777), CircleShape),
    )
}

@Composable
private fun DialogButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonBackground,
            contentColor = Color.White,
        ),
    ) {
        Text(text, color = Color.White)
    }
}

private val PageBackground = Color(0xFFFFF7FF)
private val DialogBackground = Color(0xFFFFF7FF)
private val ButtonBackground = Color(0xFF6750A4)
private val InfoTextColor = Color(0xFF1D1B20)
private val LampOff = Color(0xFF777777)

private enum class ActiveLight {
    NONE,
    RED,
    YELLOW,
    GREEN,
}

private val DisplayBackground = Color(0xFF121212)
private val DisplayGreen = Color(0xFF43A047)
private val DisplayRed = Color(0xFFE53935)
private val PreparationOrange = Color(0xFFFFB300)
private val DisplayInactive = Color(0xFF777777)
