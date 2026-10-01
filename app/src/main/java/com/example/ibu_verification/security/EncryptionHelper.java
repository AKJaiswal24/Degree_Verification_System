package com.example.ibu_verification.security;

import android.annotation.SuppressLint;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM field-level encryption with PBKDF2 key derivation.
 *
 * <p>This class deliberately references no Android types, so it can be compiled
 * and run on the desktop by the tools in {@code tools/}. The Android application
 * supplies {@link Base64} and {@link Logger} implementations that wrap
 * {@code android.util.Base64} and {@code android.util.Log}; the desktop tools
 * and JVM unit tests use the built-in JDK defaults.</p>
 */
public class EncryptionHelper {

    public static final String TAG = "EncryptionHelper";
    public static final int SALT_LENGTH_BYTES = 16;
    public static final int IV_LENGTH_BYTES = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int PBKDF2_ITERATIONS = 10000;
    public static final int KEY_LENGTH_BITS = 256;

    public interface Base64 {
        String encode(byte[] data);
        byte[] decode(String text);
    }

    public interface Logger {
        void error(String tag, String message);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int MIN_BLOB_BYTES = SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16;

    private final SecureRandom random = new SecureRandom();
    private final char[] pepper;
    private final boolean active;
    private final Base64 base64;
    private final Logger logger;

    public EncryptionHelper(String pepper, Base64 base64, Logger logger) {
        this.pepper = pepper == null ? new char[0] : pepper.toCharArray();
        this.active = this.pepper.length > 0;
        this.base64 = base64;
        this.logger = logger;
        if (!active) {
            logError("Master secret is empty. Storing values as plain text.");
        }
    }

    /**
     * Convenience constructor using JDK {@link java.util.Base64} and a
     * stderr logger. Suitable for desktop tools and JVM unit tests. The Android
     * application must use the three-argument constructor with
     * {@code AndroidSecurityAdapters} because {@code java.util.Base64} is only
     * available from API 26 and this app supports API 24.
     */
    public EncryptionHelper(String pepper) {
        this(pepper, new JdkBase64(), new JdkLogger());
    }

    private void logError(String message) {
        if (logger != null) {
            logger.error(TAG, message);
        }
    }

    public boolean isActive() {
        return active;
    }

    public String encrypt(String plainText) throws EncryptionException {
        if (plainText == null) {
            throw new IllegalArgumentException("Value to encrypt must not be null");
        }
        if (!active) {
            return plainText;
        }
        try {
            byte[] salt = new byte[SALT_LENGTH_BYTES];
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(salt);
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(UTF8));

            byte[] blob = new byte[salt.length + iv.length + cipherText.length];
            System.arraycopy(salt, 0, blob, 0, salt.length);
            System.arraycopy(iv, 0, blob, salt.length, iv.length);
            System.arraycopy(cipherText, 0, blob, salt.length + iv.length, cipherText.length);
            return base64.encode(blob);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Could not encrypt value: " + e.getMessage(), e);
        }
    }

    public String decrypt(String stored) throws EncryptionException {
        if (stored == null) {
            throw new IllegalArgumentException("Stored value must not be null");
        }
        if (!active) {
            return stored;
        }
        try {
            byte[] blob = base64.decode(stored);
            if (blob.length < MIN_BLOB_BYTES) {
                throw new EncryptionException("Stored value is too short to be encrypted");
            }
            byte[] salt = Arrays.copyOfRange(blob, 0, SALT_LENGTH_BYTES);
            byte[] iv = Arrays.copyOfRange(blob, SALT_LENGTH_BYTES, SALT_LENGTH_BYTES + IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(blob, SALT_LENGTH_BYTES + IV_LENGTH_BYTES, blob.length);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), UTF8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new EncryptionException("Could not decrypt value: " + e.getMessage(), e);
        }
    }

    private SecretKey deriveKey(byte[] salt) throws GeneralSecurityException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(pepper, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    /** JDK-provided defaults so the class also runs off-device. */
    @SuppressLint("NewApi")
    private static final class JdkBase64 implements Base64 {
        @Override
        public String encode(byte[] data) {
            return java.util.Base64.getEncoder().encodeToString(data);
        }

        @Override
        public byte[] decode(String text) {
            return java.util.Base64.getDecoder().decode(text.getBytes(UTF8));
        }
    }

    private static final class JdkLogger implements Logger {
        @Override
        public void error(String tag, String message) {
            System.err.println(tag + ": " + message);
        }
    }

    public static final class EncryptionException extends Exception {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}