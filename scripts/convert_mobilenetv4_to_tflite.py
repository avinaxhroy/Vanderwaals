#!/usr/bin/env python3
"""
MobileNetV4-Conv-Small TFLite Conversion Script
================================================

Converts MobileNetV4-Conv-Small from PyTorch to TFLite using ONNX.
Works locally without ai-edge-torch dependency issues.

Requirements:
    pip install torch timm onnx onnx2tf tensorflow

Usage:
    python convert_mobilenetv4_to_tflite.py

Output:
    - mobilenet_v4_conv_small.tflite
    - Auto-copied to: app/src/main/assets/models/
"""

import os
import sys
import numpy as np
from pathlib import Path
import shutil
import subprocess
import glob

def main():
    print("=" * 60)
    print("MobileNetV4-Conv-Small TFLite Conversion")
    print("=" * 60)
    
    # Output directory
    output_dir = Path("model_conversion_output")
    output_dir.mkdir(exist_ok=True)
    
    # Step 1: Load PyTorch model
    print("\n[1/3] Loading PyTorch model from timm...")
    try:
        import torch
        import timm
        
        torch_version = torch.__version__
        print(f"  PyTorch version: {torch_version}")
            
    except ImportError as e:
        print(f"ERROR: {e}")
        print("Please install: pip install torch timm")
        return 1
    
    model = timm.create_model(
        'mobilenetv4_conv_small.e2400_r224_in1k',
        pretrained=True,
        num_classes=0  # Remove classifier, get 1280D embedding
    )
    model.eval()
    print(f"  ✓ Model loaded: 1280D embedding output")
    
    # Step 2: Export to ONNX
    print("\n[2/3] Exporting to ONNX format...")
    onnx_path = output_dir / "mobilenet_v4_conv_small.onnx"
    
    dummy_input = torch.randn(1, 3, 224, 224)
    
    with torch.no_grad():
        torch.onnx.export(
            model,
            dummy_input,
            str(onnx_path),
            input_names=['input'],
            output_names=['embedding'],
            opset_version=13,
            do_constant_folding=True
        )
    
    size_mb = onnx_path.stat().st_size / (1024 * 1024)
    print(f"  ✓ ONNX exported: {size_mb:.2f} MB")
    
    # Step 3: Convert ONNX to TFLite using onnx2tf
    print("\n[3/3] Converting ONNX to TFLite...")
    print("  (This may take 2-3 minutes...)")
    
    try:
        # Import and use onnx2tf directly
        import onnx2tf
        
        # Convert using onnx2tf library
        onnx2tf.convert(
            input_onnx_file_path=str(onnx_path),
            output_folder_path=str(output_dir),
            output_signaturedefs=True
        )
            
        print("  ✓ Conversion complete")
        
    except ImportError:
        print("\n  ERROR: onnx2tf not installed")
        print("  Install with: pip install onnx2tf")
        return 1
    except Exception as e:
        print(f"\n  ERROR: {e}")
        return 1
    
    # Find the generated TFLite file
    tflite_files = list(output_dir.glob("**/*.tflite"))
    
    if not tflite_files:
        print("\n  ERROR: No TFLite file generated")
        return 1
    
    # Use the first TFLite file found
    generated_tflite = tflite_files[0]
    tflite_path = output_dir / "mobilenet_v4_conv_small.tflite"
    shutil.copy(generated_tflite, tflite_path)
    
    size_mb = tflite_path.stat().st_size / (1024 * 1024)
    print(f"  ✓ TFLite model: {size_mb:.2f} MB")
    
    # Verify
    print("\n[Verification] Testing TFLite model...")
    try:
        import tensorflow as tf
        
        interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
        interpreter.allocate_tensors()
        
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"  Input:  {input_details[0]['shape']}")
        print(f"  Output: {output_details[0]['shape']}")
        
        # Test inference
        test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        
        output_dim = output.shape[-1]
        print(f"  Dimension: {output_dim} (expected: 1280)")
        
        if output_dim != 1280:
            print(f"\n  ✗ WARNING: Unexpected dimension")
        else:
            print(f"  ✓ Verification passed")
            
    except ImportError:
        print("  ℹ TensorFlow not installed, skipping verification")
    except Exception as e:
        print(f"  ✗ Verification failed: {e}")
    
    # Copy to Android assets folder
    print("\n✓ Conversion complete!")
    script_dir = Path(__file__).parent
    assets_dir = script_dir.parent / "app" / "src" / "main" / "assets" / "models"
    assets_dir.mkdir(parents=True, exist_ok=True)
    
    target_path = assets_dir / "mobilenet_v4_conv_small.tflite"
    shutil.copy(tflite_path, target_path)
    
    print(f"\n✓ Copied to: {target_path}")
    print(f"  Model size: {target_path.stat().st_size / (1024 * 1024):.2f} MB")
    print("\n" + "=" * 60)
    return 0

if __name__ == '__main__':
    sys.exit(main())

