package com.example.ibu_verification.api;

import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.EncryptionHelper;

/**
 * Boundary between the app and the certificate store.
 *
 * <p>Implementations live in the cloud package. The interface is deliberately
 * narrow: the only operation the verifier app needs is a lookup by certificate
 * ID. Writes are performed by an issuer through a separate privileged path and
 * are not exposed here.</p>
 */
public interface CertificateApi {

    /**
     * Looks up a certificate by its decoded certificate ID.
     *
     * @return the certificate, or {@code null} if no record matches.
     * @throws EncryptionHelper.EncryptionException if the store cannot be decrypted.
     * @throws NetworkException                   if the request fails.
     */
    Certificate findByCertificateId(String certificateId)
            throws EncryptionHelper.EncryptionException, NetworkException;

    /**
     * Thrown when a network request fails or the server rejects it. Distinguishable
     * from {@link EncryptionHelper.EncryptionException} so the gate sequence can
     * keep reporting {@code STORE_UNREADABLE} only for genuine decryption failures.
     */
    class NetworkException extends Exception {
        public NetworkException(String message) {
            super(message);
        }

        public NetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}