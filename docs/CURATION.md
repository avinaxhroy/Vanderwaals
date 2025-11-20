# Wallpaper Curation Pipeline Documentation

## Overview

Vanderwaals uses an automated curation pipeline to process 6000+ wallpapers from community GitHub repositories. The pipeline runs weekly via GitHub Actions, extracting MobileNetV3 embeddings, colors, and metadata to generate a centralized `manifest.json` file.

## Architecture

### Pipeline Components

```
GitHub Repos (8 sources)
    ↓
GitHub Actions (weekly)
    ↓
Python Script (curate_wallpapers.py)
    ↓
manifest.json + embeddings
    ↓
jsDelivr CDN (delivery)
    ↓
Vanderwaals App (download)
```

### Data Flow

1. **Clone**: Shallow clone of each repository (depth=1)
2. **Process**: Extract embeddings, colors, metadata per wallpaper
3. **Deduplicate**: Perceptual hashing to remove duplicates
4. **Generate**: Create manifest.json with all metadata
5. **Commit**: Push manifest to repository
6. **Release**: Create GitHub release with artifacts
7. **Deliver**: Serve via jsDelivr CDN (global, fast, free)

## How It Works

### 1. MobileNetV3 Embeddings

**Pre-trained model from Google** (ImageNet weights):
- Model: MobileNetV3-Small with global average pooling
- Output: 576-dimensional embedding vector per wallpaper
- Purpose: Captures visual features (style, composition, mood)
- Speed: ~30 seconds per 1000 wallpapers on GitHub Actions

**Why embeddings?**
- Enable semantic similarity matching (cosine similarity)
- Support personalized recommendations
- Work across all image styles (no manual labeling)
- Pre-trained on 1M+ images (high quality)

### 2. Color Extraction

**K-means clustering** (sklearn):
- Extract 5 dominant colors per wallpaper
- Use downsampled image (200x200) for speed
- Output: Hex color codes (e.g., `["#282828", "#cc241d", ...]`)
- Purpose: Category filtering, theme matching, UI accents

### 3. Metadata Extraction

For each wallpaper:
- **Brightness**: ITU-R BT.601 luma (0-100)
- **Contrast**: Standard deviation (0-100)
- **Resolution**: Original dimensions (e.g., "2560x1440")
- **Category**: Auto-detected from folder structure
- **Source**: Repository name and attribution

### 4. Deduplication

**Perceptual hashing** (ImageHash library):
- Compute pHash for each wallpaper
- Compare Hamming distance between hashes
- Threshold: 5 (allows minor variations)
- Keeps first instance, removes duplicates across repos

### 5. Manifest Generation

**Structure** (see [VanderwaalsStrategy.md](../VanderwaalsStrategy.md)):

```json
{
  "version": 1,
  "last_updated": "2025-11-16T02:00:00Z",
  "model_version": "mobilenet_v3_small",
  "embedding_dim": 576,
  "total_wallpapers": 6234,
  "wallpapers": [
    {
      "id": "dharmx_abc123def456",
      "url": "https://cdn.jsdelivr.net/gh/dharmx/walls@main/gruvbox/001.jpg",
      "thumbnail": "https://cdn.jsdelivr.net/gh/dharmx/walls@main/gruvbox/001.jpg",
      "source": "github",
      "repo": "dharmx/walls",
      "category": "gruvbox",
      "colors": ["#282828", "#cc241d", "#98971a", "#d79921", "#458588"],
      "brightness": 35,
      "contrast": 68,
      "embedding": [0.234, -0.567, 0.891, ...],  // 576 floats
      "resolution": "2560x1440",
      "attribution": "dharmx/walls"
    }
    // ... 6233 more wallpapers
  ]
}
```

**Size**:
- Uncompressed: ~6-8 MB
- Gzipped: ~1.5-2 MB (delivered via CDN)

## Wallpaper Sources

### Current Sources (8 Repositories)

| Repository | Branch | Wallpapers | Categories |
|------------|--------|------------|------------|
| [dharmx/walls](https://github.com/dharmx/walls) | main | ~2500 | 40+ categories (gruvbox, nord, nature, minimal, etc.) |
| [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers) | main | ~1200 | Aesthetic, anime, landscapes |
| [makccr/wallpapers](https://github.com/makccr/wallpapers) | master | ~800 | High-quality 4K wallpapers |
| [michaelScopic/Wallpapers](https://github.com/michaelScopic/Wallpapers) | main | ~600 | Themed collections |
| [fr0st-iwnl/wallz](https://github.com/fr0st-iwnl/wallz) | main | ~500 | Modern, dark themes |
| [linuxdotexe/nordic-wallpapers](https://github.com/linuxdotexe/nordic-wallpapers) | master | ~400 | Nordic color palette |
| [Mvcvalli/mobile-wallpapers](https://github.com/Mvcvalli/mobile-wallpapers) | main | ~300 | Mobile-optimized |
| [DenverCoder1/minimalistic-wallpaper-collection](https://github.com/DenverCoder1/minimalistic-wallpaper-collection) | main | ~200 | Minimalist designs |

**Total**: ~6500 wallpapers (after deduplication: ~6000-6200)

### Adding New Sources

To add a new wallpaper repository:

1. **Edit** `scripts/curate_wallpapers.py`
2. **Add** to `REPOSITORIES` list:

```python
REPOSITORIES = [
    # ... existing repos
    {
        "url": "https://github.com/owner/repo-name",
        "branch": "main",  # or "master"
        "name": "owner/repo-name"
    }
]
```

3. **Test** locally:
```bash
cd scripts
python curate_wallpapers.py --test
```

4. **Commit** and push:
```bash
git add scripts/curate_wallpapers.py
git commit -m "feat: add new wallpaper source (owner/repo-name)"
git push
```

5. **Trigger** workflow manually to test full run

### Source Requirements

Good wallpaper repositories should have:
- ✅ **High resolution**: 1920x1080 minimum (prefer 4K)
- ✅ **Organized structure**: Folders by category/theme
- ✅ **Active maintenance**: Recent commits, responsive maintainer
- ✅ **Open license**: MIT, CC0, or equivalent
- ✅ **Quality content**: Curated, not random scrapes
- ✅ **Reasonable size**: <5000 images per repo (performance)

## GitHub Actions Workflow

### Automatic Schedule

**When**: Every Sunday at 2:00 AM UTC
**Duration**: 30-90 minutes (depending on new wallpapers)
**Trigger**: GitHub Actions cron schedule

### Manual Trigger

Trigger manually via GitHub UI:

1. Go to **Actions** tab in your repository
2. Select **"Curate Wallpapers"** workflow
3. Click **"Run workflow"** button
4. Configure options:
   - **Test mode**: Process only 10 images per repo (5 min run)
   - **Skip release**: Don't create GitHub release
5. Click **"Run workflow"**

Or via GitHub CLI:
```bash
# Full run
gh workflow run curate.yml

# Test run
gh workflow run curate.yml -f test_mode=true

# Skip release
gh workflow run curate.yml -f skip_release=true
```

### Workflow Steps

1. **Checkout**: Clone repository
2. **Setup Python**: Install Python 3.10 + dependencies
3. **Disk check**: Monitor available space
4. **Run curation**: Execute `curate_wallpapers.py`
5. **Validate**: Check manifest structure and embeddings
6. **Copy to assets**: Place manifest in `app/src/main/assets/`
7. **Commit**: Push updated manifest (with `[skip ci]`)
8. **Upload artifacts**: Save logs and files (30 days)
9. **Create release**: Tag release with manifest files
10. **Summary**: Post results to workflow summary

### Monitoring

**Check workflow status**:
- Actions tab: https://github.com/avinaxhroy/Vanderwaals/actions
- Email notifications (if enabled in GitHub settings)
- Workflow summary (detailed stats)

**Logs**:
- View in Actions → Workflow run → Step logs
- Download artifacts: `curation.log` (detailed Python logs)

**Failures**:
- Automatic retry (GitHub Actions default: 0 retries)
- Notification via workflow failure
- Check logs for specific error (clone, network, model, etc.)

## Hosting & Delivery

### jsDelivr CDN (Recommended)

**Advantages**:
- ✅ **Global CDN**: Fast delivery worldwide
- ✅ **Free forever**: No rate limits, no costs
- ✅ **Automatic caching**: Edge caching + browser caching
- ✅ **Version control**: Access specific commits/tags/branches
- ✅ **High availability**: 99.99% uptime SLA

**URL Format**:
```
https://cdn.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}
```

**Examples**:
```bash
# Latest from main branch
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json

# Specific release tag
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@manifest-123/app/src/main/assets/manifest.json

# Specific commit
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@a1b2c3d4/app/src/main/assets/manifest.json

# Compressed version (recommended)
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json.gz
```

**Purge cache** (force update):
```bash
# Purge specific file
curl -X POST "https://purge.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json"
```

### Alternative: GitHub Raw

**URL Format**:
```
https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}
```

**Example**:
```bash
https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/app/src/main/assets/manifest.json
```

**Limitations**:
- ❌ Rate limited: 5000 requests/hour per IP
- ❌ Slower: No global CDN
- ❌ No caching: Direct from GitHub servers

**Use case**: Fallback only if jsDelivr fails

### Alternative: GitHub Releases

Attach manifest to GitHub release:

**URL Format**:
```
https://github.com/{owner}/{repo}/releases/download/{tag}/{file}
```

**Example**:
```bash
https://github.com/avinaxhroy/Vanderwaals/releases/download/manifest-123/manifest.json.gz
```

**Advantages**:
- ✅ Immutable: Specific version never changes
- ✅ Documented: Release notes with metadata
- ✅ Downloadable: Users can manually download

**Disadvantages**:
- ❌ No CDN: Direct from GitHub
- ❌ Manual update: Need to update app with new release URL

## App Integration

### NetworkModule Configuration

Update `app/src/main/java/me/avinas/vanderwaals/di/NetworkModule.kt`:

```kotlin
object NetworkModule {
    
    // jsDelivr CDN base URL (primary)
    private const val JSDELIVR_BASE_URL = "https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/"
    
    // GitHub raw base URL (fallback)
    private const val GITHUB_RAW_BASE_URL = "https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/"
    
    // Manifest path
    private const val MANIFEST_PATH = "app/src/main/assets/manifest.json"
    
    @Provides
    @Singleton
    fun provideManifestUrl(
        @JsDelivrBaseUrl jsDelivrUrl: String,
        @GitHubRawBaseUrl githubRawUrl: String
    ): String {
        return if (BuildConfig.USE_LOCAL_MANIFEST) {
            "file:///android_asset/manifest.json"  // Local from assets (debug)
        } else {
            "$jsDelivrUrl$MANIFEST_PATH"  // Remote from CDN (release)
        }
    }
}
```

### Build Configuration

Add to `app/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        // ...
        
        buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false")
        buildConfigField("String", "MANIFEST_BASE_URL", "\"https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/\"")
    }
    
    buildTypes {
        debug {
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "true")  // Local manifest for debug
        }
        
        release {
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false")  // CDN manifest for release
        }
    }
}
```

### Usage in App

```kotlin
@Inject
lateinit var manifestService: ManifestService

suspend fun syncManifest() {
    try {
        // Download manifest from CDN
        val manifest = manifestService.getManifest()
        
        // Parse and store wallpapers
        database.wallpaperDao().insertAll(manifest.wallpapers)
        
        Log.d(TAG, "Synced ${manifest.wallpapers.size} wallpapers")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to sync manifest", e)
        // Fallback to cached manifest or local assets
    }
}
```

## Testing

### Local Testing

Test curation script locally:

```bash
# Test mode (10 images per repo, ~5 minutes)
cd scripts
python curate_wallpapers.py --test

# Full run (all images, ~30-60 minutes)
python curate_wallpapers.py

# Resume from checkpoint
python curate_wallpapers.py --resume
```

**Output**:
- `curation_output/manifest.json` - Full manifest
- `curation_output/manifest.json.gz` - Compressed
- `curation_output/curation.log` - Detailed logs
- `curation_output/checkpoint.json` - Resume state (deleted on success)

### Validation

Validate manifest structure:

```bash
cd scripts/curation_output

# Check JSON is valid
jq empty manifest.json

# Check total wallpapers
jq '.total_wallpapers' manifest.json

# Check embedding dimensions
jq '.wallpapers[0].embedding | length' manifest.json

# Check categories
jq '.wallpapers | group_by(.category) | map({category: .[0].category, count: length})' manifest.json

# Check file size
ls -lh manifest.json*
```

### CDN Testing

Test CDN delivery:

```bash
# Download manifest from CDN
curl -I https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json

# Check response headers (should include CDN cache headers)
curl -v https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json.gz -o /dev/null

# Download and validate
curl https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json | jq '.total_wallpapers'
```

## Performance

### GitHub Actions Limits

**Free tier** (sufficient for this project):
- Storage: 500 MB
- Minutes: 2000/month (workflow duration)
- Concurrent jobs: 20
- Job timeout: 6 hours max (we use 4 hours)

**Our usage**:
- ~90 minutes per weekly run
- ~360 minutes per month
- Well within free tier limits

### Optimization

**Incremental processing**:
- Clone one repo at a time
- Process wallpapers
- Delete repo before cloning next
- Saves disk space (GitHub Actions: 20 GB available)

**Checkpoint system**:
- Save progress after each repo
- Resume from last checkpoint if workflow fails
- Prevents re-processing completed repos

**TensorFlow optimization**:
- CPU-only version (smaller, sufficient)
- Suppress verbose logging
- Use all available cores (2 on GitHub Actions)

## Troubleshooting

### Common Issues

**1. Workflow timeout**
- Symptom: Workflow exceeds 4-hour timeout
- Solution: Process fewer repos per run, or split into multiple workflows

**2. Out of disk space**
- Symptom: "No space left on device"
- Solution: Reduce repo count, delete temp files more aggressively

**3. Memory error**
- Symptom: Python killed by OOM
- Solution: Process images in smaller batches, reduce batch size

**4. Failed to clone repository**
- Symptom: Git clone timeout or error
- Solution: Check repo URL, branch name, network connectivity

**5. Invalid embedding dimensions**
- Symptom: Validation fails with wrong embedding length
- Solution: Check MobileNetV3 model version, re-download weights

**6. CDN returns 404**
- Symptom: jsDelivr URL not found
- Solution: Wait 5-10 minutes for CDN propagation, or purge cache

### Debug Mode

Enable detailed logging:

```python
# In curate_wallpapers.py
logging.basicConfig(level=logging.DEBUG)  # Change from INFO to DEBUG
```

### Support

- **Issues**: https://github.com/avinaxhroy/Vanderwaals/issues
- **Discussions**: https://github.com/avinaxhroy/Vanderwaals/discussions
- **Strategy**: See [VanderwaalsStrategy.md](../VanderwaalsStrategy.md)

## Future Enhancements

### Planned Features

1. **Delta updates**: Only download changed wallpapers since last sync
2. **Quality scoring**: ML-based quality assessment (blur, artifacts)
3. **Thumbnail generation**: Pre-generated low-res previews
4. **Multi-resolution**: Generate multiple sizes (mobile, tablet, desktop)
5. **Video wallpapers**: Support for live wallpapers/videos
6. **User submissions**: Community-contributed wallpapers
7. **NSFW filtering**: Content moderation via ML

### Contributing

Want to improve the curation pipeline?

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly (local + test mode)
5. Submit a pull request

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

**Last updated**: November 16, 2025  
**Maintained by**: Vanderwaals Team  
**License**: MIT
