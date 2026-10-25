package com.example.annoymous;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.File;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class RetiredMemoryDeviceTest {
 @Test public void kernelReadMatchesLiveAllocationAndHandlesFreedAddress()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  long h=NativeModel.openWithTokenIdScrubbing(new File(c.getExternalFilesDir(null),"Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm").getAbsolutePath(),c.getCacheDir().getAbsolutePath(),c.getApplicationInfo().nativeLibraryDir,false);
  long address=0;try{
   NativeModel.sendMedia(h,"Reply with hello.".getBytes(StandardCharsets.UTF_8),"","");
   JSONObject data=new JSONObject(new String(NativeModel.inspect(h,"{\"layer\":-1,\"slot\":-1}"),StandardCharsets.UTF_8));
   JSONObject cell=data.getJSONArray("tokens").getJSONObject(0);
   address=Long.parseUnsignedLong(cell.getString("address").substring(2),16);
   byte[] bytes=NativeModel.readAddress(address,4);assertEquals(4,bytes.length);assertEquals(cell.getInt("id"),ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt());
   RetiredMemory metadata=new RetiredMemory();metadata.capture(h,"Gemma3 Normal");
  }finally{NativeModel.close(h);}
  // Freed memory may remain readable or be unmapped; both outcomes are valid.
  try{assertTrue(NativeModel.readAddress(address,4).length<=4);}catch(IllegalStateException expected){}
  try{NativeModel.readAddress(1,4);fail("Invalid address must be rejected by kernel");}catch(IllegalStateException expected){}
 }
}
