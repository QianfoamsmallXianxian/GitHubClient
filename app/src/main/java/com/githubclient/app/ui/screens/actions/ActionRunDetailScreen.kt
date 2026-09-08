package com.githubclient.app.ui.screens.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.ui.components.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionRunDetailScreen(
    owner: String,
    name: String,
    runId: Long,
    onBack: () -> Unit,
    viewModel: ActionRunDetailViewModel = hiltViewModel()
) {
    val log by viewModel.log.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(owner, name, runId) { viewModel.load(owner, name, runId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run #$runId 日志") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    Button(
                        onClick = { viewModel.load(owner, name, runId) },
                        enabled = !isLoading,
                        modifier = Modifier.padding(end = 8.dp)
                    ) { Text("刷新") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading && log == null) {
                LoadingState()
            } else {
                Text(
                    text = log ?: "暂无日志",
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
