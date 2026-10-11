package com.example.healthheatv2.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A body-weight reading. Synced to the account like meal logs. */
@Entity(tableName = "weight_logs", indices = [Index(value = ["clientId"], unique = true), Index(value = ["measuredAt"])])
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val measuredAt: Long,
    val weightKg: Float,
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val dirty: Boolean = true,
    val deleted: Boolean = false
)

@Dao
interface WeightLogDao {
    @Insert
    suspend fun insert(entry: WeightLog): Long

    @Update
    suspend fun update(entry: WeightLog)

    @Query("SELECT * FROM weight_logs WHERE deleted = 0 ORDER BY measuredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeightLog>>

    @Query("UPDATE weight_logs SET deleted = 1, dirty = 1 WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM weight_logs WHERE dirty = 1")
    suspend fun getDirty(): List<WeightLog>

    @Query("SELECT COUNT(*) FROM weight_logs WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT * FROM weight_logs WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): WeightLog?

    @Query("UPDATE weight_logs SET dirty = 0 WHERE clientId = :clientId AND deleted = :deletedWhenSent")
    suspend fun markClean(clientId: String, deletedWhenSent: Boolean)
}
