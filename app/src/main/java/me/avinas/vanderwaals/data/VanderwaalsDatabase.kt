package me.avinas.vanderwaals.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.avinas.vanderwaals.data.dao.DownloadQueueDao
import me.avinas.vanderwaals.data.dao.UserPreferenceDao
import me.avinas.vanderwaals.data.dao.WallpaperHistoryDao
import me.avinas.vanderwaals.data.dao.WallpaperMetadataDao
import me.avinas.vanderwaals.data.entity.Converters
import me.avinas.vanderwaals.data.entity.DownloadQueueItem
import me.avinas.vanderwaals.data.entity.UserPreferences
import me.avinas.vanderwaals.data.entity.WallpaperHistory
import me.avinas.vanderwaals.data.entity.WallpaperMetadata

@Database(
    entities = [
        WallpaperMetadata::class,
        UserPreferences::class,
        WallpaperHistory::class,
        DownloadQueueItem::class,
        me.avinas.vanderwaals.data.entity.CategoryPreference::class,
        me.avinas.vanderwaals.data.entity.ColorPreference::class,
        me.avinas.vanderwaals.data.entity.CompositionPreference::class,
        me.avinas.vanderwaals.data.entity.TasteAnchor::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VanderwaalsDatabase : RoomDatabase() {
    
    abstract val wallpaperMetadataDao: WallpaperMetadataDao
    
    abstract val userPreferenceDao: UserPreferenceDao
    
    abstract val wallpaperHistoryDao: WallpaperHistoryDao
    
    abstract val downloadQueueDao: DownloadQueueDao
    
    abstract val categoryPreferenceDao: me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
    
    abstract val colorPreferenceDao: me.avinas.vanderwaals.data.dao.ColorPreferenceDao
    
    abstract val compositionPreferenceDao: me.avinas.vanderwaals.data.dao.CompositionPreferenceDao
    abstract val tasteAnchorDao: me.avinas.vanderwaals.data.dao.TasteAnchorDao

    companion object {
        const val DATABASE_NAME = "vanderwaals_db"
        
        const val DATABASE_VERSION = 13
        
        /**
         * Migration from database version 1 to version 2.
         * 
         * Changes:
         * - Added `contrast` column to `wallpaper_metadata` table
         * - Added index on `contrast` column
         * 
         * Migration path: v1 (brightness only) -> v2 (brightness + contrast)
         * 
         * For existing data:
         * - All existing wallpapers get default contrast value of 50
         * - This allows the app to function normally
         * - Users can adjust contrast preferences as they use the app
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE wallpaper_metadata ADD COLUMN contrast INTEGER DEFAULT 50 NOT NULL"
                )
                // Create index on contrast for efficient filtering
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wallpaper_metadata_contrast ON wallpaper_metadata(contrast)"
                )
            }
        }
        
        /**
         * Migration from database version 2 to version 3.
         * 
         * Changes:
         * - Added `momentumVector` column to `user_preferences` table for momentum-based learning
         * - Created `category_preferences` table for category-level tracking
         * - Added index on `lastShown` column for efficient recent category queries
         * 
         * Migration path: v2 (basic preferences) -> v3 (enhanced with momentum + categories)
         * 
         * For existing data:
         * - Existing user preferences get empty momentum vector (will be initialized on next update)
         * - Category preferences table starts empty (will populate as user interacts)
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Store as TEXT (JSON array) for consistency with other FloatArray columns
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN momentumVector TEXT NOT NULL DEFAULT '[]'"
                )
                
                // Prime reference from upload/category (Personalize) or empty (Auto)
                // Store as TEXT (JSON array) for consistency with other FloatArray columns
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN originalEmbedding TEXT NOT NULL DEFAULT '[]'"
                )
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_preferences (
                        category TEXT PRIMARY KEY NOT NULL,
                        likes INTEGER NOT NULL DEFAULT 0,
                        dislikes INTEGER NOT NULL DEFAULT 0,
                        views INTEGER NOT NULL DEFAULT 0,
                        lastShown INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create index on lastShown for efficient recent category queries
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_category_preferences_lastShown ON category_preferences(lastShown)"
                )
            }
        }
        
        /**
         * Migration from database version 3 to version 4.
         * 
         * Changes:
         * - Created `color_preferences` table for color-level tracking
         * - Added index on `lastShown` column for efficient recent color queries
         * - Enables fallback personalization when wallpaper categories are missing
         * 
         * Migration path: v3 (category preferences) -> v4 (category + color preferences)
         * 
         * For existing data:
         * - Color preferences table starts empty (will populate as user interacts)
         * - Uses RGB Euclidean distance for color similarity matching
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS color_preferences (
                        colorHex TEXT PRIMARY KEY NOT NULL,
                        likes INTEGER NOT NULL DEFAULT 0,
                        dislikes INTEGER NOT NULL DEFAULT 0,
                        views INTEGER NOT NULL DEFAULT 0,
                        lastShown INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create index on lastShown for efficient recent color queries
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_color_preferences_lastShown ON color_preferences(lastShown)"
                )
            }
        }
        
        /**
         * Migration from database version 4 to version 5.
         * 
         * Changes:
         * - Added `feedbackContext` column to `wallpaper_history` table
         * - Stores contextual information (time, battery, brightness) when feedback provided
         * - Enables future contextual recommendations
         * 
         * Migration path: v4 (basic history) -> v5 (history + context tracking)
         * 
         * For existing data:
         * - Existing history entries get null feedbackContext (legacy data)
         * - New feedback will include context information
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Store as TEXT (JSON) for FeedbackContext object
                db.execSQL(
                    "ALTER TABLE wallpaper_history ADD COLUMN feedbackContext TEXT DEFAULT NULL"
                )
            }
        }
        
        /**
         * Migration from database version 5 to version 6.
         * 
         * Changes:
         * - Created `composition_preferences` table for composition/layout tracking
         * - Stores learned preferences for symmetry, rule of thirds, center weight, etc.
         * - Enables advanced personalization based on visual composition patterns
         * 
         * Migration path: v5 (basic + color) -> v6 (+ composition preferences)
         * 
         * For existing data:
         * - Composition preferences table starts empty (will populate as user interacts)
         * - Default values are neutral (0.5) for all metrics
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS composition_preferences (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        averageSymmetry REAL NOT NULL DEFAULT 0.5,
                        averageRuleOfThirds REAL NOT NULL DEFAULT 0.5,
                        averageCenterWeight REAL NOT NULL DEFAULT 0.5,
                        averageEdgeDensity REAL NOT NULL DEFAULT 0.5,
                        averageComplexity REAL NOT NULL DEFAULT 0.5,
                        prefersHorizontalSymmetry REAL NOT NULL DEFAULT 0.5,
                        prefersVerticalSymmetry REAL NOT NULL DEFAULT 0.5,
                        prefersCenteredComposition REAL NOT NULL DEFAULT 0.5,
                        prefersEdgeDetail REAL NOT NULL DEFAULT 0.5,
                        sampleCount INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }
        
        /**
         * Migration from database version 6 to version 7.
         * 
         * Changes:
         * - Added performance indexes on frequently queried columns
         * - Optimizes wallpaper_history queries filtering by userFeedback
         * - Optimizes wallpaper_history queries filtering active wallpapers (removedAt IS NULL)
         * - Optimizes download_queue queries ordering by priority + filtering by downloaded status
         * 
         * Migration path: v6 (basic indexes) -> v7 (enhanced performance indexes)
         * 
         * For existing data:
         * - Indexes are created on existing data without modification
         * - Queries will automatically benefit from new indexes
         * - No data transformation required
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optimizes queries filtering by feedback (likes, dislikes)
                // Used by: getEntriesWithFeedback(), feedback analytics
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wallpaper_history_userFeedback " +
                    "ON wallpaper_history(userFeedback)"
                )
                
                // Optimizes queries for active wallpaper (WHERE removedAt IS NULL)
                // Used by: getActiveWallpaper(), getActiveWallpaperFlow()
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wallpaper_history_removedAt " +
                    "ON wallpaper_history(removedAt)"
                )
                
                // Optimizes queries ordering by priority while filtering by downloaded status
                // Used by: getTopUndownloaded(), queue management
                // SQLite will use this for covering index queries (index-only scans)
                // Note: Room's @Index annotation doesn't support DESC, so we create index without it
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_download_queue_downloaded_priority " +
                    "ON download_queue(downloaded, priority)"
                )
            }
        }
        
        /**
         * Migration from database version 7 to version 8.
         * 
         * Changes:
         * - Added composite indexes on wallpaper_metadata for complex filter queries
         * - Index on (category, brightness) for category-specific brightness filtering
         * - Index on (source, brightness) for source-specific brightness filtering
         * 
         * Migration path: v7 (single-column indexes) → v8 (+ composite indexes)
         * 
         * For existing data:
         * - Indexes are created on existing data without modification
         * - Queries will automatically benefit from new composite indexes
         * - No data transformation required
         * - Single-column indexes remain for backward compatibility
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optimizes queries filtering by both category and brightness range
                // Used by: getByCategoryAndBrightnessRange(), contextual filtering
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wallpaper_metadata_category_brightness " +
                    "ON wallpaper_metadata(category, brightness)"
                )
                
                // Optimizes queries filtering by both source and brightness range
                // Used by: getBySourceAndBrightnessRange(), source-specific contextual filtering
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_wallpaper_metadata_source_brightness " +
                    "ON wallpaper_metadata(source, brightness)"
                )
            }
        }

        /**
         * Migration from database version 8 to version 9.
         *
         * Changes:
         * - Created `vdw_cached_wallpapers` table for the Vanderwaals Collection
         *   on-demand wallpaper source (a TTL cache for API responses).
         *
         * Migration path: v8 → v9 (VDW cache table added)
         *
         * For existing data:
         * - New table starts empty (populated on-demand from API responses).
         * - No user data is modified.
         *
         * NOTE: This table is dropped again in MIGRATION_9_10, but Room requires
         * every version step to be present so the schema matches at each checkpoint.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS vdw_cached_wallpapers (
                        id TEXT NOT NULL,
                        url TEXT NOT NULL,
                        thumbnailUrl TEXT NOT NULL,
                        source TEXT NOT NULL,
                        category TEXT NOT NULL,
                        colors TEXT NOT NULL,
                        brightness INTEGER NOT NULL,
                        contrast INTEGER NOT NULL,
                        embedding TEXT NOT NULL,
                        resolution TEXT NOT NULL,
                        attribution TEXT,
                        cachedAt INTEGER NOT NULL,
                        mood TEXT NOT NULL,
                        style TEXT NOT NULL,
                        aestheticScore REAL,
                        qualityTier TEXT,
                        isDark INTEGER,
                        subCategory TEXT,
                        compositionScore REAL,
                        focalPointX REAL,
                        focalPointY REAL,
                        PRIMARY KEY(id)
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vdw_cached_wallpapers_source ON vdw_cached_wallpapers(source)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vdw_cached_wallpapers_category ON vdw_cached_wallpapers(category)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_vdw_cached_wallpapers_cachedAt ON vdw_cached_wallpapers(cachedAt)"
                )
            }
        }

        /**
         * Migration from database version 9 to version 10.
         *
         * Changes:
         * - Removed the vdw_cached_wallpapers table and its indexes; the
         *   Vanderwaals Collection on-demand source is no longer supported.
         *
         * Migration path: v9 (VDW cache present) → v10 (VDW cache dropped)
         *
         * For existing data:
         * - The vdw_cached_wallpapers table is dropped (was only ever a TTL
         *   cache for on-demand API responses; no user data is lost).
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_vdw_cached_wallpapers_source")
                db.execSQL("DROP INDEX IF EXISTS index_vdw_cached_wallpapers_category")
                db.execSQL("DROP INDEX IF EXISTS index_vdw_cached_wallpapers_cachedAt")
                db.execSQL("DROP TABLE IF EXISTS vdw_cached_wallpapers")
            }
        }

        /**
         * Migration from database version 10 to version 11.
         *
         * Changes:
         * - Added `aestheticScore` (REAL), `mood` (TEXT JSON), `style` (TEXT JSON)
         *   columns to `wallpaper_metadata` for Vanderwaals Collection semantic
         *   metadata. These fields are absent for GitHub/Bing sources and default
         *   to 0 / empty, enabling graceful degradation in the recommender.
         * - Added `moodAffinity` and `styleAffinity` (TEXT JSON Map<String,
         *   Float>) columns to `user_preferences` for learned mood/style
         *   preference tracking.
         *
         * Migration path: v10 → v11 (semantic metadata + affinity learning)
         *
         * For existing data:
         * - Existing wallpapers get aestheticScore=0, empty mood/style (neutral).
         * - Existing user preferences get empty affinity maps (start learning
         *   from subsequent feedback).
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE wallpaper_metadata ADD COLUMN aestheticScore REAL NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE wallpaper_metadata ADD COLUMN mood TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE wallpaper_metadata ADD COLUMN style TEXT NOT NULL DEFAULT '[]'"
                )
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN moodAffinity TEXT NOT NULL DEFAULT '{}'"
                )
                db.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN styleAffinity TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        /**
         * Migration from database version 11 to version 12.
         *
         * Changes:
         * - Created `taste_anchors` table for the multi-anchor taste memory
         *   that replaces the single EMA preference vector as the source of
         *   truth for personalisation.
         *
         * Migration path: v11 → v12 (taste anchors added)
         *
         * For existing data:
         * - The table starts empty. Learned taste is preserved:
         *   TasteAnchorRepositoryImpl surfaces the legacy `preferenceVector`
         *   as a synthetic in-memory anchor until real feedback populates
         *   persistent anchors, so no vector math happens in SQL.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS taste_anchors (
                        wallpaperId TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        embedding TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        strength REAL NOT NULL DEFAULT 1.0
                    )
                """)
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_taste_anchors_kind ON taste_anchors(kind)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_taste_anchors_updatedAt ON taste_anchors(updatedAt)"
                )
            }
        }

        /**
         * Migration from database version 12 to version 13.
         *
         * Changes:
         * - FloatArray columns switched from TEXT (JSON) to BLOB (raw
         *   little-endian float32) storage:
         *   - `wallpaper_metadata.embedding`
         *   - `taste_anchors.embedding`
         *   - `user_preferences.{preferenceVector, originalEmbedding, momentumVector}`
         *
         * Why: the ranking path deserialises the full catalog (plus all
         * taste anchors) on every selection.  Parsing JSON number arrays
         * (~14 KB of text per 1280-dim embedding, ~4.7M numbers per catalog
         * load) dominated selection latency and allocation rate.  BLOB
         * storage deserialises with zero parsing and shrinks each embedding
         * to 5,120 bytes; values are copied bit-exactly, so ranking results
         * are unchanged.
         *
         * Migration path: v12 (TEXT embeddings) → v13 (BLOB embeddings)
         *
         * For existing data:
         * - Each affected table is rebuilt (SQLite cannot ALTER a column's
         *   affinity); every row is copied with its embedding JSON parsed
         *   once and re-bound as a float32 BLOB.
         * - Rows whose JSON fails to parse degrade to an empty embedding,
         *   exactly as they already scored at read time.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val converters = me.avinas.vanderwaals.data.entity.Converters(
                    com.google.gson.Gson()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wallpaper_metadata_v13` (
                        `id` TEXT NOT NULL,
                        `url` TEXT NOT NULL,
                        `thumbnailUrl` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `colors` TEXT NOT NULL,
                        `brightness` INTEGER NOT NULL,
                        `contrast` INTEGER NOT NULL,
                        `embedding` BLOB NOT NULL,
                        `resolution` TEXT NOT NULL,
                        `attribution` TEXT,
                        `aestheticScore` REAL NOT NULL,
                        `mood` TEXT NOT NULL,
                        `style` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `wallpaper_metadata_v13` " +
                    "SELECT `id`, `url`, `thumbnailUrl`, `source`, `category`, `colors`, " +
                    "`brightness`, `contrast`, x'', `resolution`, `attribution`, " +
                    "`aestheticScore`, `mood`, `style` FROM `wallpaper_metadata`"
                )
                // Backfill embeddings row by row (JSON → float32 BLOB).
                val embeddingBinding = db.compileStatement(
                    "UPDATE `wallpaper_metadata_v13` SET `embedding` = ? WHERE `id` = ?"
                )
                db.query(
                    "SELECT `id`, `embedding` FROM `wallpaper_metadata` WHERE `embedding` != '[]'"
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val embeddingIndex = cursor.getColumnIndex("embedding")
                    while (cursor.moveToNext()) {
                        embeddingBinding.bindBlob(
                            1,
                            converters.floatArrayToBlob(converters.toFloatArray(cursor.getString(embeddingIndex)))
                        )
                        embeddingBinding.bindString(2, cursor.getString(idIndex))
                        embeddingBinding.executeUpdateDelete()
                    }
                }
                db.execSQL("DROP TABLE `wallpaper_metadata`")
                db.execSQL("ALTER TABLE `wallpaper_metadata_v13` RENAME TO `wallpaper_metadata`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_category` ON `wallpaper_metadata` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_source` ON `wallpaper_metadata` (`source`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_brightness` ON `wallpaper_metadata` (`brightness`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_contrast` ON `wallpaper_metadata` (`contrast`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_category_brightness` ON `wallpaper_metadata` (`category`, `brightness`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_metadata_source_brightness` ON `wallpaper_metadata` (`source`, `brightness`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `taste_anchors_v13` (
                        `wallpaperId` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `embedding` BLOB NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `strength` REAL NOT NULL,
                        PRIMARY KEY(`wallpaperId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `taste_anchors_v13` " +
                    "SELECT `wallpaperId`, `kind`, x'', `updatedAt`, `strength` FROM `taste_anchors`"
                )
                val anchorBinding = db.compileStatement(
                    "UPDATE `taste_anchors_v13` SET `embedding` = ? WHERE `wallpaperId` = ?"
                )
                db.query(
                    "SELECT `wallpaperId`, `embedding` FROM `taste_anchors` WHERE `embedding` != '[]'"
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndex("wallpaperId")
                    val embeddingIndex = cursor.getColumnIndex("embedding")
                    while (cursor.moveToNext()) {
                        anchorBinding.bindBlob(
                            1,
                            converters.floatArrayToBlob(converters.toFloatArray(cursor.getString(embeddingIndex)))
                        )
                        anchorBinding.bindString(2, cursor.getString(idIndex))
                        anchorBinding.executeUpdateDelete()
                    }
                }
                db.execSQL("DROP TABLE `taste_anchors`")
                db.execSQL("ALTER TABLE `taste_anchors_v13` RENAME TO `taste_anchors`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_taste_anchors_kind` ON `taste_anchors` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_taste_anchors_updatedAt` ON `taste_anchors` (`updatedAt`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_preferences_v13` (
                        `id` INTEGER NOT NULL,
                        `mode` TEXT NOT NULL,
                        `preferenceVector` BLOB NOT NULL,
                        `originalEmbedding` BLOB NOT NULL,
                        `momentumVector` BLOB NOT NULL,
                        `likedWallpaperIds` TEXT NOT NULL,
                        `dislikedWallpaperIds` TEXT NOT NULL,
                        `feedbackCount` INTEGER NOT NULL,
                        `epsilon` REAL NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        `moodAffinity` TEXT NOT NULL,
                        `styleAffinity` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.query("SELECT * FROM `user_preferences`").use { cursor ->
                    if (cursor.moveToFirst()) {
                        val preferenceVector = converters.floatArrayToBlob(
                            converters.toFloatArray(cursor.getString(cursor.getColumnIndexOrThrow("preferenceVector")))
                        )
                        val originalEmbedding = converters.floatArrayToBlob(
                            converters.toFloatArray(cursor.getString(cursor.getColumnIndexOrThrow("originalEmbedding")))
                        )
                        val momentumVector = converters.floatArrayToBlob(
                            converters.toFloatArray(cursor.getString(cursor.getColumnIndexOrThrow("momentumVector")))
                        )
                        db.execSQL(
                            "INSERT INTO `user_preferences_v13` VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                            arrayOf<Any?>(
                                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                                cursor.getString(cursor.getColumnIndexOrThrow("mode")),
                                preferenceVector,
                                originalEmbedding,
                                momentumVector,
                                cursor.getString(cursor.getColumnIndexOrThrow("likedWallpaperIds")),
                                cursor.getString(cursor.getColumnIndexOrThrow("dislikedWallpaperIds")),
                                cursor.getLong(cursor.getColumnIndexOrThrow("feedbackCount")),
                                cursor.getDouble(cursor.getColumnIndexOrThrow("epsilon")),
                                cursor.getLong(cursor.getColumnIndexOrThrow("lastUpdated")),
                                cursor.getString(cursor.getColumnIndexOrThrow("moodAffinity")),
                                cursor.getString(cursor.getColumnIndexOrThrow("styleAffinity"))
                            )
                        )
                    }
                }
                db.execSQL("DROP TABLE `user_preferences`")
                db.execSQL("ALTER TABLE `user_preferences_v13` RENAME TO `user_preferences`")
            }
        }

        /** Ordered migrations; append new ones here as the version increments. */
        val MIGRATIONS = arrayOf<Migration>(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
        )
        
        fun getInstance(context: android.content.Context): VanderwaalsDatabase {
            return androidx.room.Room.databaseBuilder(
                context,
                VanderwaalsDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*MIGRATIONS)
                .addTypeConverter(me.avinas.vanderwaals.data.entity.Converters(
                    com.google.gson.Gson()
                ))
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}

/**
 * Migration helper functions for common schema changes.
 */
object MigrationHelpers {
    
    fun addColumn(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String,
        defaultValue: String? = null
    ) {
        val defaultClause = if (defaultValue != null) " DEFAULT $defaultValue" else ""
        database.execSQL(
            "ALTER TABLE $tableName ADD COLUMN $columnName $columnType$defaultClause"
        )
    }
    
    fun createIndex(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
        indexName: String? = null
    ) {
        val name = indexName ?: "index_${tableName}_${columnName}"
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS $name ON $tableName($columnName)"
        )
    }
    
    fun dropIndex(
        database: SupportSQLiteDatabase,
        indexName: String
    ) {
        database.execSQL("DROP INDEX IF EXISTS $indexName")
    }
    
    /** Renames a table; changing its columns requires a new table + data copy. */
    fun renameTable(
        database: SupportSQLiteDatabase,
        oldName: String,
        newName: String
    ) {
        database.execSQL("ALTER TABLE $oldName RENAME TO $newName")
    }
}
