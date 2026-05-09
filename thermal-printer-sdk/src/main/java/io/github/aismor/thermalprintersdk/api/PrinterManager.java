package io.github.aismor.thermalprintersdk.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PrinterManager {

    void setDriver(@NonNull PrinterDriver driver);

    @Nullable
    PrinterDriver getDriver();

    @NonNull
    PrinterStatus connectAll();

    void disconnectAll();

    boolean isReady();

    @NonNull
    PrinterStatus getAggregateStatus();
}
