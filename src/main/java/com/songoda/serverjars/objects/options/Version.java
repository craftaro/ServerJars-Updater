package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.objects.Option;

public class Version extends Option {

    public Version(){
        super("version", "Shows the version of the running ServerJars.");
    }

    @Override
    public void run(String data) {
        System.out.println("Running version: v@version@");
        System.exit(0);
    }
}
