package com.songoda.serverjars.objects.options;

import com.songoda.serverjars.handlers.CommandLineHandler;
import com.songoda.serverjars.objects.Option;

public class Help extends Option {

    public Help() {
        super("help", "Shows the help page");
    }

    @Override
    public void run(String data) {
        System.out.println("Available options:");
        for(Option option : CommandLineHandler.options) {
            System.out.println("--" + option.getName() + " \t\t\t> " + option.getDescription());
        }
        System.exit(0);
    }

    @Override
    public boolean isSingle() {
        return true;
    }
}
