package com.example.annoymous;
import android.app.Instrumentation;
import android.content.Intent;
import android.widget.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class NoModelDeviceTest {
 private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private void set(Object o,String name,Object v)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,v);}
 private void invoke(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
 private void ui(Instrumentation i,Runnable task){i.runOnMainSync(task);}
 private void idle(Instrumentation i,MainActivity a)throws Exception{
  for(int n=0;n<300;n++){boolean[] ready={false};ui(i,()->{try{ready[0]=!(boolean)field(a,"busy");}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;Thread.sleep(100);}fail("Model operation timed out");
 }
 @Test public void unloadRetainsAddressesAndCanReload()throws Exception{
  Instrumentation i=InstrumentationRegistry.getInstrumentation();Intent intent=new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  MainActivity a=(MainActivity)i.startActivitySync(intent);
  try{
   idle(i,a);assertNotEquals(0L,(long)field(a,"handle"));
   NativeModel.sendMedia((long)field(a,"handle"),"Reply with hello.".getBytes(StandardCharsets.UTF_8),"","");
   ui(i,()->{try{set(a,"debugEnabled",true);invoke(a,"selectNoModel");}catch(Exception e){throw new RuntimeException(e);}});idle(i,a);
   assertEquals(0L,(long)field(a,"handle"));assertEquals(2,(int)field(a,"selectedModel"));assertTrue(((List<?>)field(field(a,"retiredMemory"),"old")).size()>0);
   ui(i,()->{try{assertEquals("No model ▾",((TextView)field(a,"modelBadge")).getText().toString());assertFalse(((ImageButton)field(a,"send")).isEnabled());assertFalse(((EditText)field(a,"input")).isEnabled());assertTrue(((ImageButton)field(a,"fresh")).isEnabled());invoke(a,"selectNoModel");}catch(Exception e){throw new RuntimeException(e);}});idle(i,a);assertEquals(0L,(long)field(a,"handle"));
   ui(i,()->{try{set(a,"selectedModel",1);invoke(a,"updateChatMode");invoke(a,"initialize");}catch(Exception e){throw new RuntimeException(e);}});idle(i,a);assertNotEquals(0L,(long)field(a,"handle"));
  }finally{ui(i,a::finish);i.waitForIdleSync();}
 }
}
