package com.example.annoymous;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

/** Opt-in diagnostic. Does not open the interactive chat or change its code. */
@RunWith(AndroidJUnit4.class)
public class CandidateBatchProbeDeviceTest {
 private static native int runProbe(String[] arguments,String logPath);
 @Test public void probe()throws Exception {
  String job=InstrumentationRegistry.getArguments().getString("probe_job");
  Assume.assumeNotNull(job);
  if(!job.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("Invalid job ID");
  Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
  // Load the exact production runtime before the standalone diagnostic wrapper.
  System.load(target.getApplicationInfo().nativeLibraryDir+"/liblitert-lm.so");
  System.loadLibrary("candidate_probe");
  File root=new File(target.getFilesDir(),job);
  File model=new File(target.getExternalFilesDir(null),"gemma-4-E2B-it_Google_Tensor_G5.litertlm");
  String[] args={model.getAbsolutePath(),target.getApplicationInfo().nativeLibraryDir,
   new File(root,"inputs").getAbsolutePath(),new File(root,"results").getAbsolutePath()};
  assertEquals("See results/native.log",0,runProbe(args,new File(root,"results/native.log").getAbsolutePath()));
 }
}
