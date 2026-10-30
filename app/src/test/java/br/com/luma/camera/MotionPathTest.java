package br.com.luma.camera;
import org.junit.Test;
import static org.junit.Assert.*;
public class MotionPathTest {
 @Test public void stationaryCameraHasNoCorrection() {
  MotionPath p=new MotionPath(30).corrections(8);
  for(double v:p.x) assertEquals(0,v,1e-9);
 }
 @Test public void alternatingShakeIsReduced() {
  MotionPath p=new MotionPath(100);
  for(int i=0;i<100;i++) p.x[i]=(i%2==0?8:-8);
  MotionPath c=p.corrections(10);
  for(int i=20;i<80;i++) assertTrue(Math.abs(p.x[i]+c.x[i])<1);
 }
 @Test public void steadyPanIsPreservedAwayFromEdges() {
  MotionPath p=new MotionPath(100);
  for(int i=0;i<100;i++) p.x[i]=i*2;
  MotionPath c=p.corrections(8);
  assertEquals(0,c.x[50],1e-8);
 }
 @Test public void interpolationAndClamping() {
  MotionPath p=new MotionPath(2); p.x[1]=10;
  assertEquals(5,p.at(0.5)[0],1e-9);
  assertEquals(10,p.at(100)[0],1e-9);
  assertEquals(0,p.at(-1)[0],1e-9);
 }
 @Test public void smoothingDoesNotCrossCameraCut() {
  MotionPath p=new MotionPath(60);p.cuts.set(30);
  for(int i=30;i<60;i++)p.x[i]=100;
  MotionPath c=p.corrections(24);
  for(double v:c.x)assertEquals(0,v,1e-8);
 }
 @Test public void interpolationDoesNotBlendDifferentScenes() {
  MotionPath p=new MotionPath(2);p.x[0]=5;p.x[1]=-20;p.cuts.set(1);
  assertEquals(5,p.at(.9)[0],1e-9);
  assertEquals(-20,p.at(1)[0],1e-9);
 }
}
