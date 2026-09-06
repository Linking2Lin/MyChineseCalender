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

// 顶层委托复用同一 DataStore，避免每个仓库实例为同一文件创建独立存储器。
// 文件名和键名是已安装用户的持久化身份，改名会丢失对现有 Token 的读取。
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jinrishici_datastore")

/**
 * 今日诗词服务适配器：复用 Token 后获取诗句，认证拒绝时最多清理并重试一次。
 *
 * 普通网络/存储/解析故障返回 null，由 ViewModel 保留旧诗词并提示失败；协程取消继续抛出。
 * 本类没有请求互斥，当前由 ViewModel 的加载标记合并点击；增加新调用入口时需重新考虑并发。
 * context 应使用 applicationContext，避免 DataStore/仓库长期持有页面。
 */
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

    /**
     * 首选 DataStore 中非空 Token，缺失才访问 token 接口。成功后先持久化再返回。
     * 缓存读取发生在本方法的 try 外，异常最终由 fetchPoem 的外层捕获并转为失败。
     */
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

    /**
     * 请求一首可用诗词，不依赖黄历日期，也不把诗句缓存到 Room。
     * DataStore/Ktor 自行管理挂起 IO，无需为这两种 API 再包一层 IO 调度。
     * Token 获取失败时仍尝试不带 Token 请求；只有 HTTP 401/403 进入清 Token 的一次性重试，
     * HTTP 成功但业务 status/content 不可用时返回 null，不进行认证重试。
     */
    suspend fun fetchPoem(): PoemResponse? {
        return try {
            val token = getOrFetchToken()
            val response = client.get("https://v2.jinrishici.com/sentence") {
                if (!token.isNullOrEmpty()) {
                    header("X-User-Token", token)
                }
            }
            if (response.status.isSuccess()) {
                response.body<PoemResponse>().takeIf(PoemResponse::isUsable)
            } else if (response.status.value == 401 || response.status.value == 403) {
                // 不递归调用 fetchPoem，确保服务端持续拒绝认证时也只有一次额外诗词请求。
                Log.w(TAG, "Token rejected (HTTP ${response.status}), clearing and retrying")
                context.dataStore.edit { it.remove(tokenKey) }
                val newToken = getOrFetchToken()
                val retryResponse = client.get("https://v2.jinrishici.com/sentence") {
                    if (!newToken.isNullOrEmpty()) {
                        header("X-User-Token", newToken)
                    }
                }
                if (retryResponse.status.isSuccess()) {
                    retryResponse.body<PoemResponse>().takeIf(PoemResponse::isUsable)
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
