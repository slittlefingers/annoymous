package com.example.annoymous;
import android.app.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Only addresses and geometry survive a model switch. No previous byte values. */
final class RetiredMemory {
 static final class Range {
  final String name; final long address,bytes,oldSize; final boolean kv;
  Range(String name,long address,long bytes,long oldSize,boolean kv){this.name=name;this.address=address;this.bytes=bytes;this.oldSize=oldSize;this.kv=kv;}
 }
 private final ArrayList<Range> old=new ArrayList<>(),seenKv=new ArrayList<>();
 private String label=""; private long capturedAt;
 synchronized void clear(){old.clear();seenKv.clear();label="";}
 synchronized void clearKv(){seenKv.clear();}
 private static long address(String value){try{return Long.parseUnsignedLong(value.replace("0x",""),16);}catch(Exception e){return 0;}}
 synchronized void observeKv(JSONObject data){
  JSONArray layers=data.optJSONArray("layers");if(layers==null)return;
  for(int i=0;i<layers.length();i++){JSONObject l=layers.optJSONObject(i);if(l==null)continue;JSONArray buffers=l.optJSONArray("buffers");if(buffers==null)continue;
   for(int j=0;j<buffers.length();j++){JSONObject b=buffers.optJSONObject(j);if(b==null)continue;long a=address(b.optString("address"));if(a==0)continue;String name="KV layer "+l.optInt("layer")+" · "+b.optString("name");seenKv.removeIf(r->r.name.equals(name));seenKv.add(new Range(name,a,b.optLong("allocated_bytes"),0,true));}
  }
 }
 synchronized void capture(long handle,String model)throws Exception{
  old.clear();label=model;capturedAt=System.currentTimeMillis();
  JSONObject data=new JSONObject(new String(NativeModel.inspect(handle,"{\"format\":\"addresses\"}"),StandardCharsets.UTF_8));
  JSONArray arrays=data.getJSONArray("token_allocations");
  for(int i=0;i<arrays.length();i++){JSONObject a=arrays.getJSONObject(i);long ptr=address(a.optString("address")),bytes=a.optLong("capacity")*4;if(ptr!=0&&bytes>0)old.add(new Range("Token array C"+a.optInt("candidate"),ptr,bytes,a.optLong("size"),false));}
  JSONArray tokens=data.getJSONArray("pending_addresses");for(int i=0;i<tokens.length();i++){JSONObject t=tokens.getJSONObject(i);{long ptr=address(t.optString("address"));if(ptr!=0)old.add(new Range("Pending ID C"+t.optInt("candidate"),ptr,4,1,false));}}
  old.addAll(seenKv);seenKv.clear();
 }
 private static String hex(long a){return "0x"+Long.toHexString(a);}
 private LinearLayout panel(Activity a){LinearLayout p=new LinearLayout(a);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(32,20,32,20);return p;}
 private TextView text(Activity a,String value){TextView v=new TextView(a);v.setText(value);v.setTextSize(14);v.setPadding(8,12,8,12);v.setTextColor(0xff382c32);return v;}
 private AlertDialog show(Activity a,String title,LinearLayout p){ScrollView s=new ScrollView(a);s.addView(p);AlertDialog d=new AlertDialog.Builder(a).setTitle(title).setView(s).setNegativeButton("Back",null).create();ScreenPrivacy.apply(a,d.getWindow());d.show();return d;}
 void show(Activity a){
  final ArrayList<Range> ranges;final String name;final long stamp;synchronized(this){ranges=new ArrayList<>(old);name=label;stamp=capturedAt;}
  LinearLayout p=panel(a);p.addView(text(a,"Previous model: "+name+"\nAddress capture: "+(stamp==0?"none":new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date(stamp)))+"\nVirtual addresses only (kernel reads strip the Android top-byte tag). No old values saved. Readable bytes may belong to a new allocation; this cannot prove old-token survival. KV addresses are expired CPU mappings, not physical device addresses."));
  if(ranges.isEmpty())p.addView(text(a,"No previous ranges. Enable Debug before switching models. KV ranges are included only if you inspected a layer before switching."));
  for(Range r:ranges){TextView row=text(a,r.name+"  ›\n"+hex(r.address)+" · "+r.bytes+" bytes"+(r.kv?" · expired mapping":" · old size "+r.oldSize));row.setTextColor(0xffa34f74);row.setOnClickListener(v->read(a,r,0));p.addView(row);}
  show(a,"Previous model addresses",p);
 }
 private void read(Activity a,Range r,long offset){
  LinearLayout p=panel(a);int count=(int)Math.min(256,r.bytes-offset);byte[] bytes;
  p.addView(text(a,r.name+"\nOriginal base "+hex(r.address)+"\nReading now at "+hex(r.address+offset)+"\nByte offset "+offset+" / "+r.bytes+"\nReleased allocation; original ownership is unknown."));
  try{bytes=NativeModel.readAddress(r.address+offset,count);}catch(Throwable e){p.addView(text(a,"Unreadable / denied: "+e.getMessage()+"\nThis does not prove that the original bytes were erased."));show(a,"Old address · live read",p);return;}
  p.addView(text(a,"Read "+bytes.length+" of "+count+" bytes now. Values are raw int32 interpretations, not confirmed token IDs."));
  JSONArray cells=new JSONArray();ByteBuffer values=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  for(int i=0;i+4<=bytes.length;i+=4){long byteOffset=offset+i;StringBuilder raw=new StringBuilder();for(int j=0;j<4;j++)raw.append(String.format(Locale.US,"%02x",bytes[i+j]&255));
   try{cells.put(new JSONObject().put("id",values.getInt(i)).put("index",byteOffset/4).put("byte_offset",byteOffset).put("address",hex(r.address+byteOffset)).put("raw_hex",raw.toString()).put("former_region",r.name.startsWith("Pending")?"pending":byteOffset/4<r.oldSize?"processed":"spare"));}catch(JSONException e){throw new IllegalStateException(e);}
  }
  Arrays.fill(bytes,(byte)0);
  String metadata=r.name+"\nOriginal base "+hex(r.address)+" · old size "+r.oldSize+"\nByte range ["+offset+", "+(offset+count)+") / "+r.bytes+"\nRead "+values.limit()+" / "+count+" bytes now.\nPink: formerly processed/pending · Gray: formerly spare.\nReleased allocation; ownership unknown. These are current raw int32 values, not confirmed token IDs.";
  new DebugInspector(a,null).retiredTokens("Previous token memory",metadata,cells,offset>0?()->read(a,r,Math.max(0,offset-256)):null,offset+count<r.bytes?()->read(a,r,offset+count):null);

 }
}
