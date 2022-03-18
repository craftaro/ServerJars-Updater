package de.tomino.serverjars

import java.io.File
import java.util.Properties
import java.io.IOException
import java.io.FileInputStream
import java.nio.file.Files
import java.io.FileOutputStream

class Config(private val file: File) {
    private val properties: Properties = Properties()

    init {
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

    private fun reset() {
        properties.clear()
        properties.setProperty("type", DEFAULT_TYPE)
        properties.setProperty("version", DEFAULT_VERSION)
    }

    companion object {
        private const val HEADER = "Acceptable Versions (latest, 1.16.4, 1.8, etc...)"
        private const val DEFAULT_TYPE = "paper"
        private const val DEFAULT_VERSION = "latest"
    }
}
