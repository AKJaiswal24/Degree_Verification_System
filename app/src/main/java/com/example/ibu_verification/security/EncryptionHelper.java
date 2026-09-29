package com.example.ibu_verification.security;

import android.util.Log;

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

public class EncryptionHelper {

    public static final String TAG = "EncryptionHelper";
    public static final int SALT_LENGTH_BYTES = 16;
    public static final int IV_LENGTH_BYTES = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;
    public static final int PBKDF2_ITERATIONS = 10000;
    public static final int KEY_LENGTH_BITS = 256;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int MIN_BLOB_BYTES = SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16;

    private final SecureRandom random = new SecureRandom();
    private final char[] pepper;
    private final boolean active;

    public EncryptionHelper(String pepper) {
        this.pepper = pepper == null ? new char[0] : pepper.toCharArray();
        this.active = this.pepper.length > 0;
        if (!active) {
            Log.e(TAG, "Master secret is empty. Storing values as plain text.");
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
            return android.util.Base64.encodeToString(blob, android.util.Base64.NO_WRAP);
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
            byte[] blob = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP);
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

    public static final class EncryptionException extends Exception {
        public EncryptionException(String message) {
            super(message);
        }

        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
