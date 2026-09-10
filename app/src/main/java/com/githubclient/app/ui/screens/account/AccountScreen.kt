package com.githubclient.app.ui.screens.account

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check

import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubclient.app.data.auth.AccountProfile
import com.githubclient.app.data.model.User
import com.githubclient.app.ui.components.LoadingState

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
    val user by viewModel.user.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val switchMessage by viewModel.switchMessage.collectAsState()
    val context = LocalContext.current

    var showSwitchDialog by remember { mutableStateOf(false) }

    // 顶部导航栏只保留返回与刷新，移除了原来的 "+" 图标
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadUser) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) LoadingState()
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            switchMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            user?.let { u -> UserCard(u) }

            Text("令牌：${viewModel.maskedToken}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("类型：${viewModel.tokenType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 切换账号入口，放在账号列表/常用项上方
            AccountMenuItem(
                title = "切换账号",
                leading = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { viewModel.clearSwitchMessage(); showSwitchDialog = true }
            )

            HorizontalDivider()

            AccountMenuItem("创建新仓库", onClick = onOpenCreateRepo)
            AccountMenuItem("仓库列表", onClick = onOpenRepositories)
            AccountMenuItem("令牌设置", onClick = onOpenTokenSettings)
            AccountMenuItem("星标", onClick = { openUrl(context, "https://github.com/${user?.login ?: ""}?tab=stars") })
            AccountMenuItem("代码片段", onClick = { openUrl(context, "https://gist.github.com/${user?.login ?: ""}") })
            AccountMenuItem("组织", onClick = { openUrl(context, "https://github.com/settings/organizations") })
            AccountMenuItem("赞助", onClick = { openUrl(context, "https://github.com/sponsors") })
            AccountMenuItem("获取令牌", onClick = { openUrl(context, "https://github.com/settings/tokens") })
            AccountMenuItem(
                "退出登录",
                onClick = { viewModel.logout(); onLogout() },
                tint = MaterialTheme.colorScheme.error
            )
        }
    }

    if (showSwitchDialog) {
        SwitchAccountDialog(
            accounts = accounts,
            currentLogin = user?.login,
            onSwitch = { login -> viewModel.switchTo(login); showSwitchDialog = false },
            onRemove = { login -> viewModel.removeAccount(login) },
            onAddNew = { showSwitchDialog = false; onOpenTokenSettings() },
            onDismiss = { showSwitchDialog = false }
        )
    }
}

@Composable
private fun SwitchAccountDialog(
    accounts: List<AccountProfile>,
    currentLogin: String?,
    onSwitch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换账号") },
        text = {
            Column {
                if (accounts.isEmpty()) {
                    Text("暂无已保存账号，请先登录一个账号。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSwitch(acc.login) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!acc.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = acc.avatarUrl,
                                    contentDescription = acc.login,
                                    modifier = Modifier.size(36.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.name ?: acc.login, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${acc.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (acc.login.equals(currentLogin, ignoreCase = true)) {
                                Icon(Icons.Default.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddNew) { Text("添加账号") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}

@Composable
private fun UserCard(u: User) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!u.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = u.avatarUrl,
                    contentDescription = "头像",
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(u.name ?: u.login, style = MaterialTheme.typography.titleLarge)
            Text("@${u.login}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            u.bio?.let { Spacer(Modifier.height(4.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AccountStat("仓库", u.publicRepos?.toString() ?: "-")
                AccountStat("关注者", u.followers?.toString() ?: "-")
                AccountStat("关注中", u.following?.toString() ?: "-")
            }
        }
    }
}

@Composable
private fun AccountStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountMenuItem(
    title: String,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leading?.invoke()
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
