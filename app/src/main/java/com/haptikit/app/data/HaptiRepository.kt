package com.haptikit.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class HaptiRepository(context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val gson = Gson()

    fun observePatterns() = dao.observePatterns()
    fun observeAssignments() = dao.observeAssignments()

    suspend fun savePattern(pattern: PatternEntity) = dao.upsertPattern(pattern)
    suspend fun getPattern(id: Long) = dao.getPattern(id)

    suspend fun deletePattern(pattern: PatternEntity) {
        dao.clearAssignmentsForPattern(pattern.id)
        dao.deletePattern(pattern)
    }

    suspend fun duplicatePattern(pattern: PatternEntity): Long =
        dao.upsertPattern(pattern.copy(id = 0, name = "${pattern.name} copy", isBuiltIn = false))

    suspend fun assign(targetId: String, type: TargetType, label: String, patternId: Long) =
        dao.upsertAssignment(AssignmentEntity(targetId, type, label, patternId))

    suspend fun clearAssignment(targetId: String) = dao.clearAssignment(targetId)

    // ---- Export / Import (JSON) ----
    data class ExportBundle(val version: Int = 1, val patterns: List<PatternEntity>, val assignments: List<AssignmentEntity>)

    suspend fun exportJson(): String =
        gson.toJson(ExportBundle(patterns = dao.getAllPatterns(), assignments = dao.getAllAssignments()))

    /**
     * Imports patterns (as new rows) and remaps each assignment from the
     * pattern id it had on the exporting device to the id that pattern got
     * here — ids differ across devices, so copying them verbatim would point
     * assignments at the wrong pattern. Patterns whose name already exists
     * are reused instead of duplicated. Returns (patterns, assignments) imported.
     */
    suspend fun importJson(json: String): Pair<Int, Int> {
        val bundle = try {
            gson.fromJson(json, ExportBundle::class.java)
        } catch (e: JsonSyntaxException) {
            null
        } ?: throw IllegalArgumentException("Not a HaptiKit backup file")

        val existingByName = dao.getAllPatterns().associateBy { it.name.lowercase() }
        val idMap = mutableMapOf<Long, Long>()
        var newPatterns = 0
        bundle.patterns.orEmpty().forEach { p ->
            val existing = existingByName[p.name.lowercase()]
            idMap[p.id] = if (existing != null && existing.timings == p.timings) {
                existing.id
            } else {
                newPatterns++
                dao.upsertPattern(p.copy(id = 0))
            }
        }
        var newAssignments = 0
        bundle.assignments.orEmpty().forEach { a ->
            val mapped = idMap[a.patternId] ?: return@forEach
            dao.upsertAssignment(a.copy(patternId = mapped))
            newAssignments++
        }
        return newPatterns to newAssignments
    }
}
