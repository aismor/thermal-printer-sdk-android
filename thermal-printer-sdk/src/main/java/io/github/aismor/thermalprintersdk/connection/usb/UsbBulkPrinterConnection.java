package io.github.aismor.thermalprintersdk.connection.usb;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.github.aismor.thermalprintersdk.api.PrinterConnection;
import io.github.aismor.thermalprintersdk.api.PrinterStatus;

public final class UsbBulkPrinterConnection implements PrinterConnection {

    public static final String ACTION_USB_PERMISSION = "io.github.aismor.thermalprintersdk.USB_PERMISSION";

    private static final Logger LOG = Logger.getLogger(UsbBulkPrinterConnection.class.getName());
    private static final int EPSON_VENDOR_ID = 1208;

    private final Context appContext;
    private final int vendorId;
    private final int productId;

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface claimedInterface;
    private UsbEndpoint bulkOut;
    private String lastError;

    public UsbBulkPrinterConnection(@NonNull Context context, int vendorId, int productId) {
        this.appContext = context.getApplicationContext();
        this.vendorId = vendorId;
        this.productId = productId;
        this.usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
    }

    public int getVendorId() {
        return vendorId;
    }

    public int getProductId() {
        return productId;
    }

    @Nullable
    public UsbDevice findTargetDevice() {
        if (usbManager == null) {
            lastError = "UsbManager indisponível";
            return null;
        }
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        if (list == null || list.isEmpty()) {
            lastError = "Nenhum dispositivo USB enumerado";
            LOG.warning(lastError);
            return null;
        }
        UsbDevice matchByVendor = null;
        UsbDevice epsonFallback = null;
        for (UsbDevice d : list.values()) {
            if (d.getVendorId() == vendorId && d.getProductId() == productId) {
                LOG.info("Dispositivo encontrado: " + d.getDeviceName()
                        + " VID=" + d.getVendorId() + " PID=" + d.getProductId());
                return d;
            }
            if (d.getVendorId() == vendorId && matchByVendor == null) {
                matchByVendor = d;
            }
            if (d.getVendorId() == EPSON_VENDOR_ID && epsonFallback == null) {
                epsonFallback = d;
            }
        }
        if (matchByVendor != null) {
            final UsbDevice mv = matchByVendor;
            LOG.info("Dispositivo encontrado por VID: " + mv.getDeviceName()
                    + " VID=" + mv.getVendorId()
                    + " PID=" + mv.getProductId());
            return mv;
        }
        if (epsonFallback != null) {
            final UsbDevice ef = epsonFallback;
            LOG.info("Fallback Epson detectado: " + ef.getDeviceName()
                    + " VID=" + ef.getVendorId()
                    + " PID=" + ef.getProductId());
            return ef;
        }
        lastError = "Impressora não encontrada (VID/PID)";
        LOG.severe(lastError + " esperado VID=0x" + String.format("%04X", vendorId)
                + " PID=0x" + String.format("%04X", productId));
        return null;
    }

    public boolean hasPermission(@Nullable UsbDevice d) {
        return d != null && usbManager != null && usbManager.hasPermission(d);
    }

    public void requestPermission(@NonNull android.app.Activity activity, @NonNull UsbDevice target) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                activity,
                0,
                new Intent(ACTION_USB_PERMISSION),
                flags);
        usbManager.requestPermission(target, pi);
        LOG.info("Solicitada permissão USB para " + target.getDeviceName());
    }

    @NonNull
    @Override
    public PrinterStatus connect() {
        disconnect();
        device = findTargetDevice();
        if (device == null) {
            return PrinterStatus.DEVICE_NOT_FOUND;
        }
        if (!hasPermission(device)) {
            lastError = "Permissão USB pendente";
            return PrinterStatus.PERMISSION_REQUIRED;
        }
        connection = usbManager.openDevice(device);
        if (connection == null) {
            lastError = "openDevice retornou null";
            LOG.severe(lastError);
            return PrinterStatus.ERROR_HARDWARE;
        }
        UsbEndpoint out = null;
        UsbInterface toClaim = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            out = findBulkOutEndpoint(intf);
            if (out != null) {
                toClaim = intf;
                break;
            }
        }
        if (toClaim == null || out == null) {
            lastError = "Endpoint BULK OUT não localizado";
            LOG.severe(lastError);
            connection.close();
            connection = null;
            return PrinterStatus.ERROR_ENDPOINT;
        }
        if (!connection.claimInterface(toClaim, true)) {
            lastError = "claimInterface falhou";
            LOG.severe(lastError);
            connection.close();
            connection = null;
            return PrinterStatus.ERROR_HARDWARE;
        }
        final UsbInterface ifaceClaimed = toClaim;
        final UsbEndpoint bulkEndpoint = out;
        claimedInterface = ifaceClaimed;
        bulkOut = bulkEndpoint;
        LOG.info("USB conectado | iface=" + ifaceClaimed.getId()
                + " maxPacket=" + bulkEndpoint.getMaxPacketSize());
        return PrinterStatus.CONNECTED;
    }

    @Nullable
    private static UsbEndpoint findBulkOutEndpoint(@NonNull UsbInterface intf) {
        for (int e = 0; e < intf.getEndpointCount(); e++) {
            UsbEndpoint ep = intf.getEndpoint(e);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                final UsbEndpoint endpointOut = ep;
                LOG.info("BULK OUT encontrado endpoint=" + e + " address=" + endpointOut.getAddress());
                return endpointOut;
            }
        }
        return null;
    }

    @Override
    public void disconnect() {
        if (connection != null && claimedInterface != null) {
            try {
                connection.releaseInterface(claimedInterface);
            } catch (Exception ignored) {
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        connection = null;
        claimedInterface = null;
        bulkOut = null;
        device = null;
    }

    @Override
    public boolean isConnected() {
        return connection != null && bulkOut != null && claimedInterface != null;
    }

    @Override
    public boolean sendRaw(@NonNull byte[] payload) {
        if (!isConnected()) {
            lastError = "Não conectado";
            return false;
        }
        int max = bulkOut.getMaxPacketSize();
        if (max <= 0) {
            max = 512;
        }
        int offset = 0;
        while (offset < payload.length) {
            int len = Math.min(max, payload.length - offset);
            byte[] chunk = Arrays.copyOfRange(payload, offset, offset + len);
            int written = connection.bulkTransfer(bulkOut, chunk, chunk.length, 8000);
            if (written < 0) {
                lastError = "bulkTransfer falhou código=" + written + " offset=" + offset;
                LOG.log(Level.SEVERE, lastError);
                return false;
            }
            offset += len;
        }
        return true;
    }

    @Nullable
    @Override
    public String getLastErrorMessage() {
        return lastError;
    }

    public void setLastError(@Nullable String msg) {
        this.lastError = msg;
    }
}
