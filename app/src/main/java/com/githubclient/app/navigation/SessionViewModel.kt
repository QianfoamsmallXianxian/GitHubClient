package com.githubclient.app.navigation

import androidx.lifecycle.ViewModel
import com.githubclient.app.data.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

/**
 * 只做一件事：把 [SessionManager] 的「token 失效/过期」事件暴露给导航层，
 * AppNavHost 收到后会跳回登录页。
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {

    val sessionExpired: SharedFlow<Unit> = sessionManager.sessionExpired

    /** 冷启动主动校验一次：本地记录的 token 是否已过期。 */
    fun checkExpiredOnStart(): Boolean = sessionManager.checkActiveTokenExpiry()
}
