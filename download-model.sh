#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p models
model='models/gemma-4-E2B-it_Google_Tensor_G5.litertlm'
expected='af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc'
if [ ! -f "$model" ]; then
 curl -fL --retry 3 -C - 'https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it_Google_Tensor_G5.litertlm?download=true' -o "$model.part"
 printf '%s  %s\n' "$expected" "$model.part" | shasum -a 256 -c -
 mv "$model.part" "$model"
else
 printf '%s  %s\n' "$expected" "$model" | shasum -a 256 -c -
fi
