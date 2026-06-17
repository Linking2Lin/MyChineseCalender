package lins.libs.module_base.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import lins.libs.module_base.model.CHNDateEntity

@Dao
interface CHNDateDao {
    @Query("SELECT * FROM chndateentity")
    fun getAll(): List<CHNDateEntity>

    @Query("SELECT * FROM chndateentity WHERE uid = :id")
    fun getById(id: Int): CHNDateEntity?

    @Delete
    fun delete(user: CHNDateEntity)

    @Query("DELETE FROM chndateentity WHERE uid = :id")
    fun deleteById(id: Int)

//    @Insert
//    fun insertAll(vararg users: CHNDateEntity)

    @Insert
    fun insertDate(user: CHNDateEntity)

    @Query("SELECT * FROM chndateentity order by uid desc limit 1")
    fun getLast() : CHNDateEntity
}
