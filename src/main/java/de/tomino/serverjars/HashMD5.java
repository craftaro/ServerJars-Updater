package de.tomino.serverjars;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashMD5 {

    public static String get(Path path) throws IOException, NoSuchAlgorithmException {
        StringBuilder hash = new StringBuilder();

        for (byte aByte : MessageDigest.getInstance("MD5").digest(Files.readAllBytes(path))) {
            hash.append(Character.forDigit((aByte >> 4) & 0xF, 16));
            hash.append(Character.forDigit((aByte & 0xF), 16));
        }

        return hash.toString();
    }
}
