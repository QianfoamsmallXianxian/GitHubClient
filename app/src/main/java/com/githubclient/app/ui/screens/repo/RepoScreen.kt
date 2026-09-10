package com.githubclient.app.ui.screens.repo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RepoScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onOpenActions: () -> Unit,
    onOpenIssues: () -> Unit,
    onOpenPulls: () -> Unit,
    onOpenReleases: () -> Unit,
    viewModel: RepoViewModel = hiltViewModel()
) {
    val repo by viewModel.repo.collectAsState()
    val contents by viewModel.contents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deleteMessage by viewModel.deleteState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var currentPath by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<RepoContent?>(null) }

    // 多选/批量删除状态
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<RepoContent?>(null) }
    var confirmBatch by remember { mutableStateOf(false) }

    LaunchedEffect(owner, name) { viewModel.loadRepo(owner, name) }
    LaunchedEffect(owner, name, currentPath) {
        viewModel.loadContents(owner, name, currentPath)
        selectionMode = false
        selectedPaths = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "已选 ${selectedPaths.size} 项"
                        else if (currentPath.isBlank()) "$owner/$name" else currentPath,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false; selectedPaths = emptySet()
                        } else if (currentPath.isBlank()) onBack()
                        else currentPath = currentPath.substringBeforeLast('/', "")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = { confirmBatch = true },
                            enabled = selectedPaths.isNotEmpty() && !isDeleting
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "删除所选", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Button(onClick = onOpenActions, modifier = Modifier.padding(end = 8.dp)) { Text("Actions") }
                        Button(onClick = { viewModel.deleteRepo(owner, name) { onBack() } }) { Text("删除") }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            repo?.let { RepoHeader(it, onOpenIssues, onOpenPulls, onOpenReleases) }
            deleteMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            when {
                isLoading && contents.isEmpty() -> LoadingState()
                contents.isEmpty() -> EmptyState("仓库为空或无法访问")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(contents, key = { it.sha.ifBlank { it.path } }) { item ->
                        val checked = selectedPaths.contains(item.path)
                        ContentRow(
                            item = item,
                            selectionMode = selectionMode,
                            checked = checked,
                            onClick = {
                                if (selectionMode) {
                                    selectedPaths = if (checked) selectedPaths - item.path else selectedPaths + item.path
                                } else if (item.type == "dir") {
                                    currentPath = if (currentPath.isBlank()) item.name else "$currentPath/${item.name}"
                                } else {
                                    selectedFile = item
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedPaths = selectedPaths + item.path
                            },
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }
        }
    }

    // 单文件删除确认
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除") },
            text = { Text("确定要删除「${item.name}」吗？目录将递归删除其中全部内容。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSingleFile(owner, name, item)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    // 批量删除确认
    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            title = { Text("批量删除") },
            text = { Text("确定要删除所选的 ${selectedPaths.size} 个文件/文件夹吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val items = contents.filter { selectedPaths.contains(it.path) }
                    viewModel.deleteItems(owner, name, items)
                    confirmBatch = false
                    selectionMode = false
                    selectedPaths = emptySet()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmBatch = false }) { Text("取消") } }
        )
    }

    selectedFile?.let { file ->
        FileContentDialog(
            owner = owner,
            name = name,
            file = file,
            onDismiss = { selectedFile = null },
            onDelete = {
                viewModel.deleteSingleFile(owner, name, file)
                selectedFile = null
            },
            viewModel = viewModel
        )
    }
}

@Composable
private fun RepoHeader(repo: Repository, onOpenIssues: () -> Unit, onOpenPulls: () -> Unit, onOpenReleases: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(repo.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            repo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StatChip(Icons.Default.Star, repo.stars.toString())
                StatChip(Icons.Default.BugReport, repo.openIssues.toString())
                repo.language?.let { StatChip(Icons.Default.Code, it) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenIssues) { Text("Issues") }
                Button(onClick = onOpenPulls) { Text("PRs") }
                Button(onClick = onOpenReleases) { Text("Releases") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentRow(
    item: RepoContent,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) {
            Icon(
                imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val icon = if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile
        Icon(icon, contentDescription = null, tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.type == "file") item.size?.let { Text("${it}B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!selectionMode) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
