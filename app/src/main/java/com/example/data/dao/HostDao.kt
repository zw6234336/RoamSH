package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.HostConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
  @Query("SELECT * FROM hosts ORDER BY isStarred DESC, lastConnectedAt DESC")
  fun getAllHosts(): Flow<List<HostConfig>>

  @Query("SELECT * FROM hosts WHERE id = :id")
  suspend fun getHostById(id: String): HostConfig?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHost(host: HostConfig)

  @Update
  suspend fun updateHost(host: HostConfig)

  @Delete
  suspend fun deleteHost(host: HostConfig)

  @Query("UPDATE hosts SET lastConnectedAt = :timestamp WHERE id = :hostId")
  suspend fun updateLastConnected(hostId: String, timestamp: Long)
}
