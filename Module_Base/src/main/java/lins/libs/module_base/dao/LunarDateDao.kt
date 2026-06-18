package lins.libs.module_base.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import lins.libs.module_base.model.LunarDateEntity

@Dao
interface LunarDateDao {

    /** 按日期查询缓存 */
    @Query("SELECT * FROM lunar_date WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): LunarDateEntity?

    /** 获取最新一条缓存 */
    @Query("SELECT * FROM lunar_date ORDER BY date DESC LIMIT 1")
    suspend fun getLast(): LunarDateEntity?

    /** 插入或覆盖（同一天多次同步时直接替换） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: LunarDateEntity)

    /** 清理过期缓存，只保留最近 N 天 */
    @Query("DELETE FROM lunar_date WHERE date NOT IN (SELECT date FROM lunar_date ORDER BY date DESC LIMIT :keepDays)")
    suspend fun cleanup(keepDays: Int)
}
