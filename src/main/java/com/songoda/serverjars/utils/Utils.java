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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

public class Utils {

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
            OnlineRequest.Response jarDetailsResponse = new OnlineRequest(Utils.generateUrl(String.format("api/fetchDetails/%s/%s/%s", category, type, version)))
                    .connect();
            String message = jarDetailsResponse.getStatusCode() == 400 ? "It seems like the provided server type or version could not be found." : (jarDetailsResponse.getStatusCode() == 500 ? "Internal server error. Please try again later" : jarDetailsResponse.getStatusMessage());
            if(jarDetailsResponse.getStatusCode() != 200) {
                System.out.println("Error while fetching the jar details: " + message);
                return false;
            }
            JsonObject jarDetails = jarDetailsResponse.responseAsJson().getAsJsonObject();

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

    public static JsonObject getJarDetails(String category, String type, String version)  {
        try {
            Utils.debug("Fetching details...");
            String url = Utils.generateUrl(String.format("api/fetchDetails/%s/%s/%s", category, type, version));
            OnlineRequest.Response response = new OnlineRequest(url).connect();
            if(response.getStatusCode() == 200) {
                return response.responseAsJson().getAsJsonObject().getAsJsonObject("response");
            }
        }catch (IOException e){
            Utils.debug(e);
        }

        return null;
    }

    public static String generateUrl(String path) {
        return String.format("%s://%s/%s", ServerJars.config.isUseHttps() ? "https" : "http", ServerJars.config.getApiDomain(), path);
    }

    public static void updateCache() {
        try {
            Utils.debug("Updating cache...");
            File file = new File(ServerJars.HOME_DIR, "cache.json");
            if(!file.exists()) file.createNewFile();
            String content = FileUtils.readFileToString(file, Charset.defaultCharset());
            JsonObject json = content.isEmpty() ? new JsonObject() : JsonParser.parseString(content).getAsJsonObject();
            String category = ServerJars.config.getCategory(), type = ServerJars.config.getType(), version = ServerJars.config.getVersion();
            JsonObject categoryJson = getOrCreateJsonObject(json, category);
            JsonObject typeJson = getOrCreateJsonObject(categoryJson, type);
            // Fetch the latest detail
            JsonObject detail = Utils.getJarDetails(category, type, version);
            if(detail != null){
                typeJson.add(detail.get("version").getAsString(), detail);
            }

            categoryJson.add(type, typeJson);
            json.add(category, categoryJson);
            FileUtils.writeStringToFile(file, json.toString(), Charset.defaultCharset());
        }catch (Exception e){
            System.err.println("There was an error while building the cache. Please run in debug mode for more information.");
            Utils.debug(e);
        }
    }

    public static JsonObject detailsFromCache(String category, String type, String version) {
        try {
            Utils.debug("Fetching details from cache...");
            File file = new File(ServerJars.HOME_DIR, "cache.json");
            JsonObject json = getOrCreateJsonObject(getOrCreateJsonObject(JsonParser.parseString(FileUtils.readFileToString(file, Charset.defaultCharset())).getAsJsonObject(), category), type);
            JsonJ el = null;
            if(!version.equalsIgnoreCase("latest")){
                el = json.get(version);
            } else {
                // Get object keys
                HashMap<Long, String> versions = new HashMap<>();
                json.entrySet()
                    .stream()
                    .forEach(it -> versions.put(it.getKey(), it.getValue().getAsJsonObject().get("built").getAsLong()));
                if(category.equalsIgnoreCase("vanilla") && type.equalsIgnoreCase("snapshot")) {

                } else {

                }
            }


            if(el != null){
                if(!el.isJsonNull()) {
                    return el.getAsJsonObject();
                }
            }
        }catch (Exception e){
            Utils.debug(e);
        }
        return null;
    }

    private static JsonObject getOrCreateJsonObject(JsonObject parent, String key){
        if(!parent.has(key)) {
            parent.add(key, new JsonObject());
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonArray getOrCreateArray(JsonObject parent, String key) {
        if(!parent.has(key)) {
            parent.add(key, new JsonArray());
        }
        return parent.getAsJsonArray(key);
    }
}
