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
    private boolean skipConfigCreation = false, debug = false, useHttps = true, useVersionCli = false, useTypeCli = false, useCategoryCli = false, useHomeDirCli = false;

    @Getter
    @Setter
    private String apiDomain = "serverjars.com";

    public void init() {
        try {
            if(!this.skipConfigCreation && !ServerJars.CFG_FILE.exists()) { // Create the config if it doesn't exist, and we aren't skipping it
                ServerJars.CFG_FILE.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() throws IOException {
        if(ServerJars.CFG_FILE.exists()){
            String category = this.getCategory(), type = this.getType(), version = this.getVersion();
            boolean useHomeDirectory = this.useHomeDirectory();
            properties.load(new FileInputStream(ServerJars.CFG_FILE));

            if(useVersionCli) { // To give priority to the cli
                this.setVersion(version);
            }

            if(useCategoryCli){ // To give priority to the cli
                this.setCategory(category);
            }

            if(useTypeCli) { // To give priority to the cli
                this.setType(type);
            }

            if(useHomeDirCli){ // To give priority to the cli
                this.setUseHomeDirectory(useHomeDirectory);
            }
        }
    }

    public void reset() {
        String category = this.getCategory(), type = this.getType(), version = this.getVersion();
        boolean useHomeDir = this.useHomeDirectory();
        properties.clear();
        this.setCategory(useCategoryCli ? category : this.getCategory());
        this.setType(useTypeCli ? type : this.getType());
        this.setVersion(useVersionCli ? version : this.getVersion());
        this.setUseHomeDirectory(useHomeDirCli ? useHomeDir : this.useHomeDirectory());
    }

    public void save() throws IOException{
        properties.store(new FileOutputStream(ServerJars.CFG_FILE), HEADER);
    }

    public String getCategory() {
        return properties.getProperty("category", "servers");
    }

    public void setCategory(String category){
        properties.setProperty("category", category);
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

    // If enabled all the arguments will be passed to the command instead of single arguments
    public static boolean cliCompatiblityMode(){
        String data = System.getenv("SJ_COMPATIBILITY");
        if(data == null) data = "0";
        return Utils.bool(data);
    }
}
