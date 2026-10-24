# Langer Câmera — 0.1.4

Câmera Android desenvolvida para testes no Redmi Note 14 4G (24117RN76L), Android 16. Pacote: `br.com.luma.camera`. Sem anúncios, contas ou APIs pagas. Fotos e vídeos são processados no próprio aparelho. A internet é usada pelo atualizador quando solicitado.

## Instalação e atualização

Versões oficiais: https://github.com/Langeresilva/Langer-Camera/releases

- Instalação inicial ou versões anteriores à 0.1.3: baixe o APK ARM64 e abra no Android. Permita câmera e microfone.
- Quem já instalou a 0.1.3 pode atualizar com o pacote `LangerCamera-0.1.3-para-0.1.4.lgrdelta`: abra o app, toque em Verificar atualização, depois Abrir pacote diferencial e selecione o arquivo. Toque em Instalar e confirme no Android.
- A partir da 0.1.4, o canal oficial já vem preenchido. Toque em Verificar atualização para consultar versões futuras. Somente versões publicadas aparecem.
- O Android precisa autorizar o Langer Câmera a solicitar instalação de atualizações. Não há instalação silenciosa.

Canal oficial: `https://github.com/Langeresilva/Langer-Camera/releases/latest/download/latest.json`

## Recursos

Captura frontal e traseira; toque para foco/exposição; fotos com tratamento adaptativo de iluminação, balanço de branco e cores; original e tratada preservadas na galeria com o mesmo identificador. O botão Comparar fotos alterna entre as duas versões da última captura concluída.

Vídeo com áudio quando autorizado, preferência por Full HD a 30 fps, estabilização após a gravação por rastreamento de pontos e suavização da trajetória. O tratamento analisa 15 amostras por segundo, limita deslocamento/rotação e usa zoom 1,25, equivalente a recortar cerca de 20% da largura e da altura visíveis. Se o rastreamento for insuficiente, exporta sem estabilizar. Correção moderada de brilho e cor consistente na sequência.

Os originais e resultados são salvos em Pictures/Langer e Movies/Langer. A notificação permite acompanhar ou cancelar o tratamento. Capturas privadas interrompidas podem ser recuperadas em Recursos e recuperação.

## Limites

O tratamento fotográfico é convencional/adaptativo: não há modelo neural, edição generativa equivalente ao ChatGPT, HDR multiquadro ou reconstrução avançada de detalhes. O giroscópio é detectado, mas não participa da estabilização desta versão.

A estabilização pode deixar tremor residual ou artefatos durante corrida, em baixa luz, com objetos próximos, cenas sem textura, cortes ou movimento dominante. Não remove borrão de movimento. O fabricante pode oferecer recursos privados não acessíveis ao app.

O usuário relatou estabilização aparentemente funcional na versão anterior. As alterações mais recentes e a instalação pelo atualizador ainda exigem validação no Redmi. Comece com vídeos curtos e confira os resultados antes de apagar os originais. Vídeos longos exigem tempo, bateria e espaço para cópias temporárias.

## Compilar

JDK 17, Android SDK/Build Tools 35, Gradle 8.13 e AGP 8.10.1. Android mínimo 13, alvo API 35, com instalação prevista no Android 16. Configure `sdk.dir` em `local.properties` e execute:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

`build.ps1` usa as ferramentas locais em `.tools/` e copia o APK para `entrega/`. Essas pastas são ignoradas pelo Git. A chave local de assinatura deve ser preservada para atualizações compatíveis; não é enviada ao repositório.

## Publicar atualizações

Gere o APK com a mesma chave. `tools/prepare_release.py` prepara o APK, o delta opcional, `latest.json` e hashes. `tools/publish_release.py` envia os assets para uma release inicialmente em rascunho e só a publica depois dos uploads. Detalhes em `updates/PUBLICACAO.md`.

O formato LGRD1 copia trechos do APK instalado e acrescenta os novos para reconstruir o APK assinado exato. Confere hash da base, hash do destino, pacote, versão superior e certificado. Sem um delta compatível, usa o APK completo. Nenhuma mídia é enviada.

## Validação

Oito testes JVM aprovados e Android Lint sem erros bloqueantes. APK e delta baixados novamente da release v0.1.4; hashes comparados e reconstrução com o leitor Java validada byte a byte, incluindo assinatura APK. O delta 0.1.3 para 0.1.4 tem 470.010 bytes; o APK tem 40.971.892 bytes.

O teste Android `PipelineInstrumentation` foi compilado, mas não executado: o emulador local não iniciou e o aparelho não está conectado. Não confundir testes JVM ou de bancada com validação completa dos codecs e instalador no celular.

## Componentes

CameraX 1.4.2, Activity 1.10.1, ExifInterface 1.4.1, Media3 1.6.1 e OpenCV 4.12.0. Avisos e licenças em THIRD_PARTY_NOTICES.md e nos assets do APK. Nenhum código ou APK de CapCut foi incorporado.
