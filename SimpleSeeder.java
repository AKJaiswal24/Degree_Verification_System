// SimpleSeeder.java - compile with: javac SimpleSeeder.java && java SimpleSeeder
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Properties;
import java.io.FileInputStream;

public class SimpleSeeder {
    static final int SALT = 16, IV = 12, TAG = 128, ITER = 10000, KEY = 256;
    static final Charset UTF8 = Charset.forName("UTF-8");
    static final SecureRandom RAND = new SecureRandom();

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.load(new FileInputStream("encryption.properties"));
        String master = p.getProperty("cert.master.secret");
        String pepper = p.getProperty("cert.hash.pepper");

        String[][] records = {
            {"CER1IBAU0001","Aarav Sharma","IBU2026CS001","ENR20260001","B.Tech Computer Science","iB University","2026","88.40","Degree"},
            {"CER1IBAU0002","Diya Patel","IBU2026CS002","ENR20260002","B.Tech Computer Science","iB University","2026","91.20","Degree"},
            {"CER1IBAU0003","Rohan Verma","IBU2026EC003","ENR20260003","B.Tech Electronics","iB University","2026","74.75","Degree"},
            {"CER2IBAU0004","Ananya Iyer","IBU2025ME004","ENR20250004","B.Tech Mechanical","iB University","2025","82.10","Degree"},
            {"CER2IBAU0005","Kabir Singh","IBU2025BA005","ENR20250005","Bachelor of Arts","iB University","2025","69.30","Degree"},
            {"CER3IBAU0006","Meera Nair","IBU2024BS006","ENR20240006","Bachelor of Science","iB University","2024","77.85","Degree"},
            {"CER3IBAU0007","Arjun Rao","IBU2024BC007","ENR20240007","Bachelor of Commerce","iB University","2024","65.90","Degree"},
        };

        System.out.println("-- Copy these INSERT statements into Supabase SQL Editor:");
        for (String[] r : records) {
            String id = r[0];
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((id + pepper).getBytes(UTF8));
            String hashHex = bytesToHex(hash);

            System.out.println("INSERT INTO certificates (certificate_id, cert_id_hash, student_name, roll_number, enrollment_number, course_name, university_name, passing_year, percentage, certificate_type) VALUES");
            System.out.println("('" + encrypt(master, r[0]) + "',");
            System.out.println(" '" + hashHex + "',");
            System.out.println(" '" + encrypt(master, r[1]) + "',");
            System.out.println(" '" + encrypt(master, r[2]) + "',");
            System.out.println(" '" + encrypt(master, r[3]) + "',");
            System.out.println(" '" + encrypt(master, r[4]) + "',");
            System.out.println(" '" + encrypt(master, r[5]) + "',");
            System.out.println(" '" + encrypt(master, r[6]) + "',");
            System.out.println(" '" + encrypt(master, r[7]) + "',");
            System.out.println(" '" + encrypt(master, r[8]) + "');");
            System.out.println();
        }
    }

    static String encrypt(String secret, String plain) throws Exception {
        byte[] salt = new byte[16]; RAND.nextBytes(salt);
        byte[] iv = new byte[12]; RAND.nextBytes(iv);
        SecretKey key = derive(secret, salt);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes(UTF8));
        byte[] blob = new byte[16 + 12 + ct.length];
        System.arraycopy(salt, 0, blob, 0, 16);
        System.arraycopy(iv, 0, blob, 16, 12);
        System.arraycopy(ct, 0, blob, 28, ct.length);
        return Base64.getEncoder().encodeToString(blob);
    }

    static SecretKey derive(String secret, byte[] salt) throws Exception {
        var f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        var spec = new PBEKeySpec(secret.toCharArray(), salt, 10000, 256);
        return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
    }

    static String bytesToHex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte v : b) s.append(String.format("%02x", v));
        return s.toString();
    }
}