package com.example.annoymous;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

/** Opt-in persistent NPU worker. The interactive chat is not opened. */
@RunWith(AndroidJUnit4.class)
public class ReconstructionDeviceTest {
 private static native int runWorker(String[] arguments,String logPath);
 @Test public void worker() throws Exception {
  String job=InstrumentationRegistry.getArguments().getString("reconstruction_job");
  Assume.assumeNotNull(job);
  if(!job.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid job ID");
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  System.load(c.getApplicationInfo().nativeLibraryDir+"/liblitert-lm.so");
  System.loadLibrary("kvreconstruction");
  File root=new File(c.getFilesDir(),job);
  String model=InstrumentationRegistry.getArguments().getString("reconstruction_model");
  if(model==null)model=new File(c.getExternalFilesDir(null),"gemma-4-E2B-it_Google_Tensor_G5.litertlm").getAbsolutePath();
  if(!new File(model).isAbsolute())throw new IllegalArgumentException("Model path must be absolute");
  assertEquals("Inspect native.log",0,runWorker(new String[]{
   model,
   c.getApplicationInfo().nativeLibraryDir,root.getAbsolutePath()},new File(root,"native.log").getAbsolutePath()));
 }
}
