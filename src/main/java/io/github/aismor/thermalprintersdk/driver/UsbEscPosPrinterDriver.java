package io.github.aismor.thermalprintersdk.driver;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import io.github.aismor.thermalprintersdk.api.PrinterDriver;
import io.github.aismor.thermalprintersdk.api.PrinterStatus;
import io.github.aismor.thermalprintersdk.connection.usb.UsbBulkPrinterConnection;
import io.github.aismor.thermalprintersdk.escpos.EscPosCommands;
import io.github.aismor.thermalprintersdk.escpos.EscPosQrEncoder;

public final class UsbEscPosPrinterDriver implements PrinterDriver {

    private static final Logger LOG = Logger.getLogger(UsbEscPosPrinterDriver.class.getName());

    private final UsbBulkPrinterConnection usb;

    private PrinterStatus lastStatus = PrinterStatus.DISCONNECTED;
    private String lastError;

    public UsbEscPosPrinterDriver(@NonNull Context context, int vendorId, int productId) {
        this.usb = new UsbBulkPrinterConnection(context, vendorId, productId);
    }

    public UsbEscPosPrinterDriver(@NonNull Context context) {
        this(context, 0x0FE6, 0x811E);
    }

    public int getUsbVendorId() {
        return usb.getVendorId();
    }

    @Override
    public void requestPermission(@NonNull Activity activity) {
        android.hardware.usb.UsbDevice d = usb.findTargetDevice();
        if (d == null) {
            lastError = usb.getLastErrorMessage();
            lastStatus = PrinterStatus.DEVICE_NOT_FOUND;
            LOG.severe(() -> "requestPermission: dispositivo ausente");
            return;
        }
        usb.requestPermission(activity, d);
    }

    @NonNull
    @Override
    public PrinterStatus connect() {
        PrinterStatus s = usb.connect();
        lastStatus = s;
        lastError = usb.getLastErrorMessage();
        LOG.info(() -> "connect → " + s + (lastError != null ? (" | " + lastError) : ""));
        return s;
    }

    @Override
    public void disconnect() {
        usb.disconnect();
        lastStatus = PrinterStatus.DISCONNECTED;
        LOG.info(() -> "disconnect");
    }

    @Override
    public boolean isConnected() {
        return usb.isConnected();
    }

    @Override
    public void printText(@Nullable String text) {
        if (!ensureReady()) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            buf.write(EscPosCommands.INIT);
            buf.write(EscPosCommands.align(0));
            buf.write(EscPosCommands.bold(false));
            buf.write(EscPosCommands.textToBytes(text != null ? text : "", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.lineFeed());
        } catch (IOException ignored) {
        }
        byte[] payload = buf.toByteArray();
        if (!usb.sendRaw(payload)) {
            failTransfer();
            return;
        }
        LOG.info(() -> "printText enviado bytes=" + payload.length);
    }

    @Override
    public void printQrCode(@NonNull String content) {
        if (!ensureReady()) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            buf.write(EscPosCommands.INIT);
            buf.write(EscPosCommands.align(1));
            buf.write(EscPosQrEncoder.buildQrCode(content));
            buf.write(EscPosCommands.lineFeed());
            buf.write(EscPosCommands.align(0));
        } catch (IOException ignored) {
        }
        if (!usb.sendRaw(buf.toByteArray())) {
            failTransfer();
        }
    }

    @Override
    public void printTest() {
        if (!ensureReady()) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            buf.write(EscPosCommands.INIT);
            buf.write(EscPosCommands.align(1));
            buf.write(EscPosCommands.bold(true));
            buf.write(EscPosCommands.textToBytes("TESTE ESC/POS USB\n", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.bold(false));
            buf.write(EscPosCommands.align(0));
            buf.write(EscPosCommands.textToBytes("Driver: UsbEscPosPrinterDriver\n", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.textToBytes("Linha normal\n", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.bold(true));
            buf.write(EscPosCommands.textToBytes("Negrito ON\n", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.bold(false));
            buf.write(EscPosCommands.textToBytes("Negrito OFF\n", EscPosCommands.DEFAULT_CHARSET));
            buf.write(EscPosCommands.feedLines(4));
            buf.write(EscPosCommands.CUT_PARTIAL_FEED);
        } catch (IOException ignored) {
        }
        if (!usb.sendRaw(buf.toByteArray())) {
            failTransfer();
        }
    }

    @Override
    public void feedLines(int lines) {
        if (!ensureReady()) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            buf.write(EscPosCommands.feedLines(lines));
        } catch (IOException ignored) {
        }
        if (!usb.sendRaw(buf.toByteArray())) {
            failTransfer();
        }
    }

    @Override
    public void cut() {
        if (!ensureReady()) {
            return;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            buf.write(EscPosCommands.feedLines(3));
            buf.write(EscPosCommands.CUT_PARTIAL_FEED);
        } catch (IOException ignored) {
        }
        if (!usb.sendRaw(buf.toByteArray())) {
            failTransfer();
        }
    }

    @Override
    public void openCashDrawer() {
        if (!ensureReady()) {
            return;
        }
        if (!usb.sendRaw(EscPosCommands.cashDrawerKick())) {
            failTransfer();
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

    private boolean ensureReady() {
        if (!usb.isConnected()) {
            lastError = "USB não conectado — chame connect()";
            lastStatus = PrinterStatus.DISCONNECTED;
            LOG.warning(() -> lastError);
            return false;
        }
        return true;
    }

    private void failTransfer() {
        lastError = usb.getLastErrorMessage();
        lastStatus = PrinterStatus.ERROR_TRANSFER;
        LOG.severe(() -> "Falha envio: " + lastError);
    }
}
