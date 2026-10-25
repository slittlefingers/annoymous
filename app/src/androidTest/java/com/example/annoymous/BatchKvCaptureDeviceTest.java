package com.example.annoymous;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;

/** Explicitly invoked research job. Ordinary chat does not collect artifacts. */
@RunWith(AndroidJUnit4.class)
public class BatchKvCaptureDeviceTest {
 private static final String G4="gemma-4-E2B-it_Google_Tensor_G5.litertlm";
 private static final String G3="Gemma3-1B-IT_q8_ekv1280_Google_Tensor_G5.litertlm";
 private static final String SAFE="[A-Za-z0-9_-]{1,64}";
 private static void atomic(File file,String text)throws Exception {
  File temp=new File(file.getPath()+".tmp");
  Files.write(temp.toPath(),text.getBytes(StandardCharsets.UTF_8));
  Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
 }
 private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return s.toString();}
 private static String sha(File f)throws Exception {
  MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] block=new byte[1024*1024];
  try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(block))!=-1)md.update(block,0,n);}return hex(md.digest());
 }
 private static void status(File dir,String state,int done,int total,String id,String error)throws Exception {
  JSONObject j=new JSONObject().put("state",state).put("completed",done).put("total",total)
    .put("sample_id",id).put("updated_ms",System.currentTimeMillis());
  if(error!=null)j.put("error",error);atomic(new File(dir,"status.json"),j.toString());
 }
 private static void checkCancel(File dir)throws IOException {if(new File(dir,"cancel").exists())throw new IOException("Cancelled by host");}
 private static JSONObject capture(long h,File destination)throws Exception {
  // Native export writes directly to a pipe. Never stage the full raw KV on disk
  // or convert it into a Java byte[]/hex/CSV. gzip is a lossless transport wrapper.
  ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
  ExecutorService compressor=Executors.newSingleThreadExecutor();
  Future<Long> written=compressor.submit(()->{
   long bytes=0;
   try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
       OutputStream out=new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(destination)),1024*1024)){
    byte[] block=new byte[1024*1024];int n;while((n=in.read(block))!=-1){out.write(block,0,n);bytes+=n;}
   }return bytes;
  });
  long started=SystemClock.elapsedRealtimeNanos();
  try {
   byte[] result;
   try {
    JSONObject request=new JSONObject().put("layer",-1).put("format","kv_raw").put("fd",pipe[1].getFd());
    result=NativeModel.inspect(h,request.toString());
   } finally {pipe[1].close();}
   long rawBytes=written.get();
   JSONObject resultJson=new JSONObject(new String(result,StandardCharsets.UTF_8));
   if(rawBytes!=resultJson.getLong("bytes"))throw new IOException("Native export byte count mismatch");
   return resultJson.put("uncompressed_bytes",rawBytes).put("compressed_bytes",destination.length())
    .put("gzip_sha256",sha(destination)).put("capture_seconds",(SystemClock.elapsedRealtimeNanos()-started)/1e9);
  } finally {
   try{pipe[0].close();}catch(IOException ignored){}
   compressor.shutdownNow();
  }
 }

 @Test public void captureBatch()throws Exception {
  String jobId=InstrumentationRegistry.getArguments().getString("batch_job");
  Assume.assumeNotNull(jobId);
  if(!jobId.matches(SAFE))throw new IllegalArgumentException("Invalid job ID");
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  File dir=new File(context.getFilesDir(),"kv_jobs/"+jobId);
  JSONObject job=new JSONObject(new String(Files.readAllBytes(new File(dir,"input.json").toPath()),StandardCharsets.UTF_8));
  JSONArray samples=job.getJSONArray("samples");int total=samples.length();
  if(total==0)throw new IllegalArgumentException("No samples");
  HashSet<String> seen=new HashSet<>();
  for(int i=0;i<total;i++){
   JSONObject s=samples.getJSONObject(i);String id=s.getString("id");
   if(!id.matches(SAFE)||!seen.add(id)||s.getString("user_prompt").isEmpty()||s.getString("user_prompt").indexOf('\0')>=0)
    throw new IllegalArgumentException("Invalid sample at index "+i);
  }
  String model=job.optString("model","gemma4-e2b");
  if(!model.equals("gemma4-e2b")&&!model.equals("gemma3-1b"))throw new IllegalArgumentException("Unknown model");
  for(int i=0;i<total;i++){
   JSONObject s=samples.getJSONObject(i);
   if(!s.has("media"))continue;
   JSONObject media=s.getJSONObject("media");String type=media.getString("type");
   if(!model.equals("gemma4-e2b")||(!type.equals("image")&&!type.equals("audio")))
    throw new IllegalArgumentException("Unsupported media/model");
   String relative=media.getString("path");
   String expected="media/"+s.getString("id")+(type.equals("image")?".jpg":".wav");
   File file=new File(dir,relative);
   if(!relative.equals(expected)||!file.isFile()||file.length()==0||file.length()>20*1024*1024
       ||!sha(file).equals(media.getString("sha256")))
    throw new IOException("Invalid media file or checksum for "+s.getString("id"));
   JSONArray order=s.getJSONArray("content_order");
   if(order.length()!=2||!order.getString(0).equals("media")||!order.getString(1).equals("text"))
    throw new IllegalArgumentException("Media must precede the original text");
  }
  if(!job.getString("capture_stage").equals("after_response"))throw new IllegalArgumentException("This runner captures after response only");
  File modelFile=new File(context.getExternalFilesDir(null),model.equals("gemma4-e2b")?G4:G3);
  long handle=0;int done=0;String active="";
  try {
   status(dir,"loading",done,total,active,null);
   if(!modelFile.isFile())throw new FileNotFoundException("Model is not installed: "+modelFile.getName());
   JSONObject info=new JSONObject().put("schema_version",1).put("job_id",jobId).put("backend","npu")
    .put("model",model).put("model_file",modelFile.getName()).put("model_bytes",modelFile.length())
    .put("model_sha256",sha(modelFile)).put("runtime_sha256",sha(new File(context.getApplicationInfo().nativeLibraryDir,"liblitert-lm.so")))
    .put("capture_stage","after_response").put("session_per_sample",true).put("max_output_tokens",512)
    .put("token_id_scrubbing",job.optBoolean("token_id_scrubbing",true))
    .put("device",android.os.Build.MODEL).put("android_fingerprint",android.os.Build.FINGERPRINT)
    .put("input_sha256",sha(new File(dir,"input.json")))
    .put("source","Live process-owned NPU TensorBuffer CPU mappings; all exposed KV allocations, not arbitrary physical DRAM")
    .put("window_note","End-of-response snapshot: sliding-window eviction may remove early prompt positions; pending token may not yet have KV.");
   long loadStarted=SystemClock.elapsedRealtimeNanos();
   handle=NativeModel.openWithTokenIdScrubbing(modelFile.getAbsolutePath(),context.getCacheDir().getAbsolutePath(),
    context.getApplicationInfo().nativeLibraryDir,job.optBoolean("token_id_scrubbing",true));
   info.put("model_load_seconds",(SystemClock.elapsedRealtimeNanos()-loadStarted)/1e9);
   atomic(new File(dir,"job.json"),info.toString(2));
   for(int i=0;i<total;i++){
    checkCancel(dir);JSONObject sample=samples.getJSONObject(i);active=sample.getString("id");
    File output=new File(dir,active);if(!output.mkdir())throw new IOException("Existing output: "+active);
    if(i>0)NativeModel.reset(handle);
    status(dir,"generating",done,total,active,null);
    long tick=SystemClock.elapsedRealtimeNanos();
    String image="",audio="";
    if(sample.has("media")){
     JSONObject media=sample.getJSONObject("media");
     String path=new File(dir,media.getString("path")).getAbsolutePath();
     if(media.getString("type").equals("image"))image=path;else audio=path;
    }
    byte[] reply=NativeModel.sendMedia(handle,sample.getString("user_prompt").getBytes(StandardCharsets.UTF_8),image,audio);
    double inference=(SystemClock.elapsedRealtimeNanos()-tick)/1e9;
    checkCancel(dir);status(dir,"capturing",done,total,active,null);
    File part=new File(output,"all_layers.gkv.gz.part"),finalFile=new File(output,"all_layers.gkv.gz");
    JSONObject capture=capture(handle,part);
    Files.move(part.toPath(),finalFile.toPath(),StandardCopyOption.ATOMIC_MOVE);
    Files.write(new File(output,"response.txt").toPath(),reply);
    JSONObject meta=new JSONObject().put("id",active).put("capture_stage","after_response")
     .put("inference_seconds",inference).put("response_utf8_bytes",reply.length)
     .put("response_sha256",sha(new File(output,"response.txt")))
     .put("prompt_sha256",hex(MessageDigest.getInstance("SHA-256").digest(sample.getString("user_prompt").getBytes(StandardCharsets.UTF_8))))
     .put("kv",capture);
    if(sample.has("media"))meta.put("media",sample.getJSONObject("media"))
      .put("content_order",sample.getJSONArray("content_order"));
    atomic(new File(output,"sample.json"),meta.toString(2));
    atomic(new File(dir,"ready.json"),new JSONObject().put("id",active).put("index",i).toString());
    status(dir,"awaiting_host",done,total,active,null);
    // Backpressure: only one KV artifact on the phone. Host acknowledges only
    // after download, gzip/hash/layout checks and durable local completion.
    File ack=new File(dir,"ack-"+active);long deadline=SystemClock.elapsedRealtime()+600_000;
    while(!ack.isFile()){
     checkCancel(dir);if(SystemClock.elapsedRealtime()>deadline)throw new IOException("Host acknowledgement timed out");
     Thread.sleep(200);
    }
    for(File f:output.listFiles())if(!f.delete())throw new IOException("Could not clean captured artifact");
    if(!output.delete()||!ack.delete()||!new File(dir,"ready.json").delete())throw new IOException("Could not finish acknowledged sample");
    done++;status(dir,"running",done,total,active,null);
   }
   status(dir,"done",done,total,active,null);
  }catch(Exception e){status(dir,"failed",done,total,active,e.getClass().getSimpleName()+": "+e.getMessage());throw e;}
  finally{if(handle!=0)NativeModel.close(handle);}
 }
}
