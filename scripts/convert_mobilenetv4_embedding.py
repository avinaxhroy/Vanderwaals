#!/usr/bin/env python3
"""
MobileNetV4-Conv-Small EMBEDDING Model Converter
=================================================
MINIMAL VERSION - Just 3 steps, robust error handling

This creates a model that outputs 1280D embeddings (not 1000 classes).
The key is: num_classes=0

Run in Google Colab or any Python 3.10+ environment with:
pip install torch timm onnx tensorflow

Usage:
  python convert_mobilenetv4_embedding.py
"""

import os
import sys

def main():
    print("=" * 60)
    print("MobileNetV4 Embedding Model Converter")
    print("=" * 60)
    
    # Step 1: Install/check dependencies
    print("\n[1/3] Checking dependencies...")
    try:
        import torch
        import timm
        print(f"  ✓ PyTorch {torch.__version__}")
        print(f"  ✓ timm {timm.__version__}")
    except ImportError as e:
        print(f"  ✗ Missing: {e}")
        print("\n  Run: pip install torch timm")
        sys.exit(1)
    
    # Step 2: Load model and export to ONNX
    print("\n[2/3] Loading MobileNetV4 and exporting to ONNX...")
    
    model = timm.create_model(
        'mobilenetv4_conv_small.e2400_r224_in1k',
        pretrained=True,
        num_classes=0  # CRITICAL: This removes classifier → 1280D output
    )
    model.eval()
    
    # Verify output dimension
    with torch.no_grad():
        test_out = model(torch.randn(1, 3, 224, 224))
        output_dim = test_out.shape[-1]
        print(f"  ✓ Model loaded: {output_dim}D output")
        
        if output_dim != 1280:
            print(f"  ✗ ERROR: Expected 1280D, got {output_dim}D")
            sys.exit(1)
    
    # Export to ONNX
    onnx_path = "mobilenetv4_embedding.onnx"
    dummy_input = torch.randn(1, 3, 224, 224)
    
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=['input'],
        output_names=['embedding'],
        opset_version=14,
        dynamic_axes={'input': {0: 'batch'}, 'embedding': {0: 'batch'}}
    )
    
    print(f"  ✓ ONNX exported: {os.path.getsize(onnx_path)/1024/1024:.1f} MB")
    
    # Step 3: Convert to TFLite
    print("\n[3/3] Converting to TFLite...")
    
    try:
        # Try onnx-tf method first (most reliable)
        from onnx_tf.backend import prepare
        import onnx
        import tensorflow as tf
        
        onnx_model = onnx.load(onnx_path)
        tf_rep = prepare(onnx_model)
        tf_rep.export_graph("tf_model")
        
        converter = tf.lite.TFLiteConverter.from_saved_model("tf_model")
        tflite_model = converter.convert()
        
        tflite_path = "mobilenet_v4_conv_small.tflite"
        with open(tflite_path, "wb") as f:
            f.write(tflite_model)
        
        print(f"  ✓ TFLite created: {os.path.getsize(tflite_path)/1024/1024:.1f} MB")
        
    except ImportError:
        print("  ! onnx-tf not available, trying onnx2tf...")
        
        import subprocess
        result = subprocess.run(
            ["onnx2tf", "-i", onnx_path, "-o", "tflite_out", "-osd"],
            capture_output=True, text=True
        )
        
        if result.returncode != 0:
            print(f"  ✗ onnx2tf failed: {result.stderr}")
            print("\n  Try: pip install onnx-tf tensorflow")
            sys.exit(1)
        
        import glob
        tflite_files = glob.glob("tflite_out/**/*.tflite", recursive=True)
        if tflite_files:
            import shutil
            tflite_path = "mobilenet_v4_conv_small.tflite"
            shutil.copy(tflite_files[0], tflite_path)
            print(f"  ✓ TFLite created: {os.path.getsize(tflite_path)/1024/1024:.1f} MB")
    
    # Verify
    print("\n" + "=" * 60)
    print("VERIFICATION")
    print("=" * 60)
    
    try:
        import tensorflow as tf
        import numpy as np
        
        interpreter = tf.lite.Interpreter(model_path=tflite_path)
        interpreter.allocate_tensors()
        
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print(f"Input:  {input_details[0]['shape']}")
        print(f"Output: {output_details[0]['shape']}")
        
        # Run test
        test_input = np.random.randn(*input_details[0]['shape']).astype(np.float32)
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        output = interpreter.get_tensor(output_details[0]['index'])
        
        if output.shape[-1] == 1280:
            print("\n✅ SUCCESS! Model outputs 1280D embeddings")
            print(f"\n📋 Copy to your project:")
            print(f"   cp {tflite_path} app/src/main/assets/models/")
        else:
            print(f"\n❌ ERROR: Got {output.shape[-1]}D instead of 1280D")
            
    except Exception as e:
        print(f"\n⚠ Verification skipped: {e}")
        print(f"  Model saved to: {tflite_path}")

if __name__ == "__main__":
    main()
