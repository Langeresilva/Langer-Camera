# Langer Câmera — versão de teste 0.1.4

Aplicativo Android local, preparado para avaliação no Xiaomi Redmi Note 14 4G (24117RN76L), Android 16. Nome do pacote: `br.com.luma.camera`. Sem permissão de internet, conta, assinatura ou API paga.

## Instalar

1. Transfira `entrega/LangerCamera-0.1.4-arm64.apk` para o Redmi (USB, Quick Share ou outro método de sua preferência).
2. Abra o APK no gerenciador de arquivos. Se o Android pedir, permita a instalação por esse gerenciador.
3. Instale o **Langer Câmera** e permita câmera e microfone. Notificações permitem acompanhar e cancelar o tratamento.
4. Tire uma foto e aguarde a mensagem de conclusão. Depois grave inicialmente um vídeo curto, de 10 a 15 segundos, com **Estabilizar após gravar** ativado.
5. Os originais e os resultados aparecem em **Pictures/Langer** (fotos) e **Movies/Langer** (vídeos). O botão Galeria abre o último arquivo salvo.

O APK é uma compilação de desenvolvimento assinada, instalável diretamente; não é uma publicação na Play Store. Não requer root nem alterações no sistema. A chave de desenvolvimento usada pelo Gradle fica no perfil local, fora do repositório; preserve-a para instalar futuras versões por cima desta.

## Implementado

- Captura frontal e traseira com CameraX; toque no visor para foco/exposição.
- Foto com ajuste adaptativo de exposição, balanço de branco limitado e realce moderado de cor; mantém orientação EXIF.
- Gravação com áudio quando autorizado, preferência por Full HD e 30 fps. A resolução pode cair se o aparelho não liberar FHD para o aplicativo.
- Tratamento automático após finalizar: OpenCV rastreia pontos, estima deslocamento e rotação com rejeição de correspondências inconsistentes e suaviza a trajetória. Exportação H.264 com Media3 e preservação do áudio.
- Estabilização usa zoom de 1,25 (perda de aproximadamente 20% da largura/altura visível), limites de correção e interpolação entre amostras analisadas a 15 Hz. Não aplica a estabilização quando há poucas correspondências confiáveis.
- Correção global moderada de brilho e saturação do vídeo, sem variar o ajuste quadro a quadro.
- Original publicado antes do tratamento; notificações, cancelamento e recuperação de capturas privadas após falha/interrupção.

## Limites desta entrega

**Esta versão não contém um modelo neural de IA.** O tratamento fotográfico é computacional e adaptativo, e não equivale a uma edição generativa no ChatGPT. Também não implementa remoção avançada de ruído, reconstrução de detalhes, HDR de múltiplos quadros ou uso do giroscópio. O diagnóstico detecta o sensor, mas a estabilização desta versão depende exclusivamente das imagens.

Não foi validado fisicamente no Redmi do usuário. Estabilização forte durante corrida permanece experimental; pode falhar em cenas escuras, sem textura, com objetos próximos ou em movimento dominante, cortes, deslocamentos muito grandes e rolling shutter. Borrão de movimento não é removido. A correção é limitada para evitar bordas vazias; isso pode deixar tremor residual.

A câmera nativa pode acessar processamento privado do fabricante que este app não possui. Não há garantia de superar a câmera Xiaomi nem de atingir o Modo Ação do iPhone.

Vídeos longos exigem tempo, bateria e espaço para o original, a exportação temporária e o resultado. A tela mostra progresso; o serviço mantém o processamento em segundo plano, sujeito aos limites de energia do Android/HyperOS. Não guarda localização GPS. Para evitar perder capturas, confira **Recursos e recuperação → Recuperar capturas** depois de uma interrupção. O original permanece na galeria após cancelar um tratamento já iniciado.

## Roteiro de teste no Redmi

Compare original e resultado na mesma cena: foto interna, foto com janela clara, vídeo parado, caminhada e corrida curta à luz do dia. Confira áudio, orientação, nitidez, recorte e oscilação de cor. Teste também alternar câmeras e sair do app durante o processamento. A primeira avaliação deve usar vídeos curtos antes de gravações importantes.

## Compilar e verificar

JDK 17, Android SDK 35 / Build Tools 35, Gradle 8.13, AGP 8.10.1. Android mínimo 13; alvo Android 15, compatível para instalação no Android 16. O foco de validação é o Redmi citado.

```powershell
$env:JAVA_HOME = 'caminho-do-jdk-17'
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

Configure `sdk.dir` em `local.properties` para seu Android SDK. As ferramentas baixadas neste computador estão em `.tools/`, ignoradas pelo Git. O script `build.ps1` usa essas ferramentas locais.

Há testes JVM da suavização e um teste Android de ponta a ponta em `PipelineInstrumentation`: cria uma foto escura e um vídeo sintético tremido, executa o pipeline, mede o tremor antes/depois e verifica salvamento. O resultado efetivamente executado está em `entrega/VALIDACAO.md`.

## Componentes

CameraX 1.4.2, AndroidX Activity 1.10.1, ExifInterface 1.4.1, Media3 1.6.1 e OpenCV 4.12.0. Veja `THIRD_PARTY_NOTICES.md`. Nenhum código ou APK do CapCut foi incorporado.

## Atualização 0.1.4

Tratamento de fotos reforçado: curva de exposição adaptativa mais ampla, contraste suave e realce de cor moderado. Original e tratada agora compartilham o identificador de captura. O botão Comparar fotos abre o último par concluído dentro do app, respeitando orientação EXIF, sem abrir a galeria externa. Não há SDK publicitário, WebView nem permissão INTERNET no APK. A hospedagem temporária do download é um serviço externo com anúncios em sua página; o link direto do APK evita essa página.

Compilação, quatro testes JVM e Android Lint concluídos. Comparador e nova aparência ainda dependem de teste físico. O tratamento permanece convencional/adaptativo, sem modelo neural.


## Atualizador próprio — 0.1.4

Esta versão adiciona INTERNET e REQUEST_INSTALL_PACKAGES para consultar/baixar atualizações e abrir o instalador com confirmação. A captura e o tratamento continuam locais. Não há anúncios. O canal HTTPS é configurável: não foi inventado um servidor permanente, e nenhum endereço temporário foi embutido no app. Sem canal configurado, a verificação automática de versões não funciona. O atualizador também aceita pacotes .lgrdelta pelo seletor de arquivos.

O formato diferencial LGRD1 (gzip) copia trechos do APK instalado e grava trechos novos para reproduzir exatamente o APK assinado. Confere SHA-256 da base e do destino; valida pacote, versão superior e certificado igual ao instalado. Não instala em silêncio. Se não houver delta compatível, o canal pode oferecer o APK completo. Nenhuma foto ou vídeo é enviado.

Para publicar: hospede latest.json, APK e delta em HTTPS; configure o endereço no app. O exemplo em updates/latest.example.json é apenas um esquema, não uma versão publicada. Gere o delta com: python tools/make_delta.py base.apk nova.apk nova.lgrdelta. Use sempre o APK exato distribuído e preserve a chave de assinatura local. Não troque a chave entre versões.

Verificação 0.1.4: oito testes JVM aprovados e lint sem erros. Reconstrução real de 0.1.2 para 0.1.4 gerou APK byte a byte idêntico, com assinatura validada. APK: 40.955.508 bytes; delta de teste: 586.738 bytes. A versão 0.1.2 não contém o atualizador, então a instalação inicial da 0.1.4 exige o APK completo. O teste diferencial anterior é de bancada e não um pacote utilizável pela interface da 0.1.2. A instalação pelo atualizador no Redmi e a consulta a um canal de produção ainda não foram testadas.
