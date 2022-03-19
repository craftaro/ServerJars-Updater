package de.tomino.serverjars;

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.util.*

class Config(private val file: File) {
    private val properties: Properties

    init {
        properties = Properties()
        reset()
    }

    var type: String?
        get() = properties.getProperty("type", DEFAULT_TYPE)
        set(type) {
            properties.setProperty("type", type ?: DEFAULT_TYPE)
        }
    var version: String?
        get() = properties.getProperty("version", DEFAULT_VERSION)
        set(version) {
            properties.setProperty("version", version ?: DEFAULT_VERSION)
        }

    fun setJvmArgs(jvmArgs: String?) {
        properties.setProperty("jvmArgs", jvmArgs)
    }

    val jvmArgs: Array<String>
        get() {
            val args = properties.getProperty("jvmArgs", DEFAULT_JVM_ARGS).split(" -").toTypedArray()
            for (i in args.indices) {
                args[i] = (if (i != 0) "-" else "") + args[i].trim { it <= ' ' }
            }
            return args
        }
    val serverJarDirectory: String
        get() = properties.getProperty("MinecraftJarDirectory", DEFAULT_SERVER_JAR_DIRECTORY)

    fun shouldDeleteOtherJarVersions(): Boolean {
        return java.lang.Boolean.parseBoolean(properties.getProperty("deleteOtherMinecraftJarVersions", "true"))
    }

    fun setLastUpdateCheck(timestamp: Long) {
        properties.setProperty("updateCheck.last", timestamp.toString())
    }

    val lastUpdateCheck: Long?
        get() {
            try {
                return properties.getProperty("updateCheck.last").toLong()
            } catch (ignore: NumberFormatException) {
            }
            return null
        }
    var isUpdateAvailable: Boolean
        get() = java.lang.Boolean.parseBoolean(properties.getProperty("updateCheck.available"))
        set(isAvailable) {
            properties.setProperty("updateCheck.available", isAvailable.toString())
        }

    @Throws(IOException::class)
    fun load() {
        reset()
        if (file.exists()) {
            FileInputStream(file).use { `in` -> properties.load(`in`) }
        }
    }

    @Throws(IOException::class)
    fun save() {
        if (!file.parentFile.exists()) {
            Files.createDirectories(file.toPath().parent)
        }
        FileOutputStream(file).use { out -> properties.store(out, HEADER) }
    }

    fun reset() {
        properties.clear()
        type = DEFAULT_TYPE
        version = DEFAULT_VERSION
        setJvmArgs(DEFAULT_JVM_ARGS)
        properties.setProperty("MinecraftJarDirectory", DEFAULT_SERVER_JAR_DIRECTORY)
        properties.setProperty("deleteOtherMinecraftJarVersions", "true")
    }

    companion object {
        private const val HEADER = "Acceptable Versions (latest, 1.16.4, 1.8, etc...)"
        private const val DEFAULT_TYPE = "paper"
        private const val DEFAULT_VERSION = "latest"
        private const val DEFAULT_JVM_ARGS = "-Xmx1G"
        private const val DEFAULT_SERVER_JAR_DIRECTORY = "./jar"
    }
}
