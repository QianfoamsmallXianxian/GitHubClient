package com.githubclient.app.ui.screens.releases

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.model.Release
import com.githubclient.app.data.model.ReleaseAsset
import com.githubclient.app.ui.components.EmptyState
import com.githubclient.app.ui.components.LoadingState

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Releases") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && releases.isEmpty() -> LoadingState()
                releases.isEmpty() -> EmptyState("暂无 Releases")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        Icon(Icons.Default.OpenInNew, contentDescription = "在浏览器打开", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                release.tagName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (release.prerelease) {
                Text("预发布", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
            release.publishedAt?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (release.assets.isNotEmpty()) {
                Text(
                    "附件 (${release.assets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                release.assets.forEach { asset ->
                    AssetRow(asset, onDownload = { onOpenUrl(asset.downloadUrl) })
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: ReleaseAsset, onDownload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(asset.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatSize(asset.size)} · 下载 ${asset.downloadCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onDownload) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
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
