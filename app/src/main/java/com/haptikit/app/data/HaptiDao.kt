package com.haptikit.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HaptiDao {

    // ---- Patterns ----
    @Query("SELECT * FROM patterns ORDER BY isBuiltIn DESC, name ASC")
    fun observePatterns(): Flow<List<PatternEntity>>

    @Query("SELECT * FROM patterns")
    suspend fun getAllPatterns(): List<PatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPattern(pattern: PatternEntity): Long

    @Delete
    suspend fun deletePattern(pattern: PatternEntity)

    @Query("SELECT * FROM patterns WHERE id = :id")
    suspend fun getPattern(id: Long): PatternEntity?

    // ---- Assignments ----
    @Query("SELECT * FROM assignments ORDER BY targetType, targetLabel")
    fun observeAssignments(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignments")
    suspend fun getAllAssignments(): List<AssignmentEntity>

    @Query("SELECT * FROM assignments WHERE targetId = :targetId LIMIT 1")
    suspend fun getAssignment(targetId: String): AssignmentEntity?

    /** Fallback contact match by display name (case-insensitive) — used when
     *  a messaging app only tells us the sender's name, not a phone number. */
    @Query("SELECT * FROM assignments WHERE targetType = 'CONTACT' AND targetLabel = :name COLLATE NOCASE LIMIT 1")
    suspend fun getContactAssignmentByName(name: String): AssignmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignment(assignment: AssignmentEntity)

    @Query("DELETE FROM assignments WHERE targetId = :targetId")
    suspend fun clearAssignment(targetId: String)

    /** Assignments pointing at a deleted pattern would silently do nothing. */
    @Query("DELETE FROM assignments WHERE patternId = :patternId")
    suspend fun clearAssignmentsForPattern(patternId: Long)
}
