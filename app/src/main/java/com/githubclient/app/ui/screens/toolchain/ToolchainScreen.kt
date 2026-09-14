package com.githubclient.app.ui.screens.toolchain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState
import com.githubclient.app.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolchainScreen(
    onBack: () -> Unit,
    viewModel: ToolchainViewModel = hiltViewModel()
) {
    val tools by viewModel.tools.collectAsState(initial = emptyList())
    val isDetecting by viewModel.isDetecting.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 从系统“所有文件访问权限”页返回时，如果已授权就自动补一次检测
    var pendingAfterGrant by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingAfterGrant) {
                pendingAfterGrant = false
                if (PermissionHelper.hasAllFilesAccess()) {
                    viewModel.detect()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具链") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            // 按需申请存储空间权限：没授权就先跳设置页，授权后再检测
                            if (PermissionHelper.hasAllFilesAccess()) {
                                viewModel.detect()
                            } else {
                                pendingAfterGrant = true
                                PermissionHelper.requestAllFilesAccess(context)
                            }
                        },
                        enabled = !isDetecting,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("检测")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            if (isDetecting) LoadingState()
            if (tools.isEmpty()) {
                EmptyState("点击右上角检测")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tools, key = { it.toolName }) { tool ->
                        ToolCard(tool)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: com.githubclient.app.data.local.ToolchainItemEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.toolName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = tool.path ?: "未安装",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            when (tool.status) {
                "INSTALLED" -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已安装",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                else -> Icon(
                    Icons.Default.Warning,
                    contentDescription = "未安装",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
