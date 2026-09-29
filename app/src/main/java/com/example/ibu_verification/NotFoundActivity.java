package com.example.ibu_verification;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NotFoundActivity extends AppCompatActivity {

    public static final String EXTRA_CERTIFICATE_ID = "extra_certificate_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_not_found);
        setSupportActionBar(findViewById(R.id.toolbar));

        String certificateId = getIntent().getStringExtra(EXTRA_CERTIFICATE_ID);
        ((TextView) findViewById(R.id.textScannedId))
                .setText(certificateId == null ? "" : certificateId);

        findViewById(R.id.buttonTryAgain).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK);
                finish();
            }
        });
    }
}
