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
  int[] row=new int[w]; long[] hist=new long[256]; double sr=0,sg=0,sb=0,count=0;
  int stride=Math.max(1,h/400);
  for(int y=0;y<h;y+=stride) {
   bitmap.getPixels(row,0,w,0,y,w,1);
   for(int x=0;x<w;x+=Math.max(1,w/400)) {
    int p=row[x],r=Color.red(p),g=Color.green(p),b=Color.blue(p);
    hist[(r*54+g*183+b*19)>>8]++;
    if(r>20&&r<235&&g>20&&g<235&&b>20&&b<235) {sr+=r;sg+=g;sb+=b;count++;}
   }
  }
  long total=0; for(long n:hist) total+=n;
  int median=128; long acc=0; for(int i=0;i<256;i++) {acc+=hist[i];if(acc>=total/2){median=i;break;}}
  double gamma=Math.max(0.65,Math.min(1.08,Math.log(0.52)/Math.log(Math.max(0.08,median/255.0))));
  double avg=(sr+sg+sb)/3;
  double wr=count>0?clamp(avg/Math.max(1,sr),0.92,1.08):1;
  double wg=count>0?clamp(avg/Math.max(1,sg),0.92,1.08):1;
  double wb=count>0?clamp(avg/Math.max(1,sb),0.92,1.08):1;
  int[] lut=new int[256]; for(int i=0;i<256;i++) lut[i]=(int)Math.round(255*Math.pow(i/255.0,gamma));
  Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
  try {
   for(int y=0;y<h;y++) {
    if(Thread.currentThread().isInterrupted()) throw new IOException("Cancelado");
    bitmap.getPixels(row,0,w,0,y,w,1);
    for(int x=0;x<w;x++) {
     int p=row[x]; double r=lut[Color.red(p)]*wr,g=lut[Color.green(p)]*wg,b=lut[Color.blue(p)]*wb;
     double lum=.2126*r+.7152*g+.0722*b;
     // A gentle S curve adds midtone separation without clipping the end points.
     double normalized=clamp(lum/255,0,1);
     double tone=255*(normalized+0.12*normalized*(1-normalized)*(2*normalized-1));
     row[x]=Color.rgb((int)clamp(tone+(r-lum)*1.09,0,255),(int)clamp(tone+(g-lum)*1.09,0,255),(int)clamp(tone+(b-lum)*1.09,0,255));
    }
    result.setPixels(row,0,w,0,y,w,1);
   }
   try(OutputStream stream=new FileOutputStream(output)) {if(!result.compress(Bitmap.CompressFormat.JPEG,95,stream)) throw new IOException("Falha ao salvar JPEG");}
   ExifInterface source=new ExifInterface(input);
   ExifInterface target=new ExifInterface(output);
   for(String tag:new String[]{ExifInterface.TAG_ORIENTATION,ExifInterface.TAG_DATETIME,ExifInterface.TAG_DATETIME_ORIGINAL,ExifInterface.TAG_MAKE,ExifInterface.TAG_MODEL,ExifInterface.TAG_EXPOSURE_TIME,ExifInterface.TAG_F_NUMBER,ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,ExifInterface.TAG_FOCAL_LENGTH}) {
    String value=source.getAttribute(tag);if(value!=null) target.setAttribute(tag,value);
   }
   target.setAttribute(ExifInterface.TAG_SOFTWARE,"Langer Camera 0.1.2 - offline tone correction");target.saveAttributes();
  } finally {bitmap.recycle();result.recycle();}
 }
 private static double clamp(double v,double lo,double hi) {return Math.max(lo,Math.min(hi,v));}
}
