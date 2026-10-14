package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PortTunnel
import kotlinx.coroutines.flow.Flow

@Dao
interface PortTunnelDao {
  @Query("SELECT * FROM port_tunnels WHERE hostId = :hostId")
  fun getTunnelsForHost(hostId: String): Flow<List<PortTunnel>>

  @Query("SELECT * FROM port_tunnels")
  fun getAllTunnels(): Flow<List<PortTunnel>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTunnel(tunnel: PortTunnel)

  @Update
  suspend fun updateTunnel(tunnel: PortTunnel)

  @Delete
  suspend fun deleteTunnel(tunnel: PortTunnel)
}
