package com.example.ibu_verification.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.CertificateLookup;
import com.example.ibu_verification.security.EncryptionHelper;
import com.example.ibu_verification.security.HashUtil;

public class CertificateRepository implements CertificateLookup {

    private final CertificateDbHelper helper;
    private final EncryptionHelper encryption;

    public CertificateRepository(CertificateDbHelper helper, EncryptionHelper encryption) {
        this.helper = helper;
        this.encryption = encryption;
    }

    @Override
    public Certificate findByCertificateId(String certificateId) throws EncryptionHelper.EncryptionException {
        String hash = HashUtil.certIdHash(certificateId);
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(
                CertificateDbHelper.TABLE,
                null,
                CertificateDbHelper.COL_HASH + " = ?",
                new String[]{hash},
                null, null, null, "1");
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readRow(cursor);
        } finally {
            cursor.close();
        }
    }

    public long insert(Certificate certificate) throws EncryptionHelper.EncryptionException {
        ContentValues values = new ContentValues();
        values.put(CertificateDbHelper.COL_ID, encryption.encrypt(certificate.getCertificateId()));
        values.put(CertificateDbHelper.COL_HASH, HashUtil.certIdHash(certificate.getCertificateId()));
        values.put(CertificateDbHelper.COL_NAME, encryption.encrypt(certificate.getStudentName()));
        values.put(CertificateDbHelper.COL_ROLL, encryption.encrypt(certificate.getRollNumber()));
        values.put(CertificateDbHelper.COL_ENROLLMENT, encryption.encrypt(certificate.getEnrollmentNumber()));
        values.put(CertificateDbHelper.COL_COURSE, encryption.encrypt(certificate.getCourseName()));
        values.put(CertificateDbHelper.COL_UNIVERSITY, encryption.encrypt(certificate.getUniversityName()));
        values.put(CertificateDbHelper.COL_YEAR, encryption.encrypt(String.valueOf(certificate.getPassingYear())));
        values.put(CertificateDbHelper.COL_PERCENTAGE, encryption.encrypt(certificate.getPercentage()));
        values.put(CertificateDbHelper.COL_TYPE, encryption.encrypt(certificate.getCertificateType()));
        return helper.getWritableDatabase().insert(CertificateDbHelper.TABLE, null, values);
    }

    public boolean isEmpty() {
        Cursor cursor = helper.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + CertificateDbHelper.TABLE, null);
        try {
            return cursor.moveToFirst() && cursor.getInt(0) == 0;
        } finally {
            cursor.close();
        }
    }

    private Certificate readRow(Cursor cursor) throws EncryptionHelper.EncryptionException {
        Certificate certificate = new Certificate();
        certificate.setCertificateId(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_ID))));
        certificate.setStudentName(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_NAME))));
        certificate.setRollNumber(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_ROLL))));
        certificate.setEnrollmentNumber(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_ENROLLMENT))));
        certificate.setCourseName(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_COURSE))));
        certificate.setUniversityName(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_UNIVERSITY))));
        certificate.setPassingYear(Integer.parseInt(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_YEAR)))));
        certificate.setPercentage(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_PERCENTAGE))));
        certificate.setCertificateType(encryption.decrypt(
                cursor.getString(cursor.getColumnIndexOrThrow(CertificateDbHelper.COL_TYPE))));
        return certificate;
    }
}
