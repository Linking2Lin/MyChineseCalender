package lins.libs.module_base.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import lins.libs.module_base.model.CHNDateEntity

@Dao
interface CHNDateDao {
    @Query("SELECT * FROM chn_date")
    suspend fun getAll(): List<CHNDateEntity>

    @Query("SELECT * FROM chn_date WHERE uid = :id")
    suspend fun getById(id: Int): CHNDateEntity?

    @Delete
    suspend fun delete(user: CHNDateEntity)

    @Query("DELETE FROM chn_date WHERE uid = :id")
    suspend fun deleteById(id: Int)

    @Insert
    suspend fun insertDate(user: CHNDateEntity)

    @Query("SELECT * FROM chn_date ORDER BY uid DESC LIMIT 1")
    suspend fun getLast(): CHNDateEntity?
}
