package com.example.archerytimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.archerytimer.model.ArcheryConfig
import com.example.archerytimer.model.ArcheryMatchState
import com.example.archerytimer.model.Lane
import com.example.archerytimer.model.TimerState
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
fun DisplayScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("显示端", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Text("暂未连接控制端")
    }
}

@Composable
fun ControlSetupScreen(onConfirmed: (ArcheryConfig) -> Unit) {
    var totalRounds by remember { mutableStateOf("") }
    var arrowsPerRound by remember { mutableStateOf("") }
    var secondsPerArrow by remember { mutableStateOf("30") }
    var firstLane by remember { mutableStateOf(Lane.AB) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("比赛参数设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        NumberField("射箭轮数", totalRounds) { totalRounds = it }
        NumberField("每轮箭数", arrowsPerRound) { arrowsPerRound = it }
        NumberField("每箭时间（秒）", secondsPerArrow) { secondsPerArrow = it }
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
                val rounds = totalRounds.toIntOrNull()
                val arrows = arrowsPerRound.toIntOrNull()
                val seconds = secondsPerArrow.toIntOrNull()
                if (rounds == null || rounds <= 0 || arrows == null || arrows <= 0 ||
                    seconds == null || seconds <= 0
                ) {
                    error = "请输入大于 0 的正整数"
                } else if (arrows > Int.MAX_VALUE / seconds) {
                    error = "每轮箭数或每箭时间过大"
                } else {
                    onConfirmed(ArcheryConfig(rounds, arrows, seconds, firstLane))
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
fun CountdownScreen(config: ArcheryConfig) {
    val match = remember(config) { ArcheryMatchState(config) }

    LaunchedEffect(match.timerState) {
        if (match.timerState == TimerState.COUNTING) {
            while (match.timerState == TimerState.COUNTING) {
                delay(1_000)
                match.tick()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (match.timerState) {
                    TimerState.WAITING_FOR_CONTINUE -> "请拔箭"
                    TimerState.FINISHED -> "比赛结束"
                    else -> match.remainingSeconds.toString()
                },
                color = Color.White,
                fontSize = if (match.timerState == TimerState.READY ||
                    match.timerState == TimerState.COUNTING
                ) 120.sp else 56.sp,
                textAlign = TextAlign.Center,
            )
        }

        MatchInfo(config, match)
        Spacer(Modifier.height(28.dp))

        when (match.timerState) {
            TimerState.READY -> ActionButton("开始发射", match::start)
            TimerState.WAITING_FOR_CONTINUE -> ActionButton("继续发射", match::continueMatch)
            TimerState.COUNTING, TimerState.FINISHED -> Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun MatchInfo(config: ArcheryConfig, match: ArcheryMatchState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        InfoText("总轮数：${config.totalRounds}")
        InfoText("每轮箭数：${config.arrowsPerRound}")
        InfoText("每箭时间：${config.secondsPerArrow} 秒")
        InfoText("先发靶道：${config.firstLane}")
        Spacer(Modifier.height(36.dp))
        InfoText("当前轮数：${match.currentRound}")
        InfoText("当前发射靶道：${match.currentLane}")
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(text)
    }
}
