package com.githubclient.app.ui.screens.login

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubclient.app.ui.theme.Dimens

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var pat by remember { mutableStateOf("") }
    val loginState by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GitHub Client",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Dimens.Sm))
        Text(
            text = "登录以访问你的仓库与 Actions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Xl))

        // OAuth 需要 client_secret，本仓库未内置有效密钥，因此改为引导用户
        // 直接在 GitHub 网页生成带 repo/workflow/delete_repo 权限的 Token。
        Button(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow,delete_repo&description=GitHubClient")
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconSm)
            )
            // 图标与文字之间留出间距，之前直接相邻会粘在一起
            Spacer(Modifier.width(Dimens.Sm))
            Text("一键获取 GitHub Token")
        }

        Spacer(Modifier.height(Dimens.Xl))

        Text(
            text = "在打开的网页中生成 Token，复制后粘贴到下方",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.Md))

        OutlinedTextField(
            value = pat,
            onValueChange = { pat = it },
            label = { Text("Personal Access Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(Dimens.Sm))

        Button(
            onClick = { viewModel.loginWithPat(pat) },
            enabled = pat.isNotBlank() && loginState !is LoginState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.IconSm),
                    strokeWidth = 2.dp
                )
            } else {
                Text("使用令牌登录")
            }
        }

        if (loginState is LoginState.Error) {
            Spacer(Modifier.height(Dimens.Lg))
            Text(
                text = (loginState as LoginState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
