package br.com.luma.camera;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.net.Uri;
import android.view.Gravity;
import android.widget.*;
import androidx.exifinterface.media.ExifInterface;
import java.io.InputStream;
import java.util.concurrent.*;

/** Shows the same capture before and after treatment, without opening another app. */
public class ComparisonActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private Bitmap original,treated;
 private ImageView image;
 private TextView label;
 private boolean showingOriginal=true,closed;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,48,24,40);root.setBackgroundColor(0xff0a1119);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(24,bars.top+20,24,bars.bottom+20);return insets;});
  label=new TextView(this);label.setTextColor(0xffa5f2d1);label.setTextSize(22);label.setGravity(Gravity.CENTER);label.setText("Carregando comparação…");root.addView(label);
  image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
  TextView note=new TextView(this);note.setText("Mesma captura, sem e com tratamento.\nAs duas imagens também ficam salvas na pasta Langer da galeria.");note.setTextColor(0xffa4afba);note.setGravity(Gravity.CENTER);root.addView(note);
  Button toggle=new Button(this);toggle.setText("Alternar Original / Tratada");root.addView(toggle);toggle.setOnClickListener(v->{showingOriginal=!showingOriginal;showImage();});
  Button share=new Button(this);share.setText("Enviar original ao ChatGPT");root.addView(share);
  Button back=new Button(this);back.setText("Voltar à câmera");root.addView(back);back.setOnClickListener(v->finish());
  String a=getSharedPreferences("luma",0).getString("photoOriginal",null),b=getSharedPreferences("luma",0).getString("photoTreated",null);
  share.setEnabled(a!=null);share.setOnClickListener(v->ChatGptShare.show(this,Uri.parse(a)));
  if(a==null||b==null){label.setText("Tire uma nova foto para comparar");return;}
  worker.execute(()->{Bitmap first=null,second=null;try{
   first=load(Uri.parse(a));second=load(Uri.parse(b));final Bitmap loadedA=first,loadedB=second;
   runOnUiThread(()->{if(closed){loadedA.recycle();loadedB.recycle();return;}original=loadedA;treated=loadedB;showImage();});
  }catch(Exception|OutOfMemoryError e){if(first!=null)first.recycle();if(second!=null)second.recycle();runOnUiThread(()->{if(!closed)label.setText("Não foi possível abrir. Tire uma nova foto ou confira a galeria.");});}});
 }
 private Bitmap load(Uri uri)throws Exception{
  BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
  try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
  opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>1800)opts.inSampleSize*=2;opts.inJustDecodeBounds=false;
  Bitmap bitmap;try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}if(bitmap==null)throw new Exception("Imagem indisponível");
  int orientation;try(InputStream in=getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}
  Matrix matrix=new Matrix();
  switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
  if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}return bitmap;
 }
 private void showImage(){if(original==null||treated==null)return;label.setText(showingOriginal?"ORIGINAL":"TRATADA · luz e cores");image.setImageBitmap(showingOriginal?original:treated);}
 @Override protected void onDestroy(){closed=true;worker.shutdownNow();image.setImageDrawable(null);if(original!=null)original.recycle();if(treated!=null)treated.recycle();super.onDestroy();}
}
