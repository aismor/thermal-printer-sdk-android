package io.github.aismor.thermalprintersdk.api;

public enum PrinterStatus {
    UNKNOWN,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    DEVICE_NOT_FOUND,
    ERROR_ENDPOINT,
    ERROR_TRANSFER,
    ERROR_HARDWARE;

    public boolean isReady() {
        return this == CONNECTED;
    }
}
