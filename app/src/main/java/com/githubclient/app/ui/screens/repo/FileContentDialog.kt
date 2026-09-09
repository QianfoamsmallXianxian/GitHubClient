package com.githubclient.app.ui.screens.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import com.githubclient.app.data.model.RepoContent
import com.githubclient.app.ui.components.LoadingState

@Composable
fun FileContentDialog(
    owner: String,
    name: String,
    file: RepoContent,
    onDismiss: () -> Unit,
    viewModel: RepoViewModel
) {
    val fileContent by viewModel.fileContent.collectAsState()
    val isFileLoading by viewModel.isFileLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    var isEditing by remember { mutableStateOf(false) }
    var editableText by remember { mutableStateOf("") }

    LaunchedEffect(owner, name, file.path) {
        viewModel.loadFileContent(owner, name, file.path)
    }

    LaunchedEffect(fileContent) {
        if (!isEditing && fileContent != null) {
            editableText = fileContent ?: ""
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().height(560.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    if (!isEditing) {
                        IconButton(onClick = {
                            editableText = fileContent ?: ""
                            isEditing = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (isFileLoading) {
                    LoadingState()
                } else if (isEditing) {
                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveFileContent(
                                    owner = owner,
                                    name = name,
                                    path = file.path,
                                    content = editableText,
                                    sha = file.sha
                                )
                                isEditing = false
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Text("保存")
                        }
                    }
                } else {
                    Text(
                        text = fileContent ?: "无法加载文件内容",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
