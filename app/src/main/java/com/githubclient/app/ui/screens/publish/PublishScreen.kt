package com.githubclient.app.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.automation.PublishState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    onBack: () -> Unit,
    viewModel: PublishViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val log by viewModel.log.collectAsState()
    var repoName by remember { mutableStateOf("") }
    var sourcePath by remember { mutableStateOf("") }
    var triggerDispatch by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val running = state is PublishState.Running

    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("终端本地上传") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("仓库名称（owner 自动用登录账号）") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = sourcePath,
                onValueChange = { sourcePath = it },
                label = { Text("源码目录或 ZIP 路径，如 /sdcard/MyProj") },
                singleLine = true,
                enabled = !running,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("额外触发 workflow_dispatch", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = triggerDispatch,
                    onCheckedChange = { triggerDispatch = it },
                    enabled = !running
                )
            }
            Button(
                onClick = { viewModel.publish(repoName, sourcePath, triggerDispatch) },
                enabled = !running && repoName.isNotBlank() && sourcePath.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (running) "正在上传..." else "开始上传并构建")
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117))
            ) {
                if (log.isEmpty()) {
                    Text(
                        text = "填写仓库名称与源码路径后开始。\n上传使用当前登录 token 自动匹配账号；\npush 后监听 push 的 Actions 会自动构建。",
                        color = Color(0xFF8B949E),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(log) { line ->
                            Text(
                                text = line,
                                color = Color(0xFFE6EDF3),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
