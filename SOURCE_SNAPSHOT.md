# Source-only GitHub snapshot

This repository contains the Ghost/annoymous Android app source, instrumentation source, runtime patches, documentation and available small reports. Model files, APKs, build caches, compiled `.so`/`.aar` files, personal captures and datasets are excluded.

Read README.md for the original build/runtime revision information. In particular, building requires the separately prepared LiteRT-LM runtime libraries under `app/src/main/jniLibs/arm64-v8a/`; Gradle does not download or rebuild all upstream runtime libraries automatically. Configure `local.properties` with your Android SDK path and set the NDK path as required by app/build.gradle. The Gradle wrapper is included.

The companion recovery repository is https://github.com/slittlefingers/kvidence . Clone both repositories as siblings when using the existing companion scripts. Device serials and personal workspace/cloud paths in this publication have been replaced with placeholders.

Retain LITERT-LM-LICENSE.txt and third-party notices when using the corresponding upstream-derived components. Historical diagnostic reports describe their own revisions and are not validation of every source snapshot.
