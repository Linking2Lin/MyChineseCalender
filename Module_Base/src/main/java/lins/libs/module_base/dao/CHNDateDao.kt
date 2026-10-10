package lins.libs.module_base.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import lins.libs.module_base.model.CHNDateEntity

/** 完整黄历的按日存储接口；日期参数统一使用 LocalDate.toString() 生成的 ISO 文本。 */
@Dao
interface CHNDateDao {
    /**
     * 只查询指定公历日，缺失返回 null；禁止改为“最后一条”作为今天的兜底。
     * @param date ISO 公历日期文本，须与保存时的主键格式一致。
     * @return 指定日期的缓存实体；没有该日记录时返回 null。
     */
    @Query("SELECT * FROM chn_date WHERE date = :date")
    suspend fun getByDate(date: String): CHNDateEntity?

    /**
     * Room 冷流，订阅后发出当前值并在表失效通知后重新查询。
     * @param date ISO 公历日期文本，须与保存时的主键格式一致。
     * @return Flow；首次查询及后续数据库变动时发出该日期的实体，缺失时为 null。
     */
    @Query("SELECT * FROM chn_date WHERE date = :date")
    fun observeByDate(date: String): Flow<CHNDateEntity?>

    /**
     * 日期主键冲突时整行替换；传入模型必须已通过核心字段校验。
     * @param entity 待替换写入的完整缓存实体，主键为 ISO 公历日期。
     * @return Unit；按日期主键完成整行替换，存储异常交给仓库处理。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: CHNDateEntity)

    /**
     * 删除闭区间外的记录；统一 ISO 格式使当前支持年份的文本比较与日期顺序一致。
     * @param before 保留区间的起始日期，包含当天；早于此值的记录将被清理。 使用 ISO 日期文本。
     * @param after 保留区间的结束日期，包含当天；晚于此值的记录将被清理。 使用 ISO 日期文本。
     * @return Unit；删除保留闭区间之外的记录，失败抛出存储异常。
     */
    @Query("DELETE FROM chn_date WHERE date < :before OR date > :after")
    suspend fun cleanup(before: String, after: String)
}
