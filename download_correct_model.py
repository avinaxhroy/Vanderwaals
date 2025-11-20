#!/usr/bin/env python3
"""
Download the correct MobileNetV3-Small model and convert it to TFLite.
"""

import tensorflow as tf
import numpy as np
from pathlib import Path

print("=" * 60)
print("Downloading MobileNetV3-Small Model")
print("=" * 60)

# Load MobileNetV3-Small from Keras
print("\n1. Loading MobileNetV3-Small from tf.keras.applications...")
model = tf.keras.applications.MobileNetV3Small(
    input_shape=(224, 224, 3),
    include_top=False,
    pooling='avg',  # Global average pooling → 576-dim output
    weights='imagenet'
)

print(f"   ✅ Model loaded")
print(f"   Input shape: {model.input_shape}")
print(f"   Output shape: {model.output_shape}")
print(f"   Output dimension: {model.output_shape[1]}")

# Convert to TFLite
print("\n2. Converting to TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)

# Optimize for size and speed
converter.optimizations = [tf.lite.Optimize.DEFAULT]

tflite_model = converter.convert()
print(f"   ✅ Converted to TFLite")
print(f"   Size: {len(tflite_model) / (1024 * 1024):.2f} MB")

# Save the model
output_path = Path("app/src/main/assets/models/mobilenet_v3_small.tflite")
output_path.parent.mkdir(parents=True, exist_ok=True)

# Backup old model first
if output_path.exists():
    backup_path = output_path.with_suffix('.tflite.backup')
    print(f"\n3. Backing up old model to {backup_path.name}...")
    output_path.rename(backup_path)
    print(f"   ✅ Old model backed up")

print(f"\n4. Saving new model to {output_path}...")
output_path.write_bytes(tflite_model)
print(f"   ✅ Model saved")

# Verify the new model
print("\n" + "=" * 60)
print("VERIFICATION")
print("=" * 60)

interpreter = tf.lite.Interpreter(model_path=str(output_path))
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"\nInput shape: {input_details[0]['shape']}")
print(f"Output shape: {output_details[0]['shape']}")

embedding_dim = output_details[0]['shape'][1]
print(f"Embedding dimension: {embedding_dim}")

# Test with dummy input
dummy_input = np.random.rand(1, 224, 224, 3).astype(np.float32)
interpreter.set_tensor(input_details[0]['index'], dummy_input)
interpreter.invoke()
output_data = interpreter.get_tensor(output_details[0]['index'])

print(f"\nTest inference output shape: {output_data.shape}")

if embedding_dim == 576:
    print("\n✅ SUCCESS! Model outputs 576 dimensions")
    print("✅ This matches the code expectations!")
else:
    print(f"\n❌ ERROR! Model outputs {embedding_dim} dimensions")
    print("❌ Expected 576 dimensions!")

print("\n" + "=" * 60)
print("Model replacement complete!")
print("=" * 60)
