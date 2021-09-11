package org.telegram.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user")
data class User(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String = "",
    @ColumnInfo(name = "email") var email: String = "",
    @ColumnInfo(name = "partnerId") val partnerId: String = "",
    @ColumnInfo(name = "disabled") val disabled: Boolean = false,
    @ColumnInfo(name = "createdAt") val createdAt: String,
    @ColumnInfo(name = "updatedAt") val updatedAt: String
    )