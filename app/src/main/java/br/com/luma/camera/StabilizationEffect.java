package br.com.luma.camera;

import android.graphics.Matrix;
import androidx.media3.effect.MatrixTransformation;

/** Bounded correction: limits rotation/translation so the output stays inside the source. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class StabilizationEffect implements MatrixTransformation {
 private final VideoAnalyzer.Result result;
 private final double[] zooms;
 public StabilizationEffect(VideoAnalyzer.Result r){result=r;zooms=r.correction().sceneZooms(r.width(),r.height());}
 @Override public Matrix getMatrix(long timeUs) {
  double[] c=result.correction().at(timeUs*VideoAnalyzer.FPS/1_000_000.0);
  double angle=Math.max(-.12,Math.min(.12,c[2]));
  double cos=Math.cos(angle),sin=Math.sin(angle),aspect=(double)result.width()/result.height();
  int index=Math.max(0,Math.min(zooms.length-1,(int)(timeUs*VideoAnalyzer.FPS/1_000_000L)));
  double zoom=zooms[index];
  // Convert pixel-space correction (positive Y down) into normalized GL coordinates.
  double tx=2*c[0]/result.width(),ty=-2*c[1]/result.height();
  // Inverse map output corners; conservatively divide spare margin across both axes.
  double spareX=Math.max(0,1-(Math.abs(cos)+Math.abs(sin)/aspect)/zoom);
  double spareY=Math.max(0,1-(Math.abs(cos)+Math.abs(sin)*aspect)/zoom);
  // Clamp in the rotated source axes: use the available crop without crossing edges.
  double u=cos*(tx*zoom)-sin*(ty*zoom)/aspect;
  double v=sin*aspect*(tx*zoom)+cos*(ty*zoom);
  u=Math.max(-spareX*zoom,Math.min(spareX*zoom,u));
  v=Math.max(-spareY*zoom,Math.min(spareY*zoom,v));
  tx=cos*u+sin*v/aspect;ty=-sin*aspect*u+cos*v;
  Matrix matrix=new Matrix();
  matrix.setValues(new float[]{(float)(zoom*cos),(float)(zoom*sin/aspect),(float)tx,(float)(-zoom*sin*aspect),(float)(zoom*cos),(float)ty,0,0,1});
  return matrix;
 }
}
