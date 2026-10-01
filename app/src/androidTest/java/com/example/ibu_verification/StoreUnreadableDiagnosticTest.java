package com.example.ibu_verification;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ibu_verification.api.CertificateApi;
import com.example.ibu_verification.api.CertificateApi.NetworkException;
import com.example.ibu_verification.db.CloudCertificateRepository;
import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.CertificatePayloadCodec;
import com.example.ibu_verification.security.EncryptionHelper;
import com.example.ibu_verification.security.GateResult;
import com.example.ibu_verification.security.GateSequence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class StoreUnreadableDiagnosticTest {

    private static final String GOOD_SECRET = "aaaa1111bbbb2222cccc3333dddd4444";
    private static final String OTHER_SECRET = "ffff9999eeee8888dddd7777cccc6666";

    private MockCertificateApi api;
    private EncryptionHelper goodEnc, otherEnc, blankEnc;

    @Before
    public void setUp() {
        api = new MockCertificateApi();
        goodEnc = new EncryptionHelper(GOOD_SECRET);
        otherEnc = new EncryptionHelper(OTHER_SECRET);
        blankEnc = new EncryptionHelper("");
    }

    private Certificate makeCertificate(String id, String name) {
        Certificate c = new Certificate();
        c.setCertificateId(id);
        c.setStudentName(name);
        c.setRollNumber("R21CS045");
        c.setEnrollmentNumber("ENU2021CS045");
        c.setCourseName("BSc Computer Science");
        c.setUniversityName("IBU");
        c.setPassingYear(2024);
        c.setPercentage("88.5");
        c.setCertificateType("Degree");
        return c;
    }

    private GateResult scan(CloudCertificateRepository repo, String certificateId) {
        return new GateSequence(repo).evaluate(CertificatePayloadCodec.encode(certificateId), false);
    }

    @Test
    public void control_matchingSecret_scansSuccessfully() throws Exception {
        // API returns a valid Certificate (simulating successful server response)
        Certificate c = makeCertificate("CER1IBAU0001", "Asha Verma");
        api.setRecord("CER1IBAU0001", c);

        CloudCertificateRepository repo = new CloudCertificateRepository(api, goodEnc);
        GateResult result = scan(repo, "CER1IBAU0001");

        assertEquals("kind=" + result.getKind() + " reason=" + result.getReason()
                        + " id=" + result.getCertificateId(),
                GateResult.Kind.FOUND, result.getKind());
        assertEquals("Asha Verma", result.getCertificate().getStudentName());
    }

    @Test
    public void masterSecretDrift_reportsStoreUnreadable() throws Exception {
        // API simulates a decryption failure (wrong master secret)
        api.setError("CER1IBAU0001", new EncryptionHelper.EncryptionException("Wrong key"));

        CloudCertificateRepository repo = new CloudCertificateRepository(api, goodEnc);
        GateResult result = scan(repo, "CER1IBAU0001");

        assertEquals(GateResult.Reason.STORE_UNREADABLE, result.getReason());
    }

    @Test
    public void blankSecret_thenRealSecret_reportsStoreUnreadable() throws Exception {
        // API simulates a decryption failure (blank secret was used to encrypt)
        api.setError("CER1IBAU0001", new EncryptionHelper.EncryptionException("Blank secret"));

        CloudCertificateRepository repo = new CloudCertificateRepository(api, goodEnc);
        assertEquals(GateResult.Reason.STORE_UNREADABLE, scan(repo, "CER1IBAU0001").getReason());
    }

    @Test
    public void unknownCertificate_returnsNotFound() {
        CloudCertificateRepository repo = new CloudCertificateRepository(api, goodEnc);
        GateResult result = scan(repo, "UNKNOWN_ID");

        assertEquals(GateResult.Kind.NOT_FOUND, result.getKind());
        assertEquals("UNKNOWN_ID", result.getCertificateId());
        assertNull(result.getCertificate());
    }

    @Test
    public void networkError_reportsStoreUnreadable() {
        // API simulates network failure
        api.setNetworkError("CER1IBAU0001");

        CloudCertificateRepository repo = new CloudCertificateRepository(api, goodEnc);
        GateResult result = scan(repo, "CER1IBAU0001");

        // Network errors are wrapped as EncryptionException -> STORE_UNREADABLE
        assertEquals(GateResult.Reason.STORE_UNREADABLE, result.getReason());
    }

    // --- Mock API for testing ---

    static class MockCertificateApi implements CertificateApi {
        private final java.util.Map<String, Certificate> records = new java.util.HashMap<>();
        private final java.util.Map<String, Exception> errors = new java.util.HashMap<>();
        private boolean networkError = false;

        void setRecord(String id, Certificate c) {
            records.put(id, c);
        }

        void setError(String id, Exception e) {
            errors.put(id, e);
        }

        void setNetworkError(String id) {
            errors.put(id, new NetworkException("Network failure"));
        }

        @Override
        public Certificate findByCertificateId(String certificateId)
                throws EncryptionHelper.EncryptionException, NetworkException {
            if (errors.containsKey(certificateId)) {
                Exception e = errors.get(certificateId);
                if (e instanceof NetworkException) throw (NetworkException) e;
                if (e instanceof EncryptionHelper.EncryptionException) throw (EncryptionHelper.EncryptionException) e;
                throw new EncryptionHelper.EncryptionException(e.getMessage(), e);
            }
            return records.get(certificateId);
        }
    }
}