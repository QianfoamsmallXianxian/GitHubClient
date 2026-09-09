package com.githubclient.app.ui.screens.repo

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.model.RepoContent
import com.githubclient.app.data.model.Repository
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenPulls: () -> Unit,
    onOpenReleases: () -> Unit,
    onDeleted: () -> Unit = {},
    viewModel: RepoViewModel = hiltViewModel()
) {
    val repo by viewModel.repo.collectAsState()
    val contents by viewModel.contents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    var currentPath by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<RepoContent?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, name) { viewModel.loadRepo(owner, name) }
    LaunchedEffect(owner, name, currentPath) { viewModel.loadContents(owner, name, currentPath) }

    LaunchedEffect(message) {
        if (message == "仓库已删除") {
            onDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentPath.isBlank()) "$owner/$name" else currentPath, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath.isBlank()) onBack() else currentPath = currentPath.substringBeforeLast('/', "")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Button(onClick = onOpenActions, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("Actions")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除仓库", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            message?.let {
                item(key = "message") {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            repo?.let { item(key = "header") { RepoHeader(it, onOpenIssues, onOpenPulls, onOpenReleases) } }
            item(key = "files_header") { Text("文件", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
            if (isLoading && contents.isEmpty()) {
                item(key = "loading") { LoadingState() }
            } else if (contents.isEmpty()) {
                item(key = "empty") { EmptyState("仓库为空或无法访问") }
            } else {
                items(contents, key = { it.sha }) { item ->
                    ContentRow(
                        item = item,
                        onClick = {
                            if (item.type == "dir") {
                                currentPath = if (currentPath.isBlank()) item.name else "$currentPath/${item.name}"
                            } else {
                                selectedFile = item
                            }
                        }
                    )
                }
            }
        }
    }

    selectedFile?.let { file ->
        FileContentDialog(owner, name, file, { selectedFile = null }, viewModel)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除仓库") },
            text = { Text("确定要删除 $owner/$name 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRepository(owner, name)
                    showDeleteDialog = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RepoHeader(
    repo: Repository,
    onOpenIssues: () -> Unit,
    onOpenPulls: () -> Unit,
    onOpenReleases: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(repo.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            repo.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(Icons.Default.Star, repo.stars.toString())
                StatChip(Icons.Default.BugReport, repo.openIssues.toString())
                StatChip(Icons.Default.CallSplit, repo.forks.toString())
                repo.language?.let { StatChip(Icons.Default.Code, it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenIssues) { Icon(Icons.Default.BugReport, contentDescription = null); Text("Issues") }
                Button(onClick = onOpenPulls) { Icon(Icons.Default.CallSplit, contentDescription = null); Text("PRs") }
                Button(onClick = onOpenReleases) { Icon(Icons.Default.Description, contentDescription = null); Text("Releases") }
            }
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun ContentRow(item: RepoContent, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val icon = if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile
        Icon(icon, contentDescription = null, tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.type == "file") item.size?.let { Text("${it}B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
