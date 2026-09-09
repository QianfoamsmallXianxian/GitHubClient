package com.githubclient.app.ui.screens.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.terminal.TerminalOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val useRoot by viewModel.useRoot.collectAsState()
    val termuxInstalled by viewModel.termuxInstalled.collectAsState()
    val termuxPermission by viewModel.termuxPermission.collectAsState()
    val message by viewModel.message.collectAsState()
    var command by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(history.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTermuxStatus() }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "刷新 Termux 状态")
                    }
                    IconButton(onClick = { viewModel.clear() }) {
                        Icon(Icons.Default.Clear, contentDescription = "清屏")
                    }
                    IconButton(onClick = { viewModel.stop() }, enabled = isRunning) {
                        Icon(Icons.Default.Stop, contentDescription = "停止")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Termux 状态卡
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Termux 状态", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = when {
                                    !termuxInstalled -> "未安装 Termux"
                                    !termuxPermission -> "已安装，未授予 RUN_COMMAND 权限"
                                    else -> "已安装，可调用"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (termuxInstalled && termuxPermission) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        if (termuxInstalled) {
                            Button(onClick = { viewModel.openTermux() }) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.height(16.dp)
                                )
                                Text("打开")
                            }
                        }
                    }

                    message?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (!termuxInstalled) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "检测到未安装 Termux。安装后可使用完整 Linux 环境。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 内置终端输出区
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (history.isEmpty()) {
                            item {
                                Text(
                                    text = "欢迎使用内置终端\n\n常用命令：\n  ls /sdcard\n  pwd\n  cat /proc/version\n  uname -a",
                                    color = Color(0xFF58A6FF),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        items(history) { output ->
                            TermOutputRow(output)
                        }
                    }
                }
            }

            // Root 开关
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = useRoot,
                    onCheckedChange = { viewModel.setUseRoot(it) }
                )
                Text(
                    text = "Root",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 命令输入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("输入命令") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        viewModel.execute(command)
                        command = ""
                    },
                    enabled = command.isNotBlank() && !isRunning
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "执行")
                }
            }

            // Termux 调用按钮
            if (termuxInstalled && termuxPermission) {
                Button(
                    onClick = {
                        viewModel.executeInTermux(command)
                    },
                    enabled = command.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("在 Termux 中执行")
                }
            }
        }
    }
}

@Composable
private fun TermOutputRow(output: TerminalOutput) {
    val color = if (output.isError) Color(0xFFF85149) else Color(0xFFE6EDF3)
    Text(
        text = output.text,
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}
