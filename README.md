# Controle de Notas

Aplicativo Android para pequenas empresas registrarem notas fiscais (contas)
com foto, categoria e valor, tudo **armazenado localmente** no aparelho, sem
custo de nuvem. Os dados podem ser exportados para **CSV** para auditoria pelo
contador.

## Funcionalidades

- Tirar foto da nota fiscal (armazenada no espaço interno do app).
- Selecionar a categoria da despesa:
  - Água
  - Luz
  - Alimentação
  - Despesas médicas
  - Cursos e treinamentos
- Informar o valor (R$) da nota.
- Campo opcional de descrição.
- Listagem de todas as notas com miniatura, categoria, valor e data.
- Excluir notas.
- Exportar todas as notas para **CSV** (separador `;`, decimal `,`, UTF-8 com
  BOM — abre corretamente no Excel em português) e compartilhar por e-mail,
  WhatsApp, Google Drive, etc.

## Tecnologias (100% gratuitas e open source)

- **Kotlin**
- **Jetpack Compose** (interface)
- **Room** (banco de dados local SQLite)
- **Coil** (carregamento de imagens)
- **Navigation Compose**
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
- Ao desinstalar o app, os dados são removidos. Faça a exportação em CSV
  periodicamente para backup/auditoria.
