package com.example.ibu_verification.security;

import android.content.Context;

import com.example.ibu_verification.AndroidSecurityAdapters;
import com.example.ibu_verification.BuildConfig;

/**
 * Factory that produces an {@link EncryptionHelper} configured for the Android
 * application. The adapter is created once and shared so every repository and
 * API instance uses the same key derivation, salt/IV generation, and Base64
 * implementation.
 */
public final class AndroidEncryptionHelper {

    private AndroidEncryptionHelper() {
    }

    public static EncryptionHelper create(Context context) {
        return new EncryptionHelper(
                BuildConfig.CERT_MASTER_SECRET,
                AndroidSecurityAdapters.BASE64,
                AndroidSecurityAdapters.LOGGER);
    }
}