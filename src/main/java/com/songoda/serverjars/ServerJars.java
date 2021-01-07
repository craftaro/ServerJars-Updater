package com.songoda.serverjars;

import com.google.gson.Gson;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.*;

public final class ServerJars {

    public static void main(final String[] args) {
        final Method mainMethod;
        System.out.println("   _____                               __               \n" +
                "  / ___/___  ______   _____  _____    / /___ ___________\n" +
                "  \\__ \\/ _ \\/ ___/ | / / _ \\/ ___/_  / / __ `/ ___/ ___/\n" +
                " ___/ /  __/ /   | |/ /  __/ /  / /_/ / /_/ / /  (__  ) \n" +
                "/____/\\___/_/    |___/\\___/_/   \\____/\\__,_/_/  /____/  \n" +
                "ServerJars.com           Made with love by Songoda <3");
        System.out.println("ServerJars is starting...");

        Path jar = setupEnv();
        if (jar == null) {
            System.out.println("ServerJars could not be reached...");
            System.out.println("Attempting to load last working Jar.");
            jar = findExistingJar();
            if (jar == null) {
                System.out.println("All attempts to run failed...");
                System.exit(1);
            }
            System.out.println("The attempt was successful!");
        }
        final String main = getMainClass(jar);
        mainMethod = getMainMethod(jar, main);

        try {
            mainMethod.invoke(null, new Object[]{args});
        } catch (final IllegalAccessException | InvocationTargetException e) {
            System.err.println("Error while running patched jar");
            e.printStackTrace();
            System.exit(1);
        }
    }
    

    private static Path setupEnv() {

        Properties properties = new Properties();
        Path cache = Paths.get("jar");

        try {
            InputStream defaultsInput = ServerJars.class.getResourceAsStream("/serverjars.properties");
            Reader reader = new BufferedReader(new InputStreamReader(defaultsInput));
            properties.load(reader);

            File file = new File("serverjars.properties");
            if (!file.exists()) {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                properties.store(fileOutputStream, "Acceptable Versions (latest, 1.15.1, 1.8, etc...)");
                fileOutputStream.close();
            }

            if (!Files.isDirectory(cache)) {
                Files.createDirectories(cache);
            }

            InputStream input = new FileInputStream(file);
            properties.load(new BufferedReader(new InputStreamReader(input)));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        String type = (String) properties.get("type");
        String version = (String) properties.get("version");

        String json;
        try {
            json = readUrl("https://serverjars.com/api/fetchAll/" + type);
        } catch (FileNotFoundException e) {
            System.out.println("Incorrect jar type \"" + type + "\" provided...");
            return null;
        } catch (IOException e) {
            return null;
        }

        Gson gson = new Gson();
        Response response = gson.fromJson(json, Response.class);

        Stream<ServerJar> stream = response.getServerJars().stream();

        if (!version.equalsIgnoreCase("latest"))
            stream = stream.filter(jar -> jar.getVersion().equalsIgnoreCase(version));
        Optional<ServerJar> optionalServerJar = stream.findFirst();

        if (!optionalServerJar.isPresent()) {
            System.out.println("The jar version \"" + version + "\" was not found in our database...");
            return null;
        }

        ServerJar serverJar = optionalServerJar.get();

        final Path jar = Paths.get(cache.normalize().toString() + File.separator + serverJar.getFileName());

        if (isJarInvalid(jar, serverJar.getMd5Hash())) {
            System.out.println("Update found, downloading...");
            try {
                for (File f : cache.toFile().listFiles())
                    if (f.getName().endsWith(".jar"))
                        f.delete();

                URL downloadUrl = new URL("https://serverjars.com/api/fetchJar/" + type + "/" + serverJar.getVersion());
                if (((HttpURLConnection) downloadUrl.openConnection()).getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    System.out.println("Connection failure...");
                    return null;
                }

                final ReadableByteChannel source = Channels.newChannel(downloadUrl.openStream());
                final FileChannel fileChannel = FileChannel.open(jar, CREATE, WRITE, TRUNCATE_EXISTING);
                fileChannel.transferFrom(source, 0, Long.MAX_VALUE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Jar updated successfully.");
        } else {
            System.out.println("The jar is up to date.");
        }
        String fileName = serverJar.getFileName().split("-")[0];
        String name = fileName.substring(0, 1).toUpperCase() + fileName.substring(1);
        String launching = "Launching " + name + "...";
        System.out.println(launching + "\n" + launching.replaceAll("[^.]", "."));
        return jar;
    }

    private static String readUrl(String urlString) throws IOException {
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            urlConn.setConnectTimeout(5000);
            urlConn.setReadTimeout(5000);
            reader = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);

            return buffer.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    private static String getMainClass(final Path jar) {
        try (
                final InputStream is = new BufferedInputStream(Files.newInputStream(jar));
                final JarInputStream js = new JarInputStream(is)
        ) {
            return js.getManifest().getMainAttributes().getValue("Main-Class");
        } catch (final IOException e) {
            System.err.println("Error reading from patched jar");
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static Method getMainMethod(final Path jar, final String mainClass) {
        Agent.addToClassPath(jar);
        try {
            final Class<?> cls = Class.forName(mainClass, true, ClassLoader.getSystemClassLoader());
            return cls.getMethod("main", String[].class);
        } catch (final NoSuchMethodException | ClassNotFoundException e) {
            System.err.println("Failed to find main method in patched jar");
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static Path findExistingJar() {
        try {
            Path cache = Paths.get("jar");
            if (Files.isDirectory(cache)) {
                List<Path> list = Files.list(cache).filter(f -> f.toString().endsWith(".jar")).collect(Collectors.toList());
                if (list.isEmpty())
                    return null;
                return list.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static byte[] readFully(final InputStream in, final int size) throws IOException {
        try {
            final int bufSize;
            if (size == -1) {
                bufSize = 16 * 1024;
            } else {
                bufSize = size;
            }

            byte[] buffer = new byte[bufSize];
            int off = 0;
            int read;
            while ((read = in.read(buffer, off, buffer.length - off)) != -1) {
                off += read;
                if (off == buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length * 2);
                }
            }
            return Arrays.copyOfRange(buffer, 0, off);
        } finally {
            in.close();
        }
    }

    private static byte[] readBytes(final Path file) {
        try {
            return readFully(Files.newInputStream(file), (int) Files.size(file));
        } catch (final IOException e) {
            System.err.println("Failed to read all of the data from " + file.toAbsolutePath());
            e.printStackTrace();
            System.exit(1);
            throw new InternalError();
        }
    }

    private static boolean isJarInvalid(final Path jar, final byte[] hash) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            if (Files.exists(jar)) {
                final byte[] jarBytes = readBytes(jar);
                return !Arrays.equals(hash, digest.digest(jarBytes));
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return true;
    }
}
