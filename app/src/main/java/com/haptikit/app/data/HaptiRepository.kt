package com.haptikit.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HaptiRepository(context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val gson = Gson()

    fun observePatterns() = dao.observePatterns()
    fun observeAssignments() = dao.observeAssignments()

    suspend fun savePattern(pattern: PatternEntity) = dao.upsertPattern(pattern)
    suspend fun deletePattern(pattern: PatternEntity) = dao.deletePattern(pattern)
    suspend fun getPattern(id: Long) = dao.getPattern(id)

    suspend fun assign(targetId: String, type: TargetType, label: String, patternId: Long) =
        dao.upsertAssignment(AssignmentEntity(targetId, type, label, patternId))

    suspend fun clearAssignment(targetId: String) = dao.clearAssignment(targetId)

    // ---- Export / Import (JSON) ----
    data class ExportBundle(val patterns: List<PatternEntity>, val assignments: List<AssignmentEntity>)

    suspend fun exportJson(patterns: List<PatternEntity>, assignments: List<AssignmentEntity>): String =
        gson.toJson(ExportBundle(patterns, assignments))

    suspend fun importJson(json: String) {
        val type = object : TypeToken<ExportBundle>() {}.type
        val bundle: ExportBundle = gson.fromJson(json, type)
        bundle.patterns.forEach { dao.upsertPattern(it.copy(id = 0)) }
        // Assignments reference pattern IDs; re-importing across devices where
        // IDs shift is a known limitation of v0.1 — patterns should be imported
        // first and re-matched by name if exact IDs don't line up.
        bundle.assignments.forEach { dao.upsertAssignment(it) }
    }
}
