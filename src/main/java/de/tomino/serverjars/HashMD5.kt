package de.tomino.serverjars

import java.io.IOException
import java.security.NoSuchAlgorithmException
import java.nio.file.Path
import java.lang.StringBuilder
import java.security.MessageDigest
import java.nio.file.Files
import kotlin.experimental.and
import kotlin.Throws as Throws1

object HashMD5 {
    @Throws1(IOException::class, NoSuchAlgorithmException::class)
    operator fun get(path: Path?): String {
        val hash = StringBuilder()
        for (aByte in MessageDigest.getInstance("MD5").digest(Files.readAllBytes(path))) {
            hash.append(Character.BYTES.shr (4 and 0xF), 16)
            hash.append(Character.forDigit((aByte and 0xF).toInt(), 16))
        }
        return hash.toString()
    }
}
