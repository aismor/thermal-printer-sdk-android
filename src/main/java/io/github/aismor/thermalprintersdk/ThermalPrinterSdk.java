package io.github.aismor.thermalprintersdk;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.logging.Logger;

import io.github.aismor.thermalprintersdk.api.PrinterManager;
import io.github.aismor.thermalprintersdk.config.EpsonPrinterConfig;
import io.github.aismor.thermalprintersdk.driver.EpsonPrinterDriver;
import io.github.aismor.thermalprintersdk.driver.UsbEscPosPrinterDriver;
import io.github.aismor.thermalprintersdk.impl.ThermalPrinterManager;

public final class ThermalPrinterSdk {

    private static final Logger LOG = Logger.getLogger(ThermalPrinterSdk.class.getName());

    public ThermalPrinterSdk() {
        logStartupVersion();
    }

    @NonNull
    public static String getVersion() {
        return BuildConfig.VERSION_NAME;
    }

    private static void logStartupVersion() {
        LOG.info(() -> "Thermal Printer SDK " + getVersion());
    }

    @NonNull
    public static PrinterManager createPrinterManager() {
        return new ThermalPrinterManager();
    }

    @NonNull
    public static UsbEscPosPrinterDriver usbEscPosGeneric(@NonNull Context context) {
        return new UsbEscPosPrinterDriver(context);
    }

    @NonNull
    public static UsbEscPosPrinterDriver usbEscPosGeneric(@NonNull Context context, int vendorId, int productId) {
        return new UsbEscPosPrinterDriver(context, vendorId, productId);
    }

    @NonNull
    public static EpsonPrinterDriver epson(@NonNull Context context, @NonNull EpsonPrinterConfig config) {
        return new EpsonPrinterDriver(context, config);
    }

    @NonNull
    public static EpsonPrinterDriver epson(@NonNull Context context) {
        return new EpsonPrinterDriver(context, EpsonPrinterConfig.defaults());
    }
}
