package br.com.luma.camera;

import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MediaFiles {
 public static String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(new Date()); }
 public static Uri publish(Context c,File file,boolean video,String name) throws IOException {
  ContentValues values=new ContentValues();
  values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
  values.put(MediaStore.MediaColumns.MIME_TYPE,video?"video/mp4":"image/jpeg");
  values.put(MediaStore.MediaColumns.RELATIVE_PATH,(video?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_PICTURES)+"/Langer");
  values.put(MediaStore.MediaColumns.IS_PENDING,1);
  ContentResolver r=c.getContentResolver();
  Uri uri=r.insert(video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
  if(uri==null) throw new IOException("Não foi possível criar o arquivo na galeria.");
  try {
   try(InputStream in=new FileInputStream(file); OutputStream out=r.openOutputStream(uri)) {
    if(out==null) throw new IOException("Galeria indisponível");
    byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) {if(Thread.currentThread().isInterrupted())throw new IOException("Cancelado");out.write(b,0,n);}
   }
   values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING,0); r.update(uri,values,null,null);
   c.getSharedPreferences("luma",0).edit().putString("lastUri",uri.toString()).putString("lastType",video?"video/mp4":"image/jpeg").apply();
   return uri;
  } catch(Exception e) { r.delete(uri,null,null); throw new IOException(e); }
 }
}
