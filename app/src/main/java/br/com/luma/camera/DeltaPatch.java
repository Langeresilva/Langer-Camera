package br.com.luma.camera;
import java.io.*;
import java.security.*;
import java.util.zip.GZIPInputStream;

/** Reconstructs the exact signed APK using bounded copy/data instructions. */
public final class DeltaPatch {
 public static final long LIMIT=250L*1024*1024;
 public static String sha256(File file)throws IOException {
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return hex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IOException(e);}
 }
 private static String hex(byte[] a){StringBuilder out=new StringBuilder();for(byte b:a)out.append(String.format(java.util.Locale.US,"%02x",b&255));return out.toString();}
 public static void apply(File base,File patch,File output)throws IOException{
  try(RandomAccessFile old=new RandomAccessFile(base,"r");DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream(patch)));OutputStream out=new FileOutputStream(output)){
   byte[] magic=new byte[5];in.readFully(magic);if(!new String(magic,java.nio.charset.StandardCharsets.US_ASCII).equals("LGRD1"))throw new IOException("Pacote diferencial inválido");
   byte[] baseHash=new byte[32],targetHash=new byte[32];in.readFully(baseHash);in.readFully(targetHash);
   if(!sha256(base).equals(hex(baseHash)))throw new IOException("Pacote não corresponde à versão instalada");
   long expected=in.readLong(),written=0;if(expected<=0||expected>LIMIT)throw new IOException("Tamanho inválido");
   byte[] buffer=new byte[65536];int commands=0;
   while(true){
    if(Thread.currentThread().isInterrupted())throw new IOException("Cancelado");
    int op=in.readUnsignedByte();if(op==255)break;if(++commands>100000)throw new IOException("Pacote excessivo");
    long offset=op==0?in.readLong():0;int length=in.readInt();
    if((op!=0&&op!=1)||length<=0||length>expected-written)throw new IOException("Instrução inválida");
    if(op==0){if(offset<0||offset>old.length()-length)throw new IOException("Referência inválida");old.seek(offset);}
    int remaining=length;while(remaining>0){int n=Math.min(remaining,buffer.length);if(op==0)old.readFully(buffer,0,n);else in.readFully(buffer,0,n);out.write(buffer,0,n);remaining-=n;}written+=length;
   }
   if(written!=expected||in.read()!=-1)throw new IOException("Pacote incompleto ou dados extras");out.flush();
   if(!sha256(output).equals(hex(targetHash)))throw new IOException("Integridade do APK reconstruído inválida");
  }catch(IOException e){output.delete();throw e;}
 }
}
