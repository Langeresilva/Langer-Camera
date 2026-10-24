package br.com.luma.camera;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.Button;

/** A quiet lens-like shutter with distinct recording and busy states. */
public final class ShutterButton extends Button {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private boolean video,recording;
 public ShutterButton(Context context){super(context);setBackgroundColor(Color.TRANSPARENT);setPadding(0,0,0,0);setMinWidth(0);setMinHeight(0);setContentDescription("Tirar foto");}
 public void setVideoMode(boolean value){video=value;setContentDescription(value?"Iniciar gravação":"Tirar foto");invalidate();}
 public void setRecording(boolean value){recording=value;setContentDescription(value?"Parar gravação":video?"Iniciar gravação":"Tirar foto");invalidate();}
 @Override protected void onDraw(Canvas canvas){
  float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-3;
  int accent=recording||video?0xffff8c87:0xff9ee5cf;
  paint.setStyle(Paint.Style.FILL);paint.setShader(new RadialGradient(x,y,r,new int[]{0x003acfa2,0x183acfa2,0x003acfa2},new float[]{0,.75f,1},Shader.TileMode.CLAMP));canvas.drawCircle(x,y,r,paint);paint.setShader(null);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);paint.setColor(isEnabled()?accent:0xff53645e);canvas.drawCircle(x,y,r-5,paint);
  if(!isEnabled()){paint.setColor(0xffb8ebda);paint.setStrokeWidth(3.5f);canvas.drawArc(x-r+5,y-r+5,x+r-5,y+r-5,(SystemClock.uptimeMillis()%1400)*360f/1400,85,false,paint);if(isAttachedToWindow()&&isShown())postInvalidateDelayed(40);}
  paint.setStyle(Paint.Style.FILL);paint.setColor(recording?0xffff7c76:video?0xfff2948c:0xffedf7f1);
  if(!isEnabled())paint.setColor(0xff71877d);
  if(recording){float side=r*.42f;canvas.drawRoundRect(x-side,y-side,x+side,y+side,side*.25f,side*.25f,paint);}
  else canvas.drawCircle(x,y,r-13,paint);
 }
 @Override public boolean onTouchEvent(android.view.MotionEvent event){
  if(isEnabled()){
   if(event.getAction()==MotionEvent.ACTION_DOWN)animate().scaleX(.93f).scaleY(.93f).setDuration(90).start();
   if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)animate().scaleX(1).scaleY(1).setDuration(150).start();
  }return super.onTouchEvent(event);
 }
}
