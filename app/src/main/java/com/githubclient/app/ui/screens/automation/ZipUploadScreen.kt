package com.githubclient.app.ui.screens.automation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
fun ZipUploadScreen(
    onBack: () -> Unit,
    viewModel: ZipUploadViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var autoTrigger by remember { mutableStateOf(true) }
    val state by viewModel.state.collectAsState()

    val zipPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> zipUri = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZIP 源码一键上传") },
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
            OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("仓库名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))

            Button(onClick = { zipPicker.launch("application/zip") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (zipUri == null) "选择 ZIP 源码包" else "已选择: ${zipUri?.lastPathSegment}")
            }
            Spacer(Modifier.height(12.dp))

            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = autoTrigger, onCheckedChange = { autoTrigger = it })
                Text("上传完成后自动触发 Actions 构建", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))

            val isUploading = state is ZipUploadState.Loading || state is ZipUploadState.Progress
            Button(
                onClick = {
                    val uri = zipUri
                    if (uri != null && owner.isNotBlank() && repo.isNotBlank()) {
                        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        if (bytes != null) {
                            viewModel.uploadZip(owner, repo, bytes, autoTrigger)
                        }
                    }
                },
                enabled = owner.isNotBlank() && repo.isNotBlank() && zipUri != null && !isUploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("解压并上传")
                }
            }

            when (val s = state) {
                is ZipUploadState.Progress -> {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { if (s.total == 0) 0f else s.done.toFloat() / s.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${s.done}/${s.total}  ${s.currentFile}", style = MaterialTheme.typography.bodySmall)
                }
                is ZipUploadState.Success -> {
                    Spacer(Modifier.height(12.dp))
                    Text("上传完成: ${s.uploaded} 成功, ${s.failed} 失败", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    s.details.take(10).forEach { detail ->
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is ZipUploadState.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }
        }
    }
}
