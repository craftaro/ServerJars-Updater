package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.objects.Option;
import com.songoda.serverjars.utils.Utils;

public class ServerJarsConfig extends Option {

    public ServerJarsConfig(){
        super("sj.*", "Adds the given argument as config to the ServerJars system.");
    }

    @Override
    public void run(String data) {
        data = data.split("\\.")[1];
        String[] args = data.split("=");
        String key = args[0];
        String value = args.length == 1 ? "true" : args[1];
        switch (key) {
            default:
                System.out.println("Unknown config key: " + key);
                break;
            case "skipConfigCreation":
                ServerJars.config.setSkipConfigCreation(Utils.bool(value));
                break;
            case "serverType":
                ServerJars.config.setType(value);
                break;
            case "version":
                ServerJars.config.setVersion(value);
                break;
            case "useHomeDirectory":
                ServerJars.config.setUseHomeDirectory(Utils.bool(value));
                break;
            case "debug":
                ServerJars.config.setDebug(Utils.bool(value));
                break;
        }
    }
}


