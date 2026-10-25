package com.example.annoymous;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class MemoryInspectionDeviceTest {
 private JSONObject read(long h,int layer,int slot)throws Exception{return new JSONObject(new String(NativeModel.inspect(h,new JSONObject().put("layer",layer).put("slot",slot).toString()),StandardCharsets.UTF_8));}
 private byte[] export(long h,Context c,int layer,String format)throws Exception{
  File f=new File(c.getCacheDir(),"forensic-test-export");
  try{try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_READ_WRITE)){NativeModel.inspect(h,new JSONObject().put("layer",layer).put("format",format).put("fd",fd.getFd()).toString());}return Files.readAllBytes(f.toPath());}finally{f.delete();}
 }
 private byte[] unhex(String hex){byte[] b=new byte[hex.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);return b;}
 @Test public void actualAllocationsAndExports()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();long h=0;
  try{
   h=NativeModel.open(new File(c.getExternalFilesDir(null),"gemma-4-E2B-it_Google_Tensor_G5.litertlm").getAbsolutePath(),c.getCacheDir().getAbsolutePath(),c.getApplicationInfo().nativeLibraryDir);
   NativeModel.sendMedia(h,"Reply with just hello.".getBytes(StandardCharsets.UTF_8),"","");
   JSONObject before=read(h,-1,-1);JSONArray tokens=before.getJSONArray("tokens");assertTrue(tokens.length()>0);
   for(int i=0;i<tokens.length();i++){JSONObject t=tokens.getJSONObject(i);assertEquals(t.getInt("id"),ByteBuffer.wrap(unhex(t.getString("raw_hex"))).order(ByteOrder.LITTLE_ENDIAN).getInt());assertTrue(t.getString("address").startsWith("0x"));}
   JSONArray allocations=before.getJSONArray("token_allocations");
   for(int i=0;i<tokens.length();i++){JSONObject t=tokens.getJSONObject(i);if(!t.getString("region").equals("live"))continue;int size=allocations.getJSONObject(t.getInt("candidate")).getInt("size");if(t.getInt("index")<size-1&&t.getInt("id")>=0)assertEquals("Consumed text ID must be scrubbed at index "+t.getInt("index"),0,t.getInt("id"));}
   String csv=new String(export(h,c,-1,"tokens_csv"),StandardCharsets.UTF_8);assertTrue(csv.startsWith("capture_ns,candidate,index,region,address,byte_offset,id_int32,raw_hex_le\n"));assertEquals(tokens.length()+1,csv.split("\n").length);
   JSONArray layers=before.getJSONArray("layers");assertTrue(layers.length()>0);int layer=layers.getJSONObject(0).getInt("layer");
   JSONObject mapped=read(h,layer,0);JSONArray buffers=mapped.getJSONArray("layers").getJSONObject(0).getJSONArray("buffers");assertTrue(buffers.length()>0);
   byte[] raw=export(h,c,layer,"kv_raw");assertEquals("GHOSTKV1",new String(raw,0,8,StandardCharsets.US_ASCII));long length=ByteBuffer.wrap(raw,8,8).order(ByteOrder.LITTLE_ENDIAN).getLong();JSONObject manifest=new JSONObject(new String(raw,16,(int)length,StandardCharsets.UTF_8));JSONArray descriptions=manifest.getJSONArray("buffers");long total=16+length;
   int slots=0;
   for(int i=0;i<buffers.length();i++){
    JSONObject b=buffers.getJSONObject(i),meta=descriptions.getJSONObject(i);assertEquals(b.getString("name"),meta.getString("name"));total+=meta.getLong("allocated_bytes");slots+=b.getInt("capacity");JSONArray samples=b.getJSONArray("samples");assertEquals(b.getInt("capacity"),samples.length());JSONObject cell=samples.getJSONObject(0);assertTrue(cell.getBoolean("full"));JSONArray offsets=cell.getJSONArray("component_byte_offsets");byte[] hex=unhex(cell.getString("hex"));int width=b.getInt("element_bytes");assertEquals(offsets.length()*width,hex.length);
    for(int j=0;j<offsets.length();j++){long offset=16+length+meta.getLong("payload_offset")+offsets.getLong(j);for(int k=0;k<width;k++)assertEquals(raw[(int)offset+k],hex[j*width+k]);}
   }
   assertEquals(total,raw.length);
   String kv=new String(export(h,c,layer,"kv_csv"),StandardCharsets.UTF_8);assertTrue(kv.startsWith("capture_ns,layer,buffer,dtype,shape,sequence_axis,slot,logical_position,active,address,first_byte_offset,raw_hex_le\n"));assertEquals(slots+1,kv.split("\n").length);
   assertEquals(before.getInt("step"),read(h,-1,-1).getInt("step"));
   String follow=new String(NativeModel.sendMedia(h,"What word did you just say? Reply with that one word.".getBytes(StandardCharsets.UTF_8),"",""),StandardCharsets.UTF_8);assertTrue("Session must retain previous response in KV",follow.toLowerCase().contains("hello"));
   JSONObject second=read(h,-1,-1);JSONArray secondTokens=second.getJSONArray("tokens"),secondAllocations=second.getJSONArray("token_allocations");for(int i=0;i<secondTokens.length();i++){JSONObject t=secondTokens.getJSONObject(i);if(t.getString("region").equals("live")&&t.getInt("index")<secondAllocations.getJSONObject(t.getInt("candidate")).getInt("size")-1&&t.getInt("id")>=0)assertEquals("Consumed second-turn ID must be scrubbed",0,t.getInt("id"));}
   NativeModel.reset(h);assertEquals(0,read(h,-1,-1).getInt("step"));
  }finally{if(h!=0)NativeModel.close(h);}
 }
}
