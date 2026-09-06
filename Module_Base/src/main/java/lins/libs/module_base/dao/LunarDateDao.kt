package lins.libs.module_base.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lins.libs.module_base.model.LunarDateEntity

/** HKO 轻量农历缓存，与完整黄历表独立；所有日期键统一为 ISO 公历日期。 */
@Dao
interface LunarDateDao {
    /** 精确查询，离线时不能用昨日农历替代不存在的今日数据。 */
    @Query("SELECT * FROM lunar_date WHERE date = :date")
    suspend fun getByDate(date: String): LunarDateEntity?

    /** 向活跃 Glance 订阅者提供首次缓存和后续变化。 */
    @Query("SELECT * FROM lunar_date WHERE date = :date")
    fun observeByDate(date: String): Flow<LunarDateEntity?>

    /** 按 date 主键覆盖，避免后台周期同步增加同日重复行。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: LunarDateEntity)

    /** 同时清理过旧与过远未来记录；调用者须把预取日期包含在保留范围中。 */
    @Query("DELETE FROM lunar_date WHERE date < :before OR date > :after")
    suspend fun cleanup(before: String, after: String)
}
