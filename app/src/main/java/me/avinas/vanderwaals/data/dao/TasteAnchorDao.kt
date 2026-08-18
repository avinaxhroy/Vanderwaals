package me.avinas.vanderwaals.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.avinas.vanderwaals.data.entity.TasteAnchor

/**
 * DAO for the [me.avinas.vanderwaals.algorithm.TasteMemory] persistence
 * layer.  Rows are always pruned newest-first so the table reflects the
 * user's *recent* taste, not their entire history.
 */
@Dao
interface TasteAnchorDao {

    @Query("SELECT * FROM taste_anchors WHERE kind = :kind ORDER BY updatedAt DESC")
    suspend fun getByKind(kind: String): List<TasteAnchor>

    /** Inserts or replaces the anchor for a wallpaper (one row per id). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(anchor: TasteAnchor)

    /** Batch variant of [upsert]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(anchors: List<TasteAnchor>)

    /**
     * Keeps only the [keep] newest anchors of a kind.  Applied after every
     * insert so state stays bounded.
     */
    @Query(
        """
        DELETE FROM taste_anchors WHERE kind = :kind AND wallpaperId NOT IN (
            SELECT wallpaperId FROM taste_anchors
            WHERE kind = :kind
            ORDER BY updatedAt DESC
            LIMIT :keep
        )
        """
    )
    suspend fun pruneTo(kind: String, keep: Int)

    @Query("DELETE FROM taste_anchors WHERE wallpaperId = :wallpaperId")
    suspend fun deleteByWallpaperId(wallpaperId: String)

    @Query("DELETE FROM taste_anchors")
    suspend fun deleteAll()
}
