package com.example.ibu_verification.security;

import com.example.ibu_verification.BuildConfig;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HashUtil() {
    }

    public static String certIdHash(String certificateId) {
        return certIdHash(certificateId, BuildConfig.CERT_HASH_PEPPER);
    }

    public static String certIdHash(String certificateId, String pepper) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(certificateId.getBytes(UTF8));
            digest.update(pepper.getBytes(UTF8));
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this device", e);
        }
    }

    static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }
}
