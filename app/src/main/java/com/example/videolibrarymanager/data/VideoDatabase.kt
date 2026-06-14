package com.example.videolibrarymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.videolibrarymanager.util.BugLogger

@Database(
    entities    = [VideoEntity::class, VideoFtsEntity::class],
    version     = VideoDatabase.DB_VERSION,
    exportSchema = true
)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao

    companion object {
        const val DB_VERSION = 3
        private const val DB_NAME = "video_library.db"
        private const val TAG = "VideoDatabase"

        @Volatile private var INSTANCE: VideoDatabase? = null

        fun getDatabase(context: Context): VideoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): VideoDatabase {
            BugLogger.info(TAG, "Building Room database: $DB_NAME v$DB_VERSION")
            return Room.databaseBuilder(
                context.applicationContext,
                VideoDatabase::class.java,
                DB_NAME
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        BugLogger.info(TAG, "DB onCreate — fresh install, schema v$DB_VERSION created")
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        BugLogger.debug(TAG, "DB onOpen — version=${db.version}")
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        BugLogger.warn(TAG, "DESTRUCTIVE migration executed — DB wiped and recreated")
                    }
                })
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                BugLogger.info(TAG, "Running MIGRATION 1→2: adding isCorrupt, thumbnailPath, checksum columns")
                db.execSQL("ALTER TABLE videos ADD COLUMN isCorrupt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE videos ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE videos ADD COLUMN checksum TEXT")
                BugLogger.info(TAG, "MIGRATION 1→2 complete")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                BugLogger.info(TAG, "Running MIGRATION 2→3: creating FTS4 table")

                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS videos_fts
                    USING fts4(name, category, path, content='videos')
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO videos_fts(docid, name, category, path)
                    SELECT id, name, category, path FROM videos
                """.trimIndent())

                BugLogger.info(TAG, "MIGRATION 2→3 complete")
            }
        }
    }
}
