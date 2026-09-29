package com.example.ibu_verification.security;

import com.example.ibu_verification.model.Certificate;

public class GateResult {

    public enum Kind {
        FOUND,
        NOT_FOUND,
        REFUSED,
        IGNORED
    }

    public enum Reason {
        NO_QR_DETECTED,
        SECURE_UNSUPPORTED,
        PLAIN_ID_REJECTED,
        NOT_A_CERTIFICATE,
        CODE_DAMAGED,
        STORE_UNREADABLE
    }

    private final Kind kind;
    private final Reason reason;
    private final String certificateId;
    private final Certificate certificate;

    private GateResult(Kind kind, Reason reason, String certificateId, Certificate certificate) {
        this.kind = kind;
        this.reason = reason;
        this.certificateId = certificateId;
        this.certificate = certificate;
    }

    public static GateResult found(String certificateId, Certificate certificate) {
        return new GateResult(Kind.FOUND, null, certificateId, certificate);
    }

    public static GateResult notFound(String certificateId) {
        return new GateResult(Kind.NOT_FOUND, null, certificateId, null);
    }

    public static GateResult refuse(Reason reason) {
        return new GateResult(Kind.REFUSED, reason, null, null);
    }

    public static GateResult ignored() {
        return new GateResult(Kind.IGNORED, null, null, null);
    }

    public Kind getKind() {
        return kind;
    }

    public Reason getReason() {
        return reason;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public Certificate getCertificate() {
        return certificate;
    }
}
