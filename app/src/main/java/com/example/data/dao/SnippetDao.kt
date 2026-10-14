package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
  @Query("SELECT * FROM snippets ORDER BY category ASC, title ASC")
  fun getAllSnippets(): Flow<List<Snippet>>

  @Query("SELECT DISTINCT category FROM snippets")
  fun getCategories(): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnippet(snippet: Snippet)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSnippets(snippets: List<Snippet>)

  @Update
  suspend fun updateSnippet(snippet: Snippet)

  @Delete
  suspend fun deleteSnippet(snippet: Snippet)
}
