package com.githubclient.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubclient.app.data.auth.GitHubAccount
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenSettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TokenSettingsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val activeLogin by viewModel.activeLogin.collectAsState()
    val message by viewModel.message.collectAsState()
    var showTokenDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<GitHubAccount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, contentDescription = "刷新") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("账号列表", style = MaterialTheme.typography.titleMedium)
            }

            items(accounts, key = { it.login }) { account ->
                AccountInfoCard(
                    account = account,
                    isActive = account.login == activeLogin,
                    onAvatarClick = {
                        selectedAccount = account
                        showEditDialog = true
                    },
                    onEdit = {
                        selectedAccount = account
                        showEditDialog = true
                    }
                )
            }


            item {
                Button(
                    onClick = { showTokenDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Text("手动粘贴 Token")
                }
            }

            message?.let {
                item {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Text(
                    "Token 获取方法：打开 GitHub 设置里的 Developer settings，进入 Personal access tokens，勾选 repo 和 workflow 权限后生成，复制粘贴到上方按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showTokenDialog) {
        TokenInputDialog(
            onDismiss = { showTokenDialog = false },
            onSave = { token ->
                viewModel.updateToken(token)
                showTokenDialog = false
            }
        )
    }

    if (showEditDialog && selectedAccount != null) {
        EditAccountDialog(
            account = selectedAccount!!,
            onDismiss = { showEditDialog = false },
            onSave = { nickname, avatarPath ->
                viewModel.updateAccount(selectedAccount!!.login, nickname, avatarPath)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun AccountInfoCard(
    account: GitHubAccount,
    isActive: Boolean,
    onAvatarClick: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.avatarUrl.isNullOrBlank()) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onAvatarClick),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onAvatarClick)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    account.nickname ?: account.login,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "@${account.login}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isActive) {
                    Text(
                        "当前账号",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TokenInputDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新 Token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "粘贴新 token 后保存，旧 token 会被替换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(token.trim()) }, enabled = token.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditAccountDialog(
    account: GitHubAccount,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var nickname by remember { mutableStateOf(account.nickname ?: "") }
    var avatarPath by remember { mutableStateOf(account.avatarUrl) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val input = context.contentResolver.openInputStream(uri)
                if (input != null) {
                    val dir = File(context.filesDir, "avatars")
                    dir.mkdirs()
                    val file = File(dir, "${account.login}.jpg")
                    file.outputStream().use { output -> input.copyTo(output) }
                    input.close()
                    avatarPath = file.absolutePath
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "登录名：${account.login}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { avatarPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (avatarPath == null) "选择自定义头像" else "已选择头像（重新选择可更换）")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nickname.trim(), avatarPath) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
