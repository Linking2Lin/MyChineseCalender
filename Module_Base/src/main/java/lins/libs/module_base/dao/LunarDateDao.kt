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
    /**
     * 精确查询，离线时不能用昨日农历替代不存在的今日数据。
     * @param date ISO 公历日期文本，须与保存时的主键格式一致。
     * @return 指定日期的缓存实体；没有该日记录时返回 null。
     */
    @Query("SELECT * FROM lunar_date WHERE date = :date")
    suspend fun getByDate(date: String): LunarDateEntity?

    /**
     * 向活跃 Glance 订阅者提供首次缓存和后续变化。
     * @param date ISO 公历日期文本，须与保存时的主键格式一致。
     * @return Flow；首次查询及后续数据库变动时发出该日期的实体，缺失时为 null。
     */
    @Query("SELECT * FROM lunar_date WHERE date = :date")
    fun observeByDate(date: String): Flow<LunarDateEntity?>

    /**
     * 按 date 主键覆盖，避免后台周期同步增加同日重复行。
     * @param entity 待替换写入的完整缓存实体，主键为 ISO 公历日期。
     * @return Unit；按日期主键完成整行替换，存储异常交给仓库处理。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: LunarDateEntity)

    /**
     * 同时清理过旧与过远未来记录；调用者须把预取日期包含在保留范围中。
     * @param before 保留区间的起始日期，包含当天；早于此值的记录将被清理。 使用 ISO 日期文本。
     * @param after 保留区间的结束日期，包含当天；晚于此值的记录将被清理。 使用 ISO 日期文本。
     * @return Unit；删除保留闭区间之外的记录，失败抛出存储异常。
     */
    @Query("DELETE FROM lunar_date WHERE date < :before OR date > :after")
    suspend fun cleanup(before: String, after: String)
}
