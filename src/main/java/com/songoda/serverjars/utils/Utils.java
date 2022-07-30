package com.songoda.serverjars.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.songoda.serverjars.ServerJars;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.io.FileUtils;
import org.semver.Version;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Utils {

    public static JsonObject loadGlobalConfig(){
        try {
            File file = new File(ServerJars.HOME_DIR, "config.json");
            String content;
            if(!file.exists()){
                file.createNewFile();
                content = "{}";
            } else {
                content = FileUtils.readFileToString(file, Charset.defaultCharset());
                if(content.isEmpty()){
                    content = "{}";
                }
            }

            return JsonParser.parseString(content).getAsJsonObject();
        }catch(IOException e){
            Utils.debug(e);
        }
        return new JsonObject();
    }

    public static void updateGlobalConfig(Consumer<JsonObject> consumer){
        try {
            File file = new File(ServerJars.HOME_DIR, "config.json");
            if(!file.exists()) file.createNewFile();
            JsonObject json = Utils.loadGlobalConfig();
            consumer.accept(json);
            FileUtils.writeStringToFile(file, json.toString(), Charset.defaultCharset());
        }catch(IOException e){
            Utils.debug(e);
        }
    }

    public static String regexFromGlob(String glob) {
        StringBuilder out = new StringBuilder("^");
        for(int i = 0; i < glob.length(); ++i) {
            final char c = glob.charAt(i);
            switch(c) {
                case '*': out.append(".*"); break;
                case '?': out.append('.'); break;
                case '.': out.append("\\."); break;
                case '\\': out.append("\\\\"); break;
                default: out.append(c);
            }
        }
        out.append('$');
        return out.toString();
    }

    public static List<String> fetchCategories() {
        try {
            OnlineRequest.Response jarCategoriesResponse = new OnlineRequest(Utils.generateUrl("api/fetchTypes"))
                .connect();
            if(jarCategoriesResponse.getStatusCode() == 200){
                return jarCategoriesResponse.responseAsJson()
                    .getAsJsonObject()
                    .get("response")
                    .getAsJsonObject()
                    .entrySet()
                    .stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            }
        }catch(IOException e){
            Utils.debug(e);
        }
        return null;
    }

    public static List<String> fetchTypes(String category) {
        try {
            OnlineRequest.Response typesResponse = new OnlineRequest(Utils.generateUrl(String.format("api/fetchTypes/%s", category)))
                .connect();
            if(typesResponse.getStatusCode() == 200){
                List<String> types = new ArrayList<>();
                typesResponse.responseAsJson().getAsJsonObject().get("response").getAsJsonObject().getAsJsonArray(category).forEach(it -> types.add(it.getAsString()));
                return types;
            }
        }catch (IOException e){
            Utils.debug(e);
        }
        return null;
    }

    public static boolean downloadJar(String category, String type, String version, File out, boolean downloading) {
        try {
            JsonObject jarDetails = Utils.getJarDetails(category, type, version);
            if(jarDetails == null) {
                System.out.println("Error while fetching the jar details! Please try again later.");
                return false;
            }

            // 1048576 = 1 mib
            int bytes = jarDetails.getAsJsonObject("response").getAsJsonObject("size").get("bytes").getAsInt();
            int bufferSize = 1024;
            ProgressBarBuilder progressBarBuilder = new ProgressBarBuilder()
                .setTaskName(String.format("%s %s (%s)", downloading ? "Downloading" : "Updating", type, version))
                .setUnit("MiB", bufferSize)
                .setInitialMax(bytes/bufferSize)
                .showSpeed()
                .setStyle(ProgressBarStyle.UNICODE_BLOCK)
                .continuousUpdate();
            try(BufferedInputStream inputStream = new BufferedInputStream(new URL(Utils.generateUrl(String.format("api/fetchJar/%s/%s%s", category, type, ((version == null || version.equals("latest")) ? "" : "/" + version)))).openStream());
            FileOutputStream outputStream = new FileOutputStream(out);
            ProgressBar progressBar = progressBarBuilder.build()) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while((bytesRead = inputStream.read(buffer, 0, bufferSize)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    progressBar.step();
                }
            }
            return true;
        }catch (IOException e){
            Utils.debug(e);
        }
        return false;
    }

    public static boolean bool(String value) {
        return value.equalsIgnoreCase("1") || value.equalsIgnoreCase("y") || value.equalsIgnoreCase("true");
    }

    public static void debug(String message, Object... args){
        Utils.debug(String.format(message, args));
    }

    public static void debug(String message) {
        if(ServerJars.config.isDebug()){
            System.out.println("DEBUG: " + message);
        }
    }

    public static void debug(Throwable throwable){
        if(ServerJars.config.isDebug()){
            StringBuilder builder = new StringBuilder();
            builder.append(throwable.getClass().getName()).append(": ").append(throwable.getMessage() != null ? throwable.getMessage() : "null").append("\n");
            for (StackTraceElement ste : throwable.getStackTrace()) {
                builder.append("\tat ").append(ste.getClassName()).append(".").append(ste.getMethodName()).append("(").append(ste.getFileName()).append(":").append(ste.getLineNumber()).append(")").append("\n");
            }
            debug(builder.toString());
        }
    }

    public static File folder(File file) {
        if(!file.exists())
            file.mkdirs();
        return file;
    }

    public static File folder(String path) {
        return folder(new File(path));
    }

    public static boolean isWindows() {
        return (System.getProperty("os") != null ? System.getProperty("os") : "").toLowerCase().contains("windows");
    }

    public static void updateCache() {
        try {
            Utils.debug("Updating cache for current settings...");
            File file = new File(ServerJars.HOME_DIR, "cache.json");
            String content;
            if(!file.exists()) {
                file.createNewFile();
                content = "{}";
            } else {
                content = FileUtils.readFileToString(file, Charset.defaultCharset());
            }
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            String category = ServerJars.config.getCategory(), type = ServerJars.config.getType(), version = ServerJars.config.getVersion();
            JsonObject categoryJson = getOrCreateJsonObject(json, category);
            JsonObject typeJson = getOrCreateJsonObject(categoryJson, type);
            // Fetch the latest detail
            JsonObject detail = Utils.getJarDetails(category, type, version, true);
            if(detail != null){
                typeJson.add(detail.get("version").getAsString(), detail);
            }

            categoryJson.add(type, typeJson);
            json.add(category, categoryJson);
            FileUtils.writeStringToFile(file, json.toString(), Charset.defaultCharset());
        } catch (Exception e){
            System.err.println("There was an error while building the cache. Please run in debug mode for more information.");
            Utils.debug(e);
        }
    }

    public static void updateCachedDetails(String category, String type, String version, JsonObject data){
        try {
            Utils.debug("Updating cache for " + type + " (" + category + ") version " + version + "...");
            File file = new File(ServerJars.HOME_DIR, "cache.json");
            String content;
            if(!file.exists()) {
                file.createNewFile();
                content = "{}";
            } else {
                content = FileUtils.readFileToString(file, Charset.defaultCharset());
            }
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            JsonObject categoryJson = getOrCreateJsonObject(json, category);
            JsonObject typesJson = getOrCreateJsonObject(categoryJson, type);
            typesJson.add(version, data);
            categoryJson.add(type, typesJson);
            json.add(category, categoryJson);
            FileUtils.writeStringToFile(file, json.toString(), Charset.defaultCharset());
            Utils.debug("Cache updated!");
        }catch(Exception e){
            Utils.debug("Failed to update cached details for " + type + " (" + category + ") version " + version + ":");
            Utils.debug(e);
        }
    }

    public static JsonObject detailsFromCache(String category, String type, String version) {
        try {
            Utils.debug("Fetching details from cache for " + type + " (" + category + ") version " + version + "...");
            File file = new File(ServerJars.HOME_DIR, "cache.json");
            if(!file.exists()) {
                file.createNewFile();
                FileUtils.writeStringToFile(file, "{}", Charset.defaultCharset());
            }
            JsonObject json = getOrCreateJsonObject(getOrCreateJsonObject(JsonParser.parseString(FileUtils.readFileToString(file, Charset.defaultCharset())).getAsJsonObject(), category), type);
            JsonElement el = null;
            if(!version.equalsIgnoreCase("latest")){
                el = json.get(version);
            } else { // Parse the latest version
                // Get object keys
                HashMap<Long, String> versions = new HashMap<>();
                json.entrySet().forEach(it -> versions.put(it.getValue().getAsJsonObject().get("built").getAsLong(), it.getKey()));
                if(category.equalsIgnoreCase("vanilla") && type.equalsIgnoreCase("snapshot")) { // Due to the versioning name with snapshot we need to filter by built time
                    long lastBuildTime = versions.keySet().stream().min(Comparator.comparingLong(a -> a)).orElse(0L);
                    if(versions.containsKey(lastBuildTime)) {
                        el = json.get(versions.get(lastBuildTime));
                    }
                } else { // If is not vanilla snapshot, just filter by version name
                    String latestVersion;
                    if((latestVersion = versions.values().stream().min(Comparator.comparing(Version::parse)).orElse(null)) != null){
                        el = json.get(latestVersion);

                    }
                }
            }


            if(el != null){
                if(!el.isJsonNull()) {
                    Utils.debug("Found cached details for version %s.", el.getAsJsonObject().get("version").getAsString());
                    return el.getAsJsonObject();
                }
            }
        }catch (Exception e){
            Utils.debug(e);
        }
        return null;
    }

    public static JsonObject getJarDetails(String category, String type, String version, boolean skipCache)  {
        try {
            Utils.debug("Fetching details...");
            long now = System.currentTimeMillis();
            String url = Utils.generateUrl(String.format("api/fetchDetails/%s/%s/%s", category, type, version));
            JsonObject json = getOrCreateJsonObject(loadGlobalConfig(), "detailsRequestTime");
            long lastRequest = json.has(url) ? json.get(url).getAsLong() : 0L;
            if(now - lastRequest < 120000 && !skipCache) { // To avoid rate limiting, and also improve performance, if it's less than 2 minutes we try to use cached details
                JsonObject cached = detailsFromCache(category, type, version);
                if(cached != null){
                    return cached;
                }
            }
            OnlineRequest.Response response = new OnlineRequest(url).connect();
            if(response.getStatusCode() == 200) {
                JsonObject details = response.responseAsJson().getAsJsonObject().getAsJsonObject("response");
                Utils.updateCachedDetails(category, type, version, details);
                updateGlobalConfig(config -> {
                    JsonObject timings = getOrCreateJsonObject(config, "detailsRequestTime");
                    timings.addProperty(url, now);
                    config.add("detailsRequestTime", timings);
                });
                return details;
            }
        }catch (IOException e){
            Utils.debug(e);
        }

        return null;
    }

    public static JsonObject getJarDetails(String category, String type, String version)  {
        return getJarDetails(category, type, version, false);
    }

    public static String generateUrl(String path) {
        return String.format("%s://%s/%s", ServerJars.config.isUseHttps() ? "https" : "http", ServerJars.config.getApiDomain(), path);
    }

    public static JsonObject getOrCreateJsonObject(JsonObject parent, String key){
        if(!parent.has(key)) {
            parent.add(key, new JsonObject());
        }
        return parent.getAsJsonObject(key);
    }

    public static JsonArray getOrCreateArray(JsonObject parent, String key) {
        if(!parent.has(key)) {
            parent.add(key, new JsonArray());
        }
        return parent.getAsJsonArray(key);
    }
}
