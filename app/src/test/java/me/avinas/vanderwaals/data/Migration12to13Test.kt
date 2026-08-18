package me.avinas.vanderwaals.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import me.avinas.vanderwaals.data.entity.Converters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real v12 → v13 migration against a v12 database built from
 * the checked-in schema, then validates the migrated schema against the
 * generated v13 schema.
 *
 * This is the guarantee that upgrading users do not hit Room's
 * "Migration didn't properly handle" crash (or the destructive fallback
 * wiping their taste anchors and history): if the migration's CREATE
 * statements ever drift from what Room expects at v13,
 * [MigrationTestHelper.runMigrationsAndValidate] fails here, not in
 * production.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class Migration12to13Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VanderwaalsDatabase::class.java
    )

    private val converters = Converters(Gson())
    private val migration =
        VanderwaalsDatabase.MIGRATIONS.first { it.startVersion == 12 && it.endVersion == 13 }

    private fun embedding1280(seed: Int): FloatArray =
        FloatArray(1280) { (kotlin.math.sin((it + seed) * 0.37) * 0.05).toFloat() }

    @Test
    fun `migrates v12 data to v13 with bit-exact embeddings and intact schema`() {
        val goodEmbedding = embedding1280(1)
        val goodAnchor = embedding1280(2)
        val prefVector = embedding1280(3)
        val originalEmbedding = embedding1280(4)

        helper.createDatabase(TEST_DB, 12).use { db ->
            // Full row with a real 1280-dim embedding, plus rows covering
            // empty and corrupt legacy JSON.
            db.execSQL(
                "INSERT INTO wallpaper_metadata VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    "good", "https://example.com/good.jpg", "https://example.com/t.jpg",
                    "github", "minimal", "[\"#282828\"]", 42, 55,
                    converters.fromFloatArray(goodEmbedding),
                    "1920x1080", null, 7.5f, "[\"calm\"]", "[\"gradient\"]"
                )
            )
            db.execSQL(
                "INSERT INTO wallpaper_metadata VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    "empty", "https://example.com/e.jpg", "https://example.com/t.jpg",
                    "bing", "nature", "[\"#cc241d\"]", 70, 40,
                    "[]", "3840x2160", "Bing", 0f, "[]", "[]"
                )
            )
            db.execSQL(
                "INSERT INTO wallpaper_metadata VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    "corrupt", "https://example.com/c.jpg", "https://example.com/t.jpg",
                    "vanderwaals", "abstract", "[]", 10, 10,
                    "not valid json", "2560x1440", null, 0f, "[]", "[]"
                )
            )

            db.execSQL(
                "INSERT INTO taste_anchors VALUES(?,?,?,?,?)",
                arrayOf<Any?>("liked1", "like", converters.fromFloatArray(goodAnchor), 1_700_000_000_000L, 1.0f)
            )
            db.execSQL(
                "INSERT INTO taste_anchors VALUES(?,?,?,?,?)",
                arrayOf<Any?>("disliked1", "dislike", "[]", 1_699_000_000_000L, 0.4f)
            )

            db.execSQL(
                "INSERT INTO user_preferences VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>(
                    1, "personalized",
                    converters.fromFloatArray(prefVector),
                    converters.fromFloatArray(originalEmbedding),
                    "[]",
                    "[\"good\"]", "[\"disliked1\"]", 12, 0.1f, 1_700_000_100_000L,
                    "{\"calm\":0.5}", "{}"
                )
            )

            // Unrelated table must be untouched by the migration.
            db.execSQL(
                "INSERT INTO wallpaper_history (wallpaperId, appliedAt, removedAt, userFeedback, downloadedToStorage) VALUES(?,?,?,?,?)",
                arrayOf<Any?>("good", 1_700_000_050_000L, 1_700_000_060_000L, "LIKE", 0)
            )
        }

        // Runs MIGRATION_12_13 and validates the resulting schema against
        // the generated v13 schema — fails on any drift.
        helper.runMigrationsAndValidate(TEST_DB, 13, true, migration).use { db ->

            // Embeddings survived, bit-exact.
            db.query("SELECT embedding FROM wallpaper_metadata WHERE id = 'good'").use { c ->
                assertTrue(c.moveToFirst())
                assertArrayEquals(goodEmbedding, converters.blobToFloatArray(c.getBlob(0)), 0f)
            }
            // Empty and corrupt legacy values degrade to empty embeddings.
            db.query("SELECT embedding FROM wallpaper_metadata WHERE id IN ('empty','corrupt')").use { c ->
                while (c.moveToNext()) assertEquals(0, c.getBlob(0).size)
            }
            assertEquals(3, db.query("SELECT COUNT(*) FROM wallpaper_metadata").use { it.moveToFirst(); it.getInt(0) })

            db.query("SELECT kind, embedding, strength FROM taste_anchors ORDER BY updatedAt DESC").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("like", c.getString(0))
                assertArrayEquals(goodAnchor, converters.blobToFloatArray(c.getBlob(1)), 0f)
                assertEquals(1.0f, c.getFloat(2), 0f)
                assertTrue(c.moveToNext())
                assertEquals("dislike", c.getString(0))
                assertEquals(0, c.getBlob(1).size)
            }

            db.query("SELECT * FROM user_preferences").use { c ->
                assertTrue(c.moveToFirst())
                assertArrayEquals(prefVector, converters.blobToFloatArray(c.getBlob(c.getColumnIndexOrThrow("preferenceVector"))), 0f)
                assertArrayEquals(originalEmbedding, converters.blobToFloatArray(c.getBlob(c.getColumnIndexOrThrow("originalEmbedding"))), 0f)
                assertEquals(12, c.getInt(c.getColumnIndexOrThrow("feedbackCount")))
                assertEquals("{\"calm\":0.5}", c.getString(c.getColumnIndexOrThrow("moodAffinity")))
            }

            db.query("SELECT COUNT(*) FROM wallpaper_history").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
        }
    }

    @Test
    fun `migrates an empty database without onboarding data`() {
        helper.createDatabase(TEST_DB_EMPTY, 12).close()
        // No crash, schema still validates.
        helper.runMigrationsAndValidate(TEST_DB_EMPTY, 13, true, migration).close()
    }

    companion object {
        private const val TEST_DB = "migration-test.db"
        private const val TEST_DB_EMPTY = "migration-test-empty.db"
    }
}
