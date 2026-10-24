package br.com.luma.camera;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native vector controls, independent of the phone's emoji/font rendering. */
public final class CameraGlyph extends Drawable {
 private final String name;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int color;
 public CameraGlyph(String name,int color){this.name=name;this.color=color;}
 @Override public void draw(Canvas canvas){
  canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
  paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
  switch(name){
   case "flip":
    canvas.drawArc(4,4,20,20,205,135,false,paint);canvas.drawArc(4,4,20,20,25,135,false,paint);
    path(canvas,17,3,20,8,14,8);path(canvas,7,21,4,16,10,16);canvas.drawCircle(12,12,2.3f,paint);break;
   case "menu":
    canvas.drawLine(5,6,19,6,paint);canvas.drawLine(5,12,19,12,paint);canvas.drawLine(5,18,19,18,paint);
    paint.setStyle(Paint.Style.FILL);canvas.drawCircle(9,6,2,paint);canvas.drawCircle(15,12,2,paint);canvas.drawCircle(9,18,2,paint);break;
   case "gallery":
    canvas.drawRoundRect(3,3,21,21,4,4,paint);canvas.drawCircle(8,8,1.5f,paint);path(canvas,4,17,9,12,13,16,16,12,21,18);break;
   case "spark":
    path(canvas,10,3,12,9,18,11,12,13,10,19,8,13,2,11,8,9,10,3);canvas.drawLine(19,3,19,7,paint);canvas.drawLine(17,5,21,5,paint);canvas.drawLine(19,17,19,21,paint);canvas.drawLine(17,19,21,19,paint);break;
   case "video":
    canvas.drawRoundRect(2,6,16,18,3,3,paint);path(canvas,16,10,22,7,22,17,16,14);break;
   default:
    canvas.drawRoundRect(3,6,21,20,4,4,paint);path(canvas,8,6,10,3,14,3,16,6);canvas.drawCircle(12,13,4,paint);
  }
  canvas.restore();
 }
 private void path(Canvas canvas,float... coords){Path p=new Path();p.moveTo(coords[0],coords[1]);for(int i=2;i<coords.length;i+=2)p.lineTo(coords[i],coords[i+1]);canvas.drawPath(p,paint);}
 @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 @Override public int getIntrinsicWidth(){return 24;}
 @Override public int getIntrinsicHeight(){return 24;}
}
