package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SshKey
import kotlinx.coroutines.flow.Flow

@Dao
interface SshKeyDao {
  @Query("SELECT * FROM ssh_keys ORDER BY createdAt DESC")
  fun getAllKeys(): Flow<List<SshKey>>

  @Query("SELECT * FROM ssh_keys WHERE id = :id")
  suspend fun getKeyById(id: String): SshKey?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertKey(key: SshKey)

  @Update
  suspend fun updateKey(key: SshKey)

  @Delete
  suspend fun deleteKey(key: SshKey)
}
