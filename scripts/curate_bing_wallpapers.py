#!/usr/bin/env python3
"""
Vanderwaals Bing Wallpaper Curation Pipeline
=============================================

Curates wallpapers from the Bing Wallpaper Archive (npanuhin/Bing-Wallpaper-Archive).
Generates TWO manifest files:
  - bing_manifest_lite.json: Last 2 years (~700 wallpapers, ~2MB)
  - bing_manifest_full.json: Full archive (~5400+ wallpapers, ~15MB)

Features:
- Fetches from bing.npanuhin.me API (year-specific endpoints)
- MobileNetV3-Small for 576-dim embeddings
- 5 dominant colors per wallpaper (K-means)
- Brightness and contrast calculation
- Perceptual hash deduplication
- Resume capability with checkpoints
- Optimized for GitHub Actions

Usage:
    python curate_bing_wallpapers.py [--test] [--resume] [--lite-only] [--full-only]
    
    --test: Process only 30 images (quick validation)
    --resume: Resume from last checkpoint
    --lite-only: Generate only the lite manifest (last 2 years)
    --full-only: Generate only the full manifest
"""

import os
import sys
import json
import gzip
import hashlib
import logging
import argparse
import tempfile
import time
import requests
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Set
from datetime import datetime
from collections import defaultdict
from io import BytesIO

# Suppress TensorFlow verbosity
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
os.environ['TF_ENABLE_ONEDNN_OPTS'] = '0'

import numpy as np
from PIL import Image
from tqdm import tqdm
import imagehash

# Import TensorFlow after setting env vars
import tensorflow as tf
tf.get_logger().setLevel('ERROR')

# ============================================================================
# CONFIGURATION
# ============================================================================

# Bing API configuration
BING_API_BASE = "https://bing.npanuhin.me"
DEFAULT_REGION = "US"
DEFAULT_LANGUAGE = "en"

# Years to include
CURRENT_YEAR = datetime.now().year
LITE_YEARS = 3  # Last 3 years for lite version
FULL_START_YEAR = 2009  # Bing wallpapers started in 2009

# Output configuration
OUTPUT_DIR = Path("curation_output")
MANIFEST_LITE_PATH = OUTPUT_DIR / "bing_manifest_lite.json"
MANIFEST_FULL_PATH = OUTPUT_DIR / "bing_manifest_full.json"
CHECKPOINT_PATH = OUTPUT_DIR / "bing_checkpoint.json"

# Image processing configuration
TARGET_SIZE = (224, 224)  # MobileNetV3 input size
MIN_RESOLUTION = (800, 600)  # Minimum wallpaper resolution

# Deduplication configuration
PHASH_THRESHOLD = 5  # Hamming distance for perceptual hash

# Create output directory if it doesn't exist
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(OUTPUT_DIR / 'bing_curation.log', mode='a')
    ]
)
logger = logging.getLogger(__name__)

# ============================================================================
# MOBILENET MODEL
# ============================================================================

class EmbeddingExtractor:
    """Extract 576-dimensional embeddings using MobileNetV3-Small."""
    
    def __init__(self):
        """Initialize MobileNetV3-Small model."""
        logger.info("Loading MobileNetV3-Small model...")
        
        self.model = tf.keras.applications.MobileNetV3Small(
            input_shape=(224, 224, 3),
            include_top=False,
            weights='imagenet',
            pooling='avg'
        )
        
        self.model.trainable = False
        logger.info(f"Model loaded: {self.model.output_shape[1]} dimensions")
    
    def extract(self, image: Image.Image) -> Optional[np.ndarray]:
        """Extract embedding from PIL Image."""
        try:
            # Resize and convert to RGB
            img = image.convert('RGB').resize(TARGET_SIZE, Image.Resampling.LANCZOS)
            
            # Convert to array
            x = np.array(img, dtype=np.float32)
            
            # Preprocess for MobileNetV3
            x = tf.keras.applications.mobilenet_v3.preprocess_input(x)
            
            # Add batch dimension
            x = np.expand_dims(x, axis=0)
            
            # Extract embedding
            embedding = self.model.predict(x, verbose=0)[0]
            
            # Normalize to unit length
            embedding = embedding / np.linalg.norm(embedding)
            
            return embedding
            
        except Exception as e:
            logger.warning(f"Failed to extract embedding: {e}")
            return None

# ============================================================================
# COLOR EXTRACTION
# ============================================================================

def extract_colors(image: Image.Image, num_colors: int = 5) -> List[str]:
    """Extract dominant colors from image using k-means clustering."""
    try:
        img = image.convert('RGB')
        img.thumbnail((200, 200))
        
        pixels = np.array(img).reshape(-1, 3)
        
        from sklearn.cluster import KMeans
        kmeans = KMeans(n_clusters=num_colors, random_state=42, n_init=10)
        kmeans.fit(pixels)
        
        colors = kmeans.cluster_centers_.astype(int)
        hex_colors = [f"#{r:02x}{g:02x}{b:02x}" for r, g, b in colors]
        
        return hex_colors
        
    except Exception as e:
        logger.warning(f"Failed to extract colors: {e}")
        return ['#000000'] * num_colors

# ============================================================================
# IMAGE ANALYSIS
# ============================================================================

def calculate_brightness(image: Image.Image) -> int:
    """Calculate perceived brightness (0-100)."""
    try:
        img = image.convert('RGB')
        img.thumbnail((100, 100))
        pixels = np.array(img)
        
        r, g, b = pixels[:,:,0], pixels[:,:,1], pixels[:,:,2]
        luma = 0.299 * r + 0.587 * g + 0.114 * b
        brightness = int(np.mean(luma) / 255 * 100)
        
        return brightness
    except Exception:
        return 50

def calculate_contrast(image: Image.Image) -> int:
    """Calculate contrast (0-100)."""
    try:
        img = image.convert('L')
        img.thumbnail((100, 100))
        pixels = np.array(img)
        
        contrast = int(np.std(pixels) / 127.5 * 100)
        return min(contrast, 100)
    except Exception:
        return 50

def get_image_resolution(image: Image.Image) -> str:
    """Get image resolution as 'WIDTHxHEIGHT'."""
    return f"{image.width}x{image.height}"

def compute_perceptual_hash(image: Image.Image) -> str:
    """Compute perceptual hash for deduplication."""
    try:
        return str(imagehash.phash(image))
    except Exception:
        return ""

def is_duplicate(phash: str, processed_hashes: Set[str], threshold: int = PHASH_THRESHOLD) -> bool:
    """Check if image is duplicate."""
    if not phash:
        return False
    
    try:
        hash_obj = imagehash.hex_to_hash(phash)
    except Exception:
        return False
    
    if phash in processed_hashes:
        return True
    
    if threshold > 0:
        for existing_hash_str in processed_hashes:
            try:
                existing_hash = imagehash.hex_to_hash(existing_hash_str)
                if hash_obj - existing_hash <= threshold:
                    return True
            except Exception:
                continue
    
    return False

# ============================================================================
# CATEGORY DETECTION
# ============================================================================

CATEGORY_KEYWORDS = {
    'nature': ['nature', 'landscape', 'forest', 'mountain', 'ocean', 'sunset', 'sunrise', 'sky', 'lake', 'river', 'waterfall', 'tree', 'flower', 'beach', 'island'],
    'animals': ['animal', 'bird', 'fish', 'wildlife', 'dog', 'cat', 'bear', 'whale', 'elephant', 'lion', 'tiger', 'penguin', 'butterfly', 'horse'],
    'architecture': ['architecture', 'building', 'castle', 'church', 'temple', 'bridge', 'tower', 'cathedral', 'palace', 'monument', 'lighthouse'],
    'city': ['city', 'urban', 'street', 'skyline', 'downtown'],
    'space': ['space', 'planet', 'galaxy', 'star', 'nebula', 'moon', 'eclipse', 'aurora'],
    'abstract': ['abstract', 'pattern', 'geometric', 'art', 'colorful'],
    'festival': ['festival', 'holiday', 'celebration', 'christmas', 'halloween', 'easter', 'carnival'],
    'winter': ['winter', 'snow', 'ice', 'frozen', 'cold'],
    'autumn': ['autumn', 'fall', 'leaves', 'harvest'],
    'spring': ['spring', 'blossom', 'bloom', 'tulip', 'cherry'],
    'summer': ['summer', 'beach', 'tropical', 'sunny'],
}

def detect_category(title: str, description: str) -> str:
    """Detect category from title and description."""
    text = f"{title} {description}".lower()
    
    # Count matches for each category
    scores = {}
    for category, keywords in CATEGORY_KEYWORDS.items():
        score = sum(1 for keyword in keywords if keyword in text)
        if score > 0:
            scores[category] = score
    
    if scores:
        return max(scores, key=scores.get)
    
    return 'nature'  # Default for Bing wallpapers

# ============================================================================
# BING API CLIENT
# ============================================================================

class BingApiClient:
    """Client for fetching wallpapers from Bing Wallpaper Archive."""
    
    def __init__(self, region: str = DEFAULT_REGION, language: str = DEFAULT_LANGUAGE):
        self.region = region
        self.language = language
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Vanderwaals-Curation/1.0'
        })
    
    def fetch_year_data(self, year: int) -> List[Dict]:
        """Fetch wallpapers for a specific year."""
        url = f"{BING_API_BASE}/{self.region}/{self.language}.{year}.json"
        
        try:
            response = self.session.get(url, timeout=30)
            if response.status_code == 404:
                logger.info(f"No data for year {year}")
                return []
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.warning(f"Failed to fetch year {year}: {e}")
            return []
    
    def fetch_all_years(self, start_year: int, end_year: int) -> List[Dict]:
        """Fetch wallpapers for a range of years."""
        all_wallpapers = []
        
        for year in range(start_year, end_year + 1):
            data = self.fetch_year_data(year)
            all_wallpapers.extend(data)
            logger.info(f"Fetched {len(data)} wallpapers from {year}")
        
        return all_wallpapers
    
    def download_image(self, url: str) -> Optional[Image.Image]:
        """Download image from URL."""
        try:
            response = self.session.get(url, timeout=60)
            response.raise_for_status()
            return Image.open(BytesIO(response.content))
        except Exception as e:
            logger.warning(f"Failed to download {url}: {e}")
            return None

# ============================================================================
# WALLPAPER PROCESSOR
# ============================================================================

class WallpaperProcessor:
    """Process wallpapers and extract metadata."""
    
    def __init__(self, extractor: EmbeddingExtractor, client: BingApiClient):
        self.extractor = extractor
        self.client = client
        self.processed_hashes: Set[str] = set()
    
    def process_wallpaper(self, data: Dict) -> Optional[Dict]:
        """Process a single wallpaper entry."""
        url = data.get('url')
        if not url:
            return None
        
        # Download image
        image = self.client.download_image(url)
        if image is None:
            return None
        
        # Check minimum resolution
        if image.width < MIN_RESOLUTION[0] or image.height < MIN_RESOLUTION[1]:
            logger.debug(f"Skipping low-res image: {image.width}x{image.height}")
            return None
        
        # Check for duplicates
        phash = compute_perceptual_hash(image)
        if is_duplicate(phash, self.processed_hashes):
            logger.debug(f"Skipping duplicate: {url}")
            return None
        
        # Add to processed hashes
        if phash:
            self.processed_hashes.add(phash)
        
        # Extract embedding
        embedding = self.extractor.extract(image)
        if embedding is None:
            return None
        
        # Extract colors
        colors = extract_colors(image)
        
        # Calculate brightness and contrast
        brightness = calculate_brightness(image)
        contrast = calculate_contrast(image)
        
        # Get resolution
        resolution = get_image_resolution(image)
        
        # Build description
        title = data.get('title') or ''
        caption = data.get('caption') or ''
        subtitle = data.get('subtitle') or ''
        description = data.get('description') or ''
        copyright_info = data.get('copyright') or ''
        
        full_description = '\n'.join(filter(None, [title, caption, subtitle, description, copyright_info]))
        
        # Detect category
        category = detect_category(title, description)
        
        # Generate ID
        date_str = data.get('date', '')
        wallpaper_id = f"bing_{date_str}_{self.client.region}_{self.client.language}"
        
        # Quantize embedding for smaller file size (same as main curation)
        # Scale to [-127, 127] and store as int8 values in list
        quantized = np.clip(embedding * 127, -127, 127).astype(np.int8).tolist()
        
        return {
            'id': wallpaper_id,
            'url': url,
            'title': title,
            'description': full_description[:500] if full_description else '',  # Limit description length
            'category': category,
            'resolution': resolution,
            'embedding': quantized,
            'colors': colors,
            'brightness': brightness,
            'contrast': contrast,
            'source': 'bing',
            'date': date_str
        }

# ============================================================================
# MANIFEST GENERATION
# ============================================================================

def generate_manifest(wallpapers: List[Dict], output_path: Path, manifest_type: str):
    """Generate manifest file."""
    manifest = {
        'version': '1.0.0',
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'model_version': 'mobilenet_v3_small',
        'embedding_dim': 576,
        'embedding_quantized': True,
        'embedding_scale': 127,
        'total_wallpapers': len(wallpapers),
        'source': 'bing',
        'manifest_type': manifest_type,
        'wallpapers': wallpapers
    }
    
    # Save uncompressed
    with open(output_path, 'w') as f:
        json.dump(manifest, f, separators=(',', ':'))
    
    # Save compressed
    gz_path = output_path.with_suffix('.json.gz')
    with gzip.open(gz_path, 'wt', encoding='utf-8') as f:
        json.dump(manifest, f, separators=(',', ':'))
    
    logger.info(f"Generated {manifest_type} manifest: {len(wallpapers)} wallpapers")
    logger.info(f"  Uncompressed: {output_path.stat().st_size / (1024*1024):.2f} MB")
    logger.info(f"  Compressed: {gz_path.stat().st_size / (1024*1024):.2f} MB")

# ============================================================================
# CHECKPOINT MANAGEMENT
# ============================================================================

def save_checkpoint(processed: List[Dict], year: int, index: int):
    """Save checkpoint for resume."""
    checkpoint = {
        'processed': processed,
        'year': year,
        'index': index,
        'timestamp': datetime.utcnow().isoformat()
    }
    with open(CHECKPOINT_PATH, 'w') as f:
        json.dump(checkpoint, f)

def load_checkpoint() -> Optional[Dict]:
    """Load checkpoint if exists."""
    if CHECKPOINT_PATH.exists():
        with open(CHECKPOINT_PATH, 'r') as f:
            return json.load(f)
    return None

def clear_checkpoint():
    """Remove checkpoint file."""
    if CHECKPOINT_PATH.exists():
        CHECKPOINT_PATH.unlink()

# ============================================================================
# MAIN
# ============================================================================

def main():
    parser = argparse.ArgumentParser(description='Curate Bing Wallpapers')
    parser.add_argument('--test', action='store_true', help='Test mode (30 images only)')
    parser.add_argument('--resume', action='store_true', help='Resume from checkpoint')
    parser.add_argument('--lite-only', action='store_true', help='Generate only lite manifest')
    parser.add_argument('--full-only', action='store_true', help='Generate only full manifest')
    args = parser.parse_args()
    
    logger.info("=" * 60)
    logger.info("BING WALLPAPER CURATION PIPELINE")
    logger.info("=" * 60)
    
    # Initialize components
    extractor = EmbeddingExtractor()
    client = BingApiClient()
    processor = WallpaperProcessor(extractor, client)
    
    # Determine years to fetch
    lite_start_year = CURRENT_YEAR - LITE_YEARS + 1
    full_start_year = FULL_START_YEAR
    
    # Fetch wallpaper data
    if args.full_only:
        logger.info(f"Fetching full archive: {full_start_year} to {CURRENT_YEAR}")
        all_data = client.fetch_all_years(full_start_year, CURRENT_YEAR)
    elif args.lite_only:
        logger.info(f"Fetching lite archive: {lite_start_year} to {CURRENT_YEAR}")
        all_data = client.fetch_all_years(lite_start_year, CURRENT_YEAR)
    else:
        logger.info(f"Fetching full archive for both manifests: {full_start_year} to {CURRENT_YEAR}")
        all_data = client.fetch_all_years(full_start_year, CURRENT_YEAR)
    
    # Sort by date (newest first for consistency)
    all_data.sort(key=lambda x: x.get('date', ''), reverse=True)
    
    # Limit for test mode
    if args.test:
        logger.info("TEST MODE: Processing only 30 images")
        all_data = all_data[:30]
    
    logger.info(f"Total wallpapers to process: {len(all_data)}")
    
    # Resume from checkpoint if requested
    processed_wallpapers = []
    start_index = 0
    if args.resume:
        checkpoint = load_checkpoint()
        if checkpoint:
            processed_wallpapers = checkpoint.get('processed', [])
            start_index = checkpoint.get('index', 0)
            logger.info(f"Resumed from checkpoint: {len(processed_wallpapers)} already processed")
    
    # Process wallpapers
    logger.info("Processing wallpapers...")
    failed = 0
    duplicates = 0
    
    for i, data in enumerate(tqdm(all_data[start_index:], desc="Processing", initial=start_index, total=len(all_data))):
        actual_index = start_index + i
        
        result = processor.process_wallpaper(data)
        
        if result:
            processed_wallpapers.append(result)
        else:
            failed += 1
        
        # Save checkpoint every 100 images
        if (actual_index + 1) % 100 == 0:
            save_checkpoint(processed_wallpapers, CURRENT_YEAR, actual_index + 1)
    
    logger.info(f"Processing complete: {len(processed_wallpapers)} successful, {failed} failed")
    
    # Generate manifests
    if not args.full_only:
        # Filter for lite manifest (last 2 years)
        lite_cutoff = f"{lite_start_year}-01-01"
        lite_wallpapers = [w for w in processed_wallpapers if w.get('date', '') >= lite_cutoff]
        generate_manifest(lite_wallpapers, MANIFEST_LITE_PATH, 'lite')
    
    if not args.lite_only:
        generate_manifest(processed_wallpapers, MANIFEST_FULL_PATH, 'full')
    
    # Clear checkpoint on success
    clear_checkpoint()
    
    logger.info("=" * 60)
    logger.info("CURATION COMPLETE")
    logger.info("=" * 60)

if __name__ == '__main__':
    main()
