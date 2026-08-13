# Controle de Notas

Aplicativo Android para pequenas empresas registrarem notas fiscais (contas)
com foto ou PDF, categoria e valor, tudo **armazenado localmente** no aparelho,
sem custo de nuvem. Os dados podem ser exportados para **CSV** para auditoria
pelo contador.

## Funcionalidades

- Tirar foto da nota fiscal (armazenada no espaço interno do app).
- **Anexar a nota em PDF**, escolhendo o arquivo dentro do app ou abrindo o PDF
  em outro aplicativo e escolhendo "Controle de Notas" em *Abrir com* /
  *Compartilhar*. O app **lê o texto do PDF** e preenche automaticamente data,
  valor, chave de acesso, categoria e descrição (basta conferir e salvar).
- Selecionar a categoria da despesa:
  - Água
  - Luz
  - Internet
  - Alimentação
  - Despesas médicas
  - Cursos e treinamentos
- Informar o valor (R$) da nota.
- Campo opcional de descrição.
- Ler o QR Code / código de barras da nota (chave de acesso).
- Listagem das notas **da mais recente para a mais antiga**, com **rolagem
  infinita** de 8 notas por página. A lista não carrega as imagens, para ficar
  rápida mesmo com muitas notas.
- **Filtro por ano e por mês** (o mês é opcional: dá para ver o ano inteiro ou
  apenas um mês).
- Editar e excluir notas.
- Exportar as notas do período filtrado **em segundo plano**, com barra de
  progresso na tela e **notificação quando termina — mesmo com o app fechado**.
  Basta tocar na notificação para compartilhar por e-mail, WhatsApp, Drive etc.
  - **CSV** (separador `;`, decimal `,`) para o contador.
  - **CSV + anexos**: pacote `.zip` com o CSV, um relatório HTML com as notas em
    miniatura (inclusive a primeira página dos PDFs) e a pasta `anexos/` com os
    arquivos originais. O relatório é gravado direto dentro do `.zip`, uma nota
    por vez, e usa miniaturas — montar o HTML inteiro em memória com as fotos em
    tamanho original estourava a memória do aparelho.

## Tecnologias (100% gratuitas e open source)

- **Kotlin**
- **Jetpack Compose** (interface)
- **Room** (banco de dados local SQLite)
- **Coil** (carregamento de imagens)
- **Navigation Compose**
- **WorkManager** (exportação em segundo plano com notificação)
- **PDFBox-Android** (leitura do texto das notas em PDF, licença Apache 2.0)
- Câmera via `ACTION_IMAGE_CAPTURE` + `FileProvider` (sem SDKs pagos)

O banco de dados é local (SQLite via Room). Nenhum serviço de nuvem é utilizado.

## Como compilar e executar

### Opção A — Android Studio (recomendado)

1. Instale o [Android Studio](https://developer.android.com/studio) (gratuito).
2. Abra a pasta `ControleNotas` em **File > Open**.
3. Aguarde o Gradle sincronizar (o Android Studio baixa automaticamente o
   Gradle Wrapper e as dependências).
4. Conecte um celular Android (com depuração USB) ou crie um emulador.
5. Clique em **Run ▶**.

### Opção B — Linha de comando

Se você já tem o Gradle instalado, gere o wrapper e compile:

```powershell
cd ControleNotas
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

O APK gerado ficará em `app/build/outputs/apk/debug/app-debug.apk`.

## Requisitos

- Android 7.0 (API 24) ou superior.
- Câmera no dispositivo.

## Onde ficam os dados

- Banco de dados: `controle_notas.db` (armazenamento interno do app).
- Fotos: pasta `images/` no armazenamento interno do app.
- PDFs: pasta `anexos/` no armazenamento interno do app.
- Ao desinstalar o app, os dados são removidos. Faça a exportação em CSV
  periodicamente para backup/auditoria.
