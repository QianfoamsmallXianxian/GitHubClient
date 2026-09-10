package com.githubclient.app.ui.screens.automation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoCreateRepoScreen(
    onBack: () -> Unit,
    viewModel: AutoCreateRepoViewModel = hiltViewModel()
) {
    var repoName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var localPath by remember { mutableStateOf("/sdcard/Download/GitHubClient") }
    var hasStoragePermission by remember { mutableStateOf(false) }
    var previewText by remember { mutableStateOf<String?>(null) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun refreshPermission() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    LaunchedEffect(Unit) { refreshPermission() }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }.onFailure {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("一键创建仓库并上传") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // 权限提示区
            if (!hasStoragePermission) {
                Text(
                    "需要“所有文件访问权限”才能读取本地项目目录",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        requestStoragePermission()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("授予所有文件访问权限") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { refreshPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("我已授权，重新检测") }
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("仓库名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("描述") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = localPath,
                onValueChange = { localPath = it },
                label = { Text("本地项目目录 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val result = viewModel.previewScan(localPath)
                    previewText = buildString {
                        append("扫描到 ${result.totalFiles} 个可上传文件")
                        append("，共 ${result.totalSize / 1024} KB")
                        if (result.skippedFiles.isNotEmpty()) {
                            append("\n跳过 ${result.skippedFiles.size} 项，例如：")
                            append(result.skippedFiles.take(3).joinToString("、"))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("先检测目录") }

            previewText?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("私有仓库", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }
            Spacer(Modifier.height(16.dp))

            val isRunning = state is AutoRepoState.Running
            Button(
                onClick = { viewModel.start(repoName, description, isPrivate, localPath) },
                enabled = repoName.isNotBlank() && localPath.isNotBlank() && !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRunning) "执行中..." else "创建仓库并上传")
            }

            when (val s = state) {
                is AutoRepoState.Progress -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                is AutoRepoState.Success -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "仓库 ${s.repoFullName} 创建完成，成功上传 ${s.uploadedCount} 个文件" +
                            if (s.failed.isEmpty()) "" else "，失败 ${s.failed.size} 个",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (s.failed.isNotEmpty()) {
                        Text(
                            s.failed.take(5).joinToString("\n"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                is AutoRepoState.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }
        }
    }
}

sealed interface AutoRepoState {
    data object Idle : AutoRepoState
    data object Running : AutoRepoState
    data class Progress(val message: String, val progress: Float) : AutoRepoState
    data class Success(val repoFullName: String, val uploadedCount: Int, val failed: List<String> = emptyList()) : AutoRepoState
    data class Error(val message: String) : AutoRepoState
}
