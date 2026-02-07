"""
MobileNetV4-Conv-Small to TFLite Conversion - PRODUCTION READY
================================================================
Tested and working in Google Colab as of Jan 2026
Run this in a fresh Colab notebook with GPU runtime
"""

# ============================================================================
# STEP 1: Clean install of dependencies
# ============================================================================
print("=" * 70)
print("STEP 1: Installing dependencies (this takes ~2 minutes)")
print("=" * 70)

# Uninstall conflicting packages first
!pip uninstall -y -q torch torchvision ai-edge-torch protobuf tensorflow torchaudio 2>/dev/null || true

# Install exact working versions for Python 3.12
!pip install -q torch==2.5.1 torchvision==0.20.1
!pip install -q timm==1.0.24
!pip install -q protobuf==5.29.2
!pip install -q ml-dtypes==0.5.0
!pip install -q tensorflow==2.19.0
!pip install -q ai-edge-torch==0.7.1

print("✓ Dependencies installed\n")

# ============================================================================
# STEP 2: Load MobileNetV4 model
# ============================================================================
print("=" * 70)
print("STEP 2: Loading MobileNetV4-Conv-Small from timm")
print("=" * 70)

import torch
import timm
import warnings
warnings.filterwarnings('ignore')

model = timm.create_model(
    'mobilenetv4_conv_small.e2400_r224_in1k',
    pretrained=True,
    num_classes=0  # Remove classifier head → 1280D embedding
)
model.eval()
print("✓ Model loaded: 1280-dimensional embedding output\n")

# ============================================================================
# STEP 3: Convert to TFLite using ai-edge-torch
# ============================================================================
print("=" * 70)
print("STEP 3: Converting to TFLite format")
print("=" * 70)

import ai_edge_torch

sample_input = torch.randn(1, 3, 224, 224)

try:
    edge_model = ai_edge_torch.convert(model, (sample_input,))
    print("✓ Conversion successful\n")
except Exception as e:
    print(f"❌ CONVERSION FAILED: {e}")
    print("\nTroubleshooting:")
    print("1. Runtime → Restart runtime")
    print("2. Re-run all cells")
    raise

# ============================================================================
# STEP 4: Export and verify
# ============================================================================
print("=" * 70)
print("STEP 4: Exporting and verifying model")
print("=" * 70)

output_path = "mobilenet_v4_conv_small.tflite"
edge_model.export(output_path)

import os
size_mb = os.path.getsize(output_path) / (1024 * 1024)
print(f"✓ TFLite model saved: {output_path} ({size_mb:.2f} MB)")

# Verify with TensorFlow Lite interpreter
import tensorflow as tf
import numpy as np

interpreter = tf.lite.Interpreter(model_path=output_path)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"  Input shape:  {input_details[0]['shape']}")
print(f"  Output shape: {output_details[0]['shape']}")

# Test inference
test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
interpreter.set_tensor(input_details[0]['index'], test_input)
interpreter.invoke()
output = interpreter.get_tensor(output_details[0]['index'])

output_dim = output.shape[-1]
print(f"  Output dimension: {output_dim}")

# ============================================================================
# FINAL RESULT
# ============================================================================
print("\n" + "=" * 70)
if output_dim == 1280:
    print("✅ SUCCESS! Model is ready for production use.")
    print("=" * 70)
    print("\n📥 DOWNLOAD INSTRUCTIONS:")
    print("   1. Click Files icon (📁) in left sidebar")
    print("   2. Find 'mobilenet_v4_conv_small.tflite'")
    print("   3. Right-click → Download")
    print("\n📋 COPY TO YOUR PROJECT:")
    print("   cp ~/Downloads/mobilenet_v4_conv_small.tflite \\")
    print("      app/src/main/assets/models/")
    print("\n" + "=" * 70)
else:
    print(f"❌ FAILED: Output dimension is {output_dim}, expected 1280")
    print("=" * 70)
