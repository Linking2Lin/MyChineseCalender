package lins.applications.appwidget.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import lins.applications.appwidget.model.CHNDateEnity

@Dao
interface CHNDateDao {
    @Query("SELECT * FROM chndateenity")
    fun getAll(): List<CHNDateEnity>

    @Query("SELECT * FROM chndateenity WHERE uid = :id")
    fun getById(id: Int): CHNDateEnity?

    @Delete
    fun delete(user: CHNDateEnity)

    @Query("DELETE FROM chndateenity WHERE uid = :id")
    fun deleteById(id: Int)

//    @Insert
//    fun insertAll(vararg users: CHNDateEnity)

    @Insert
    fun insertDate(user: CHNDateEnity)


}