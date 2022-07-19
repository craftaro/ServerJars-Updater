package com.songoda.serverjars.handlers;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.utils.Utils;
import lombok.Getter;
import lombok.Setter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigHandler {

    private static final String HEADER = "Acceptable Versions (latest, 1.16.4, 1.8, etc...)";

    private final Properties properties = new Properties();

    @Getter
    @Setter
    private boolean skipConfigCreation = false, debug = false;

    public void init() {
        try {
            if(!this.skipConfigCreation && !ServerJars.CFG_FILE.exists()) { // Create the config if it doesn't exist, and we aren't skipping it
                ServerJars.CFG_FILE.createNewFile();
            }

            this.load();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() throws IOException {
        if(ServerJars.CFG_FILE.exists()){
            properties.load(new FileInputStream(ServerJars.CFG_FILE));
        }
    }

    public void reset() {
        properties.clear();
        this.setType(this.getType());
        this.setVersion(this.getVersion());
        this.setUseHomeDirectory(this.useHomeDirectory());
    }

    public void save() throws IOException{
        properties.store(new FileOutputStream(ServerJars.CFG_FILE), HEADER);
    }

    public String getType(){
        return properties.getProperty("type", "paper");
    }

    public void setType(String type){
        properties.setProperty("type", type);
    }

    public void setVersion(String version){
        properties.setProperty("version", version);
    }

    public String getVersion(){
        return properties.getProperty("version", "latest");
    }

    public boolean useHomeDirectory() {
        return Utils.bool(properties.getProperty("useHomeDirectory", "false"));
    }

    public void setUseHomeDirectory(boolean useHomeDirectory){
        properties.setProperty("useHomeDirectory", Boolean.toString(useHomeDirectory));
    }

    public void setLastUpdateCheck(long timestamp) {
        properties.setProperty("updateCheck.last", String.valueOf(timestamp));
    }

    public Long getLastUpdateCheck() {
        try {
            return Long.parseLong(properties.getProperty("updateCheck.last"));
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    public void setUpdateAvailable(boolean isAvailable) {
        properties.setProperty("updateCheck.available", String.valueOf(isAvailable));
    }

    public boolean isUpdateAvailable() {
        return Boolean.parseBoolean(properties.getProperty("updateCheck.available"));
    }
}
