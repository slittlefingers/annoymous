package com.example.annoymous;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.*;
import java.nio.*;

/** Bounded 16 kHz mono PCM capture, with a standard WAV header. */
final class WavRecorder {
 interface Result { void complete(File file, Exception error); }
 private volatile boolean active;
 void start(File file, Result callback) {
  active = true;
  new Thread(() -> {
   AudioRecord recorder = null;
   Exception error = null;
   int bytes = 0;
   try {
    int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    if (minimum <= 0) throw new IOException("The microphone format is unavailable.");
    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, 8192));
    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) throw new IOException("Could not start the microphone.");
    try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
     out.setLength(0); out.write(new byte[44]);
     byte[] buffer = new byte[4096];
     recorder.startRecording();
     while (active && bytes < 16000 * 2 * 30) {
      int count = recorder.read(buffer, 0, Math.min(buffer.length, 16000 * 2 * 30 - bytes));
      if (count < 0) throw new IOException("Microphone read failed: " + count);
      if (count > 0) { out.write(buffer, 0, count); bytes += count; }
     }
     if (bytes < 3200) throw new IOException("The recording is too short. Please try again.");
     ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
     header.put(new byte[]{'R','I','F','F'}).putInt(bytes + 36).put(new byte[]{'W','A','V','E','f','m','t',' '});
     header.putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
     header.put(new byte[]{'d','a','t','a'}).putInt(bytes); out.seek(0); out.write(header.array());
    }
   } catch (SecurityException e) { error = new IOException("Microphone permission was revoked.", e); file.delete(); }
   catch (Exception e) { error = e; file.delete(); }
   finally { active = false; if (recorder != null) { try { recorder.stop(); } catch (IllegalStateException ignored) {} recorder.release(); } }
   callback.complete(error == null ? file : null, error);
  }, "ghost-microphone").start();
 }
 void stop() { active = false; }
}
