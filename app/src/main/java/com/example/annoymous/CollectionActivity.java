package com.example.annoymous;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/** Native control client; the acquisition worker remains independent of this screen. */
public final class CollectionActivity extends Activity {
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private TextView status,detail; private EditText name,context; private Spinner profiles;
 private Button pause,resume,change,create; private boolean active,busy; private String profileKey="";
 private final Runnable poll=new Runnable(){public void run(){request("status",null);if(active)handler.postDelayed(this,3000);}};
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private Button button(LinearLayout parent,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());parent.addView(b);return b;}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);ScreenPrivacy.apply(this,getWindow());
  LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(18),dp(20),dp(24));body.setBackgroundColor(0xfff7f5f5);
  body.setOnApplyWindowInsetsListener((v,w)->{android.graphics.Insets i=w.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(20)+i.left,dp(12)+i.top,dp(20)+i.right,dp(24)+i.bottom);return w;});
  ScrollView scroll=new ScrollView(this);scroll.addView(body);setContentView(scroll);
  TextView title=new TextView(this);title.setText("数据采集");title.setTextSize(28);body.addView(title);
  status=new TextView(this);status.setTextSize(23);status.setText("连接电脑控制服务…");body.addView(status);
  detail=new TextView(this);detail.setTextSize(15);body.addView(detail);
  pause=button(body,"暂停采集",()->request("pause",new JSONObject()));
  resume=button(body,"启动 / 继续",()->request("resume",new JSONObject()));
  TextView label=new TextView(this);label.setText("上下文配置（先暂停，再切换）");body.addView(label);
  profiles=new Spinner(this);body.addView(profiles);
  change=button(body,"切换到所选配置",()->{try{request("profile",new JSONObject().put("name",String.valueOf(profiles.getSelectedItem())));}catch(Exception e){error(e);}});
  name=new EditText(this);name.setHint("新配置名，例如 context_short");name.setSingleLine(true);body.addView(name);
  context=new EditText(this);context.setHint("图片前的文本；完整多轮对话请填 messages JSON 数组\n例如 [{\"role\":\"user\",\"content\":\"请看图片\"}]");context.setMinLines(4);context.setGravity(Gravity.TOP);body.addView(context);
  create=button(body,"创建上下文配置",()->{try{
   String text=context.getText().toString().trim();JSONArray messages;
   if(text.startsWith("["))messages=new JSONArray(text);
   else messages=new JSONArray().put(new JSONObject().put("role","user").put("content",text));
   request("create",new JSONObject().put("name",name.getText().toString().trim()).put("messages",messages));
  }catch(Exception e){error(e);}});
  TextView note=new TextView(this);note.setText("每 500 张休息 5 分钟。切换配置后使用独立数据目录。手机需要通过 USB 连接运行控制服务的电脑；关闭此页面不停止采集。");body.addView(note);
  button(body,"返回 Ghost",()->{startActivity(new Intent(this,MainActivity.class));finish();});
 }
 private void error(Exception e){detail.setText("控制未完成："+e.getMessage());}
 private JSONObject exchange(String endpoint,JSONObject payload)throws Exception{
  File file=new File(getExternalFilesDir(null),"collection_connection.json");
  JSONObject cfg=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
  // USB adb reverse exposes the computer's loopback service only to this device.
  URL url=new URL("http://127.0.0.1:8765/api/"+endpoint);
  HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(4000);c.setReadTimeout(10000);
  try{
   if(payload!=null){c.setRequestMethod("POST");c.setRequestProperty("X-Control-Token",cfg.getString("token"));c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}}
   int code=c.getResponseCode();InputStream stream=code<400?c.getInputStream():c.getErrorStream();
   String text;try(InputStream in=stream){text=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
   if(code>=400)throw new IOException(text);return new JSONObject(text);
  }finally{c.disconnect();}
 }
 private void request(String endpoint,JSONObject payload){
  if(busy)return;busy=true;
  io.execute(()->{try{JSONObject result=exchange(endpoint,payload);if(!endpoint.equals("status"))result=exchange("status",null);final JSONObject data=result;
   runOnUiThread(()->{busy=false;if(active)render(data);});
  }catch(Exception e){runOnUiThread(()->{busy=false;if(active){status.setText("控制服务未连接");error(e);}});}});
 }
 private void render(JSONObject d){
  String mode=d.optString("mode");status.setText(d.optString("profile")+"\n"+d.optInt("completed")+" / "+d.optInt("total"));
  JSONObject build=d.optJSONObject("build"),thermal=d.optJSONObject("thermal");
  String phase=mode.equals("paused")?"已暂停":mode.equals("pausing")?"正在保存并关闭 worker":mode.equals("waiting")?"等待休息结束":mode.equals("complete")?"采集完成":mode.equals("running")?"采集 / 初始化中":mode;
  if(build!=null&&mode.equals("running")&&build.optString("stage").startsWith("cooling"))phase="自动休息中";
  detail.setText(phase+" · 休息剩余 "+d.optInt("cooldown")+" 秒\n热状态："+(thermal==null?"未知":thermal.optInt("thermal_status"))+ (d.isNull("error")?"":"\n"+d.optString("error")));
  pause.setEnabled((d.optBoolean("active")||mode.equals("waiting"))&&!mode.equals("pausing"));resume.setEnabled(!d.optBoolean("active")&&!mode.equals("waiting")&&!mode.equals("pausing")&&!mode.equals("complete"));change.setEnabled(!d.optBoolean("active")&&!mode.equals("pausing")&&!mode.equals("waiting"));
  JSONArray list=d.optJSONArray("profiles");String key=list==null?"":list.toString();if(!key.equals(profileKey)&&list!=null){profileKey=key;String[] names=new String[list.length()];int selected=0;for(int i=0;i<names.length;i++){names[i]=list.optJSONObject(i).optString("name");if(names[i].equals(d.optString("profile")))selected=i;}profiles.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));profiles.setSelection(selected);}
 }
 @Override protected void onResume(){super.onResume();active=true;handler.post(poll);}
 @Override protected void onPause(){active=false;handler.removeCallbacks(poll);super.onPause();}
 @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
