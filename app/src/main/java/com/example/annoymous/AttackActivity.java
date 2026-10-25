package com.example.annoymous;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Read-only experiment viewer. Runs in :dashboard and never loads the inference engine. */
public final class AttackActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService reader=Executors.newSingleThreadExecutor();
 private boolean active,reading; private String runId="",batchKey="",contentKey=""; private int shownPositions;
 private TextView state,label,metrics,rule,position,phase,recovered,summary,connection;
 private LinearLayout chips,candidates; private Button chat;
 private JSONObject snapshot;
 private final Runnable poll=new Runnable(){public void run(){refresh();if(active)handler.postDelayed(this,1000);}};
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
 private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout card(LinearLayout parent){LinearLayout c=column();c.setPadding(dp(18),dp(14),dp(18),dp(14));c.setBackground(shape(0xffffffff,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);parent.addView(c,lp);return c;}
 private Button button(String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(0xff914d6a);b.setOnClickListener(v->action.run());return b;}
 private String distanceLabel(JSONObject record){String metric=record==null?"L2":record.optString("distance_metric","l2").toUpperCase(java.util.Locale.ROOT);if(record!=null&&"collision_plus_cpa".equals(record.optString("method")))metric="L2";return "lsb".equals(record==null?"":record.optString("distance_space"))?"KV "+metric+" (LSB steps)":"Raw KV "+metric;}
 private String number(double x){return String.format(Locale.US,"%.3f",x);}
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);ScreenPrivacy.apply(this,getWindow());
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root=column();root.setBackgroundColor(0xfff7f5f5);
  root.setOnApplyWindowInsetsListener((v,w)->{Insets i=w.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+i.left,i.top,dp(18)+i.right,i.bottom);return w;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(72)));
  TextView title=text("Attack",27,0xff272229);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  header.addView(button("采集",()->startActivity(new Intent(this,CollectionActivity.class))));
  header.addView(button("Debug",()->ScreenPrivacy.settings(this)));
  chat=button("Chat",()->{startActivity(new Intent(this,MainActivity.class));finish();});header.addView(chat);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=column();scroll.addView(body);
  LinearLayout top=card(body);state=text("Waiting for a reconstruction",20,0xff914d6a);state.setTypeface(null,Typeface.BOLD);top.addView(state);
  label=text("Start a chat recovery with live events enabled.",12,0xff82777f);top.addView(label);
  metrics=text("— accepted    ·    — candidates",17,0xff272229);top.addView(metrics);
  connection=text("No experiment connected",12,0xff82777f);top.addView(connection);
  rule=text("Accepted means the candidate passed the configured KV test. Identity is checked separately after recovery.",12,0xff82777f);top.addView(rule);
  LinearLayout current=card(body);position=text("Current position",18,0xff272229);position.setTypeface(null,Typeface.BOLD);current.addView(position);
  phase=text("Waiting for candidates…",13,0xff82777f);current.addView(phase);candidates=column();current.addView(candidates);
  LinearLayout tokens=card(body);tokens.addView(text("Recovered sequence",18,0xff272229));recovered=text("No accepted tokens yet.",16,0xff493b45);recovered.setTextIsSelectable(true);tokens.addView(recovered);
  tokens.addView(text("Tap a position to inspect every evaluated batch.",12,0xff82777f));
  HorizontalScrollView strip=new HorizontalScrollView(this);chips=new LinearLayout(this);strip.addView(chips);tokens.addView(strip);
  LinearLayout end=card(body);end.addView(text("Summary",18,0xff272229));summary=text("The summary updates as the experiment runs.",14,0xff655963);end.addView(summary);
  setContentView(root);
 }
 @Override protected void onResume(){super.onResume();ScreenPrivacy.refresh(this);active=true;handler.post(poll);}
 @Override protected void onPause(){active=false;handler.removeCallbacks(poll);super.onPause();}
 @Override protected void onDestroy(){reader.shutdownNow();super.onDestroy();}
 private void refresh(){
  if(reading)return;reading=true;
  reader.execute(()->{JSONObject data=null;String error=null;
   try{File f=new File(getFilesDir(),"attack_live.json");if(f.isFile()){if(f.length()>4*1024*1024)throw new IOException("Snapshot too large");data=new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}}
   catch(Exception e){error=e.getMessage();}
   final JSONObject result=data;final String problem=error;runOnUiThread(()->{reading=false;if(!active)return;if(result!=null)render(result);else if(problem!=null)connection.setText("Monitor read error: "+problem);});
  });
 }
 private void render(JSONObject d){
  snapshot=d;String id=d.optString("run_id");if(!id.equals(runId)){runId=id;shownPositions=0;chips.removeAllViews();batchKey="";candidates.removeAllViews();}
  String status=d.optString("status","waiting");boolean running=status.equals("running")||status.equals("waiting");
  double age=Math.max(0,System.currentTimeMillis()/1000.-d.optDouble("relay_time",0));
  state.setText(running?(age>8?"Updates paused":"Reconstruction in progress"):status.equals("complete")?"Reconstruction complete":status.replace('_',' '));
  chat.setEnabled(!running);chat.setContentDescription(running?"Chat unavailable during reconstruction":"Open chat");
  connection.setText((age>8?"Last sync "+Math.round(age)+"s ago":"Connected · refreshes every second")+"   ·   "+number(d.optDouble("elapsed_seconds"))+" s");
  String nextContent=id+":"+d.optDouble("event_time")+":"+d.optString("evaluation")+":"+status;
  if(nextContent.equals(contentKey))return;contentKey=nextContent;
  label.setText(d.optString("label"));metrics.setText(d.optInt("accepted")+" / "+d.optInt("planned_positions")+" accepted   ·   "+d.optInt("total_queries")+" candidates");
  connection.setText((age>8?"Last sync "+Math.round(age)+"s ago":"Connected · refreshes every second")+"   ·   "+number(d.optDouble("elapsed_seconds"))+" s");
  JSONObject cfg=d.optJSONObject("config");String method=cfg==null?"":cfg.optString("method");
  rule.setText((method.equals("collision_plus_cpa")?"Collision+ CPA · calibrated K/V thresholds "+cfg.optJSONArray("kv_thresholds")+" (+1%)":method+" · "+distanceLabel(cfg)+" < "+(cfg==null?4:cfg.optDouble("epsilon")))+"\nAccepted ≠ verified identity. Green tiles pass the threshold; pink marks the selected token.");
  JSONObject cur=d.optJSONObject("current"),batch=d.optJSONObject("batch");
  if(cur!=null)position.setText("Position "+cur.optInt("position")+"  ·  token "+(cur.optInt("index")+1));
  Integer chosen=null;JSONArray completed=d.optJSONArray("positions");if(cur!=null&&completed!=null)for(int i=0;i<completed.length();i++){JSONObject r=completed.optJSONObject(i);if(r!=null&&r.optInt("index",-1)==cur.optInt("index")&&!r.isNull("accepted_token_id"))chosen=r.optInt("accepted_token_id");}
  if(batch!=null){String key=id+":"+batch.optLong("sequence")+":"+chosen;phase.setText("Batch "+batch.optInt("batch")+" · "+batch.optString("phase")+" · "+batch.optInt("queries")+" queried");if(!key.equals(batchKey)){batchKey=key;renderBatch(candidates,batch,chosen);}}
  else{phase.setText("Preparing the next position…");candidates.removeAllViews();batchKey="";}
  String restored=d.optString("recovered_text");recovered.setText(restored.isEmpty()?"No decoded accepted text yet.":restored);
  JSONArray rows=d.optJSONArray("positions");if(rows!=null){while(shownPositions<rows.length()){JSONObject r=rows.optJSONObject(shownPositions++);if(r==null)continue;String token=r.optString("piece").replace('\n',' ');Button chip=button("#"+r.optInt("position")+"\n"+(token.isEmpty()?r.optString("accepted_token_id","—"):token),()->openPosition(d,r));chip.setBackground(shape(r.optBoolean("committed")?0xfff8e3ed:0xfff1dfd8,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(90),dp(68));lp.rightMargin=dp(7);chips.addView(chip,lp);}}
  String info="Method: "+method+"\nAccepted: "+d.optInt("accepted")+" / "+d.optInt("planned_positions")+"\nEvaluated candidates: "+d.optInt("total_queries")+"\nElapsed: "+number(d.optDouble("elapsed_seconds"))+" s";
  JSONObject evaluation=d.optJSONObject("evaluation");if(evaluation!=null)info+="\nVerified token IDs: "+evaluation.optInt("correct_ids")+" / "+evaluation.optInt("truth_tokens")+"\nFalse acceptances: "+evaluation.optInt("false_acceptances");else info+="\nIdentity verification: not evaluated";
  if(d.has("error"))info+="\n"+d.optString("error");summary.setText(info);
  if(cur!=null){int idx=cur.optInt("index");JSONObject selectedRow=new JSONObjectBuilder().position(idx,cur.optInt("position"));if(completed!=null)for(int i=0;i<completed.length();i++){JSONObject r=completed.optJSONObject(i);if(r!=null&&r.optInt("index",-1)==idx)selectedRow=r;}final JSONObject detailRow=selectedRow;position.setOnClickListener(v->openPosition(d,detailRow));}
 }
 // A tiny builder keeps click listeners free of checked JSON exceptions.
 private static final class JSONObjectBuilder extends JSONObject {JSONObject position(int index,int position){try{put("index",index);put("position",position);}catch(JSONException ignored){}return this;}}
 private void renderBatch(LinearLayout parent,JSONObject batch,Integer accepted){
  parent.removeAllViews();JSONArray ids=batch.optJSONArray("token_ids"),dist=batch.optJSONArray("distances"),pieces=batch.optJSONArray("pieces"),pass=batch.optJSONArray("passing_ids"),components=batch.optJSONArray("kv_distances");if(ids==null||dist==null)return;
  HashSet<Integer> passing=new HashSet<>();if(pass!=null)for(int i=0;i<pass.length();i++)passing.add(pass.optInt(i));
  final int[] page={0};LinearLayout nav=new LinearLayout(this);TextView pages=text("",12,0xff82777f);LinearLayout tiles=column();
  Runnable draw=()->{tiles.removeAllViews();int first=page[0]*32,last=Math.min(ids.length(),first+32);pages.setText((first+1)+"–"+last+" / "+ids.length());LinearLayout row=null;
   for(int i=first;i<last;i++){if((i-first)%4==0){row=new LinearLayout(this);tiles.addView(row);}final int at=i,id=ids.optInt(i);double l2=dist.optDouble(i);TextView tile=text(id+"\n"+number(l2),11,0xff43353e);tile.setGravity(Gravity.CENTER);tile.setTypeface(Typeface.MONOSPACE);tile.setBackground(shape(accepted!=null&&accepted==id?0xfff1bed6:passing.contains(id)?0xffdceee3:0xfff2eff1,10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(tile,lp);
    tile.setOnClickListener(v->{String detail="Token ID: "+id+"\nBatch candidate: "+(at+1)+"\nOverall query: "+(batch.optInt("queries")-ids.length()+at+1)+"\n"+distanceLabel(batch)+": "+dist.optDouble(at)+"\nPhase: "+batch.optString("phase")+"\nThreshold: "+(passing.contains(id)?"passed":"not passed");if(pieces!=null)detail+="\nToken text: "+pieces.optString(at);if(components!=null)detail+="\nK / V L2: "+components.optJSONArray(at);ScreenPrivacy.show(this,new AlertDialog.Builder(this).setTitle("Candidate").setMessage(detail).setPositiveButton("Back",null).create());});
   }
  };
  nav.addView(button("‹",()->{if(page[0]>0){page[0]--;draw.run();}}));nav.addView(pages,new LinearLayout.LayoutParams(0,-2,1));nav.addView(button("›",()->{if((page[0]+1)*32<ids.length()){page[0]++;draw.run();}}));parent.addView(nav);parent.addView(tiles);draw.run();
 }
 private void openPosition(JSONObject source,JSONObject positionRow){
  final String archive=source.optString("archive");final int index=positionRow.optInt("index");
  LinearLayout panel=column();panel.setPadding(dp(16),dp(8),dp(16),dp(8));TextView description=text("Loading recorded batches…",14,0xff655963);panel.addView(description);
  LinearLayout controls=new LinearLayout(this);panel.addView(controls);ScrollView scroll=new ScrollView(this);LinearLayout detail=column();scroll.addView(detail);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(330)));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Position "+positionRow.optInt("position")).setView(panel).setPositiveButton("Back",null).create();ScreenPrivacy.show(this,dialog);
  reader.execute(()->{try{
   File file=new File(getFilesDir(),archive).getCanonicalFile();if(!file.getPath().startsWith(new File(getFilesDir(),"attack_runs").getCanonicalPath()+File.separator))throw new IOException("Invalid archive path");
   ArrayList<Long> offsets=new ArrayList<>();try(RandomAccessFile input=new RandomAccessFile(file,"r")){long end=input.length();while(input.getFilePointer()<end){long offset=input.getFilePointer();String line=input.readLine();if(line==null)break;JSONObject event=new JSONObject(new String(line.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8));if(event.optString("type").equals("batch")&&event.optInt("index",-1)==index)offsets.add(offset);}}
   runOnUiThread(()->{if(isFinishing()||!dialog.isShowing())return;if(offsets.isEmpty()){description.setText("No completed candidate batch recorded yet.");return;}final int[] selected={Math.max(0,Math.min(offsets.size()-1,positionRow.optInt("accepted_batch",offsets.size())-1))};
    Runnable show=()->{int selection=selected[0];reader.execute(()->{try(RandomAccessFile input=new RandomAccessFile(file,"r")){input.seek(offsets.get(selection));String line=input.readLine();JSONObject batch=new JSONObject(new String(line.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8));runOnUiThread(()->{if(!dialog.isShowing())return;description.setText("Batch "+(selection+1)+" / "+offsets.size()+" · "+batch.optString("phase")+"\n"+batch.optInt("queries")+" candidates evaluated"+(positionRow.has("committed")?"\n"+(positionRow.optBoolean("committed")?"Accepted":"Unresolved")+" · selected ID "+positionRow.optString("accepted_token_id")+" · batch "+positionRow.optString("accepted_batch")+"\nCommit "+distanceLabel(batch)+": "+positionRow.optString("commit_distance",positionRow.optString("commit_l2")):"\nSearch in progress"));renderBatch(detail,batch,positionRow.isNull("accepted_token_id")?null:positionRow.optInt("accepted_token_id"));});}catch(Exception e){runOnUiThread(()->description.setText("Could not read batch: "+e.getMessage()));}});};
    controls.addView(button("Previous batch",()->{if(selected[0]>0){selected[0]--;show.run();}}));controls.addView(button("Next batch",()->{if(selected[0]+1<offsets.size()){selected[0]++;show.run();}}));show.run();
   });
  }catch(Exception e){runOnUiThread(()->description.setText("Could not open history: "+e.getMessage()));}});
 }
}
