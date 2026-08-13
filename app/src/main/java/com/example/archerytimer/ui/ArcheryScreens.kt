package com.example.archerytimer.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.archerytimer.communication.FakeDisplayTransport
import com.example.archerytimer.communication.RemoteMatchPhase
import com.example.archerytimer.communication.ShootingGroup
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
fun DisplayScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val viewModel = remember { DisplayViewModel(FakeDisplayTransport()) }
    val uiState by viewModel.uiState.collectAsState(initial = DisplayUiState())
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }
    DisposableEffect(activity) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val remoteState = uiState.matchState
    val activeGroup = if (
        remoteState?.phase == RemoteMatchPhase.PREPARATION ||
        remoteState?.phase == RemoteMatchPhase.SHOOTING
    ) {
        remoteState.activeGroup
    } else {
        ShootingGroup.NONE
    }

    Column(
        modifier = Modifier.fillMaxSize().background(DisplayBackground).padding(24.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            DisplayCenterContent(uiState)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 72.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIndicator("AB", activeGroup == ShootingGroup.AB)
            GroupIndicator("CD", activeGroup == ShootingGroup.CD)
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
private fun DisplayCenterContent(uiState: DisplayUiState) {
    val state = uiState.matchState
    if (uiState.connectionState != ConnectionState.CONNECTED || state == null) {
        Text(
            text = if (uiState.connectionState == ConnectionState.MATCHED) "匹配成功" else "匹配中",
            color = Color.White,
            fontSize = 64.sp,
        )
        return
    }

    when (state.phase) {
        RemoteMatchPhase.PREPARATION -> RemoteCountdown(
            state.remainingMillis,
            PreparationOrange,
        )
        RemoteMatchPhase.SHOOTING -> RemoteCountdown(
            state.remainingMillis,
            if (state.remainingMillis <= 10_000L) DisplayRed else DisplayGreen,
        )
        RemoteMatchPhase.WAITING -> DisplayStatusText("比赛待开始")
        RemoteMatchPhase.PAUSED -> DisplayStatusText("暂停中")
        RemoteMatchPhase.PULL_ARROWS -> DisplayStatusText("请拔箭")
        RemoteMatchPhase.FINISHED -> DisplayStatusText("比赛结束")
    }
}

@Composable
private fun RemoteCountdown(remainingMillis: Long, color: Color) {
    val seconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1_000L
    Text(
        text = seconds.toString().padStart(3, '0'),
        color = color,
        fontSize = 180.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DisplayStatusText(text: String) {
    Text(text = text, color = Color.White, fontSize = 64.sp, textAlign = TextAlign.Center)
}

@Composable
private fun GroupIndicator(label: String, active: Boolean) {
    val color = if (active) DisplayGreen else DisplayInactive
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).background(color, CircleShape))
        Text(text = label, color = color, fontSize = 40.sp)
    }
}

@Composable
fun ControlSetupScreen(
    onConfirmed: (ArcheryConfig) -> Unit,
    onBack: () -> Unit,
) {
    var totalArrows by remember { mutableStateOf("720") }
    var arrowsPerRound by remember { mutableStateOf("6") }
    var secondsPerArrow by remember { mutableStateOf("30") }
    var preparationSeconds by remember { mutableStateOf("10") }
    var firstLane by remember { mutableStateOf(Lane.AB) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "←  返回",
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable(onClick = onBack)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        )
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
    onExitConfirmed: () -> Unit,
) {
    val match = remember(config) { ArcheryMatchState(config) }
    var showExitDialog by remember { mutableStateOf(false) }
    var resumeAfterDialog by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier.fillMaxSize().background(PageBackground).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "←  返回",
            color = Color.Black,
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .clickable(onClick = ::requestExit)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        )
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
                DialogButton("否，让箭再飞一会", ::cancelExit)
            },
        )
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
