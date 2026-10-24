package br.com.luma.camera;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.zip.GZIPOutputStream;

public class DeltaPatchTest {
 private File file(byte[] data)throws Exception{File f=File.createTempFile("langer-test-",".bin");f.deleteOnExit();Files.write(f.toPath(),data);return f;}
 private File patch(byte[] base,byte[] target,boolean corrupt)throws Exception{
  File f=file(new byte[0]);try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)))){
   out.writeBytes("LGRD1");out.write(MessageDigest.getInstance("SHA-256").digest(base));byte[] hash=MessageDigest.getInstance("SHA-256").digest(target);if(corrupt)hash[0]^=1;out.write(hash);out.writeLong(target.length);
   out.writeByte(0);out.writeLong(0);out.writeInt(3);out.writeByte(1);out.writeInt(target.length-3);out.write(target,3,target.length-3);out.writeByte(255);
  }return f;
 }
 @Test public void copyAndDataRebuildExactBytes()throws Exception{
  byte[] base="ABC-original".getBytes(),target="ABC-new-version".getBytes();File output=file(new byte[0]);DeltaPatch.apply(file(base),patch(base,target,false),output);assertArrayEquals(target,Files.readAllBytes(output.toPath()));
 }
 @Test public void rejectsDifferentInstalledApk()throws Exception{
  byte[] base="ABC-old".getBytes(),target="ABC-new".getBytes();File output=file(new byte[0]);assertThrows(IOException.class,()->DeltaPatch.apply(file("WRONG".getBytes()),patch(base,target,false),output));assertFalse(output.exists());
 }
 @Test public void rejectsCorruptedTargetHash()throws Exception{
  byte[] base="ABC-old".getBytes(),target="ABC-new".getBytes();File output=file(new byte[0]);assertThrows(IOException.class,()->DeltaPatch.apply(file(base),patch(base,target,true),output));assertFalse(output.exists());
 }
 @Test public void rejectsTruncatedPackage()throws Exception{
  byte[] base="ABC-old".getBytes(),target="ABC-new".getBytes();File p=patch(base,target,false);byte[] data=Files.readAllBytes(p.toPath());Files.write(p.toPath(),java.util.Arrays.copyOf(data,15));File output=file(new byte[0]);assertThrows(IOException.class,()->DeltaPatch.apply(file(base),p,output));assertFalse(output.exists());
 }
}
