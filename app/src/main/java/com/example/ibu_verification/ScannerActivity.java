package com.example.ibu_verification;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ibu_verification.api.CertificateApi;
import com.example.ibu_verification.api.SupabaseCertificateApi;
import com.example.ibu_verification.db.CloudCertificateRepository;
import com.example.ibu_verification.model.Certificate;
import com.example.ibu_verification.security.AndroidEncryptionHelper;
import com.example.ibu_verification.security.CertificateLookup;
import com.example.ibu_verification.security.EncryptionHelper;
import com.example.ibu_verification.security.GateResult;
import com.example.ibu_verification.security.GateSequence;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public class ScannerActivity extends AppCompatActivity {

    private static final int CAMERA_REQUEST = 41;
    private static final int RESULT_REQUEST = 42;

    private DecoratedBarcodeView barcodeView;
    private TextView bannerRefusal;
    private ProgressBar progressChecking;
    private ImageButton torchButton;

    private CaptureManager captureManager;
    private ExecutorService worker;
    private GateSequence gates;

    private boolean resumed;
    private boolean resultOpened;
    private boolean torchOn;
    private boolean waitingForLookup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        barcodeView = findViewById(R.id.barcodeView);
        bannerRefusal = findViewById(R.id.bannerRefusal);
        progressChecking = findViewById(R.id.progressChecking);
        torchButton = findViewById(R.id.buttonTorch);

        List<BarcodeFormat> formats = new ArrayList<>();
        formats.add(BarcodeFormat.QR_CODE);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        barcodeView.setDecoderFactory(new DefaultDecoderFactory(formats, hints, null, 0));

        captureManager = new CaptureManager(this, barcodeView);
        captureManager.initializeFromIntent(getIntent(), null);

        worker = Executors.newSingleThreadExecutor();

        OkHttpClient client = new OkHttpClient();
        EncryptionHelper encryption = AndroidEncryptionHelper.create(this);
        CertificateApi api = new SupabaseCertificateApi(client, encryption);
        final CertificateLookup lookup = new CloudCertificateRepository(api, encryption);
        gates = new GateSequence(lookup);

        torchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTorch();
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        if (torchOn) {
            barcodeView.setTorchOn();
            torchButton.setContentDescription(getString(R.string.scanner_torch_off));
        } else {
            barcodeView.setTorchOff();
            torchButton.setContentDescription(getString(R.string.scanner_torch_on));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_REQUEST) {
            return;
        }
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            barcodeView.setVisibility(View.GONE);
            showRefusal(getString(R.string.scanner_permission_denied));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (captureManager != null) {
            captureManager.onResume();
        }
        startScanning();
    }

    @Override
    protected void onPause() {
        if (captureManager != null) {
            captureManager.onPause();
        }
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (captureManager != null) {
            captureManager.onDestroy();
        }
        if (worker != null) {
            worker.shutdownNow();
        }
        super.onDestroy();
    }

    private void startScanning() {
        if (barcodeView.getVisibility() != View.VISIBLE || resultOpened || waitingForLookup) {
            return;
        }
        barcodeView.resume();
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null || result.getText() == null || result.getText().isEmpty()) {
                    return;
                }
                barcodeView.pause();
                runGates(result.getText());
            }
        });
    }

    private void runGates(final String scannedText) {
        waitingForLookup = true;
        progressChecking.setVisibility(View.VISIBLE);
        worker.execute(new Runnable() {
            @Override
            public void run() {
                final GateResult result = gates.evaluate(scannedText, resultOpened);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onGateFinished(result);
                    }
                });
            }
        });
    }

    private void onGateFinished(GateResult result) {
        progressChecking.setVisibility(View.GONE);

        if (result.getKind() == GateResult.Kind.IGNORED) {
            waitingForLookup = false;
            return;
        }

        if (result.getKind() == GateResult.Kind.REFUSED) {
            waitingForLookup = false;
            showRefusal(messageFor(result.getReason()));
            return;
        }

        openResult(result);
    }

    private String messageFor(GateResult.Reason reason) {
        if (reason == null) {
            return "";
        }
        switch (reason) {
            case NO_QR_DETECTED:
                return getString(R.string.gate_no_qr);
            case SECURE_UNSUPPORTED:
                return getString(R.string.gate_secure_unsupported);
            case PLAIN_ID_REJECTED:
                return getString(R.string.gate_plain_id);
            case NOT_A_CERTIFICATE:
                return getString(R.string.gate_not_certificate);
            case CODE_DAMAGED:
                return getString(R.string.gate_damaged);
            case STORE_UNREADABLE:
                return getString(R.string.gate_store_unreadable);
            default:
                return "";
        }
    }

    private void openResult(GateResult result) {
        if (resultOpened) {
            return;
        }

        resultOpened = true;
        waitingForLookup = true;
        barcodeView.pause();
        barcodeView.setTorchOff();

        Intent intent;
        if (result.getKind() == GateResult.Kind.FOUND) {
            intent = new Intent(this, VerificationActivity.class);
            intent.putExtra(VerificationActivity.EXTRA_CERTIFICATE, (Certificate) result.getCertificate());
        } else {
            intent = new Intent(this, NotFoundActivity.class);
            intent.putExtra(NotFoundActivity.EXTRA_CERTIFICATE_ID, result.getCertificateId());
        }
        startActivityForResult(intent, RESULT_REQUEST);
    }

    private void showRefusal(String message) {
        if (!resumed) {
            return;
        }
        bannerRefusal.setText(message);
        bannerRefusal.setVisibility(View.VISIBLE);
        startScanning();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RESULT_REQUEST) {
            return;
        }
        resultOpened = false;
        waitingForLookup = false;
        bannerRefusal.setVisibility(View.GONE);
        startScanning();
    }
}