package br.com.luma.camera;

import android.graphics.Matrix;
import androidx.media3.effect.MatrixTransformation;

/** Bounded correction: limits rotation/translation so the output stays inside the source. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class StabilizationEffect implements MatrixTransformation {
 private final VideoAnalyzer.Result result;
 public StabilizationEffect(VideoAnalyzer.Result r){result=r;}
 @Override public Matrix getMatrix(long timeUs) {
  double[] c=result.correction().at(timeUs*VideoAnalyzer.FPS/1_000_000.0);
  double angle=Math.max(-.055,Math.min(.055,c[2]));
  double cos=Math.cos(angle),sin=Math.sin(angle),aspect=(double)result.width()/result.height();
  double zoom=1.25;
  // Convert pixel-space correction (positive Y down) into normalized GL coordinates.
  double tx=2*c[0]/result.width(),ty=-2*c[1]/result.height();
  // Inverse map output corners; conservatively divide spare margin across both axes.
  double spareX=Math.max(0,1-(Math.abs(cos)+Math.abs(sin)/aspect)/zoom);
  double spareY=Math.max(0,1-(Math.abs(cos)+Math.abs(sin)*aspect)/zoom);
  double bound=Math.min(spareX,spareY)*zoom/(1+Math.max(aspect,1/aspect));
  tx=Math.max(-bound,Math.min(bound,tx*zoom));ty=Math.max(-bound,Math.min(bound,ty*zoom));
  Matrix matrix=new Matrix();
  matrix.setValues(new float[]{(float)(zoom*cos),(float)(zoom*sin/aspect),(float)tx,(float)(-zoom*sin*aspect),(float)(zoom*cos),(float)ty,0,0,1});
  return matrix;
 }
}
