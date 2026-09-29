package com.example.ibu_verification;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.ibu_verification.db.CertificateDbHelper;
import com.example.ibu_verification.db.CertificateRepository;
import com.example.ibu_verification.db.CertificateSeeder;
import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.CertificatePayloadCodec;
import com.example.ibu_verification.security.EncryptionHelper;
import com.example.ibu_verification.security.GateResult;
import com.example.ibu_verification.security.GateSequence;
import com.example.ibu_verification.security.HashUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class StoreUnreadableDiagnosticTest {

    private static final String GOOD_SECRET = "aaaa1111bbbb2222cccc3333dddd4444";
    private static final String OTHER_SECRET = "ffff9999eeee8888dddd7777cccc6666";
    private static final String SEED_ID = "CER1IBAU0001";

    private Context context;
    private CertificateDbHelper helper;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        helper = new CertificateDbHelper(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("DROP TABLE IF EXISTS " + CertificateDbHelper.TABLE);
        db.execSQL(CertificateDbHelper.CREATE_TABLE);
        db.execSQL(CertificateDbHelper.CREATE_HASH_INDEX);
    }

    private Certificate sample() {
        Certificate c = new Certificate();
        c.setCertificateId(SEED_ID);
        c.setStudentName("Asha Verma");
        c.setRollNumber("R21CS045");
        c.setEnrollmentNumber("ENU2021CS045");
        c.setCourseName("BSc Computer Science");
        c.setUniversityName("IBU");
        c.setPassingYear(2024);
        c.setPercentage("88.5");
        c.setCertificateType("Degree");
        return c;
    }

    private GateResult scan(CertificateRepository repo, String certificateId) {
        return new GateSequence(repo).evaluate(CertificatePayloadCodec.encode(certificateId), false);
    }

    private boolean rowFoundByHash(String certificateId) {
        Cursor cursor = helper.getReadableDatabase().query(
                CertificateDbHelper.TABLE, null,
                CertificateDbHelper.COL_HASH + " = ?",
                new String[]{HashUtil.certIdHash(certificateId)}, null, null, null, "1");
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    @Test
    public void seededRecords_carryTheNewUniversityName() throws Exception {
        new CertificateSeeder(new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET)))
                .seedIfEmpty();

        CertificateRepository reader = new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET));
        Certificate found = reader.findByCertificateId("CER1IBAU0001");
        assertNotNull(found);
        assertEquals("iB University", found.getUniversityName());
        assertEquals("Aarav Sharma", found.getStudentName());
    }

    @Test
    public void control_matchingSecret_scansSuccessfully() throws Exception {
        CertificateRepository repo = new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET));
        repo.insert(sample());
        GateResult result = scan(repo, SEED_ID);
        assertEquals("kind=" + result.getKind() + " reason=" + result.getReason()
                        + " id=" + result.getCertificateId(),
                GateResult.Kind.FOUND, result.getKind());
    }

    @Test
    public void masterSecretDriftAlone_reportsStoreUnreadable() throws Exception {
        CertificateRepository writer = new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET));
        writer.insert(sample());

        // The hash pepper is untouched, so the row is still located...
        assertTrue("row must still be found by hash", rowFoundByHash(SEED_ID));

        // ...but the installed app now presents a different master secret.
        CertificateRepository reader = new CertificateRepository(helper, new EncryptionHelper(OTHER_SECRET));
        assertEquals(GateResult.Reason.STORE_UNREADABLE, scan(reader, SEED_ID).getReason());
    }

    @Test
    public void hashPepperDriftAlone_reportsNotFound_notStoreUnreadable() throws Exception {
        CertificateRepository writer = new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET));
        writer.insert(sample());

        // With a rotated hash pepper the computed hash no longer matches any row,
        // so findByCertificateId returns null and the gate reports NOT_FOUND.
        String rotatedHash = HashUtil.certIdHash(SEED_ID, OTHER_SECRET);
        Cursor cursor = helper.getReadableDatabase().query(
                CertificateDbHelper.TABLE, null,
                CertificateDbHelper.COL_HASH + " = ?", new String[]{rotatedHash},
                null, null, null, "1");
        try {
            assertFalse("row must not be findable after hash pepper rotation", cursor.moveToFirst());
        } finally {
            cursor.close();
        }
    }

    @Test
    public void rowsSeededWhileSecretWasBlank_becomeStoreUnreadable() throws Exception {
        // First launch with a blank cert.master.secret: EncryptionHelper degrades to
        // plaintext, so the row is written unencrypted.
        assertFalse(new EncryptionHelper("").isActive());
        new CertificateRepository(helper, new EncryptionHelper("")).insert(sample());

        // Hash pepper never changed, so the row is still found...
        assertTrue(rowFoundByHash(SEED_ID));

        // ...and decrypting a plaintext string as a ciphertext blob fails.
        CertificateRepository reader = new CertificateRepository(helper, new EncryptionHelper(GOOD_SECRET));
        assertEquals(GateResult.Reason.STORE_UNREADABLE, scan(reader, SEED_ID).getReason());
    }
}
