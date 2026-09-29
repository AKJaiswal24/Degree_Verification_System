package com.example.ibu_verification;

import android.content.Context;

import com.example.ibu_verification.db.CertificateDbHelper;
import com.example.ibu_verification.db.CertificateRepository;
import com.example.ibu_verification.db.CertificateSeeder;
import com.example.ibu_verification.security.EncryptionHelper;

public class AppGraph {

    private final CertificateDbHelper dbHelper;
    private final EncryptionHelper encryption;
    private final CertificateRepository repository;

    public AppGraph(Context context) {
        dbHelper = new CertificateDbHelper(context);
        encryption = new EncryptionHelper(BuildConfig.CERT_MASTER_SECRET);
        repository = new CertificateRepository(dbHelper, encryption);
    }

    public CertificateRepository repository() {
        return repository;
    }

    public void seedOnFirstLaunch() {
        try {
            new CertificateSeeder(repository).seedIfEmpty();
        } catch (EncryptionHelper.EncryptionException e) {
            android.util.Log.e("AppGraph", "Seeding failed", e);
        }
    }
}
