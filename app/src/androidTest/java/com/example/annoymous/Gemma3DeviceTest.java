package com.example.annoymous;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import org.json.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class Gemma3DeviceTest {
 @Test public void npuLoadsAndReplies() throws Exception {
 Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
 for(boolean scrub:new boolean[]{false,true}){
 long h=NativeModel.openWithTokenIdScrubbing(new File(c.getExternalFilesDir(null),"Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm").getAbsolutePath(),c.getCacheDir().getAbsolutePath(),c.getApplicationInfo().nativeLibraryDir,scrub);
 try{
  String reply=new String(NativeModel.sendMedia(h,"Remember the word sunflower. Reply with that word only.".getBytes(StandardCharsets.UTF_8),"",""),StandardCharsets.UTF_8);assertTrue(reply,reply.toLowerCase().contains("sunflower"));
  String next=new String(NativeModel.sendMedia(h,"What word did I ask you to remember? Reply with the word only.".getBytes(StandardCharsets.UTF_8),"",""),StandardCharsets.UTF_8);assertTrue(next,next.toLowerCase().contains("sunflower"));
  JSONObject data=new JSONObject(new String(NativeModel.inspect(h,"{\"layer\":-1,\"slot\":-1}"),StandardCharsets.UTF_8));
  JSONArray tokens=data.getJSONArray("tokens"),allocations=data.getJSONArray("token_allocations");int positive=0;
  for(int i=0;i<tokens.length();i++){JSONObject t=tokens.getJSONObject(i);int size=allocations.getJSONObject(t.getInt("candidate")).getInt("size");if(t.getString("region").equals("live")&&t.getInt("index")<size-1&&t.getInt("id")>0)positive++;}
  if(scrub)assertEquals(0,positive);else assertTrue(positive>0);
 }finally{NativeModel.close(h);}
 }
 }
}
