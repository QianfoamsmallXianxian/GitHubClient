package com.githubclient.app.ui.screens.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.data.model.Artifact
import com.githubclient.app.data.model.WorkflowJob
import com.githubclient.app.data.model.WorkflowRun
import com.githubclient.app.ui.components.LoadingState
import java.time.Duration
import java.time.Instant

private val SuccessGreen = Color(0xFF1F883D)
private val WarnYellow  = Color(0xFFD4A72C)
private val FailRed     = Color(0xFFCF222E)
private val MutedGray   = Color(0xFF8B949E)
private val CardBg      = Color(0xFFF6F8FA)
private val BorderLine  = Color(0xFFE1E4E8)
private val LogBg       = Color(0xFF0D1117)
private val LogFg       = Color(0xFFC9D1D9)

private val ErrorPatterns = listOf(
    "##[error]", "BUILD FAILED", "FAILURE", "Caused by", "Exception",
    "error:", "Error:", "error ", "Error ", "failed", "Failed", "FAILED"
)

private fun isErrorLine(line: String): Boolean = ErrorPatterns.any { line.contains(it) }

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
    val progress by viewModel.progress.collectAsState()
    val run by viewModel.runDetail.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val artifacts by viewModel.artifacts.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    LaunchedEffect(owner, name, runId) { viewModel.load(owner, name, runId) }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Text(
                        run?.displayTitle ?: run?.name ?: "Run #$runId",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.load(owner, name, runId) },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
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
        ) {
            run?.let { r ->
                SectionSpacer()
                RunHeader(r)
                SectionSpacer()
                SummaryCard(r)
                Spacer(Modifier.height(12.dp))
                WorkflowRow(r)
                SectionSpacer()
                JobListSection(jobs)
            } ?: run {
                if (isLoading) {
                    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
                        LoadingState()
                    }
                }
            }

            progress?.let { p ->
                SectionSpacer()
                ProgressCard(p)
            }

            artifacts.takeIf { it.isNotEmpty() }?.let { list ->
                SectionSpacer()
                ArtifactsSection(
                    artifacts = list,
                    downloadState = downloadState,
                    onDownload = { a -> viewModel.downloadArtifact(owner, name, a) },
                    onInstall = { path -> viewModel.installApk(path) }
                )
            }

            if (run?.conclusion == "failure") {
                SectionSpacer()
                LogSection(log = log)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionSpacer() {
    HorizontalDivider(thickness = 8.dp, color = Color(0xFFF0F3F6))
}

@Composable
private fun RunHeader(run: WorkflowRun) {
    val state = conclusionState(run.status, run.conclusion)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = when (state) {
                "success" -> SuccessGreen.copy(alpha = 0.12f)
                "failure" -> FailRed.copy(alpha = 0.12f)
                else -> WarnYellow.copy(alpha = 0.15f)
            },
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when (state) {
                        "success" -> Icons.Filled.CheckCircle
                        "failure" -> Icons.Filled.Error
                        else -> Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = when (state) {
                        "success" -> SuccessGreen
                        "failure" -> FailRed
                        else -> WarnYellow
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                run.displayTitle ?: run.name ?: "Workflow run",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            run.runNumber?.let {
                Text(
                    "#" + it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedGray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(run: WorkflowRun) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, BorderLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(
                buildString {
                    run.event?.let { append("Triggered via ").append(it) }
                    run.createdAt?.let {
                        if (isNotEmpty()) append(" · ")
                        append(relativeTime(it))
                    }
                }.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = MutedGray
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                run.actor?.let { u ->
                    Text(u.login, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                }
                run.headSha?.take(7)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = MutedGray
                    )
                    Spacer(Modifier.width(8.dp))
                }
                run.headBranch?.let { branch ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFDDF4FF))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            branch,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0969DA),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = BorderLine)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatColumn("Status", statusLabel(run.status, run.conclusion))
                StatColumn("Total duration", runDuration(run))
                StatColumn("Attempt", run.runAttempt?.toString() ?: "1")
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MutedGray)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkflowRow(run: WorkflowRun) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEAEEF2)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Y",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF57606A)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                (run.name ?: "workflow") + ".yml",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            run.event?.let {
                Text("on: " + it, style = MaterialTheme.typography.bodySmall, color = MutedGray)
            }
        }
    }
}

@Composable
private fun JobListSection(jobs: List<WorkflowJob>) {
    if (jobs.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        jobs.forEach { JobRow(it) }
    }
}

@Composable
private fun JobRow(job: WorkflowJob) {
    val state = conclusionState(job.status, job.conclusion)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (state) {
                "success" -> Icons.Filled.CheckCircle
                "failure" -> Icons.Filled.Error
                else -> Icons.Filled.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when (state) {
                "success" -> SuccessGreen
                "failure" -> FailRed
                else -> WarnYellow
            },
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            job.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            duration(job.startedAt, job.completedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MutedGray
        )
    }
}

@Composable
private fun ProgressCard(p: BuildProgress) {
    val done = p.statusText.contains("成功")
    val failed = p.statusText.contains("失败")
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    done -> Icons.Filled.CheckCircle
                    failed -> Icons.Filled.Error
                    else -> Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = when {
                    done -> SuccessGreen
                    failed -> FailRed
                    else -> WarnYellow
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                p.percent.toString() + "%  " + p.statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { p.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = if (done) SuccessGreen else MaterialTheme.colorScheme.primary,
            trackColor = Color(0xFFEAEEF2)
        )
        Spacer(Modifier.height(8.dp))
        Text(p.etaText, style = MaterialTheme.typography.bodySmall, color = MutedGray)
    }
}

@Composable
private fun ArtifactsSection(
    artifacts: List<Artifact>,
    downloadState: DownloadState?,
    onDownload: (Artifact) -> Unit,
    onInstall: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text("Artifacts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        artifacts.forEach { a ->
            val st = downloadState?.takeIf { it.artifactName == a.name }
            val readyApk = st?.apkPath?.takeIf { st.status == "done" && it.isNotBlank() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .then(
                        if (readyApk != null) Modifier.clickable { onInstall(readyApk) }
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Inventory2,
                    contentDescription = null,
                    tint = Color(0xFF57606A),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(humanSize(a.sizeInBytes))
                            when {
                                st == null -> {}
                                st.status == "downloading" -> append(if (st.progress < 0) " · 下载中" else " · 下载中 " + st.progress + "%")
                                st.status == "done" -> append(" · 已下载，点击安装")
                                else -> append(" · " + st.status)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            st?.status?.startsWith("error") == true -> FailRed
                            st?.status == "done" -> SuccessGreen
                            else -> MutedGray
                        }
                    )
                }
                IconButton(
                    onClick = { onDownload(a) },
                    enabled = st?.status != "downloading"
                ) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "下载",
                        tint = when {
                            st?.status == "done" -> SuccessGreen
                            st?.status?.startsWith("error") == true -> FailRed
                            else -> Color(0xFF0969DA)
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 仅构建失败时展示；默认只列错误行，便于精准定位 */
@Composable
private fun LogSection(log: String?) {
    val clipboard = LocalClipboardManager.current
    val errorLines = remember(log) { extractErrorLines(log) }
    var copied by remember { mutableStateOf(false) }

    val textToShow = if (errorLines.isEmpty()) {
        log?.takeIf { it.isNotBlank() } ?: "暂无日志"
    } else {
        errorLines.joinToString("\n") { "L" + it.first + ": " + it.second }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "错误日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 4.dp)
            )
            AssistChip(
                onClick = {
                    clipboard.setText(AnnotatedString(textToShow))
                    copied = true
                },
                label = { Text(if (copied) "已复制" else "复制", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = if (copied) SuccessGreen else MaterialTheme.colorScheme.onSurface
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = LogBg,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = textToShow,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                ),
                color = LogFg
            )
        }
    }
}

private fun extractErrorLines(log: String?): List<Pair<Int, String>> {
    if (log.isNullOrBlank()) return emptyList()
    val lines = log.split('\n')
    val result = mutableListOf<Pair<Int, String>>()
    var lastAdded = -10
    lines.forEachIndexed { idx, raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) return@forEachIndexed
        if (isErrorLine(line)) {
            if (idx - lastAdded > 1 && result.isNotEmpty()) {
                result.add(idx to "...")
            }
            result.add((idx + 1) to line)
            lastAdded = idx
        }
    }
    return result
}

private fun conclusionState(status: String, conclusion: String?): String = when {
    status == "completed" && conclusion == "success" -> "success"
    status == "completed" && conclusion == "failure" -> "failure"
    else -> "in_progress"
}

private fun statusLabel(status: String, conclusion: String?): String = when {
    status == "completed" && conclusion == "success" -> "Success"
    status == "completed" && conclusion == "failure" -> "Failure"
    status == "completed" -> conclusion?.replaceFirstChar { it.uppercase() } ?: "Completed"
    status == "queued" -> "Queued"
    status == "in_progress" -> "In progress"
    else -> status
}

private fun runDuration(run: WorkflowRun): String {
    val start = run.runStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: run.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return "—"
    val end = run.updatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "—"
    return fmtDuration(Duration.between(start, end).seconds.coerceAtLeast(0))
}

private fun duration(startIso: String?, endIso: String?): String {
    val s = startIso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "—"
    val e = endIso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return "进行中"
    return fmtDuration(Duration.between(s, e).seconds.coerceAtLeast(0))
}

private fun fmtDuration(sec: Long): String = when {
    sec < 60 -> sec.toString() + "s"
    sec < 3600 -> (sec / 60).toString() + "m " + (sec % 60).toString() + "s"
    else -> (sec / 3600).toString() + "h " + ((sec % 3600) / 60).toString() + "m"
}

private fun relativeTime(iso: String): String {
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val sec = Duration.between(t, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        sec < 60 -> sec.toString() + " seconds ago"
        sec < 3600 -> (sec / 60).toString() + " minutes ago"
        sec < 86400 -> (sec / 3600).toString() + " hours ago"
        else -> (sec / 86400).toString() + " days ago"
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}
