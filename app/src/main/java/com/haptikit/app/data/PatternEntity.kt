package com.haptikit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * A single custom vibration pattern.
 * [timings] and [amplitudes] map directly onto VibrationEffect.createWaveform().
 * timings[i]   -> how long segment i lasts, in ms
 * amplitudes[i]-> 0..255 strength for that segment (0 = off)
 */
@Entity(tableName = "patterns")
@TypeConverters(PatternConverters::class)
data class PatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timings: List<Long>,
    val amplitudes: List<Int>,
    val isBuiltIn: Boolean = false
)

class PatternConverters {
    @TypeConverter
    fun fromLongList(value: List<Long>): String = value.joinToString(",")

    @TypeConverter
    fun toLongList(value: String): List<Long> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.toLong() }

    @TypeConverter
    fun fromIntList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toIntList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.toInt() }
}
