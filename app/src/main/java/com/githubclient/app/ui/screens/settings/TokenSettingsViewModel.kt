package com.githubclient.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.githubclient.app.data.auth.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class TokenInfo(
    val hasToken: Boolean = false,
    val type: String = "无",
    val masked: String = "未登录"
)

@HiltViewModel
class TokenSettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _tokenInfo = MutableStateFlow(loadInfo())
    val tokenInfo: StateFlow<TokenInfo> = _tokenInfo

    fun refresh() {
        _tokenInfo.value = loadInfo()
    }

    fun logout() {
        tokenManager.logout()
        refresh()
    }

    private fun loadInfo(): TokenInfo {
        return TokenInfo(
            hasToken = tokenManager.hasToken(),
            type = tokenManager.getTokenType(),
            masked = tokenManager.getMaskedToken()
        )
    }
}
