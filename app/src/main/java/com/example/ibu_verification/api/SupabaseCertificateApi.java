package com.example.ibu_verification.api;

import android.util.Log;

import com.example.ibu_verification.AndroidSecurityAdapters;
import com.example.ibu_verification.BuildConfig;
import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.EncryptionHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * {@link CertificateApi} backed by a Supabase project.
 *
 * <p>The verifier app only ever performs a lookup. The certificate ID is sent
 * to a server-side Edge Function ({@code lookup-cert}), which computes the
 * peppered SHA-256 hash using the pepper held on the server and queries the
 * {@code certificates} table. The app never receives the pepper and never
 * performs an unauthenticated read against the table directly.</p>
 *
 * <p>Fields are encrypted client-side with AES-256-GCM before they are stored
 * and decrypted after they are read, exactly as the local store did. Supabase
 * therefore sees only ciphertext and the lookup hash.</p */
public class SupabaseCertificateApi implements CertificateApi {

    private static final String TAG = "SupabaseCertificateApi";
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final MediaType JSON =
            MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final EncryptionHelper encryption;
    private final HttpUrl lookupUrl;

    public SupabaseCertificateApi(OkHttpClient client, EncryptionHelper encryption) {
        this.client = client;
        this.encryption = encryption;
        this.lookupUrl = HttpUrl.parse(BuildConfig.SUPABASE_URL)
                .newBuilder()
                .addPathSegment("functions")
                .addPathSegment("v1")
                .addPathSegment("lookup-cert")
                .build();
    }

    @Override
    public Certificate findByCertificateId(String certificateId)
            throws EncryptionHelper.EncryptionException, CertificateApi.NetworkException {
        if (certificateId == null || certificateId.isEmpty()) {
            return null;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("certificateId", certificateId);
        } catch (org.json.JSONException e) {
            throw new CertificateApi.NetworkException("Could not build request body", e);
        }

        Request request = new Request.Builder()
                .url(lookupUrl)
                .post(okhttp3.RequestBody.create(body.toString().getBytes(UTF8), JSON))
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new CertificateApi.NetworkException(
                        "Lookup failed with HTTP " + response.code());
            }
            if (response.body() == null) {
                return null;
            }
            JSONObject json = new JSONObject(response.body().string());
            if (json.has("error") && !json.isNull("error")) {
                throw new CertificateApi.NetworkException(
                        "Server reported an error: " + json.opt("error"));
            }
            if (!json.has("found") || !json.getBoolean("found")) {
                return null;
            }
            return readCertificate(json);
        } catch (IOException e) {
            throw new CertificateApi.NetworkException("Network request failed", e);
        } catch (org.json.JSONException e) {
            throw new CertificateApi.NetworkException("Could not parse server response", e);
        }
    }

    private Certificate readCertificate(JSONObject json)
            throws EncryptionHelper.EncryptionException, CertificateApi.NetworkException {
        JSONObject row = json.optJSONObject("row");
        if (row == null) {
            // Some Edge Function shapes return the fields at the top level.
            row = json;
        }
        Certificate certificate = new Certificate();
        certificate.setCertificateId(decrypt(row, "certificate_id"));
        certificate.setStudentName(decrypt(row, "student_name"));
        certificate.setRollNumber(decrypt(row, "roll_number"));
        certificate.setEnrollmentNumber(decrypt(row, "enrollment_number"));
        certificate.setCourseName(decrypt(row, "course_name"));
        certificate.setUniversityName(decrypt(row, "university_name"));
        try {
            certificate.setPassingYear(Integer.parseInt(decrypt(row, "passing_year")));
        } catch (NumberFormatException e) {
            throw new CertificateApi.NetworkException("Malformed passing year", e);
        }
        certificate.setPercentage(decrypt(row, "percentage"));
        certificate.setCertificateType(decrypt(row, "certificate_type"));
        return certificate;
    }

    private String decrypt(JSONObject row, String key)
            throws EncryptionHelper.EncryptionException, CertificateApi.NetworkException {
        try {
            if (!row.has(key) || row.isNull(key)) {
                throw new CertificateApi.NetworkException("Missing field " + key);
            }
            return encryption.decrypt(row.getString(key));
        } catch (org.json.JSONException e) {
            throw new CertificateApi.NetworkException("Missing or invalid field: " + key, e);
        }
    }
}