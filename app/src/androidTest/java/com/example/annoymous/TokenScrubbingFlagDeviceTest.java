package com.example.annoymous;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TokenScrubbingFlagDeviceTest {
 private JSONObject inspect(long h,int layer,int slot)throws Exception{return new JSONObject(new String(NativeModel.inspect(h,new JSONObject().put("layer",layer).put("slot",slot).toString()),StandardCharsets.UTF_8));}
 private String send(long h,String text){return new String(NativeModel.sendMedia(h,text.getBytes(StandardCharsets.UTF_8),"",""),StandardCharsets.UTF_8);}
 private int consumedPositive(JSONObject data)throws Exception{
  JSONArray tokens=data.getJSONArray("tokens"),allocations=data.getJSONArray("token_allocations");int positive=0;
  for(int i=0;i<tokens.length();i++){JSONObject token=tokens.getJSONObject(i);int size=allocations.getJSONObject(token.getInt("candidate")).getInt("size");if(token.getString("region").equals("live")&&token.getInt("index")<size-1&&token.getInt("id")>0)positive++;}
  return positive;
 }
 @Test public void optInChangesMemoryWithoutChangingGreedyRepliesOrKv()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String firstBaseline=null,secondBaseline=null,kvBaseline=null;
  for(boolean enabled:new boolean[]{false,true}){
   long h=NativeModel.openWithTokenIdScrubbing(new File(c.getExternalFilesDir(null),"gemma-4-E2B-it_Google_Tensor_G5.litertlm").getAbsolutePath(),c.getCacheDir().getAbsolutePath(),c.getApplicationInfo().nativeLibraryDir,enabled);
   try{
    String first=send(h,"Reply with just hello.");assertTrue(first.toLowerCase().contains("hello"));JSONObject firstState=inspect(h,-1,-1);int positive=consumedPositive(firstState);if(enabled)assertEquals("Opt-in must clear consumed text IDs",0,positive);else assertTrue("Disabled must preserve original IDs",positive>0);
    int layer=firstState.getJSONArray("layers").getJSONObject(0).getInt("layer");JSONObject mapped=inspect(h,layer,0);String kv=mapped.getJSONArray("layers").getJSONObject(0).getJSONArray("buffers").getJSONObject(0).getJSONArray("samples").getJSONObject(0).getString("hex");
    String second=send(h,"What word did you just say? Reply with that one word.");assertTrue("KV context must work in both modes",second.toLowerCase().contains("hello"));JSONObject secondState=inspect(h,-1,-1);if(enabled)assertEquals(0,consumedPositive(secondState));else assertTrue(consumedPositive(secondState)>0);
    if(!enabled){firstBaseline=first;secondBaseline=second;kvBaseline=kv;}else{assertEquals(firstBaseline,first);assertEquals(secondBaseline,second);assertEquals("Active KV slot must be unchanged by token scrubbing",kvBaseline,kv);}
    int capacity=secondState.getJSONArray("token_allocations").getJSONObject(0).getInt("capacity");NativeModel.reset(h);JSONObject reset=inspect(h,-1,-1);assertEquals(0,reset.getInt("step"));JSONObject allocation=reset.getJSONArray("token_allocations").getJSONObject(0);assertEquals(0,allocation.getInt("size"));if(enabled)assertEquals("Opt-in must release cleared token array on reset/context restore",0,allocation.getInt("capacity"));else assertTrue("Disabled must retain original allocation behavior",allocation.getInt("capacity")>=capacity);
   }finally{NativeModel.close(h);}
  }
 }
}
