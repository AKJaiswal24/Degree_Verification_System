package com.example.ibu_verification.security;

import com.example.ibu_verification.model.Certificate;

public class GateSequence {

    public static final String MARKER_IBU1 = "IBU1";
    public static final String MARKER_SECURE = "SQR1";

    private final CertificateLookup lookup;

    public GateSequence(CertificateLookup lookup) {
        this.lookup = lookup;
    }

    public GateResult evaluate(String scannedText, boolean resultAlreadyOpen) {
        if (resultAlreadyOpen) {
            return GateResult.ignored();
        }

        if (scannedText == null || scannedText.trim().isEmpty()) {
            return GateResult.refuse(GateResult.Reason.NO_QR_DETECTED);
        }

        if (scannedText.contains(MARKER_SECURE)) {
            return GateResult.refuse(GateResult.Reason.SECURE_UNSUPPORTED);
        }

        if (!scannedText.contains(MARKER_IBU1)) {
            if (looksLikePlainCertificateId(scannedText)) {
                return GateResult.refuse(GateResult.Reason.PLAIN_ID_REJECTED);
            }
            return GateResult.refuse(GateResult.Reason.NOT_A_CERTIFICATE);
        }

        String certificateId;
        try {
            certificateId = CertificatePayloadCodec.decode(scannedText);
        } catch (CertificatePayloadCodec.PayloadException e) {
            return GateResult.refuse(GateResult.Reason.CODE_DAMAGED);
        }

        return gateFour(certificateId);
    }

    private GateResult gateFour(String certificateId) {
        try {
            Certificate certificate = lookup.findByCertificateId(certificateId);
            if (certificate == null) {
                return GateResult.notFound(certificateId);
            }
            return GateResult.found(certificateId, certificate);
        } catch (EncryptionHelper.EncryptionException e) {
            return GateResult.refuse(GateResult.Reason.STORE_UNREADABLE);
        }
    }

    private boolean looksLikePlainCertificateId(String text) {
        String trimmed = text.trim();
        return CertificatePayloadCodec.VALID_ID.matcher(trimmed).matches()
                || trimmed.matches("^CER[0-9][A-Z0-9]{4,}$");
    }
}
