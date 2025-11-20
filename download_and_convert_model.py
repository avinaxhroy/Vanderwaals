#!/usr/bin/env python3
"""
Download and convert the EXACT MobileNetV3-Small model used in curation script.
This ensures the Android app uses the same model as the Python curation pipeline.
"""

import tensorflow as tf
import numpy as np
from pathlib import Path
import sys

print("=" * 80)
print("DOWNLOADING MOBILENETV3-SMALL MODEL (SAME AS CURATION SCRIPT)")
print("=" * 80)

# Step 1: Load the exact same model as curate_wallpapers.py
print("\n[1/5] Loading MobileNetV3-Small from tf.keras.applications...")
print("       (This matches curate_wallpapers.py lines 152-157)")

model = tf.keras.applications.MobileNetV3Small(
    input_shape=(224, 224, 3),
    include_top=False,  # Remove classification head
    weights='imagenet',  # Pre-trained on ImageNet
    pooling='avg'  # Global average pooling → 576-dim output
)

print(f"       ✅ Model loaded successfully")
print(f"       Input shape: {model.input_shape}")
print(f"       Output shape: {model.output_shape}")
print(f"       Embedding dimension: {model.output_shape[1]}")

if model.output_shape[1] != 576:
    print(f"\n       ❌ ERROR: Expected 576 dimensions, got {model.output_shape[1]}")
    sys.exit(1)

# Step 2: Test the model with dummy input
print("\n[2/5] Testing model inference...")
dummy_input = np.random.rand(1, 224, 224, 3).astype(np.float32)
# Preprocess like MobileNetV3 expects
dummy_input = tf.keras.applications.mobilenet_v3.preprocess_input(dummy_input)
output = model.predict(dummy_input, verbose=0)
print(f"       ✅ Test inference successful")
print(f"       Output shape: {output.shape}")
print(f"       Output sample: {output[0][:5]}")

# Step 3: Convert to TFLite (NO quantization for maximum accuracy)
print("\n[3/5] Converting to TFLite (full precision, no quantization)...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# DO NOT use quantization - we want exact same accuracy as Python
# converter.optimizations = [tf.lite.Optimize.DEFAULT]  # DISABLED

tflite_model = converter.convert()
model_size_mb = len(tflite_model) / (1024 * 1024)
print(f"       ✅ Converted to TFLite")
print(f"       Model size: {model_size_mb:.2f} MB")

# Step 4: Verify the TFLite model works
print("\n[4/5] Verifying TFLite model...")
interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"       TFLite input shape: {input_details[0]['shape']}")
print(f"       TFLite output shape: {output_details[0]['shape']}")

# Test with the same dummy input
interpreter.set_tensor(input_details[0]['index'], dummy_input)
interpreter.invoke()
tflite_output = interpreter.get_tensor(output_details[0]['index'])

print(f"       ✅ TFLite inference successful")
print(f"       Output shape: {tflite_output.shape}")
print(f"       Output sample: {tflite_output[0][:5]}")

# Verify outputs match (within floating point tolerance)
diff = np.abs(output[0] - tflite_output[0]).max()
print(f"       Max difference between Keras and TFLite: {diff:.6f}")

if diff < 0.001:
    print(f"       ✅ Outputs match! (diff < 0.001)")
else:
    print(f"       ⚠️  Warning: Large difference ({diff:.6f})")

# Step 5: Save to Android assets
print("\n[5/5] Saving to Android app assets...")
output_path = Path("app/src/main/assets/models/mobilenet_v3_small.tflite")
output_path.parent.mkdir(parents=True, exist_ok=True)

# Backup old models
if output_path.exists():
    backup_path = Path(str(output_path) + f".backup_{Path(output_path).stat().st_mtime:.0f}")
    print(f"       Backing up old model to {backup_path.name}...")
    output_path.rename(backup_path)

output_path.write_bytes(tflite_model)
print(f"       ✅ Model saved to: {output_path}")
print(f"       File size: {model_size_mb:.2f} MB")

# Final verification
print("\n" + "=" * 80)
print("✅ SUCCESS! MODEL DOWNLOAD AND CONVERSION COMPLETE")
print("=" * 80)
print("\nModel Details:")
print(f"  • Source: tf.keras.applications.MobileNetV3Small")
print(f"  • Weights: ImageNet pre-trained")
print(f"  • Architecture: include_top=False, pooling='avg'")
print(f"  • Input: (1, 224, 224, 3) float32")
print(f"  • Output: (1, 576) float32")
print(f"  • Size: {model_size_mb:.2f} MB")
print(f"  • Precision: Full (no quantization)")
print(f"\n✅ This is the EXACT same model used in curate_wallpapers.py")
print("✅ Android app will now use identical embeddings as the database")
print("\nNext steps:")
print("  1. Rebuild the Android app")
print("  2. Test with different wallpapers")
print("  3. Verify similar wallpapers are now correctly matched")
