package com.example.ibu_verification.security;

import com.example.ibu_verification.model.Certificate;

public interface CertificateLookup {

    Certificate findByCertificateId(String certificateId) throws EncryptionHelper.EncryptionException;
}
