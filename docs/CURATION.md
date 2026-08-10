# Wallpaper curation pipeline documentation

Technical specification for the automated wallpaper curation and indexing pipeline.

## Overview

Vanderwaals indexes wallpapers from community GitHub repositories using an automated weekly pipeline. The workflow clones source repositories, extracts 1280-dimensional MobileNetV4 embeddings, detects dominant colors, deduplicates entries, and outputs a compressed `manifest.json` delivered over CDN.

---

## Pipeline architecture

```
GitHub Repositories (8 sources)
    ↓
GitHub Actions (weekly schedule)
    ↓
Python Script (curate_wallpapers.py)
    ↓
manifest.json + int8 embeddings
    ↓
jsDelivr CDN (distribution)
    ↓
Vanderwaals Android App (catalog sync)
```

---

## Data processing steps

### 1. MobileNetV4 embeddings
- **Model**: MobileNetV4-Conv-Small via PyTorch/TFLite
- **Vector length**: 1280 floats per image
- **Quantization**: Converted to int8 to reduce manifest download payload size from ~60 MB to ~8 MB

### 2. Color palette extraction
- **Method**: K-means clustering ($K=5$) on downsampled $200 \times 200$ images
- **Output**: 5 dominant hex color codes per wallpaper

### 3. Metadata calculation
- **Luma brightness**: ITU-R BT.601 calculation ($0\text{--}100$)
- **Contrast**: Standard deviation of pixel intensities
- **Dimensions**: Source width and height in pixels

### 4. Perceptual deduplication
- **Method**: Perceptual hashing (`ImageHash` pHash)
- **Threshold**: Hamming distance $\le 5$ across repositories
- **Action**: Retains original instance and drops duplicate copies

---

## Manifest payload schema

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
      "embedding": [12, -45, 89],
      "resolution": "2560x1440",
      "attribution": "dharmx/walls"
    }
  ]
}
```

---

## Distribution endpoints

### jsDelivr CDN
```
https://cdn.jsdelivr.net/gh/avinaxhroy/Vanderwaals@main/app/src/main/assets/manifest.json
```

### GitHub raw fallback
```
https://raw.githubusercontent.com/avinaxhroy/Vanderwaals/main/app/src/main/assets/manifest.json
```

---

## Source requirements

Candidate wallpaper repositories must meet the following criteria:
- **Resolution**: Minimum $1920 \times 1080$ pixels (higher preferred)
- **Structure**: Organized into category or theme subdirectories
- **License**: Compatible open license (MIT, CC0, or original author permission)
- **Content**: Visual artwork and photography without watermarks

---

Copyright © 2024–2025 Avinash Roy.
