package com.songoda.serverjars.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import lombok.Getter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class OnlineRequest {

    @Getter
    private final String url;

    private final LinkedHashMap<String, Response> cachedResponse = new LinkedHashMap<>();
    private final LinkedHashMap<String, Long> timeCachedResponse = new LinkedHashMap<>();

    public OnlineRequest(String url) {
        this.url = url;
    }

    public Response connect() throws IOException {
        String key = this.getUrl();
        long time = this.timeCachedResponse.getOrDefault(key, 0L);
        if(!this.cachedResponse.containsKey(key) || (time == 0L || (System.currentTimeMillis() - time) < 15000L)) {
            URL url = new URL(this.getUrl());
            HttpURLConnection connection = ((HttpURLConnection) url.openConnection());
            Response response = new Response(connection);
            this.cachedResponse.put(key, response);
            this.timeCachedResponse.put(key, System.currentTimeMillis());
            return response;
        }

        return this.cachedResponse.get(key);
    }

    public static class Response {

        @Getter
        private final int statusCode;

        @Getter
        private final String statusMessage;

        @Getter
        private final String response;

        @Getter
        private final InputStream inputStream;

        public Response(HttpURLConnection connection) throws IOException {
            this.statusCode = connection.getResponseCode();
            this.statusMessage = connection.getResponseMessage();
            this.inputStream = connection.getInputStream();
            this.response = new BufferedReader(new InputStreamReader(this.inputStream)).lines().collect(Collectors.joining());
        }

        public JsonElement responseAsJson() {
            return JsonParser.parseString(this.response);
        }
    }
}
