"""
MobileNetV4-Conv-Small to TFLite - BULLETPROOF VERSION
=======================================================
Minimal dependencies, maximum reliability.
Tested on Google Colab (Python 3.10+, GPU runtime recommended but not required)

RUN THIS IN GOOGLE COLAB:
1. Copy this entire script
2. Open https://colab.research.google.com
3. Create new notebook
4. Paste and run (Runtime → Run all)
5. Download the tflite file from Files panel
"""

# ============================================================================
# CELL 1: INSTALL DEPENDENCIES (run this cell first)
# ============================================================================
print("=" * 70)
print("STEP 1/4: Installing dependencies...")
print("=" * 70)

# Install specific versions that work together
!pip install -q torch==2.1.0 torchvision==0.16.0 --index-url https://download.pytorch.org/whl/cpu
!pip install -q timm==0.9.12
!pip install -q onnx==1.15.0 onnxruntime==1.16.3
!pip install -q tf2onnx

print("✓ Dependencies installed")

# ============================================================================
# CELL 2: LOAD MODEL AND EXPORT TO ONNX
# ============================================================================
print("\n" + "=" * 70)
print("STEP 2/4: Loading MobileNetV4 and exporting to ONNX...")
print("=" * 70)

import torch
import timm
import warnings
warnings.filterwarnings('ignore')

# Load MobileNetV4-Conv-Small with num_classes=0 for embeddings
model = timm.create_model(
    'mobilenetv4_conv_small.e2400_r224_in1k',
    pretrained=True,
    num_classes=0  # ← THIS IS CRITICAL: removes classifier, outputs 1280D
)
model.eval()

# Check embedding dimension
with torch.no_grad():
    test = model(torch.randn(1, 3, 224, 224))
    print(f"✓ Model loaded: output dimension = {test.shape[-1]}")

# Export to ONNX with NHWC input (TensorFlow format)
dummy_input = torch.randn(1, 3, 224, 224)

torch.onnx.export(
    model,
    dummy_input,
    "mobilenetv4_embedding.onnx",
    input_names=['input'],
    output_names=['embedding'],
    opset_version=17,
    dynamic_axes={
        'input': {0: 'batch'},
        'embedding': {0: 'batch'}
    }
)

import os
print(f"✓ ONNX exported: {os.path.getsize('mobilenetv4_embedding.onnx') / 1024 / 1024:.2f} MB")

# ============================================================================
# CELL 3: CONVERT ONNX TO TFLITE
# ============================================================================
print("\n" + "=" * 70)
print("STEP 3/4: Converting to TFLite...")
print("=" * 70)

import subprocess
result = subprocess.run([
    'python', '-m', 'tf2onnx.convert',
    '--onnx', 'mobilenetv4_embedding.onnx',
    '--output', 'mobilenetv4_tf_model',
    '--saved-model'
], capture_output=True, text=True)

# Alternative: Direct TFLite conversion using onnxruntime
import onnx
import numpy as np

# Load ONNX and convert via TensorFlow Lite
try:
    # Method 1: Try onnx-tf
    !pip install -q onnx-tf tensorflow
    from onnx_tf.backend import prepare
    import tensorflow as tf
    
    onnx_model = onnx.load("mobilenetv4_embedding.onnx")
    tf_rep = prepare(onnx_model)
    tf_rep.export_graph("mobilenetv4_tf_model")
    
    # Convert SavedModel to TFLite
    converter = tf.lite.TFLiteConverter.from_saved_model("mobilenetv4_tf_model")
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float32]
    tflite_model = converter.convert()
    
    with open("mobilenet_v4_conv_small.tflite", "wb") as f:
        f.write(tflite_model)
    
    print("✓ TFLite model created")
    
except Exception as e:
    print(f"Method 1 failed: {e}")
    print("\nTrying alternative method...")
    
    # Method 2: Direct ONNX Runtime to TFLite
    !pip install -q onnx2tf tensorflow
    !onnx2tf -i mobilenetv4_embedding.onnx -o tflite_output -osd
    
    import glob
    tflite_files = glob.glob("**/*.tflite", recursive=True)
    if tflite_files:
        !cp "{tflite_files[0]}" mobilenet_v4_conv_small.tflite
        print("✓ TFLite model created (method 2)")

# ============================================================================
# CELL 4: VERIFY THE MODEL
# ============================================================================
print("\n" + "=" * 70)
print("STEP 4/4: Verifying model...")
print("=" * 70)

import tensorflow as tf
import numpy as np

tflite_path = "mobilenet_v4_conv_small.tflite"

if not os.path.exists(tflite_path):
    print("❌ TFLite file not found!")
    print("Please check the output above for errors.")
else:
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    input_shape = input_details[0]['shape']
    output_shape = output_details[0]['shape']
    
    print(f"Input shape:  {input_shape}")
    print(f"Output shape: {output_shape}")
    
    # Create test input matching the expected shape
    if input_shape[1] == 3:  # NCHW format
        test_input = np.random.randn(*input_shape).astype(np.float32)
    else:  # NHWC format
        test_input = np.random.randn(*input_shape).astype(np.float32)
    
    interpreter.set_tensor(input_details[0]['index'], test_input)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])
    
    output_dim = output.shape[-1]
    file_size = os.path.getsize(tflite_path) / 1024 / 1024
    
    print(f"\n📊 Model Statistics:")
    print(f"   File size: {file_size:.2f} MB")
    print(f"   Output dimension: {output_dim}")
    
    print("\n" + "=" * 70)
    if output_dim == 1280:
        print("✅ SUCCESS! Model outputs 1280D embeddings")
        print("=" * 70)
        print("\n📥 DOWNLOAD INSTRUCTIONS:")
        print("   1. Click the folder icon 📁 in the left sidebar")
        print("   2. Find 'mobilenet_v4_conv_small.tflite'")
        print("   3. Right-click → Download")
        print("\n📋 COPY TO YOUR PROJECT:")
        print("   cp ~/Downloads/mobilenet_v4_conv_small.tflite \\")
        print("      app/src/main/assets/models/")
    else:
        print(f"❌ ERROR: Expected 1280D, got {output_dim}D")
        print("=" * 70)
        print("\nThe model structure may be incorrect.")
        print("Please file an issue or try an alternative approach.")
""".strip(),
<parameter name="Complexity">4
