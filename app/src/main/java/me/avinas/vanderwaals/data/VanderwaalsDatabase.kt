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

/**
 * Room database for Vanderwaals wallpaper personalization system.
 * 
 * Extends Vanderwaals's database architecture with intelligent personalization tables:
 * - [WallpaperMetadata]: Pre-computed embeddings and metadata for 6000+ wallpapers
 * - [UserPreferences]: Learned aesthetic preferences with EMA algorithm
 * - [WallpaperHistory]: User interaction tracking for learning and history UI
 * - [DownloadQueueItem]: Priority-based download queue management
 * 
 * **Architecture:**
 * - Uses Room for reactive data access with Flow
 * - Custom TypeConverters for complex types (FloatArray, List<String>)
 * - Indexed columns for performance (category, brightness, priority)
 * - Auto-cleanup for history (100 entries) and queue (50 entries)
 * 
 * **Learning Algorithm:**
 * The database supports a complete personalization pipeline:
 * 1. Load wallpapers with embeddings from WallpaperMetadata
 * 2. Calculate similarity using UserPreferences.preferenceVector
 * 3. Populate DownloadQueueItem with top matches
 * 4. Track user interactions in WallpaperHistory
 * 5. Update preferences based on feedback (EMA algorithm)
 * 6. Re-rank queue with updated similarities
 * 
 * **Type Converters:**
 * - [Converters] handles List<String> and FloatArray serialization
 * - Provided via dependency injection for testability
 * 
 * **Database Migration:**
 * - Version 1: Initial schema with all tables
 * - Future versions: Add migrations in [MIGRATIONS] list
 * - Pre-launch: Use fallbackToDestructiveMigration() for development
 * 
 * **Usage:**
 * ```kotlin
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object DatabaseModule {
 *     @Provides
 *     @Singleton
 *     fun provideDatabase(
 *         @ApplicationContext context: Context,
 *         converters: Converters
 *     ): VanderwaalsDatabase {
 *         return Room.databaseBuilder(
 *             context,
 *             VanderwaalsDatabase::class.java,
 *             VanderwaalsDatabase.DATABASE_NAME
 *         )
 *         .addTypeConverter(converters)
 *         .addMigrations(*VanderwaalsDatabase.MIGRATIONS)
 *         .fallbackToDestructiveMigration() // Remove for production
 *         .build()
 *     }
 * }
 * ```
 * 
 * @see WallpaperMetadata
 * @see UserPreferences
 * @see WallpaperHistory
 * @see DownloadQueueItem
 * @see Converters
 */
@Database(
    entities = [
        WallpaperMetadata::class,
        UserPreferences::class,
        WallpaperHistory::class,
        DownloadQueueItem::class,
        me.avinas.vanderwaals.data.entity.CategoryPreference::class,
        me.avinas.vanderwaals.data.entity.ColorPreference::class,
        me.avinas.vanderwaals.data.entity.CompositionPreference::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VanderwaalsDatabase : RoomDatabase() {
    
    /**
     * DAO for accessing wallpaper metadata.
     * 
     * Provides queries for:
     * - Loading all wallpapers with embeddings
     * - Filtering by category, source, brightness
     * - Inserting/updating from manifest sync
     */
    abstract val wallpaperMetadataDao: WallpaperMetadataDao
    
    /**
     * DAO for managing user preferences.
     * 
     * Singleton pattern (single row with id = 1):
     * - Load/update preference vector
     * - Switch between auto/personalized modes
     * - Track feedback count and epsilon
     */
    abstract val userPreferenceDao: UserPreferenceDao
    
    /**
     * DAO for wallpaper application history.
     * 
     * Provides queries for:
     * - Recording wallpaper applications/removals
     * - Tracking user feedback (likes/dislikes)
     * - Displaying history in UI
     * - Calculating implicit feedback from duration
     */
    abstract val wallpaperHistoryDao: WallpaperHistoryDao
    
    /**
     * DAO for managing download queue.
     * 
     * Provides queries for:
     * - Populating queue with top matches
     * - Retrieving wallpapers to download
     * - Updating download status and priorities
     * - Re-ranking after feedback
     */
    abstract val downloadQueueDao: DownloadQueueDao
    
    /**
     * DAO for managing category preferences.
     * 
     * Provides queries for:
     * - Tracking likes/dislikes per category
     * - Recording category views
     * - Calculating category scores
     * - Identifying underexplored categories
     */
    abstract val categoryPreferenceDao: me.avinas.vanderwaals.data.dao.CategoryPreferenceDao
    
    /**
     * DAO for managing color preferences.
     * 
     * Provides queries for:
     * - Tracking likes/dislikes per color hex code
     * - Recording color views from wallpaper palettes
     * - Calculating color preference scores
     * - Fallback personalization when categories are missing
     */
    abstract val colorPreferenceDao: me.avinas.vanderwaals.data.dao.ColorPreferenceDao
    
    /**
     * DAO for managing composition preferences.
     * 
     * Provides queries for:
     * - Tracking learned composition/layout preferences
     * - Storing average symmetry, center weight, complexity values
     * - Calculating composition similarity scores
     * - Personalization based on visual composition patterns
     */
    abstract val compositionPreferenceDao: me.avinas.vanderwaals.data.dao.CompositionPreferenceDao

    companion object {
        /**
         * Database name for Room.
         */
        const val DATABASE_NAME = "vanderwaals_db"
        
        /**
         * Current database version.
         */
        const val DATABASE_VERSION = 6
        
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add contrast column with default value of 50
                database.execSQL(
                    "ALTER TABLE wallpaper_metadata ADD COLUMN contrast INTEGER DEFAULT 50 NOT NULL"
                )
                // Create index on contrast for efficient filtering
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add momentumVector column to user_preferences
                // Store as TEXT (JSON array) for consistency with other FloatArray columns
                database.execSQL(
                    "ALTER TABLE user_preferences ADD COLUMN momentumVector TEXT NOT NULL DEFAULT '[]'"
                )
                
                // Create category_preferences table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_preferences (
                        category TEXT PRIMARY KEY NOT NULL,
                        likes INTEGER NOT NULL DEFAULT 0,
                        dislikes INTEGER NOT NULL DEFAULT 0,
                        views INTEGER NOT NULL DEFAULT 0,
                        lastShown INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create index on lastShown for efficient recent category queries
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create color_preferences table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS color_preferences (
                        colorHex TEXT PRIMARY KEY NOT NULL,
                        likes INTEGER NOT NULL DEFAULT 0,
                        dislikes INTEGER NOT NULL DEFAULT 0,
                        views INTEGER NOT NULL DEFAULT 0,
                        lastShown INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Create index on lastShown for efficient recent color queries
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add feedbackContext column to wallpaper_history
                // Store as TEXT (JSON) for FeedbackContext object
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create composition_preferences table
                database.execSQL("""
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
         * Array of database migrations.
         * 
         * Add new migrations here when incrementing version:
         * ```kotlin
         * val MIGRATIONS = arrayOf(
         *     MIGRATION_1_2,
         *     MIGRATION_2_3,
         *     // ...
         * )
         * ```
         */
        val MIGRATIONS = arrayOf<Migration>(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )
        
        /**
         * Gets or creates the VanderwaalsDatabase singleton instance.
         * 
         * @param context Android application context
         * @return VanderwaalsDatabase instance
         */
        fun getInstance(context: android.content.Context): VanderwaalsDatabase {
            return androidx.room.Room.databaseBuilder(
                context,
                VanderwaalsDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(*MIGRATIONS)
                .build()
        }
        
        /**
         * Example migration from version 1 to 2.
         * 
         * Uncomment and modify when needed:
         * ```kotlin
         * private val MIGRATION_1_2 = object : Migration(1, 2) {
         *     override fun migrate(database: SupportSQLiteDatabase) {
         *         // Example: Add a new column
         *         database.execSQL(
         *             "ALTER TABLE wallpaper_metadata ADD COLUMN tags TEXT DEFAULT '[]'"
         *         )
         *     }
         * }
         * ```
         */
    }
}

/**
 * Example Migration: Version 1 → Version 2 (for reference)
 * 
 * To use:
 * 1. Uncomment this migration
 * 2. Add to MIGRATIONS array
 * 3. Increment DATABASE_VERSION to 2
 * 4. Update @Database version to 2
 */
/*
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Example 1: Add a new column
        database.execSQL(
            "ALTER TABLE wallpaper_metadata ADD COLUMN tags TEXT DEFAULT '[]'"
        )
        
        // Example 2: Create a new table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS category_preferences (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                category TEXT NOT NULL,
                likeCount INTEGER NOT NULL DEFAULT 0,
                dislikeCount INTEGER NOT NULL DEFAULT 0,
                UNIQUE(category)
            )
        """)
        
        // Example 3: Create an index
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_wallpaper_metadata_tags ON wallpaper_metadata(tags)"
        )
        
        // Example 4: Populate new table from existing data
        database.execSQL("""
            INSERT OR IGNORE INTO category_preferences (category, likeCount, dislikeCount)
            SELECT 
                wm.category,
                SUM(CASE WHEN wh.userFeedback = 'like' THEN 1 ELSE 0 END) as likeCount,
                SUM(CASE WHEN wh.userFeedback = 'dislike' THEN 1 ELSE 0 END) as dislikeCount
            FROM wallpaper_metadata wm
            LEFT JOIN wallpaper_history wh ON wm.id = wh.wallpaperId
            GROUP BY wm.category
        """)
    }
}
*/

/**
 * Migration helper functions for common schema changes.
 */
object MigrationHelpers {
    
    /**
     * Adds a new nullable column to a table.
     * 
     * @param database Database instance
     * @param tableName Name of the table
     * @param columnName Name of the new column
     * @param columnType SQL type (e.g., "TEXT", "INTEGER", "REAL")
     * @param defaultValue Default value for existing rows (optional)
     */
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
    
    /**
     * Creates an index on a table column.
     * 
     * @param database Database instance
     * @param tableName Name of the table
     * @param columnName Name of the column to index
     * @param indexName Optional custom index name
     */
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
    
    /**
     * Drops an index from the database.
     * 
     * @param database Database instance
     * @param indexName Name of the index to drop
     */
    fun dropIndex(
        database: SupportSQLiteDatabase,
        indexName: String
    ) {
        database.execSQL("DROP INDEX IF EXISTS $indexName")
    }
    
    /**
     * Renames a table (requires creating new table and copying data).
     * 
     * @param database Database instance
     * @param oldName Current table name
     * @param newName New table name
     */
    fun renameTable(
        database: SupportSQLiteDatabase,
        oldName: String,
        newName: String
    ) {
        database.execSQL("ALTER TABLE $oldName RENAME TO $newName")
    }
}
