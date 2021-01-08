package com.songoda.serverjars;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;

public class JarLoader extends URLClassLoader {

    static {
        registerAsParallelCapable();
    }

    public JarLoader(java.lang.ClassLoader parent) {
        super(new URL[0], parent);
    }

    public JarLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public static JarLoader findAncestor(java.lang.ClassLoader classLoader) {
        do {
            if (classLoader instanceof JarLoader)
                return (JarLoader) classLoader;
            classLoader = classLoader.getParent();
        } while (classLoader != null);

        return null;
    }

    void add(URL url) {
        addURL(url);
    }

    @SuppressWarnings("unused")
    private void appendToClassPathForInstrumentation(String jarfile) throws IOException {
        add(Paths.get(jarfile).toRealPath().toUri().toURL());
    }
}