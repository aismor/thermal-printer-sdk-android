package io.github.aismor.thermalprintersdk.impl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.aismor.thermalprintersdk.api.PrinterDriver;
import io.github.aismor.thermalprintersdk.api.PrinterManager;
import io.github.aismor.thermalprintersdk.api.PrinterStatus;

public final class ThermalPrinterManager implements PrinterManager {

    private PrinterDriver driver;

    @Override
    public void setDriver(@NonNull PrinterDriver driver) {
        this.driver = driver;
    }

    @Nullable
    @Override
    public PrinterDriver getDriver() {
        return driver;
    }

    @NonNull
    @Override
    public PrinterStatus connectAll() {
        if (driver == null) {
            return PrinterStatus.DISCONNECTED;
        }
        return driver.connect();
    }

    @Override
    public void disconnectAll() {
        if (driver != null) {
            driver.disconnect();
        }
    }

    @Override
    public boolean isReady() {
        return driver != null && driver.isConnected();
    }

    @NonNull
    @Override
    public PrinterStatus getAggregateStatus() {
        if (driver == null) {
            return PrinterStatus.DISCONNECTED;
        }
        PrinterStatus s = driver.getLastStatus();
        return s != null ? s : PrinterStatus.UNKNOWN;
    }
}
