package br.com.luma.camera;

/** Offline trajectory smoothing. No Android dependencies, so it can be tested on the JVM. */
public final class MotionPath {
 public final double[] x,y,a;
 public final java.util.BitSet cuts=new java.util.BitSet();
 public MotionPath(int n) { x=new double[n]; y=new double[n]; a=new double[n]; }
 public MotionPath corrections(int radius) {
  MotionPath out=new MotionPath(x.length);
  for(int i=0;i<x.length;i++) {
   double sx=0,sy=0,sa=0,weight=0;
   int start=Math.max(0,cuts.previousSetBit(i));
   int next=cuts.nextSetBit(i+1),end=next<0?x.length-1:next-1;
   for(int j=Math.max(start,i-radius);j<=Math.min(end,i+radius);j++) {
    double w=Math.exp(-0.5*Math.pow((j-i)/(radius/2.0+0.5),2));
    sx+=x[j]*w; sy+=y[j]*w; sa+=a[j]*w; weight+=w;
   }
   out.x[i]=sx/weight-x[i]; out.y[i]=sy/weight-y[i]; out.a[i]=sa/weight-a[i];
  }
  out.cuts.or(cuts);
  return out;
 }
 public double[] at(double index) {
  int i=Math.max(0,Math.min(x.length-1,(int)index));
  int j=Math.min(x.length-1,i+1); if(cuts.get(j))j=i; double t=Math.max(0,Math.min(1,index-i));
  return new double[]{x[i]*(1-t)+x[j]*t,y[i]*(1-t)+y[j]*t,a[i]*(1-t)+a[j]*t};
 }
}
