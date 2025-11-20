package me.avinas.vanderwaals.network

/**
 * Retrofit API service for GitHub content fetching.
 * 
 * Provides endpoints for:
 * - Downloading manifest.json with wallpaper metadata
 * - Fetching wallpaper images from GitHub raw URLs or jsDelivr CDN
 * - Accessing GitHub community wallpaper collections
 * 
 * Base URLs:
 * - GitHub raw: https://raw.githubusercontent.com/[user]/[repo]/main/
 * - jsDelivr CDN: https://cdn.jsdelivr.net/gh/[user]/[repo]@main/
 * 
 * Rate limits:
 * - 5000 requests/hour per IP (distributed across users)
 * - No authentication required for public repos
 * - Each user downloads from their device's unique IP
 * 
 * @see me.avinas.vanderwaals.network.dto.ManifestDto
 * @see me.avinas.vanderwaals.domain.usecase.SyncWallpaperCatalogUseCase
 */
interface GitHubApiService {
    
}
