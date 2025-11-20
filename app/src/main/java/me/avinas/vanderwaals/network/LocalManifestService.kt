package me.avinas.vanderwaals.network

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import me.avinas.vanderwaals.network.dto.ManifestDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalManifestService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) {
    
    suspend fun getManifest(): ManifestDto {
        val json = context.assets.open("sample-manifest.json")
            .bufferedReader()
            .use { it.readText() }
        return gson.fromJson(json, ManifestDto::class.java)
    }
}
