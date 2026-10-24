package br.com.luma.camera;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

@androidx.annotation.OptIn(markerClass=androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
public class MainActivity extends ComponentActivity {
 private static final int INK=0xff0a1119,MINT=0xffa5f2d1,TEXT=0xfff1f5f8,MUTED=0xffa4afba;
 private PreviewView preview;
 private TextView status,badge,timer,modeLabel;
 private Button shutter,flip,photoMode,videoMode,gallery,info;
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
 private final Runnable refresh=new Runnable(){public void run(){
  SharedPreferences p=getSharedPreferences("luma",0);
  boolean busy=p.getBoolean("busy",false);
  if(recording!=null){long s=(SystemClock.elapsedRealtime()-recordStart)/1000;timer.setText(String.format(Locale.US,"● %02d:%02d",s/60,s%60));}
  else if(!capturing&&!binding) status.setText(busy?p.getString("status","Processando…"):p.getString("status","Pronto para capturar"));
  shutter.setEnabled(!binding&&!capturing&&!busy);
  flip.setEnabled(!binding&&!capturing&&!busy&&recording==null);
  photoMode.setEnabled(recording==null&&!capturing&&!busy&&!binding);
  videoMode.setEnabled(recording==null&&!capturing&&!busy&&!binding);
  stabilize.setEnabled(recording==null&&!busy&&!capturing&&!binding);
  gallery.setEnabled(recording==null&&!capturing);
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
 private Button button(String value){Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(TEXT);b.setBackground(shape(0xff1c2834,18));b.setMinHeight(dp(44));return b;}
 private void buildUi(){
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(INK);
  root.setPadding(dp(16),dp(6),dp(16),dp(8));setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(16)+bars.left,dp(6)+bars.top,dp(16)+bars.right,dp(8)+bars.bottom);return insets;});
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
  TextView title=text("LANGER",26,TEXT);title.setLetterSpacing(.15f);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
  TextView offline=text("●  OFFLINE",11,MINT);offline.setLetterSpacing(.1f);header.addView(offline);
  TextView sub=text("Sua luz. Seu movimento.",13,MUTED);root.addView(sub);
  FrameLayout viewfinder=new FrameLayout(this);LinearLayout.LayoutParams vf=new LinearLayout.LayoutParams(-1,0,1);vf.topMargin=dp(14);vf.bottomMargin=dp(12);root.addView(viewfinder,vf);
  preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);viewfinder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  badge=text("Câmera traseira",11,TEXT);badge.setPadding(dp(12),dp(8),dp(12),dp(8));badge.setBackground(shape(0xa8101923,12));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(10),dp(10),0,0);viewfinder.addView(badge,bp);
  timer=text("",15,0xffff8686);FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);tp.setMargins(0,dp(14),dp(14),0);viewfinder.addView(timer,tp);
  preview.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_UP&&provider!=null&&recording==null){
   try{if(activeCamera==null)return true;
    MeteringPoint point=preview.getMeteringPointFactory().createPoint(event.getX(),event.getY());activeCamera.getCameraControl().startFocusAndMetering(new FocusMeteringAction.Builder(point).setAutoCancelDuration(3,TimeUnit.SECONDS).build());
    Toast.makeText(this,"Ajustando foco e exposição",Toast.LENGTH_SHORT).show();
   }catch(Exception ignored){}v.performClick();}return true;});
  LinearLayout modes=new LinearLayout(this);modes.setGravity(Gravity.CENTER);root.addView(modes);
  photoMode=button("Foto");videoMode=button("Vídeo");LinearLayout.LayoutParams modeParams=new LinearLayout.LayoutParams(dp(110),dp(42));modeParams.setMargins(dp(5),0,dp(5),0);modes.addView(photoMode,modeParams);modes.addView(videoMode,new LinearLayout.LayoutParams(modeParams));
  photoMode.setOnClickListener(v->setMode(false));videoMode.setOnClickListener(v->setMode(true));
  modeLabel=text("",12,MUTED);modeLabel.setGravity(Gravity.CENTER);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);ml.topMargin=dp(10);root.addView(modeLabel,ml);
  stabilize=new Switch(this);stabilize.setText("Estabilizar após gravar");stabilize.setTextSize(13);stabilize.setTextColor(TEXT);stabilize.setChecked(getSharedPreferences("luma",0).getBoolean("stabilize",true));root.addView(stabilize,new LinearLayout.LayoutParams(-1,dp(40)));
  stabilize.setOnCheckedChangeListener((b,on)->getSharedPreferences("luma",0).edit().putBoolean("stabilize",on).apply());
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(80));cp.topMargin=dp(4);root.addView(controls,cp);
  flip=button("Virar");controls.addView(flip,new LinearLayout.LayoutParams(0,dp(46),1));flip.setOnClickListener(v->{front=!front;bindCamera();});
  shutter=button("●");shutter.setTextSize(36);shutter.setTextColor(INK);shutter.setBackground(shape(MINT,40));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(76),dp(76));sp.setMargins(dp(22),0,dp(22),0);controls.addView(shutter,sp);shutter.setOnClickListener(v->{if(videoModeSelected)toggleVideo();else capturePhoto();});
  gallery=button("Galeria");controls.addView(gallery,new LinearLayout.LayoutParams(0,dp(46),1));gallery.setOnClickListener(v->openGallery());
  status=text("Abrindo câmera…",12,MUTED);status.setGravity(Gravity.CENTER);status.setMinHeight(dp(40));root.addView(status,new LinearLayout.LayoutParams(-1,-2));
  info=button("Recursos e recuperação");info.setTextSize(11);root.addView(info,new LinearLayout.LayoutParams(-1,dp(38)));info.setOnClickListener(v->showInfo());
  LinearLayout photoActions=new LinearLayout(this);root.addView(photoActions,new LinearLayout.LayoutParams(-1,dp(48)));
  Button compare=button("Comparar fotos");photoActions.addView(compare,new LinearLayout.LayoutParams(0,-1,1));
  compare.setOnClickListener(v->startActivity(new Intent(this,ComparisonActivity.class)));
  Button share=button("Enviar ao ChatGPT");LinearLayout.LayoutParams shareParams=new LinearLayout.LayoutParams(0,-1,1);shareParams.leftMargin=dp(6);photoActions.addView(share,shareParams);
  share.setOnClickListener(v->{if(recording!=null||capturing){toast("Finalize a captura antes de compartilhar.");return;}ChatGptShare.latest(this);});
  Button updates=button("Verificar atualização");root.addView(updates,new LinearLayout.LayoutParams(-1,dp(40)));
  updates.setOnClickListener(v->{if(recording!=null||capturing||getSharedPreferences("luma",0).getBoolean("busy",false)){toast("Finalize a captura e o tratamento antes de atualizar.");return;}startActivity(new Intent(this,UpdaterActivity.class));});
  updateModes();
 }
 private void setMode(boolean video){videoModeSelected=video;updateModes();bindCamera();}
 private void updateModes(){
  photoMode.setTextColor(videoModeSelected?MUTED:INK);photoMode.setBackground(shape(videoModeSelected?0xff1c2834:MINT,18));
  videoMode.setTextColor(videoModeSelected?INK:MUTED);videoMode.setBackground(shape(videoModeSelected?MINT:0xff1c2834,18));
  stabilize.setVisibility(videoModeSelected?View.VISIBLE:View.GONE);
  modeLabel.setText(videoModeSelected?"1080p · tratamento após gravar · original preservado":"Luz e cores automáticas · original + foto tratada");
 }
 private void loadCamera(){
  if(loadingCamera)return;loadingCamera=true;
  ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
  future.addListener(()->{try{provider=future.get();bindCamera();}catch(Exception e){error("Não foi possível abrir a câmera",e);}finally{loadingCamera=false;}},ContextCompat.getMainExecutor(this));
 }
 private void bindCamera(){
  if(provider==null)return;binding=true;status.setText("Preparando câmera…");
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
   Preview p=pb.build();p.setSurfaceProvider(preview.getSurfaceProvider());
   if(videoModeSelected){
    Recorder recorder=new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD,FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build();
    videoCapture=new VideoCapture.Builder<>(recorder).setTargetFrameRate(new android.util.Range<>(30,30)).build();videoCapture.setTargetRotation(captureRotation);
    activeCamera=provider.bindToLifecycle(this,selector,p,videoCapture);
   }else{
    imageCapture=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setTargetResolution(new Size(3000,4000)).setTargetRotation(captureRotation).build();
    activeCamera=provider.bindToLifecycle(this,selector,p,imageCapture);
   }
   cameraNote="Camera2 EIS: "+(hasEis?"disponível":"não anunciado")+"\nEstabilização do app: pós-processamento por imagem, independente do EIS.\n";
   badge.setText((front?"Frontal":"Traseira")+" · "+(videoModeSelected?"VÍDEO":"FOTO"));
   status.setText("Pronto para capturar");
  }catch(Exception e){error("Falha ao configurar câmera",e);}finally{binding=false;}
 }
 private void capturePhoto(){
  if(imageCapture==null||capturing)return;capturing=true;status.setText("Capturando…");
  File file=new File(getFilesDir(),"photo_"+MediaFiles.stamp()+".jpg");
  imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(this),new ImageCapture.OnImageSavedCallback(){
   public void onImageSaved(ImageCapture.OutputFileResults result){capturing=false;process(file,false);}
   public void onError(ImageCaptureException e){capturing=false;error("Não foi possível tirar a foto",e);}
  });
 }
 private void toggleVideo(){
  if(recording!=null){shutter.setEnabled(false);status.setText("Finalizando gravação…");recording.stop();return;}
  if(videoCapture==null)return;
  try{
   recordingFile=new File(getFilesDir(),"video_"+MediaFiles.stamp()+".mp4");
   PendingRecording pending=videoCapture.getOutput().prepareRecording(this,new FileOutputOptions.Builder(recordingFile).build());
   boolean audio=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
   if(audio)pending=pending.withAudioEnabled();else toast("Gravando sem áudio. Ative o microfone nas permissões para ter som.");
   recording=pending.start(ContextCompat.getMainExecutor(this),event->{
    if(event instanceof VideoRecordEvent.Start){recordStart=SystemClock.elapsedRealtime();shutter.setText("■");shutter.setTextSize(28);shutter.setBackground(shape(0xffff8888,40));status.setText("Gravando · toque no quadrado para finalizar");}
    if(event instanceof VideoRecordEvent.Finalize){
     VideoRecordEvent.Finalize end=(VideoRecordEvent.Finalize)event;recording=null;timer.setText("");shutter.setText("●");shutter.setTextSize(36);shutter.setBackground(shape(MINT,40));
     if(!end.hasError()||end.getError()==VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE||end.getError()==VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED){
      if(recordingFile.length()>0){if(resumed)process(recordingFile,true);else preservePending(recordingFile,true);}
     }else{preservePending(recordingFile,true);error("A gravação foi interrompida; arquivo preservado",end.getCause());}
    }
   });
  }catch(Exception e){error("Não foi possível gravar",e);}
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
  String message="Versão de teste 0.1.7\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n\n"+cameraNote+"Giroscópio: "+(gyro?"detectado; ainda não usado nesta versão":"não detectado")+"\n\nFotos: ajuste conservador de luz com preservação das cores. Esta versão não contém modelo neural de IA.\n\nVídeo: análise a 15 amostras/s, suavização de deslocamento e rotação; recorte de 20% na imagem quando estabilizada. Cor e brilho consistentes no vídeo. Corrida intensa, baixa luz e objetos próximos podem limitar o resultado.\n\nProcessamento 100% local, sem internet. Originais e resultados ficam na pasta Langer da galeria. Não apague o original antes de conferir o resultado.\n\nAo sair, uma gravação é finalizada. O tratamento continua pela notificação, que permite cancelar. Se o Android encerrar o app, recupere as capturas aqui.\n\nBibliotecas: AndroidX/Media3 (Apache 2.0), OpenCV 4.12 (Apache 2.0).";
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
 @Override protected void onPause(){resumed=false;handler.removeCallbacks(refresh);if(orientationListener!=null)orientationListener.disable();if(recording!=null)recording.stop();super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacks(refresh);io.shutdown();super.onDestroy();}
}
