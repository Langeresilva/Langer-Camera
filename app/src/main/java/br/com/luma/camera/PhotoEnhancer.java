package br.com.luma.camera;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

/** Adaptive photographic tone correction; deliberately not advertised as a neural model. */
public final class PhotoEnhancer {
 public static void process(File input,File output) throws IOException {
  BitmapFactory.Options opts=new BitmapFactory.Options();
  opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getAbsolutePath(),opts);
  opts.inSampleSize=1;while((long)(opts.outWidth/opts.inSampleSize)*(opts.outHeight/opts.inSampleSize)>16_000_000L)opts.inSampleSize*=2;
  opts.inJustDecodeBounds=false;
  opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
  Bitmap bitmap=BitmapFactory.decodeFile(input.getAbsolutePath(),opts);
  if(bitmap==null) throw new IOException("Foto inválida");
  int w=bitmap.getWidth(),h=bitmap.getHeight();
  int[] row=new int[w]; long[] hist=new long[256];
  int stride=Math.max(1,h/400);
  for(int y=0;y<h;y+=stride) {
   bitmap.getPixels(row,0,w,0,y,w,1);
   for(int x=0;x<w;x+=Math.max(1,w/400)) {
    int p=row[x],r=Color.red(p),g=Color.green(p),b=Color.blue(p);
    hist[(r*54+g*183+b*19)>>8]++;
   }
  }
  long total=0; for(long n:hist) total+=n;
  int median=128; long acc=0; for(int i=0;i<256;i++) {acc+=hist[i];if(acc>=total/2){median=i;break;}}
  double strength=PhotoTone.strength(median);
  Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  try {
   for(int y=0;y<h;y++) {
    if(Thread.currentThread().isInterrupted()) throw new IOException("Cancelado");
    bitmap.getPixels(row,0,w,0,y,w,1);
    for(int x=0;x<w;x++) {
     row[x]=PhotoTone.apply(row[x],strength);
    }
    result.setPixels(row,0,w,0,y,w,1);
   }
   try(OutputStream stream=new FileOutputStream(output)) {if(!result.compress(Bitmap.CompressFormat.JPEG,95,stream)) throw new IOException("Falha ao salvar JPEG");}
   ExifInterface source=new ExifInterface(input);
   ExifInterface target=new ExifInterface(output);
   for(String tag:new String[]{ExifInterface.TAG_ORIENTATION,ExifInterface.TAG_DATETIME,ExifInterface.TAG_DATETIME_ORIGINAL,ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,ExifInterface.TAG_FOCAL_LENGTH}) {
    String value=source.getAttribute(tag);if(value!=null) target.setAttribute(tag,value);
   }
   target.setAttribute(ExifInterface.TAG_SOFTWARE,"Langer Camera 0.1.5 - conservative luminance correction");target.saveAttributes();
  } finally {bitmap.recycle();result.recycle();}
 }
}
