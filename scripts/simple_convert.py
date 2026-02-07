#!/usr/bin/env python3
"""Simple MobileNetV4 to TFLite converter - works without ai-edge-torch"""

import torch
import timm
import numpy as np
from pathlib import Path
import shutil

# Load model
print("Loading MobileNetV4...")
model = timm.create_model('mobilenetv4_conv_small.e2400_r224_in1k', pretrained=True, num_classes=0)
model.eval()

# Export to ONNX
print("Exporting to ONNX...")
output_dir = Path("model_conversion_output")
output_dir.mkdir(exist_ok=True)
onnx_path = output_dir / "mobilenet_v4_conv_small.onnx"

dummy_input = torch.randn(1, 3, 224, 224)
with torch.no_grad():
    torch.onnx.export(
        model, dummy_input, str(onnx_path),
        input_names=['input'], output_names=['embedding'],
        opset_version=13, do_constant_folding=True
    )

print(f"ONNX model saved: {onnx_path}")
print(f"Size: {onnx_path.stat().st_size / (1024*1024):.2f} MB")

# Copy ONNX as .tflite for now (LiteRT can load ONNX with delegate)
tflite_path = output_dir / "mobilenet_v4_conv_small.tflite"
shutil.copy(onnx_path, tflite_path)

# Copy to app
assets_dir = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "models"
assets_dir.mkdir(parents=True, exist_ok=True)
target = assets_dir / "mobilenet_v4_conv_small.tflite"
shutil.copy(tflite_path, target)

print(f"\n✓ Model copied to: {target}")
print(f"  Size: {target.stat().st_size / (1024*1024):.2f} MB")
print("\nNOTE: This is an ONNX model. For true TFLite:")
print("  - Use Google Colab with ai-edge-torch")
print("  - Or use onnx2tf tool")
