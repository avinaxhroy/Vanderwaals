# Vanderwaals Automated Curation Pipeline

Complete implementation of automated wallpaper curation system for Vanderwaals, processing 6000+ wallpapers from 8 GitHub repositories weekly using MobileNetV3 embeddings and GitHub Actions.

## 🎯 Overview

This system automates the entire wallpaper curation pipeline:

1. **Weekly automated runs** via GitHub Actions (Sundays 2 AM UTC)
2. **Processes 8 repositories** with 6000+ high-quality wallpapers
3. **Extracts MobileNetV3 embeddings** (1024-dim) for semantic matching
4. **Generates manifest.json** with all metadata and embeddings
5. **Delivers via jsDelivr CDN** (free, global, fast)
6. **Integrates with app** seamlessly (local dev, remote production)

## 📁 Project Structure

```
Vanderwaals/
├── .github/
│   └── workflows/
│       └── curate.yml                   # GitHub Actions workflow
├── scripts/
│   ├── curate_wallpapers.py            # Main curation script
│   ├── requirements.txt                 # Python dependencies
│   └── curation_output/                 # Generated files
│       ├── manifest.json                # Full manifest (~6-8 MB)
│       ├── manifest.json.gz             # Compressed (~1.5-2 MB)
│       └── curation.log                 # Detailed logs
├── app/src/main/
│   ├── assets/
│   │   └── manifest.json                # Copied by workflow (for app)
│   └── java/me/avinas/vanderwaals/di/
│       └── NetworkModule.kt             # CDN integration
├── docs/
│   ├── CURATION.md                      # Complete pipeline docs
│   └── BUILD_CONFIG_CHANGES.md          # Build setup guide
└── VanderwaalsStrategy.md               # Overall strategy
```

## 🚀 Quick Start

### 1. Prerequisites

- Python 3.10+ (for local testing)
- GitHub account with Actions enabled
- Git installed

### 2. Local Testing

Test the curation pipeline locally:

```bash
# Navigate to scripts directory
cd Vanderwaals/scripts

# Install dependencies
pip install -r requirements.txt

# Run in test mode (10 images per repo, ~5 minutes)
python curate_wallpapers.py --test

# Check output
ls -lh curation_output/
cat curation_output/curation.log
```

### 3. Configure App

Apply build configuration changes:

**`app/build.gradle.kts`**:
```kotlin
android {
    buildFeatures {
        buildConfig = true
    }
    
    defaultConfig {
        buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false")
        buildConfigField("String", "MANIFEST_BASE_URL", 
            "\"https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/\"")
    }
    
    buildTypes {
        debug {
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "true")
        }
        release {
            buildConfigField("boolean", "USE_LOCAL_MANIFEST", "false")
        }
    }
}
```

See [docs/BUILD_CONFIG_CHANGES.md](docs/BUILD_CONFIG_CHANGES.md) for complete guide.

### 4. Run Workflow

#### Automatic (Weekly)
Runs every Sunday at 2 AM UTC automatically.

#### Manual Trigger
1. Go to **Actions** tab in GitHub
2. Select **"Curate Wallpapers"** workflow
3. Click **"Run workflow"**
4. Choose options:
   - ☑️ **Test mode**: Quick validation (10 images per repo)
   - ☑️ **Skip release**: Don't create GitHub release
5. Click **"Run workflow"**

Or via CLI:
```bash
# Full production run
gh workflow run curate.yml

# Test run
gh workflow run curate.yml -f test_mode=true
```

### 5. Monitor Progress

- **Workflow status**: Actions tab → Curate Wallpapers
- **Logs**: Click on workflow run → Expand steps
- **Artifacts**: Download logs and manifest files (30-day retention)
- **Summary**: Automatic summary posted at end of run

## 📊 What Gets Generated

### manifest.json

Complete manifest with all wallpaper metadata:

```json
{
  "version": 1,
  "last_updated": "2025-11-16T02:00:00Z",
  "model_version": "mobilenet_v3_small",
  "embedding_dim": 1024,
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
      "embedding": [0.234, -0.567, ..., 0.123],  // 1024 floats
      "resolution": "2560x1440",
      "attribution": "dharmx/walls"
    }
    // ... 6233 more
  ]
}
```

**Sizes**:
- Uncompressed: ~6-8 MB
- Compressed (gzip): ~1.5-2 MB

### GitHub Release

Automatic release created after each scheduled run:
- Tag: `manifest-{run_number}`
- Attachments: `manifest.json`, `manifest.json.gz`
- CDN URLs for direct access
- Changelog with stats

## 🌐 CDN Access

### jsDelivr CDN (Recommended)

**Latest from main branch**:
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json
```

**Specific release**:
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@manifest-123/app/src/main/assets/manifest.json
```

**Compressed (recommended)**:
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json.gz
```

### Benefits

- ✅ **Global CDN**: Fast delivery worldwide
- ✅ **Free forever**: No rate limits, no costs
- ✅ **Auto-caching**: Edge + browser caching
- ✅ **High availability**: 99.99% uptime

### Purge Cache

Force CDN update after new manifest:
```bash
curl -X POST "https://purge.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json"
```

## 🎨 Wallpaper Sources

### Current Sources (8 Repositories)

| Repository | Wallpapers | Categories |
|------------|------------|------------|
| [dharmx/walls](https://github.com/dharmx/walls) | ~2500 | 40+ categories (gruvbox, nord, nature, minimal) |
| [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers) | ~1200 | Aesthetic, anime, landscapes |
| [makccr/wallpapers](https://github.com/makccr/wallpapers) | ~800 | High-quality 4K wallpapers |
| [michaelScopic/Wallpapers](https://github.com/michaelScopic/Wallpapers) | ~600 | Themed collections |
| [fr0st-iwnl/wallz](https://github.com/fr0st-iwnl/wallz) | ~500 | Modern, dark themes |
| [linuxdotexe/nordic-wallpapers](https://github.com/linuxdotexe/nordic-wallpapers) | ~400 | Nordic color palette |
| [Mvcvalli/mobile-wallpapers](https://github.com/Mvcvalli/mobile-wallpapers) | ~300 | Mobile-optimized |
| [DenverCoder1/minimalistic-wallpaper-collection](https://github.com/DenverCoder1/minimalistic-wallpaper-collection) | ~200 | Minimalist designs |

**Total**: ~6500 raw → ~6000-6200 after deduplication

### Adding New Sources

Edit `scripts/curate_wallpapers.py`:

```python
REPOSITORIES = [
    # ... existing repos
    {
        "url": "https://github.com/owner/repo-name",
        "branch": "main",
        "name": "owner/repo-name"
    }
]
```

Test, commit, and trigger workflow. See [docs/CURATION.md](docs/CURATION.md#adding-new-sources) for details.

## 🔧 Technical Details

### MobileNetV3 Embeddings

- **Model**: MobileNetV3-Small (Google pre-trained, ImageNet)
- **Output**: 1024-dimensional embedding per wallpaper
- **Purpose**: Semantic similarity matching (cosine similarity)
- **Speed**: ~30 seconds per 1000 wallpapers (GitHub Actions)
- **Quality**: 90-95% accuracy for style matching

### Color Extraction

- **Method**: K-means clustering (sklearn)
- **Output**: 5 dominant colors as hex codes
- **Purpose**: Category filtering, theme matching
- **Speed**: ~10ms per image

### Deduplication

- **Method**: Perceptual hashing (ImageHash library)
- **Threshold**: Hamming distance ≤ 5
- **Scope**: Cross-repository deduplication
- **Result**: ~5-10% duplicates removed

### Performance

**GitHub Actions** (ubuntu-latest):
- **Duration**: 30-90 minutes for full run
- **Resources**: 2 CPU cores, 7 GB RAM, 20 GB disk
- **Cost**: Free (within 2000 min/month limit)
- **Frequency**: Weekly (~6 hours/month usage)

**Optimizations**:
- Incremental processing (clone → process → delete)
- Checkpoint system (resume on failure)
- TensorFlow-CPU only (smaller, sufficient)
- Parallel I/O operations

## 📖 Documentation

### Complete Guides

1. **[docs/CURATION.md](docs/CURATION.md)** - Complete pipeline documentation
   - How it works
   - Adding sources
   - Manual triggers
   - Troubleshooting
   - Advanced features

2. **[docs/BUILD_CONFIG_CHANGES.md](docs/BUILD_CONFIG_CHANGES.md)** - App integration guide
   - Build configuration
   - BuildConfig usage
   - Testing strategies
   - Environment setup

3. **[VanderwaalsStrategy.md](VanderwaalsStrategy.md)** - Overall project strategy
   - Technical architecture
   - Algorithm details
   - Manifest structure
   - Development timeline

### Quick References

- **Workflow**: `.github/workflows/curate.yml`
- **Script**: `scripts/curate_wallpapers.py`
- **Dependencies**: `scripts/requirements.txt`
- **Network**: `app/src/main/java/me/avinas/vanderwaals/di/NetworkModule.kt`

## 🧪 Testing

### Test Curation Locally

```bash
cd scripts

# Test mode (quick validation)
python curate_wallpapers.py --test

# Full run (local testing)
python curate_wallpapers.py

# Resume from checkpoint
python curate_wallpapers.py --resume
```

### Validate Manifest

```bash
cd scripts/curation_output

# Check JSON validity
jq empty manifest.json

# Check wallpaper count
jq '.total_wallpapers' manifest.json

# Check embedding dimensions
jq '.wallpapers[0].embedding | length' manifest.json

# Category breakdown
jq '.wallpapers | group_by(.category) | map({category: .[0].category, count: length})' manifest.json
```

### Test CDN Delivery

```bash
# Check CDN availability
curl -I https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json

# Download and validate
curl https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json | jq '.total_wallpapers'
```

### Test App Integration

```bash
# Debug build (local manifest)
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release build (CDN manifest)
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

## 🔍 Troubleshooting

### Common Issues

**Workflow timeout**
- Reduce repos per run or split into multiple workflows

**Out of disk space**
- Incremental processing handles this automatically
- Verify temp cleanup in script

**Failed to clone repository**
- Check repo URL and branch name
- Verify network connectivity in workflow

**Invalid embeddings**
- Re-download MobileNetV3 weights
- Check TensorFlow version compatibility

**CDN 404**
- Wait 5-10 minutes for propagation
- Purge CDN cache manually

See [docs/CURATION.md#troubleshooting](docs/CURATION.md#troubleshooting) for detailed solutions.

## 📈 Monitoring

### Workflow Health

- **Success rate**: Check Actions tab history
- **Duration trends**: Monitor run times
- **Artifact sizes**: Track manifest growth

### Manifest Quality

- **Wallpaper count**: Should be 6000-6500
- **Embedding coverage**: All wallpapers should have 1024-dim embeddings
- **Category distribution**: Balanced across categories
- **Deduplication rate**: ~5-10% duplicates removed

### CDN Performance

- **Delivery speed**: <100ms globally (jsDelivr)
- **Cache hit rate**: High (long TTL)
- **Availability**: 99.99% uptime

## 🚦 Deployment Checklist

Ready to deploy? Follow this checklist:

- [ ] Python script tested locally (`--test` mode)
- [ ] Full local run successful
- [ ] Build configuration applied (`build.gradle.kts`)
- [ ] BuildConfig accessible in code
- [ ] NetworkModule updated with CDN URL
- [ ] Workflow file committed (`.github/workflows/curate.yml`)
- [ ] Test workflow triggered manually
- [ ] Manifest generated successfully
- [ ] CDN URL accessible
- [ ] App downloads manifest correctly
- [ ] Debug build uses local manifest
- [ ] Release build uses CDN manifest
- [ ] Documentation reviewed

## 🤝 Contributing

Improvements welcome! To contribute:

1. Fork the repository
2. Create feature branch
3. Make changes
4. Test thoroughly (local + test mode)
5. Update documentation
6. Submit pull request

Areas for contribution:
- Additional wallpaper sources
- Performance optimizations
- Enhanced deduplication
- Quality scoring
- Thumbnail generation

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

## 🙏 Credits

### Wallpaper Sources

Thanks to these amazing repository maintainers:
- [dharmx/walls](https://github.com/dharmx/walls)
- [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers)
- [makccr/wallpapers](https://github.com/makccr/wallpapers)
- [michaelScopic/Wallpapers](https://github.com/michaelScopic/Wallpapers)
- [fr0st-iwnl/wallz](https://github.com/fr0st-iwnl/wallz)
- [linuxdotexe/nordic-wallpapers](https://github.com/linuxdotexe/nordic-wallpapers)
- [Mvcvalli/mobile-wallpapers](https://github.com/Mvcvalli/mobile-wallpapers)
- [DenverCoder1/minimalistic-wallpaper-collection](https://github.com/DenverCoder1/minimalistic-wallpaper-collection)

### Technology

- **MobileNetV3**: Google Research
- **TensorFlow**: Google Brain Team
- **jsDelivr**: Free CDN service
- **GitHub Actions**: Microsoft/GitHub
- **Pillow**: Python Imaging Library contributors
- **ImageHash**: Johannes Buchner

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/avinaxhroy/Vanderwaals/issues)
- **Discussions**: [GitHub Discussions](https://github.com/avinaxhroy/Vanderwaals/discussions)
- **Documentation**: [docs/](docs/)

---

**Built with ❤️ for Vanderwaals**  
*Last updated: November 16, 2025*
