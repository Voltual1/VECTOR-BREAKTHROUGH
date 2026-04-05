package me.voltual.vb.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.voltual.vb.core.database.entity.ScriptEntry

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY lastModified DESC")
    fun getAllScripts(): Flow<List<ScriptEntry>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScriptById(id: Int): ScriptEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntry)

    @Delete
    suspend fun deleteScript(script: ScriptEntry)

    @Update
    suspend fun updateScript(script: ScriptEntry)
}