package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.objects.Option;

public class MinecraftArgumentDD extends Option {

    public MinecraftArgumentDD() {
        super("mcdd.*", "Adds the given argument to be executed directly as argument (with a double dash) of the server launcher. (Ex: --mcdd.nogui > --nogui)");
    }

    @Override
    public void run(String data) {
        data = data.replaceFirst("mcdd.", "");
        if(!data.isEmpty()) {
            ServerJars.minecraftArguments.add("-" + data); // Only add a single dash because the main class will add another dash before the command.
        }
    }
}
