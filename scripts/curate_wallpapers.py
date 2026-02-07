#!/usr/bin/env python3
"""
Vanderwaals Wallpaper Curation Pipeline
==========================================

Production-ready script optimized for GitHub Actions to process 6000+ wallpapers
from 8 GitHub repositories. Extracts MobileNetV4-Conv-Small embeddings, colors, and metadata
to generate manifest_v3.json.

Features:
- SMART INCREMENTAL UPDATES: Only process repos with new commits (via GitHub API)
- AUTO-ADAPTIVE REPO DETECTION: Automatically finds wallpaper directories
- OPTIMIZED MANIFEST: Quantized embeddings for 80%+ size reduction
- MobileNetV4-Conv-Small for 1280-dim embeddings (via timm)
- 5 dominant colors per wallpaper (Pillow)
- Brightness and contrast calculation
- Category detection from folder structure
- Perceptual hash deduplication (ImageHash)
- Resume capability with checkpoints
- Optimized for GitHub Actions (Ubuntu, 20GB space, 7GB RAM, 2 cores)

Usage:
    python curate_wallpapers.py [--test] [--resume] [--full] [--no-quantize]
    
    --test: Process only 10 images per repo (quick validation)
    --resume: Resume from last checkpoint (load intermediate results)
    --full: Force full re-curation (ignore incremental update detection)
    --no-quantize: Use full float32 embeddings (larger manifest)
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
import signal
import atexit
import time
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Set
from datetime import datetime
from collections import defaultdict
import subprocess

import numpy as np
from PIL import Image
from tqdm import tqdm
import imagehash

# PyTorch and timm for MobileNetV4-Conv-Small embeddings
import torch
import timm
from timm.data import resolve_data_config
from timm.data.transforms_factory import create_transform

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
MANIFEST_PATH = OUTPUT_DIR / "manifest_v3.json"  # v3 format with MobileNetV4 1280D embeddings
CHECKPOINT_PATH = OUTPUT_DIR / "checkpoint.json"
UPDATE_TRACKER_PATH = OUTPUT_DIR / "update_tracker.json"  # For incremental updates
PREVIOUS_MANIFEST_PATH = OUTPUT_DIR / "manifest_v3_previous.json"  # Cache for incremental merge

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
# RESOURCE MANAGER
# ============================================================================

class ResourceManager:
    """Manage global resources and cleanup."""
    
    def __init__(self):
        self.temp_dirs = []
        self.signal_received = False
    
    def register_signal_handlers(self):
        """Register handlers for graceful shutdown."""
        signal.signal(signal.SIGINT, self._signal_handler)
        signal.signal(signal.SIGTERM, self._signal_handler)
        atexit.register(self.cleanup)
    
    def _signal_handler(self, signum, frame):
        """Handle shutdown signals."""
        logger.warning(f"Received signal {signum}, initiating graceful shutdown...")
        self.signal_received = True
        self.cleanup()
        sys.exit(130 if signum == signal.SIGINT else 143)
    
    def cleanup(self):
        """Clean up all resources."""
        if hasattr(self, '_cleaned') and self._cleaned:
            return
        
        logger.info("Cleaning up resources...")
        
        # Clean up temp directories
        for temp_dir in self.temp_dirs:
            if temp_dir.exists():
                try:
                    shutil.rmtree(temp_dir)
                    logger.debug(f"Removed temp directory: {temp_dir}")
                except Exception as e:
                    logger.warning(f"Failed to remove temp directory {temp_dir}: {e}")
        
        # Clear TensorFlow session
        try:
            # Clear any GPU memory if using CUDA
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
            logger.debug("Cleared PyTorch resources")
        except Exception as e:
            logger.debug(f"PyTorch cleanup: {e}")
        
        self._cleaned = True

# Global resource manager
resource_manager = ResourceManager()

# ============================================================================
# VALIDATION
# ============================================================================

def validate_configuration() -> List[str]:
    """Validate script configuration before processing.
    
    Returns:
        List of validation error messages (empty if valid)
    """
    errors = []
    
    # Validate repositories configuration
    if not REPOSITORIES:
        errors.append("No repositories configured")
        return errors
    
    for idx, repo in enumerate(REPOSITORIES):
        required_keys = ['url', 'branch', 'name']
        for key in required_keys:
            if key not in repo:
                errors.append(f"Repository {idx} missing required key: {key}")
        
        # Validate URL format
        if 'url' in repo and not repo['url'].startswith(('https://', 'git@')):
            errors.append(f"Repository {idx} has invalid URL: {repo['url']}")
        
        # Validate name format
        if 'name' in repo and '/' not in repo['name']:
            errors.append(f"Repository {idx} name should be 'owner/repo': {repo['name']}")
    
    # Validate file size limits
    if MAX_FILE_SIZE <= 0:
        errors.append(f"MAX_FILE_SIZE must be positive: {MAX_FILE_SIZE}")
    
    # Validate resolution
    if len(MIN_RESOLUTION) != 2 or any(x <= 0 for x in MIN_RESOLUTION):
        errors.append(f"MIN_RESOLUTION must be (width, height) with positive values: {MIN_RESOLUTION}")
    
    # Check disk space
    try:
        stat = os.statvfs(str(OUTPUT_DIR))
        free_space_gb = (stat.f_bavail * stat.f_frsize) / (1024**3)
        if free_space_gb < 2:  # Require at least 2GB free
            errors.append(f"Insufficient disk space: {free_space_gb:.1f}GB free (need 2GB minimum)")
    except Exception as e:
        logger.warning(f"Could not check disk space: {e}")
    
    return errors

def validate_manifest(manifest: Dict) -> List[str]:
    """Validate manifest structure before saving.
    
    Returns:
        List of validation error messages (empty if valid)
    """
    errors = []
    
    required_keys = ['version', 'last_updated', 'model_version', 'embedding_dim', 'total_wallpapers', 'wallpapers']
    for key in required_keys:
        if key not in manifest:
            errors.append(f"Manifest missing required key: {key}")
    
    # Validate wallpapers
    if 'wallpapers' in manifest:
        if not isinstance(manifest['wallpapers'], list):
            errors.append("Manifest 'wallpapers' must be a list")
        elif len(manifest['wallpapers']) == 0:
            errors.append("Manifest has no wallpapers")
        else:
            # Validate first wallpaper as sample
            sample = manifest['wallpapers'][0]
            required_wp_keys = ['id', 'url', 'category', 'colors', 'brightness', 'contrast']
            for key in required_wp_keys:
                if key not in sample:
                    errors.append(f"Wallpaper missing required key: {key}")
            
            # Validate embedding (either full or quantized)
            if 'embedding' not in sample and 'e' not in sample:
                errors.append("Wallpaper must have either 'embedding' or 'e'")
            
            # Validate embedding dimension (only for full embeddings)
            if 'embedding' in sample:
                if not isinstance(sample['embedding'], list):
                    errors.append("Embedding must be a list")
                elif len(sample['embedding']) != 1280:
                    errors.append(f"Embedding has wrong dimension: {len(sample['embedding'])} (expected 1280)")
    
    # Validate count matches
    if 'total_wallpapers' in manifest and 'wallpapers' in manifest:
        if manifest['total_wallpapers'] != len(manifest['wallpapers']):
            errors.append(f"total_wallpapers ({manifest['total_wallpapers']}) doesn't match "
                        f"actual count ({len(manifest['wallpapers'])})")
    
    return errors

# ============================================================================
# MOBILENET MODEL
# ============================================================================

class EmbeddingExtractor:
    """Extract 1280-dimensional embeddings using MobileNetV4-Conv-Small via timm."""
    
    def __init__(self):
        """Initialize MobileNetV4-Conv-Small model."""
        logger.info("Loading MobileNetV4-Conv-Small model...")
        
        self.device = torch.device('cpu')  # Use CPU for GitHub Actions compatibility
        
        # Load pre-trained MobileNetV4-Conv-Small from timm
        # num_classes=0 removes classifier head and returns 1280D embedding
        # The model architecture: 960 channels → 1x1 projection → 1280D
        self.model = timm.create_model(
            'mobilenetv4_conv_small.e2400_r224_in1k',
            pretrained=True,
            num_classes=0  # Remove classifier, get 1280D embedding
        )
        self.model.eval()
        self.model.to(self.device)
        
        # Get preprocessing transform from timm (handles normalization correctly)
        self.config = resolve_data_config({}, model=self.model)
        self.transform = create_transform(**self.config)
        
        logger.info("Model loaded: 1280 dimensions")
    
    @torch.no_grad()
    def extract(self, image_path: Path) -> Optional[np.ndarray]:
        """
        Extract embedding from image.
        
        Args:
            image_path: Path to image file
            
        Returns:
            1280-dim numpy array, or None if extraction fails
        """
        try:
            # Load image and convert to RGB
            img = Image.open(image_path).convert('RGB')
            
            # Apply timm preprocessing transform
            input_tensor = self.transform(img).unsqueeze(0).to(self.device)
            
            # Extract embedding
            embedding = self.model(input_tensor).squeeze().cpu().numpy()
            
            # Normalize to unit length
            norm = np.linalg.norm(embedding)
            if norm > 0:
                embedding = embedding / norm
            
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
        # Load image and resize for faster processing with proper resource management
        with Image.open(image_path) as img:
            img = img.convert('RGB')
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
        with Image.open(image_path) as img:
            img = img.convert('RGB')
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
        with Image.open(image_path) as img:
            img = img.convert('L')  # Grayscale
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
        with Image.open(image_path) as img:
            return str(imagehash.phash(img))
    except Exception as e:
        logger.warning(f"Failed to compute phash for {image_path}: {e}")
        return ""

def is_duplicate(phash: str, processed_hashes: Set[str], threshold: int = PHASH_THRESHOLD) -> bool:
    """Check if image is duplicate with optimized comparison.
    
    Args:
        phash: Perceptual hash to check
        processed_hashes: Set of already processed hashes
        threshold: Hamming distance threshold
        
    Returns:
        True if duplicate found
    """
    if not phash:
        return False
    
    try:
        hash_obj = imagehash.hex_to_hash(phash)
    except Exception as e:
        logger.warning(f"Failed to parse phash {phash}: {e}")
        return False
    
    # Use set lookup for exact matches (O(1))
    if phash in processed_hashes:
        return True
    
    # Only do expensive comparison for near-duplicates if threshold > 0
    if threshold > 0:
        for existing_hash_str in processed_hashes:
            try:
                existing_hash = imagehash.hex_to_hash(existing_hash_str)
                if hash_obj - existing_hash <= threshold:
                    return True
            except Exception as e:
                logger.debug(f"Failed to compare hashes: {e}")
                continue
    
    return False

# ============================================================================
# PROGRESS TRACKING
# ============================================================================

class ProgressTracker:
    """Track processing progress and statistics."""
    
    def __init__(self):
        self.stats = {
            'repos_processed': 0,
            'repos_failed': 0,
            'images_found': 0,
            'images_processed': 0,
            'images_failed': 0,
            'duplicates_found': 0,
            'total_size_bytes': 0,
            'start_time': time.time(),
            'repo_times': []
        }
        self.current_repo = None
        self.current_repo_start = 0
    
    def record_repo_start(self, repo_name: str, image_count: int):
        """Record start of repository processing."""
        self.current_repo = repo_name
        self.current_repo_start = time.time()
        self.stats['images_found'] += image_count
    
    def record_repo_complete(self, images_processed: int, duplicates: int):
        """Record completion of repository processing."""
        elapsed = time.time() - self.current_repo_start
        self.stats['repos_processed'] += 1
        self.stats['images_processed'] += images_processed
        self.stats['duplicates_found'] += duplicates
        self.stats['repo_times'].append((self.current_repo, elapsed))
        
        rate = images_processed / elapsed if elapsed > 0 else 0
        logger.info(f"Repo stats: {images_processed} processed, {duplicates} duplicates, "
                   f"{elapsed:.1f}s elapsed, {rate:.1f} images/sec")
    
    def record_repo_failed(self, repo_name: str, error: str):
        """Record failed repository."""
        self.stats['repos_failed'] += 1
        logger.error(f"Repository {repo_name} failed: {error}")
    
    def record_image_failed(self):
        """Record failed image processing."""
        self.stats['images_failed'] += 1
    
    def print_summary(self):
        """Print final processing summary."""
        elapsed_total = time.time() - self.stats['start_time']
        
        print("\n" + "="*60)
        print("PROCESSING SUMMARY")
        print("="*60)
        print(f"Total time: {elapsed_total/60:.1f} minutes")
        print(f"Repositories: {self.stats['repos_processed']} processed, "
              f"{self.stats['repos_failed']} failed")
        print(f"Images: {self.stats['images_found']} found, "
              f"{self.stats['images_processed']} processed, "
              f"{self.stats['images_failed']} failed, "
              f"{self.stats['duplicates_found']} duplicates")
        
        if self.stats['images_processed'] > 0:
            rate = self.stats['images_processed'] / elapsed_total
            success_rate = (self.stats['images_processed'] / self.stats['images_found']) * 100
            print(f"Processing rate: {rate:.1f} images/sec")
            print(f"Success rate: {success_rate:.1f}%")
        
        if self.stats['repo_times']:
            print(f"\nSlowest repositories:")
            sorted_times = sorted(self.stats['repo_times'], key=lambda x: x[1], reverse=True)[:3]
            for repo, elapsed in sorted_times:
                print(f"  {repo}: {elapsed:.1f}s")
        print("="*60 + "\n")

# ============================================================================
# REPOSITORY PROCESSING
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
    Auto-adaptive category detection from folder structure and filename.
    
    Uses intelligent path analysis to extract categories without manual configuration:
    1. First checks immediate parent folder name against known categories
    2. Falls back to keyword matching in full path
    3. Uses repo name as last resort
    
    Args:
        file_path: Path to image file (relative to repo root)
        repo_name: Name of source repository
        
    Returns:
        Category string (lowercase)
    """
    # Get path parts for analysis
    path_str = str(file_path).lower()
    parts = [p.lower() for p in file_path.parts]
    
    # Strategy 1: Check immediate parent folder (most reliable)
    if len(parts) >= 2:
        parent_folder = parts[-2]  # Immediate parent of the file
        
        # Direct match with known categories
        if parent_folder in CATEGORY_KEYWORDS:
            return parent_folder
        
        # Check if parent folder contains category keyword
        for category, keywords in CATEGORY_KEYWORDS.items():
            for keyword in keywords:
                if keyword in parent_folder:
                    return category
    
    # Strategy 2: Check grandparent folder (for nested structures like images/nature/...)
    if len(parts) >= 3:
        grandparent = parts[-3]
        for category, keywords in CATEGORY_KEYWORDS.items():
            for keyword in keywords:
                if keyword in grandparent:
                    return category
    
    # Strategy 3: Check full path for any keywords
    for category, keywords in CATEGORY_KEYWORDS.items():
        for keyword in keywords:
            if keyword in path_str:
                return category
    
    # Strategy 4: Derive from repo name
    repo_lower = repo_name.lower()
    if 'minimal' in repo_lower:
        return 'minimal'
    elif 'nordic' in repo_lower:
        return 'nord'
    elif 'aesthetic' in repo_lower:
        return 'aesthetic'
    elif 'mobile' in repo_lower:
        return 'mobile'
    elif 'anime' in repo_lower:
        return 'anime'
    
    # Strategy 5: Use parent folder name directly if it looks like a category
    # (not a generic name like 'images' or 'wallpapers')
    generic_names = {'images', 'wallpapers', 'walls', 'pics', 'pictures', 'src', 'assets'}
    if len(parts) >= 2:
        parent = parts[-2]
        if parent not in generic_names and len(parent) > 2:
            return parent
    
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

def find_wallpapers(repo_path: Path, specific_files: Optional[List[str]] = None) -> List[Path]:
    """
    Find all wallpaper images in repository with auto-adaptive detection.
    
    This function automatically adapts to different repository structures:
    - Detects common wallpaper directories (images/, wallpapers/, walls/, etc.)
    - Handles nested folder structures
    - Filters out documentation and non-wallpaper directories
    
    Args:
        repo_path: Path to cloned repository
        specific_files: Optional list of specific file paths to check (for incremental updates)
        
    Returns:
        List of valid wallpaper image paths
    """
    # Common directories to skip (not wallpaper content)
    SKIP_DIRS = {'.git', '.github', 'node_modules', '__pycache__', 'scripts', 
                  'docs', 'documentation', '.vscode', '.idea', 'thumbnails',
                  'thumbs', 'preview', 'previews', 'samples', 'readme_assets'}
    
    wallpapers = []
    
    if specific_files:
        # Incremental mode: only check specified files
        for file_path in specific_files:
            full_path = repo_path / file_path
            if full_path.exists() and full_path.suffix.lower() in SUPPORTED_EXTENSIONS:
                wallpapers.append(full_path)
    else:
        # Full scan mode: auto-detect wallpaper locations
        # First, try to find obvious wallpaper directories
        wallpaper_dirs = []
        
        for candidate in repo_path.iterdir():
            if not candidate.is_dir():
                continue
            
            name_lower = candidate.name.lower()
            
            # Skip non-content directories
            if name_lower in SKIP_DIRS or name_lower.startswith('.'):
                continue
            
            # Check if this looks like a content directory
            wallpaper_dirs.append(candidate)
        
        # If no obvious content dirs, scan from root
        if not wallpaper_dirs:
            wallpaper_dirs = [repo_path]
        
        # Scan each directory for images
        for scan_dir in wallpaper_dirs:
            for ext in SUPPORTED_EXTENSIONS:
                wallpapers.extend(scan_dir.rglob(f"*{ext}"))
    
    # Filter by file size and resolution
    valid_wallpapers = []
    for img_path in wallpapers:
        try:
            # Skip if in a skip directory
            if any(part.lower() in SKIP_DIRS for part in img_path.parts):
                continue
            
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
    test_mode: bool = False,
    tracker: Optional[ProgressTracker] = None
) -> Tuple[List[Dict], int]:
    """
    Process a single repository.
    
    Args:
        repo: Repository configuration
        extractor: MobileNetV3 embedding extractor
        processed_hashes: Set of already processed perceptual hashes
        test_mode: If True, process only 10 images
        tracker: Progress tracker for statistics
        
    Returns:
        Tuple of (wallpaper metadata list, duplicate count)
    """
    wallpapers = []
    duplicates_found = 0
    
    with tempfile.TemporaryDirectory() as temp_dir:
        temp_path = Path(temp_dir)
        
        # Clone repository (with sparse checkout in test mode)
        repo_path = clone_repository(repo, temp_path, test_mode=test_mode)
        if not repo_path:
            if tracker:
                tracker.record_repo_failed(repo['name'], "Clone failed")
            return wallpapers, duplicates_found
        
        # Find wallpapers
        image_paths = find_wallpapers(repo_path)
        logger.info(f"Found {len(image_paths)} images in {repo['name']}")
        
        if test_mode:
            image_paths = image_paths[:10]
            logger.info(f"TEST MODE: Processing only {len(image_paths)} images")
        
        # Record start with tracker
        if tracker:
            tracker.record_repo_start(repo['name'], len(image_paths))
        
        # Process each image
        for img_path in tqdm(image_paths, desc=f"Processing {repo['name']}"):
            try:
                # Compute perceptual hash for deduplication
                phash = compute_perceptual_hash(img_path)
                if not phash:
                    if tracker:
                        tracker.record_image_failed()
                    continue
                
                # Check for duplicates using optimized function
                if is_duplicate(phash, processed_hashes):
                    duplicates_found += 1
                    continue
                
                # Extract embedding
                embedding = extractor.extract(img_path)
                if embedding is None:
                    if tracker:
                        tracker.record_image_failed()
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
                    'attribution': repo['name'],
                    'phash': phash  # Store for checkpoint recovery
                }
                
                wallpapers.append(wallpaper_data)
                processed_hashes.add(phash)
                
            except Exception as e:
                logger.warning(f"Failed to process {img_path}: {e}")
                if tracker:
                    tracker.record_image_failed()
                continue
        
        logger.info(f"✓ Processed {len(wallpapers)} wallpapers from {repo['name']} ({duplicates_found} duplicates)")
        
        # Record completion with tracker
        if tracker:
            tracker.record_repo_complete(len(wallpapers), duplicates_found)
    
    return wallpapers, duplicates_found

def save_checkpoint(wallpapers: List[Dict], repo_index: int, processed_hashes: Set[str]):
    """Save intermediate results to checkpoint file with hash tracking."""
    checkpoint_data = {
        'wallpapers': wallpapers,
        'last_repo_index': repo_index,
        'processed_hashes': list(processed_hashes),  # Store hash set for resume
        'timestamp': datetime.utcnow().isoformat()
    }
    
    try:
        with open(CHECKPOINT_PATH, 'w') as f:
            json.dump(checkpoint_data, f)
        logger.info(f"✓ Saved checkpoint after repo {repo_index} ({len(processed_hashes)} hashes)")
    except Exception as e:
        logger.error(f"Failed to save checkpoint: {e}")

def load_checkpoint() -> Tuple[List[Dict], int, Set[str]]:
    """
    Load checkpoint from previous run with validation.
    
    Returns:
        (wallpapers, last_repo_index, processed_hashes)
    """
    if not CHECKPOINT_PATH.exists():
        return [], 0, set()
    
    try:
        with open(CHECKPOINT_PATH, 'r') as f:
            data = json.load(f)
        
        # Validate checkpoint structure
        required_keys = ['wallpapers', 'last_repo_index', 'timestamp']
        if not all(key in data for key in required_keys):
            logger.warning("Checkpoint file is missing required keys, starting fresh")
            return [], 0, set()
        
        wallpapers = data.get('wallpapers', [])
        last_repo_index = data.get('last_repo_index', 0)
        
        # Validate wallpapers structure
        if wallpapers and not isinstance(wallpapers, list):
            logger.warning("Checkpoint wallpapers is not a list, starting fresh")
            return [], 0, set()
        
        # Rebuild hash set from checkpoint (new: support both old and new format)
        processed_hashes = set(data.get('processed_hashes', []))
        
        # Fallback: rebuild from wallpaper metadata if not in checkpoint
        if not processed_hashes and wallpapers:
            for wp in wallpapers:
                if 'phash' in wp and wp['phash']:
                    processed_hashes.add(wp['phash'])
        
        logger.info(f"✓ Loaded checkpoint: {len(wallpapers)} wallpapers, "
                   f"{len(processed_hashes)} hashes, resuming from repo {last_repo_index + 1}")
        
        return wallpapers, last_repo_index + 1, processed_hashes
        
    except json.JSONDecodeError as e:
        logger.error(f"Checkpoint file is corrupted (JSON decode failed): {e}")
        logger.info("Starting fresh (checkpoint ignored)")
        return [], 0, set()
    except Exception as e:
        logger.error(f"Failed to load checkpoint: {e}")
        logger.info("Starting fresh (checkpoint ignored)")
        return [], 0, set()

def generate_manifest(wallpapers: List[Dict], use_quantization: bool = True) -> Dict:
    """
    Generate final manifest.json structure with optional embedding quantization.
    
    Args:
        wallpapers: List of wallpaper metadata dicts
        use_quantization: If True, quantize embeddings for smaller manifest size
        
    Returns:
        Manifest dictionary ready for JSON serialization
    """
    processed_wallpapers = wallpapers
    
    # Apply quantization if enabled
    if use_quantization and wallpapers:
        logger.info("Quantizing embeddings for size optimization...")
        
        try:
            from embedding_quantizer import EmbeddingQuantizer
            quantizer = EmbeddingQuantizer(verify_quality=True)
            
            processed_wallpapers = []
            for wp in tqdm(wallpapers, desc="Quantizing"):
                wp_copy = wp.copy()
                
                # Get embedding (handle both list and numpy array)
                embedding = wp.get('embedding', [])
                if isinstance(embedding, list):
                    embedding = np.array(embedding, dtype=np.float32)
                
                # Quantize
                quantized = quantizer.quantize(embedding)
                
                # Replace embedding with quantized format
                # Keep original embedding key for backward compatibility
                wp_copy['e'] = quantized['e']
                wp_copy['eMin'] = quantized.get('eMin')
                wp_copy['eMax'] = quantized.get('eMax')
                
                # Remove phash (internal use only, not needed in manifest)
                wp_copy.pop('phash', None)
                
                # Keep original embedding for now (can be removed later for full optimization)
                # Uncomment next line to fully remove original embedding:
                wp_copy.pop('embedding', None)
                
                processed_wallpapers.append(wp_copy)
            
            # Log quality stats
            quality = quantizer.get_quality_report()
            logger.info(f"Quantization complete: {quality.get('total', 0)} embeddings, "
                       f"min similarity: {quality.get('min_similarity', 0):.4f}, "
                       f"fallbacks: {quality.get('fallback_to_float16', 0)}")
            
        except ImportError:
            logger.warning("Quantizer not available, using full embeddings")
            processed_wallpapers = wallpapers
        except Exception as e:
            logger.warning(f"Quantization failed, using full embeddings: {e}")
            processed_wallpapers = wallpapers
    
    manifest = {
        'version': 3,  # Version 3 = MobileNetV4 with 1280D embeddings
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'model_version': 'mobilenet_v4_conv_small',
        'embedding_dim': 1280,
        'total_wallpapers': len(processed_wallpapers),
        'quantized': use_quantization,  # Flag for app-side
        'wallpapers': processed_wallpapers
    }
    
    return manifest

def save_manifest(manifest: Dict):
    """Save manifest.json and compressed version with validation."""
    # Validate manifest before saving
    validation_errors = validate_manifest(manifest)
    if validation_errors:
        logger.error("Manifest validation failed:")
        for error in validation_errors:
            logger.error(f"  - {error}")
        raise ValueError(f"Manifest validation failed with {len(validation_errors)} errors")
    
    # Save uncompressed JSON (compact for size)
    with open(MANIFEST_PATH, 'w') as f:
        json.dump(manifest, f, separators=(',', ':'))  # Minimal JSON for smaller size
    
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
    print(f"Manifest version: {manifest.get('version', 1)}")
    print(f"Embeddings quantized: {manifest.get('quantized', False)}")
    print(f"Manifest size: {file_size_mb:.2f} MB")
    print(f"Compressed size: {compressed_size_mb:.2f} MB")
    print(f"Compression ratio: {file_size_mb/compressed_size_mb:.1f}x")
    
    # Category breakdown
    categories = defaultdict(int)
    for w in manifest['wallpapers']:
        categories[w['category']] += 1
    
    print("\nCategory breakdown:")
    for cat, count in sorted(categories.items(), key=lambda x: x[1], reverse=True)[:15]:
        print(f"  {cat}: {count}")
    
    if len(categories) > 15:
        print(f"  ... and {len(categories) - 15} more categories")
    
    print("="*60 + "\n")

def load_previous_manifest() -> Tuple[Dict[str, Dict], Dict]:
    """
    Load previous manifest for incremental updates.
    
    Returns:
        Tuple of (wallpaper_dict_by_id, manifest_metadata)
    """
    manifest_path = PREVIOUS_MANIFEST_PATH if PREVIOUS_MANIFEST_PATH.exists() else MANIFEST_PATH
    
    if not manifest_path.exists():
        return {}, {}
    
    try:
        with open(manifest_path, 'r') as f:
            manifest = json.load(f)
        
        # Index wallpapers by ID for quick lookup
        wp_dict = {wp['id']: wp for wp in manifest.get('wallpapers', [])}
        
        logger.info(f"Loaded {len(wp_dict)} wallpapers from previous manifest")
        return wp_dict, manifest
        
    except Exception as e:
        logger.warning(f"Could not load previous manifest: {e}")
        return {}, {}

# ============================================================================
# MAIN
# ============================================================================

def main():
    """Main execution function with smart incremental updates and quantization."""
    parser = argparse.ArgumentParser(description='Curate wallpapers from GitHub repositories')
    parser.add_argument('--test', action='store_true', help='Test mode: process only 10 images per repo')
    parser.add_argument('--resume', action='store_true', help='Resume from last checkpoint')
    parser.add_argument('--dry-run', action='store_true', help='Validate configuration without processing')
    parser.add_argument('--full', action='store_true', help='Force full re-curation (ignore incremental detection)')
    parser.add_argument('--no-quantize', action='store_true', help='Disable embedding quantization (larger output)')
    args = parser.parse_args()
    
    # Register signal handlers for graceful shutdown
    resource_manager.register_signal_handlers()
    
    # Create output directory
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    logger.info("="*60)
    logger.info("VANDERWAALS WALLPAPER CURATION PIPELINE v2.0")
    logger.info("="*60)
    logger.info(f"Smart incremental updates: {'DISABLED' if args.full else 'ENABLED'}")
    logger.info(f"Embedding quantization: {'DISABLED' if args.no_quantize else 'ENABLED'}")
    
    # Validate configuration
    logger.info("Validating configuration...")
    validation_errors = validate_configuration()
    if validation_errors:
        logger.error("Configuration validation failed:")
        for error in validation_errors:
            logger.error(f"  - {error}")
        return 1
    logger.info("✓ Configuration valid")
    
    # Dry-run mode: validate and exit
    if args.dry_run:
        logger.info("DRY RUN MODE - Configuration validated successfully")
        logger.info(f"Would process {len(REPOSITORIES)} repositories")
        for repo in REPOSITORIES:
            logger.info(f"  - {repo['name']}")
        return 0
    
    if args.test:
        logger.info("TEST MODE ENABLED: Processing 10 images per repo")
    
    try:
        # Initialize embedding extractor
        extractor = EmbeddingExtractor()
        
        # Initialize progress tracker
        tracker = ProgressTracker()
        
        # ================================================================
        # SMART INCREMENTAL UPDATE DETECTION
        # ================================================================
        github_client = None
        update_tracker = None
        repos_to_process = []
        repos_skipped = []
        
        if not args.full and not args.test:
            logger.info("\n--- Checking for repository updates ---")
            try:
                from github_client import GitHubAPIClient, UpdateTracker
                
                github_client = GitHubAPIClient()
                update_tracker = UpdateTracker(str(UPDATE_TRACKER_PATH))
                
                for repo in REPOSITORIES:
                    name = repo['name']
                    owner, repo_name = name.split('/')
                    branch = repo.get('branch', 'main')
                    
                    last_sha = update_tracker.get_last_sha(name)
                    
                    has_updates, current_sha, changed_files = github_client.check_repo_has_updates(
                        owner, repo_name, branch, last_sha
                    )
                    
                    if has_updates:
                        repos_to_process.append({
                            **repo,
                            '_current_sha': current_sha,
                            '_changed_files': changed_files,
                            '_is_incremental': len(changed_files) > 0
                        })
                        if changed_files:
                            logger.info(f"  ✓ {name}: {len(changed_files)} new/changed images")
                        else:
                            logger.info(f"  ✓ {name}: First run or major update")
                    else:
                        repos_skipped.append(name)
                        logger.info(f"  ○ {name}: No changes (skipping)")
                
                if repos_skipped:
                    logger.info(f"\nSkipping {len(repos_skipped)} unchanged repos, processing {len(repos_to_process)}")
                
            except ImportError:
                logger.warning("GitHub client not available, processing all repos")
                repos_to_process = REPOSITORIES.copy()
            except Exception as e:
                logger.warning(f"Incremental check failed, processing all repos: {e}")
                repos_to_process = REPOSITORIES.copy()
        else:
            # Full mode or test mode: process all repos
            repos_to_process = REPOSITORIES.copy()
        
        if not repos_to_process:
            logger.info("\n✓ All repositories are up to date! No processing needed.")
            return 0
        
        # ================================================================
        # LOAD PREVIOUS STATE
        # ================================================================
        # Load checkpoint if resuming
        if args.resume:
            all_wallpapers, start_index, processed_hashes = load_checkpoint()
        else:
            all_wallpapers = []
            start_index = 0
            processed_hashes = set()
        
        # Load previous manifest for incremental merge
        previous_wallpapers, _ = load_previous_manifest()
        
        # ================================================================
        # PROCESS REPOSITORIES
        # ================================================================
        for idx, repo in enumerate(repos_to_process[start_index:], start=start_index):
            logger.info(f"\n[{idx + 1}/{len(repos_to_process)}] Processing {repo['name']}...")
            
            try:
                # Check for incremental processing
                changed_files = repo.get('_changed_files', None)
                
                # Process repository with progress tracking
                wallpapers, duplicates = process_repository(
                    repo, extractor, processed_hashes, 
                    test_mode=args.test, tracker=tracker
                )
                all_wallpapers.extend(wallpapers)
                
                # Update tracker with new SHA
                if update_tracker and '_current_sha' in repo:
                    update_tracker.update_repo(
                        repo['name'], 
                        repo['_current_sha'], 
                        len(wallpapers)
                    )
                
                # Save checkpoint after each repo (now includes hashes)
                save_checkpoint(all_wallpapers, idx, processed_hashes)
                
            except Exception as e:
                logger.error(f"Failed to process {repo['name']}: {e}")
                tracker.record_repo_failed(repo['name'], str(e))
                continue
        
        # ================================================================
        # MERGE WITH PREVIOUS DATA (for skipped repos)
        # ================================================================
        if repos_skipped and previous_wallpapers:
            logger.info(f"\nMerging {len(previous_wallpapers)} wallpapers from skipped repos...")
            
            # Get IDs of newly processed wallpapers
            new_ids = {wp['id'] for wp in all_wallpapers}
            
            # Add wallpapers from skipped repos
            for wp_id, wp in previous_wallpapers.items():
                # Check if this wallpaper is from a skipped repo
                repo_name = wp.get('repo', '')
                if repo_name in repos_skipped and wp_id not in new_ids:
                    all_wallpapers.append(wp)
            
            logger.info(f"Total after merge: {len(all_wallpapers)} wallpapers")
        
        # Save update tracker
        if update_tracker:
            update_tracker.save()
        
        # Print processing summary
        tracker.print_summary()
        
        # ================================================================
        # GENERATE AND SAVE MANIFEST
        # ================================================================
        logger.info("\nGenerating final manifest...")
        use_quantization = not args.no_quantize
        manifest = generate_manifest(all_wallpapers, use_quantization=use_quantization)
        save_manifest(manifest)
        
        # Save copy for next incremental run
        if MANIFEST_PATH.exists():
            shutil.copy(MANIFEST_PATH, PREVIOUS_MANIFEST_PATH)
        
        # Cleanup checkpoint
        if CHECKPOINT_PATH.exists():
            CHECKPOINT_PATH.unlink()
        
        logger.info("✓ Curation pipeline complete!")
        return 0
        
    except KeyboardInterrupt:
        logger.warning("Received interrupt signal, cleaning up...")
        resource_manager.cleanup()
        return 130  # Standard exit code for Ctrl+C
    except Exception as e:
        logger.error(f"Fatal error in curation pipeline: {e}", exc_info=True)
        resource_manager.cleanup()
        return 1

if __name__ == '__main__':
    sys.exit(main())

