package com.haptikit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TargetType { CONTACT, APP }

/**
 * Links a target (a contact's lookup key, or an app's package name)
 * to one of the saved patterns.
 */
@Entity(tableName = "assignments")
data class AssignmentEntity(
    @PrimaryKey val targetId: String, // contact lookupKey OR package name
    val targetType: TargetType,
    val targetLabel: String,          // display name shown in the UI
    val patternId: Long
)
