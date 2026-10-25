package com.example.annoymous;

import android.content.Context;
import android.net.Uri;
import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

final class MediaFiles {
 static File image(Context context, Uri uri) throws IOException {
  BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
  try (InputStream in = context.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, options); }
  if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("This image cannot be decoded.");
  options.inSampleSize = 1;
  while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2;
  options.inJustDecodeBounds = false;
  Bitmap bitmap;
  try (InputStream in = context.getContentResolver().openInputStream(uri)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
  if (bitmap == null) throw new IOException("This image cannot be decoded.");
  File file = null;
  try {
   int orientation;
   try (InputStream in = context.getContentResolver().openInputStream(uri)) { orientation = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL); }
   Matrix matrix = new Matrix();
   switch (orientation) {
    case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1,1); break;
    case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
    case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1,-1); break;
    case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1,1); break;
    case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
    case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1,1); break;
    case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
    default: break;
   }
   if (!matrix.isIdentity()) { Bitmap rotated = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true); if(rotated!=bitmap)bitmap.recycle(); bitmap=rotated; }
   file = File.createTempFile("ghost_attachment_", ".jpg", context.getCacheDir());
   try (OutputStream out = new FileOutputStream(file)) { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("Could not prepare the image."); }
   return file;
  } catch (IOException e) { if(file!=null)file.delete(); throw e; }
  finally { bitmap.recycle(); }
 }
 static File audio(Context context, Uri uri) throws IOException {
  File file = File.createTempFile("ghost_attachment_", ".audio", context.getCacheDir());
  try {
   try (InputStream in=context.getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(file)) {
    if(in==null)throw new IOException("Cannot read the audio file.");
    byte[] buffer=new byte[65536]; int n;long total=0;
    while((n=in.read(buffer))!=-1){total+=n;if(total>20*1024*1024)throw new IOException("Audio files must be under 20 MB.");out.write(buffer,0,n);}
   }
   android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
   try {reader.setDataSource(file.getAbsolutePath());String duration=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)>30000)throw new IOException("Choose an audio clip of 30 seconds or less.");}
   finally {reader.release();}
   return file;
  } catch(Exception e){file.delete();if(e instanceof IOException)throw (IOException)e;throw new IOException("Cannot read this audio format.",e);}
 }
}
