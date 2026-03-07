#!/usr/bin/env bash
set -euo pipefail

# Downloads the all-MiniLM-L6-v2 ONNX model and tokenizer for local embedding inference.
# Run this script from the project root before building.

BASE_URL="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main"

JVM_DIR="shared/src/jvmMain/resources/models/minilm"
ANDROID_DIR="shared/src/androidMain/assets/models/minilm"

mkdir -p "$JVM_DIR" "$ANDROID_DIR"

echo "Downloading tokenizer.json..."
curl -L -o "$JVM_DIR/tokenizer.json" "$BASE_URL/tokenizer.json"
cp "$JVM_DIR/tokenizer.json" "$ANDROID_DIR/tokenizer.json"

ARCH=$(uname -m)
case "$ARCH" in
    x86_64|amd64)
        JVM_MODEL="model_quint8_avx2.onnx"
        echo "Detected x86_64 -- using AVX2 quantized model"
        ;;
    arm64|aarch64)
        JVM_MODEL="model_qint8_arm64.onnx"
        echo "Detected ARM64 -- using ARM quantized model"
        ;;
    *)
        JVM_MODEL="model.onnx"
        echo "Unknown architecture '$ARCH' -- using unquantized model"
        ;;
esac

echo "Downloading JVM model (~22MB)..."
curl -L -o "$JVM_DIR/model.onnx" "$BASE_URL/onnx/$JVM_MODEL"

echo "Downloading Android model (qint8_arm64, ~22MB)..."
curl -L -o "$ANDROID_DIR/model.onnx" "$BASE_URL/onnx/model_qint8_arm64.onnx"

echo "Done. Model files:"
ls -lh "$JVM_DIR"/
ls -lh "$ANDROID_DIR"/
