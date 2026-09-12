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
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.host == "api.github.com") {
                    response.header("github-authentication-token-expiration")
                        ?.let { raw -> parseGithubExpiry(raw) }
                        ?.let { epoch -> tokenManager.updateTokenExpiry(epoch) }

                    if (response.code == 401) {
                        val activeToken = tokenManager.getToken()
                        val requestAuth = request.header("Authorization")
                        val isActiveTokenRequest =
                            !activeToken.isNullOrBlank() && requestAuth == "Bearer $activeToken"
                        if (isActiveTokenRequest) {
                            sessionManager.onTokenExpired()
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
