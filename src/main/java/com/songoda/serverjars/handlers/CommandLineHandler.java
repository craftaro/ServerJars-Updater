package com.songoda.serverjars.handlers;

import com.songoda.serverjars.ServerJars;
import com.songoda.serverjars.objects.Option;
import com.songoda.serverjars.objects.options.Help;
import com.songoda.serverjars.objects.options.MinecraftArgument;
import com.songoda.serverjars.objects.options.MinecraftArgumentDD;
import com.songoda.serverjars.objects.options.ServerJarsConfig;
import com.songoda.serverjars.objects.options.Version;
import com.songoda.serverjars.utils.Utils;

import java.util.Arrays;

public class CommandLineHandler {

    public static final Option[] options = new Option[]{
            new Help(),
            new Version(),
            new MinecraftArgument(),
            new MinecraftArgumentDD(),
            new ServerJarsConfig(),
    };

    private final String[] args;

    public CommandLineHandler(String[] args) {
        this.args = args;
        this.checkForMissingOptions();
        int optionsFound = ((int) Arrays.stream(options).filter(option -> Arrays.stream(this.args).anyMatch(it -> it.matches(Utils.regexFromGlob("--"+option.getName())))).count());
        for (String raw : args) {
            if (raw.startsWith("--")) {
                String arg = raw.substring(2);
                // Find the option
                Option option = Arrays.stream(options).filter(it -> arg.matches(Utils.regexFromGlob(it.getName()))).findFirst().orElse(null);
                if (option != null) {
                    if ((!option.isSingle() && optionsFound >= 1) || (option.isSingle() && optionsFound == 1)) {
                        // Run the option
                        option.run(arg);
                    }

                    continue; // Prevent further execution
                } else if(!ConfigHandler.cliCompatiblityMode()){
                    System.out.println("Unknown option: --" + arg);
                    System.exit(1);
                    break;
                }
            }

            if(ConfigHandler.cliCompatiblityMode()) {
                ServerJars.minecraftArguments.add(raw);
            }
        }
    }

    private void checkForMissingOptions() {
        Option[] missingOptions = Arrays.stream(options)
                .filter(Option::isRequired)
                .filter(option -> Arrays.asList(this.args).contains("--" + option.getName()))
                .toArray(Option[]::new);
        if (missingOptions.length > 0) {
            System.out.println("Missing required options: ");
            for (Option option : missingOptions) {
                System.out.println("  - " + option.getName());
            }
            System.exit(1);
        }
    }


}
