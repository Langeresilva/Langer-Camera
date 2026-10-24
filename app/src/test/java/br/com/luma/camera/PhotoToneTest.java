package br.com.luma.camera;
import org.junit.Test;
import static org.junit.Assert.*;
public class PhotoToneTest {
 @Test public void skinColorRatiosArePreserved(){
  int r=170,g=125,b=108,out=PhotoTone.apply(0xff000000|(r<<16)|(g<<8)|b,.55);
  assertEquals((double)r/g,(double)((out>>>16)&255)/((out>>>8)&255),.015);
  assertEquals((double)b/g,(double)(out&255)/((out>>>8)&255),.015);
 }
 @Test public void neutralPixelsStayNeutral(){for(int v=0;v<256;v++){int out=PhotoTone.apply(0xff000000|(v<<16)|(v<<8)|v,.55);assertEquals((out>>>16)&255,(out>>>8)&255);assertEquals(out&255,(out>>>8)&255);}}
 @Test public void brightSkinIsNotLifted(){assertEquals(0xffe0c5b8,PhotoTone.apply(0xffe0c5b8,.55));}
 @Test public void wellExposedSceneRemainsUnchanged(){assertEquals(0xffa47763,PhotoTone.apply(0xffa47763,PhotoTone.strength(160)));}
 @Test public void shadowsReceiveBoundedLift(){int out=PhotoTone.apply(0xff303030,.55);int v=out&255;assertTrue(v>48);assertTrue(v<=54);}
 @Test public void saturatedChannelNeverClipsOtherChannels(){assertEquals(0xffff4010,PhotoTone.apply(0xffff4010,.55));}
}
