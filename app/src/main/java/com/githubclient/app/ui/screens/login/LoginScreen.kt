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
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GitHub Client",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录以访问你的仓库与 Actions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                runCatching {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow,delete_repo&description=GitHubClient")
                    )
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Text("一键获取 GitHub Token")
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "在打开的网页中生成 Token，复制后粘贴到下方",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pat,
            onValueChange = { pat = it },
            label = { Text("Personal Access Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { viewModel.loginWithPat(pat) },
            enabled = pat.isNotBlank() && loginState !is LoginState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("使用令牌登录")
            }
        }

        if (loginState is LoginState.Error) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = (loginState as LoginState.Error).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
