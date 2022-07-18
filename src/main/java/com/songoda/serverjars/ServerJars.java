package com.songoda.serverjars;

import com.serverjars.api.JarDetails;
import com.serverjars.api.request.AllRequest;
import com.serverjars.api.request.LatestRequest;
import com.serverjars.api.request.TypesRequest;
import com.serverjars.api.response.AllResponse;
import com.serverjars.api.response.LatestResponse;
import com.serverjars.api.response.TypesResponse;
import com.songoda.serverjars.handlers.CommandLineHandler;
import com.songoda.serverjars.handlers.ConfigHandler;
import com.songoda.serverjars.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ServerJars {
    public static final File WORKING_DIRECTORY = new File(".");
    public static final File CFG_FILE = new File(WORKING_DIRECTORY, "serverjars.properties");
    public static final File HOME_DIR = Utils.folder(new File(Utils.folder(System.getProperty("user.home")), ".serverjars/"));
    private static File CACHE_DIR = new File(WORKING_DIRECTORY, "jar");

    public static final List<String> minecraftArguments = new ArrayList<>();
    public static final ConfigHandler config = new ConfigHandler();

    public static void main(final String[] args) throws IOException, NoSuchAlgorithmException {
        System.out.println("   _____                               __               \n" +
                "  / ___/___  ______   _____  _____    / /___ ___________\n" +
                "  \\__ \\/ _ \\/ ___/ | / / _ \\/ ___/_  / / __ `/ ___/ ___/\n" +
                " ___/ /  __/ /   | |/ /  __/ /  / /_/ / /_/ / /  (__  ) \n" +
                "/____/\\___/_/    |___/\\___/_/   \\____/\\__,_/_/  /____/  \n" +
                "ServerJars.com           Made with love by Songoda <3\n");
        Utils.debug("Loading CommandLineHandler...");
        new CommandLineHandler(args);
        Utils.debug("Initializing configuration...");
        config.init();

        File jar;
        if (!config.isSkipConfigCreation()) {
            System.out.println("\nIt looks like this is your first time using the updater. Would you like to create a config file now? [Y/N]\n" +
                    "If you choose 'n' a default config will be created for you instead.");
            String choice = awaitInput(s -> s.equalsIgnoreCase("y") || s.equalsIgnoreCase("n"), "Please choose Y or N");
            jar = setupEnv(choice == null || choice.equalsIgnoreCase("y"));
        } else {
            System.out.println("Skipping config creation and using default values...");
            jar = setupEnv(false);
        }

        new UpdateChecker(config); // Check for new app updates

        if (jar == null) {
            System.out.println("\nServerJars could not be reached...");
            System.out.println("\nAttempting to load last working Jar.");

            jar = findExistingJar();

            if (jar == null) {
                System.out.println("\nAll attempts to run failed...");
                System.exit(1);
            }

            System.out.println("\nThe attempt was successful!");
        }

        /* Start Minecraft server */
        LinkedList<String> cmd = new LinkedList<>(); // This will be the list of commands to be run
        cmd.add(getJavaExecutable()); // The 'java' cmd
        cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments()); // We pass the vm arguments
        cmd.add("-jar"); // Now we add the '-jar' argument
        cmd.add(jar.getAbsolutePath()); // Pass the server location
        cmd.addAll(minecraftArguments.stream().map(it -> "-" + it).toList()); // Pass the minecraft arguments that we got with '--mc.*'

        Utils.debug("Running command: " + String.join(" ", cmd));

        try {
            Process process = new ProcessBuilder(cmd)
                    .command(cmd)
                    .inheritIO()
                    .start();

            Runtime.getRuntime().addShutdownHook(new Thread(process::destroy));

            while (process.isAlive()) {
                try {
                    int exitCode = process.waitFor();

                    if (exitCode != 0) {
                        System.err.println("Server unexpectedly exited with code " + exitCode);
                    }

                    break;
                } catch (InterruptedException ignore) { }
            }
        } catch (IOException ex) {
            System.err.println("Error starting the Minecraft server.");
            ex.printStackTrace();
        }
    }

    private static File setupEnv(boolean guided) throws IOException, NoSuchAlgorithmException {
        if(!config.isSkipConfigCreation()) {
            config.reset();
        }
        config.load();

        String type = config.getType();
        String version = config.getVersion();

        if (guided) {
            System.out.println("Connecting to ServerJars to find available jar types...\n");
            TypesResponse typesResponse = new TypesRequest().send();
            if (typesResponse.isSuccess()) {
                Map<String, List<String>> typeMap = typesResponse.getAllTypes();
                List<String> types = new ArrayList<>();
                for (List<String> typeList : typeMap.values()) {
                    types.addAll(typeList);
                }

                System.out.println("What server type would you like to use?" + "\n" + "Available types:");
                StringBuilder typeString = new StringBuilder();
                int i = 0;
                for (String t : types) {
                    if (i == 6) {
                        typeString.append("\n");
                        i = 0;
                    }
                    typeString.append(t).append(", ");
                    i++;
                }
                System.out.println(typeString.substring(0, typeString.length() - 2) + ".");
                String chosenJar = awaitInput(s -> types.contains(s.toLowerCase()), "The jar type '%s' was not listed above in the type list\nPlease choose another.");
                if (chosenJar == null) {
                    chosenJar = "paper";
                    System.out.println("Unable to get user input -> defaulting to paper.");
                }
                type = chosenJar;
                System.out.println("\nWhat server version would you like to run?" + "\n" + "Leave this blank or type 'latest' for latest");
                String chosenVersion = awaitInput(s -> true, "Hmm.. that version was somehow incorrect...");

                if (chosenVersion != null && chosenVersion.isEmpty()) {
                    chosenVersion = "latest";
                } else if (chosenVersion == null) {
                    chosenVersion = "latest";
                    System.out.println("Unable to get user input -> defaulting to latest.");
                }

                version = chosenVersion;

                System.out.println("\\nWould you like to use always the same server jar for every ServerJars instance? [Y/N]");
                String alwaysUse = awaitInput(s -> s.equalsIgnoreCase("y") || s.equalsIgnoreCase("n"), "Please choose Y or N");
                config.setUseHomeDirectory(alwaysUse == null || alwaysUse.equalsIgnoreCase("y"));

                System.out.println("Setup completed!\n");
                config.setType(type);
                config.setVersion(version);

                try {
                    config.save();
                } catch (IOException e) {
                    System.out.println("Could not save to properties file. Default values will be used...\n");
                }
            } else {
                System.out.println("Connection to ServerJars could not be established. Default values will be used...\n");
            }
        }

        if(config.useHomeDirectory()) { // Now we set up the cache dir to be the home dir if is set to
            CACHE_DIR = new File(HOME_DIR, "jar");
        }

        Utils.debug("Loading " + type + " (" + version + ")");

        JarDetails jarDetails = null;
        if (!version.equals("latest")) {
            AllResponse allResponse = new AllRequest(type).send();
            for (JarDetails jar : allResponse.getJars()) {
                if (jar.getVersion().equalsIgnoreCase(version)) {
                    jarDetails = jar;
                }
            }
        }

        if(jarDetails == null) {
            if(!version.equals("latest")){ // Only show the error message if is not the latest version what we're looking for.
                System.out.println("Could not fetch jar details for the given version '" + version + "'. Using latest...");
            }
            LatestResponse latestResponse = new LatestRequest(type).send();
            jarDetails = latestResponse.latestJar;
        }

        Files.createDirectories(CACHE_DIR.toPath()); // Create the cached files directory
        File jar = new File(CACHE_DIR, jarDetails.getFile()); // This is the server jar

        String hash = jar.exists() ? getHashMd5(jar.toPath()) : "";
        if (hash.isEmpty() || !hash.equals(jarDetails.getHash())) {
            File[] cachedFiles = CACHE_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (cachedFiles != null) {
                for (File f : cachedFiles) {
                    Files.deleteIfExists(f.toPath());
                }
            }

            boolean download = Utils.downloadJar(type, version, jar, hash.isEmpty());
            if (!download) {
                return null;
            }

            System.out.println("\nJar successfully " + (hash.isEmpty() ? "downloaded" : "updated") + ".");
        } else {
            System.out.println("\nThe jar is up to date.");
        }

        String launching = "\nLaunching " + jarDetails.getFile() + "...";
        System.out.println(launching + "\n" + launching.replaceAll("[^.]", ".") + "\n");

        return jar;
    }

    private static File findExistingJar() {
        File[] files = new File("jar")
                .listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        return files != null ? files[0] : null;
    }

    private static String awaitInput(Predicate<String> predicate, String errorMessage) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();

                if (predicate.test(line)) {
                    return line;
                } else {
                    System.err.println("\n" + String.format(errorMessage, line));
                }
            }
        } catch (IOException ignore) {
        }

        return null;
    }

    private static String getJavaExecutable() {
        File binDir = new File(System.getProperty("java.home"), "bin");
        File javaExe = new File(binDir, "java");

        if (!javaExe.exists()) {
            javaExe = new File(binDir, "java.exe");
        }

        if (!javaExe.exists()) {
            System.err.println("We could not find your java executable inside '" + binDir.getAbsolutePath() + "' - Using command 'java' instead");

            return "java";
        }

        return javaExe.getAbsolutePath();
    }

    private static String getHashMd5(Path path) throws IOException, NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();

        for (byte aByte : MessageDigest.getInstance("MD5").digest(Files.readAllBytes(path))) {
            hash.append(Character.forDigit((aByte >> 4) & 0xF, 16));
            hash.append(Character.forDigit((aByte & 0xF), 16));
        }

        return hash.toString();
    }
}
