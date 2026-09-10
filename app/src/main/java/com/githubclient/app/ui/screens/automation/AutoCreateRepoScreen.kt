package com.githubclient.app.ui.screens.automation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.githubclient.app.ui.theme.Dimens

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

    val state by viewModel.state.collectAsState()
    val previewText by viewModel.preview.collectAsState()
    val isPreviewing by viewModel.isPreviewing.collectAsState()
    val context = LocalContext.current

    fun refreshPermission() {
        hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    // 从系统「所有文件访问权限」页面返回时会触发 ON_RESUME，自动重新检测，
    // 不用再手动点「我已授权，重新检测」。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshPermission()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            // 统一间距：字段之间固定 12dp，不再逐处插 Spacer
            verticalArrangement = Arrangement.spacedBy(Dimens.Md)
        ) {
            if (!hasStoragePermission) {
                Text(
                    "需要“所有文件访问权限”才能读取本地项目目录",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { requestStoragePermission() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("授予所有文件访问权限") }
                OutlinedButton(
                    onClick = { refreshPermission() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("我已授权，重新检测") }
            }

            OutlinedTextField(
                value = repoName,
                onValueChange = { repoName = it },
                label = { Text("仓库名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("描述") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = localPath,
                onValueChange = {
                    localPath = it
                    viewModel.clearPreview()
                },
                label = { Text("本地项目目录 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { viewModel.previewScan(localPath) },
                enabled = !isPreviewing,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isPreviewing) "检测中..." else "先检测目录") }

            previewText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("私有仓库", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }

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
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is AutoRepoState.Success -> {
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
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is AutoRepoState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
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
