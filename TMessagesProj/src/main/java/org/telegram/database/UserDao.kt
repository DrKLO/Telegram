package org.telegram.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface  UserDao {
    @Query("SELECT * FROM user")
    fun getAll(): List<User>

    @Query("SELECT * FROM user WHERE id LIKE :id")
    fun findById(id: String): List<User>

    @Insert
    fun insertAll(vararg user: User)

} 