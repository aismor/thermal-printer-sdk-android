package io.github.aismor.thermalprintersdk.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PrinterConnection {

    @NonNull
    PrinterStatus connect();

    void disconnect();

    boolean isConnected();

    boolean sendRaw(@NonNull byte[] payload);

    @Nullable
    String getLastErrorMessage();
}
