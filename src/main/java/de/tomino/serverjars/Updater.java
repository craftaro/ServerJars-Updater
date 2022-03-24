package de.tomino.serverjars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Updater {

    private static final String URL = "https://api.github.com/repos/TominoLP/Updater/releases/latest";

    private static String DOWNLOAD_URL;
    private static boolean needUpdate;

    private static final String CURRENT_VERSION = "1.0.0";

    public static void start() {
        UpdaterAPI.downloadUpdater(new File(getJarPath().getParentFile() + "/Updater.jar"));

        try {
            update();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void update() throws IOException {
        needUpdate = checkForUpdate(true);
        if(needUpdate) {
            System.out.println("New Version found! Restarting...");
            UpdaterAPI.update(DOWNLOAD_URL, new File(getJarPath().getParentFile().getAbsoluteFile() + "/" + getJarPath().getName()), true);
            System.exit(0);
        }

//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                if(needUpdate) UpdaterAPI.update(DOWNLOAD_URL, getJarPath().getParentFile().getAbsoluteFile(), true);
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//        }));
    }

    public static boolean checkForUpdate(boolean download) throws IOException {
        HttpURLConnection connect = (HttpURLConnection) new URL(URL).openConnection();

        connect.setConnectTimeout(10000);
        connect.connect();

        InputStream in = connect.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        if(connect.getResponseCode() == 200) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();

            String latestVersion = object.get("tag_name").getAsString();

            latestVersion = latestVersion.replace("v", "");

            if(latestVersion.toLowerCase().contains("pre-release"))
                return false;

            boolean needUpdate = compareVersions(CURRENT_VERSION, latestVersion) == -1;

            if(needUpdate && download) {
                String url = object.entrySet().stream().filter(e -> e.getKey().equals("assets")).map(Map.Entry::getValue).findFirst().orElseThrow(() -> new RuntimeException("Can not update system"))
                        .getAsJsonArray()
                        .get(0).getAsJsonObject().get("browser_download_url").getAsString();

                if(url == null) return false;

                System.out.println(url);
                DOWNLOAD_URL = url;
            }

            return needUpdate;
        }
        return false;
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
        return new File(Updater.class.getProtectionDomain().getCodeSource().getLocation().getPath());
    }
}
