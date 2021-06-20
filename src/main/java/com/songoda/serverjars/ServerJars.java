package com.songoda.serverjars;

import com.serverjars.api.JarDetails;
import com.serverjars.api.Response;
import com.serverjars.api.request.AllRequest;
import com.serverjars.api.request.JarRequest;
import com.serverjars.api.request.LatestRequest;
import com.serverjars.api.request.TypesRequest;
import com.serverjars.api.response.AllResponse;
import com.serverjars.api.response.LatestResponse;
import com.serverjars.api.response.TypesResponse;

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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ServerJars {
    private static final File WORKING_DIRECTORY = new File(".");
    private static final File CFG_FILE = new File(WORKING_DIRECTORY, "serverjars.properties");
    private static final File CACHE_DIR = new File(WORKING_DIRECTORY, "jar");

    private static final Config cfg = new Config(CFG_FILE);

    public static void main(final String[] args) throws IOException, NoSuchAlgorithmException {
        // TODO
        // --help
        // --version
        // --sj.CFG_OPTION=VALUE
        // --sjSkipCfg [Skips potential config creation and uses default values if any are missing (everything set via args means no file is created)]
        // Prefix '--mc.' sends the rest of the string to the mc server (without 'mc.' prefix) [Allows for --mc.help to print spigot help]

        System.out.println("\n  █████╗  █████╗ ███████╗ █████╗ ███╗  ██╗ ██████╗██████╗ ██╗██████╗ ███████╗\n ██╔══██╗██╔══██╗██╔════╝██╔══██╗████╗ ██║██╔════╝██╔══██╗██║██╔══██╗██╔════╝\n ██║  ██║██║  ╚═╝█████╗  ███████║██╔██╗██║╚█████╗ ██████╔╝██║██████╔╝█████╗\n ██║  ██║██║  ██╗██╔══╝  ██╔══██║██║╚████║ ╚═══██╗██╔═══╝ ██║██╔══██╗██╔══╝\n ╚█████╔╝╚█████╔╝███████╗██║  ██║██║ ╚███║██████╔╝██║     ██║██║  ██║███████╗\n  ╚════╝  ╚════╝ ╚══════╝╚═╝  ╚═╝╚═╝  ╚══╝╚═════╝ ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝");

        System.out.println("\nSearching for updates...");

        File jar;
        if (!CFG_FILE.exists()) {
            System.out.println("\nIt looks like this is your first time using the updater. Would you like to create a config file now? [Y/N]\n" +
                    "If you choose 'n' a default config will be created for you instead.");
            String choice = awaitInput(s -> s.equalsIgnoreCase("y") || s.equalsIgnoreCase("n"), "Please choose Y or N");
            jar = setupEnv(choice == null || choice.equalsIgnoreCase("y"));
        } else {
            jar = setupEnv(false);
        }

//        new UpdateChecker(cfg); // Check for new app updates

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

        String[] vmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments().toArray(new String[0]);

        /* Start Minecraft server */
        String[] cmd = new String[vmArgs.length + args.length + 3];
        cmd[0] = getJavaExecutable();

        System.arraycopy(vmArgs, 0, cmd, 1, vmArgs.length);

        cmd[1 + vmArgs.length] = "-jar";
        cmd[2 + vmArgs.length] = jar.getAbsolutePath();

        System.arraycopy(args, 0, cmd, 3 + vmArgs.length, args.length);

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
                } catch (InterruptedException ignore) {
                }
            }
        } catch (IOException ex) {
            System.err.println("Error starting the Minecraft server.");
            ex.printStackTrace();
        }
    }

    private static File setupEnv(boolean guided) throws IOException, NoSuchAlgorithmException {
        cfg.load();

        String type = cfg.getType();
        String version = cfg.getVersion();

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
                }
                if (chosenVersion == null) {
                    chosenVersion = "latest";
                    System.out.println("Unable to get user input -> defaulting to latest.");
                }

                version = chosenVersion;
                System.out.println("Setup completed!\n");
                cfg.setType(type);
                cfg.setVersion(version);

                try {
                    cfg.save();
                } catch (IOException e) {
                    System.out.println("Could not save to properties file. Default values will be used...\n");
                }
            } else {
                System.out.println("Connection to ServerJars could not be established. Default values will be used...\n");
            }
        }

        JarDetails jarDetails = null;
        if (version.equals("latest")) {
            LatestResponse latestResponse = new LatestRequest(type).send();
            jarDetails = latestResponse.latestJar;
        } else {
            AllResponse allResponse = new AllRequest(type).send();
            for (JarDetails jar : allResponse.getJars()) {
                if (jar.getVersion().equalsIgnoreCase(version)) {
                    jarDetails = jar;
                }
            }
        }

        Files.createDirectories(CACHE_DIR.toPath());
        File jar = new File(CACHE_DIR, jarDetails.getFile());

        String hash = jar.exists() ? getHashMd5(jar.toPath()) : "";
        if (hash.isEmpty() || !hash.equals(jarDetails.getHash())) {
            System.out.println(hash.isEmpty() ? "\nDownloading jar..." : "\nUpdate found, downloading...");

            File[] cachedFiles = CACHE_DIR.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            if (cachedFiles != null) {
                for (File f : cachedFiles) {
                    Files.deleteIfExists(f.toPath());
                }
            }

            Response response = new JarRequest(type, version.equalsIgnoreCase("latest") ? null : version, jar).send();
            if (!response.isSuccess()) {
                System.out.println("\nThe jar version \"" + version + "\" was not found in our database...");
                return null;
            }

            System.out.println("\nJar updated successfully.");
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
