package com.example.annoymous;
final class NativeModel {
 static { System.loadLibrary("GemmaModelConstraintProvider"); System.loadLibrary("litert-lm"); System.loadLibrary("annoymous"); }
 static long open(String model, String cache, String libraries) {
  // Ghost opts in explicitly; the native library default remains unchanged.
  return openWithTokenIdScrubbing(model, cache, libraries, true);
 }
 static long openWithTokenIdScrubbing(String model, String cache, String libraries, boolean enabled) {
  return openWithBackend(model,cache,libraries,"npu",enabled);
 }
 static native long openWithBackend(String model,String cache,String libraries,String backend,boolean enabled);
 static native byte[] send(long handle, byte[] prompt);
 static native byte[] sendMedia(long handle, byte[] message, String image, String audio);
 static native byte[] inspect(long handle, String query);
 static native byte[] readAddress(long address,int count);
 static native void reset(long handle);
 static native void close(long handle);
}
