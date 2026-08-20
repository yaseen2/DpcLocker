import os
import sys

# Force UTF-8 stdout for Windows console
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')
if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8')

import torch
from transformers import AutoModelForImageClassification
import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType

def export_and_quantize():
    model_name = "Falconsai/nsfw_image_detection"
    output_dir = os.path.abspath("models")
    os.makedirs(output_dir, exist_ok=True)

    raw_onnx_path = os.path.join(output_dir, "falcons_nsfw_fp32.onnx")
    quantized_onnx_path = os.path.join(output_dir, "falcons_nsfw_quantized.onnx")

    print(f"[*] Loading {model_name}...")
    model = AutoModelForImageClassification.from_pretrained(model_name)
    model.eval()

    dummy_input = torch.randn(1, 3, 224, 224, dtype=torch.float32)

    print(f"[*] Exporting PyTorch ViT model to ONNX: {raw_onnx_path}...")
    torch.onnx.export(
        model,
        dummy_input,
        raw_onnx_path,
        export_params=True,
        opset_version=18,
        do_constant_folding=True,
        input_names=["pixel_values"],
        output_names=["logits"],
        dynamo=False
    )
    print(f"[+] Raw ONNX model exported. Size: {os.path.getsize(raw_onnx_path) / (1024 * 1024):.2f} MB")

    print(f"[*] Applying dynamic INT8 quantization...")
    quantize_dynamic(
        model_input=raw_onnx_path,
        model_output=quantized_onnx_path,
        weight_type=QuantType.QUInt8
    )
    print(f"[+] Quantized ONNX model saved to: {quantized_onnx_path}")
    print(f"[+] Quantized Model Size: {os.path.getsize(quantized_onnx_path) / (1024 * 1024):.2f} MB")

    # Verify model with ONNX Runtime
    import onnxruntime as ort
    import numpy as np

    session = ort.InferenceSession(quantized_onnx_path)
    test_input = np.random.randn(1, 3, 224, 224).astype(np.float32)
    outputs = session.run(["logits"], {"pixel_values": test_input})
    logits = outputs[0][0]
    exp_logits = np.exp(logits - np.max(logits))
    probs = exp_logits / np.sum(exp_logits)
    print(f"[+] Verification test pass! Sample Probs: normal={probs[0]:.4f}, nsfw={probs[1]:.4f}")

if __name__ == "__main__":
    export_and_quantize()
