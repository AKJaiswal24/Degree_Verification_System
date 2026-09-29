package com.example.ibu_verification.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class CertificateDbHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "ibu_certificates.db";
    public static final int DATABASE_VERSION = 2;

    public static final String TABLE = "certificates";
    public static final String COL_PK = "id";
    public static final String COL_ID = "certificate_id";
    public static final String COL_HASH = "cert_id_hash";
    public static final String COL_NAME = "student_name";
    public static final String COL_ROLL = "roll_number";
    public static final String COL_ENROLLMENT = "enrollment_number";
    public static final String COL_COURSE = "course_name";
    public static final String COL_UNIVERSITY = "university_name";
    public static final String COL_YEAR = "passing_year";
    public static final String COL_PERCENTAGE = "percentage";
    public static final String COL_TYPE = "certificate_type";

    public static final String CREATE_TABLE =
            "CREATE TABLE " + TABLE + " ("
                    + COL_PK + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + COL_ID + " TEXT NOT NULL, "
                    + COL_HASH + " TEXT NOT NULL, "
                    + COL_NAME + " TEXT NOT NULL, "
                    + COL_ROLL + " TEXT NOT NULL, "
                    + COL_ENROLLMENT + " TEXT NOT NULL, "
                    + COL_COURSE + " TEXT NOT NULL, "
                    + COL_UNIVERSITY + " TEXT NOT NULL, "
                    + COL_YEAR + " TEXT NOT NULL, "
                    + COL_PERCENTAGE + " TEXT NOT NULL, "
                    + COL_TYPE + " TEXT NOT NULL)";

    public static final String CREATE_HASH_INDEX =
            "CREATE UNIQUE INDEX idx_cert_hash ON " + TABLE + " (" + COL_HASH + ")";

    public CertificateDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_HASH_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        for (int version = oldVersion + 1; version <= newVersion; version++) {
            if (version == 2) {
                rebuildEncryptedTable(db);
            }
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    private void rebuildEncryptedTable(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE + " RENAME TO " + TABLE + "_old");
        db.execSQL(CREATE_TABLE);
        db.execSQL(CREATE_HASH_INDEX);
        db.execSQL("DROP TABLE " + TABLE + "_old");
    }
}
