package de.tomino.serverjars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

public class UpdaterAPI {


    private static final String API = "https://api.github.com/repos/ZeusSeinGrossopa/UpdaterAPI/releases/latest";

    private static File updaterFile = null;

    private static File jarPath;

    public static void downloadUpdater(File destination) {
//        destination = new File((destination.isDirectory() ? destination : new File(FilenameUtils.getPath(destination.getAbsolutePath()))) + "/Updater.jar");

        final File finalDestination = destination;
        updaterFile = finalDestination;

        getLatestVersion(urlCallback -> {
            try {
                URL url = new URL(urlCallback);

                FileUtils.copyURLToFile(url, finalDestination);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static void getLatestVersion(Consumer<String> consumer) {
//        new Thread(() -> {
            try {
                HttpURLConnection connect = (HttpURLConnection) new URL(API).openConnection();

                connect.setConnectTimeout(10000);
                connect.connect();

                InputStream in = connect.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                if (connect.getResponseCode() == 200) {
                    JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

                    consumer.accept(object.entrySet().stream().filter(e -> e.getKey().equals("assets")).map(Map.Entry::getValue).findFirst().orElseThrow(() -> new RuntimeException("Can not update system"))
                            .getAsJsonArray()
                            .get(0).getAsJsonObject().get("browser_download_url").getAsString());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
//        }).start();
    }

    public static void update(String url, File newFile) throws IOException {
        if(updaterFile == null)
            throw new NullPointerException("The downloadUpdater must be called before using this method. Alternate use the #update(updaterFile, url, newFile) method.");

        update(updaterFile, url, newFile);
    }

    public static void update(String url, File newFile, boolean restart) throws IOException {
        if(updaterFile == null)
            throw new NullPointerException("The downloadUpdater must be called before using this method. Alternate use the #update(updaterFile, url, newFile) method.");

        update(updaterFile, url, newFile, restart);
    }

    public static void update(File updaterFile, String url, File newFile) throws IOException {
        update(updaterFile, getJarPath(), url, newFile, false);
    }

    public static void update(File updaterFile, String url, File newFile, boolean restart) throws IOException {
        update(updaterFile, getJarPath(), url, newFile, restart);
    }

    public static void update(File updaterFile, File oldFile, String url, File newFile, boolean restart) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

        ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", updaterFile.getAbsolutePath(), url, oldFile.getAbsolutePath(), newFile.getAbsolutePath(), restart ? "true" : "");

        builder.start();
    }

    public static boolean needUpdate(String version1, String version2) {
        return compareVersions(version1, version2) == -1;
    }

    public static int compareVersions(String version1, String version2){
        String[] levels1 = version1.split("\\.");
        String[] levels2 = version2.split("\\.");

        int length = Math.max(levels1.length, levels2.length);
        for (int i = 0; i < length; i++){
            Integer v1 = i < levels1.length ? Integer.parseInt(levels1[i]) : 0;
            Integer v2 = i < levels2.length ? Integer.parseInt(levels2[i]) : 0;
            int compare = v1.compareTo(v2);
            if (compare != 0){
                return compare;
            }
        }
        return 0;
    }

    public static File getJarPath() {
        if(jarPath == null) {
            try {
                return new File(UpdaterAPI.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsoluteFile();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }

        return jarPath;
    }
}
