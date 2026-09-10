package com.githubclient.app.ui.screens.account

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubclient.app.data.auth.GitHubAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenCreateRepo: () -> Unit,
    onOpenRepositories: () -> Unit,
    onOpenTokenSettings: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val activeLogin by viewModel.activeLogin.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showSwitchDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
                // 右上角的 "+" 图标已按要求移除
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(accounts, key = { it.login }) { account ->
                AccountCard(
                    account = account,
                    isActive = account.login == activeLogin,
                    onSwitch = { viewModel.switchAccount(account.login) },
                    onDelete = { viewModel.deleteAccount(account.login) }
                )
            }
            item(key = "actions") {
                Spacer(Modifier.height(8.dp))
                // 切换账号入口，位于"添加账号"上方
                Button(
                    onClick = { showSwitchDialog = true },
                    enabled = accounts.size > 1,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Text("切换账号")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("添加账号")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenCreateRepo, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("创建仓库")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenTokenSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Text("账号信息")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Text("退出登录")
                }
            }
        }
    }

    if (showSwitchDialog) {
        SwitchAccountDialog(
            accounts = accounts,
            activeLogin = activeLogin,
            onSwitch = { login ->
                viewModel.switchAccount(login)
                showSwitchDialog = false
            },
            onDismiss = { showSwitchDialog = false }
        )
    }

    if (showAddDialog) {
        AddAccountDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { token, login ->
                viewModel.addAccount(token, login)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun SwitchAccountDialog(
    accounts: List<GitHubAccount>,
    activeLogin: String?,
    onSwitch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换账号") },
        text = {
            if (accounts.isEmpty()) {
                Text("暂无已保存账号，请先添加账号。")
            } else {
                Column {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = acc.login != activeLogin) { onSwitch(acc.login) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (acc.avatarUrl.isNullOrBlank()) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                AsyncImage(
                                    model = acc.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.nickname ?: acc.login, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${acc.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (acc.login == activeLogin) {
                                Icon(Icons.Default.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun AccountCard(
    account: GitHubAccount,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isActive, onClick = onSwitch),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account.avatarUrl.isNullOrBlank()) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(account.nickname ?: account.login, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isActive) "当前账号" else "点击切换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除账号", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var token by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加账号") },
        text = {
            Column {
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text("GitHub Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text("用户名(可选)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(token.trim(), login.trim()) }, enabled = token.isNotBlank()) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
