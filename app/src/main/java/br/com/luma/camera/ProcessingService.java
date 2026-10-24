package br.com.luma.camera;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class ProcessingService extends Service {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private Transformer transformer;
 private File source,output;
 private boolean video,originalPublished;
 private String captureName;
 private Uri originalUri;
 private volatile boolean ended;
 private static final String CHANNEL="processing";
 private PowerManager.WakeLock wakeLock;
 private final Runnable exportProgress=new Runnable(){public void run(){
  if(transformer==null)return;ProgressHolder p=new ProgressHolder();
  if(transformer.getProgress(p)==Transformer.PROGRESS_STATE_AVAILABLE) status("Exportando vídeo · "+p.progress+"%",true);
  main.postDelayed(this,1200);
 }};
 private Notification notification(String text) {
  Intent cancel=new Intent(this,ProcessingService.class).setAction("cancel");
  return new Notification.Builder(this,CHANNEL).setSmallIcon(br.com.luma.camera.R.drawable.ic_camera)
   .setContentTitle("Langer Câmera").setContentText(text).setOngoing(true)
   .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE))
   .addAction(new Notification.Action.Builder(null,"Cancelar",PendingIntent.getService(this,1,cancel,PendingIntent.FLAG_IMMUTABLE)).build()).build();
 }
 @Override public void onCreate(){
  super.onCreate();
  getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Processamento offline",NotificationManager.IMPORTANCE_LOW));
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId) {
  if(intent==null){stopSelf();return START_NOT_STICKY;}
  if("cancel".equals(intent.getAction())){cancel();return START_NOT_STICKY;}
  if(source!=null)return START_NOT_STICKY;
  source=new File(intent.getStringExtra("path"));video=intent.getBooleanExtra("video",false);
  captureName="LANGER_"+MediaFiles.stamp();
  boolean stabilize=intent.getBooleanExtra("stabilize",true);
  startForeground(7,notification("Preparando processamento…"));status("Preparando processamento…",true);
  wakeLock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Langer:Processing");wakeLock.acquire(30*60*1000L);
  getSharedPreferences("luma",0).edit().putString("pending",source.getAbsolutePath()).putBoolean("pendingVideo",video).apply();
  worker.execute(()->{
   try {
    originalUri=MediaFiles.publish(this,source,video,captureName+"_original."+(video?"mp4":"jpg"));originalPublished=true;
    output=new File(getCacheDir(),"enhanced_"+MediaFiles.stamp()+(video?".mp4":".jpg"));
    if(video){
     VideoAnalyzer.Result analysis=VideoAnalyzer.analyze(source,stabilize,s->status(s,true));
     main.post(()->exportVideo(analysis,stabilize));
    } else {
     status("Melhorando iluminação e cores…",true);PhotoEnhancer.process(source,output);
     if(ended) return;
     Uri treatedUri=MediaFiles.publish(this,output,false,captureName+"_tratada.jpg");
     getSharedPreferences("luma",0).edit().putString("photoOriginal",originalUri.toString()).putString("photoTreated",treatedUri.toString()).apply();
     main.post(()->finish("Duas fotos salvas · toque em Comparar fotos",true));
    }
   }catch(Exception|OutOfMemoryError e){main.post(()->fail(e));}
  });
  return START_NOT_STICKY;
 }
 private void exportVideo(VideoAnalyzer.Result analysis,boolean stabilize){
  if(worker.isShutdown()||ended)return;
  try {
   status("Exportando vídeo…",true);
   List<Effect> effects=new ArrayList<>();
   boolean reliable=stabilize&&analysis.tracked()>Math.max(3,analysis.samples()/5);
   if(reliable) effects.add(new StabilizationEffect(analysis));
   effects.add(new Brightness(analysis.brightness()));
   effects.add(new HslAdjustment.Builder().adjustSaturation(4).build());
   EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
    .setEffects(new Effects(Collections.emptyList(),effects)).build();
   transformer=new Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264)
    .addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition composition,ExportResult result){
      main.removeCallbacks(exportProgress);
      worker.execute(()->{try{
       MediaFiles.publish(ProcessingService.this,output,true,"LANGER_"+MediaFiles.stamp()+"_melhorado.mp4");
       main.post(()->finish(reliable?"Vídeo estabilizado e tratado salvo · original preservado":stabilize?"Vídeo tratado salvo · movimento insuficiente para estabilizar com segurança":"Vídeo com cores e iluminação tratados salvo",true));
      }catch(Exception e){main.post(()->fail(e));}});
     }
     @Override public void onError(Composition composition,ExportResult result,ExportException e){fail(e);}
    }).build();
   transformer.start(item,output.getAbsolutePath());main.post(exportProgress);
  }catch(Exception e){fail(e);}
 }
 private void status(String message,boolean busy){
  if(ended)return;
  getSharedPreferences("luma",0).edit().putString("status",message).putBoolean("busy",busy).apply();
  if(busy)getSystemService(NotificationManager.class).notify(7,notification(message));
 }
 private void fail(Throwable e){
  if(ended)return;
  android.util.Log.e("Langer","Processing failed",e);
  finish((originalPublished?"Original salvo. ":"Captura preservada para recuperação. ")+"Falha no tratamento: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),false);
 }
 private void cancel(){
  if(transformer!=null)transformer.cancel();worker.shutdownNow();
  finish(originalPublished?"Processamento cancelado · original salvo":"Cancelado · captura preservada para recuperação",false);
 }
 private void finish(String message,boolean success){
  if(ended)return;
  main.removeCallbacks(exportProgress);status(message,false);
  ended=true;
  if(success){if(source!=null)source.delete();if(output!=null)output.delete();getSharedPreferences("luma",0).edit().remove("pending").apply();}
  stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
 }
 @Override public void onDestroy(){
  ended=true;
  main.removeCallbacks(exportProgress);if(transformer!=null)transformer.cancel();worker.shutdownNow();
  if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
  getSharedPreferences("luma",0).edit().putBoolean("busy",false).apply();super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}
