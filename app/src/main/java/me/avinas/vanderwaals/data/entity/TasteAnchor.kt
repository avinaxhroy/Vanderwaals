package me.avinas.vanderwaals.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

/**
 * Room entity for one row of the user's taste memory — a liked or
 * disliked wallpaper whose embedding anchors future ranking decisions.
 *
 * Replaces the single EMA `preferenceVector` as the source of truth for
 * personalisation.  Rows are pruned by recency ([updatedAt]) so the table
 * stays bounded, and embeddings may be empty for catalog items without a
 * client-side embedding (Vanderwaals Collection); such rows still serve as
 * cooldown markers for the item-level suppression.
 *
 * @see me.avinas.vanderwaals.algorithm.TasteMemory
 */
@Entity(
    tableName = "taste_anchors",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["updatedAt"])
    ]
)
@TypeConverters(Converters::class)
data class TasteAnchor(
    @PrimaryKey
    val wallpaperId: String,
    /** Either [KIND_LIKE] or [KIND_DISLIKE]. */
    val kind: String,
    val embedding: FloatArray,
    val updatedAt: Long,
    /** 1.0 for explicit feedback; implicit feedback records less. */
    val strength: Float = 1.0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TasteAnchor) return false
        return wallpaperId == other.wallpaperId &&
            kind == other.kind &&
            updatedAt == other.updatedAt &&
            strength == other.strength &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = wallpaperId.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + strength.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }

    companion object {
        const val KIND_LIKE = "like"
        const val KIND_DISLIKE = "dislike"

        /** Synthetic id used when seeding an anchor from the legacy preference vector. */
        const val LEGACY_SEED_ID = "__legacy_vector__"
    }
}
