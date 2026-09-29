package tools;

import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoSelfTest {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int ITERATIONS = 10000;
    private static final int KEY_BITS = 256;

    public static void main(String[] args) throws Exception {
        String pepper = "self-test-pepper-value-32-bytes!!!";
        int failures = 0;

        failures += check("round trip returns the original text", roundTrip(pepper, "CER1IBAU0001"));
        failures += check("two encryptions of the same value differ", freshSaltPerValue(pepper));
        failures += check("a flipped ciphertext bit is rejected", tamperRejected(pepper));
        failures += check("a different pepper cannot decrypt", wrongPepperFails(pepper));
        failures += check("blob length equals salt+iv+text+tag", blobLayoutIsCorrect(pepper));

        System.out.println();
        System.out.println(failures == 0 ? "CRYPTO SELF TEST PASSED" : failures + " CHECK(S) FAILED");
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static int check(String name, boolean passed) {
        System.out.println((passed ? "PASS  " : "FAIL  ") + name);
        return passed ? 0 : 1;
    }

    private static boolean roundTrip(String pepper, String plain) {
        try {
            String blob = encrypt(pepper, plain);
            return plain.equals(decrypt(pepper, blob));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean freshSaltPerValue(String pepper) throws Exception {
        return !encrypt(pepper, "same").equals(encrypt(pepper, "same"));
    }

    private static boolean tamperRejected(String pepper) throws Exception {
        String blob = encrypt(pepper, "CER1IBAU0001");
        byte[] raw = Base64Codec.decode(blob);
        raw[raw.length - 1] ^= 0x01;
        try {
            decrypt(pepper, Base64Codec.encode(raw));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean wrongPepperFails(String pepper) throws Exception {
        String blob = encrypt(pepper, "CER1IBAU0001");
        try {
            decrypt(pepper + "x", blob);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean blobLayoutIsCorrect(String pepper) throws Exception {
        String plain = "CER1IBAU0001";
        byte[] raw = Base64Codec.decode(encrypt(pepper, plain));
        return raw.length == SALT_BYTES + IV_BYTES + plain.getBytes(UTF8).length + TAG_BITS / 8;
    }

    private static String encrypt(String pepper, String plain) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pepper, salt), new GCMParameterSpec(TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(UTF8));

        byte[] blob = new byte[salt.length + iv.length + cipherText.length];
        System.arraycopy(salt, 0, blob, 0, salt.length);
        System.arraycopy(iv, 0, blob, salt.length, iv.length);
        System.arraycopy(cipherText, 0, blob, salt.length + iv.length, cipherText.length);
        return Base64Codec.encode(blob);
    }

    private static String decrypt(String pepper, String blob) throws Exception {
        byte[] raw = Base64Codec.decode(blob);
        byte[] salt = Arrays.copyOfRange(raw, 0, SALT_BYTES);
        byte[] iv = Arrays.copyOfRange(raw, SALT_BYTES, SALT_BYTES + IV_BYTES);
        byte[] cipherText = Arrays.copyOfRange(raw, SALT_BYTES + IV_BYTES, raw.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pepper, salt), new GCMParameterSpec(TAG_BITS, iv));
        return new String(cipher.doFinal(cipherText), UTF8);
    }

    private static SecretKeySpec deriveKey(String pepper, byte[] salt) throws Exception {
        javax.crypto.SecretKeyFactory factory =
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pepper.toCharArray(), salt, ITERATIONS, KEY_BITS);
        byte[] key = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(key, "AES");
    }
}
