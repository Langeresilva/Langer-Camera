package br.com.luma.camera;

import android.graphics.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;

/** Decode only a small, correctly oriented preview from the actual captured file. */
public final class CaptureThumbnail {
 public static Bitmap read(FileInputStream stream)throws IOException {
  int orientation=new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);
  stream.getChannel().position(0);BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
  BitmapFactory.decodeStream(stream,null,options);options.inSampleSize=1;
  while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>240)options.inSampleSize*=2;
  options.inJustDecodeBounds=false;stream.getChannel().position(0);
  Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options);if(bitmap==null)throw new IOException("Miniatura indisponível");
  Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
  if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
  return bitmap;
 }
}
