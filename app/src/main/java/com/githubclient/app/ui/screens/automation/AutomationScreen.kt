package com.githubclient.app.ui.screens.automation

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenAutoCreateRepo: () -> Unit,
    onOpenManualCreateRepo: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenZipUpload: () -> Unit,
    onOpenAiModify: () -> Unit
) {
    val features = listOf(
        FeatureItem("一键创建仓库并上传", "扫描本地项目目录，自动创建远程仓库并上传所有源码文件", Icons.Default.Folder, onOpenAutoCreateRepo),
        FeatureItem("ZIP 上传源码", "选择本地 ZIP 压缩包，解压后批量上传文本源码，可自动触发构建", Icons.Default.UploadFile, onOpenZipUpload),
        FeatureItem("创建仓库", "只创建远程仓库，不上传文件", Icons.Default.CreateNewFolder, onOpenManualCreateRepo),
        FeatureItem("上传单个文件", "手动指定路径与内容，上传或更新一个文件", Icons.Default.Upload, onOpenUpload),
        FeatureItem("AI 修改源码", "输入需求自动改代码并提交", Icons.Default.AutoAwesome, onOpenAiModify),
        FeatureItem("AI 服务设置", "配置 AI API 地址与密钥", Icons.Default.Settings, onOpenAiSettings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自动化中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(features) { feature ->
                FeatureCard(feature)
            }
        }
    }
}

private data class FeatureItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun FeatureCard(item: FeatureItem) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
