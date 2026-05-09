package io.github.aismor.thermalprintersdk.driver;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Logger;

import io.github.aismor.thermalprintersdk.api.PrinterDriver;
import io.github.aismor.thermalprintersdk.api.PrinterStatus;
import io.github.aismor.thermalprintersdk.config.EpsonPrinterConfig;
import io.github.aismor.thermalprintersdk.connection.usb.UsbBulkPrinterConnection;

public final class EpsonPrinterDriver implements PrinterDriver {

    private static final Logger LOG = Logger.getLogger(EpsonPrinterDriver.class.getName());

    private static volatile boolean nativeEpos2LoadFailed;

    private static final int EPSON_USB_VENDOR_ID = 1208;
    private static final String AUTO_SERIES = "AUTO";
    private static final String DEFAULT_TARGET = "";
    private static final String DEFAULT_PRINTER_SERIES = "TM_T88";

    private final Context appContext;
    private final EpsonPrinterConfig config;
    private PrinterStatus lastStatus = PrinterStatus.DISCONNECTED;
    private String lastError;
    private Object printerObj;
    private boolean connected;

    public EpsonPrinterDriver(@NonNull Context context, @NonNull EpsonPrinterConfig config) {
        this.appContext = context.getApplicationContext();
        this.config = config;
    }

    public static boolean isNativeEpos2Unavailable() {
        return nativeEpos2LoadFailed;
    }

    private static void markNativeEpos2FailureIfNeeded(@Nullable Throwable t) {
        if (t == null || nativeEpos2LoadFailed) {
            return;
        }
        for (Throwable x = t; x != null; x = x.getCause()) {
            if (x instanceof UnsatisfiedLinkError) {
                nativeEpos2LoadFailed = true;
                LOG.warning(() -> "libepos2.so não encontrada no APK — incluir jniLibs ePOS ou usar ESC/POS USB.");
                return;
            }
        }
    }

    @Override
    public void requestPermission(@NonNull Activity activity) {
        UsbManager usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            LOG.warning(() -> "requestPermission: UsbManager indisponível");
            return;
        }
        UsbDevice device = findEpsonUsbDevice();
        if (device == null) {
            LOG.warning(() -> "requestPermission: nenhuma Epson USB enumerada — use target TCP ou USB");
            return;
        }
        if (usbManager.hasPermission(device)) {
            LOG.info(() -> "requestPermission: permissão USB Epson já concedida");
            return;
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                activity,
                0,
                new Intent(UsbBulkPrinterConnection.ACTION_USB_PERMISSION),
                flags);
        usbManager.requestPermission(device, pi);
        LOG.info(() -> "requestPermission: solicitada permissão USB para Epson");
    }

    @NonNull
    @Override
    public PrinterStatus connect() {
        try {
            ensurePrinterInstance();
            String target = resolveTarget();
            if (target.isEmpty()) {
                lastError = "Target Epson vazio (ex.: TCP:192.168.0.10 ou USB:)";
                lastStatus = PrinterStatus.ERROR_HARDWARE;
                return lastStatus;
            }
            PrinterStatus usbPerm = verificarPermissaoUsbSeNecessario(target);
            if (usbPerm != null) {
                lastStatus = usbPerm;
                connected = false;
                return usbPerm;
            }
            Method connect = printerObj.getClass().getMethod("connect", String.class, int.class);
            int paramDefault = getStaticIntField(printerObj.getClass(), "PARAM_DEFAULT", 0);
            connect.invoke(printerObj, target, paramDefault);
            connected = true;
            lastStatus = PrinterStatus.CONNECTED;
            lastError = null;
            LOG.info(() -> "connect Epson ok target=" + target);
            return lastStatus;
        } catch (ClassNotFoundException e) {
            connected = false;
            markNativeEpos2FailureIfNeeded(e);
            lastError = "SDK Epson ausente: classes com.epson.epos2 não encontradas";
            lastStatus = PrinterStatus.ERROR_HARDWARE;
            LOG.severe(() -> lastError + " " + e.getMessage());
            return lastStatus;
        } catch (Throwable t) {
            connected = false;
            markNativeEpos2FailureIfNeeded(t);
            lastError = "Falha connect Epson: " + t.getMessage();
            lastStatus = PrinterStatus.ERROR_HARDWARE;
            LOG.severe(() -> lastError);
            return lastStatus;
        }
    }

    @Override
    public void disconnect() {
        if (printerObj == null) {
            connected = false;
            lastStatus = PrinterStatus.DISCONNECTED;
            return;
        }
        try {
            Method clear = printerObj.getClass().getMethod("clearCommandBuffer");
            clear.invoke(printerObj);
        } catch (Throwable ignored) {
        }
        try {
            Method disconnect = printerObj.getClass().getMethod("disconnect");
            disconnect.invoke(printerObj);
        } catch (Throwable t) {
            LOG.warning(() -> "disconnect Epson com falha: " + t.getMessage());
        } finally {
            connected = false;
            lastStatus = PrinterStatus.DISCONNECTED;
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void printText(@Nullable String text) {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addTextLang = printerObj.getClass().getMethod("addTextLang", int.class);
            int langEn = getStaticIntField(printerObj.getClass(), "LANG_EN", 1);
            addTextLang.invoke(printerObj, langEn);

            Method addText = printerObj.getClass().getMethod("addText", String.class);
            addText.invoke(printerObj, text != null ? text : "");
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha printText Epson", t);
        }
    }

    @Override
    public void printQrCode(@NonNull String content) {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addTextLang = printerObj.getClass().getMethod("addTextLang", int.class);
            int langEn = getStaticIntField(printerObj.getClass(), "LANG_EN", 1);
            addTextLang.invoke(printerObj, langEn);

            Method addSymbol = printerObj.getClass().getMethod(
                    "addSymbol",
                    String.class, int.class, int.class, int.class, int.class, int.class);
            int symbolQrcodeModel2 = getStaticIntField(printerObj.getClass(), "SYMBOL_QRCODE_MODEL_2", 0);
            int levelM = getStaticIntField(printerObj.getClass(), "LEVEL_M", 0);
            int width = 8;
            int height = 8;
            addSymbol.invoke(printerObj, content, symbolQrcodeModel2, levelM, width, height, 0);
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha printQrCode Epson", t);
        }
    }

    @Override
    public void printTest() {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addTextLang = printerObj.getClass().getMethod("addTextLang", int.class);
            int langEn = getStaticIntField(printerObj.getClass(), "LANG_EN", 1);
            addTextLang.invoke(printerObj, langEn);
            Method addText = printerObj.getClass().getMethod("addText", String.class);
            addText.invoke(printerObj, "TESTE EPSON ePOS\n");
            addText.invoke(printerObj, "Driver: EpsonPrinterDriver\n");
            addText.invoke(printerObj, "USB/LAN\n");
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha printTest Epson", t);
        }
    }

    @Override
    public void feedLines(int lines) {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addFeedLine = printerObj.getClass().getMethod("addFeedLine", int.class);
            addFeedLine.invoke(printerObj, Math.max(0, lines));
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha feedLines Epson", t);
        }
    }

    @Override
    public void cut() {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addCut = printerObj.getClass().getMethod("addCut", int.class);
            int cutFeed = getStaticIntField(printerObj.getClass(), "CUT_FEED", 0);
            addCut.invoke(printerObj, cutFeed);
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha cut Epson", t);
        }
    }

    @Override
    public void openCashDrawer() {
        if (!ensureConnected()) {
            return;
        }
        try {
            Method addPulse = printerObj.getClass().getMethod("addPulse", int.class, int.class, int.class);
            int drawer2pin = getStaticIntField(printerObj.getClass(), "DRAWER_2PIN", 0);
            addPulse.invoke(printerObj, drawer2pin, 200, 200);
            sendDataAndClear();
        } catch (Throwable t) {
            fail("Falha openCashDrawer Epson", t);
        }
    }

    @Nullable
    @Override
    public PrinterStatus getLastStatus() {
        return lastStatus;
    }

    @Nullable
    @Override
    public String getLastErrorMessage() {
        return lastError;
    }

    private boolean ensureConnected() {
        if (connected) {
            return true;
        }
        PrinterStatus s = connect();
        boolean ok = s == PrinterStatus.CONNECTED;
        if (!ok) {
            LOG.warning(() -> "ensureConnected falhou status=" + s + " lastError=" + lastError);
        }
        return ok;
    }

    private void ensurePrinterInstance() throws Exception {
        if (printerObj != null) {
            return;
        }
        Class<?> printerClass = Class.forName("com.epson.epos2.printer.Printer");
        int series = resolveSeries(printerClass);
        int modelAnk = getStaticIntField(printerClass, "MODEL_ANK", 1);
        Constructor<?> ctor = printerClass.getConstructor(int.class, int.class, Context.class);
        printerObj = ctor.newInstance(series, modelAnk, appContext);
        LOG.info(() -> "ensurePrinterInstance OK seriesConst=" + series + " modelAnk=" + modelAnk);
    }

    private int resolveSeries(Class<?> printerClass) {
        String configured = config.getSeriesKey();
        if (configured.trim().isEmpty()) {
            configured = DEFAULT_PRINTER_SERIES;
        }
        String key = configured.trim().toUpperCase();
        if (AUTO_SERIES.equals(key)) {
            String autoSeries = detectSeriesFromUsb();
            if (autoSeries != null && !autoSeries.isEmpty()) {
                key = autoSeries;
            } else {
                key = DEFAULT_PRINTER_SERIES;
            }
        } else if (!key.startsWith("TM_")) {
            key = "TM_" + key;
        }
        return getStaticIntField(printerClass, key, getStaticIntField(printerClass, DEFAULT_PRINTER_SERIES, 0));
    }

    @Nullable
    private String detectSeriesFromUsb() {
        UsbDevice device = findEpsonUsbDevice();
        if (device == null) {
            return null;
        }
        String product = device.getProductName();
        if (product == null || product.trim().isEmpty()) {
            product = device.getDeviceName();
        }
        if (product == null) {
            return null;
        }
        String p = product.toUpperCase();
        if (p.contains("T20X")) {
            LOG.info(() -> "Série Epson auto detectada: TM_T20X");
            return "TM_T20X";
        }
        if (p.contains("T88")) {
            LOG.info(() -> "Série Epson auto detectada: TM_T88");
            return "TM_T88";
        }
        if (p.contains("T82X")) {
            LOG.info(() -> "Série Epson auto detectada: TM_T82X");
            return "TM_T82X";
        }
        if (p.contains("M30")) {
            LOG.info(() -> "Série Epson auto detectada: TM_M30");
            return "TM_M30";
        }
        LOG.info(() -> "Série Epson USB não reconhecida automaticamente: " + p);
        return null;
    }

    @Nullable
    private PrinterStatus verificarPermissaoUsbSeNecessario(@NonNull String target) {
        String t = target.trim().toUpperCase();
        if (!t.startsWith("USB")) {
            return null;
        }
        UsbManager um = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        if (um == null) {
            return null;
        }
        UsbDevice dev = findEpsonUsbDevice();
        if (dev == null) {
            return null;
        }
        if (!um.hasPermission(dev)) {
            lastError = "Permissão USB pendente para impressora Epson";
            LOG.warning(() -> lastError);
            return PrinterStatus.PERMISSION_REQUIRED;
        }
        return null;
    }

    @NonNull
    private String resolveTarget() {
        String prefTarget = config.getTarget();
        if (!prefTarget.trim().isEmpty()) {
            return normalizeTarget(prefTarget.trim());
        }
        UsbDevice epsonUsb = findEpsonUsbDevice();
        if (epsonUsb != null) {
            LOG.info(() -> "Target Epson: USB VID=0x" + String.format("%04x", epsonUsb.getVendorId())
                    + " PID=0x" + String.format("%04x", epsonUsb.getProductId()));
            return "USB:";
        }
        return DEFAULT_TARGET;
    }

    @NonNull
    private String normalizeTarget(@NonNull String target) {
        if (target.isEmpty()) {
            return target;
        }
        String up = target.toUpperCase();
        if (up.startsWith("TCP:") || up.startsWith("BT:")) {
            return target;
        }
        if ("USB".equals(up) || "USB_AUTO".equals(up) || up.startsWith("USB:")) {
            UsbDevice epsonUsb = findEpsonUsbDevice();
            if (epsonUsb != null) {
                LOG.info(() -> "Epson USB detectado VID=0x"
                        + String.format("%04x", epsonUsb.getVendorId())
                        + " PID=0x" + String.format("%04x", epsonUsb.getProductId()));
                return "USB:";
            }
            LOG.warning(() -> "Target USB configurado, mas nenhuma Epson USB foi detectada");
            return "USB:";
        }
        return target;
    }

    @Nullable
    private UsbDevice findEpsonUsbDevice() {
        UsbManager usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return null;
        }
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        for (UsbDevice device : devices.values()) {
            if (isEpsonDevice(device)) {
                return device;
            }
        }
        return null;
    }

    private boolean isEpsonDevice(@Nullable UsbDevice device) {
        if (device == null) {
            return false;
        }
        if (device.getVendorId() == EPSON_USB_VENDOR_ID) {
            return true;
        }
        String manufacturer = device.getManufacturerName();
        return manufacturer != null && manufacturer.toLowerCase().contains("epson");
    }

    private void sendDataAndClear() throws Exception {
        Method sendData = printerObj.getClass().getMethod("sendData", int.class);
        int paramDefault = getStaticIntField(printerObj.getClass(), "PARAM_DEFAULT", 0);
        sendData.invoke(printerObj, paramDefault);
        Method clear = printerObj.getClass().getMethod("clearCommandBuffer");
        clear.invoke(printerObj);
        LOG.info(() -> "sendData Epson concluído (buffer enviado)");
    }

    private int getStaticIntField(Class<?> clazz, String name, int fallback) {
        try {
            Field field = clazz.getField(name);
            return field.getInt(null);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void fail(String msg, Throwable t) {
        connected = false;
        lastStatus = PrinterStatus.ERROR_TRANSFER;
        lastError = msg + ": " + t.getMessage();
        LOG.severe(() -> lastError);
    }
}
