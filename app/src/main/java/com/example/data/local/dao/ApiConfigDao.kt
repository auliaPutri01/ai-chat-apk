package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ApiConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs WHERE isActive = 1 LIMIT 1")
    fun getActiveConfig(): Flow<ApiConfigEntity?>

    @Query("SELECT * FROM api_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfigOnce(): ApiConfigEntity?

    @Query("SELECT * FROM api_configs ORDER BY providerName ASC")
    fun getAllConfigs(): Flow<List<ApiConfigEntity>>

    @Query("SELECT * FROM api_configs WHERE id = :configId LIMIT 1")
    suspend fun getConfigById(configId: String): ApiConfigEntity?

    @Query("SELECT * FROM api_configs ORDER BY providerName ASC")
    suspend fun getAllConfigsOnce(): List<ApiConfigEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: ApiConfigEntity)

    @Update
    suspend fun update(config: ApiConfigEntity)

    @Query("UPDATE api_configs SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE api_configs SET isActive = 1 WHERE id = :configId")
    suspend fun activateConfig(configId: String)

    @Query("DELETE FROM api_configs WHERE id = :configId")
    suspend fun deleteConfig(configId: String)
}
