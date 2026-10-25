package com.example.annoymous;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
 private final ExecutorService worker = Executors.newSingleThreadExecutor();
 private io.noties.markwon.Markwon markdown;
 private TextView attachmentStatus; private ImageButton microphone;
 private File pendingImage, pendingAudio, pendingCamera; private final WavRecorder recorder=new WavRecorder();
 private boolean recording, destroyed, debugEnabled;
 private boolean ghostMode=true; private TextView chatTitle; private ImageView welcomeIcon;
 private final RetiredMemory retiredMemory=new RetiredMemory();
 private String loadedModelLabel="";
 private DebugInspector inspector; private String exportFormat; private int exportLayer;
 private TextView status, transcript; private EditText input; private ImageButton send, load, fresh;
 private ScrollView scroll; private LinearLayout welcome; private long handle; private boolean busy;
 private static final int NO_MODEL=2;
 private int selectedModel=0; private int importModel=0; private TextView modelBadge;
 private static final String[] MODEL_FILES={"gemma-4-E2B-it_Google_Tensor_G5.litertlm","Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm"};
 private static final String[] MODEL_NAMES={"Gemma 4 E2B","Gemma 3 1B"};
 private static final long[] MODEL_SIZES={3113545589L,1678542365L};
 private File modelFile(int index){return new File(getExternalFilesDir(null),MODEL_FILES[index]);}
 private File modelFile() { return modelFile(selectedModel); }
 private String modelName(){return selectedModel==NO_MODEL?"No model":MODEL_NAMES[selectedModel];}
 private String backendName(){return "NPU";}
 private boolean modelReady(int index){if(index<0||index>=MODEL_FILES.length)return false;File f=modelFile(index);return f.isFile()&&f.length()==MODEL_SIZES[index];}
 private void showModels(){
  if(busy||recording)return;
  String[] labels=new String[3];for(int i=0;i<2;i++)labels[i]=MODEL_NAMES[i]+(i==0?" · NPU":" · NPU · Text only")+(modelReady(i)?"":" · Import")+(i==selectedModel?"  ✓":"");
  labels[NO_MODEL]="No model"+(selectedModel==NO_MODEL?"  ✓":"");
  ScreenPrivacy.show(this,new android.app.AlertDialog.Builder(this).setTitle("Choose model").setItems(labels,(d,index)->{
   if(index==NO_MODEL){selectNoModel();return;}
   if(!modelReady(index)){importModel=index;pickFile("*/*",1);return;}
   if(index==selectedModel&&handle!=0)return;
   selectedModel=index;
   clearAttachments();input.setText("");transcript.setText("");welcome.setVisibility(android.view.View.VISIBLE);updateChatMode();initialize();
  }).setNegativeButton("Close",null).create());
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  if(state!=null){ghostMode=state.getBoolean("ghost_mode",true);selectedModel=state.getInt("selected_model",0);importModel=state.getInt("import_model",0);if(selectedModel<0||selectedModel>NO_MODEL)selectedModel=0;if(importModel<0||importModel>1)importModel=0;}
  File[] stale=getCacheDir().listFiles((dir,name) -> name.startsWith("ghost_attachment_"));
  if(stale!=null)for(File file:stale)file.delete();
  File cameraDirectory=new File(getCacheDir(),"ghost_camera");cameraDirectory.mkdirs();
  if(state!=null&&state.getString("camera_file")!=null){String name=state.getString("camera_file");if(name.startsWith("ghost_attachment_")&&!name.contains("/"))pendingCamera=new File(cameraDirectory,name);}
  if(pendingCamera==null){File[] oldPhotos=cameraDirectory.listFiles();if(oldPhotos!=null)for(File file:oldPhotos)file.delete();}
  markdown=io.noties.markwon.Markwon.builder(this)
   .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(this))
   .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
   .usePlugin(new io.noties.markwon.AbstractMarkwonPlugin(){
    @Override public void configureTheme(io.noties.markwon.core.MarkwonTheme.Builder builder){
     builder.linkColor(0xffa34f74).codeTextColor(0xff57374a).codeBackgroundColor(0xfff5e9ef).codeBlockBackgroundColor(0xfff0f1ec).blockQuoteColor(0xffd390ad);
    }
   }).build();
  ScreenPrivacy.apply(this,getWindow());
  getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  LinearLayout root = new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setBackgroundColor(0xfffafaf8);
  root.setOnApplyWindowInsetsListener((v,w) -> {android.graphics.Insets inset=w.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());v.setPadding(dp(16)+inset.left,inset.top,dp(16)+inset.right,dp(10)+inset.bottom);return w;});

  android.widget.FrameLayout header = new android.widget.FrameLayout(this);
  root.addView(header,new LinearLayout.LayoutParams(-1,dp(82)));
  ImageButton menu=icon(R.drawable.ic_menu,"Menu",false);
  android.widget.FrameLayout.LayoutParams left=new android.widget.FrameLayout.LayoutParams(dp(48),dp(48),Gravity.START|Gravity.CENTER_VERTICAL);
  header.addView(menu,left);
  TextView title=new TextView(this); chatTitle=title; title.setText("Ghost chat");title.setTextColor(0xff181916);title.setTextSize(18);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);
  android.widget.FrameLayout.LayoutParams centered=new android.widget.FrameLayout.LayoutParams(-2,-1,Gravity.CENTER);header.addView(title,centered);
  fresh=icon(R.drawable.ic_ghost,"New session",false);
  fresh.setBackground(surface(0xfffce8ef,30,false)); fresh.setPadding(dp(7),dp(7),dp(7),dp(7));
  header.addView(fresh,new android.widget.FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.CENTER_VERTICAL));

  android.widget.FrameLayout content=new android.widget.FrameLayout(this);
  root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  welcome=new LinearLayout(this);welcome.setOrientation(LinearLayout.VERTICAL);welcome.setGravity(Gravity.CENTER);welcome.setPadding(dp(18),0,dp(18),0);
  ImageView ghost=new ImageView(this);welcomeIcon=ghost;ghost.setImageResource(R.drawable.ic_ghost_dark);welcome.addView(ghost,new LinearLayout.LayoutParams(dp(38),dp(42)));
  TextView note=new TextView(this);note.setText("A little space for your thoughts.");note.setTextSize(19);note.setTextColor(0xff30312d);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(24),0,dp(10));welcome.addView(note);
  TextView detail=new TextView(this);detail.setText("Chats stay on this device. Only the latest reply is displayed. Start a new session to clear the context.");detail.setTextSize(14);detail.setTextColor(0xff777873);detail.setGravity(Gravity.CENTER);detail.setLineSpacing(dp(3),1);welcome.addView(detail);
  content.addView(welcome,new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
  scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);
  transcript=new TextView(this);transcript.setTextSize(18);transcript.setTextColor(0xff252621);transcript.setLineSpacing(dp(6),1);transcript.setPadding(dp(12),dp(24),dp(12),dp(24));transcript.setTextIsSelectable(true);scroll.addView(transcript);
  content.addView(scroll,new android.widget.FrameLayout.LayoutParams(-1,-1));

  status=new TextView(this);status.setText("Gemma 4 E2B · On-device NPU");status.setTextColor(0xff81827c);status.setTextSize(12);status.setGravity(Gravity.CENTER);status.setPadding(dp(6),dp(8),dp(6),dp(12));root.addView(status);
  LinearLayout composer=new LinearLayout(this);composer.setOrientation(LinearLayout.VERTICAL);composer.setPadding(dp(12),dp(6),dp(12),dp(12));composer.setBackground(surface(0xffffffff,26,true));composer.setElevation(dp(2));root.addView(composer,new LinearLayout.LayoutParams(-1,-2));
  input=new EditText(this);input.setHint("Message ghost");input.setTextSize(20);input.setTextColor(0xff252621);input.setHintTextColor(0xff90918b);input.setBackgroundColor(android.graphics.Color.TRANSPARENT);input.setPadding(dp(4),dp(10),dp(4),dp(12));input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);input.setMaxLines(4);input.setMinHeight(dp(58));composer.addView(input,new LinearLayout.LayoutParams(-1,-2));
  attachmentStatus=new TextView(this);attachmentStatus.setTextSize(12);attachmentStatus.setTextColor(0xffa34f74);attachmentStatus.setPadding(dp(4),0,dp(4),dp(8));attachmentStatus.setVisibility(android.view.View.GONE);composer.addView(attachmentStatus);attachmentStatus.setOnClickListener(v -> removeAttachment());
  LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);composer.addView(toolbar);
  load=icon(R.drawable.ic_plus,"Add attachment",false);toolbar.addView(load,new LinearLayout.LayoutParams(dp(40),dp(40)));
  TextView model=new TextView(this);modelBadge=model;model.setText(modelName()+" ▾");model.setOnClickListener(v -> showModels());model.setContentDescription("Choose model");model.setTextColor(0xff3e4039);model.setTextSize(13);model.setTypeface(null,android.graphics.Typeface.BOLD);model.setGravity(Gravity.CENTER);model.setPadding(dp(15),0,dp(15),0);model.setBackground(surface(0xfff1f2ed,20,false));LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(-2,dp(38));badge.leftMargin=dp(8);toolbar.addView(model,badge);
  android.view.View spacer=new android.view.View(this);toolbar.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
  microphone=icon(R.drawable.ic_microphone,"Record audio",false);LinearLayout.LayoutParams micLayout=new LinearLayout.LayoutParams(dp(40),dp(40));micLayout.rightMargin=dp(8);toolbar.addView(microphone,micLayout);microphone.setOnClickListener(v -> toggleRecording());
  send=icon(R.drawable.ic_send,"Send message",true);toolbar.addView(send,new LinearLayout.LayoutParams(dp(42),dp(42)));
  setContentView(root);
  menu.setOnClickListener(v -> showSidebar());
  load.setOnClickListener(v -> showAttachments());
  send.setOnClickListener(v -> sendMessage());
  inspector=new DebugInspector(this,new DebugInspector.Source(){
   public void read(int layer,int slot,DebugInspector.Result result){readDebug(layer,slot,result);}
   public void export(int layer,String format){chooseDebugExport(layer,format);}
  });
  updateChatMode();
  fresh.setOnClickListener(v -> showSessionTools());
  if(modelReady(selectedModel)) initialize(); else setBusy(false,selectedModel==NO_MODEL?"No model · Engine unloaded":"Tap the model name to import a model.");
 }

 private void startNewSession(){ if(busy) return; if(handle==0){status.setText("Load a model first.");return;} setBusy(true,"Starting a new session…"); worker.execute(() -> { try { if(handle != 0) NativeModel.reset(handle);retiredMemory.clearKv(); runOnUiThread(() -> {clearAttachments(); transcript.setText(""); welcome.setVisibility(android.view.View.VISIBLE); setBusy(false,"New session · Ready");}); } catch(Throwable e) { showError(e); } });}
 private void updateChatMode(){
  chatTitle.setText(ghostMode?"Ghost chat":"Normal chat");
  fresh.setImageResource(ghostMode?R.drawable.ic_ghost:R.drawable.ic_account);
  fresh.setBackground(surface(ghostMode?0xfffce8ef:0xffe9eef6,30,false));
  fresh.setContentDescription(ghostMode?"Ghost chat tools":"Normal chat tools");
  welcomeIcon.setImageResource(ghostMode?R.drawable.ic_ghost_dark:R.drawable.ic_chat);
  input.setHint(ghostMode?"Message ghost":"Message Gemma");
  modelBadge.setText(modelName()+" ▾");
 }
 private void switchChatMode(boolean next){
  if(busy||recording||next==ghostMode)return;
  
  // This is an engine setting: close the old engine before opening the new one.
  ghostMode=next;clearAttachments();input.setText("");transcript.setText("");welcome.setVisibility(android.view.View.VISIBLE);updateChatMode();
  if(selectedModel!=NO_MODEL&&modelReady(selectedModel))initialize();
 }
 private void showSessionTools(){
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(12),dp(22),dp(12));
  TextView modeHeading=new TextView(this);modeHeading.setText("Chat mode");modeHeading.setTextSize(14);modeHeading.setTextColor(0xff81747a);panel.addView(modeHeading);
  android.app.AlertDialog[] picker={null};
  panel.addView(sidebarRow(R.drawable.ic_ghost,"Ghost chat"+(ghostMode?"  ✓":""),ghostMode,()->{picker[0].dismiss();switchChatMode(true);}));
  panel.addView(sidebarRow(R.drawable.ic_account,"Normal chat"+(!ghostMode?"  ✓":""),!ghostMode,()->{picker[0].dismiss();switchChatMode(false);}));
  TextView modeNote=new TextView(this);modeNote.setText("Ghost clears consumed token IDs. Normal keeps the original runtime behavior. Switching starts a new session.");modeNote.setTextSize(13);modeNote.setTextColor(0xff81747a);modeNote.setPadding(0,dp(10),0,dp(16));panel.addView(modeNote);
  Switch toggle=new Switch(this);toggle.setText("Debug");toggle.setTextSize(18);toggle.setChecked(debugEnabled);panel.addView(toggle,new LinearLayout.LayoutParams(-1,dp(56)));
  TextView note=new TextView(this);note.setText("Read live session memory on demand. No automatic capture or history.");note.setTextColor(0xff81747a);note.setPadding(0,0,0,dp(12));panel.addView(note);
  LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);panel.addView(options);options.setVisibility(debugEnabled?android.view.View.VISIBLE:android.view.View.GONE);
  TextView privacyHeading=new TextView(this);privacyHeading.setText("Screen privacy");privacyHeading.setTextSize(14);privacyHeading.setTextColor(0xff81747a);privacyHeading.setPadding(0,dp(18),0,0);panel.addView(privacyHeading);
  ScreenPrivacy.addControls(this,panel);
  ScrollView toolsScroll=new ScrollView(this);toolsScroll.addView(panel);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle(ghostMode?"Ghost chat":"Normal chat").setView(toolsScroll).setNegativeButton("Close",null).setNeutralButton("New session",(d,w)->startNewSession()).create();
  picker[0]=dialog;
  options.addView(sidebarRow(R.drawable.ic_file,"Token IDs",false,()->{dialog.dismiss();inspector.tokens();}));
  options.addView(sidebarRow(R.drawable.ic_info,"KV Cache",false,()->{dialog.dismiss();inspector.layers();}));
  options.addView(sidebarRow(R.drawable.ic_file,"Previous model addresses",false,()->{if(busy||recording)return;dialog.dismiss();retiredMemory.show(this);}));
  toggle.setOnCheckedChangeListener((v,on)->{debugEnabled=on;if(!on)retiredMemory.clear();options.setVisibility(on?android.view.View.VISIBLE:android.view.View.GONE);});
  ScreenPrivacy.apply(this,dialog.getWindow());dialog.show();
 }
 private boolean canInspect(){if(!debugEnabled)return false;if(busy||recording||handle==0){Toast.makeText(this,"Wait until the model is ready.",Toast.LENGTH_SHORT).show();return false;}return true;}
 private android.app.AlertDialog debugProgress(String title){android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setMessage(title).setCancelable(false).create();ScreenPrivacy.apply(this,d.getWindow());d.show();return d;}
 private void readDebug(int layer,int slot,DebugInspector.Result callback){
  if(!canInspect())return;String previous=status.getText().toString();setBusy(true,"Reading live memory…");android.app.AlertDialog progress=debugProgress("Reading live memory…");
  worker.execute(()->{try{org.json.JSONObject query=new org.json.JSONObject().put("layer",layer).put("slot",slot).put("format","inspect");org.json.JSONObject data=new org.json.JSONObject(new String(NativeModel.inspect(handle,query.toString()),StandardCharsets.UTF_8));runOnUiThread(()->{progress.dismiss();if(destroyed)return;setBusy(false,previous);if(debugEnabled){if(layer>=0)retiredMemory.observeKv(data);callback.accept(data);}});}catch(Throwable e){runOnUiThread(()->progress.dismiss());showError(e);}});
 }
 private void chooseDebugExport(int layer,String format){
  if(!canInspect())return;exportLayer=layer;exportFormat=format;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(format.equals("kv_raw")?"application/octet-stream":"text/csv");intent.putExtra(Intent.EXTRA_TITLE,"ghost-"+(format.equals("tokens_csv")?"token-ids":"kv"+(layer<0?"-all":"-layer-"+layer))+(format.equals("kv_raw")?".gkv":".csv"));startActivityForResult(intent,71);
 }
 private void writeDebugExport(Uri uri,int layer,String format){
  if(!canInspect())return;String previous=status.getText().toString();setBusy(true,"Exporting live memory…");android.app.AlertDialog progress=debugProgress("Exporting live memory…");
  worker.execute(()->{try(android.os.ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"wt")){if(fd==null)throw new IOException("Cannot open the export destination.");org.json.JSONObject query=new org.json.JSONObject().put("layer",layer).put("format",format).put("fd",fd.getFd());NativeModel.inspect(handle,query.toString());runOnUiThread(()->{progress.dismiss();if(destroyed)return;setBusy(false,previous);Toast.makeText(this,"Export saved",Toast.LENGTH_SHORT).show();});}catch(Throwable e){try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}runOnUiThread(()->progress.dismiss());showError(e);}});
 }

 private void setBusy(boolean b,String text) { busy=b; status.setText(text); send.setEnabled(!b&&handle!=0); send.setAlpha(send.isEnabled()?1f:0.35f); load.setEnabled(!b&&handle!=0&&selectedModel==0); microphone.setEnabled(!b&&handle!=0&&selectedModel==0); load.setAlpha(load.isEnabled() ? 1f : 0.35f); fresh.setEnabled(!b); modelBadge.setEnabled(!b); input.setEnabled(!b&&handle!=0); if(b)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
 private void showError(Throwable e) { runOnUiThread(() -> {if(destroyed)return;setBusy(false,"Error: "+e.getMessage()+(handle==0?". Choose a model to retry.":". If the context is full, start a new session."));}); }
 private void closeCurrentEngine(boolean rememberAddresses){
  if(handle==0)return;
  if(rememberAddresses){try{retiredMemory.capture(handle,loadedModelLabel);}catch(Exception e){retiredMemory.clear();}}else retiredMemory.clear();
  NativeModel.close(handle);handle=0;loadedModelLabel="";
 }
 private void selectNoModel(){
  if(busy||recording)return;
  final boolean remember=debugEnabled;selectedModel=NO_MODEL;
  clearAttachments();input.setText("");transcript.setText("");welcome.setVisibility(android.view.View.VISIBLE);updateChatMode();
  setBusy(true,"Unloading model…");
  worker.execute(()->{try{closeCurrentEngine(remember);runOnUiThread(()->{if(!destroyed)setBusy(false,"No model · Engine unloaded");});}catch(Throwable e){showError(e);}});
 }
 private void initialize() {
  if(selectedModel==NO_MODEL)return;
  final boolean scrub=ghostMode,rememberAddresses=debugEnabled;
  final String path=modelFile().getAbsolutePath(),backend="npu",ready="Ready · "+modelName()+" · "+backendName();
  setBusy(true,"Loading "+modelName()+"…");
  worker.execute(() -> { try { closeCurrentEngine(rememberAddresses); handle=NativeModel.openWithBackend(path,getCacheDir().getAbsolutePath(),getApplicationInfo().nativeLibraryDir,backend,scrub);loadedModelLabel=path.substring(path.lastIndexOf('/')+1)+(scrub?" · Ghost":" · Normal"); runOnUiThread(() -> {if(!destroyed)setBusy(false,ready);}); } catch(Throwable e){showError(e);} });
 }
 private void sendMessage() {
  String typed=input.getText().toString().trim(); if(busy||recording)return;
  if(typed.isEmpty()&&pendingImage==null&&pendingAudio==null)return;
  if(handle==0){if(selectedModel==NO_MODEL){status.setText("No model · Choose a model to chat.");return;}if(modelReady(selectedModel))initialize();else status.setText("Import a model first.");return;}
  final File image=pendingImage, audio=pendingAudio;
  final String text=typed.isEmpty()?(audio!=null
   ? "Treat the audio as my spoken message and respond naturally to what I say, using the current conversation context. If an image is attached, consider it too. Do not merely transcribe or translate unless I ask you to. If my intent is unclear, ask a brief clarifying question."
   : "Consider this image together with our current conversation and respond naturally to my likely intent. If it is unclear what I want help with, ask a brief clarifying question. Do not default to a generic image description."):typed;
  pendingImage=null;pendingAudio=null;updateAttachments();
  input.setText("");transcript.setText("");welcome.setVisibility(android.view.View.GONE);setBusy(true,"Thinking…");
  worker.execute(() -> {try {
   String answer=new String(NativeModel.sendMedia(handle,text.getBytes(StandardCharsets.UTF_8),image==null?"":image.getAbsolutePath(),audio==null?"":audio.getAbsolutePath()),StandardCharsets.UTF_8);
   runOnUiThread(() -> {if(destroyed)return;markdown.setMarkdown(transcript,answer);setBusy(false,"Ready · Session context retained");scroll.post(() -> scroll.smoothScrollTo(0,0));});
  }catch(Throwable e){showError(e);}finally{if(image!=null)image.delete();if(audio!=null)audio.delete();}});
 }

 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
 if(request==71){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&exportFormat!=null)writeDebugExport(data.getData(),exportLayer,exportFormat);exportFormat=null;return;}
 if(request==4){File captured=pendingCamera;pendingCamera=null;if(captured==null)return;if(result!=RESULT_OK||captured.length()==0){captured.delete();return;}prepareAttachment(Uri.fromFile(captured),2,captured);return;}
 if(result!=RESULT_OK||data==null||data.getData()==null)return;
 if(request==2||request==3||request==5){Uri selected=data.getData();int kind=request;if(request==5){String type=getContentResolver().getType(selected);if(type!=null&&type.startsWith("image/"))kind=2;else if(type!=null&&type.startsWith("audio/"))kind=3;else{status.setText("Choose an image or audio file.");return;}}prepareAttachment(selected,kind,null);return;}
 if(request!=1||busy)return;final int target=importModel;Uri uri=data.getData();setBusy(true,"Importing model. Keep the app open…");worker.execute(() -> {File temp=new File(modelFile(target)+".part");try {
  try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){if(in==null)throw new IOException("Cannot read the model.");byte[] buffer=new byte[1024*1024];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
  if(temp.length()!=MODEL_SIZES[target])throw new IOException("Select the correct "+MODEL_NAMES[target]+" model file.");
  if(!temp.renameTo(modelFile(target)))throw new IOException("Could not save the model.");runOnUiThread(()->{selectedModel=target;clearAttachments();transcript.setText("");welcome.setVisibility(android.view.View.VISIBLE);updateChatMode();initialize();});
 }catch(Throwable e){temp.delete();showError(e);} });}
 private void prepareAttachment(Uri selected,int kind,File source){setBusy(true,"Preparing attachment…");worker.execute(() -> {try{File file=kind==2?MediaFiles.image(this,selected):MediaFiles.audio(this,selected);runOnUiThread(() -> {if(destroyed){file.delete();return;}if(kind==2){if(pendingImage!=null)pendingImage.delete();pendingImage=file;}else{if(pendingAudio!=null)pendingAudio.delete();pendingAudio=file;}updateAttachments();setBusy(false,"Attachment ready · Add a message or tap send");});}catch(Throwable e){showError(e);}finally{if(source!=null)source.delete();}});}
 private void pickFile(String type,int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(type);if(request==5)i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","audio/*"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,request);}
 private void pickPhoto(){if(android.os.Build.VERSION.SDK_INT>=33){Intent i=new Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);i.setType("image/*");startActivityForResult(i,2);}else pickFile("image/*",2);}
 private void takePhoto(){
  if(busy||recording)return;
  Intent camera=new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
  if(camera.resolveActivity(getPackageManager())==null){status.setText("No camera app is available.");return;}
  try{File directory=new File(getCacheDir(),"ghost_camera");if(!directory.exists()&&!directory.mkdirs())throw new IOException("Cannot prepare the camera.");pendingCamera=File.createTempFile("ghost_attachment_",".jpg",directory);
   Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".camera",pendingCamera);camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri);camera.setClipData(android.content.ClipData.newRawUri("Photo",uri));camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(camera,4);
  }catch(Exception e){if(pendingCamera!=null)pendingCamera.delete();pendingCamera=null;showError(e);}
 }
 private void showAttachments(){
  if(busy||recording||selectedModel!=0)return;if(handle==0){showModels();return;}
  ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
  android.app.Dialog sheet=new android.app.Dialog(this);sheet.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(10),dp(18),dp(24));panel.setBackground(surface(0xfffafaf8,30,false));
  android.widget.FrameLayout gripArea=new android.widget.FrameLayout(this);panel.addView(gripArea,new LinearLayout.LayoutParams(-1,dp(16)));android.view.View grip=new android.view.View(this);grip.setBackground(surface(0xffc5c6c0,3,false));gripArea.addView(grip,new android.widget.FrameLayout.LayoutParams(dp(48),dp(4),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
  final float[] down={0};gripArea.setOnTouchListener((v,e) -> {if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){down[0]=e.getRawY();return true;}if(e.getAction()==android.view.MotionEvent.ACTION_MOVE){panel.setTranslationY(Math.max(0,e.getRawY()-down[0]));return true;}if(e.getAction()==android.view.MotionEvent.ACTION_UP||e.getAction()==android.view.MotionEvent.ACTION_CANCEL){if(panel.getTranslationY()>dp(60))sheet.dismiss();else panel.animate().translationY(0).setDuration(150).start();v.performClick();return true;}return false;});
  android.widget.FrameLayout heading=new android.widget.FrameLayout(this);panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(64)));
  TextView title=new TextView(this);title.setText("Add to Chat");title.setTextSize(20);title.setTextColor(0xff20211d);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);heading.addView(title,new android.widget.FrameLayout.LayoutParams(-1,-1));
  ImageButton close=icon(R.drawable.ic_close,"Close attachments",false);heading.addView(close,new android.widget.FrameLayout.LayoutParams(dp(42),dp(42),Gravity.START|Gravity.CENTER_VERTICAL));close.setOnClickListener(v -> sheet.dismiss());
  LinearLayout tiles=new LinearLayout(this);LinearLayout.LayoutParams grid=new LinearLayout.LayoutParams(-1,dp(112));grid.topMargin=dp(16);grid.bottomMargin=dp(16);panel.addView(tiles,grid);
  tiles.addView(attachmentTile(R.drawable.ic_camera,"Camera",() -> {sheet.dismiss();takePhoto();}));
  tiles.addView(attachmentTile(R.drawable.ic_photo,"Photos",() -> {sheet.dismiss();pickPhoto();}));
  tiles.addView(attachmentTile(R.drawable.ic_file,"Files",() -> {sheet.dismiss();pickFile("*/*",5);}));
  android.view.View recent=sidebarRow(R.drawable.ic_photo,"Show recent photos",false,() -> {sheet.dismiss();pickPhoto();});recent.setBackground(surface(0xffffffff,22,false));panel.addView(recent);
  LinearLayout audioGroup=new LinearLayout(this);audioGroup.setOrientation(LinearLayout.VERTICAL);audioGroup.setBackground(surface(0xffffffff,22,false));LinearLayout.LayoutParams audioLayout=new LinearLayout.LayoutParams(-1,-2);audioLayout.topMargin=dp(14);panel.addView(audioGroup,audioLayout);
  audioGroup.addView(attachmentRow(R.drawable.ic_microphone,"Record audio",() -> {sheet.dismiss();toggleRecording();}));
  android.view.View divider=new android.view.View(this);divider.setBackgroundColor(0xffecece7);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.leftMargin=dp(46);line.rightMargin=dp(14);audioGroup.addView(divider,line);
  audioGroup.addView(attachmentRow(R.drawable.ic_file,"Import audio",() -> {sheet.dismiss();pickFile("audio/*",3);}));
  sheet.setContentView(panel);android.view.Window window=sheet.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);ScreenPrivacy.apply(this,window);window.setDimAmount(0.25f);window.setGravity(Gravity.BOTTOM);window.setWindowAnimations(R.style.AttachmentAnimation);WindowManager.LayoutParams params=window.getAttributes();params.y=dp(10);window.setAttributes(params);}
  sheet.show();if(window!=null)window.setLayout(getResources().getDisplayMetrics().widthPixels-dp(16),WindowManager.LayoutParams.WRAP_CONTENT);
 }
 private android.view.View attachmentTile(int resource,String label,Runnable action){LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setBackground(surface(0xfff0f1ed,23,false));LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,-1,1);layout.leftMargin=dp(3);layout.rightMargin=dp(3);tile.setLayoutParams(layout);ImageView image=new ImageView(this);image.setImageResource(resource);tile.addView(image,new LinearLayout.LayoutParams(dp(26),dp(26)));TextView name=new TextView(this);name.setText(label);name.setGravity(Gravity.CENTER);name.setTextAlignment(android.view.View.TEXT_ALIGNMENT_CENTER);name.setTextSize(16);name.setTextColor(0xff252621);name.setTypeface(null,android.graphics.Typeface.BOLD);name.setPadding(0,dp(10),0,0);tile.addView(name,new LinearLayout.LayoutParams(-1,-2));tile.setContentDescription(label);tile.setFocusable(true);tile.setOnClickListener(v -> action.run());return tile;}
 private android.view.View attachmentRow(int icon,String label,Runnable action){android.view.View row=sidebarRow(icon,label,false,action);row.setBackgroundColor(android.graphics.Color.TRANSPARENT);return row;}
 @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("ghost_mode",ghostMode);state.putInt("selected_model",selectedModel);state.putInt("import_model",importModel);if(pendingCamera!=null)state.putString("camera_file",pendingCamera.getName());super.onSaveInstanceState(state);}
 private void updateAttachments(){String label=(pendingImage==null?"":"Image attached")+(pendingImage!=null&&pendingAudio!=null?" · ":"")+(pendingAudio==null?"":"Audio attached");attachmentStatus.setText(label.isEmpty()?"":label+" · Tap to remove");attachmentStatus.setVisibility(label.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);}
 private void clearAttachments(){if(pendingImage!=null)pendingImage.delete();if(pendingAudio!=null)pendingAudio.delete();pendingImage=null;pendingAudio=null;updateAttachments();}
 private void removeAttachment(){if(busy||recording)return;ScreenPrivacy.show(this,new android.app.AlertDialog.Builder(this).setTitle("Remove attachment?").setItems(new String[]{"Remove image","Remove audio","Remove all"},(d,which) -> {if(which==0||which==2){if(pendingImage!=null)pendingImage.delete();pendingImage=null;}if(which==1||which==2){if(pendingAudio!=null)pendingAudio.delete();pendingAudio=null;}updateAttachments();}).create());}
 private void toggleRecording(){
  if(recording){recorder.stop();microphone.setEnabled(false);status.setText("Preparing recording…");return;}
  if(busy||selectedModel!=0)return;if(handle==0){status.setText("Load the model first.");return;}
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},40);return;}
  try{File file=File.createTempFile("ghost_attachment_",".wav",getCacheDir());recording=true;setBusy(true,"Recording · Tap stop when finished (max 30 sec)");microphone.setImageResource(R.drawable.ic_stop);microphone.setContentDescription("Stop recording");microphone.setBackground(surface(0xfff5b6cb,30,false));microphone.setEnabled(true);
   recorder.start(file,(saved,error) -> runOnUiThread(() -> {recording=false;if(destroyed){if(saved!=null)saved.delete();return;}microphone.setImageResource(R.drawable.ic_microphone);microphone.setContentDescription("Record audio");microphone.setBackground(surface(0xfff0f1ec,30,true));if(error!=null){showError(error);return;}if(pendingAudio!=null)pendingAudio.delete();pendingAudio=saved;updateAttachments();setBusy(false,"Recording ready · Add a message or tap send");}));
  }catch(IOException e){recording=false;showError(e);}
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==40){if(results.length>0&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)toggleRecording();else status.setText("Microphone permission is required to record audio.");}}
 @Override protected void onResume(){super.onResume();ScreenPrivacy.refresh(this);}
 @Override protected void onStop(){if(recording)recorder.stop();super.onStop();}
 private void showSidebar(){
  ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
  android.app.Dialog drawer=new android.app.Dialog(this);
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(24),dp(28),dp(20),dp(22));
  android.graphics.drawable.GradientDrawable background=surface(0xfff6f6f2,0,false);background.setCornerRadii(new float[]{0,0,dp(30),dp(30),dp(30),dp(30),0,0});panel.setBackground(background);
  LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
  TextView brand=new TextView(this);brand.setText(ghostMode?"Ghost chat":"Normal chat");brand.setTextSize(28);brand.setTextColor(0xff20211d);brand.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD));heading.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  ImageButton close=icon(R.drawable.ic_menu,"Close menu",false);heading.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));close.setOnClickListener(v -> drawer.dismiss());panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(66)));

  LinearLayout upper=new LinearLayout(this);upper.setOrientation(LinearLayout.VERTICAL);upper.setPadding(0,dp(22),0,0);panel.addView(upper,new LinearLayout.LayoutParams(-1,0,1));
  upper.addView(sidebarRow(R.drawable.ic_chat,"Chats",false,() -> drawer.dismiss()));
  upper.addView(sidebarRow(R.drawable.ic_info,"Attack",false,() -> {drawer.dismiss();startActivity(new Intent(this,AttackActivity.class));}));
  upper.addView(sidebarRow(R.drawable.ic_info,"数据采集",false,() -> {drawer.dismiss();startActivity(new Intent(this,CollectionActivity.class));}));
  upper.addView(sidebarRow(R.drawable.ic_plus,"Models",false,() -> {drawer.dismiss();showModels();}));
  upper.addView(sidebarRow(R.drawable.ic_info,"About",false,() -> {drawer.dismiss();ScreenPrivacy.show(this,new android.app.AlertDialog.Builder(this).setTitle("Ghost chat").setMessage("Powered by Gemma 4 on your device. Only the latest reply is displayed. Session context stays in memory until you start a new session or close the app.").setPositiveButton("OK",null).create());}));

  LinearLayout history=new LinearLayout(this);history.setOrientation(LinearLayout.VERTICAL);panel.addView(history,new LinearLayout.LayoutParams(-1,0,1));
  TextView label=new TextView(this);label.setText("History");label.setTextSize(14);label.setTextColor(0xff7d7e77);label.setPadding(dp(12),0,0,dp(14));history.addView(label);
  history.addView(sidebarRow(R.drawable.ic_ghost_dark,"ghost1",true,() -> drawer.dismiss()));
  TextView current=new TextView(this);current.setText("Current session");current.setTextSize(12);current.setTextColor(0xff8c7a80);current.setPadding(dp(49),dp(5),0,0);history.addView(current);

  LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);panel.addView(footer,new LinearLayout.LayoutParams(-1,dp(60)));
  ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.ic_ghost_dark);avatar.setPadding(dp(9),dp(9),dp(9),dp(9));avatar.setBackground(surface(0xfffce8ef,30,false));footer.addView(avatar,new LinearLayout.LayoutParams(dp(44),dp(44)));
  android.view.View space=new android.view.View(this);footer.addView(space,new LinearLayout.LayoutParams(0,1,1));
  TextView newChat=new TextView(this);newChat.setText("＋  New chat");newChat.setTextColor(0xffffffff);newChat.setTextSize(16);newChat.setTypeface(null,android.graphics.Typeface.BOLD);newChat.setGravity(Gravity.CENTER);newChat.setPadding(dp(19),0,dp(19),0);newChat.setBackground(surface(0xff20211d,28,false));newChat.setContentDescription("Create a new session");footer.addView(newChat,new LinearLayout.LayoutParams(-2,dp(50)));newChat.setOnClickListener(v -> {drawer.dismiss();startNewSession();});
  drawer.setContentView(panel);
  android.view.Window window=drawer.getWindow();
  if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);ScreenPrivacy.apply(this,window);window.setDimAmount(0.12f);window.setGravity(Gravity.START);window.setWindowAnimations(R.style.SidebarAnimation);}
  drawer.show();
  if(window!=null)window.setLayout(Math.min(dp(360),Math.round(getResources().getDisplayMetrics().widthPixels*0.84f)),WindowManager.LayoutParams.MATCH_PARENT);
 }
 private android.view.View sidebarRow(int drawable,String title,boolean selected,Runnable action){
  LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(58)));row.setBackground(surface(selected?0xfff5e5ec:0xfff6f6f2,16,false));row.setContentDescription(title);row.setFocusable(true);
  ImageView image=new ImageView(this);image.setImageResource(drawable);row.addView(image,new LinearLayout.LayoutParams(dp(26),dp(26)));
  TextView text=new TextView(this);text.setText(title);text.setTextSize(19);text.setTextColor(0xff292a25);text.setTypeface(null,selected?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(-2,-2);words.leftMargin=dp(14);row.addView(text,words);row.setOnClickListener(v -> action.run());return row;
 }
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private android.graphics.drawable.GradientDrawable surface(int color,int radius,boolean border){android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));if(border)shape.setStroke(dp(1),0xffe0e1db);return shape;}
 private ImageButton icon(int resource,String label,boolean dark){ImageButton button=new ImageButton(this);button.setImageResource(resource);button.setContentDescription(label);button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);button.setPadding(dp(11),dp(11),dp(11),dp(11));button.setBackground(surface(dark?0xff20211d:0xfff0f1ec,30,!dark));return button;}
 @Override protected void onDestroy(){destroyed=true;recorder.stop();clearAttachments();worker.execute(() -> {if(handle!=0){NativeModel.close(handle);handle=0;}});worker.shutdown();super.onDestroy();}
}
