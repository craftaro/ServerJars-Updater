package com.songoda.serverjars;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.songoda.serverjars.handlers.ConfigHandler;
import org.semver.Version;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class UpdateChecker {
    private static final String API_URL = "https://api.github.com/repos/ServerJars/Updater/releases/latest";
    private static final String DOWNLOAD_URL = "https://github.com/ServerJars/Updater/releases/latest";

    public UpdateChecker(ConfigHandler cfg) {
        if (cfg.isUpdateAvailable() &&
                // Recheck after some time just to be on the safe side
                cfg.getLastUpdateCheck() + TimeUnit.DAYS.toMillis(3) <= System.currentTimeMillis()) {
            announceUpdateAvailable();
        } else if (cfg.getLastUpdateCheck() == null ||
                cfg.getLastUpdateCheck() + TimeUnit.DAYS.toMillis(1) > System.currentTimeMillis()) {
            Thread thread = new Thread(() -> {
                try {
                    boolean isUpdateAvailable = this.checkForUpdates();

                    cfg.setLastUpdateCheck(System.currentTimeMillis());
                    cfg.setUpdateAvailable(isUpdateAvailable);

                    // Announce update availability on next launch or on error
                    try {
                        cfg.save();
                    } catch (IOException ex) {
                        System.err.println("Could not write UpdateCheck information to config file: " + ex.getMessage());

                        if (isUpdateAvailable) {
                            announceUpdateAvailable();
                        }
                    }
                } catch (IOException ignore) {
                    // Error when checking for latest version
                }
            }, "ServerJars-AppUpdateChecker");

            thread.setDaemon(true);
            thread.start();
        }
    }

    void announceUpdateAvailable() {
        System.err.println("\n= = = = = = = = = = =\n" +
                "A new version of ServerJars-Update is available!\n" +
                "Please download the new version from " + DOWNLOAD_URL + "\n" +
                "= = = = = = = = = = =\n");
    }

    boolean checkForUpdates() throws IOException {
        HttpsURLConnection con = (HttpsURLConnection) new URL(API_URL).openConnection();

        con.setRequestProperty("Accept", "application/vnd.github.v3+json");
        con.setRequestProperty("Content-Type", "application/json");

        con.setRequestProperty("User-Agent", "ServerJars-Updater/@version@(" +
                System.getProperty("os.name") + "; " +
                System.getProperty("os.arch") + ") (+https://github.com/ServerJars/Updater#readme)");

        con.setInstanceFollowRedirects(true);

        con.setConnectTimeout(10_000);
        con.connect();

        InputStream in = con.getErrorStream() == null ? con.getInputStream() : con.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

        if (con.getResponseCode() == 200) {
            JsonObject body = JsonParser.parseReader(reader).getAsJsonObject();

            String version = body.get("tag_name").getAsString();

            if (version.charAt(0) == 'v') {
                version = version.substring(1);
            }

            String current = "@version@";

            try {
                return Version.parse(version).compareTo(Version.parse(current.startsWith("v") ? current.substring(1) : current)) > 0;
            } catch (IllegalArgumentException ignore) {
                // One of the versions is not a valid SemVer
            }
        }

        return false;
    }
}
