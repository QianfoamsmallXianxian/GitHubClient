package com.githubclient.app.ui.screens.releases

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubclient.app.data.model.Release
import com.githubclient.app.data.model.ReleaseAsset
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState
import com.githubclient.app.ui.components.ScrollableFill
import com.githubclient.app.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    viewModel: ReleasesViewModel = hiltViewModel()
) {
    val releases by viewModel.releases.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(owner, name) { viewModel.load(owner, name) }

    var isRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) isRefreshing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Releases") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.load(owner, name)
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                isLoading && releases.isEmpty() -> ScrollableFill { LoadingState() }
                releases.isEmpty() -> ScrollableFill { EmptyState("暂无 Releases，下拉可刷新") }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Dimens.ListPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Sm)
                ) {
                    items(releases, key = { it.id }) { release ->
                        ReleaseCard(release, onOpenUrl = { url -> openUrl(context, url) })
                    }
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
private fun ReleaseCard(release: Release, onOpenUrl: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation)
    ) {
        Column(
            modifier = Modifier.padding(Dimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.Sm)
        ) {
            // 标题行：版本名 + 跳转网页
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    release.name ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                release.htmlUrl?.let { url ->
                    IconButton(onClick = { onOpenUrl(url) }) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = "在浏览器打开",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 标签 + 预发布标记
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Sm)
            ) {
                Text(
                    release.tagName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (release.prerelease) {
                    Text(
                        "预发布",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 发布者 + 发布时间（对齐 GitHub 网页版）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Sm)
            ) {
                val avatar = release.author?.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.IconSm).clip(CircleShape)
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.IconSm),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    release.author?.login ?: "未知作者",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                release.publishedAt?.let {
                    Text(
                        "发布于 ${it.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 更新内容
            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                HorizontalDivider(thickness = Dimens.Divider)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 作者上传的附件
            if (release.assets.isNotEmpty()) {
                HorizontalDivider(thickness = Dimens.Divider)
                Text(
                    "附件 (${release.assets.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                release.assets.forEach { asset ->
                    AssetRow(asset, onDownload = { onOpenUrl(asset.downloadUrl) })
                }
            }

            // 源码包下载（对应网页版的 Source code zip / tar.gz）
            if (release.zipballUrl != null || release.tarballUrl != null) {
                HorizontalDivider(thickness = Dimens.Divider)
                Text("源码包", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.Sm)
                ) {
                    release.zipballUrl?.let { url ->
                        OutlinedButton(
                            onClick = { onOpenUrl(url) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.IconSm)
                            )
                            Spacer(Modifier.width(Dimens.Xs))
                            Text("zip")
                        }
                    }
                    release.tarballUrl?.let { url ->
                        OutlinedButton(
                            onClick = { onOpenUrl(url) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.IconSm)
                            )
                            Spacer(Modifier.width(Dimens.Xs))
                            Text("tar.gz")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: ReleaseAsset, onDownload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.IconSm)
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = Dimens.Sm),
            verticalArrangement = Arrangement.spacedBy(Dimens.Xs)
        ) {
            Text(
                asset.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatSize(asset.size)} · 下载 ${asset.downloadCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onDownload) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSm)
            )
            Spacer(Modifier.width(Dimens.Xs))
            Text("下载")
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
