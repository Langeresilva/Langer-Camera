package br.com.luma.camera;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import org.opencv.android.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;
import org.opencv.calib3d.Calib3d;

public final class VideoAnalyzer {
 public static final int FPS=15;
 public record Result(MotionPath correction,int width,int height,float brightness,int tracked,int samples) {}
 public static Result analyze(File file,boolean stabilize,Consumer<String> progress) throws Exception {
  if(!OpenCVLoader.initLocal()) throw new IllegalStateException("OpenCV indisponível neste aparelho");
  MediaMetadataRetriever retriever=new MediaMetadataRetriever();
  Mat previous=null;
  try {
   retriever.setDataSource(file.getAbsolutePath());
   long duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
   int frames=Math.max(1,(int)Math.ceil(duration*FPS/1000.0));
   MotionPath path=new MotionPath(frames); double luminosity=0; int width=256,height=144,tracked=0,received=0;
   for(int i=0;i<frames;i++) {
    if(Thread.currentThread().isInterrupted()) throw new InterruptedException("Cancelado");
    Bitmap bmp=retriever.getScaledFrameAtTime(i*1_000_000L/FPS,MediaMetadataRetriever.OPTION_CLOSEST,320,320);
    if(i>0) {path.x[i]=path.x[i-1];path.y[i]=path.y[i-1];path.a[i]=path.a[i-1];}
    if(bmp==null) continue;
    width=bmp.getWidth();height=bmp.getHeight();
    Mat rgba=new Mat(),gray=new Mat();
    Utils.bitmapToMat(bmp,rgba);bmp.recycle();Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);rgba.release();
    luminosity+=Core.mean(gray).val[0];received++;
    if(stabilize&&previous!=null) {
     MatOfPoint corners=new MatOfPoint();Imgproc.goodFeaturesToTrack(previous,corners,140,0.015,8);
     if(corners.rows()>=12) {
      MatOfPoint2f p0=new MatOfPoint2f(corners.toArray()),p1=new MatOfPoint2f();
      MatOfByte status=new MatOfByte();MatOfFloat errors=new MatOfFloat();
      Video.calcOpticalFlowPyrLK(previous,gray,p0,p1,status,errors);
      Point[] old=p0.toArray(),next=p1.toArray();byte[] ok=status.toArray();float[] err=errors.toArray();
      ArrayList<Point> a=new ArrayList<>(),b=new ArrayList<>();
      for(int j=0;j<ok.length;j++) if(ok[j]!=0&&err[j]<25&&next[j].x>=0&&next[j].x<width&&next[j].y>=0&&next[j].y<height){a.add(old[j]);b.add(next[j]);}
      if(a.size()>=10) {
       MatOfPoint2f aa=new MatOfPoint2f(),bb=new MatOfPoint2f();aa.fromList(a);bb.fromList(b);Mat inliers=new Mat();
       Mat affine=Calib3d.estimateAffinePartial2D(aa,bb,inliers,Calib3d.RANSAC,2.0,1500,0.99,10);
       if(!affine.empty()&&Core.countNonZero(inliers)>=8) {
        double m00=affine.get(0,0)[0],m01=affine.get(0,1)[0],m10=affine.get(1,0)[0],m11=affine.get(1,1)[0];
        double angle=Math.atan2(m10,m00),scale=Math.hypot(m00,m10);
        double dx=affine.get(0,2)[0]+(m00-1)*width/2.0+m01*height/2.0;
        double dy=affine.get(1,2)[0]+m10*width/2.0+(m11-1)*height/2.0;
        if(Math.abs(dx)<width*.23&&Math.abs(dy)<height*.23&&Math.abs(angle)<.18&&scale>.9&&scale<1.1){
         path.x[i]+=dx;path.y[i]+=dy;path.a[i]+=angle;tracked++;
        }
       }
       affine.release();aa.release();bb.release();inliers.release();
      }
      p0.release();p1.release();status.release();errors.release();
     }
     corners.release();
    }
    if(previous!=null) previous.release(); previous=gray;
    if(i%FPS==0) progress.accept("Analisando vídeo · "+(i*100/frames)+"%");
   }
   float brightness=(float)Math.max(-.025,Math.min(.07,(112-luminosity/Math.max(1,received))/850));
   return new Result(path.corrections(8),width,height,brightness,tracked,frames);
  } finally {if(previous!=null) previous.release();retriever.release();}
 }
}
