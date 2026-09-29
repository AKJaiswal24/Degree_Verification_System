package com.example.ibu_verification;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ibu_verification.model.Certificate;

import java.util.Date;

public class VerificationActivity extends AppCompatActivity {

    public static final String EXTRA_CERTIFICATE = "extra_certificate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verification);
        setSupportActionBar(findViewById(R.id.toolbar));

        Certificate certificate = (Certificate) getIntent().getSerializableExtra(EXTRA_CERTIFICATE);
        if (certificate == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.valueStudentName)).setText(certificate.getStudentName());
        ((TextView) findViewById(R.id.valueRollNumber)).setText(certificate.getRollNumber());
        ((TextView) findViewById(R.id.valueEnrollmentNumber)).setText(certificate.getEnrollmentNumber());
        ((TextView) findViewById(R.id.valueCourseName)).setText(certificate.getCourseName());
        ((TextView) findViewById(R.id.valueUniversityName)).setText(certificate.getUniversityName());
        ((TextView) findViewById(R.id.valuePassingYear)).setText(String.valueOf(certificate.getPassingYear()));
        ((TextView) findViewById(R.id.valuePercentage)).setText(certificate.getPercentage());
        ((TextView) findViewById(R.id.valueCertificateType)).setText(certificate.getCertificateType());

        String stamp = DateFormat.getMediumDateFormat(this).format(new Date())
                + ", " + DateFormat.getTimeFormat(this).format(new Date());
        ((TextView) findViewById(R.id.textVerifiedOn))
                .setText(getString(R.string.verification_verified_on, stamp));

        findViewById(R.id.buttonScanAnother).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });

        findViewById(R.id.buttonGoHome).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent home = new Intent(VerificationActivity.this, HomeActivity.class);
                home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(home);
                finish();
            }
        });
    }
}
