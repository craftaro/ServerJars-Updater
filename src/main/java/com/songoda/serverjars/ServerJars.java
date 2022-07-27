package com.songoda.serverjars;

import com.google.gson.JsonObject;
import com.songoda.serverjars.handlers.CommandLineHandler;
import com.songoda.serverjars.handlers.ConfigHandler;
import com.songoda.serverjars.utils.Utils;
import lombok.Getter;
import org.apache.commons.io.FileUtils;

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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ServerJars {
    public static final File WORKING_DIRECTORY = new File(".");
    public static final File CFG_FILE = new File(WORKING_DIRECTORY, "serverjars.properties");
    public static final File HOME_DIR = Utils.folder(new File(Utils.folder(System.getProperty("user.home")), ".serverjars/"));
    private static File CACHE_DIR = new File(WORKING_DIRECTORY, "jar");

    @Getter
    private static boolean isFirstStart = false;

    public static final List<String> minecraftArguments = new ArrayList<>();
    public static final ConfigHandler config = new ConfigHandler();

    public static void main(final String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
        System.out.println("   _____                               __               \n" +
                "  / ___/___  ______   _____  _____    / /___ ___________\n" +
                "  \\__ \\/ _ \\/ ___/ | / / _ \\/ ___/_  / / __ `/ ___/ ___/\n" +
                " ___/ /  __/ /   | |/ /  __/ /  / /_/ / /_/ / /  (__  ) \n" +
                "/____/\\___/_/    |___/\\___/_/   \\____/\\__,_/_/  /____/  \n" +
                "ServerJars.com           Made with love by Songoda <3\n");
        Utils.debug("Loading CommandLineHandler...");
        isFirstStart = !CFG_FILE.exists();
        config.init();
        new CommandLineHandler(args);
        Utils.debug("Initializing configuration...");
        config.load();

        File jar;
        if (!config.isSkipConfigCreation()) {
            if(isFirstStart) {
                System.out.println("\nIt looks like this is your first time using the updater. Would you like to create a config file now? [Y/N]\n" +
                                   "If you choose 'n' a default config will be created for you instead.");
                String choice = awaitInput(s -> s.equalsIgnoreCase("y") || s.equalsIgnoreCase("n"), "Please choose Y or N");
                jar = setupEnv(choice == null || choice.equalsIgnoreCase("y"));
            } else {
                jar = setupEnv(false);
            }
        } else {
            System.out.println("Skipping config creation...");
            jar = setupEnv(false);
        }

        Utils.updateCache();

        new UpdateChecker(config); // Check for new app updates

        if (jar == null) {
            System.out.println("\nServerJars could not be reached...");
            System.out.println("\nAttempting to load last working Jar.");

            jar = findExistingJar();

            if (jar == null) {
                System.out.println("\nAll attempts to run failed...");
                System.out.println("\nMake sure that you're running a valid jar and version. You can use '--sj.debug' to find more information about this error.");
                System.exit(1);
            }

            System.out.println("\nThe attempt was successful!");
        }

        /* Start Minecraft server */
        LinkedList<String> cmd = new LinkedList<>(); // This will be the list of commands to be run
        if(config.getType().equalsIgnoreCase("forge")){
            FileUtils.writeLines(new File(WORKING_DIRECTORY, "user_jvm_args.txt"), ManagementFactory.getRuntimeMXBean().getInputArguments());
            File forgeRunner = new File(WORKING_DIRECTORY, Utils.isWindows() ? "run.bat" : "run.sh");
            if(!forgeRunner.exists()){
                System.out.println("\nCould not find forge runner! Please rerun the updater.");
                System.exit(1);
            }

            if(Utils.isWindows()) {
                cmd.add("cmd.exe");
                cmd.add("/c");
            }
            cmd.add(forgeRunner.getAbsolutePath());
        } else {
            cmd.add(getJavaExecutable()); // The 'java' cmd
            cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments()); // We pass the vm arguments
            cmd.add("-jar"); // Now we add the '-jar' argument
            cmd.add(jar.getAbsolutePath()); // Pass the server location
        }

        cmd.addAll(minecraftArguments.stream().map(it -> "-" + it).collect(Collectors.toList())); // Pass the minecraft arguments that we got with '--mc.*' or '--mcdd.*'

        Utils.debug("Running command: " + String.join(" ", cmd));

        try {
            Process process = new ProcessBuilder(cmd)
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

    private static File setupEnv(boolean guided) throws IOException, NoSuchAlgorithmException, InterruptedException {
        if(!config.isSkipConfigCreation()) {
            config.reset();
            config.load();
        }

        String type = config.getType();
        String category = config.getCategory();
        String version = config.getVersion();

        if (guided) {
            System.out.println("Connecting to ServerJars to find available jar categories...\n");
            List<String> categories = Utils.fetchCategories();
            if(categories != null){
                System.out.println("Which server category would you like to use?" + "\n" + "Available categories:");
                StringBuilder categoryString = new StringBuilder();
                int i = 0;
                for (String t : categories) {
                    if (i == 6) {
                        categoryString.append("\n");
                        i = 0;
                    }
                    categoryString.append(t).append(", ");
                    i++;
                }
                System.out.println(categoryString.substring(0, categoryString.length() - 2) + ".");
                String choosenCategory = awaitInput(s -> categories.contains(s.toLowerCase()), "The jar type '%s' was not listed above in the type list\nPlease choose another.");
                if (choosenCategory == null) {
                    choosenCategory = "servers";
                    System.out.println("Unable to get user input -> defaulting to servers.");
                }
                category = choosenCategory;

                System.out.println("Connecting to ServerJars to find available jar types...\n");
                List<String> types = Utils.fetchTypes(category);
                if (types != null) {
                    System.out.println("Which server type would you like to use?" + "\n" + "Available types:");
                    StringBuilder typeString = new StringBuilder();
                    i = 0;
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

                    System.out.println("\nWould you like to use always the same server jar for every ServerJars instance? [Y/N]");
                    String alwaysUse = awaitInput(s -> s.equalsIgnoreCase("y") || s.equalsIgnoreCase("n"), "Please choose Y or N");
                    config.setUseHomeDirectory(alwaysUse == null || alwaysUse.equalsIgnoreCase("y"));

                    System.out.println("Setup completed!\n");
                    config.setCategory(category);
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
            } else {
                System.out.println("Connection to ServerJars could not be established. Default values will be used...\n");
            }
        }

        if(config.useHomeDirectory()) { // Now we set up the cache dir to be the home dir if is set to
            CACHE_DIR = new File(HOME_DIR, "jar");
        }

        Utils.debug("Loading " + type + " (" + category + "), version: " + version + "...");

        JsonObject jarDetails = Utils.getJarDetails(category, type, version);

        if(jarDetails == null) {
            if(!version.equals("latest")){ // Only show the error message if is not the latest version what we're looking for.
                System.out.println("Could not fetch jar details for the given version '" + version + "'. Using latest...");
                config.setVersion("latest");
                try {
                    config.save();
                    return setupEnv(false);
                }catch (IOException e){
                    System.err.println("Failed to save the config. Please try again later");
                    Utils.debug(e);
                }
            }

            System.out.println("Could not find a suitable jar for you to use. Checking for cached jars...");
            // The naming should be "type-version.jar"
            JsonObject cachedDetails = Utils.detailsFromCache(category, type, version);

            if(cachedDetails == null) {
                System.out.println("Could not find a cached jar to use. Maybe you should check if the site is up and that you have a working internet connection.");
                System.exit(1);
            }

            File cached = new File(CACHE_DIR, cachedDetails.get("file").getAsString());

            System.out.println("Found " + cached.getName() + " in the cache directory. Using this jar.");
            jarDetails.addProperty("cached", true);
            jarDetails = cachedDetails;
        }

        Files.createDirectories(CACHE_DIR.toPath()); // Create the cached files directory
        File jar = new File(CACHE_DIR, jarDetails.get("file").getAsString()); // This is the server jar

        String hash = jar.exists() ? getHashMd5(jar.toPath()) : "";
        boolean updateFound = hash.isEmpty() || !hash.equals(jarDetails.get("md5").getAsString());
        if (updateFound) {
            File[] cachedFiles = CACHE_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (cachedFiles != null) {
                for (File f : cachedFiles) {
                    Files.deleteIfExists(f.toPath());
                }
            }

            boolean download = Utils.downloadJar(category, type, version, jar, hash.isEmpty());
            if (!download) {
                return null;
            }

            System.out.println("\nJar successfully " + (hash.isEmpty() ? "downloaded" : "updated") + ".");
        } else {
            if(!jarDetails.has("cached")){
                System.out.println("\nThe jar is up to date.");
            }
        }

        Utils.debug("Running jar with hash: " + jarDetails.get("md5").getAsString());

        File forgeRunner = new File(WORKING_DIRECTORY, Utils.isWindows() ? "run.bat" : "run.sh");
        if(type.equalsIgnoreCase("forge") && (updateFound || !forgeRunner.exists())) {
            System.out.println("\n"+(updateFound ? "The system detected forge as server type" : "The forge runner could not be found.")+". We are now going to run the forge installer to update the libraries...");
            String[] cmd = new String[]{getJavaExecutable(), "-jar", jar.getAbsolutePath(), "--installServer"};
            Utils.debug("Running forge installer: " + String.join(" ", cmd));
            new ProcessBuilder(cmd)
                .directory(WORKING_DIRECTORY)
                .start()
                .waitFor(); // We wait for the installer to finish to then run the server.
        }

        String launching = "\nLaunching " + jarDetails.get("file").getAsString() + "...";
        System.out.println(launching + "\n" + launching.replaceAll("[^.]", ".") + "\n");

        return jar;
    }

    private static File findExistingJar() {
        File[] files = new File("jar")
        .listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

        return files != null ? (files.length > 0 ? files[0] : null) : null;
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
