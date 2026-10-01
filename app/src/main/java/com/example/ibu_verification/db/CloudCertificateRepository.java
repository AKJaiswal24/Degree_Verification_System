package com.example.ibu_verification.db;

import com.example.ibu_verification.api.CertificateApi;
import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.CertificateLookup;
import com.example.ibu_verification.security.EncryptionHelper;

/**
 * Cloud-backed implementation of {@link CertificateLookup}.
 *
 * <p>This replaces the former SQLite + AES-GCM-at-rest implementation. The
 * storage semantics are unchanged: every field is still encrypted with
 * AES-256-GCM before it leaves the device and decrypted after it returns, and
 * lookups still use a peppered SHA-256 hash. The difference is that the
 * encrypted rows and the hash index now live in Supabase, and the pepper is
 * applied server-side by an Edge Function so the client never holds it.</p>
 */
public class CloudCertificateRepository implements CertificateLookup {

    private final CertificateApi api;
    private final EncryptionHelper encryption;

    public CloudCertificateRepository(CertificateApi api, EncryptionHelper encryption) {
        this.api = api;
        this.encryption = encryption;
    }

    @Override
    public Certificate findByCertificateId(String certificateId)
            throws EncryptionHelper.EncryptionException {
        try {
            return api.findByCertificateId(certificateId);
        } catch (CertificateApi.NetworkException e) {
            // A network failure is not a store decryption failure. The gate
            // sequence only knows about EncryptionException, so wrap the
            // network error in one and let the UI report it as a transient
            // failure rather than "This device could not read its own store".
            throw new EncryptionHelper.EncryptionException(
                    "Certificate lookup failed over the network: " + e.getMessage(), e);
        }
    }

    public EncryptionHelper getEncryption() {
        return encryption;
    }
}