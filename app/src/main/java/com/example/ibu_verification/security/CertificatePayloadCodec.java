package com.example.ibu_verification.security;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class CertificatePayloadCodec {

    public static final String MARKER = "IBU1";
    public static final int BLOCK_SIZE = 5;
    public static final int MAX_PLAIN_LENGTH = 120;
    public static final int HEADER_GROUPS = 2;
    public static final int CHECKSUM_GROUPS = 1;

    public static final Pattern VALID_ID = Pattern.compile("^CER[0-9]IB[A-Z0-9]+U[A-Z0-9]+$");

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final int ALPHABET_SIZE = 64;

    private CertificatePayloadCodec() {
    }

    public static boolean hasMarker(String text) {
        return text != null && text.contains(MARKER);
    }

    public static String encode(String plainCertificateId) {
        if (plainCertificateId == null || plainCertificateId.isEmpty()) {
            throw new IllegalArgumentException("Certificate ID must not be empty");
        }
        if (!VALID_ID.matcher(plainCertificateId).matches()) {
            throw new IllegalArgumentException("Certificate ID does not match " + VALID_ID.pattern());
        }
        byte[] raw = plainCertificateId.getBytes(UTF8);
        if (raw.length > MAX_PLAIN_LENGTH) {
            throw new IllegalArgumentException("Certificate ID longer than " + MAX_PLAIN_LENGTH + " bytes");
        }

        List<Integer> values = new ArrayList<>();
        values.add((raw.length >> 6) & 0x3F);
        values.add(raw.length & 0x3F);
        for (byte b : raw) {
            int unsigned = b & 0xFF;
            values.add(unsigned & 0x3F);
            values.add((unsigned >> 6) << 4);
        }

        int padded = (values.size() + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE;
        while (values.size() < padded) {
            values.add(0);
        }

        List<Integer> scrambled = reverseBlocks(values, BLOCK_SIZE);
        int checksum = weightedChecksum(scrambled);

        StringBuilder builder = new StringBuilder();
        builder.append(MARKER);
        for (int value : scrambled) {
            builder.append(ALPHABET[value]);
        }
        builder.append(ALPHABET[checksum]);
        return builder.toString();
    }

    public static String decode(String text) throws PayloadException {
        int markerIndex = text == null ? -1 : text.indexOf(MARKER);
        if (markerIndex < 0) {
            throw new PayloadException("Marker " + MARKER + " is not present");
        }

        String body = text.substring(markerIndex + MARKER.length());
        if (body.length() < BLOCK_SIZE + CHECKSUM_GROUPS) {
            throw new PayloadException("Payload is too short");
        }
        if (body.length() > MAX_BODY_LENGTH + CHECKSUM_GROUPS) {
            throw new PayloadException("Payload is too long: " + body.length());
        }
        if (body.length() % BLOCK_SIZE != 1) {
            throw new PayloadException("Payload length is not a multiple of " + BLOCK_SIZE + " plus one checksum");
        }

        List<Integer> scrambled = new ArrayList<>();
        for (int i = 0; i < body.length() - 1; i++) {
            scrambled.add(alphabetIndex(body.charAt(i)));
        }
        int storedChecksum = alphabetIndex(body.charAt(body.length() - 1));

        List<Integer> values = reverseBlocks(scrambled, BLOCK_SIZE);

        int byteLength = (values.get(0) << 6) | values.get(1);
        if (byteLength <= 0 || byteLength > MAX_PLAIN_LENGTH) {
            throw new PayloadException("Declared length out of range: " + byteLength);
        }
        int expectedGroups = HEADER_GROUPS + byteLength * 2;
        int expectedPadded = (expectedGroups + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE;
        if (values.size() != expectedPadded) {
            throw new PayloadException("Declared length does not match payload size");
        }
        for (int i = expectedGroups; i < expectedPadded; i++) {
            if (values.get(i) != 0) {
                throw new PayloadException("Padding is not zero at position " + i);
            }
        }

        if (weightedChecksum(scrambled) != storedChecksum) {
            throw new PayloadException("Checksum does not match");
        }

        byte[] raw = new byte[byteLength];
        for (int i = 0; i < byteLength; i++) {
            int low = values.get(HEADER_GROUPS + i * 2);
            int top = values.get(HEADER_GROUPS + i * 2 + 1);
            if (top % 16 != 0) {
                throw new PayloadException("Invalid six bit pair at position " + i);
            }
            raw[i] = (byte) ((low & 0x3F) | (((top >> 4) & 0x03) << 6));
        }

        String plain = new String(raw, UTF8);
        if (!VALID_ID.matcher(plain).matches()) {
            throw new PayloadException("Decoded text is not a certificate ID");
        }
        return plain;
    }

    public static int maxBodyLength() {
        int groups = HEADER_GROUPS + MAX_PLAIN_LENGTH * 2;
        int padded = (groups + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE;
        return padded;
    }

    private static final int MAX_BODY_LENGTH = maxBodyLength();

    static int weightedChecksum(List<Integer> scrambled) {
        int sum = 0;
        for (int i = 0; i < scrambled.size(); i++) {
            sum = (sum + (scrambled.get(i) + 1) * (i + 1)) % ALPHABET_SIZE;
        }
        return sum;
    }

    static List<Integer> reverseBlocks(List<Integer> values, int blockSize) {
        List<Integer> out = new ArrayList<>(values.size());
        for (int start = 0; start < values.size(); start += blockSize) {
            int end = Math.min(start + blockSize, values.size());
            for (int i = end - 1; i >= start; i--) {
                out.add(values.get(i));
            }
        }
        return out;
    }

    static int alphabetIndex(char c) throws PayloadException {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 26;
        }
        if (c >= '0' && c <= '9') {
            return c - '0' + 52;
        }
        if (c == '-') {
            return 62;
        }
        if (c == '_') {
            return 63;
        }
        throw new PayloadException("Character '" + c + "' is not in the IBU alphabet");
    }

    public static final class PayloadException extends Exception {
        public PayloadException(String message) {
            super(message);
        }
    }
}
