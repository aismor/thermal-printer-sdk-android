package io.github.aismor.thermalprintersdk.escpos;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class EscPosCommands {

    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static final byte ESC = 0x1B;
    public static final byte GS = 0x1D;
    public static final byte LF = 0x0A;

    public static final byte[] INIT = new byte[]{ESC, '@'};

    public static final int DEFAULT_LEFT_MARGIN_DOTS = 0;

    public static byte[] align(int n) {
        return new byte[]{ESC, 'a', (byte) (n & 0xFF)};
    }

    public static byte[] leftMarginDots(int dots) {
        int d = Math.max(0, Math.min(dots, 65535));
        return new byte[]{GS, 'L', (byte) (d & 0xFF), (byte) ((d >> 8) & 0xFF)};
    }

    public static byte[] bold(boolean on) {
        return new byte[]{ESC, 'E', (byte) (on ? 1 : 0)};
    }

    public static byte[] feedLines(int n) {
        int lines = Math.max(0, Math.min(n, 255));
        return new byte[]{ESC, 'd', (byte) lines};
    }

    public static byte[] lineFeed() {
        return new byte[]{LF};
    }

    public static final byte[] CUT_PARTIAL_FEED = new byte[]{GS, 'V', 65, 0};

    public static final byte[] CUT_FULL_SIMPLE = new byte[]{GS, 'V', 0};

    public static byte[] cashDrawerKick() {
        return new byte[]{ESC, 'p', 0, 50, (byte) 120};
    }

    public static byte[] textToBytes(String s, Charset charset) {
        if (s == null) {
            return new byte[0];
        }
        return s.getBytes(charset != null ? charset : StandardCharsets.UTF_8);
    }

    private EscPosCommands() {
    }
}
