package com.githubclient.app.ui.screens.actions

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var selectionMode by remember { mutableStateOf(false) }

    val allSelected = runs.isNotEmpty() && selectedIds.containsAll(runs.map { it.id })

    LaunchedEffect(owner, name) { viewModel.load(owner, name) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "已选 ${selectedIds.size}" else "Actions") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            viewModel.clearSelection()
                        } else onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            selectionMode = false
                            viewModel.clearSelection()
                        }) { Icon(Icons.Default.Close, contentDescription = "取消选择") }
                    } else {
                        Button(onClick = onOpenDispatch, modifier = Modifier.padding(end = 8.dp)) { Text("手动触发") }
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.deleteSelected(owner, name) },
                        enabled = selectedIds.isNotEmpty() && !isDeleting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Text(if (isDeleting) "删除中..." else "删除选中 (${selectedIds.size})")
                    }
                    Button(onClick = {
                        if (allSelected) viewModel.clearSelection() else viewModel.selectAll()
                    }) { Text(if (allSelected) "取消全选" else "全选") }
                    Button(onClick = {
                        selectionMode = false
                        viewModel.clearSelection()
                    }) { Text("取消") }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                isLoading && runs.isEmpty() -> LoadingState()
                runs.isEmpty() -> EmptyState("暂无 Workflow Runs")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(runs, key = { it.id }) { run ->
                        RunCard(
                            run = run,
                            selectionMode = selectionMode,
                            selected = selectedIds.contains(run.id),
                            onOpen = { onOpenRun(run.id) },
                            onLongClick = {
                                // 长按进入选择模式，仅选中当前项（不自动全选）
                                selectionMode = true
                                if (!selectedIds.contains(run.id)) viewModel.toggleSelect(run.id)
                            },
                            onToggleSelect = { viewModel.toggleSelect(run.id) },
                            onCancel = { viewModel.cancelRun(owner, name, run.id) },
                            onRerun = { viewModel.rerunRun(owner, name, run.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunCard(
    run: WorkflowRun,
    selectionMode: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                    onLongClick = onLongClick
                )
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                }
                Column(modifier = Modifier.weight(1f).padding(start = if (selectionMode) 8.dp else 0.dp)) {
                    Text(run.displayTitle ?: run.name ?: "Run #${run.id}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${run.status} ${run.conclusion ?: ""}".trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    run.headBranch?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
                StatusIcon(run)
            }
            if (!selectionMode) {
                if (run.status == "in_progress" || run.status == "queued") {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onCancel) { Text("取消") } }
                } else if (run.status == "completed" && run.conclusion == "failure") {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = onRerun) { Text("重跑") } }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(run: WorkflowRun) {
    when {
        run.status == "completed" && run.conclusion == "success" -> Icon(Icons.Default.CheckCircle, contentDescription = "成功", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
        run.status == "completed" && run.conclusion == "failure" -> Icon(Icons.Default.Error, contentDescription = "失败", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        run.status == "in_progress" || run.status == "queued" -> Icon(Icons.Default.PlayArrow, contentDescription = "进行中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        else -> Icon(Icons.Default.Schedule, contentDescription = "等待", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}
