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
import androidx.compose.material.icons.automirrored.filled.Description
import androidx.compose.material.icons.automirrored.filled.Folder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Issue
import androidx.compose.material.icons.filled.Star
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
import com.githubclient.app.ui.components.SectionHeader

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
    viewModel: RepoViewModel = hiltViewModel()
) {
    val repo by viewModel.repo.collectAsState()
    val contents by viewModel.contents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var currentPath by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<RepoContent?>(null) }

    LaunchedEffect(owner, name) { viewModel.loadRepo(owner, name) }
    LaunchedEffect(owner, name, currentPath) { viewModel.loadContents(owner, name, currentPath) }

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
                    Button(onClick = onOpenActions, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Actions")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            repo?.let { RepoHeader(it, onOpenIssues, onOpenPulls, onOpenReleases) }
            when {
                isLoading && contents.isEmpty() -> LoadingState()
                contents.isEmpty() -> EmptyState("仓库为空或无法访问")
                else -> {
                    SectionHeader("文件")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
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
        }
    }

    selectedFile?.let { file ->
        FileContentDialog(owner, name, file, { selectedFile = null }, viewModel)
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
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(repo.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            repo.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(Icons.Default.Star, repo.stars.toString())
                StatChip(Icons.Default.Issue, repo.openIssues.toString())
                repo.language?.let { StatChip(Icons.Default.Code, it) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

@Composable
private fun ContentRow(item: RepoContent, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val icon = if (item.type == "dir") Icons.AutoMirrored.Filled.Folder else Icons.AutoMirrored.Filled.Description
        Icon(icon, contentDescription = null, tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.type == "file") item.size?.let { Text("${it}B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
