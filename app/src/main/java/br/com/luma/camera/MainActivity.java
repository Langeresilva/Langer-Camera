package br.com.luma.camera;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.camera2.*;
import android.hardware.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.Size;
import androidx.activity.ComponentActivity;
import androidx.camera.core.*;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.video.*;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass={androidx.camera.camera2.interop.ExperimentalCamera2Interop.class,androidx.camera.video.ExperimentalPersistentRecording.class})
public class MainActivity extends ComponentActivity {
 private static final int INK=0xff091311,MINT=0xff9ee5cf,TEXT=0xffedf7f1,MUTED=0xff94aaa1;
 private PreviewView preview;
 private TextView status,badge,timer,modeLabel,thumbnailLabel,captureNotice;
 private Button flip,photoMode,videoMode,gallery,info,flashButton;
 private ShutterButton shutter;
 private ImageView thumbnail;
 private View captureFlash,focusRing;
 private FrameLayout viewfinder;
 private Bitmap thumbnailBitmap;
 private String thumbnailUri;
 private int thumbnailGeneration;
 private boolean destroyed;
 private Switch stabilize;
 private ProcessCameraProvider provider;
 private ImageCapture imageCapture;
 private VideoCapture<Recorder> videoCapture;
 private Recording recording;
 private boolean videoModeSelected=false,front=false,capturing=false,resumed=false,binding=false;
 private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler handler=new Handler(Looper.getMainLooper());
 private long recordStart;
 private File recordingFile;
 private String cameraNote="";
 private androidx.camera.core.Camera activeCamera;
 private OrientationEventListener orientationListener;
 private boolean loadingCamera;
 private int captureRotation=Surface.ROTATION_0;
 private Preview cameraPreview;
 private int flashMode=ImageCapture.FLASH_MODE_OFF;
 private boolean screenFlashEnabled,screenFlashActive,torchWanted,switchingCamera,recordingStarted,recordingStarting,recordingStopping,volumeSwitchHeld;
 private float previousBrightness=-1;
 private View screenLight;
 private long lastCameraSwitch=-2000,recordedNanos;
 private int switchSerial,screenFlashSerial;
 private Runnable switchDeadline,screenFlashDeadline;
 private androidx.camera.core.Camera switchObservedCamera;
 private final ImageCapture.ScreenFlash screenFlash=new ImageCapture.ScreenFlash(){
  public void apply(long expires,ImageCapture.ScreenFlashListener listener){
   if(destroyed||!resumed){listener.onCompleted();return;}
   if(!screenFlashActive)previousBrightness=getWindow().getAttributes().screenBrightness;
   screenFlashActive=true;int serial=++screenFlashSerial;screenLight.setVisibility(View.VISIBLE);
   WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.screenBrightness=1f;getWindow().setAttributes(attributes);
   screenLight.postOnAnimation(()->{if(screenFlashActive&&serial==screenFlashSerial)listener.onCompleted();});
   if(screenFlashDeadline!=null)handler.removeCallbacks(screenFlashDeadline);
   screenFlashDeadline=()->clearScreenLight();handler.postDelayed(screenFlashDeadline,Math.max(1000,Math.min(10000,expires-System.currentTimeMillis()+1000)));
  }
  public void clear(){clearScreenLight();}
 };
 private final Runnable refresh=new Runnable(){public void run(){
  SharedPreferences p=getSharedPreferences("luma",0);
  boolean busy=p.getBoolean("busy",false);
  if(recording!=null){long s=recordedNanos/1_000_000_000L;timer.setText(String.format(Locale.US,"● %02d:%02d",s/60,s%60));}
  else if(!capturing&&!binding) status.setText(busy?p.getString("status","Processando…"):p.getString("status","Pronto para capturar"));
  shutter.setEnabled(!binding&&!capturing&&!busy&&!recordingStarting&&!recordingStopping);
  flip.setEnabled(!binding&&!capturing&&!busy&&!recordingStarting&&!recordingStopping&&!switchingCamera);
  photoMode.setEnabled(recording==null&&!capturing&&!busy&&!binding);
  videoMode.setEnabled(recording==null&&!capturing&&!busy&&!binding);
  stabilize.setEnabled(recording==null&&!busy&&!capturing&&!binding);
  gallery.setEnabled(recording==null&&!capturing&&!busy);
  flashButton.setEnabled(!binding&&!capturing&&!busy&&!switchingCamera&&!recordingStarting&&!recordingStopping&&activeCamera!=null&&(front&&!videoModeSelected||activeCamera.getCameraInfo().hasFlashUnit()));
  if(!busy&&!capturing&&recording==null)refreshThumbnail(p.getString("lastUri",null));
  shutter.invalidate();
  if(resumed)handler.postDelayed(this,500);
 }};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  boolean serviceRunning=false;
  for(ActivityManager.RunningServiceInfo service:((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getRunningServices(100))
   if(service.service.getClassName().equals(ProcessingService.class.getName()))serviceRunning=true;
  if(!serviceRunning)getSharedPreferences("luma",0).edit().putBoolean("busy",false).apply();
  getWindow().setStatusBarColor(INK);getWindow().setNavigationBarColor(INK);
  if(state!=null){front=state.getBoolean("front");videoModeSelected=state.getBoolean("video");}
  flashMode=getSharedPreferences("luma",0).getInt("flashMode",ImageCapture.FLASH_MODE_OFF);if(flashMode!=ImageCapture.FLASH_MODE_AUTO&&flashMode!=ImageCapture.FLASH_MODE_ON)flashMode=ImageCapture.FLASH_MODE_OFF;
  screenFlashEnabled=getSharedPreferences("luma",0).getBoolean("screenFlash",false);
  buildUi();
  orientationListener=new OrientationEventListener(this){public void onOrientationChanged(int angle){
   if(angle==ORIENTATION_UNKNOWN)return;
   captureRotation=angle>=45&&angle<135?Surface.ROTATION_270:angle>=135&&angle<225?Surface.ROTATION_180:angle>=225&&angle<315?Surface.ROTATION_90:Surface.ROTATION_0;
   if(imageCapture!=null)imageCapture.setTargetRotation(captureRotation);
   if(videoCapture!=null&&recording==null)videoCapture.setTargetRotation(captureRotation);
  }};
  if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS},41);
  else loadCamera();
 }
 @Override public void onSaveInstanceState(Bundle b){b.putBoolean("front",front);b.putBoolean("video",videoModeSelected);super.onSaveInstanceState(b);}
 private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
 private TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
 private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private android.graphics.drawable.Drawable surface(int color,int radius,int stroke){GradientDrawable d=shape(color,radius);if(stroke!=0)d.setStroke(dp(1),stroke);return new RippleDrawable(ColorStateList.valueOf(0x269ee5cf),d,null);}
 private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff597068,TEXT}));b.setBackground(surface(0xff15251f,16,0xff294036));b.setMinHeight(dp(44));b.setMinWidth(0);b.setPadding(dp(12),0,dp(12),0);return b;}
 private void glyph(Button b,String name,int color){CameraGlyph icon=new CameraGlyph(name,color);icon.setBounds(0,0,dp(22),dp(22));b.setCompoundDrawables(icon,null,null,null);b.setCompoundDrawablePadding(dp(10));}
 private void buildUi(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(INK);
  root.setPadding(dp(18),dp(6),dp(18),dp(8));FrameLayout stage=new FrameLayout(this);stage.addView(root,new FrameLayout.LayoutParams(-1,-1));screenLight=new View(this);screenLight.setBackgroundColor(Color.WHITE);screenLight.setVisibility(View.GONE);stage.addView(screenLight,new FrameLayout.LayoutParams(-1,-1));setContentView(stage);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+bars.left,dp(6)+bars.top,dp(18)+bars.right,dp(8)+bars.bottom);return insets;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(54)));
  ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_launcher_foreground);header.addView(mark,new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
  TextView title=text("LANGER",21,TEXT);title.setLetterSpacing(.16f);brand.addView(title);
  TextView sub=text("Encontre sua melhor luz",11,MUTED);brand.addView(sub);
  flashButton=button("OFF");flashButton.setTextSize(8);flashButton.setPadding(dp(7),dp(5),dp(7),dp(3));CameraGlyph flashGlyph=new CameraGlyph("flash",MINT);flashGlyph.setBounds(0,0,dp(20),dp(20));flashButton.setCompoundDrawables(null,flashGlyph,null,null);flashButton.setCompoundDrawablePadding(dp(1));LinearLayout.LayoutParams lightPosition=new LinearLayout.LayoutParams(dp(48),dp(48));lightPosition.setMarginEnd(dp(6));header.addView(flashButton,lightPosition);flashButton.setOnClickListener(v->showFlashOptions());
  info=button("");info.setPadding(dp(13),dp(13),dp(13),dp(13));info.setContentDescription("Opções: atualizações, recursos e recuperação");CameraGlyph menu=new CameraGlyph("menu",MINT);menu.setBounds(0,0,dp(22),dp(22));info.setCompoundDrawables(menu,null,null,null);header.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));info.setOnClickListener(v->showOptions());
  viewfinder=new FrameLayout(this);viewfinder.setBackground(shape(0xff030907,26));viewfinder.setClipToOutline(true);LinearLayout.LayoutParams vf=new LinearLayout.LayoutParams(-1,0,1);vf.topMargin=dp(8);vf.bottomMargin=dp(10);root.addView(viewfinder,vf);
  preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);viewfinder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  badge=text("Câmera traseira",11,TEXT);badge.setPadding(dp(12),dp(8),dp(12),dp(8));badge.setBackground(shape(0xb30c2018,18));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);bp.setMargins(dp(12),dp(12),0,0);viewfinder.addView(badge,bp);
  timer=text("",14,0xffffa09b);timer.setPadding(dp(10),dp(6),dp(10),dp(6));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);tp.setMargins(0,dp(12),dp(10),0);viewfinder.addView(timer,tp);
  focusRing=new View(this);GradientDrawable focusShape=shape(Color.TRANSPARENT,14);focusShape.setStroke(dp(1.5f),MINT);focusRing.setBackground(focusShape);focusRing.setVisibility(View.GONE);viewfinder.addView(focusRing,new FrameLayout.LayoutParams(dp(58),dp(58)));
  captureNotice=text("Foto capturada",13,INK);captureNotice.setGravity(Gravity.CENTER);captureNotice.setPadding(dp(18),dp(9),dp(18),dp(9));captureNotice.setBackground(shape(MINT,20));captureNotice.setVisibility(View.GONE);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);viewfinder.addView(captureNotice,np);
  GestureDetector gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
   @Override public boolean onDown(MotionEvent event){return true;}
   @Override public boolean onDoubleTap(MotionEvent event){switchCamera();return true;}
   @Override public boolean onSingleTapConfirmed(MotionEvent event){
    if(activeCamera!=null&&!binding&&!switchingCamera&&!capturing){try{
     MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());activeCamera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build());showFocus(event.getX(),event.getY());
    }catch(Exception ignored){}}return true;
   }
  });
  preview.setOnTouchListener((v,event)->{gestures.onTouchEvent(event);if(event.getAction()==MotionEvent.ACTION_UP)v.performClick();return true;});
  LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);FrameLayout.LayoutParams modesPosition=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);modesPosition.bottomMargin=dp(10);viewfinder.addView(modes,modesPosition);
  photoMode=button("Foto");videoMode=button("Vídeo");glyph(photoMode,"camera",INK);glyph(videoMode,"video",MUTED);LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(dp(124),dp(44));modeParams.setMargins(dp(4),0,dp(4),0);modes.addView(photoMode,modeParams);modes.addView(videoMode,new LinearLayout.LayoutParams(modeParams));
  photoMode.setOnClickListener(v->setMode(false));videoMode.setOnClickListener(v->setMode(true));
  modeLabel=text("",10,TEXT);modeLabel.setGravity(Gravity.CENTER);modeLabel.setPadding(dp(10),dp(5),dp(10),dp(5));modeLabel.setBackground(shape(0x990c2018,12));FrameLayout.LayoutParams ml=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);ml.topMargin=dp(54);viewfinder.addView(modeLabel,ml);
  stabilize=new Switch(this);stabilize.setText("Estabilizar após gravar");stabilize.setTextSize(13);stabilize.setTextColor(TEXT);stabilize.setChecked(getSharedPreferences("luma",0).getBoolean("stabilize",true));root.addView(stabilize,new LinearLayout.LayoutParams(-1,dp(40)));
  stabilize.setOnCheckedChangeListener((b,on)->getSharedPreferences("luma",0).edit().putBoolean("stabilize",on).apply());
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(100));cp.topMargin=dp(4);root.addView(controls,cp);
  LinearLayout album=new LinearLayout(this);album.setOrientation(LinearLayout.VERTICAL);album.setGravity(Gravity.CENTER);controls.addView(album,new LinearLayout.LayoutParams(0,-1,1));
  FrameLayout thumbnailFrame=new FrameLayout(this);thumbnailFrame.setBackground(shape(0xff1b3027,16));thumbnailFrame.setClipToOutline(true);album.addView(thumbnailFrame,new LinearLayout.LayoutParams(dp(60),dp(60)));
  thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);thumbnail.setImageDrawable(new CameraGlyph("gallery",MINT));thumbnail.setPadding(dp(16),dp(16),dp(16),dp(16));thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);thumbnailFrame.addView(thumbnail,new FrameLayout.LayoutParams(-1,-1));
  gallery=button("");gallery.setBackground(surface(Color.TRANSPARENT,16,0xff587766));gallery.setContentDescription("Abrir última captura na galeria");thumbnailFrame.addView(gallery,new FrameLayout.LayoutParams(-1,-1));gallery.setOnClickListener(v->openGallery());
  thumbnailLabel=text("Galeria",10,MUTED);thumbnailLabel.setGravity(Gravity.CENTER);thumbnailLabel.setPadding(0,dp(5),0,0);album.addView(thumbnailLabel);
  shutter=new ShutterButton(this);controls.addView(shutter,new LinearLayout.LayoutParams(dp(96),dp(96)));shutter.setOnClickListener(v->{if(videoModeSelected)toggleVideo();else capturePhoto();});
  LinearLayout cameraFlip=new LinearLayout(this);cameraFlip.setOrientation(LinearLayout.VERTICAL);cameraFlip.setGravity(Gravity.CENTER);controls.addView(cameraFlip,new LinearLayout.LayoutParams(0,-1,1));
  flip=button("");flip.setPadding(dp(18),dp(18),dp(18),dp(18));flip.setBackground(surface(0xff15251f,30,0xff345143));CameraGlyph flipIcon=new CameraGlyph("flip",MINT);flipIcon.setBounds(0,0,dp(24),dp(24));flip.setCompoundDrawables(flipIcon,null,null,null);flip.setContentDescription("Alternar câmera frontal e traseira, inclusive durante a gravação. Atalho: Volume mais.");cameraFlip.addView(flip,new LinearLayout.LayoutParams(dp(60),dp(60)));flip.setOnClickListener(v->switchCamera());TextView flipLabel=text("Virar câmera",10,MUTED);flipLabel.setPadding(0,dp(5),0,0);cameraFlip.addView(flipLabel);
  status=text("Abrindo câmera…",11,TEXT);status.setGravity(Gravity.CENTER);status.setPadding(dp(12),dp(7),dp(12),dp(7));status.setBackground(shape(0xb30c2018,14));status.setMaxLines(3);FrameLayout.LayoutParams statusPosition=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);statusPosition.bottomMargin=dp(66);statusPosition.leftMargin=dp(12);statusPosition.rightMargin=dp(12);viewfinder.addView(status,statusPosition);
  Button share=button("Melhorar no ChatGPT");glyph(share,"spark",MINT);share.setBackground(surface(0xff14372c,18,0xff426a57));root.addView(share,new LinearLayout.LayoutParams(-1,dp(52)));
  share.setOnClickListener(v->{if(recording!=null||capturing){toast("Finalize a captura antes de compartilhar.");return;}ChatGptShare.latest(this);});
  captureFlash=new View(this);captureFlash.setBackgroundColor(0xffe4fff0);captureFlash.setVisibility(View.GONE);viewfinder.addView(captureFlash,new FrameLayout.LayoutParams(-1,-1));
  updateModes();
 }
 private void setMode(boolean video){videoModeSelected=video;updateModes();bindCamera();}
 private void showOptions(){
  new AlertDialog.Builder(this).setTitle("Langer Câmera").setItems(new String[]{"Verificar atualização","Recursos e recuperação","Editar pedido ao ChatGPT"},(d,choice)->{
   if(choice==2){ChatGptShare.editPrompt(this);return;}
   if(choice==1){showInfo();return;}
   if(recording!=null||capturing||getSharedPreferences("luma",0).getBoolean("busy",false)){toast("Finalize a captura e o tratamento antes de atualizar.");return;}
   startActivity(new Intent(this,UpdaterActivity.class));
  }).setNegativeButton("Fechar",null).show();
 }
 private void showFocus(float x,float y){
  focusRing.animate().cancel();FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)focusRing.getLayoutParams();
  params.leftMargin=(int)Math.max(0,Math.min(viewfinder.getWidth()-dp(58),x-dp(29)));params.topMargin=(int)Math.max(0,Math.min(viewfinder.getHeight()-dp(58),y-dp(29)));focusRing.setLayoutParams(params);
  focusRing.setVisibility(View.VISIBLE);focusRing.setAlpha(1);focusRing.setScaleX(1.25f);focusRing.setScaleY(1.25f);focusRing.animate().scaleX(1).scaleY(1).alpha(0).setStartDelay(300).setDuration(700).withEndAction(()->focusRing.setVisibility(View.GONE)).start();
 }
 private void captureFeedback(File file){
  if(destroyed)return;
  if(resumed){
   captureFlash.animate().cancel();captureFlash.setVisibility(View.VISIBLE);captureFlash.setAlpha(.55f);captureFlash.animate().alpha(0).setStartDelay(0).setDuration(210).withEndAction(()->captureFlash.setVisibility(View.GONE)).start();
   captureNotice.animate().cancel();captureNotice.setText("Foto capturada · preparando");captureNotice.setVisibility(View.VISIBLE);captureNotice.setAlpha(1);captureNotice.setTranslationY(dp(8));captureNotice.animate().translationY(0).setStartDelay(0).setDuration(150).withEndAction(()->captureNotice.animate().alpha(0).setStartDelay(1400).setDuration(220).withEndAction(()->captureNotice.setVisibility(View.GONE)).start()).start();
   shutter.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
  }
  thumbnailLabel.setText("Processando…");thumbnailUri=null;int generation=++thumbnailGeneration;
  try{
   // Open before the processing service can remove the temporary capture.
   FileInputStream stream=new FileInputStream(file);
   try{io.execute(()->{try(stream){Bitmap bitmap=CaptureThumbnail.read(stream);runOnUiThread(()->applyThumbnail(bitmap,generation,true));}catch(Exception ignored){}});}
   catch(RejectedExecutionException e){stream.close();}
  }catch(IOException ignored){}
 }
 private void refreshThumbnail(String uri){
  thumbnailLabel.setText("Galeria");
  if(uri==null){if(thumbnailBitmap!=null){thumbnailBitmap=null;thumbnail.setImageDrawable(new CameraGlyph("gallery",MINT));thumbnail.setPadding(dp(16),dp(16),dp(16),dp(16));}return;}
  if(uri==null||uri.equals(thumbnailUri)||destroyed)return;thumbnailUri=uri;int generation=++thumbnailGeneration;
  io.execute(()->{try{Bitmap bitmap=getContentResolver().loadThumbnail(Uri.parse(uri),new Size(180,180),null);runOnUiThread(()->applyThumbnail(bitmap,generation,false));}
   catch(Exception e){runOnUiThread(()->{if(!destroyed&&generation==thumbnailGeneration){thumbnailUri=null;}});}});
 }
 private void applyThumbnail(Bitmap bitmap,int generation,boolean pending){
  if(destroyed||generation!=thumbnailGeneration){bitmap.recycle();return;}
  thumbnailBitmap=bitmap;thumbnail.setPadding(0,0,0,0);thumbnail.setImageBitmap(bitmap);gallery.setContentDescription(pending?"Foto capturada; tratamento em andamento":"Abrir última captura na galeria");
  thumbnail.animate().cancel();thumbnail.setScaleX(.88f);thumbnail.setScaleY(.88f);thumbnail.animate().scaleX(1).scaleY(1).setDuration(220).start();
 }
 private void updateModes(){
  photoMode.setTextColor(videoModeSelected?MUTED:INK);photoMode.setBackground(surface(videoModeSelected?0xff15251f:MINT,14,0));glyph(photoMode,"camera",videoModeSelected?MUTED:INK);
  videoMode.setTextColor(videoModeSelected?INK:MUTED);videoMode.setBackground(surface(videoModeSelected?MINT:0xff15251f,14,0));glyph(videoMode,"video",videoModeSelected?INK:MUTED);
  shutter.setVideoMode(videoModeSelected);
  stabilize.setVisibility(videoModeSelected?View.VISIBLE:View.GONE);
  modeLabel.setText(videoModeSelected?"Trocar câmera: toque duplo ou Volume +":"Luz equilibrada · foto tratada no aparelho");
  updateFlashButton();
 }
 private void clearScreenLight(){
  screenFlashSerial++;if(screenFlashDeadline!=null)handler.removeCallbacks(screenFlashDeadline);screenFlashDeadline=null;
  if(screenLight!=null)screenLight.setVisibility(View.GONE);
  if(screenFlashActive){WindowManager.LayoutParams attributes=getWindow().getAttributes();attributes.screenBrightness=previousBrightness;getWindow().setAttributes(attributes);screenFlashActive=false;}
 }
 private void showFlashOptions(){
  if(activeCamera==null||capturing||switchingCamera||binding||recordingStopping)return;
  if(videoModeSelected){
   if(!activeCamera.getCameraInfo().hasFlashUnit()){toast("Esta câmera não tem luz contínua para vídeo.");return;}
   new AlertDialog.Builder(this).setTitle("Luz do vídeo").setSingleChoiceItems(new String[]{"Desligada","Ligada · luz contínua"},torchWanted?1:0,(d,item)->{torchWanted=item==1;applyLighting();d.dismiss();}).setNegativeButton("Fechar",null).show();
  }else if(front){
   new AlertDialog.Builder(this).setTitle("Flash da câmera frontal").setSingleChoiceItems(new String[]{"Desligado","Ligado · iluminar com a tela"},screenFlashEnabled?1:0,(d,item)->{screenFlashEnabled=item==1;getSharedPreferences("luma",0).edit().putBoolean("screenFlash",screenFlashEnabled).apply();applyLighting();d.dismiss();}).setNegativeButton("Fechar",null).show();
  }else{
   if(!activeCamera.getCameraInfo().hasFlashUnit()){toast("Esta câmera não possui flash.");return;}
   int selected=flashMode==ImageCapture.FLASH_MODE_AUTO?1:flashMode==ImageCapture.FLASH_MODE_ON?2:0;
   new AlertDialog.Builder(this).setTitle("Flash da foto").setSingleChoiceItems(new String[]{"Desligado","Automático","Ligado"},selected,(d,item)->{flashMode=item==1?ImageCapture.FLASH_MODE_AUTO:item==2?ImageCapture.FLASH_MODE_ON:ImageCapture.FLASH_MODE_OFF;getSharedPreferences("luma",0).edit().putInt("flashMode",flashMode).apply();applyLighting();d.dismiss();}).setNegativeButton("Fechar",null).show();
  }
 }
 private void updateFlashButton(){
  if(flashButton==null)return;
  boolean hasFlash=activeCamera!=null&&activeCamera.getCameraInfo().hasFlashUnit();
  String value=videoModeSelected?(!hasFlash?"SEM":torchWanted?"LUZ":"OFF"):front?(screenFlashEnabled?"TELA":"OFF"):!hasFlash?"SEM":flashMode==ImageCapture.FLASH_MODE_AUTO?"AUTO":flashMode==ImageCapture.FLASH_MODE_ON?"ON":"OFF";
  flashButton.setText(value);flashButton.setContentDescription(videoModeSelected?"Luz contínua do vídeo: "+value:"Flash da foto: "+value);
 }
 private void applyLighting(){
  if(activeCamera==null)return;androidx.camera.core.Camera target=activeCamera;
  if(imageCapture!=null){imageCapture.setScreenFlash(screenFlash);imageCapture.setFlashMode(front?(screenFlashEnabled?ImageCapture.FLASH_MODE_SCREEN:ImageCapture.FLASH_MODE_OFF):target.getCameraInfo().hasFlashUnit()?flashMode:ImageCapture.FLASH_MODE_OFF);}
  if(target.getCameraInfo().hasFlashUnit()){
   boolean on=videoModeSelected&&torchWanted;ListenableFuture<Void> light=target.getCameraControl().enableTorch(on);
   light.addListener(()->{try{light.get();}catch(Exception e){if(target==activeCamera&&on&&!destroyed){torchWanted=false;updateFlashButton();toast("Não foi possível ligar a luz contínua.");}}},ContextCompat.getMainExecutor(this));
  }
  updateFlashButton();
 }
 private boolean busyWithCapture(){return capturing||binding||switchingCamera||recordingStarting||recordingStopping||getSharedPreferences("luma",0).getBoolean("busy",false);}
 private void switchCamera(){
  if(provider==null||busyWithCapture()||!resumed)return;
  long now=SystemClock.elapsedRealtime();if(now-lastCameraSwitch<1000)return;lastCameraSwitch=now;
  boolean oldFront=front;CameraSelector target=front?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;
  try{if(!provider.hasCamera(target)){toast("A outra câmera não está disponível.");return;}}catch(Exception e){error("Não foi possível consultar a câmera",e);return;}
  flip.animate().rotationBy(180).setDuration(220).start();
  if(recording==null){front=!front;bindCamera();return;}
  if(!recordingStarted||cameraPreview==null||videoCapture==null)return;
  Recording session=recording;switchingCamera=true;flip.setEnabled(false);status.setText("Alternando câmera…");int serial=++switchSerial;
  try{
   session.pause();if(activeCamera!=null&&activeCamera.getCameraInfo().hasFlashUnit())activeCamera.getCameraControl().enableTorch(false);
   provider.unbind(cameraPreview,videoCapture);front=!oldFront;activeCamera=provider.bindToLifecycle(this,target,cameraPreview,videoCapture);
   waitForSwitch(session,serial,oldFront,false);
  }catch(Exception e){rollbackSwitch(session,serial,oldFront);}
 }
 private void clearSwitchWatch(){
  if(switchDeadline!=null)handler.removeCallbacks(switchDeadline);switchDeadline=null;
  if(switchObservedCamera!=null){switchObservedCamera.getCameraInfo().getCameraState().removeObservers(this);switchObservedCamera=null;}
 }
 private void waitForSwitch(Recording session,int serial,boolean oldFront,boolean rollback){
  clearSwitchWatch();if(destroyed||recording!=session||recordingStopping||serial!=switchSerial)return;
  switchObservedCamera=activeCamera;androidx.camera.core.Camera expected=activeCamera;
  switchDeadline=()->{if(serial!=switchSerial||recording!=session)return;if(rollback)finishFailedSwitch();else rollbackSwitch(session,serial,oldFront);};handler.postDelayed(switchDeadline,6000);
  expected.getCameraInfo().getCameraState().observe(this,state->{
   if(serial!=switchSerial||recording!=session||recordingStopping||!switchingCamera||!resumed||activeCamera!=expected)return;
   if(state.getType()==CameraState.Type.OPEN){
    clearSwitchWatch();switchingCamera=false;
    try{session.resume();applyLighting();badge.setText((front?"Frontal":"Traseira")+" · VÍDEO");status.setText("Gravando · toque duplo ou Volume + para virar");if(rollback)toast("Troca indisponível. A gravação continua na câmera anterior.");}
    catch(Exception e){finishFailedSwitch();}
   }
  });
 }
 private void rollbackSwitch(Recording session,int serial,boolean oldFront){
  if(recording!=session||recordingStopping||serial!=switchSerial)return;clearSwitchWatch();
  try{provider.unbind(cameraPreview,videoCapture);front=oldFront;activeCamera=provider.bindToLifecycle(this,front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,videoCapture);status.setText("Restaurando câmera anterior…");waitForSwitch(session,serial,oldFront,true);}
  catch(Exception e){finishFailedSwitch();}
 }
 private void finishFailedSwitch(){toast("Não foi possível retomar a câmera. Finalizando para preservar o vídeo gravado.");stopRecording();}
 private void stopRecording(){
  if(recording==null||recordingStopping)return;recordingStopping=true;switchSerial++;clearSwitchWatch();switchingCamera=false;shutter.setEnabled(false);status.setText("Finalizando gravação…");recording.stop();
 }
 @Override public boolean onKeyDown(int keyCode,KeyEvent event){
  if(keyCode==KeyEvent.KEYCODE_VOLUME_UP&&recording!=null&&resumed){volumeSwitchHeld=true;if(event.getRepeatCount()==0)switchCamera();return true;}
  return super.onKeyDown(keyCode,event);
 }
 @Override public boolean onKeyUp(int keyCode,KeyEvent event){
  if(keyCode==KeyEvent.KEYCODE_VOLUME_UP&&volumeSwitchHeld){volumeSwitchHeld=false;return true;}
  return super.onKeyUp(keyCode,event);
 }
 private void loadCamera(){
  if(loadingCamera)return;loadingCamera=true;
  ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
  future.addListener(()->{try{provider=future.get();bindCamera();}catch(Exception e){error("Não foi possível abrir a câmera",e);}finally{loadingCamera=false;}},ContextCompat.getMainExecutor(this));
 }
 private void bindCamera(){
  if(provider==null||recording!=null)return;binding=true;status.setText("Preparando câmera…");clearScreenLight();
  try{
   CameraSelector selector=front?CameraSelector.DEFAULT_FRONT_CAMERA:CameraSelector.DEFAULT_BACK_CAMERA;
   if(!provider.hasCamera(selector)){front=!front;binding=false;toast("Câmera indisponível");return;}
   provider.unbindAll();imageCapture=null;videoCapture=null;
   Preview.Builder pb=new Preview.Builder();
   String id=androidx.camera.camera2.interop.Camera2CameraInfo.from(provider.getAvailableCameraInfos().stream().filter(c->selector.filter(Collections.singletonList(c)).size()>0).findFirst().orElseThrow()).getCameraId();
   CameraCharacteristics cc=((CameraManager)getSystemService(CAMERA_SERVICE)).getCameraCharacteristics(id);
   int[] eis=cc.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
   boolean hasEis=false;if(eis!=null)for(int m:eis)if(m==CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)hasEis=true;
   // Use one stabilization stage to avoid fighting vendor EIS during offline analysis.
   new Camera2Interop.Extender<>(pb).setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
   Preview p=pb.build();p.setSurfaceProvider(preview.getSurfaceProvider());cameraPreview=p;
   if(videoModeSelected){
    Recorder recorder=new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD,FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build();
    videoCapture=new VideoCapture.Builder<>(recorder).setTargetFrameRate(new android.util.Range<>(30,30)).build();videoCapture.setTargetRotation(captureRotation);
    activeCamera=provider.bindToLifecycle(this,selector,p,videoCapture);
   }else{
    imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setTargetResolution(new Size(3000,4000)).setTargetRotation(captureRotation).build();
    activeCamera=provider.bindToLifecycle(this,selector,p,imageCapture);
   }
   cameraNote="Camera2 EIS: "+(hasEis?"disponível":"não anunciado")+"\nEstabilização do app: pós-processamento por imagem, independente do EIS.\n";
   applyLighting();
   badge.setText((front?"Frontal":"Traseira")+" · "+(videoModeSelected?"VÍDEO":"FOTO"));
   status.setText("Pronto para capturar");
  }catch(Exception e){error("Falha ao configurar câmera",e);}finally{binding=false;}
 }
 private void capturePhoto(){
  if(imageCapture==null||capturing)return;capturing=true;shutter.setEnabled(false);status.setText("Capturando…");
  File file=new File(getFilesDir(),"photo_"+MediaFiles.stamp()+".jpg");
  imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){
   public void onImageSaved(ImageCapture.OutputFileResults result){clearScreenLight();capturing=false;captureFeedback(file);process(file,false);}
   public void onError(ImageCaptureException e){clearScreenLight();capturing=false;error("Não foi possível tirar a foto",e);}
  });
 }
 private void toggleVideo(){
  if(recording!=null){stopRecording();return;}
  if(videoCapture==null||busyWithCapture())return;
  try{
   recordingStarting=true;recordingStarted=false;recordingStopping=false;recordedNanos=0;shutter.setEnabled(false);
   recordingFile=new File(getFilesDir(),"video_"+MediaFiles.stamp()+".mp4");
   PendingRecording pending=videoCapture.getOutput().prepareRecording(this,new FileOutputOptions.Builder(recordingFile).build()).asPersistentRecording();
   boolean audio=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
   if(audio)pending=pending.withAudioEnabled();else toast("Gravando sem áudio. Ative o microfone nas permissões para ter som.");
   recording=pending.start(ContextCompat.getMainExecutor(this),event->{
    recordedNanos=event.getRecordingStats().getRecordedDurationNanos();
    if(event instanceof VideoRecordEvent.Start){recordingStarting=false;recordingStarted=true;recordStart=SystemClock.elapsedRealtime();shutter.setRecording(true);timer.setBackground(shape(0xb33e1719,16));status.setText("Gravando · toque duplo ou Volume + para virar");}
    if(event instanceof VideoRecordEvent.Finalize){
     VideoRecordEvent.Finalize end=(VideoRecordEvent.Finalize)event;recording=null;recordingStarted=false;recordingStarting=false;recordingStopping=false;switchSerial++;clearSwitchWatch();switchingCamera=false;timer.setText("");timer.setBackgroundColor(Color.TRANSPARENT);shutter.setRecording(false);torchWanted=false;applyLighting();
     if(!end.hasError()||end.getError()==VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE||end.getError()==VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED){
      if(recordingFile.length()>0){if(resumed)process(recordingFile,true);else preservePending(recordingFile,true);}
     }else{preservePending(recordingFile,true);error("A gravação foi interrompida; arquivo preservado",end.getCause());}
    }
   });
  }catch(Exception e){recordingStarting=false;recordingStarted=false;error("Não foi possível gravar",e);}
 }
 private void preservePending(File file,boolean video){getSharedPreferences("luma",0).edit().putString("pending",file.getAbsolutePath()).putBoolean("pendingVideo",video).putString("status","Captura pendente · abra Recursos e recuperação").apply();}
 private void process(File file,boolean video){
  preservePending(file,video);
  try{getSharedPreferences("luma",0).edit().putBoolean("busy",true).putString("status","Preparando processamento…").apply();
   startForegroundService(new Intent(this,ProcessingService.class).putExtra("path",file.getAbsolutePath()).putExtra("video",video).putExtra("stabilize",stabilize.isChecked()));
  }catch(Exception e){getSharedPreferences("luma",0).edit().putBoolean("busy",false).apply();error("Captura preservada; tratamento não iniciou",e);}
 }
 private void openGallery(){
  SharedPreferences p=getSharedPreferences("luma",0);String uri=p.getString("lastUri",null);
  if(uri==null){toast("As capturas aparecerão em Pictures/Langer e Movies/Langer.");return;}
  try{startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri),p.getString("lastType","image/jpeg")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
  catch(Exception e){toast("Abra a galeria do celular e procure a pasta Langer.");}
 }
 private void showInfo(){
  SensorManager sm=(SensorManager)getSystemService(SENSOR_SERVICE);boolean gyro=sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!=null;
  SharedPreferences p=getSharedPreferences("luma",0);
  String message="Versão de teste 0.1.11\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n\n"+cameraNote+"Giroscópio: "+(gyro?"detectado; ainda não usado nesta versão":"não detectado")+"\n\nFotos: ajuste conservador de luz com preservação das cores. Esta versão não contém modelo neural de IA.\n\nVídeo: análise a 15 amostras/s, suavização de deslocamento e rotação; recorte de 20% na imagem quando estabilizada. Cor e brilho consistentes no vídeo. Corrida intensa, baixa luz e objetos próximos podem limitar o resultado.\n\nProcessamento 100% local, sem internet. Fotos: somente a tratada é salva na galeria. Vídeos: original e resultado ficam na pasta Langer.\n\nAo sair, uma gravação é finalizada. O tratamento continua pela notificação, que permite cancelar. Se o Android encerrar o app, recupere as capturas aqui.\n\nBibliotecas: AndroidX/Media3 (Apache 2.0), OpenCV 4.12 (Apache 2.0).";
  AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("Langer · recursos reais").setMessage(message).setPositiveButton("Fechar",null);
  if(p.getBoolean("busy",false))dialog.setNegativeButton("Cancelar tratamento",(d,w)->startService(new Intent(this,ProcessingService.class).setAction("cancel")));
  else dialog.setNeutralButton("Recuperar capturas",(d,w)->recoverCaptures());
  dialog.show();
 }
 private void recoverCaptures(){
  if(getSharedPreferences("luma",0).getBoolean("busy",false))return;
  File[] files=getFilesDir().listFiles((d,n)->n.startsWith("photo_")||n.startsWith("video_"));
  if(files==null||files.length==0){toast("Nenhuma captura pendente.");return;}
  Arrays.sort(files,Comparator.comparing(File::getName));
  String[] names=Arrays.stream(files).map(f->f.getName()+" · "+(f.length()/1024)+" KB").toArray(String[]::new);
  new AlertDialog.Builder(this).setTitle("Capturas preservadas").setItems(names,(d,w)->process(files[w],files[w].getName().endsWith(".mp4"))).setNegativeButton("Fechar",null).show();
 }
 private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 private void error(String text,Throwable e){android.util.Log.e("Langer",text,e);String message=text+(e!=null&&e.getMessage()!=null?": "+e.getMessage():"");status.setText(message);getSharedPreferences("luma",0).edit().putString("status",message).apply();toast(message);}
 @Override public void onRequestPermissionsResult(int request,String[] names,int[] grants){super.onRequestPermissionsResult(request,names,grants);if(request==41){if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)loadCamera();else new AlertDialog.Builder(this).setTitle("Permissão da câmera").setMessage("Permita o acesso à câmera nas configurações do Android para capturar fotos e vídeos.").setPositiveButton("Configurações",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).setNegativeButton("Fechar",null).show();}}
 @Override protected void onResume(){super.onResume();resumed=true;handler.post(refresh);if(orientationListener!=null)orientationListener.enable();if(provider!=null)bindCamera();else if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)loadCamera();}
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(refresh);if(orientationListener!=null)orientationListener.disable();clearScreenLight();torchWanted=false;if(activeCamera!=null&&activeCamera.getCameraInfo().hasFlashUnit())activeCamera.getCameraControl().enableTorch(false);stopRecording();super.onPause();}
 @Override protected void onDestroy(){destroyed=true;switchSerial++;clearSwitchWatch();clearScreenLight();if(recording!=null)recording.stop();thumbnailGeneration++;handler.removeCallbacks(refresh);captureFlash.animate().cancel();captureNotice.animate().cancel();focusRing.animate().cancel();thumbnail.animate().cancel();thumbnail.setImageDrawable(null);thumbnailBitmap=null;io.shutdown();super.onDestroy();}
}
