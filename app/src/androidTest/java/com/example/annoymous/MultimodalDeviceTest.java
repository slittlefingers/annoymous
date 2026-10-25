package com.example.annoymous;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MultimodalDeviceTest {
 private String send(long handle,String text,String image,String audio) {
  return new String(NativeModel.sendMedia(handle,text.getBytes(StandardCharsets.UTF_8),image,audio),StandardCharsets.UTF_8);
 }
 @Test public void imageAudioAndSessionContext() throws Exception {
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  File model=new File(context.getExternalFilesDir(null),"gemma-4-E2B-it_Google_Tensor_G5.litertlm");
  assertTrue("Push the Tensor G5 model before this test",model.isFile());
  File image=new File(context.getCacheDir(),"test-red.png");
  Bitmap bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.RED);
  try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  File audio=new File(context.getCacheDir(),"test-speech.wav");
  try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("test-speech.wav");OutputStream out=new FileOutputStream(audio)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
  File preparedImage=MediaFiles.image(context,Uri.fromFile(image));
  File preparedAudio=MediaFiles.audio(context,Uri.fromFile(audio));
  long handle=0;
  try {
   handle=NativeModel.open(model.getAbsolutePath(),context.getCacheDir().getAbsolutePath(),context.getApplicationInfo().nativeLibraryDir);
   String first=send(handle,"What color is this image? Remember its color. Reply briefly.",preparedImage.getAbsolutePath(),"");
   android.util.Log.i("GhostMultimodalTest","IMAGE: "+first);
   assertTrue("Image response should identify red: "+first,first.toLowerCase().contains("red"));
   String follow=send(handle,"What color was the image I just showed you? Reply briefly.","","");
   android.util.Log.i("GhostMultimodalTest","FOLLOWUP: "+follow);
   assertTrue("The same Session should retain the image context: "+follow,follow.toLowerCase().contains("red"));
   NativeModel.reset(handle);
   String speech=send(handle,"Transcribe this audio exactly.","",preparedAudio.getAbsolutePath());
   android.util.Log.i("GhostMultimodalTest","AUDIO: "+speech);
   assertTrue("Audio response should transcribe the synthetic fixture: "+speech,speech.toLowerCase().contains("forty")||speech.contains("42"));
   NativeModel.reset(handle);
   File invalid=new File(context.getCacheDir(),"invalid-image.bin");
   try(FileOutputStream out=new FileOutputStream(invalid)){out.write(new byte[]{1,2,3,4});}
   try {send(handle,"Describe this image",invalid.getAbsolutePath(),"");fail("Invalid image must return an error");}
   catch(IllegalStateException expected){assertNotNull(expected.getMessage());}
   finally{invalid.delete();}
  } finally {if(handle!=0)NativeModel.close(handle);image.delete();audio.delete();preparedImage.delete();preparedAudio.delete();}
 }
}
