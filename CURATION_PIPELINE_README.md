# Vanderwaals automated curation pipeline

Implementation of the automated wallpaper curation system for Vanderwaals, processing 6,000+ wallpapers from 8 GitHub repositories using MobileNetV4 embeddings and GitHub Actions.

## Overview

The curation pipeline automates wallpaper indexing:

1. **Weekly scheduled runs** via GitHub Actions (Sundays 2 AM UTC)
2. **Indexes 8 repositories** containing 6,000+ wallpapers
3. **Extracts MobileNetV4 embeddings** (1280-dimensional feature vectors) for aesthetic matching
4. **Generates manifest.json** containing metadata, color palettes, and embeddings
5. **Delivers payloads via jsDelivr CDN**
6. **Integrates with the Android app** for catalog synchronization

## Project structure

```
Vanderwaals/
├── .github/workflows/
│   └── curate.yml                   # GitHub Actions workflow
├── scripts/
│   ├── curate_wallpapers.py            # Main curation script
│   ├── requirements.txt                 # Python dependencies
│   └── curation_output/                 # Output assets
│       ├── manifest.json                # Full manifest (~8 MB)
│       ├── manifest.json.gz             # Compressed manifest (~2 MB)
│       └── curation.log                 # Curation logs
├── app/src/main/assets/
│   └── manifest.json                    # Sample asset for local testing
└── docs/
    └── CURATION.md                      # Pipeline technical specification
```

## Quickstart

### 1. Prerequisites

- Python 3.10 or higher
- Git installed

### 2. Local testing

Run the curation pipeline locally in test mode:

```bash
cd scripts
pip install -r requirements.txt

# Run test mode (indexes 10 images per repository)
python curate_wallpapers.py --test

# Inspect generated outputs
ls -lh curation_output/
cat curation_output/curation.log
```

### 3. Workflow triggers

#### Scheduled runs
Executes automatically every Sunday at 2:00 AM UTC.

#### Manual execution
1. Open **Actions** on GitHub.
2. Select **Curate Wallpapers**.
3. Click **Run workflow**.
4. Toggle options (**Test mode** or **Skip release**) and submit.

Via GitHub CLI:
```bash
# Production run
gh workflow run curate.yml

# Test run
gh workflow run curate.yml -f test_mode=true
```

## Generated assets

### manifest.json

Sample JSON manifest structure:

```json
{
  "version": 3,
  "last_updated": "2026-01-16T02:00:00Z",
  "model_version": "mobilenet_v4_conv_small",
  "embedding_dim": 1280,
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
      "embedding": [0.234, -0.567, 0.123],
      "resolution": "2560x1440",
      "attribution": "dharmx/walls"
    }
  ]
}
```

**Payload sizes**:
- Uncompressed JSON: ~8 MB
- Compressed (gzip): ~2 MB

---

## CDN access

### jsDelivr CDN

Latest manifest from main branch:
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json
```

Specific release tag:
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@manifest-123/app/src/main/assets/manifest.json
```

Cache purge endpoint:
```bash
curl -X POST "https://purge.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json"
```

---

## Wallpaper sources

Indexed repositories:

| Repository | Wallpapers | Categories |
|------------|------------|------------|
| [dharmx/walls](https://github.com/dharmx/walls) | ~2,500 | 40+ categories (gruvbox, nord, nature, minimal) |
| [D3Ext/aesthetic-wallpapers](https://github.com/D3Ext/aesthetic-wallpapers) | ~1,200 | Aesthetic, anime, landscapes |
| [makccr/wallpapers](https://github.com/makccr/wallpapers) | ~800 | 4K collections |
| [michaelScopic/Wallpapers](https://github.com/michaelScopic/Wallpapers) | ~600 | Themed collections |
| [fr0st-iwnl/wallz](https://github.com/fr0st-iwnl/wallz) | ~500 | Modern, dark themes |
| [linuxdotexe/nordic-wallpapers](https://github.com/linuxdotexe/nordic-wallpapers) | ~400 | Nordic palette |
| [Mvcvalli/mobile-wallpapers](https://github.com/Mvcvalli/mobile-wallpapers) | ~300 | Mobile vertical formats |
| [DenverCoder1/minimalistic-wallpaper-collection](https://github.com/DenverCoder1/minimalistic-wallpaper-collection) | ~200 | Minimal designs |

---

## Technical processing details

### Feature extraction
- **Model**: MobileNetV4-Conv-Small
- **Vector dimension**: 1280 floats per image
- **Quantization**: Converted to int8 for binary compression in manifest output

### Color palette extraction
- **Method**: K-means clustering via `scikit-learn`
- **Output**: 5 dominant colors in hex format

### Deduplication
- **Method**: Perceptual hashing via `ImageHash`
- **Threshold**: Hamming distance $\le 5$ across repositories

---

## Validation and testing

### Local manifest verification

```bash
cd scripts/curation_output

# Validate JSON syntax
jq empty manifest.json

# Check total entry count
jq '.total_wallpapers' manifest.json

# Check embedding vector dimensions
jq '.wallpapers[0].embedding | length' manifest.json
```

---

## License and credits

- **Pipeline code**: AGPL-3.0 / Commercial dual license
- **Wallpaper images**: Subject to their respective original repository licenses
