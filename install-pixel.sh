#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
model='models/gemma-4-E2B-it_Google_Tensor_G5.litertlm'
[ -f "$model" ] || { echo 'Run ./download-model.sh first'; exit 1; }
[ "$(stat -f%z "$model")" = '3113545589' ] || { echo 'Model download incomplete'; exit 1; }
adb get-state
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/Android/data/com.example.annoymous/files
adb push "$model" /sdcard/Android/data/com.example.annoymous/files/
adb shell am force-stop com.example.annoymous
adb shell am start -n com.example.annoymous/.MainActivity
