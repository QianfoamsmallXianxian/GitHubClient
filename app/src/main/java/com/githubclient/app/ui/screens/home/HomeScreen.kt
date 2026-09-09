package com.githubclient.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.local.RepoCacheEntity
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRepo: (String, String) -> Unit,
    onOpenToolchain: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAutomation: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenPrompt: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val repos by viewModel.repos.collectAsState(initial = emptyList())
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("仓库") },
                actions = {
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "账号")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSearch,
                    icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                    label = { Text("搜索") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenTerminal,
                    icon = { Icon(Icons.Default.Terminal, contentDescription = "终端") },
                    label = { Text("终端") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenAutomation,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "自动化") },
                    label = { Text("自动化") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenPrompt,
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = "提示词") },
                    label = { Text("提示词") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenToolchain,
                    icon = { Icon(Icons.Default.Build, contentDescription = "工具链") },
                    label = { Text("工具链") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && repos.isEmpty() -> LoadingState()
                repos.isEmpty() -> EmptyState("暂无仓库，下拉刷新或检查登录")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(repo) { onOpenRepo(repo.ownerLogin, repo.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(repo: RepoCacheEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            repo.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = repo.stars.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                repo.language?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
