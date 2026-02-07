# =============================================================================
# MobileNetV4-Conv-Small EMBEDDING Model Converter (1280D)
# =============================================================================
# COPY THIS ENTIRE CELL INTO GOOGLE COLAB
# =============================================================================

import os
import subprocess
import sys

def install_dependencies():
    print("STEP 1: Installing dependencies (this may take 2-3 minutes)...")
    # Install specific versions to ensure compatibility
    pkgs = [
        "torch", "torchvision", "timm", 
        "onnx", "onnxscript", "onnx2tf", 
        "onnx_graphsurgeon", "simple_onnx_processing_tools"
    ]
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-q"] + pkgs)
    # Install tensorflow separately if needed, but Colab usually has it. 
    # Ensuring we have a recent version
    subprocess.check_call([sys.executable, "-m", "pip", "install", "-U", "-q", "tensorflow"])
    print("Dependencies installed.\n")

install_dependencies()

import torch
import timm
import tensorflow as tf
import numpy as np
import shutil
import glob

print("STEP 2: Loading MobileNetV4 Model...")
# Create model with 0 classes to get the pooling layer output (frameless embedding)
model = timm.create_model('mobilenetv4_conv_small.e2400_r224_in1k', pretrained=True, num_classes=0)
model.eval()

# Verify PyTorch output
dummy_input = torch.randn(1, 3, 224, 224)
with torch.no_grad():
    out_torch = model(dummy_input)
    print(f"PyTorch Output Shape: {out_torch.shape}")
    if out_torch.shape[-1] != 1280:
        raise ValueError(f"Expected 1280 dimensions, got {out_torch.shape[-1]}")
print("Model loaded successfully.\n")

print("STEP 3: Exporting to ONNX...")
onnx_path = "mobilenetv4.onnx"
torch.onnx.export(
    model, 
    dummy_input, 
    onnx_path, 
    input_names=['input'], 
    output_names=['output'], 
    opset_version=17
)
print(f"ONNX exported to {onnx_path}\n")

print("STEP 4: Converting ONNX to TFLite (onnx2tf)...")
# onnx2tf is very robust. We use subprocess to call it as a CLI tool.
# -osd: optimization for stable diffusion (optional but good for some ops)
# -oiqt: optimize input quantization
tflite_out_dir = "tflite_out"
if os.path.exists(tflite_out_dir):
    shutil.rmtree(tflite_out_dir)

cmd = [
    "onnx2tf", 
    "-i", onnx_path, 
    "-o", tflite_out_dir,
    "-v" # Verbose 
]
subprocess.run(cmd, check=True)
print("Conversion command finished.\n")

print("STEP 5: Verifying TFLite Model...")
# Find the generated tflite file
tflite_files = glob.glob(f"{tflite_out_dir}/*.tflite")
if not tflite_files:
    raise FileNotFoundError("No TFLite file generated!")

tflite_model_path = tflite_files[0]
final_model_name = "mobilenet_v4_conv_small.tflite"
shutil.copy(tflite_model_path, final_model_name)

# Verification
interpreter = tf.lite.Interpreter(model_path=final_model_name)
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print(f"TFLite Input Info: {input_details[0]['shape']}")
print(f"TFLite Output Info: {output_details[0]['shape']}")

# Run inference
test_input = np.random.randn(*input_details[0]['shape']).astype(np.float32)
interpreter.set_tensor(input_details[0]['index'], test_input)
interpreter.invoke()
test_output = interpreter.get_tensor(output_details[0]['index'])

out_dim = test_output.shape[-1]
file_size_mb = os.path.getsize(final_model_name) / (1024 * 1024)

print(f"\nFinal Check:")
print(f"- Output Dimensions: {out_dim}")
print(f"- File Size: {file_size_mb:.2f} MB")

if out_dim == 1280:
    print("\n✅ SUCCESS! The model is ready.")
    print(f"Download the file '{final_model_name}' from the file browser on the left.")
    
    # Attempt to download automatically in Colab
    try:
        from google.colab import files
        print("Attempting automatic download...")
        files.download(final_model_name)
    except ImportError:
        pass
else:
    print(f"\n❌ FAILURE: Expected 1280 dimensions, got {out_dim}")
