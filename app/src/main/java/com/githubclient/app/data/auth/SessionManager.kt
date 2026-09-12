package com.githubclient.app.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话管理器：负责在「token 失效 / 过期」时统一登出，
 * 并把事件广播给导航层，让 App 自动回到登录页。
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenManager: TokenManager
) {

    // replay = 1：保证即使登出发生在收集开始之前（例如冷启动主动校验），
    // 导航层之后订阅时仍能收到一次事件。
    private val _sessionExpired = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1
    )

    /** token 失效/过期事件。收到后应清理登录态并跳转登录页。 */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    /**
     * 由网络层在收到 401（token 被吊销或已过期）时调用。
     * 幂等：已经没有 token 时不再重复触发。
     */
    fun onTokenExpired() {
        if (!tokenManager.hasToken()) return
        Timber.w("GitHub token 已失效或过期，自动退出登录")
        tokenManager.logout()
        _sessionExpired.tryEmit(Unit)
    }

    /**
     * 冷启动时的主动校验：本地记录的到期时间已过则直接登出。
     * @return true 表示 token 已过期并已触发登出。
     */
    fun checkActiveTokenExpiry(): Boolean {
        if (!tokenManager.isActiveTokenExpired()) return false
        onTokenExpired()
        return true
    }
}
