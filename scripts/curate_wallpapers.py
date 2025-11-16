#!/usr/bin/env python3
"""
Vanderwaals Wallpaper Curation Pipeline
==========================================

Production-ready script optimized for GitHub Actions to process 6000+ wallpapers
from 8 GitHub repositories. Extracts MobileNetV3 embeddings, colors, and metadata
to generate manifest.json.

Features:
- Incremental processing (clone → process → delete → repeat)
- MobileNetV3-Small for 576-dim embeddings
- 5 dominant colors per wallpaper (Pillow)
- Brightness and contrast calculation
- Category detection from folder structure
- Perceptual hash deduplication (ImageHash)
- Resume capability with checkpoints
- Optimized for GitHub Actions (Ubuntu, 20GB space, 7GB RAM, 2 cores)

Usage:
    python curate_wallpapers.py [--test] [--resume]
    
    --test: Process only 10 images per repo (quick validation)
    --resume: Resume from last checkpoint (load intermediate results)
"""

import os
import sys
import json
import gzip
import shutil
import hashlib
import logging
import argparse
import tempfile
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Set
from datetime import datetime
from collections import defaultdict
import subprocess

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

# Wallpaper source repositories
# Each repo has a 'test_path' - a small folder for quick testing (sparse checkout)
REPOSITORIES = [
    {
        "url": "https://github.com/dharmx/walls",
        "branch": "main",
        "name": "dharmx/walls",
        "test_path": "animated"  # Small folder with ~20 images
    },
    {
        "url": "https://github.com/D3Ext/aesthetic-wallpapers",
        "branch": "main",
        "name": "D3Ext/aesthetic-wallpapers",
        "test_path": "images/anime"  # Smaller subfolder
    },
    {
        "url": "https://github.com/makccr/wallpapers",
        "branch": "master",
        "name": "makccr/wallpapers",
        "test_path": "Abstract"  # Small category folder
    },
    {
        "url": "https://github.com/michaelScopic/Wallpapers",
        "branch": "main",
        "name": "michaelScopic/Wallpapers",
        "test_path": "Minimal"  # Small folder
    },
    {
        "url": "https://github.com/fr0st-iwnl/wallz",
        "branch": "main",
        "name": "fr0st-iwnl/wallz",
        "test_path": "gruvbox"  # Theme-specific folder
    },
    {
        "url": "https://github.com/linuxdotexe/nordic-wallpapers",
        "branch": "master",
        "name": "linuxdotexe/nordic-wallpapers",
        "test_path": "wallpapers"  # Main folder (repo is already small)
    },
    {
        "url": "https://github.com/Mvcvalli/mobile-wallpapers",
        "branch": "main",
        "name": "Mvcvalli/mobile-wallpapers",
        "test_path": "."  # Root level (small repo with ~300 images)
    },
    {
        "url": "https://github.com/DenverCoder1/minimalistic-wallpaper-collection",
        "branch": "main",
        "name": "DenverCoder1/minimalistic-wallpaper-collection",
        "test_path": "images/minimalistic"  # Specific category
    }
]

# Output configuration
OUTPUT_DIR = Path("curation_output")
MANIFEST_PATH = OUTPUT_DIR / "manifest.json"
CHECKPOINT_PATH = OUTPUT_DIR / "checkpoint.json"

# Image processing configuration
SUPPORTED_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.webp'}
TARGET_SIZE = (224, 224)  # MobileNetV3 input size
MIN_RESOLUTION = (800, 600)  # Minimum wallpaper resolution
MAX_FILE_SIZE = 20 * 1024 * 1024  # 20 MB max file size

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
        logging.FileHandler(OUTPUT_DIR / 'curation.log', mode='a')
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
        
        # Load pre-trained MobileNetV3-Small (Google's weights)
        self.model = tf.keras.applications.MobileNetV3Small(
            input_shape=(224, 224, 3),
            include_top=False,  # Remove classification head
            weights='imagenet',  # Pre-trained on ImageNet
            pooling='avg'  # Global average pooling → 576-dim output
        )
        
        # Set to inference mode
        self.model.trainable = False
        
        logger.info(f"Model loaded: {self.model.output_shape[1]} dimensions")
    
    def extract(self, image_path: Path) -> Optional[np.ndarray]:
        """
        Extract embedding from image.
        
        Args:
            image_path: Path to image file
            
        Returns:
            576-dim numpy array, or None if extraction fails
        """
        try:
            # Load and preprocess image
            img = tf.keras.preprocessing.image.load_img(
                image_path,
                target_size=TARGET_SIZE
            )
            
            # Convert to array
            x = tf.keras.preprocessing.image.img_to_array(img)
            
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
            logger.warning(f"Failed to extract embedding from {image_path}: {e}")
            return None

# ============================================================================
# COLOR EXTRACTION
# ============================================================================

def extract_colors(image_path: Path, num_colors: int = 5) -> List[str]:
    """
    Extract dominant colors from image using k-means clustering.
    
    Args:
        image_path: Path to image file
        num_colors: Number of dominant colors to extract
        
    Returns:
        List of hex color strings (e.g., ['#282828', '#cc241d', ...])
    """
    try:
        # Load image and resize for faster processing
        img = Image.open(image_path).convert('RGB')
        img.thumbnail((200, 200))  # Reduce resolution for speed
        
        # Convert to numpy array
        pixels = np.array(img).reshape(-1, 3)
        
        # Use k-means to find dominant colors
        from sklearn.cluster import KMeans
        kmeans = KMeans(n_clusters=num_colors, random_state=42, n_init=10)
        kmeans.fit(pixels)
        
        # Get cluster centers (dominant colors)
        colors = kmeans.cluster_centers_.astype(int)
        
        # Convert to hex
        hex_colors = [f"#{r:02x}{g:02x}{b:02x}" for r, g, b in colors]
        
        return hex_colors
        
    except Exception as e:
        logger.warning(f"Failed to extract colors from {image_path}: {e}")
        return ['#000000'] * num_colors  # Default black

# ============================================================================
# IMAGE ANALYSIS
# ============================================================================

def calculate_brightness(image_path: Path) -> int:
    """
    Calculate perceived brightness (0-100).
    
    Uses ITU-R BT.601 luma coefficients.
    """
    try:
        img = Image.open(image_path).convert('RGB')
        img.thumbnail((100, 100))  # Reduce for speed
        pixels = np.array(img)
        
        # Calculate luma
        r, g, b = pixels[:,:,0], pixels[:,:,1], pixels[:,:,2]
        luma = 0.299 * r + 0.587 * g + 0.114 * b
        
        # Normalize to 0-100
        brightness = int(np.mean(luma) / 255 * 100)
        
        return brightness
        
    except Exception as e:
        logger.warning(f"Failed to calculate brightness for {image_path}: {e}")
        return 50  # Default medium brightness

def calculate_contrast(image_path: Path) -> int:
    """
    Calculate contrast (0-100) using standard deviation.
    """
    try:
        img = Image.open(image_path).convert('L')  # Grayscale
        img.thumbnail((100, 100))
        pixels = np.array(img)
        
        # Contrast = standard deviation
        contrast = int(np.std(pixels) / 127.5 * 100)
        
        return min(contrast, 100)
        
    except Exception as e:
        logger.warning(f"Failed to calculate contrast for {image_path}: {e}")
        return 50

def get_image_resolution(image_path: Path) -> str:
    """Get image resolution as 'WIDTHxHEIGHT'."""
    try:
        with Image.open(image_path) as img:
            return f"{img.width}x{img.height}"
    except Exception:
        return "unknown"

def compute_perceptual_hash(image_path: Path) -> str:
    """Compute perceptual hash for deduplication."""
    try:
        return str(imagehash.phash(Image.open(image_path)))
    except Exception as e:
        logger.warning(f"Failed to compute phash for {image_path}: {e}")
        return ""

# ============================================================================
# CATEGORY DETECTION
# ============================================================================

CATEGORY_KEYWORDS = {
    'nature': ['nature', 'landscape', 'forest', 'mountain', 'ocean', 'sunset', 'sunrise', 'sky'],
    'minimal': ['minimal', 'minimalist', 'simple', 'clean'],
    'dark': ['dark', 'black', 'night', 'moody'],
    'abstract': ['abstract', 'geometric', 'pattern', 'art'],
    'anime': ['anime', 'manga', 'waifu', 'character'],
    'gruvbox': ['gruvbox'],
    'nord': ['nord', 'nordic'],
    'city': ['city', 'urban', 'building', 'street'],
    'space': ['space', 'planet', 'galaxy', 'star', 'nebula'],
    'gradient': ['gradient', 'color'],
    'gaming': ['game', 'gaming', 'cyberpunk'],
}

def detect_category(file_path: Path, repo_name: str) -> str:
    """
    Detect category from folder structure and filename.
    
    Args:
        file_path: Path to image file
        repo_name: Name of source repository
        
    Returns:
        Category string (lowercase)
    """
    # Get path as string
    path_str = str(file_path).lower()
    
    # Check each category's keywords
    for category, keywords in CATEGORY_KEYWORDS.items():
        for keyword in keywords:
            if keyword in path_str:
                return category
    
    # Repo-specific defaults
    if 'minimal' in repo_name.lower():
        return 'minimal'
    elif 'nordic' in repo_name.lower():
        return 'nord'
    elif 'aesthetic' in repo_name.lower():
        return 'aesthetic'
    
    # Default
    return 'other'

# ============================================================================
# REPOSITORY PROCESSING
# ============================================================================

def clone_repository(repo: Dict, temp_dir: Path, test_mode: bool = False, max_retries: int = 3) -> Optional[Path]:
    """
    Clone a repository with shallow clone and retry logic.
    
    In test mode, uses git sparse-checkout to download ONLY the specific test_path folder,
    drastically reducing download size and time (e.g., 20MB instead of 2GB).
    
    Implements robust error handling with retries and proper timeouts to prevent
    hanging in CI/CD environments like GitHub Actions.
    
    Args:
        repo: Repository configuration dict with 'test_path' for sparse checkout
        temp_dir: Temporary directory for cloning
        test_mode: If True, use sparse checkout with test_path (60s timeout)
        max_retries: Number of retry attempts on failure
        
    Returns:
        Path to cloned repository, or None if failed
    """
    repo_name = repo['name'].replace('/', '_')
    repo_path = temp_dir / repo_name
    
    attempt = 0
    last_error = None
    
    while attempt < max_retries:
        attempt += 1
        
        try:
            # Sparse checkout for test mode
            if test_mode and 'test_path' in repo:
                logger.info(f"Cloning {repo['name']} (sparse: {repo['test_path']}) - attempt {attempt}/{max_retries}")
                
                try:
                    # Set environment to prevent credential prompts and hangs
                    git_env = os.environ.copy()
                    git_env['GIT_TERMINAL_PROMPT'] = '0'
                    git_env['GIT_ASKPASS'] = 'echo'
                    
                    # Step 1: Init sparse clone
                    init_cmd = [
                        'git', 'clone',
                        '--depth', '1',
                        '--filter=blob:none',
                        '--sparse',
                        '--single-branch',
                        '--branch', repo['branch'],
                        '--no-checkout',
                        repo['url'],
                        str(repo_path)
                    ]
                    result = subprocess.run(init_cmd, capture_output=True, text=True, timeout=30, env=git_env)
                    if result.returncode != 0:
                        raise Exception(f"Init failed: {result.stderr[:100]}")
                    
                    # Step 2: Configure sparse paths
                    sparse_cmd = ['git', '-C', str(repo_path), 'sparse-checkout', 'set', '--no-cone', repo['test_path']]
                    result = subprocess.run(sparse_cmd, capture_output=True, text=True, timeout=10, env=git_env)
                    if result.returncode != 0:
                        logger.warning(f"Sparse-checkout config failed: {result.stderr[:50]}")
                    
                    # Step 3: Checkout files
                    checkout_cmd = ['git', '-C', str(repo_path), 'checkout']
                    result = subprocess.run(checkout_cmd, capture_output=True, text=True, timeout=30, env=git_env)
                    if result.returncode != 0:
                        raise Exception(f"Checkout failed: {result.stderr[:100]}")
                    
                    logger.info(f"✓ Cloned {repo['name']} (sparse)")
                    return repo_path
                    
                except subprocess.TimeoutExpired:
                    last_error = "Timeout during sparse clone"
                    logger.warning(f"{repo['name']}: {last_error}")
                    if repo_path.exists():
                        shutil.rmtree(repo_path)
                    if attempt < max_retries:
                        import time
                        time.sleep(5)
                        continue
                    return None
                    
            else:
                # Full clone for production
                logger.info(f"Cloning {repo['name']} (full) - attempt {attempt}/{max_retries}")
                
                # Set environment to prevent credential prompts
                git_env = os.environ.copy()
                git_env['GIT_TERMINAL_PROMPT'] = '0'
                git_env['GIT_ASKPASS'] = 'echo'
                
                cmd = [
                    'git', 'clone',
                    '--depth', '1',
                    '--single-branch',
                    '--branch', repo['branch'],
                    repo['url'],
                    str(repo_path)
                ]
                
                try:
                    result = subprocess.run(cmd, capture_output=True, text=True, timeout=300, env=git_env)
                    
                    if result.returncode == 0:
                        logger.info(f"✓ Cloned {repo['name']}")
                        return repo_path
                    else:
                        error_msg = result.stderr[:200]
                        if 'Could not resolve host' in result.stderr or 'Connection' in result.stderr:
                            last_error = f"Network error: {error_msg}"
                            logger.warning(f"{repo['name']}: {last_error}")
                            if attempt < max_retries:
                                import time
                                time.sleep(5)
                                continue
                        else:
                            last_error = f"Clone failed: {error_msg}"
                            logger.error(f"{repo['name']}: {last_error}")
                            return None
                            
                except subprocess.TimeoutExpired:
                    last_error = "Timeout after 300s"
                    logger.warning(f"{repo['name']}: {last_error}")
                    if repo_path.exists():
                        shutil.rmtree(repo_path)
                    if attempt < max_retries:
                        import time
                        time.sleep(10)
                        continue
                    return None
                    
        except KeyboardInterrupt:
            logger.error(f"Clone interrupted for {repo['name']}")
            raise
        except Exception as e:
            last_error = f"Error: {str(e)[:100]}"
            logger.error(f"{repo['name']}: {last_error}")
            if repo_path.exists():
                shutil.rmtree(repo_path)
            if attempt < max_retries:
                import time
                time.sleep(5)
                continue
            return None
    
    # All retries exhausted
    logger.error(f"Failed to clone {repo['name']} after {max_retries} attempts. Last error: {last_error}")
    return None

def find_wallpapers(repo_path: Path) -> List[Path]:
    """Find all wallpaper images in repository."""
    wallpapers = []
    
    for ext in SUPPORTED_EXTENSIONS:
        wallpapers.extend(repo_path.rglob(f"*{ext}"))
    
    # Filter by file size and resolution
    valid_wallpapers = []
    for img_path in wallpapers:
        try:
            # Check file size
            if img_path.stat().st_size > MAX_FILE_SIZE:
                continue
            
            # Check resolution
            with Image.open(img_path) as img:
                if img.width >= MIN_RESOLUTION[0] and img.height >= MIN_RESOLUTION[1]:
                    valid_wallpapers.append(img_path)
        except Exception:
            continue
    
    return valid_wallpapers

def generate_wallpaper_id(repo_name: str, relative_path: str) -> str:
    """Generate unique wallpaper ID."""
    # Create hash from repo + path
    hash_input = f"{repo_name}/{relative_path}"
    hash_hex = hashlib.md5(hash_input.encode()).hexdigest()[:12]
    
    # Sanitize repo name
    repo_short = repo_name.split('/')[0].replace('-', '').replace('_', '')[:8]
    
    return f"{repo_short}_{hash_hex}"

def generate_cdn_url(repo_name: str, relative_path: str, branch: str = "main") -> str:
    """Generate jsDelivr CDN URL for wallpaper."""
    # Clean path
    clean_path = relative_path.replace('\\', '/').lstrip('/')
    
    # jsDelivr format: https://cdn.jsdelivr.net/gh/{owner}/{repo}@{branch}/{path}
    return f"https://cdn.jsdelivr.net/gh/{repo_name}@{branch}/{clean_path}"

# ============================================================================
# MAIN PROCESSING
# ============================================================================

def process_repository(
    repo: Dict,
    extractor: EmbeddingExtractor,
    processed_hashes: Set[str],
    test_mode: bool = False
) -> List[Dict]:
    """
    Process a single repository.
    
    Args:
        repo: Repository configuration
        extractor: MobileNetV3 embedding extractor
        processed_hashes: Set of already processed perceptual hashes
        test_mode: If True, process only 10 images
        
    Returns:
        List of wallpaper metadata dictionaries
    """
    wallpapers = []
    
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        
        # Clone repository (with sparse checkout in test mode)
        repo_path = clone_repository(repo, temp_path, test_mode=test_mode)
        if not repo_path:
            return wallpapers
        
        # Find wallpapers
        image_paths = find_wallpapers(repo_path)
        logger.info(f"Found {len(image_paths)} images in {repo['name']}")
        
        if test_mode:
            image_paths = image_paths[:10]
            logger.info(f"TEST MODE: Processing only {len(image_paths)} images")
        
        # Process each image
        for img_path in tqdm(image_paths, desc=f"Processing {repo['name']}"):
            try:
                # Compute perceptual hash for deduplication
                phash = compute_perceptual_hash(img_path)
                if not phash:
                    continue
                
                # Check for duplicates
                is_duplicate = False
                for existing_hash in processed_hashes:
                    if imagehash.hex_to_hash(phash) - imagehash.hex_to_hash(existing_hash) <= PHASH_THRESHOLD:
                        is_duplicate = True
                        break
                
                if is_duplicate:
                    continue
                
                # Extract embedding
                embedding = extractor.extract(img_path)
                if embedding is None:
                    continue
                
                # Extract colors
                colors = extract_colors(img_path)
                
                # Calculate metrics
                brightness = calculate_brightness(img_path)
                contrast = calculate_contrast(img_path)
                resolution = get_image_resolution(img_path)
                
                # Detect category
                relative_path = img_path.relative_to(repo_path)
                category = detect_category(relative_path, repo['name'])
                
                # Generate ID and URL
                wallpaper_id = generate_wallpaper_id(repo['name'], str(relative_path))
                cdn_url = generate_cdn_url(repo['name'], str(relative_path), repo['branch'])
                
                # Create metadata entry
                wallpaper_data = {
                    'id': wallpaper_id,
                    'url': cdn_url,
                    'thumbnail': cdn_url,  # Same as URL (jsDelivr handles resizing via query params if needed)
                    'source': 'github',
                    'repo': repo['name'],
                    'category': category,
                    'colors': colors,
                    'brightness': brightness,
                    'contrast': contrast,
                    'embedding': embedding.tolist(),
                    'resolution': resolution,
                    'attribution': repo['name']
                }
                
                wallpapers.append(wallpaper_data)
                processed_hashes.add(phash)
                
            except Exception as e:
                logger.warning(f"Failed to process {img_path}: {e}")
                continue
        
        logger.info(f"✓ Processed {len(wallpapers)} wallpapers from {repo['name']}")
    
    return wallpapers

def save_checkpoint(wallpapers: List[Dict], repo_index: int):
    """Save intermediate results to checkpoint file."""
    checkpoint_data = {
        'wallpapers': wallpapers,
        'last_repo_index': repo_index,
        'timestamp': datetime.utcnow().isoformat()
    }
    
    with open(CHECKPOINT_PATH, 'w') as f:
        json.dump(checkpoint_data, f)
    
    logger.info(f"✓ Saved checkpoint after repo {repo_index}")

def load_checkpoint() -> Tuple[List[Dict], int, Set[str]]:
    """
    Load checkpoint from previous run.
    
    Returns:
        (wallpapers, last_repo_index, processed_hashes)
    """
    if not CHECKPOINT_PATH.exists():
        return [], 0, set()
    
    try:
        with open(CHECKPOINT_PATH, 'r') as f:
            data = json.load(f)
        
        wallpapers = data.get('wallpapers', [])
        last_repo_index = data.get('last_repo_index', 0)
        
        # Rebuild hash set (approximate - using IDs as proxy)
        processed_hashes = set()
        
        logger.info(f"✓ Loaded checkpoint: {len(wallpapers)} wallpapers, resuming from repo {last_repo_index + 1}")
        
        return wallpapers, last_repo_index + 1, processed_hashes
        
    except Exception as e:
        logger.error(f"Failed to load checkpoint: {e}")
        return [], 0, set()

def generate_manifest(wallpapers: List[Dict]) -> Dict:
    """Generate final manifest.json structure."""
    manifest = {
        'version': 1,
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'model_version': 'mobilenet_v3_small',
        'embedding_dim': 576,
        'total_wallpapers': len(wallpapers),
        'wallpapers': wallpapers
    }
    
    return manifest

def save_manifest(manifest: Dict):
    """Save manifest.json and compressed version."""
    # Save uncompressed JSON
    with open(MANIFEST_PATH, 'w') as f:
        json.dump(manifest, f, indent=2)
    
    file_size_mb = MANIFEST_PATH.stat().st_size / (1024 * 1024)
    logger.info(f"✓ Saved manifest.json ({file_size_mb:.2f} MB)")
    
    # Save compressed version
    compressed_path = MANIFEST_PATH.with_suffix('.json.gz')
    with open(MANIFEST_PATH, 'rb') as f_in:
        with gzip.open(compressed_path, 'wb') as f_out:
            shutil.copyfileobj(f_in, f_out)
    
    compressed_size_mb = compressed_path.stat().st_size / (1024 * 1024)
    logger.info(f"✓ Saved manifest.json.gz ({compressed_size_mb:.2f} MB)")
    
    # Print statistics
    print("\n" + "="*60)
    print("CURATION COMPLETE")
    print("="*60)
    print(f"Total wallpapers: {manifest['total_wallpapers']}")
    print(f"Manifest size: {file_size_mb:.2f} MB")
    print(f"Compressed size: {compressed_size_mb:.2f} MB")
    print(f"Compression ratio: {file_size_mb/compressed_size_mb:.1f}x")
    
    # Category breakdown
    categories = defaultdict(int)
    for w in manifest['wallpapers']:
        categories[w['category']] += 1
    
    print("\nCategory breakdown:")
    for cat, count in sorted(categories.items(), key=lambda x: x[1], reverse=True):
        print(f"  {cat}: {count}")
    
    print("="*60 + "\n")

# ============================================================================
# MAIN
# ============================================================================

def main():
    """Main execution function."""
    parser = argparse.ArgumentParser(description='Curate wallpapers from GitHub repositories')
    parser.add_argument('--test', action='store_true', help='Test mode: process only 10 images per repo')
    parser.add_argument('--resume', action='store_true', help='Resume from last checkpoint')
    args = parser.parse_args()
    
    # Create output directory
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    logger.info("="*60)
    logger.info("VANDERWAALS WALLPAPER CURATION PIPELINE")
    logger.info("="*60)
    
    if args.test:
        logger.info("TEST MODE ENABLED: Processing 10 images per repo")
    
    # Initialize embedding extractor
    extractor = EmbeddingExtractor()
    
    # Load checkpoint if resuming
    if args.resume:
        all_wallpapers, start_index, processed_hashes = load_checkpoint()
    else:
        all_wallpapers = []
        start_index = 0
        processed_hashes = set()
    
    # Process repositories incrementally
    for idx, repo in enumerate(REPOSITORIES[start_index:], start=start_index):
        logger.info(f"\n[{idx + 1}/{len(REPOSITORIES)}] Processing {repo['name']}...")
        
        try:
            # Process repository
            wallpapers = process_repository(repo, extractor, processed_hashes, test_mode=args.test)
            all_wallpapers.extend(wallpapers)
            
            # Save checkpoint after each repo
            save_checkpoint(all_wallpapers, idx)
            
        except Exception as e:
            logger.error(f"Failed to process {repo['name']}: {e}")
            continue
    
    # Generate and save final manifest
    logger.info("\nGenerating final manifest...")
    manifest = generate_manifest(all_wallpapers)
    save_manifest(manifest)
    
    # Cleanup checkpoint
    if CHECKPOINT_PATH.exists():
        CHECKPOINT_PATH.unlink()
    
    logger.info("✓ Curation pipeline complete!")

if __name__ == '__main__':
    main()
