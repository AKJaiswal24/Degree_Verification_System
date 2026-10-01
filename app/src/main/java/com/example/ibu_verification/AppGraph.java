package com.example.ibu_verification;

import android.content.Context;

import com.example.ibu_verification.api.CertificateApi;
import com.example.ibu_verification.api.SupabaseCertificateApi;
import com.example.ibu_verification.db.CloudCertificateRepository;
import com.example.ibu_verification.security.AndroidEncryptionHelper;
import com.example.ibu_verification.security.EncryptionHelper;

import okhttp3.OkHttpClient;

public class AppGraph {

    private final CloudCertificateRepository repository;

    public AppGraph(Context context) {
        OkHttpClient client = new OkHttpClient();
        EncryptionHelper encryption = AndroidEncryptionHelper.create(context);
        CertificateApi api = new SupabaseCertificateApi(client, encryption);
        repository = new CloudCertificateRepository(api, encryption);
    }

    public CloudCertificateRepository repository() {
        return repository;
    }
}