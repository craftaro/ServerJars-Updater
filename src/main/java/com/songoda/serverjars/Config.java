package com.songoda.serverjars;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Properties;

public class Config {
    private static final String HEADER = "Acceptable Versions (latest, 1.16.4, 1.8, etc...)";

    private static final String DEFAULT_TYPE = "paper";
    private static final String DEFAULT_VERSION = "latest";
    private static final boolean DEFAULT_USE_HOME = false;

    private final Properties properties;
    private final File file;

    public Config(File file) {
        this.file = file;
        this.properties = new Properties();

        reset();
    }

    void setType(String type) {
        this.properties.setProperty("type", type == null ? DEFAULT_TYPE : type);
    }

    String getType() {
        return this.properties.getProperty("type", DEFAULT_TYPE);
    }

    void setVersion(String version) {
        this.properties.setProperty("version", version == null ? DEFAULT_VERSION : version);
    }

    String getVersion() {
        return this.properties.getProperty("version", DEFAULT_VERSION);
    }

    void setUseHome(boolean home) {
        this.properties.setProperty("useSameJar", String.valueOf(home));
    }

    boolean getUseHome() {
        return this.properties.getProperty("useSameJar", String.valueOf(DEFAULT_USE_HOME)).equals("true");
    }

    void setLastUpdateCheck(long timestamp) {
        this.properties.setProperty("updateCheck.last", String.valueOf(timestamp));
    }

    Long getLastUpdateCheck() {
        try {
            return Long.parseLong(this.properties.getProperty("updateCheck.last"));
        } catch (NumberFormatException ignore) {
        }

        return null;
    }

    void setUpdateAvailable(boolean isAvailable) {
        this.properties.setProperty("updateCheck.available", String.valueOf(isAvailable));
    }

    boolean isUpdateAvailable() {
        return Boolean.parseBoolean(this.properties.getProperty("updateCheck.available"));
    }

    void load() throws IOException {
        reset();

        if (this.file.exists()) {
            try (InputStream in = new FileInputStream(this.file)) {
                this.properties.load(in);
            }
        }
    }

    void save() throws IOException {
        if (!this.file.getParentFile().exists()) {
            Files.createDirectories(this.file.toPath().getParent());
        }

        try (OutputStream out = new FileOutputStream(this.file)) {
            this.properties.store(out, HEADER);
        }
    }

    void reset() {
        this.properties.clear();

        this.properties.setProperty("type", DEFAULT_TYPE);
        this.properties.setProperty("version", DEFAULT_VERSION);
    }
}
