#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
hf download litert-community/Gemma3-1B-IT Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm --revision d448835d84411a08f577e25f3afebe528402d73b --local-dir models
printf '%s  %s\n' '1ed29548b302764ce32ebf03d7df8fff943218b76d14230e97ee4bd0224cd8d1' 'models/Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm' | shasum -a 256 -c -
