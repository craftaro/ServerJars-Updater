package com.songoda.serverjars;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Response {

    private String status;

    @SerializedName("response")
    private LinkedList<ServerJar> serverJars;

    public String getStatus() {
        return status;
    }

    public List<ServerJar> getServerJars() {
        return Collections.unmodifiableList(serverJars);
    }
}
