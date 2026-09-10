package com.githubclient.app.ui.screens.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.model.Commit
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
    onOpenReleases: () -> Unit,
    onDeleted: () -> Unit = {},
    viewModel: RepoViewModel = hiltViewModel()
) {
    val repo by viewModel.repo.collectAsState()
    val contents by viewModel.contents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val commits by viewModel.commits.collectAsState()
    val isCommitsLoading by viewModel.isCommitsLoading.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<RepoContent?>(null) }
    var showDeleteRepoDialog by remember { mutableStateOf(false) }
    var showCommitsDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var copyHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(owner, name) { viewModel.loadRepo(owner, name) }
    LaunchedEffect(owner, name, currentPath) { viewModel.loadContents(owner, name, currentPath) }

    LaunchedEffect(copyHint) {
        if (copyHint != null) {
            kotlinx.coroutines.delay(2000)
            copyHint = null
        }
    }

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
                        if (selectionMode) {
                            selectionMode = false
                            viewModel.clearSelection()
                        } else if (currentPath.isBlank()) onBack() else currentPath = currentPath.substringBeforeLast('/', "")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectionMode = false
                            viewModel.clearSelection()
                        }) { Icon(Icons.Default.Close, contentDescription = "取消选择") }
                    } else {
                        Button(onClick = onOpenActions, modifier = Modifier.padding(end = 4.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Actions")
                        }
                        IconButton(onClick = { showDeleteRepoDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除仓库", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.deleteSelected() },
                        enabled = selectedPaths.isNotEmpty() && !isDeleting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(if (isDeleting) "删除中..." else "删除选中 (${selectedPaths.size})")
                    }
                    Button(onClick = {
                        selectionMode = false
                        viewModel.clearSelection()
                    }) { Text("取消") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            copyHint?.let {
                item(key = "copyhint") {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
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
            repo?.let { r ->
                item(key = "header") {
                    RepoHeader(
                        repo = r,
                        onOpenCommits = {
                            viewModel.loadCommits(owner, name)
                            showCommitsDialog = true
                        },
                        onCopyLink = {
                            copyToClipboard(context, "https://github.com/$owner/$name")
                            copyHint = "已复制链接: https://github.com/$owner/$name"
                        },
                        onOpenReleases = onOpenReleases,
                        onToggleSearch = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                searchQuery = ""
                                viewModel.filterContents("")
                            }
                        },
                        onToggleSelection = {
                            selectionMode = !selectionMode
                            viewModel.clearSelection()
                        }
                    )
                }
            }
            if (showSearchBar) {
                item(key = "search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.filterContents(it)
                        },
                        label = { Text("搜索文件") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    )
                }
            }
            item(key = "files_header") { Text("文件", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall) }
            if (isLoading && contents.isEmpty()) {
                item(key = "loading") { LoadingState() }
            } else if (contents.isEmpty()) {
                item(key = "empty") { EmptyState("仓库为空或无法访问") }
            } else {
                items(contents, key = { it.sha }) { item ->
                    ContentRow(
                        item = item,
                        selectionMode = selectionMode,
                        selected = selectedPaths.contains(item.path),
                        onClick = {
                            if (selectionMode) {
                                viewModel.toggleSelect(item)
                            } else if (item.type == "dir") {
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

    if (showDeleteRepoDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteRepoDialog = false },
            title = { Text("删除仓库") },
            text = { Text("确定要删除 $owner/$name 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRepository(owner, name)
                    showDeleteRepoDialog = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteRepoDialog = false }) { Text("取消") }
            }
        )
    }

    if (showCommitsDialog) {
        AlertDialog(
            onDismissRequest = { showCommitsDialog = false },
            title = { Text("提交历史") },
            text = {
                if (isCommitsLoading) {
                    LoadingState()
                } else if (commits.isEmpty()) {
                    Text("暂无提交记录")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        items(commits) { c ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    c.commit?.message?.lineSequence()?.firstOrNull() ?: "(无提交信息)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${c.sha.take(7)}  ${c.commit?.author?.name ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCommitsDialog = false }) { Text("关闭") }
            }
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("repo", text))
    }
}

@Composable
private fun RepoHeader(
    repo: Repository,
    onOpenCommits: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenReleases: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleSelection: () -> Unit
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
                Button(onClick = onOpenCommits) { Icon(Icons.Default.History, contentDescription = null); Text("提交") }
                Button(onClick = onCopyLink) { Icon(Icons.Default.Link, contentDescription = null); Text("链接") }
                Button(onClick = onToggleSearch) { Icon(Icons.Default.Search, contentDescription = null); Text("搜索") }
                Button(onClick = onOpenReleases) { Icon(Icons.Default.Description, contentDescription = null); Text("Releases") }
                Button(onClick = onToggleSelection) { Icon(Icons.Default.DeleteSweep, contentDescription = null); Text("批量删") }
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
private fun ContentRow(
    item: RepoContent,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        val icon = if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile
        Icon(icon, contentDescription = null, tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.type == "file") item.size?.let { Text("${it}B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
