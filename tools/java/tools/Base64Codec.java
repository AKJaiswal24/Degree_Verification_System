package tools;

import java.nio.charset.Charset;
import java.util.Base64;

final class Base64Codec {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private Base64Codec() {
    }

    static String encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    static byte[] decode(String text) {
        return Base64.getDecoder().decode(text.getBytes(UTF8));
    }
}
