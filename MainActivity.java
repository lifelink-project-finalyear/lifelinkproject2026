package com.example.lifelink;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final int LOC_PERMISSION_CODE = 1001;

    // Replace with your laptop LAN IP where sver.js runs
    private static final String SERVER_URL = "http://192.168.1.10:3000/location/update";

    private static final String CASE_ID = "CASE_1023";
    private static final String PATIENT_ID = "PAT_88";
    private static final String DISEASE_TYPE = "Cardiac";

    private LocationManager locationManager;
    private LocationListener locationListener;

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        btnStart.setOnClickListener(v -> checkPermissionAndStart());
        btnStop.setOnClickListener(v -> stopTracking());

        locationListener = location -> {
            double lat = location.getLatitude();
            double lng = location.getLongitude();
            runOnUiThread(() -> tvStatus.setText("Sharing: " + lat + ", " + lng));
            sendLocationToServer(lat, lng);
        };
    }

    private void checkPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOC_PERMISSION_CODE
            );
            return;
        }
        startTracking();
    }

    private void startTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                5,
                locationListener
        );

        tvStatus.setText("Live sharing started");
    }

    private void stopTracking() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            tvStatus.setText("Live sharing stopped");
        }
    }

    private void sendLocationToServer(double lat, double lng) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(SERVER_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);

                JSONObject body = new JSONObject();
                body.put("caseId", CASE_ID);
                body.put("patientId", PATIENT_ID);
                body.put("diseaseType", DISEASE_TYPE);
                body.put("lat", lat);
                body.put("lng", lng);
                body.put("timestamp", System.currentTimeMillis());

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                runOnUiThread(() -> tvStatus.setText("Sent update (HTTP " + code + ")"));
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Send failed: " + e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOC_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        } else {
            tvStatus.setText("Location permission denied");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTracking();
    }
}
