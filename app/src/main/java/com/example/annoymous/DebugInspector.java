package com.example.annoymous;

import android.app.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.nio.*;
import java.util.*;

/** On-demand views of live native allocations. No response history or snapshot cache. */
final class DebugInspector {
 interface Result { void accept(JSONObject data); }
 interface Source { void read(int layer,int slot,Result result); void export(int layer,String format); }
 private final Activity activity; private final Source source;
 private static final int INK=0xff382c32, ROSE=0xffa34f74;
 DebugInspector(Activity activity,Source source){this.activity=activity;this.source=source;}
 private int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String value,int size,int color){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
 private TextView button(String label,Runnable action){TextView v=text(label,14,ROSE);v.setGravity(Gravity.CENTER);v.setPadding(dp(14),dp(10),dp(14),dp(10));v.setBackground(shape(0xfff7e7ee,16));v.setOnClickListener(w -> action.run());return v;}
 private LinearLayout dialog(String title,String subtitle,Dialog[] holder){return dialog(title,subtitle,holder,false);}
 private LinearLayout dialog(String title,String subtitle,Dialog[] holder,boolean child){
  Dialog d=new Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE);holder[0]=d;
  LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(20),dp(18),dp(16));root.setBackground(shape(0xfffafaf8,26));
  LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
  if(child){TextView back=button("‹ Back",d::dismiss);back.setContentDescription("Back to previous view");back.setMinHeight(dp(44));header.addView(back);}
  TextView name=text(title,22,INK);name.setTypeface(null,Typeface.BOLD);if(child)name.setPadding(dp(12),0,0,0);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));if(!child)header.addView(button("Close",d::dismiss));root.addView(header);
  TextView note=text(subtitle,12,0xff7e7478);note.setPadding(0,dp(12),0,dp(16));root.addView(note);
  d.setContentView(root);Window window=d.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);ScreenPrivacy.apply(activity,window);window.setDimAmount(0.25f);}
  d.show();if(window!=null)window.setLayout(activity.getResources().getDisplayMetrics().widthPixels-dp(24),Math.round(activity.getResources().getDisplayMetrics().heightPixels*0.83f));
  // Release display-only references when closed. Native reads are never retained elsewhere.
  d.setOnDismissListener(w -> root.removeAllViews());return root;
 }
 void tokens(){source.read(-1,-1,this::tokenGrid);}
 void layers(){source.read(-1,-1,this::layerList);}
 void retiredTokens(String title,String metadata,JSONArray cells,Runnable previous,Runnable next){
  Dialog[] holder={null};LinearLayout root=dialog(title,metadata,holder,true);
  grid(root,cells,(cell,index)->Integer.toString(cell.optInt("id")),(cell,index)->"#"+cell.optLong("index"),
   (cell,index)->cell.optString("former_region").equals("spare")?0xffeeeae8:0xfff3dce6,
   (cell,index)->details("Old array slot #"+cell.optLong("index"),"Former region: "+cell.optString("former_region")+"\nValue now (int32): "+cell.optInt("id")+"\nVirtual address: "+cell.optString("address")+"\nByte offset: "+cell.optLong("byte_offset")+"\nRaw bytes (little-endian): "+cell.optString("raw_hex")+"\n\nReleased allocation. Original ownership is unknown. This is not a confirmed surviving token ID."));
  LinearLayout pages=new LinearLayout(activity);
  if(previous!=null)pages.addView(button("‹ Previous bytes",()->{holder[0].dismiss();previous.run();}));
  if(next!=null)pages.addView(button("Next bytes ›",()->{holder[0].dismiss();next.run();}));
  if(previous!=null||next!=null)root.addView(pages);
 }
 private void tokenGrid(JSONObject data){
  JSONArray tokens=data.optJSONArray("tokens");if(tokens==null)tokens=new JSONArray();
  JSONArray processed=new JSONArray(),spare=new JSONArray(),pending=new JSONArray();
  for(int i=0;i<tokens.length();i++){JSONObject cell=tokens.optJSONObject(i);if(cell==null)continue;String region=cell.optString("region");if(region.equals("pending"))pending.put(cell);else if(region.equals("spare"))spare.put(cell);else processed.put(cell);}
  final JSONArray processedCells=processed,spareCells=spare;final JSONArray allocations=data.optJSONArray("token_allocations");
  Dialog[] holder={null};LinearLayout root=dialog("Token memory","Array storage and pending ID are separate allocations.\nScreen order does not imply adjacent memory addresses.",holder);
  LinearLayout tabs=new LinearLayout(activity);tabs.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout region=new LinearLayout(activity);region.setOrientation(LinearLayout.VERTICAL);
  TextView processedTab=button("Processed · "+processed.length(),()->showTokenArray(region,processedCells,allocations,false));
  TextView spareTab=button("Spare · "+spare.length(),()->showTokenArray(region,spareCells,allocations,true));
  tabs.addView(processedTab);LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(6);tabs.addView(spareTab,gap);root.addView(tabs);
  TextView export=button("Export all regions · CSV",()->{holder[0].dismiss();source.export(-1,"tokens_csv");});LinearLayout.LayoutParams exportLayout=new LinearLayout.LayoutParams(-1,-2);exportLayout.topMargin=dp(8);exportLayout.bottomMargin=dp(10);root.addView(export,exportLayout);
  root.addView(region,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout separate=new LinearLayout(activity);separate.setOrientation(LinearLayout.VERTICAL);separate.setPadding(dp(12),dp(12),dp(12),dp(8));separate.setBackground(shape(0xfff7e7ee,18));LinearLayout.LayoutParams footer=new LinearLayout.LayoutParams(-1,-2);footer.topMargin=dp(12);root.addView(separate,footer);
  TextView heading=text("Pending · separate allocation",15,ROSE);heading.setTypeface(null,Typeface.BOLD);separate.addView(heading);
  if(pending.length()==0){TextView empty=text("No pending token",12,0xff7e7478);empty.setPadding(0,dp(8),0,dp(4));separate.addView(empty);}
  for(int i=0;i<pending.length();i++){JSONObject cell=pending.optJSONObject(i);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,0);separate.addView(row);
   TextView value=text(Integer.toString(cell.optInt("id")),18,INK);value.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);value.setGravity(Gravity.CENTER);value.setBackground(shape(0xffe5c2d3,12));row.addView(value,new LinearLayout.LayoutParams(dp(56),dp(56)));
   TextView address=text("Candidate "+cell.optInt("candidate")+" · next position #"+cell.optInt("index")+"\nID address "+cell.optString("address")+"\nNot an element of token_ids[]",11,0xff7e7478);address.setPadding(dp(12),0,0,0);row.addView(address,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->tokenDetails(cell));
  }
  showTokenArray(region,processedCells,allocations,false);
 }
 private void showTokenArray(LinearLayout region,JSONArray cells,JSONArray allocations,boolean spare){
  region.removeAllViews();StringBuilder info=new StringBuilder(spare?"Array spare capacity · raw bytes, not valid token IDs":"Processed array · in-size slots, including overwritten zeros");
  if(allocations!=null)for(int i=0;i<allocations.length();i++){JSONObject allocation=allocations.optJSONObject(i);if(allocation==null)continue;info.append("\nC").append(allocation.optInt("candidate")).append(" · base ").append(allocation.optString("address")).append("\n").append(spare?"Spare range [":"Processed range [").append(spare?allocation.optInt("size"):0).append(", ").append(allocation.optInt(spare?"capacity":"size")).append(") · 4 bytes per array slot");}
  TextView meta=text(info.toString(),11,0xff7e7478);meta.setPadding(0,0,0,dp(10));region.addView(meta);
  grid(region,cells,(cell,index)->Integer.toString(cell.optInt("id")),(cell,index)->"#"+cell.optInt("index"),
   (cell,index)->spare?0xffeeeae8:0xfff3dce6,(cell,index)->tokenDetails(cell));
 }
 private void tokenDetails(JSONObject cell){
  boolean pending=cell.optString("region").equals("pending");String region=cell.optString("region").equals("live")?"processed / in-size (not a scrub result)":cell.optString("region");
  details(pending?"Pending token":"Array slot #"+cell.optInt("index"),"Candidate: "+cell.optInt("candidate")+"\nStorage region: "+region+"\n"+(pending?"Next logical position: ":"Array index: ")+cell.optInt("index")+"\nValue (int32): "+cell.optInt("id")+"\nAddress: "+cell.optString("address")+(pending?"\nSeparate TokenData allocation; no array byte offset.":"\nArray byte offset: "+cell.optLong("byte_offset"))+"\nRaw bytes (little-endian): "+cell.optString("raw_hex")+"\n\n"+(pending?"This ID is stored separately from token_ids[]. Its logical position does not describe physical placement.":"Processed and spare slots belong to the same array allocation. Spare bytes are not necessarily valid tokens. Multimodal positions may exist only as embeddings."));
 }
 private void layerList(JSONObject data){
  Dialog[] holder={null};LinearLayout root=dialog("KV Cache","Live NPU cache · "+data.optInt("kv_positions")+" logical positions\nChoose a layer to map its buffers. Reads do not scan unrelated or freed process memory.",holder);
  LinearLayout exports=new LinearLayout(activity);exports.addView(button("All layers · CSV",() -> {holder[0].dismiss();source.export(-1,"kv_csv");}));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(8);exports.addView(button("Raw + layout",() -> {holder[0].dismiss();source.export(-1,"kv_raw");}),gap);root.addView(exports);
  ScrollView scroll=new ScrollView(activity);LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,dp(12),0,0);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  JSONArray layers=data.optJSONArray("layers");if(layers==null)return;
  for(int i=0;i<layers.length();i++){JSONObject layer=layers.optJSONObject(i);if(layer==null)continue;int id=layer.optInt("layer");JSONArray buffers=layer.optJSONArray("buffers");long bytes=0;Set<Integer> capacities=new TreeSet<>();if(buffers!=null)for(int j=0;j<buffers.length();j++){JSONObject b=buffers.optJSONObject(j);bytes+=b.optLong("allocated_bytes");capacities.add(b.optInt("capacity"));}
   LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(16),dp(14),dp(16),dp(14));row.setBackground(shape(0xfff5e5ec,18));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);layout.bottomMargin=dp(8);list.addView(row,layout);
   TextView name=text(String.format(Locale.US,"Layer %02d  ›",id),17,INK);name.setTypeface(null,Typeface.BOLD);row.addView(name);row.addView(text((buffers==null?0:buffers.length())+" buffers · "+String.format(Locale.US,"%.2f MiB",bytes/1048576.0)+" · slots "+capacities,12,0xff87717c));
   row.setOnClickListener(v -> source.read(id,-1,result -> {kvLayer(result,id);}));
  }
 }
 private void kvLayer(JSONObject data,int layerId){
  JSONArray layers=data.optJSONArray("layers");if(layers==null||layers.length()==0){details("KV Cache","This layer is unavailable.");return;}
  JSONObject layer=layers.optJSONObject(0);JSONArray buffers=layer.optJSONArray("buffers");if(buffers==null||buffers.length()==0)return;
  Dialog[] holder={null};LinearLayout root=dialog("Layer "+layerId,"Physical slots · Tap a block for all stored components\nPink: logically active. Grey: unused or stale. Color is not a memory-zeroization result.",holder,true);
  LinearLayout exportBar=new LinearLayout(activity);exportBar.addView(button("Export CSV",() -> {holder[0].dismiss();source.export(layerId,"kv_csv");}));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(8);exportBar.addView(button("Raw + layout",() -> {holder[0].dismiss();source.export(layerId,"kv_raw");}),gap);root.addView(exportBar);
  LinearLayout tabs=new LinearLayout(activity);tabs.setPadding(0,dp(10),0,dp(10));root.addView(tabs);
  LinearLayout region=new LinearLayout(activity);region.setOrientation(LinearLayout.VERTICAL);root.addView(region,new LinearLayout.LayoutParams(-1,0,1));
  for(int i=0;i<buffers.length();i++){JSONObject buffer=buffers.optJSONObject(i);if(buffer==null)continue;TextView tab=button(buffer.optString("name").replace("kv_cache_","").toUpperCase(Locale.US),() -> bufferGrid(region,buffer,layerId));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(6);tabs.addView(tab,lp);}
  bufferGrid(region,buffers.optJSONObject(0),layerId);
 }
 private void bufferGrid(LinearLayout region,JSONObject buffer,int layer){
  region.removeAllViews();TextView meta=text(buffer.optString("name")+" · "+buffer.optString("dtype")+"\nShape "+buffer.optJSONArray("shape")+" · axis "+buffer.optInt("sequence_axis")+"\nMapped address "+buffer.optString("address"),12,0xff7e7478);meta.setPadding(0,0,0,dp(12));region.addView(meta);
  JSONArray cells=buffer.optJSONArray("samples");if(cells==null)cells=new JSONArray();final String name=buffer.optString("name");
  grid(region,cells,(cell,index) -> Integer.toString(cell.optInt("slot")),(cell,index) -> cell.optBoolean("active")?"pos "+cell.optInt("position"):"unused",
    (cell,index) -> cell.optBoolean("active")?0xfff3dce6:0xffeeeae8,
    (cell,index) -> source.read(layer,cell.optInt("slot"),data -> showKvValues(data,name,cell.optInt("slot"))));
 }
 private void showKvValues(JSONObject data,String name,int slot){
  JSONArray layers=data.optJSONArray("layers");if(layers==null||layers.length()==0)return;JSONArray buffers=layers.optJSONObject(0).optJSONArray("buffers");if(buffers==null)return;
  for(int i=0;i<buffers.length();i++){JSONObject buffer=buffers.optJSONObject(i);if(!name.equals(buffer.optString("name")))continue;JSONArray samples=buffer.optJSONArray("samples");if(samples==null||slot>=samples.length())return;JSONObject value=samples.optJSONObject(slot);
   String raw=value.optString("hex"),dtype=buffer.optString("dtype");int width=buffer.optInt("element_bytes",1);JSONArray offsets=value.optJSONArray("component_byte_offsets");
   StringBuilder body=new StringBuilder("Buffer: ").append(name).append("\nPhysical slot: ").append(slot).append("\nLogical position: ").append(value.optInt("position")).append("\nActive (logical): ").append(value.optBoolean("active")).append("\nBase address: ").append(buffer.optString("address")).append("\nType: ").append(dtype).append(" (raw storage, not dequantized)\nStrides: ").append(buffer.optJSONArray("strides")).append("\nValues are raw storage, not dequantized.");
   List<String[]> rows=new ArrayList<>();
   for(int j=0;j<raw.length()/(width*2);j++){String item=raw.substring(j*width*2,(j+1)*width*2);rows.add(new String[]{Integer.toString(j),offsets==null?"?":Long.toString(offsets.optLong(j)),decode(item,dtype),item});}
   kvValueTable("Slot "+slot,body.toString(),rows);return;
  }
 }
 private void kvValueTable(String title,String metadata,List<String[]> rows){
  Dialog[] holder={null};LinearLayout root=dialog(title,metadata,holder,true);
  String[] headers={"Component","Byte offset","Value","Raw LE"};int[] widths=new int[4];
  TextView measure=text("",12,INK);measure.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
  for(int column=0;column<4;column++){float widest=measure.getPaint().measureText(headers[column]);for(String[] row:rows)widest=Math.max(widest,measure.getPaint().measureText(row[column]));widths[column]=(int)Math.ceil(widest)+dp(24);}
  HorizontalScrollView horizontal=new HorizontalScrollView(activity);horizontal.setHorizontalScrollBarEnabled(true);root.addView(horizontal,new LinearLayout.LayoutParams(-1,0,1));
  int total=0;for(int width:widths)total+=width;
  LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);horizontal.addView(content,new HorizontalScrollView.LayoutParams(total,-1));
  content.addView(kvTableRow(headers,widths,true,0),new LinearLayout.LayoutParams(total,-2));
  ScrollView vertical=new ScrollView(activity);content.addView(vertical,new LinearLayout.LayoutParams(total,0,1));
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);vertical.addView(body);
  for(int row=0;row<rows.size();row++)body.addView(kvTableRow(rows.get(row),widths,false,row),new LinearLayout.LayoutParams(total,-2));
 }
 private LinearLayout kvTableRow(String[] values,int[] widths,boolean header,int row){
  LinearLayout line=new LinearLayout(activity);line.setOrientation(LinearLayout.HORIZONTAL);line.setBackgroundColor(header?0xfff3dce6:(row%2==0?0xfffafaf8:0xfff0eeec));
  for(int column=0;column<4;column++){TextView cell=text(values[column],12,header?ROSE:INK);cell.setTypeface(Typeface.MONOSPACE,header?Typeface.BOLD:Typeface.NORMAL);cell.setSingleLine(true);cell.setPadding(dp(12),dp(9),dp(12),dp(9));cell.setGravity(Gravity.CENTER_VERTICAL|(column==3?Gravity.START:Gravity.END));cell.setTextIsSelectable(!header);line.addView(cell,new LinearLayout.LayoutParams(widths[column],-2));}
  return line;
 }
 private String decode(String hex,String dtype){
  try{byte[] bytes=new byte[hex.length()/2];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);ByteBuffer b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
   switch(dtype){case "int8":return Byte.toString(b.get());case "uint8":return Integer.toString(b.get()&255);case "int16":return Short.toString(b.getShort());case "int32":return Integer.toString(b.getInt());case "float32":return Float.toString(b.getFloat());case "float16":int h=b.getShort()&65535;int sign=(h&32768)<<16,exp=(h>>10)&31,m=h&1023;int bits;if(exp==0){if(m==0)bits=sign;else{int e=-14;while((m&1024)==0){m<<=1;e--;}bits=sign|((e+127)<<23)|((m&1023)<<13);}}else bits=sign|((exp==31?255:exp+112)<<23)|(m<<13);return Float.toString(Float.intBitsToFloat(bits));default:return "0x"+hex;}
  }catch(Exception e){return "0x"+hex;}
 }
 private interface Label {String value(JSONObject cell,int index);}
 private interface Color {int value(JSONObject cell,int index);}
 private interface Click {void value(JSONObject cell,int index);}
 private void grid(LinearLayout root,JSONArray cells,Label top,Label bottom,Color color,Click click){
  ScrollView scroll=new ScrollView(activity);GridLayout grid=new GridLayout(activity);int columns=Math.max(4,Math.min(7,(activity.getResources().getDisplayMetrics().widthPixels/dp(1)-64)/54));grid.setColumnCount(columns);scroll.addView(grid);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout pager=new LinearLayout(activity);pager.setGravity(Gravity.CENTER_VERTICAL);TextView page=text("",12,0xff7e7478);page.setGravity(Gravity.CENTER);int[] current={0};Runnable[] paint={null};
  TextView prev=button("‹ Prev",() -> {if(current[0]>0){current[0]--;paint[0].run();}}),next=button("Next ›",() -> {if((current[0]+1)*120<cells.length()){current[0]++;paint[0].run();}});pager.addView(prev);pager.addView(page,new LinearLayout.LayoutParams(0,-2,1));pager.addView(next);root.addView(pager);
  paint[0]=() -> {grid.removeAllViews();int start=current[0]*120,end=Math.min(start+120,cells.length());int side=(activity.getResources().getDisplayMetrics().widthPixels-dp(64))/columns;
   for(int i=start;i<end;i++){JSONObject cell=cells.optJSONObject(i);if(cell==null)continue;final int index=i;LinearLayout block=new LinearLayout(activity);block.setOrientation(LinearLayout.VERTICAL);block.setGravity(Gravity.CENTER);block.setBackground(shape(color.value(cell,i),10));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=side-dp(4);lp.height=side-dp(4);lp.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(block,lp);TextView id=text(top.value(cell,i),12,INK);id.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);id.setGravity(Gravity.CENTER);id.setMaxLines(1);id.setAutoSizeTextTypeUniformWithConfiguration(7,12,1,android.util.TypedValue.COMPLEX_UNIT_SP);block.addView(id);TextView position=text(bottom.value(cell,i),9,0xff8c737e);position.setGravity(Gravity.CENTER);block.addView(position);block.setOnClickListener(v -> click.value(cell,index));}
   page.setText(cells.length()==0?"No allocated slots":(start+1)+"–"+end+" / "+cells.length());prev.setAlpha(current[0]==0?0.35f:1);next.setAlpha(end>=cells.length()?0.35f:1);scroll.scrollTo(0,0);};paint[0].run();
 }
 private void details(String title,String content){ScrollView scroll=new ScrollView(activity);TextView text=text(content,12,INK);text.setTypeface(Typeface.MONOSPACE);text.setTextIsSelectable(true);text.setPadding(dp(20),dp(12),dp(20),dp(12));scroll.addView(text);AlertDialog d=new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Back",null).create();d.setOnDismissListener(v -> text.setText(""));ScreenPrivacy.apply(activity,d.getWindow());d.show();}
}
