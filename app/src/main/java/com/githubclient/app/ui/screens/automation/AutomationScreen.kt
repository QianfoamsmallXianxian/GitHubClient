package com.githubclient.app.ui.screens.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        FeatureItem(
            "一键创建仓库并上传",
            "扫描本地项目目录，自动创建远程仓库并上传所有源码文件",
            Icons.Default.Folder,
            onOpenAutoCreateRepo,
            "使用方法：\n1. 点击进入后选择本地项目目录\n2. 填写仓库名和描述\n3. 点击开始上传\n系统会自动创建远程仓库并把源码文件全部推送到仓库"
        ),
        FeatureItem(
            "ZIP 源码一键上传",
            "选择 ZIP 自动解压到 GitHub 仓库并触发 Actions 构建",
            Icons.Default.Archive,
            onOpenZipUpload,
            "使用方法：\n1. 输入 Owner（只填用户名，如 QianfoamsmallXianxian）\n2. 输入仓库名\n3. 选择 ZIP 源码包\n4. 勾选自动触发构建\n5. 点击解压并上传\n系统会自动剥离开头的公共目录，把源码上传到正确位置"
        ),
        FeatureItem(
            "AI 修改源码",
            "输入需求自动改代码并提交",
            Icons.Default.AutoAwesome,
            onOpenAiModify,
            "使用方法：\n1. 输入仓库 Owner 和名称\n2. 描述你想改的功能或修复的问题\n3. 系统调用 AI 生成修改方案\n4. 确认后自动写入文件并提交到 GitHub"
        ),
        FeatureItem(
            "AI 服务设置",
            "配置 AI API 地址与密钥",
            Icons.Default.Settings,
            onOpenAiSettings,
            "使用方法：\n1. 输入 AI 服务 API 地址\n2. 输入 API 密钥\n3. 保存后 AI 修改源码功能才能使用"
        )
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
    val onClick: () -> Unit,
    val usage: String
)

@Composable
private fun FeatureCard(item: FeatureItem) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开"
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    item.usage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
