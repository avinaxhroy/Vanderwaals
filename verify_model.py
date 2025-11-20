#!/usr/bin/env python3
"""
Verify the TFLite model's output dimensions.
"""

import tensorflow as tf
import numpy as np

model_path = "app/src/main/assets/models/mobilenet_v3_small.tflite"

try:
    # Load the TFLite model
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    # Get input and output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print("=" * 60)
    print("TFLite Model Analysis")
    print("=" * 60)
    print(f"\nModel file: {model_path}")
    
    import os
    file_size_mb = os.path.getsize(model_path) / (1024 * 1024)
    print(f"File size: {file_size_mb:.2f} MB")
    
    print("\n" + "-" * 60)
    print("INPUT DETAILS:")
    print("-" * 60)
    for i, detail in enumerate(input_details):
        print(f"\nInput {i}:")
        print(f"  Name: {detail['name']}")
        print(f"  Shape: {detail['shape']}")
        print(f"  Type: {detail['dtype']}")
    
    print("\n" + "-" * 60)
    print("OUTPUT DETAILS:")
    print("-" * 60)
    for i, detail in enumerate(output_details):
        print(f"\nOutput {i}:")
        print(f"  Name: {detail['name']}")
        print(f"  Shape: {detail['shape']}")
        print(f"  Type: {detail['dtype']}")
        
        # Calculate embedding dimension
        if len(detail['shape']) > 1:
            embedding_dim = detail['shape'][1]
        else:
            embedding_dim = detail['shape'][0]
        
        print(f"  → Embedding Dimension: {embedding_dim}")
    
    # Test with dummy input
    print("\n" + "=" * 60)
    print("VERIFICATION TEST:")
    print("=" * 60)
    
    input_shape = input_details[0]['shape']
    print(f"\nGenerating random input of shape {input_shape}...")
    
    # Create dummy input (random normalized image)
    dummy_input = np.random.rand(*input_shape).astype(np.float32)
    
    # Run inference
    interpreter.set_tensor(input_details[0]['index'], dummy_input)
    interpreter.invoke()
    
    # Get output
    output_data = interpreter.get_tensor(output_details[0]['index'])
    
    print(f"Output shape: {output_data.shape}")
    print(f"Output embedding dimension: {output_data.shape[1] if len(output_data.shape) > 1 else output_data.shape[0]}")
    
    # Determine which model this is
    embedding_dim = output_data.shape[1] if len(output_data.shape) > 1 else output_data.shape[0]
    
    print("\n" + "=" * 60)
    print("CONCLUSION:")
    print("=" * 60)
    
    if embedding_dim == 576:
        print("✅ Model is MobileNetV3-Small (576 dimensions)")
        print("✅ MATCHES current code expectations!")
    elif embedding_dim == 1024:
        print("❌ Model is MobileNetV3-Large (1024 dimensions)")
        print("❌ MISMATCH: Code expects 576 dimensions!")
        print("\n⚠️  ACTION REQUIRED: Replace with MobileNetV3-Small model")
    else:
        print(f"⚠️  Unknown model (unexpected {embedding_dim} dimensions)")
    
    print("=" * 60)
    
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
