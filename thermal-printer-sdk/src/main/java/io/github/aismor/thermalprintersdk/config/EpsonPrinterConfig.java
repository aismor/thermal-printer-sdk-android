package io.github.aismor.thermalprintersdk.config;

import androidx.annotation.NonNull;

public final class EpsonPrinterConfig {

    private final String target;
    private final String seriesKey;

    private EpsonPrinterConfig(@NonNull String target, @NonNull String seriesKey) {
        this.target = target;
        this.seriesKey = seriesKey;
    }

    @NonNull
    public static EpsonPrinterConfig defaults() {
        return new EpsonPrinterConfig("", "TM_T88");
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String getTarget() {
        return target;
    }

    @NonNull
    public String getSeriesKey() {
        return seriesKey;
    }

    public static final class Builder {

        private String target = "";
        private String seriesKey = "TM_T88";

        @NonNull
        public Builder target(@NonNull String value) {
            this.target = value != null ? value : "";
            return this;
        }

        @NonNull
        public Builder seriesKey(@NonNull String value) {
            this.seriesKey = value != null && !value.isEmpty() ? value : "TM_T88";
            return this;
        }

        @NonNull
        public EpsonPrinterConfig build() {
            return new EpsonPrinterConfig(target != null ? target : "", seriesKey);
        }
    }
}
