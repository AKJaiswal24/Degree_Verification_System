package com.example.ibu_verification;

import android.util.Base64;
import android.util.Log;

import com.example.ibu_verification.security.EncryptionHelper;

/**
 * Android-specific adapters that expose {@link android.util.Base64} and
 * {@link android.util.Log} to {@link EncryptionHelper}. The security class is
 * deliberately platform-agnostic so it can be reused by desktop tools and JVM
 * unit tests.
 */
public final class AndroidSecurityAdapters {

    public static final EncryptionHelper.Base64 BASE64 = new EncryptionHelper.Base64() {
        @Override
        public String encode(byte[] data) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        }

        @Override
        public byte[] decode(String text) {
            return Base64.decode(text, Base64.NO_WRAP);
        }
    };

    public static final EncryptionHelper.Logger LOGGER = new EncryptionHelper.Logger() {
        @Override
        public void error(String tag, String message) {
            Log.e(tag, message);
        }
    };

    private AndroidSecurityAdapters() {
    }
}