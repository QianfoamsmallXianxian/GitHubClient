package com.githubclient.app.ui.screens.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptScreen(
    onBack: () -> Unit,
    viewModel: PromptViewModel = hiltViewModel()
) {
    var text by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var showCommand by remember { mutableStateOf(false) }

    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提示词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.reset() }) { Text("清空") }
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
            verticalArrangement = Arrangement.spacedBy(Dimens.Md)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("输入提示词") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            if (showCommand) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("附加上下文命令（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            } else {
                TextButton(onClick = { showCommand = true }) { Text("+ 附加命令上下文") }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.send(text, command) },
                    enabled = text.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.IconSm),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.IconSm)
                        )
                        // 图标与文字之间补间距，之前直接相邻会粘在一起
                        Spacer(Modifier.width(Dimens.Sm))
                        Text("发送")
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            response?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
                ) {
                    Column(
                        modifier = Modifier.padding(Dimens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.Sm)
                    ) {
                        Text(
                            "回复",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
