"""
MobileNetV4 to TFLite - RELIABLE METHOD
========================================
Uses ONNX → TFLite conversion (no ai-edge-torch)
Tested and working in Google Colab
"""

# ============================================================================
# STEP 1: Install dependencies
# ============================================================================
print("=" * 70)
print("Installing dependencies...")
print("=" * 70)

!pip install -q torch torchvision timm
!pip install -q onnx onnx2tf
!pip install -q tensorflow

print("✓ Dependencies installed\n")

# ============================================================================
# STEP 2: Load MobileNetV4 and export to ONNX
# ============================================================================
print("=" * 70)
print("Loading MobileNetV4 and exporting to ONNX...")
print("=" * 70)

import torch
import timm
import warnings
warnings.filterwarnings('ignore')

# Load model
model = timm.create_model(
    'mobilenetv4_conv_small.e2400_r224_in1k',
    pretrained=True,
    num_classes=0
)
model.eval()
print("✓ Model loaded (1280D embeddings)")

# Export to ONNX
dummy_input = torch.randn(1, 3, 224, 224)
onnx_path = "mobilenet_v4_conv_small.onnx"

with torch.no_grad():
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=['input'],
        output_names=['embedding'],
        opset_version=13,
        do_constant_folding=True
    )

import os
size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
print(f"✓ ONNX exported: {size_mb:.2f} MB\n")

# ============================================================================
# STEP 3: Convert ONNX to TFLite using onnx2tf
# ============================================================================
print("=" * 70)
print("Converting ONNX to TFLite (this takes ~2-3 minutes)...")
print("=" * 70)

!onnx2tf -i mobilenet_v4_conv_small.onnx -o . -osd

print("✓ Conversion complete\n")

# ============================================================================
# STEP 4: Find and verify TFLite model
# ============================================================================
print("=" * 70)
print("Verifying TFLite model...")
print("=" * 70)

import glob
tflite_files = glob.glob("**/*.tflite", recursive=True)

if not tflite_files:
    print("❌ No TFLite file found!")
else:
    tflite_path = tflite_files[0]
    print(f"Found: {tflite_path}")
    
    # Rename to standard name
    final_path = "mobilenet_v4_conv_small.tflite"
    !cp "{tflite_path}" "{final_path}"
    
    size_mb = os.path.getsize(final_path) / (1024 * 1024)
    print(f"✓ TFLite model: {size_mb:.2f} MB")
    
    # Verify
    import tensorflow as tf
    import numpy as np
    
    interpreter = tf.lite.Interpreter(model_path=final_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print(f"  Input:  {input_details[0]['shape']}")
    print(f"  Output: {output_details[0]['shape']}")
    
    # Test
    test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    
    output_dim = output.shape[-1]
    print(f"  Dimension: {output_dim}")
    
    # ========================================================================
    # FINAL RESULT
    # ========================================================================
    print("\n" + "=" * 70)
    if output_dim == 1280:
        print("✅ SUCCESS!")
        print("=" * 70)
        print("\n📥 DOWNLOAD:")
        print("   Files → mobilenet_v4_conv_small.tflite → Download")
        print("\n📋 COPY TO PROJECT:")
        print("   cp ~/Downloads/mobilenet_v4_conv_small.tflite \\")
        print("      app/src/main/assets/models/")
        print("\n" + "=" * 70)
    else:
        print(f"❌ Wrong dimension: {output_dim}")
        print("=" * 70)
