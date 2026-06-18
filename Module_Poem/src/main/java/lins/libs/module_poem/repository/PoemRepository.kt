package lins.libs.module_poem.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import lins.libs.module_poem.model.PoemResponse
import lins.libs.module_base.network.KtorClient

// Declare the DataStore delegate at the top level of the file as recommended by Android official docs
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jinrishici_datastore")

class PoemRepository(private val context: Context) {
    private val client = KtorClient.client
    private val tokenKey = stringPreferencesKey("user_token")

    companion object {
        private const val TAG = "PoemRepository"
    }

    @Serializable
    private data class TokenResponse(
        val status: String,
        val data: String
    )

    private suspend fun getOrFetchToken(): String? {
        val cachedToken = context.dataStore.data.map { preferences ->
            preferences[tokenKey]
        }.firstOrNull()

        if (!cachedToken.isNullOrEmpty()) {
            return cachedToken
        }

        return try {
            val response = client.get("https://v2.jinrishici.com/token")
            if (response.status.isSuccess()) {
                val tokenRes = response.body<TokenResponse>()
                if (tokenRes.status == "success" && tokenRes.data.isNotEmpty()) {
                    context.dataStore.edit { preferences ->
                        preferences[tokenKey] = tokenRes.data
                    }
                    tokenRes.data
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch token", e)
            null
        }
    }

    // 注意：此处不需要 withContext(Dispatchers.IO)，因为：
    // 1. DataStore 的读写是 suspend 函数，内部使用自己的调度器
    // 2. Ktor 的 client.get 也是 suspend 函数，不需要外部 IO 调度器
    suspend fun fetchPoem(): PoemResponse? {
        return try {
            val token = getOrFetchToken()
            val response = client.get("https://v2.jinrishici.com/sentence") {
                if (!token.isNullOrEmpty()) {
                    header("X-User-Token", token)
                }
            }
            if (response.status.isSuccess()) {
                response.body<PoemResponse>()
            } else if (response.status.value == 401 || response.status.value == 403) {
                // Token 失效，清除缓存并重试一次
                Log.w(TAG, "Token rejected (HTTP ${response.status}), clearing and retrying")
                context.dataStore.edit { it.remove(tokenKey) }
                val newToken = getOrFetchToken()
                val retryResponse = client.get("https://v2.jinrishici.com/sentence") {
                    if (!newToken.isNullOrEmpty()) {
                        header("X-User-Token", newToken)
                    }
                }
                if (retryResponse.status.isSuccess()) {
                    retryResponse.body<PoemResponse>()
                } else {
                    Log.e(TAG, "Retry failed: HTTP ${retryResponse.status}")
                    null
                }
            } else {
                Log.e(TAG, "Failed to fetch poem: HTTP ${response.status}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception during fetchPoem", e)
            null
        }
    }
}
