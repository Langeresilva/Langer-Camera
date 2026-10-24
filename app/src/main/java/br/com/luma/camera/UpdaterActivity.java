package br.com.luma.camera;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.*;

public class UpdaterActivity extends Activity {
 private static final String DEFAULT_CHANNEL="https://github.com/Langeresilva/Langer-Camera/releases/latest/download/latest.json";
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView status;private EditText endpoint;private Button check,install,local;
 private File ready;private boolean closed,busy;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,48,24,32);setContentView(root);
  root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(24,b.top+20,24,b.bottom+20);return insets;});
  TextView title=new TextView(this);title.setText("Atualizações · Langer Câmera");title.setTextSize(22);root.addView(title);
  TextView note=new TextView(this);note.setText("O atualizador usa internet somente ao consultar e baixar versões. Fotos e vídeos continuam locais. O Android pede confirmação para instalar.\n\nEndereço HTTPS do canal de atualização:");root.addView(note);
  endpoint=new EditText(this);endpoint.setSingleLine(true);endpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);endpoint.setHint(DEFAULT_CHANNEL);String saved=getSharedPreferences("luma",0).getString("updateEndpoint","");endpoint.setText(saved.isBlank()?DEFAULT_CHANNEL:saved);root.addView(endpoint);
  check=new Button(this);check.setText("Verificar atualização");root.addView(check);check.setOnClickListener(v->check());
  local=new Button(this);local.setText("Abrir pacote diferencial (.lgrdelta)");root.addView(local);local.setOnClickListener(v->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),72));
  install=new Button(this);install.setText("Instalar atualização verificada");install.setEnabled(false);root.addView(install);install.setOnClickListener(v->install());
  status=new TextView(this);status.setTextSize(15);status.setText("Canal oficial: Langeresilva/Langer-Camera no GitHub.\nAs versões precisam estar publicadas pelo desenvolvedor para aparecer aqui. Também é possível abrir um pacote diferencial recebido por arquivo.");root.addView(status,new LinearLayout.LayoutParams(-1,0,1));
  Button back=new Button(this);back.setText("Voltar");root.addView(back);back.setOnClickListener(v->finish());
 }
 private long installedVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).getLongVersionCode();}catch(Exception e){return 0;}}
 private void message(String text){runOnUiThread(()->{if(!closed)status.setText(text);});}
 private void lock(boolean value){busy=value;check.setEnabled(!value);local.setEnabled(!value);install.setEnabled(!value&&ready!=null);endpoint.setEnabled(!value);}
 private File folder(){File dir=new File(getCacheDir(),"updates");dir.mkdirs();return dir;}
 private static URL https(String address)throws Exception{URL u=new URL(address);if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||u.getHost().isEmpty())throw new IOException("Use um endereço HTTPS válido");return u;}
 private void download(String address,File out,long limit)throws Exception{
  URL url=https(address);
  for(int redirects=0;redirects<6;redirects++){
   HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
   try{
    int code=connection.getResponseCode();if(code>=300&&code<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Redirecionamento inválido");url=https(new URL(url,location).toString());continue;}
    if(code!=200)throw new IOException("Servidor respondeu HTTP "+code);
    long length=connection.getContentLengthLong();if(length>limit)throw new IOException("Arquivo acima do limite");
    try(InputStream in=connection.getInputStream();OutputStream stream=new FileOutputStream(out)){
     byte[] buffer=new byte[65536];int n;long total=0;int last=-1;
     while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelado");total+=n;if(total>limit)throw new IOException("Arquivo acima do limite");stream.write(buffer,0,n);int percent=length>0?(int)(total*100/length):-1;if(limit>65536&&percent!=last){last=percent;message("Baixando atualização · "+(percent>=0?percent+"%":total/1024+" KB"));}}
     if(total==0)throw new IOException("Arquivo vazio");
    }return;
   }finally{connection.disconnect();}
  }throw new IOException("Excesso de redirecionamentos");
 }
 private void check(){
  if(busy)return;String url=endpoint.getText().toString().trim();
  try{https(url);}catch(Exception e){message("Ainda falta configurar o endereço permanente do canal de atualizações.");return;}
  getSharedPreferences("luma",0).edit().putString("updateEndpoint",url).apply();lock(true);message("Consultando versões…");
  worker.execute(()->{try{
   File manifest=new File(folder(),"latest.json");download(url,manifest,65536);
   JSONObject data=new JSONObject(new String(java.nio.file.Files.readAllBytes(manifest.toPath()),java.nio.charset.StandardCharsets.UTF_8));
   if(!getPackageName().equals(data.getString("packageName")))throw new IOException("Canal de outro aplicativo");
   long version=data.getLong("versionCode");if(version<=installedVersion()){message("Você já está na versão mais recente deste canal.");unlock();return;}
   String name=data.getString("versionName"),hash=data.getString("sha256");if(!hash.matches("[a-fA-F0-9]{64}"))throw new IOException("Hash inválido");
   JSONObject delta=data.optJSONObject("delta");boolean useDelta=false;
   if(delta!=null&&delta.optLong("fromVersionCode",-1)==installedVersion())useDelta=DeltaPatch.sha256(new File(getApplicationInfo().sourceDir)).equalsIgnoreCase(delta.optString("baseSha256"));
   final boolean differential=useDelta;
   runOnUiThread(()->{if(closed)return;new AlertDialog.Builder(this).setTitle("Versão "+name).setMessage(data.optString("notes","")+"\n\n"+(differential?"Será baixado somente o pacote de diferenças.":"Esta versão exige o APK completo; não há pacote diferencial compatível.")).setPositiveButton("Baixar",(d,w)->worker.execute(()->fetch(data,differential))).setNegativeButton("Agora não",(d,w)->lock(false)).setOnCancelListener(d->lock(false)).show();});
  }catch(Exception e){failure(e);}});
 }
 private void fetch(JSONObject data,boolean differential){try{
  File apk=new File(folder(),"update.apk");
  if(differential){File patch=new File(folder(),"update.lgrdelta");download(data.getJSONObject("delta").getString("url"),patch,DeltaPatch.LIMIT);message("Reconstruindo atualização…");DeltaPatch.apply(new File(getApplicationInfo().sourceDir),patch,apk);patch.delete();}
  else download(data.getString("apkUrl"),apk,DeltaPatch.LIMIT);
  if(!DeltaPatch.sha256(apk).equalsIgnoreCase(data.getString("sha256")))throw new IOException("Integridade do download inválida");
  verify(apk,data.getLong("versionCode"));ready=apk;message("Atualização verificada. Toque em Instalar para abrir a confirmação do Android.");unlock();
 }catch(Exception e){failure(e);}}
 private void verify(File apk,long expected)throws Exception{
  PackageManager pm=getPackageManager();PackageInfo next=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES),current=pm.getPackageInfo(getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
  if(next==null||!getPackageName().equals(next.packageName)||next.getLongVersionCode()<=current.getLongVersionCode()||(expected>0&&next.getLongVersionCode()!=expected))throw new IOException("APK não é uma atualização válida deste app");
  if(next.signingInfo==null||current.signingInfo==null)throw new IOException("APK sem assinatura verificável");
  android.content.pm.Signature[] a=next.signingInfo.getApkContentsSigners(),b=current.signingInfo.getApkContentsSigners();
  if(a.length!=b.length||a.length==0)throw new IOException("Assinatura diferente");for(int i=0;i<a.length;i++)if(!Arrays.equals(a[i].toByteArray(),b[i].toByteArray()))throw new IOException("Assinatura diferente da versão instalada");
 }
 private void install(){try{
  if(ready==null)return;verify(ready,0);
  if(!getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(this).setTitle("Autorizar atualização").setMessage("Na próxima tela, permita que o Langer Câmera instale suas atualizações. Depois volte e toque em Instalar.").setPositiveButton("Abrir configuração",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())))).setNegativeButton("Cancelar",null).show();return;}
  Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updates",ready);
  startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
 }catch(Exception e){failure(e);}}
 private void unlock(){runOnUiThread(()->{if(!closed)lock(false);});}
 private void failure(Exception e){ready=null;new File(folder(),"update.apk").delete();message("Não foi possível atualizar: "+e.getMessage());unlock();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=72||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();lock(true);worker.execute(()->{try{
  File patch=new File(folder(),"local.lgrdelta"),apk=new File(folder(),"update.apk");try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(patch)){if(in==null)throw new IOException("Arquivo indisponível");byte[] buf=new byte[65536];long size=0;int n;while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Cancelado");size+=n;if(size>DeltaPatch.LIMIT)throw new IOException("Pacote excessivo");out.write(buf,0,n);}}
  message("Reconstruindo atualização…");DeltaPatch.apply(new File(getApplicationInfo().sourceDir),patch,apk);verify(apk,0);patch.delete();ready=apk;message("Pacote diferencial aplicado e assinatura verificada. Toque em Instalar.");unlock();
 }catch(Exception e){failure(e);}});}
 @Override protected void onDestroy(){closed=true;worker.shutdownNow();super.onDestroy();}
}
