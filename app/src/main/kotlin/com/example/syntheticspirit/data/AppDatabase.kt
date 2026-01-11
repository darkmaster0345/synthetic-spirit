package com.example.syntheticspirit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BlockedDomain::class, WhitelistedDomain::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun whitelistedDomainDao(): WhitelistedDomainDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "synthetic_spirit_db"
                )
                // For a FOSS app with 200k+ entries, we NEVER use destructive migration on upgrade.
                // In version 2+, we will use @AutoMigration(from = 1, to = 2) to preserve data.
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING) // Prevent UI stutter
                .fallbackToDestructiveMigrationOnDowngrade() // Only wipe on downgrade to prevent crashes
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
