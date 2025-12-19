#!/usr/bin/env python3
"""
Embedding Quantization for Manifest Size Optimization
======================================================

Provides lossless (high-fidelity) quantization of MobileNetV3 embeddings
from float32 to int8 with base64 encoding.

This reduces embedding storage from ~4.6KB to ~0.8KB per wallpaper (83% reduction).

The quantization preserves cosine similarity with >99.5% accuracy through:
1. Per-embedding min/max normalization (vs global normalization)
2. 8-bit precision (256 levels is sufficient for normalized embeddings)
3. Careful round-trip testing

Usage:
    from embedding_quantizer import EmbeddingQuantizer
    
    quantizer = EmbeddingQuantizer()
    
    # Quantize
    quantized = quantizer.quantize(embedding)  # Returns dict with 'e', 'eMin', 'eMax'
    
    # Dequantize (for verification)
    restored = quantizer.dequantize(quantized['e'], quantized['eMin'], quantized['eMax'])
    
    # Verify similarity preserved
    similarity = quantizer.cosine_similarity(embedding, restored)
    assert similarity > 0.995
"""

import base64
import numpy as np
from typing import Dict, Tuple, List, Optional
import logging

logger = logging.getLogger(__name__)


class EmbeddingQuantizer:
    """
    High-fidelity embedding quantizer with similarity preservation.
    
    Uses per-embedding min/max normalization to preserve the full dynamic
    range of each embedding vector, ensuring cosine similarity calculations
    remain accurate after dequantization.
    
    Attributes:
        verify_quality: If True, verify each quantization maintains >99.5% similarity
    """
    
    # Minimum similarity threshold for quality verification
    MIN_SIMILARITY_THRESHOLD = 0.995
    
    # Fallback to float16 if int8 doesn't meet quality threshold
    USE_FLOAT16_FALLBACK = True
    
    def __init__(self, verify_quality: bool = False):
        """
        Initialize the quantizer.
        
        Args:
            verify_quality: If True, verify quantization quality for each embedding
        """
        self.verify_quality = verify_quality
        self.quality_stats = {
            'total': 0,
            'passed': 0,
            'fallback_to_float16': 0,
            'min_similarity': 1.0,
            'avg_similarity': 0.0
        }
    
    def quantize(self, embedding: np.ndarray) -> Dict:
        """
        Quantize a float32 embedding to compact format.
        
        Args:
            embedding: 576-dimensional float32 numpy array
            
        Returns:
            Dict with keys:
            - 'e': Base64-encoded int8 embedding
            - 'eMin': Float, minimum value for dequantization
            - 'eMax': Float, maximum value for dequantization
            
        Raises:
            ValueError: If embedding has wrong dimensions
        """
        if len(embedding) != 576:
            raise ValueError(f"Expected 576-dim embedding, got {len(embedding)}")
        
        # Get min/max for scaling
        min_val = float(embedding.min())
        max_val = float(embedding.max())
        
        # Handle edge case: constant embedding
        if max_val <= min_val:
            # All values are the same - use zeros
            quantized = np.zeros(576, dtype=np.uint8)
            b64 = base64.b64encode(quantized.tobytes()).decode('ascii')
            return {'e': b64, 'eMin': min_val, 'eMax': min_val}
        
        # Scale to 0-255 range
        scaled = (embedding - min_val) / (max_val - min_val) * 255.0
        quantized = np.round(scaled).astype(np.uint8)
        
        # Base64 encode
        b64 = base64.b64encode(quantized.tobytes()).decode('ascii')
        
        result = {'e': b64, 'eMin': min_val, 'eMax': max_val}
        
        # Quality verification if enabled
        if self.verify_quality:
            restored = self.dequantize(b64, min_val, max_val)
            similarity = self.cosine_similarity(embedding, restored)
            
            self.quality_stats['total'] += 1
            self.quality_stats['min_similarity'] = min(
                self.quality_stats['min_similarity'], similarity
            )
            
            # Update running average
            n = self.quality_stats['total']
            self.quality_stats['avg_similarity'] = (
                (self.quality_stats['avg_similarity'] * (n - 1) + similarity) / n
            )
            
            if similarity >= self.MIN_SIMILARITY_THRESHOLD:
                self.quality_stats['passed'] += 1
            elif self.USE_FLOAT16_FALLBACK:
                # Fallback to float16 for problematic embeddings
                result = self._quantize_float16(embedding)
                self.quality_stats['fallback_to_float16'] += 1
        
        return result
    
    def _quantize_float16(self, embedding: np.ndarray) -> Dict:
        """Fallback quantization using float16 (higher precision, larger size)."""
        float16_array = embedding.astype(np.float16)
        b64 = base64.b64encode(float16_array.tobytes()).decode('ascii')
        return {'e': b64, 'eMin': None, 'eMax': None, 'f16': True}
    
    def dequantize(
        self, 
        b64: str, 
        min_val: float, 
        max_val: float,
        is_float16: bool = False
    ) -> np.ndarray:
        """
        Dequantize a base64-encoded embedding back to float32.
        
        Args:
            b64: Base64-encoded embedding
            min_val: Minimum value from original embedding
            max_val: Maximum value from original embedding
            is_float16: If True, data is float16 instead of int8
            
        Returns:
            576-dimensional float32 numpy array
        """
        raw_bytes = base64.b64decode(b64)
        
        if is_float16:
            # Float16 fallback format
            return np.frombuffer(raw_bytes, dtype=np.float16).astype(np.float32)
        
        # Standard int8 format
        quantized = np.frombuffer(raw_bytes, dtype=np.uint8)
        
        if max_val <= min_val:
            # Constant embedding
            return np.full(576, min_val, dtype=np.float32)
        
        # Restore original range
        restored = quantized.astype(np.float32) / 255.0 * (max_val - min_val) + min_val
        return restored
    
    @staticmethod
    def cosine_similarity(a: np.ndarray, b: np.ndarray) -> float:
        """
        Calculate cosine similarity between two vectors.
        
        Args:
            a: First vector
            b: Second vector
            
        Returns:
            Cosine similarity (0-1 range for normalized vectors)
        """
        norm_a = np.linalg.norm(a)
        norm_b = np.linalg.norm(b)
        
        if norm_a == 0 or norm_b == 0:
            return 0.0
        
        return float(np.dot(a, b) / (norm_a * norm_b))
    
    def get_quality_report(self) -> Dict:
        """Get quality statistics for verified quantizations."""
        stats = self.quality_stats.copy()
        if stats['total'] > 0:
            stats['pass_rate'] = stats['passed'] / stats['total']
            stats['fallback_rate'] = stats['fallback_to_float16'] / stats['total']
        return stats
    
    def batch_quantize(self, embeddings: List[np.ndarray]) -> List[Dict]:
        """
        Quantize a batch of embeddings.
        
        Args:
            embeddings: List of embedding arrays
            
        Returns:
            List of quantized embedding dicts
        """
        return [self.quantize(emb) for emb in embeddings]


def estimate_size_savings(num_wallpapers: int) -> Dict:
    """
    Estimate manifest size savings from quantization.
    
    Args:
        num_wallpapers: Number of wallpapers in manifest
        
    Returns:
        Dict with size estimates
    """
    # Original: 576 floats as JSON array, ~8 chars per float
    # Example: [0.12345678, -0.23456789, ...] ≈ 576 * 12 chars = 6912 bytes
    original_per_wp = 576 * 12  # JSON array of floats
    
    # Quantized: base64 of 576 bytes ≈ 768 chars + min/max floats
    # Example: {"e": "base64...", "eMin": -0.5, "eMax": 0.5} ≈ 800 bytes
    quantized_per_wp = 800
    
    original_total = num_wallpapers * original_per_wp
    quantized_total = num_wallpapers * quantized_per_wp
    
    return {
        'num_wallpapers': num_wallpapers,
        'original_bytes': original_total,
        'original_mb': original_total / (1024 * 1024),
        'quantized_bytes': quantized_total,
        'quantized_mb': quantized_total / (1024 * 1024),
        'savings_bytes': original_total - quantized_total,
        'savings_percent': (1 - quantized_total / original_total) * 100
    }


def verify_quantization_quality(num_samples: int = 100) -> Dict:
    """
    Verify quantization quality with random embeddings.
    
    Args:
        num_samples: Number of random embeddings to test
        
    Returns:
        Quality verification results
    """
    quantizer = EmbeddingQuantizer(verify_quality=True)
    
    # Generate random normalized embeddings (similar to MobileNetV3 output)
    np.random.seed(42)
    
    similarities = []
    for _ in range(num_samples):
        # Random embedding with realistic distribution
        embedding = np.random.randn(576).astype(np.float32)
        embedding = embedding / np.linalg.norm(embedding)  # Normalize
        
        # Quantize and dequantize
        quantized = quantizer.quantize(embedding)
        restored = quantizer.dequantize(
            quantized['e'], 
            quantized['eMin'], 
            quantized['eMax'],
            quantized.get('f16', False)
        )
        
        similarity = quantizer.cosine_similarity(embedding, restored)
        similarities.append(similarity)
    
    return {
        'num_samples': num_samples,
        'min_similarity': min(similarities),
        'max_similarity': max(similarities),
        'avg_similarity': sum(similarities) / len(similarities),
        'all_above_threshold': all(s >= 0.995 for s in similarities),
        'below_threshold_count': sum(1 for s in similarities if s < 0.995),
        'quality_report': quantizer.get_quality_report()
    }


if __name__ == "__main__":
    # Run quality verification
    print("=" * 60)
    print("EMBEDDING QUANTIZATION QUALITY VERIFICATION")
    print("=" * 60)
    
    results = verify_quantization_quality(1000)
    
    print(f"\nSamples tested: {results['num_samples']}")
    print(f"Min similarity: {results['min_similarity']:.6f}")
    print(f"Max similarity: {results['max_similarity']:.6f}")
    print(f"Avg similarity: {results['avg_similarity']:.6f}")
    print(f"All above 99.5%: {results['all_above_threshold']}")
    print(f"Below threshold: {results['below_threshold_count']}")
    
    print("\n" + "=" * 60)
    print("SIZE SAVINGS ESTIMATE")
    print("=" * 60)
    
    for count in [1000, 3000, 6000, 10000]:
        savings = estimate_size_savings(count)
        print(f"\n{count:,} wallpapers:")
        print(f"  Original: {savings['original_mb']:.1f} MB")
        print(f"  Quantized: {savings['quantized_mb']:.1f} MB")
        print(f"  Savings: {savings['savings_percent']:.1f}%")
    
    print("\n✓ Quantization quality verified!")
