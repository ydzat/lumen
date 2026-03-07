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

echo "Downloading JVM model (quint8_avx2, ~22MB)..."
curl -L -o "$JVM_DIR/model.onnx" "$BASE_URL/onnx/model_quint8_avx2.onnx"

echo "Downloading Android model (qint8_arm64, ~22MB)..."
curl -L -o "$ANDROID_DIR/model.onnx" "$BASE_URL/onnx/model_qint8_arm64.onnx"

echo "Done. Model files:"
ls -lh "$JVM_DIR"/
ls -lh "$ANDROID_DIR"/
