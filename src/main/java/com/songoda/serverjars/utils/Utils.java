package com.songoda.serverjars.utils;

import com.google.gson.JsonObject;
import com.songoda.serverjars.ServerJars;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

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

    public static boolean downloadJar(String type, String version, File out, boolean downloading) {
        try {
            OnlineRequest.Response jarDetailsResponse = new OnlineRequest("https://serverjars.com/api/fetchDetails/" + type + "/" + version)
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
            try(BufferedInputStream inputStream = new BufferedInputStream(new URL("https://serverjars.com/api/fetchJar/" + type +  ((version == null || version.equals("latest")) ? "" : "/" + version)).openStream());
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

    public static JsonObject getJarDetails(String type, String version)  {
        try {
            String url = String.format("https://serverjars.com/api/fetchDetails/%s/%s", type, version);
            OnlineRequest.Response response = new OnlineRequest(url).connect();
            if(response.getStatusCode() != 200) {
                return null;
            }

            return response.responseAsJson().getAsJsonObject().getAsJsonObject("response");
        }catch (IOException e){
            Utils.debug(e);
            return null;
        }
    }
}
