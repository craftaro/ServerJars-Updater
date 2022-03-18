package de.tomino.serverjars;

import java.net.URL;
import java.net.URLClassLoader;

@Deprecated
public class JarLoader extends URLClassLoader {
    static {
        registerAsParallelCapable();

        System.err.println("You are using '" + JarLoader.class.getName() + "' which is no longer required - Please start ServerJars without specifying it as it is subject to be removed in future updates.");
    }

    public JarLoader(java.lang.ClassLoader parent) {
        super(new URL[0], parent);
    }
}
