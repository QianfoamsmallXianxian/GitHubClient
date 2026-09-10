package com.githubclient.app.di

import com.githubclient.app.data.auth.TokenManager
import com.githubclient.app.data.remote.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            // GitHub 的 Actions 日志接口会 302 到对象存储，需要跟随重定向
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host

                // 只给 GitHub API 注入认证头。
                // 之前对所有请求无条件注入，会污染用户配置的第三方 AI 服务：
                // 那些请求自己已经带了 Authorization，加上这里再 addHeader 会变成两个
                // Authorization 头，服务端取第一个（GitHub token）后必然 401。
                val isGitHubApi = host == "api.github.com"
                val token = tokenManager.getToken()

                val newRequest = if (isGitHubApi && !token.isNullOrBlank()) {
                    request.newBuilder()
                        // 用 header() 是替换，避免与调用方已设置的头重复
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .addInterceptor(logging)
            .build()
    }

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
