package br.com.luma.camera;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.widget.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;

/** User-confirmed Android sharing; never calls the OpenAI API or sends in background. */
public final class ChatGptShare {
 private static final String CHATGPT_PACKAGE="com.openai.chatgpt";
 public static final String PROMPT="Melhore esta fotografia com um resultado natural: equilibre a iluminação e as sombras, corrija dominantes de cor, reduza ruído e melhore a definição sem exagero. Preserve rigorosamente a identidade, os traços faciais, a textura natural da pele, o corpo, a expressão, o enquadramento e os elementos da cena. Não afine o rosto ou corpo, não aplique maquiagem ou embelezamento e não invente detalhes. Preserve áreas claras, evite pele esverdeada ou lavada e mantenha as cores realistas. Entregue a imagem editada.";
 public static void latest(Activity activity){
  SharedPreferences prefs=activity.getSharedPreferences("luma",0);
  String uri=prefs.getString("photoTreated",null);
  if(uri==null){toast(activity,"Tire uma foto primeiro e aguarde o tratamento terminar.");return;}
  show(activity,Uri.parse(uri));
 }
 public static void show(Activity activity,Uri photo){
  try(android.os.ParcelFileDescriptor descriptor=activity.getContentResolver().openFileDescriptor(photo,"r")){
   if(descriptor==null)throw new java.io.IOException("Imagem indisponível");
  }catch(Exception e){toast(activity,"Foto indisponível. Tire uma nova foto ou restaure o arquivo na galeria.");return;}
  promptDialog(activity,photo);
 }
 public static void editPrompt(Activity activity){promptDialog(activity,null);}
 private static void promptDialog(Activity activity,Uri photo){
  SharedPreferences prefs=activity.getSharedPreferences("luma",0);
  int padding=(int)(20*activity.getResources().getDisplayMetrics().density);
  ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(padding,padding/2,padding,padding/2);scroll.addView(body);
  TextView explanation=new TextView(activity);explanation.setTextSize(13);
  explanation.setText(photo==null?"Personalize o pedido de edição. Ao salvar, ele será usado nos próximos envios neste aparelho.":"Edite o pedido abaixo. A foto tratada e esse texto serão enviados ao ChatGPT, onde você confirma a mensagem e salva o resultado. Não usa a API paga. O resultado não retorna automaticamente ao Langer Câmera.");body.addView(explanation);
  EditText editor=new EditText(activity);editor.setTextSize(15);editor.setGravity(Gravity.TOP|Gravity.START);editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);editor.setSingleLine(false);editor.setMinLines(5);editor.setMaxLines(8);editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});
  editor.setHint("Descreva como deseja melhorar a foto");editor.setContentDescription("Pedido de edição para o ChatGPT");editor.setText(prefs.getString("chatGptPrompt",PROMPT));body.addView(editor,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout actions=new LinearLayout(activity);body.addView(actions);
  Button restore=new Button(activity);restore.setAllCaps(false);restore.setText("Restaurar padrão");actions.addView(restore,new LinearLayout.LayoutParams(0,-2,1));restore.setOnClickListener(v->{editor.setText(PROMPT);editor.setSelection(0);});
  Button copy=new Button(activity);copy.setAllCaps(false);copy.setText("Copiar pedido");actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));copy.setOnClickListener(v->{String value=validated(editor);if(value==null)return;((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pedido de edição",value));toast(activity,"Pedido copiado. Cole no ChatGPT se o texto não aparecer.");});
  AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(photo==null?"Editar pedido ao ChatGPT":"Enviar foto ao ChatGPT").setView(scroll).setPositiveButton(photo==null?"Salvar pedido":"Abrir ChatGPT",null).setNegativeButton("Cancelar",null);
  if(photo!=null)builder.setNeutralButton("Salvar pedido",null);
  AlertDialog dialog=builder.create();
  dialog.setOnShowListener(d->{
   dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
   if(photo!=null)dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String value=validated(editor);if(value==null)return;prefs.edit().putString("chatGptPrompt",value).apply();toast(activity,"Pedido salvo para os próximos envios.");dialog.dismiss();});
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
    String value=validated(editor);if(value==null)return;prefs.edit().putString("chatGptPrompt",value).apply();dialog.dismiss();
    if(photo==null){toast(activity,"Pedido salvo para os próximos envios.");return;}
    Intent intent=new Intent(Intent.ACTION_SEND).setType("image/jpeg")
     .putExtra(Intent.EXTRA_STREAM,photo).putExtra(Intent.EXTRA_TEXT,value)
     .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.setClipData(ClipData.newUri(activity.getContentResolver(),"Foto tratada",photo));
    try{activity.startActivity(new Intent(intent).setPackage(CHATGPT_PACKAGE));}
    catch(ActivityNotFoundException|SecurityException e){
     new AlertDialog.Builder(activity).setTitle("Não foi possível abrir o ChatGPT")
      .setMessage("O ChatGPT pode não estar instalado neste perfil ou esta versão não aceita o envio direto da imagem. Você pode escolher um aplicativo manualmente.")
      .setPositiveButton("Escolher aplicativo",(fallback,which)->{
       try{activity.startActivity(Intent.createChooser(intent,"Compartilhar foto tratada").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
       catch(ActivityNotFoundException|SecurityException unavailable){toast(activity,"Nenhum aplicativo disponível para receber a foto.");}
      }).setNegativeButton("Cancelar",null).show();
    }
   });
  });dialog.show();
 }
 private static String validated(EditText editor){String value=editor.getText().toString().trim();if(value.isEmpty()){editor.setError("Escreva o pedido ou restaure o padrão.");editor.requestFocus();return null;}return value;}
 private static void toast(Activity a,String s){Toast.makeText(a,s,Toast.LENGTH_LONG).show();}
}
