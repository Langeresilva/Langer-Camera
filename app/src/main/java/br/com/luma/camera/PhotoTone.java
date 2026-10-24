package br.com.luma.camera;

/** Conservative luminance lift with identical RGB gain: no scene-average white balance. */
public final class PhotoTone {
 public static double strength(int median){return median<75?0.55:median<115?0.35:median<145?0.15:0;}
 public static int apply(int rgb,double strength){
  int r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
  double luminance=.2126*r+.7152*g+.0722*b;
  int peak=Math.max(r,Math.max(g,b));
  if(luminance==0||luminance>=192||peak==255||strength<=0)return rgb;
  double x=luminance/192;
  double lift=40*strength*4*x*Math.pow(1-x,3);
  double gain=Math.min(1.12,Math.min(1+lift/luminance,255.0/peak));
  return (rgb&0xff000000)|((int)Math.round(r*gain)<<16)|((int)Math.round(g*gain)<<8)|(int)Math.round(b*gain);
 }
}
