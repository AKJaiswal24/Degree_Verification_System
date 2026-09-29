package com.example.ibu_verification.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.ibu_verification.model.Certificate;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class GateSequenceTest {

    private static final String KNOWN = "CER1IBAU0001";
    private static final String UNKNOWN = "CER1IBAU9999";

    private Set<String> store;
    private int lookups;
    private GateSequence gates;

    @Before
    public void setUp() {
        store = new HashSet<>();
        store.add(KNOWN);
        lookups = 0;
        gates = new GateSequence(new CertificateLookup() {
            @Override
            public Certificate findByCertificateId(String certificateId) {
                lookups++;
                if (!store.contains(certificateId)) {
                    return null;
                }
                Certificate certificate = new Certificate();
                certificate.setCertificateId(certificateId);
                certificate.setStudentName("Aarav Sharma");
                return certificate;
            }
        });
    }

    @Test
    public void gate0bIgnoresEverythingWhenResultIsOpening() {
        assertEquals(GateResult.Kind.IGNORED, gates.evaluate(CertificatePayloadCodec.encode(KNOWN), true).getKind());
        assertEquals(0, lookups);
    }

    @Test
    public void gate0RejectsEmptyAndBlank() {
        assertEquals(GateResult.Kind.REFUSED, gates.evaluate(null, false).getKind());
        assertEquals(GateResult.Kind.REFUSED, gates.evaluate("", false).getKind());
        assertEquals(GateResult.Kind.REFUSED, gates.evaluate("   ", false).getKind());
        assertEquals(0, lookups);
    }

    @Test
    public void gate1RejectsSecurePayload() {
        GateResult result = gates.evaluate("SQR1AAAAABBBBB", false);
        assertEquals(GateResult.Kind.REFUSED, result.getKind());
        assertTrue(result.getReason() == GateResult.Reason.SECURE_UNSUPPORTED);
        assertEquals(0, lookups);
    }

    @Test
    public void gate2RejectsPlainCertificateIdWithItsOwnMessage() {
        GateResult result = gates.evaluate(KNOWN, false);
        assertEquals(GateResult.Kind.REFUSED, result.getKind());
        assertTrue(result.getReason() == GateResult.Reason.PLAIN_ID_REJECTED);
        assertEquals(0, lookups);
    }

    @Test
    public void gate2RejectsForeignPayloadWithDifferentMessage() {
        GateResult url = gates.evaluate("https://example.com/wifi?S=abc", false);
        assertTrue(url.getReason() == GateResult.Reason.NOT_A_CERTIFICATE);
        assertEquals(0, lookups);
    }

    @Test
    public void gate3RejectsDamagedCode() {
        String encoded = CertificatePayloadCodec.encode(KNOWN);
        char[] chars = encoded.toCharArray();
        chars[CertificatePayloadCodec.MARKER.length() + 3] =
                chars[CertificatePayloadCodec.MARKER.length() + 3] == 'A' ? 'B' : 'A';
        GateResult result = gates.evaluate(new String(chars), false);
        assertEquals(GateResult.Kind.REFUSED, result.getKind());
        assertTrue(result.getReason() == GateResult.Reason.CODE_DAMAGED);
        assertEquals(0, lookups);
    }

    @Test
    public void gate4FindsKnownCertificate() {
        GateResult result = gates.evaluate(CertificatePayloadCodec.encode(KNOWN), false);
        assertEquals(GateResult.Kind.FOUND, result.getKind());
        assertEquals(KNOWN, result.getCertificateId());
        assertEquals("Aarav Sharma", result.getCertificate().getStudentName());
    }

    @Test
    public void gate4ReportsNotFoundForUnknownId() {
        GateResult result = gates.evaluate(CertificatePayloadCodec.encode(UNKNOWN), false);
        assertEquals(GateResult.Kind.NOT_FOUND, result.getKind());
        assertEquals(UNKNOWN, result.getCertificateId());
        assertNull(result.getCertificate());
    }

    @Test
    public void onlyGate4ReachesTheLookup() {
        gates.evaluate(null, false);
        gates.evaluate("", false);
        gates.evaluate("SQR1ZZZZZ", false);
        gates.evaluate(KNOWN, false);
        gates.evaluate("https://example.com", false);
        assertEquals(0, lookups);
    }
}
