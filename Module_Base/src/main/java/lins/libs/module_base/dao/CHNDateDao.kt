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
    /** 只查询指定公历日，缺失返回 null；禁止改为“最后一条”作为今天的兜底。 */
    @Query("SELECT * FROM chn_date WHERE date = :date")
    suspend fun getByDate(date: String): CHNDateEntity?

    /** Room 冷流，订阅后发出当前值并在表失效通知后重新查询。 */
    @Query("SELECT * FROM chn_date WHERE date = :date")
    fun observeByDate(date: String): Flow<CHNDateEntity?>

    /** 日期主键冲突时整行替换；传入模型必须已通过核心字段校验。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: CHNDateEntity)

    /** 删除闭区间外的记录；统一 ISO 格式使当前支持年份的文本比较与日期顺序一致。 */
    @Query("DELETE FROM chn_date WHERE date < :before OR date > :after")
    suspend fun cleanup(before: String, after: String)
}
