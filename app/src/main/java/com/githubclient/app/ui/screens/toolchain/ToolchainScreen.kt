package com.githubclient.app.ui.screens.toolchain

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.local.ToolchainItemEntity
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolchainScreen(
    onBack: () -> Unit,
    viewModel: ToolchainViewModel = hiltViewModel()
) {
    val tools by viewModel.tools.collectAsState(initial = emptyList())
    val isDetecting by viewModel.isDetecting.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具链") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    Button(onClick = { viewModel.detect() }, enabled = !isDetecting, modifier = Modifier.padding(end = 8.dp)) {
                        Text("检测")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isDetecting) LoadingState()
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (tools.isEmpty()) {
                EmptyState("点击右上角检测")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tools, key = { it.toolName }) { tool ->
                        ToolCard(tool) { viewModel.download(tool.toolName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolchainItemEntity, onDownload: () -> Unit) {
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
                Text(tool.path ?: "未安装", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            when {
                tool.status == "INSTALLED" -> Icon(Icons.Default.CheckCircle, contentDescription = "已安装", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                tool.status == "DOWNLOADING" -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                tool.status == "FAILED" -> Icon(Icons.Default.Warning, contentDescription = "失败", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                else -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "下载", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
