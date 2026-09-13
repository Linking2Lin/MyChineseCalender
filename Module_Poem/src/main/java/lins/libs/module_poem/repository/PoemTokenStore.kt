package lins.libs.module_poem.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// 文件名和键名沿用已有安装，不通过重命名来“修复”读取异常。
private val Context.poemDataStore by preferencesDataStore(name = "jinrishici_datastore")

/** Token 持久化契约，隔离 Android 存储与网络流程，使磁盘故障可以单独测试。 */
internal interface PoemTokenStore {
    /** @return 当前 Token；尚未保存返回 null，读失败抛出异常。 */
    suspend fun read(): String?

    /**
     * @param token 新 Token；null 表示删除已有 Token。
     * @return Unit；写入完成后返回，失败抛出异常。
     */
    suspend fun write(token: String?)
}

/**
 * 使用应用级 DataStore 保存 Token，不持有 Activity。
 * @param context 应用 Context，用于访问原有 Preferences 文件。
 */
internal class DataStorePoemTokenStore(context: Context) : PoemTokenStore {
    private val store = context.applicationContext.poemDataStore
    private val tokenKey = stringPreferencesKey("user_token")

    /** @return 原有 user_token 键的值，不存在时返回 null。 */
    override suspend fun read(): String? = store.data.first()[tokenKey]

    /**
     * @param token 待持久化值，null 时只删除 Token 键，不清空其他配置。
     * @return Unit；事务提交后完成。
     */
    override suspend fun write(token: String?) {
        store.edit { preferences ->
            if (token == null) preferences.remove(tokenKey) else preferences[tokenKey] = token
        }
    }
}
