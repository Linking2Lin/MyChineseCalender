package lins.libs.module_poem.repository

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import lins.libs.module_base.network.KtorClient
import lins.libs.module_poem.model.PoemResponse

/**
 * 今日诗词网络流程：读取/获取 Token → 请求诗句 → 认证拒绝时最多重试一次。
 * 存储只是缓存，读写失败不阻断已经可用的 Token；取消仍向上传播。互斥范围覆盖整次请求，
 * 防止同一个仓库的并发调用重复申请 Token 或让旧认证失败清掉刚取得的新 Token。
 *
 * @param tokens 可注入的 Token 存储，生产使用 DataStore。
 * @param client 共享或测试专用 HTTP 客户端；本仓库不负责关闭它。
 * @param reportFailure 故障记录回调，禁止记录 Token 值，测试可替换为无操作函数。
 */
class PoemRepository internal constructor(
    private val tokens: PoemTokenStore,
    private val client: HttpClient,
    private val reportFailure: (String, Exception?) -> Unit = { message, error ->
        Log.e("PoemRepository", message, error)
    },
) {
    /**
     * 装配真实网络和存储依赖。
     * @param context 业务入口 Context，内部只保留其 applicationContext。
     */
    constructor(context: Context) : this(DataStorePoemTokenStore(context), KtorClient.client)

    // 下面三个 Token 状态只在 mutex 保护的整次请求中访问，避免旧请求清掉新凭证。
    private val mutex = Mutex()
    // 表示本实例已完成首次缓存读取或主动失效处理；不代表磁盘一定读取成功。
    private var tokenRead = false
    // 当前可复用的凭证；先更新内存再保存，允许磁盘故障期间继续请求诗词。
    private var memoryToken: String? = null
    // 内存与磁盘可能不一致；待写值也可以为 null，表示删除已拒绝的凭证。
    private var tokenDirty = false

    /**
     * Token 接口的传输模型。
     * @param status 业务状态；只有 success 才采用 data。
     * @param data 服务端下发的 Token，空白值不可用，不能写入诊断日志。
     */
    @Serializable
    private data class TokenResponse(val status: String, val data: String)

    /**
     * 优先复用进程内 Token，首次访问才读磁盘；获取成功后先保留内存再尝试写盘。
     * @return 可用 Token，获取失败返回 null；此时仍允许上层按原有行为尝试匿名请求。
     */
    private suspend fun getOrFetchToken(): String? {
        if (!tokenRead) {
            // 读异常仅意味着没有可信缓存，不能让整个诗词请求提前退出。
            memoryToken = try {
                tokens.read()?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure("Unable to read poem token cache", e)
                null
            }
            tokenRead = true
        }
        if (memoryToken != null) {
            if (tokenDirty) persistToken()
            return memoryToken
        }
        return try {
            val response = client.get("https://v2.jinrishici.com/token")
            if (!response.status.isSuccess()) return null
            val result = response.body<TokenResponse>()
            if (result.status != "success" || result.data.isBlank()) return null
            memoryToken = result.data
            tokenDirty = true
            persistToken()
            memoryToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure("Unable to fetch poem token", e)
            null
        }
    }

    /**
     * 尽力将内存 Token 保存到磁盘，失败保留 dirty，下一次调用可重试而不重复申请 Token。
     * @return Unit；普通存储故障只记录，取消异常继续抛出。
     */
    private suspend fun persistToken() {
        try {
            tokens.write(memoryToken)
            tokenDirty = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure("Unable to persist poem token", e)
        }
    }

    /**
     * 标记认证失败 Token 不可信。即使删除磁盘缓存失败，也禁止重新读取它参与本轮重试。
     * @return Unit；取消异常继续抛出，存储失败不阻断申请新 Token。
     */
    private suspend fun rejectToken() {
        tokenRead = true
        memoryToken = null
        tokenDirty = true
        persistToken()
    }

    /**
     * 获取一首可展示的诗词；HTTP 401/403 清 Token 后最多再请求一次，避免递归无限重试。
     * HTTP 成功但业务状态失败或正文为空时返回 null，不以 HTTP 成功冒充有数据。
     * @return 有效 PoemResponse；普通网络/解析故障返回 null，由原有页面展示失败状态。
     * @throws CancellationException 调用方取消时传播，不吞掉取消或启动额外重试。
     */
    suspend fun fetchPoem(): PoemResponse? = mutex.withLock {
        try {
            // 上限针对诗句请求次数；只有认证拒绝才进入下一轮，不对普通故障无限重试。
            repeat(2) { attempt ->
                val token = getOrFetchToken()
                val response = client.get("https://v2.jinrishici.com/sentence") {
                    if (!token.isNullOrBlank()) header("X-User-Token", token)
                }
                if (response.status.isSuccess()) {
                    return@withLock response.body<PoemResponse>().takeIf(PoemResponse::isUsable)
                }
                if (response.status.value != 401 && response.status.value != 403) {
                    reportFailure("Poem HTTP ${response.status.value}", null)
                    return@withLock null
                }
                // 第二次也拒绝时清掉无效 Token，但不再发起第三次请求。
                rejectToken()
                if (attempt == 1) reportFailure("Poem authentication retry rejected", null)
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportFailure("Unable to fetch poem", e)
            null
        }
    }
}
