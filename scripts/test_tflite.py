#!/usr/bin/env python3
"""Quick test to verify the TFLite model outputs 1280D embeddings"""

import tensorflow as tf
import numpy as np

model_path = "/Users/avinash/Documents/Git/Vanderwaals/app/src/main/assets/models/mobilenet_v4_conv_small.tflite"

print("Testing TFLite model...")
interpreter = tf.lite.Interpreter(model_path=model_path)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"Input shape:  {input_details[0]['shape']}")
print(f"Output shape: {output_details[0]['shape']}")

# Test inference
test_input = np.random.randn(1, 224, 224, 3).astype(np.float32)
interpreter.set_tensor(input_details[0]['index'], test_input)
interpreter.invoke()
output = interpreter.get_tensor(output_details[0]['index'])

output_dim = output.shape[-1]
print(f"Output dimension: {output_dim}")

if output_dim == 1280:
    print("\n✅ SUCCESS! Model outputs 1280D embeddings")
else:
    print(f"\n❌ ERROR: Expected 1280D, got {output_dim}D")
