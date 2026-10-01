package com.example.ibu_verification;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // The certificate store is now seeded server-side by an issuer
        // (see the seed-cert Edge Function). There is nothing to seed on
        // first launch, so we simply wire the cloud repository for later
        // screens to consume.
        new AppGraph(this);

        findViewById(R.id.buttonScan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, ScannerActivity.class));
            }
        });
    }
}