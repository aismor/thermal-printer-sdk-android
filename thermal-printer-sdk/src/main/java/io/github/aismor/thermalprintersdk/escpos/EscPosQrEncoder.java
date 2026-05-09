package io.github.aismor.thermalprintersdk.escpos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;

public final class EscPosQrEncoder {

    private EscPosQrEncoder() {
    }

    @NonNull
    public static byte[] buildQrCode(@NonNull String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00});
            out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08});
            out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30});
            int ln = 3 + data.length;
            int pL = ln & 0xFF;
            int pH = (ln >> 8) & 0xFF;
            out.write(new byte[]{0x1D, 0x28, 0x6B, (byte) pL, (byte) pH, 0x31, 0x50, 0x30});
            out.write(data);
            out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});
        } catch (IOException ignored) {
        }
        return out.toByteArray();
    }
}
