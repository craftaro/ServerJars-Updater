package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.objects.Option;

import java.util.Arrays;

public class MinecraftArgument extends Option {

    public MinecraftArgument() {
        super("mc.*", "Adds the given argument to be ran directly as argument of the server launcher.");
    }

    @Override
    public void run(String data) {
        data = data.replaceFirst("mc.", "");
        if(!data.isBlank()) {
            ServerJars.minecraftArguments.add(data);
        }
    }
}
