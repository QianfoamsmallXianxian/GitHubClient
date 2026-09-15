package com.githubclient.app.di

import com.githubclient.app.data.auth.SessionManager
import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.remote.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Singleton
import com.githubclient.app.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    /**
     * 上一次已经触发过登出通知的 token。
     *
     * 用途：OkHttp 拦截器跑在 IO 线程上，并发的多个 401 可能同时进入，
     * 用它保证「同一个失效 token 只触发一次 onTokenExpired」。
     *
     * 重要：必须在收到成功响应时复位为 null。
     * 否则该 token 被用户重新登录后又失效时，去重判断会永远跳过，
     * 表现为「token 已失效但 App 仍以为登录中」。
     */
    private val lastNotifiedToken = AtomicReference<String?>(null)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        sessionManager: SessionManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // 上传大仓库时 GitHub 偶发响应慢，60s 容易在批量中途超时。
            // 放宽到 120s，配合上层的超时重试，减少「传到一半断掉」。
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host

                val isGitHubApi = host == "api.github.com"
                val token = tokenManager.getToken()

                val newRequest = if (isGitHubApi && !token.isNullOrBlank()) {
                    request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            // 识别 token 有效期：
            // 1) 读取响应头 github-authentication-token-expiration，记录到期时间；
            // 2) 收到 401 且该请求用的就是「当前活跃 token」时，交给 SessionManager 自动登出。
            //    只在 token 匹配时登出，避免「添加其它账号 / 校验某个 token」误伤当前登录态。
            // 3) 成功响应时清空去重标记，保证同一 token 重新登录后仍能再次触发登出。
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.host == "api.github.com") {
                    response.header("github-authentication-token-expiration")
                        ?.let { raw -> parseGithubExpiry(raw) }
                        ?.let { epoch -> tokenManager.updateTokenExpiry(epoch) }

                    if (response.isSuccessful) {
                        // 当前 token 可用：清空「已通知」标记。
                        // 少了这一步，同一 token 重新登录后再次失效就不会再登出。
                        lastNotifiedToken.set(null)
                    } else if (response.code == 401) {
                        val activeToken = tokenManager.getToken()
                        val requestAuth = request.header("Authorization")
                        val isActiveTokenRequest =
                            !activeToken.isNullOrBlank() && requestAuth == "Bearer $activeToken"
                        if (isActiveTokenRequest) {
                            // 只有「上次通知的不是这个 token」时才触发，
                            // 用 compareAndSet 保证并发 401 下只有一个线程真正登出。
                            if (lastNotifiedToken.compareAndSet(null, activeToken)) {
                                sessionManager.onTokenExpired()
                            }
                        }
                    }
                }
                response
            }
            .addInterceptor(logging)
            .build()
    }

    /** 解析形如 `2026-09-19 03:23:14 UTC` 的到期时间。 */
    private fun parseGithubExpiry(raw: String): Long? = runCatching {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        format.parse(raw.trim())?.time
    }.getOrNull()

    @Provides
    @Singleton
    fun provideGitHubApi(client: OkHttpClient): GitHubApi {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubApi::class.java)
    }
}
