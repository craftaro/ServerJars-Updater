package com.songoda.serverjars;

import com.google.gson.annotations.SerializedName;

public class ServerJar {

    private String version;
    @SerializedName("file")
    private String fileName;
    @SerializedName("md5")
    private String md5Hash;
    @SerializedName("built")
    private String buildDate;

    public String getVersion() {
        return version;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getMd5Hash() {
        return fromHex(md5Hash);
    }

    public String getBuildDate() {
        return buildDate;
    }

    private static byte[] fromHex(final String s) {
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex " + s + " must be divisible by two");
        }
        final byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            final char left = s.charAt(i * 2);
            final char right = s.charAt(i * 2 + 1);
            final byte b = (byte) ((getValue(left) << 4) | (getValue(right) & 0xF));
            bytes[i] = b;
        }
        return bytes;
    }

    private static int getValue(final char c) {
        int i = Character.digit(c, 16);
        if (i < 0) {
            throw new IllegalArgumentException("Invalid hex char: " + c);
        }
        return i;
    }
}
