package com.example.annoymous;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.util.AtomicFile;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Shared across chat and :dashboard; avoids process-local SharedPreferences caches. */
final class ScreenPrivacy {
 private static final WeakHashMap<Window,Boolean> windows=new WeakHashMap<>();
 private static AtomicFile file(Context context){return new AtomicFile(new File(context.getFilesDir(),"screen_privacy"));}
 static boolean blocked(Context context){
  try{return !new String(file(context).readFully(),java.nio.charset.StandardCharsets.US_ASCII).equals("0");}
  catch(IOException e){return true;}
 }
 static void apply(Context context,Window window){
  if(window==null)return;
  windows.put(window,Boolean.TRUE);
  setFlag(window,blocked(context));
 }
 private static void setFlag(Window window,boolean block){
  if(block)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
 }
 static void refresh(Activity activity){
  boolean block=blocked(activity);windows.put(activity.getWindow(),Boolean.TRUE);
  for(Window window:new ArrayList<>(windows.keySet()))if(window!=null)setFlag(window,block);
 }
 static <T extends Dialog> T show(Context context,T dialog){apply(context,dialog.getWindow());dialog.show();return dialog;}
 private static void save(Context context,boolean block)throws IOException{
  AtomicFile target=file(context);FileOutputStream stream=null;
  try{stream=target.startWrite();stream.write(block?'1':'0');target.finishWrite(stream);}
  catch(IOException e){if(stream!=null)target.failWrite(stream);throw e;}
 }
 static void addControls(Activity activity,LinearLayout panel){
  int pad=Math.round(22*activity.getResources().getDisplayMetrics().density);
  Switch toggle=new Switch(activity);toggle.setText("Block screenshots & screen sharing");toggle.setTextSize(16);toggle.setPadding(0,pad/2,0,pad/2);toggle.setChecked(blocked(activity));panel.addView(toggle);
  TextView description=new TextView(activity);description.setText("Turn off to allow screenshots, screen recording and screen sharing. Applies to chat, Attack and debug views.");description.setTextSize(14);description.setTextColor(0xff81747a);description.setPadding(0,pad/2,0,0);panel.addView(description);
  toggle.setOnCheckedChangeListener((button,checked)->{
   try{save(activity,checked);refresh(activity);}
   catch(IOException e){button.setChecked(blocked(activity));Toast.makeText(activity,"Could not save screen privacy setting",Toast.LENGTH_LONG).show();}
  });
 }
 static void settings(Activity activity){
  int pad=Math.round(22*activity.getResources().getDisplayMetrics().density);
  LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(pad,pad/2,pad,pad);
  addControls(activity,panel);
  show(activity,new AlertDialog.Builder(activity).setTitle("Debug & settings").setView(panel).setPositiveButton("Done",null).create());
 }
}
