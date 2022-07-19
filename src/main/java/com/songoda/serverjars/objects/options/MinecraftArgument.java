package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.objects.Option;

import java.util.Arrays;

public class MinecraftArgument extends Option {

    public MinecraftArgument() {
        super("mc.*", "Adds the given argument to be executed directly as argument (with a single dash) of the server launcher. (Ex: --mc.nogui > -nogui)");
    }

    @Override
    public void run(String data) {
        data = data.replaceFirst("mc.", "");
        if(!data.isEmpty()) {
            ServerJars.minecraftArguments.add(data);
        }
    }
}
