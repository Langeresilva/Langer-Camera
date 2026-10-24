# Canal oficial do Langer Câmera

Repositório: https://github.com/Langeresilva/Langer-Camera

Canal configurado por padrão no APK 0.1.4:

`https://github.com/Langeresilva/Langer-Camera/releases/latest/download/latest.json`

O canal só responde depois da publicação de uma GitHub Release com o asset `latest.json`. A versão 0.1.3 pode usar o mesmo canal se o endereço for preenchido na tela Atualizações; não precisa reinstalar o APK completo para receber a 0.1.4.

## Estado da preparação

- APK 0.1.4 (versionCode 5) compilado e verificações concluídas.
- Pacote diferencial 0.1.3 → 0.1.4: 470.010 bytes, contra 40.971.892 bytes do APK completo.
- APK reconstruído com o leitor Java: hash idêntico e assinatura verificada.
- Assets preparados localmente em `entrega/release-v0.1.4/`.
- Acesso de escrita confirmado para `futebolassertivo` após aceitar o convite autorizado pelo proprietário.

A release e o código são publicados pelo procedimento abaixo. A chave de assinatura permanece fora do repositório e precisa ser preservada para atualizações compatíveis.

## Publicar após liberar acesso

Revisar o código e os arquivos selecionados para o primeiro commit. Não incluir `.tools/`, `entrega/`, `local.properties`, chaves ou credenciais. Enviar o commit para o repositório e então executar:

```powershell
python tools/publish_release.py entrega/release-v0.1.4 --commit HASH_DO_COMMIT_PUBLICADO
```

O publicador verifica permissão, cria uma release em rascunho, envia os arquivos e só então a torna pública. Os binários são assets da release, não arquivos do histórico Git. O script usa a credencial do Git Credential Manager em memória, sem gravá-la ou imprimi-la.

Após publicar, conferir o manifesto HTTPS e os downloads do APK e delta. Só então considerar o canal operacional. A instalação pelo atualizador no Redmi ainda exige validação física.
