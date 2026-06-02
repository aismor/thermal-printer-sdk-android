package io.github.aismor.thermalprintersdk.escpos;

import java.util.regex.Pattern;

public final class EscPosPrintContent {

    private static final Pattern LOGCAT_LINE_PREFIX = Pattern.compile(
            "^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]+:\\s*");

    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String stripped = LOGCAT_LINE_PREFIX.matcher(line).replaceFirst("");
            if (stripped.isEmpty()) {
                continue;
            }
            if (isEmitterHeaderOnly(stripped)) {
                continue;
            }
            out.append(stripped).append('\n');
        }
        if (out.length() == 0) {
            return "";
        }
        int len = out.length();
        if (out.charAt(len - 1) == '\n') {
            out.setLength(len - 1);
        }
        return out.toString();
    }

    private static boolean isEmitterHeaderOnly(String line) {
        String t = line.trim();
        return "EmitterComposition".equals(t)
                || "EmitterComposition:".equals(t);
    }

    private EscPosPrintContent() {
    }
}
