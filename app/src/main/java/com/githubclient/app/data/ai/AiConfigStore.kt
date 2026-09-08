package com.githubclient.app.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiDataStore by preferencesDataStore(name = "ai_config")

@Singleton
class AiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val baseUrl = stringPreferencesKey("ai_base_url")
        val apiKey = stringPreferencesKey("ai_api_key")
        val model = stringPreferencesKey("ai_model")
    }

    suspend fun saveConfig(baseUrl: String, apiKey: String, model: String) {
        context.aiDataStore.edit { prefs ->
            prefs[Keys.baseUrl] = baseUrl
            prefs[Keys.apiKey] = apiKey
            prefs[Keys.model] = model
        }
    }

    suspend fun getBaseUrl(): String = context.aiDataStore.data.map { it[Keys.baseUrl] ?: "" }.first()
    suspend fun getApiKey(): String = context.aiDataStore.data.map { it[Keys.apiKey] ?: "" }.first()
    suspend fun getModel(): String = context.aiDataStore.data.map { it[Keys.model] ?: "" }.first()
    suspend fun hasConfig(): Boolean = getBaseUrl().isNotBlank() && getApiKey().isNotBlank()
}
