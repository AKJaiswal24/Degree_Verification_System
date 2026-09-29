package tools;

import com.example.ibu_verification.security.CertificatePayloadCodec;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

public class QrFixtureGenerator {

    private static final int SIZE = 480;
    private static final int QUIET_ZONE = 4;

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "sample_qr_codes");
        Path encodedDir = root.resolve("encoded");
        Path plainDir = root.resolve("plain");
        Files.createDirectories(encodedDir);
        Files.createDirectories(plainDir);

        int failures = 0;
        for (String id : SampleRecords.IDS) {
            String payload = CertificatePayloadCodec.encode(id);
            failures += runChecks(id, payload);
            writePng(encodedDir.resolve(safeName(id) + ".png"), payload);
            System.out.println("encoded  " + id + "  ->  " + payload);
        }

        for (String plain : SampleRecords.PLAIN_FIXTURES) {
            writePng(plainDir.resolve(safeName(plain) + ".png"), plain);
            System.out.println("plain    " + plain);
        }

        for (String secure : SampleRecords.SECURE_FIXTURES) {
            writePng(plainDir.resolve("secure_" + safeName(secure) + ".png"), secure);
            System.out.println("secure   " + secure);
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static int runChecks(String originalId, String payload)
            throws CertificatePayloadCodec.PayloadException {
        int failures = 0;
        if (!payload.startsWith(CertificatePayloadCodec.MARKER)) {
            System.out.println("  FAIL check 1: payload has no marker");
            failures++;
        } else {
            String body = payload.substring(CertificatePayloadCodec.MARKER.length());
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                boolean legal = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9') || c == '-' || c == '_';
                if (!legal) {
                    System.out.println("  FAIL check 1: illegal alphabet character " + c);
                    failures++;
                    break;
                }
            }
        }

        String decoded = CertificatePayloadCodec.decode(payload);
        if (originalId.equals(decoded)) {
            System.out.println("  pass check 2: decodes back to " + originalId);
        } else {
            System.out.println("  FAIL check 2: decoded " + decoded);
            failures++;
        }

        char[] edited = payload.toCharArray();
        int editAt = CertificatePayloadCodec.MARKER.length() + 2;
        edited[editAt] = edited[editAt] == 'A' ? 'B' : 'A';
        if (isRejected(new String(edited), originalId)) {
            System.out.println("  pass check 3: single character edit rejected");
        } else {
            System.out.println("  FAIL check 3: single character edit was accepted");
            failures++;
        }

        char[] swapped = payload.toCharArray();
        int swapAt = CertificatePayloadCodec.MARKER.length();
        if (swapped[swapAt] != swapped[swapAt + 1]) {
            char temp = swapped[swapAt];
            swapped[swapAt] = swapped[swapAt + 1];
            swapped[swapAt + 1] = temp;
            if (isRejected(new String(swapped), originalId)) {
                System.out.println("  pass check 4: two character swap rejected");
            } else {
                System.out.println("  FAIL check 4: two character swap was accepted");
                failures++;
            }
        }

        return failures;
    }

    private static boolean isRejected(String candidate, String originalId) {
        try {
            return !originalId.equals(CertificatePayloadCodec.decode(candidate));
        } catch (CertificatePayloadCodec.PayloadException | IllegalArgumentException e) {
            return true;
        }
    }

    private static String safeName(String text) {
        StringBuilder builder = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static void writePng(Path target, String text) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QUIET_ZONE);

        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        ImageIO.write(image, "png", target.toFile());
    }
}
