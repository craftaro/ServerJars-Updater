package com.songoda.serverjars.objects;

import lombok.Getter;

public abstract class Option {

    @Getter
    private final String name, description;

    public Option(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void run(String data) {

    }

    public boolean isRequired() {
        return false;
    }

    /* This means that it'll only be executed if is the only argument used. Like --help, it won't be executed if we have `--mc.test --help` */
    public boolean isSingle() {
        return false;
    }
}
