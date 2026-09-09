package com.githubclient.app.ui.screens.actions

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onOpenRun: (Long) -> Unit,
    onOpenDispatch: () -> Unit,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val runs by viewModel.runs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(owner, name) { viewModel.load(owner, name) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Actions") },
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
        ) {
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                isLoading && runs.isEmpty() -> LoadingState()
                runs.isEmpty() -> EmptyState("暂无工作流运行")
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(runs, key = { it.id }) { run ->
                        RunCard(
                            run = run,
                            onOpen = { onOpenRun(run.id) },
                            onCancel = { viewModel.cancelRun(owner, name, run.id) },
                            onRerun = { viewModel.rerunRun(owner, name, run.id) },
                            onDelete = { viewModel.deleteRun(owner, name, run.id) }
                        )
                    }
                }
            }

            Button(
                onClick = onOpenDispatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("手动触发工作流")
            }
        }
    }
}

@Composable
private fun RunCard(
    run: WorkflowRun,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        run.displayTitle ?: run.name ?: "Run #${run.id}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${run.status} ${run.conclusion ?: ""}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    run.headBranch?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                StatusIcon(run)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (run.status == "in_progress" || run.status == "queued") {
                    TextButton(onClick = onCancel) { Text("取消") }
                }
                if (run.status == "completed" && run.conclusion == "failure") {
                    TextButton(onClick = onRerun) { Text("重跑") }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(run: WorkflowRun) {
    when {
        run.status == "completed" && run.conclusion == "success" -> Icon(
            Icons.Default.CheckCircle,
            contentDescription = "成功",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        run.status == "completed" && run.conclusion == "failure" -> Icon(
            Icons.Default.Error,
            contentDescription = "失败",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp)
        )
        run.status == "in_progress" || run.status == "queued" -> Icon(
            Icons.Default.PlayArrow,
            contentDescription = "进行中",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        else -> Icon(
            Icons.Default.Schedule,
            contentDescription = "等待",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}
