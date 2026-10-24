package br.com.luma.camera;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.Toast;

/** User-confirmed Android sharing; never calls the OpenAI API or sends in background. */
public final class ChatGptShare {
 public static final String PROMPT="Melhore esta fotografia com um resultado natural: equilibre a iluminação e as sombras, corrija dominantes de cor, reduza ruído e melhore a definição sem exagero. Preserve rigorosamente a identidade, os traços faciais, a textura natural da pele, o corpo, a expressão, o enquadramento e os elementos da cena. Não afine o rosto ou corpo, não aplique maquiagem ou embelezamento e não invente detalhes. Preserve áreas claras, evite pele esverdeada ou lavada e mantenha as cores realistas. Entregue a imagem editada.";
 public static void latest(Activity activity){
  SharedPreferences prefs=activity.getSharedPreferences("luma",0);
  String uri=prefs.getString("shareOriginal",prefs.getString("photoOriginal",null));
  if(uri==null){toast(activity,"Tire uma foto primeiro e aguarde o original ser salvo.");return;}
  show(activity,Uri.parse(uri));
 }
 public static void show(Activity activity,Uri original){
  try(android.os.ParcelFileDescriptor descriptor=activity.getContentResolver().openFileDescriptor(original,"r")){
   if(descriptor==null)throw new java.io.IOException("Imagem indisponível");
  }catch(Exception e){toast(activity,"Original indisponível. Tire uma nova foto ou restaure o arquivo na galeria.");return;}
  AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Enviar original ao ChatGPT")
   .setMessage("Você escolherá o ChatGPT na tela de compartilhamento do Android e confirmará o pedido lá. A edição e o salvamento do resultado acontecem no ChatGPT, dentro dos limites da sua conta. Não usa a API paga.\n\nA foto original e este pedido serão compartilhados:\n\n"+PROMPT+"\n\nSe o ChatGPT não preencher o texto, use Copiar pedido e cole na conversa. O resultado não retorna automaticamente ao Langer Câmera.")
   .setPositiveButton("Compartilhar",(d,w)->{
    Intent intent=new Intent(Intent.ACTION_SEND).setType("image/jpeg")
     .putExtra(Intent.EXTRA_STREAM,original).putExtra(Intent.EXTRA_TEXT,PROMPT)
     .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newUri(activity.getContentResolver(),"Foto original",original));
    try{activity.startActivity(Intent.createChooser(intent,"Escolha o ChatGPT").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
    catch(ActivityNotFoundException e){toast(activity,"Nenhum aplicativo disponível para receber a foto.");}
   }).setNeutralButton("Copiar pedido",null).setNegativeButton("Cancelar",null).create();
  dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
   ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pedido de edição",PROMPT));toast(activity,"Pedido copiado. Cole na conversa do ChatGPT, se necessário.");
  }));dialog.show();
 }
 private static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
