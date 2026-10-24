package br.com.luma.camera;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

/** Device-side end-to-end check with an encoded synthetic shaky video. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class PipelineInstrumentation extends Instrumentation {
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Context context=getTargetContext();File dir=context.getCacheDir();
   File original=new File(dir,"test_dark.jpg"),enhanced=new File(dir,"test_light.jpg");
   Bitmap bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.rgb(45,50,55));
   try(FileOutputStream stream=new FileOutputStream(original)){bitmap.compress(Bitmap.CompressFormat.JPEG,98,stream);}bitmap.recycle();
   PhotoEnhancer.process(original,enhanced);Bitmap after=BitmapFactory.decodeFile(enhanced.getAbsolutePath());
   if(after==null||Color.green(after.getPixel(100,100))<=50)throw new AssertionError("Foto escura não foi clareada");after.recycle();
   File source=new File(dir,"test_shaky.mp4");encode(source);
   VideoAnalyzer.Result analyzed=VideoAnalyzer.analyze(source,true,s->{});
   if(analyzed.tracked()<20)throw new AssertionError("Poucos movimentos rastreados: "+analyzed.tracked());
   File output=new File(dir,"test_stable.mp4");if(output.exists())output.delete();
   CountDownLatch latch=new CountDownLatch(1);Throwable[] error=new Throwable[1];Transformer[] exporter=new Transformer[1];
   runOnMainSync(()->{
    List<Effect> effects=Arrays.asList(new StabilizationEffect(analyzed),new Brightness(analyzed.brightness()),new HslAdjustment.Builder().adjustSaturation(4).build());
    exporter[0]=new Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition c,ExportResult r){latch.countDown();}
     @Override public void onError(Composition c,ExportResult r,ExportException e){error[0]=e;latch.countDown();}
    }).build();
    exporter[0].start(new EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source))).setEffects(new Effects(Collections.emptyList(),effects)).build(),output.getAbsolutePath());
   });
   if(!latch.await(120,TimeUnit.SECONDS))throw new AssertionError("Exportação excedeu 120s");
   if(error[0]!=null)throw new AssertionError(error[0]);
   if(output.length()<1000)throw new AssertionError("Vídeo exportado vazio");
   VideoAnalyzer.Result stabilized=VideoAnalyzer.analyze(output,true,s->{});
   double inputShake=energy(analyzed.correction()),outputShake=energy(stabilized.correction());
   if(outputShake>=inputShake*.85)throw new AssertionError("Estabilização não reduziu tremor: "+inputShake+" -> "+outputShake);
   MediaFiles.publish(context,output,true,"LANGER_TESTE_estabilizado.mp4");
   result.putString("stream","PASS: foto clareada; OpenCV rastreou "+analyzed.tracked()+" amostras; vídeo H.264 exportado e salvo. Tremor "+inputShake+" -> "+outputShake+"\n");
   finish(-1,result);
  }catch(Throwable e){android.util.Log.e("LangerTest","Pipeline failure",e);result.putString("stream","FAIL: "+e+"\n");finish(0,result);}
 }
 private double energy(MotionPath p){double total=0;for(int i=3;i<p.x.length-3;i++)total+=p.x[i]*p.x[i]+p.y[i]*p.y[i];return total/Math.max(1,p.x.length-6);}
 private void encode(File file) throws Exception {
  int width=320,height=240;MediaCodec codec=MediaCodec.createEncoderByType("video/avc");MediaMuxer muxer=null;
  try{
   MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
   format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
   format.setInteger(MediaFormat.KEY_BIT_RATE,2_000_000);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
   codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();muxer=new MediaMuxer(file.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
   int track=-1,frame=0;boolean done=false,eos=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
   int[] texture=new int[width*height];Random random=new Random(9);for(int i=0;i<texture.length;i++)texture[i]=50+random.nextInt(160);
   while(!done){
    if(!eos){int index=codec.dequeueInputBuffer(10000);if(index>=0){
     if(frame==90){codec.queueInputBuffer(index,0,0,frame*1_000_000L/30,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eos=true;}
     else{
      Image image=codec.getInputImage(index);if(image==null)throw new IOException("Encoder sem imagem YUV");
      int dx=(int)Math.round(7*Math.sin(frame*1.1)),dy=(int)Math.round(5*Math.cos(frame*1.3));
      for(int plane=0;plane<3;plane++){
       Image.Plane p=image.getPlanes()[plane];ByteBuffer buffer=p.getBuffer();int w=plane==0?width:width/2,h=plane==0?height:height/2;
       for(int y=0;y<h;y++)for(int x=0;x<w;x++){
        int v=128;if(plane==0){int sx=Math.max(0,Math.min(width-1,x-dx)),sy=Math.max(0,Math.min(height-1,y-dy));v=texture[sy*width+sx];}
        buffer.put(y*p.getRowStride()+x*p.getPixelStride(),(byte)v);
       }
      }
      image.close();codec.queueInputBuffer(index,0,width*height*3/2,frame*1_000_000L/30,0);frame++;
     }
    }}
    int out=codec.dequeueOutputBuffer(info,10000);
    if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.getOutputFormat());muxer.start();}
    else if(out>=0){ByteBuffer data=codec.getOutputBuffer(out);if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0){data.position(info.offset);data.limit(info.offset+info.size);muxer.writeSampleData(track,data,info);}done=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(out,false);}
   }
   muxer.stop();
  }finally{codec.stop();codec.release();if(muxer!=null)muxer.release();}
 }
}
