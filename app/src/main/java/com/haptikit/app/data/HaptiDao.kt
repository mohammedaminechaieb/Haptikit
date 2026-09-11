package com.haptikit.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HaptiDao {

    // ---- Patterns ----
    @Query("SELECT * FROM patterns ORDER BY isBuiltIn DESC, name ASC")
    fun observePatterns(): Flow<List<PatternEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(pattern: PatternEntity): Long

    @Delete
    suspend fun deletePattern(pattern: PatternEntity)

    @Query("SELECT * FROM patterns WHERE id = :id")
    suspend fun getPattern(id: Long): PatternEntity?

    // ---- Assignments ----
    @Query("SELECT * FROM assignments ORDER BY targetType, targetLabel")
    fun observeAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments WHERE targetId = :targetId LIMIT 1")
    suspend fun getAssignment(targetId: String): AssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: AssignmentEntity)

    @Query("DELETE FROM assignments WHERE targetId = :targetId")
    suspend fun clearAssignment(targetId: String)
}
