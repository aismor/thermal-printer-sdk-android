package io.github.aismor.thermalprintersdk.api;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PrinterDriver {

    void requestPermission(@NonNull Activity activity);

    @NonNull
    PrinterStatus connect();

    void disconnect();

    boolean isConnected();

    void printText(@Nullable String text);

    void printQrCode(@NonNull String content);

    void printTest();

    void feedLines(int lines);

    void cut();

    void openCashDrawer();

    @Nullable
    PrinterStatus getLastStatus();

    @Nullable
    String getLastErrorMessage();
}
