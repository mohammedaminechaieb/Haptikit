package com.haptikit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PatternEntity::class, AssignmentEntity::class], version = 1, exportSchema = false)
@TypeConverters(PatternConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): HaptiDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haptikit.db"
                ).addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed a few starter patterns so the app isn't empty on first launch.
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = get(context).dao()
                            dao.upsertPattern(
                                PatternEntity(name = "Heartbeat", timings = listOf(0, 90, 90, 90, 200), amplitudes = listOf(0, 200, 0, 200, 0), isBuiltIn = true)
                            )
                            dao.upsertPattern(
                                PatternEntity(name = "SOS", timings = listOf(0, 100, 100, 100, 100, 100, 300, 100, 100, 100), amplitudes = listOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255), isBuiltIn = true)
                            )
                            dao.upsertPattern(
                                PatternEntity(name = "Gentle Tap", timings = listOf(0, 40), amplitudes = listOf(0, 90), isBuiltIn = true)
                            )
                        }
                    }
                }).build().also { INSTANCE = it }
            }
    }
}
