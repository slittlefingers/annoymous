# annoymous

Minimal Android chat app for Pixel 10 / Google Tensor G5. Each Send calls the selected local Gemma model through the C API and a small JNI bridge.

The app uses a single native **Session**, not Conversation. Each request supplies only the current user turn, framed with Gemma 4's `<|turn>user` / `<turn|>` / `<|turn>model` markers from the pinned model template. Session prompt templating is disabled; the runtime adds BOS on the first turn. KV context accumulates in the same Session until Create new session is clicked. No previous text is resent. Errors/context exhaustion do not trigger an automatic reset.

The app keeps no chat history list/database/file. The display is cleared on Send and replaced with only the latest reply. Native input/response objects are released after each call. Model replacement is disabled after initialization. Closing the activity or terminating the process releases the in-memory Session.

This describes application-level text retention, not guaranteed memory erasure: runtime buffers/token IDs and transient Java/native copies may remain; KV still represents previous turns. The existing original runtime scrubbing limitations still apply. The app has no INTERNET permission; import/download happens outside the app.

## Build

Requirements: JDK 17, Android SDK 35, Android NDK r30-beta1 (30.0.14904198), macOS host. Set sdk.dir in local.properties; optionally set ANDROID_NDK_HOME. Run:

```sh
./gradlew assembleDebug
```

Output: app/build/outputs/apk/debug/app-debug.apk (debug signed, ARM64, Android 12+). The JNI bridge rebuilds from source as part of Gradle. Runtime libraries are packaged in app/src/main/jniLibs/arm64-v8a/.

## Install on Pixel 10

Enable USB debugging and connect the phone, then:

```sh
./download-model.sh
./install-pixel.sh
```

Alternatively install the APK and use Import model to select the downloaded Tensor G5 .litertlm file. The app copies the model into its own external files directory (requires roughly 3.1 GB additional storage). Tap Send after initialization; Create new session resets the Session. Output is returned after generation completes, capped at 512 tokens per turn, with a 4096-token engine context. No thinking-enabling system preface is supplied. Long conversations may exhaust context; start a new conversation then.

## Provenance

- Runtime: LiteRT-LM origin/kv-privacy-v0.18.0, commit 87203e8fdba7a951660cb57c7c9f67b6bf7dead3; includes original two scrubbing commits, excludes uncommitted DecodeNonSpeculative fix.
- Runtime target: //c:litert-lm, Android ARM64, API 31, 16 KB LOAD alignment.
- Model: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Model revision: b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1
- File: gemma-4-E2B-it_Google_Tensor_G5.litertlm (3,113,545,589 bytes)
- SHA256: af1082986639ecde7db95d91be6fe54f8b6b458104734c5bafc204e69d6852dc
- Google Tensor dispatch library: built from the same LiteRT revision as the runtime, 26895c9fbcc25c43faa8c1a98cd1fd28951602c3. Replaces the incompatible v2.2.0 prebuilt. The OSS build removes an unused internal profiling-hook dependency; see native-patches/google-tensor-oss-build.patch.
- libGemmaModelConstraintProvider.so: original branch's Git LFS prebuilt.

The app selects NPU explicitly, sets the dispatch directory to nativeLibraryDir, and declares libedgetpu_litert.so and libedgetpu_util.so as optional vendor libraries. It reports initialization errors without silently switching backend. The phone must expose compatible vendor runtime libraries; their compatibility with this model/dispatch combination needs on-device validation. Validated on connected Pixel 10 (Android 16): NPU initialization, two consecutive turns with retained KV context, and explicit session reset succeeded. Device validation notes are under diagnostics/.

The APK name is annoymous. It uses a debug signing key for local testing and is not a release-store build. The model is intentionally separate from the APK.

## Rebuild matching Google Tensor dispatch

Use the same LiteRT-LM checkout/build environment used for //c:litert-lm. Apply native-patches/google-tensor-oss-build.patch to the fetched LiteRT source (external/litert beneath the Bazel output base), then build:

```sh
ANDROID_NDK_HOME=/path/to/ndk bazel build --config=android_arm64 --macos_sdk_version=15.5 --linkopt=-Wl,-z,max-page-size=16384 @litert//litert/vendors/google_tensor/dispatch:dispatch_api_so
```

Copy bazel-bin/external/litert/litert/vendors/google_tensor/dispatch/libLiteRtDispatch_GoogleTensor.so into app/src/main/jniLibs/arm64-v8a/, then run ./gradlew assembleDebug. Do not replace it with the older v2.2.0 release library: its dispatch function table lacks the get_hooks member expected by this runtime.

Reply rendering uses Markwon 4.6.2 (core, tables, strikethrough). Supports headings, emphasis, lists, quotes, links, inline/fenced code, tables and strikethrough. Rendering is native TextView-based and loads no remote images. Only the latest reply is retained in the display.

## Images and audio

The + menu supports Choose photo and Import audio. The microphone button records up to 30 seconds; tap it again to stop. Permission is requested on first recording. Add an optional text prompt and tap Send; without text, audio is treated as a spoken chat message (considering any attached image), and images are interpreted in the current conversation context. When intent is unclear, the model is asked to briefly clarify rather than default to transcription, translation, or generic image description. Explicit typed instructions override these defaults. One image and one audio clip can be pending at a time. Tap the attachment label to remove either. Images are downsampled to at most 1600 pixels on their longest edge and EXIF orientation is applied. Imported audio must be at most 30 seconds and under 20 MB; formats must be supported by Android and the native miniaudio decoder (WAV is validated). Recording produces 16 kHz mono PCM16 WAV.

Text decoding uses NPU; vision and audio encoders use CPU. An app-specific runtime entry point uses the model's metadata and Gemma model data processor to preprocess only the current turn before Session.GenerateContent. It never creates a Conversation or stores a message history. Attachments are temporary cache files, deleted after send, on explicit removal/reset/close, or on the next launch after process death. The KV context still accumulates until New session.

To rebuild the runtime extension, run `python3 runtime-extension/install.py /path/to/LiteRT-LM`, build `//c:litert-lm` with the Android flags documented above, and copy the resulting shared library into app/src/main/jniLibs/arm64-v8a/. Keep the matching Google Tensor dispatch library.

Device test: `./gradlew connectedDebugAndroidTest` (requires the model already on the Pixel). The fixture is a synthetic red image and synthetic speech, not user media. Tests cover image identification, following-turn KV context, audio transcription, MediaFiles preparation, and a corrupt-image error. The test APK is separate from the app.

The + button opens an Add to Chat bottom sheet with Camera, Photos and Files tiles, a recent-photos shortcut, Record audio and Import audio. Camera uses the system camera with a scoped FileProvider output URI; successful captures go through the same image preparation as selected photos. Photos uses Android Photo Picker on Android 13+, with a document-picker fallback. Files is restricted to images/audio. Cancelled camera files are removed; only the camera-specific cache subdirectory is shared with the camera app.

## Live memory inspection

Tap the top-right pink ghost → enable **Debug** → **Token IDs** or **KV Cache**. Debug defaults off and is not persisted. No debug widgets appear in the chat and no per-turn snapshots are recorded. Reads run only on explicit inspection/export, while generation is idle and the execution manager owns the session executor.

Token memory separates Processed and Spare array regions with their shared base address and index ranges. A fixed Pending card shows its separate ID address and next logical position; display order does not imply adjacency. Token blocks read the executor's live token allocation, including spare capacity and the pending ID. Block details show candidate, index, region, address, int32 and original little-endian bytes. Spare capacity is not a valid token transcript; multimodal embedding positions need not have token IDs. KV opens by layer, then K/V buffer and physical slot. A slot detail reads all stored components using the tensor's actual strides, with offsets, dtype, raw value and bytes. Component values use fixed aligned columns, right-aligned numbers, alternating rows and a pinned header; long tables support horizontal and vertical scrolling. Detail and layer screens have a top-left Back button, returning to the preserved parent grid/list; Android Back dismisses only the current detail. Pink means logically active, not secure erasure; grey slots can retain data. Quantized values are raw, not dequantized. Displayed data is a point-in-time read; reopen to refresh.

**Export CSV** writes directly to a user-selected document through its file descriptor. Tokens have one row per allocated int32 slot; KV has one row per physical slot, with all component bytes in `raw_hex_le`, shape, dtype, sequence axis, address and offset. CSV omits allocation padding. **Raw + layout** preserves full selected KV allocations, including padding: `.gkv` contains `GHOSTKV1` (8 bytes), manifest length (uint64 LE), UTF-8 JSON manifest, then buffers. Manifest payload offsets are relative to the start of the binary payload. Extract with `python3 runtime-extension/unpack_kv.py capture.gkv output-directory`.

This inspects process-owned live allocations and CPU mappings of NPU TensorBuffers. It does not read arbitrary physical DRAM, freed heap blocks, other processes, or inaccessible device memory. TensorBuffer read locks can synchronize/map backend buffers. Rendering necessarily creates temporary Java/native copies; dismissing releases references but does not guarantee physical erasure of those observer copies. Export creates an explicit persistent forensic artifact. No token or KV values are written to application logs by this feature.

To rebuild: apply `runtime-extension/runtime-debug.patch` to the clean `87203e8f` runtime checkout (`git apply /path/to/runtime-debug.patch`), run `runtime-extension/install.py`, then rebuild and copy `liblitert-lm.so` as above. Also apply `runtime-extension/npu-nonspeculative-scrub.patch` before building: this fixes the missing consumed-ID scrub in unconstrained non-speculative NPU decode. Then apply `runtime-extension/token-id-scrubbing-flag.patch`. Also apply `runtime-extension/address-metadata.patch` after the flag patch. It adds an address-only query without reading token values. The native default is off; Ghost explicitly enables scrubbing through the C setter before engine creation. See `../LiteRT-LM/docs/npu_token_id_scrubbing.md` for C/C++/CLI usage and scope. Native inspection/export validation: `MemoryInspectionDeviceTest` compares int32 to source bytes, mapped KV components to `.gkv` payload offsets, CSV row counts and headers, and verifies reads leave the session step unchanged.

## Model switching

Tap the model badge or Menu → Models. Gemma 4 E2B and Gemma 3 1B both use Tensor G5 NPU. Switching closes the old engine and starts a new Session. Gemma 3 1B is text only, with a 1280-token context; camera, image and audio actions are disabled for it. Gemma 4 E2B retains multimodal input and its 4096-token configuration.

Gemma 3 source: https://huggingface.co/litert-community/Gemma3-1B-IT, revision `d448835d84411a08f577e25f3afebe528402d73b`, file `Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm` (1,678,542,365 bytes). Run `hf auth login` after accepting the model's access terms, then `./download-gemma3.sh`. E4B has been removed.

## Previous model addresses (Debug)

When Debug is enabled, closing an engine for a model/mode switch retains only the previous engine's token-array addresses, size/capacity, pending-ID addresses, and any KV CPU mapping addresses previously viewed in Debug. Only one previous engine is retained, in memory; disabling Debug clears this metadata. The address-only runtime query does not copy token values. KV addresses are expired CPU mappings, not physical GPU/NPU addresses.

Previous model addresses reads up to 256 bytes per page on demand through Linux `process_vm_readv` against this process, stripping Android's top-byte pointer tag. It never dereferences freed pointers. A read can fail or be partial. A readable or nonzero range does not establish original ownership, original token values, or whether the allocation was scrubbed before reuse. Only currently displayed read results are held for the dialog, with no previous-value snapshot or disk log.

The model selector also offers **No model**. This closes the active Session and engine without opening a replacement. With Debug enabled, previous allocation addresses remain available for on-demand inspection; the composer is disabled until a real model is selected. Choosing No model again does not overwrite retained addresses. Address inspection and ordinary UI allocations can still reuse freed memory, so this does not freeze the old heap contents.

## Screen capture setting

Open the top-right avatar in chat (the same panel as **Debug**), or **Debug** in the Attack header. **Block screenshots & screen sharing** defaults to on. Turning it off clears Android `FLAG_SECURE` for the page and its dialogs; turning it on restores it. The setting is persisted atomically in app-private storage, shared by chat and the separate dashboard process, and re-read when each activity resumes. Existing conversation storage behavior is unchanged.

Pixel verification: the protected application area is black in an ADB screenshot; after disabling the switch, both the Attack page and Settings dialog appear normally. Screen-sharing applications use the same Android window protection; a separate MediaProjection session was not tested.
