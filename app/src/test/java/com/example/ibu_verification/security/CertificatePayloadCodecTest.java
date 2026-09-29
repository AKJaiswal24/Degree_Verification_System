package com.example.ibu_verification.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class CertificatePayloadCodecTest {

    private static final String SAMPLE_ID = "CER1IBAU0001";

    @Test
    public void roundTripReturnsOriginalId() throws Exception {
        String encoded = CertificatePayloadCodec.encode(SAMPLE_ID);
        assertEquals(SAMPLE_ID, CertificatePayloadCodec.decode(encoded));
    }

    @Test
    public void roundTripWorksForEverySeedId() throws Exception {
        String[] ids = {
                "CER1IBAU0001", "CER1IBAU0002", "CER1IBAU0003",
                "CER2IBAU0004", "CER2IBAU0005", "CER3IBAU0006",
                "CER3IBAU0007"
        };
        for (String id : ids) {
            assertEquals(id, CertificatePayloadCodec.decode(CertificatePayloadCodec.encode(id)));
        }
    }

    @Test
    public void encodedTextStartsWithMarkerAndUsesOnlyIbuAlphabet() {
        String encoded = CertificatePayloadCodec.encode(SAMPLE_ID);
        assertTrue(encoded.startsWith(CertificatePayloadCodec.MARKER));
        String body = encoded.substring(CertificatePayloadCodec.MARKER.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            boolean legal = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            assertTrue("Illegal character " + c, legal);
        }
    }

    @Test
    public void standardBase64AlphabetCharactersAreRejected() {
        String encoded = CertificatePayloadCodec.encode(SAMPLE_ID);
        String body = encoded.substring(CertificatePayloadCodec.MARKER.length());
        boolean sawPlusSlashOrEquals = body.indexOf('+') >= 0 || body.indexOf('/') >= 0
                || body.indexOf('=') >= 0;
        assertFalse("IBU body must not contain Base64-only characters", sawPlusSlashOrEquals);
    }

    @Test
    public void markerIsFoundInsideLongerText() throws Exception {
        String wrapped = "https://ibu.edu/verify?code=" + CertificatePayloadCodec.encode(SAMPLE_ID);
        assertEquals(SAMPLE_ID, CertificatePayloadCodec.decode(wrapped));
    }

    @Test
    public void singleCharacterEditIsRejected() {
        String encoded = CertificatePayloadCodec.encode(SAMPLE_ID);
        for (int i = CertificatePayloadCodec.MARKER.length(); i < encoded.length(); i++) {
            char[] chars = encoded.toCharArray();
            chars[i] = chars[i] == 'A' ? 'B' : 'A';
            String mutated = new String(chars);
            boolean rejected = false;
            try {
                String decoded = CertificatePayloadCodec.decode(mutated);
                rejected = !SAMPLE_ID.equals(decoded);
            } catch (CertificatePayloadCodec.PayloadException e) {
                rejected = true;
            }
            assertTrue("Edit at " + i + " was not detected", rejected);
        }
    }

    @Test
    public void twoCharacterSwapIsRejected() {
        String encoded = CertificatePayloadCodec.encode(SAMPLE_ID);
        int start = CertificatePayloadCodec.MARKER.length();
        for (int a = start; a < encoded.length() - 1; a++) {
            if (encoded.charAt(a) == encoded.charAt(a + 1)) {
                continue;
            }
            char[] chars = encoded.toCharArray();
            char temp = chars[a];
            chars[a] = chars[a + 1];
            chars[a + 1] = temp;
            String swapped = new String(chars);
            boolean rejected = false;
            try {
                String decoded = CertificatePayloadCodec.decode(swapped);
                rejected = !SAMPLE_ID.equals(decoded);
            } catch (CertificatePayloadCodec.PayloadException e) {
                rejected = true;
            }
            assertTrue("Swap at " + a + " was not detected", rejected);
        }
    }

    @Test
    public void missingMarkerIsRejected() {
        try {
            CertificatePayloadCodec.decode("CER1IBAU0001");
            fail("Expected PayloadException");
        } catch (CertificatePayloadCodec.PayloadException expected) {
            assertTrue(expected.getMessage().contains("Marker"));
        }
    }

    @Test
    public void encodeRejectsTextThatIsNotACertificateId() {
        try {
            CertificatePayloadCodec.encode("HELLO WORLD");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("does not match"));
        }
    }

    @Test
    public void illegalAlphabetCharacterIsRejected() {
        try {
            CertificatePayloadCodec.decode(CertificatePayloadCodec.MARKER + "ABC!DE");
            fail("Expected PayloadException");
        } catch (CertificatePayloadCodec.PayloadException expected) {
            assertTrue(expected.getMessage().contains("not in the IBU alphabet"));
        }
    }
}
