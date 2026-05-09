# Thermal Printer SDK (Android)

Biblioteca Android (**AAR**) com dois drivers de impressão térmica:

| Driver | Uso típico |
|--------|------------|
| **USB ESC/POS genérico** | Impressoras térmicas USB em modo compatível ESC/POS (bulk OUT). |
| **Epson ePOS2** | Impressoras Epson via SDK oficial (`com.epson.epos2`), USB ou rede (`TCP:`). |

Pacote Java base: `io.github.aismor.thermalprintersdk`.

---

## Requisitos

- **JDK 11** (para compilar o projeto do SDK).
- **Android SDK** instalado (variável `ANDROID_HOME` ou ficheiro `local.properties` na raiz deste repositório com `sdk.dir=/caminho/para/Android/sdk`).
- App consumidor: **minSdk 21** ou superior (igual ao projeto da biblioteca na raiz).

---

## Compilar o AAR

Na raiz do repositório (o projeto Gradle **root** é a própria biblioteca):

```bash
./gradlew assembleRelease
```

O artefato fica em:

`build/outputs/aar/thermal-printer-sdk-release.aar`

Para desenvolvimento pode usar também `assembleDebug` (ficheiro `*-debug.aar`).

---

## Incluir no teu projeto Android

### 1. Copiar o AAR

Coloca o `.aar` na pasta do app, por exemplo `app/libs/`.

### 2. Dependências no `build.gradle` do módulo app

```kotlin
dependencies {
    implementation(files("libs/thermal-printer-sdk-release.aar"))
    implementation("androidx.annotation:annotation:1.7.1")
}
```

Num **multi-módulo** onde a biblioteca é o projeto **Gradle raiz** deste clone, o consumidor usa `implementation(project(":"))` (é o que faz `local-test-apk`). Noutro mono-repo podes publicar o AAR ou incluir o projeto com outro nome em `settings.gradle.kts`.

---

## Manifest do aplicativo

O AAR declara `android.hardware.usb.host` como opcional; no **teu** app convém garantir:

- Permissão explícita USB no Android 13+ quando aplicável (`android.permission.USB_PERMISSION` — uso habitual é pedir à runtime com `UsbManager.requestPermission`).
- Um **`BroadcastReceiver`** registado para a mesma **action** que o SDK usa ao pedir permissão:

```text
io.github.aismor.thermalprintersdk.USB_PERMISSION
```

No receiver, após `ACTION_USB_PERMISSION` com `EXTRA_PERMISSION_GRANTED`, podes chamar `driver.connect()` de novo.

Sem esse alinhamento entre intent da permissão e o receiver, o fluxo USB pode ficar bloqueado em “permissão pendente”.

---

## API rápida (`ThermalPrinterSdk`)

| Método | Descrição |
|--------|-----------|
| `getVersion()` | Versão do artefato (via `BuildConfig.VERSION_NAME`). |
| `createPrinterManager()` | Devolve um `PrinterManager` para configurar um `PrinterDriver` e ligar/desligar. |
| `usbEscPosGeneric(Context)` | Driver ESC/POS com **VID/PID predefinidos** (`0x0FE6` / `0x811E`). Ajusta se a tua impressora for outra. |
| `usbEscPosGeneric(Context, vendorId, productId)` | Mesmo driver com VID/PID explícitos (hex em decimal). |
| `epson(Context)` | Epson com configuração por omissão (`EpsonPrinterConfig.defaults()`). |
| `epson(Context, EpsonPrinterConfig)` | Epson com alvo e série configuráveis. |

Contratos principais:

- `PrinterDriver`: `requestPermission(Activity)`, `connect()`, `disconnect()`, `printText`, `printQrCode`, `printTest`, `feedLines`, `cut`, `openCashDrawer`, estado em `PrinterStatus` / mensagens de erro.
- `PrinterManager`: `setDriver`, `connectAll`, `disconnectAll`, `isReady`, etc.

---

## Exemplo: só ESC/POS USB

```java
UsbEscPosPrinterDriver driver = ThermalPrinterSdk.usbEscPosGeneric(context);

driver.requestPermission(activity);
PrinterStatus st = driver.connect();
if (st == PrinterStatus.PERMISSION_REQUIRED) {
    return;
}
if (st != PrinterStatus.CONNECTED) {
    return;
}

driver.printText("Olá\n");
driver.cut();
driver.disconnect();
```

---

## Exemplo: `PrinterManager` com um driver

```java
PrinterManager manager = ThermalPrinterSdk.createPrinterManager();
PrinterDriver driver = ThermalPrinterSdk.usbEscPosGeneric(context);
manager.setDriver(driver);

PrinterStatus st = manager.connectAll();
if (manager.isReady()) {
    driver.printTest();
}
manager.disconnectAll();
```

---

## Exemplo: Epson (USB ou rede)

Configuração explícita:

```java
EpsonPrinterConfig cfg = EpsonPrinterConfig.builder()
        .target("TCP:192.168.0.10")
        .seriesKey("TM_T88")
        .build();

EpsonPrinterDriver driver = ThermalPrinterSdk.epson(context, cfg);
PrinterStatus st = driver.connect();
```

- **`target`**: por exemplo `TCP:192.168.0.10`, ou deixa vazio para tentar deteção USB Epson (`USB:` internamente quando há dispositivo).
- **`seriesKey`**: constante da API Epson (ex.: `TM_T88`, `TM_T20X`). Usa `AUTO` para tentativa automática com base no nome USB (comportamento já implementado no driver).

### Dependências nativas Epson

O driver Epson usa reflexão sobre `com.epson.epos2`. É necessário:

1. Incluir no app as **bibliotecas e classes do SDK Epson ePOS** (conforme a licença Epson).
2. Copiar **`libepos2.so`** para `app/src/main/jniLibs` nas ABIs que suportas (**armeabi-v7a** e **arm64-v8a** são os casos mais comuns).

Sem as `.so` corretas, `connect()` pode falhar com erro de classe nativa (`UnsatisfiedLinkError`); o método utilitário `EpsonPrinterDriver.isNativeEpos2Unavailable()` indica falha de carregamento nativo.

---

## Logs

O SDK usa **`java.util.logging`** (JUL). Por defeito no Android essas linhas **não** aparecem no Logcat.

Na **app de teste** (`local-test-apk`) existe um encaminhamento JUL → Logcat (`JulToLogcat`), tags **`ThermalPrinterSdk`** (SDK) e **`ThermalTest`** (passos da UI). Erros nas operações de impressão são também capturados e mostrados no painel de texto da app.

Para analisar falhas (incl. Epson) no PC:

```bash
adb logcat -s ThermalTest:V ThermalPrinterSdk:V AndroidRuntime:E libc:F DEBUG:F
```

Craches **nativos** (`libepos2.so`) aparecem como tombstone (`DEBUG`, `libc`) — usa também `adb shell ls /data/tombstones` em builds debuggable ou o relatório em **Android Studio → Logcat** filtrando pelo pacote `io.github.aismor.thermalprintersdk.test`.

---

## APK de teste (módulo local `local-test-apk`)

O diretório `local-test-apk/` na raiz deste repositório **não é versionado** (está no `.gitignore`) e serve só para instalações em máquina de desenvolvimento.

Se existir `local-test-apk/build.gradle.kts`, o `settings.gradle.kts` inclui o módulo **`:local-test-apk`**, que depende da biblioteca na raiz via `implementation(project(":"))` e oferece um ecrã simples (ESC/POS USB e Epson) para validar o SDK.

As bibliotecas Epson de teste ficam **dentro** de `local-test-apk` (cópias locais, sem depender de outro repositório):

- `local-test-apk/libs/epson/` — JARs (`ePOS2.jar`, `ePOSEasySelect.jar`, …)
- `local-test-apk/src/main/jniLibs/` — `libepos2.so` e `libeposeasyselect.so` (`armeabi-v7a`, `arm64-v8a`)

Para atualizar versões do SDK Epson, substitui manualmente esses ficheiros (licença Epson).

```bash
./gradlew :local-test-apk:assembleDebug
```

O APK de debug fica em:

`local-test-apk/build/outputs/apk/debug/local-test-apk-debug.apk`

Quem clona o repositório sem essa pasta continua a conseguir compilar só o SDK; o módulo de teste é opcional.

---

## Licença

Define a licença do teu projeto aqui (MIT, Apache 2.0, proprietária, etc.). O driver Epson está sujeito aos termos do **Epson ePOS SDK**.
