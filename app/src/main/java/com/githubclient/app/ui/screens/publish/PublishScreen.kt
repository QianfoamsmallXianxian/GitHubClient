package com.githubclient.app.ui.screens.publish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.automation.PublishState

private val SuccessGreen = Color(0xFF1F883D)
private val FailRed = Color(0xFFCF222E)
private val InfoBlue = Color(0xFF0969DA)
private val SuccessBg = Color(0xFFDAFBE1)
private val FailBg = Color(0xFFFFEBE9)
private val InfoBg = Color(0xFFDDF4FF)

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

            StatusBanner(state, running)

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

/**
 * 状态条：把 PublishState 的 message 显示出来。
 * 之前 state 只被用来算 running 布尔值，Success/Error 的文案全被丢掉，
 * 导致上传成功或失败在界面上都看不出来。
 */
@Composable
private fun StatusBanner(state: PublishState, running: Boolean) {
    val (bg, fg, text) = when (state) {
        is PublishState.Running -> Triple(InfoBg, InfoBlue, "正在上传，请勿退出页面...")
        is PublishState.Success -> Triple(SuccessBg, SuccessGreen, state.message)
        is PublishState.Error -> Triple(FailBg, FailRed, state.message)
        PublishState.Idle -> return
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
            } else {
                Icon(
                    imageVector = when (state) {
                        is PublishState.Success -> Icons.Filled.CheckCircle
                        is PublishState.Error -> Icons.Filled.Error
                        else -> Icons.Filled.Info
                    },
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
