package com.projects.eventvalidator;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ResultActivity extends AppCompatActivity {

    // IMPORTANT: Use http://10.0.2.2:3000/api/v1/scan for Android Emulator testing
    // Or your machine's local IP (e.g., http://192.168.1.x:3000) if testing on your physical Vivo phone.
    private static final String API_URL = "https://eventticketgenerator.onrender.com/api/v1/scan";

    private TextView tvScanStatus, tvAttendeeName, tvAttendeeEmail, tvTicketId, tvRawQrData;
    private ProgressBar progressBar;
    private LinearLayout attendeeInfoLayout;

    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // Initialize UI
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvAttendeeName = findViewById(R.id.tvAttendeeName);
        tvAttendeeEmail = findViewById(R.id.tvAttendeeEmail);
        tvTicketId = findViewById(R.id.tvTicketId);
        progressBar = findViewById(R.id.progressBar);
        attendeeInfoLayout = findViewById(R.id.attendeeInfoLayout);
        tvRawQrData = findViewById(R.id.tvRawQrData);

        client = new OkHttpClient();

        // Get the scanned data from the camera
        String qrPayload = getIntent().getStringExtra("QR_DATA");

        if (qrPayload != null) {
            // Display the raw QR data for debugging
            tvRawQrData.setText(qrPayload);

            // Call the backend with the exact slug you requested
            verifyTicketWithBackend(qrPayload, "hsm-developer-community-2026");
        } else {
            tvRawQrData.setText("ERROR: NULL");
            showError("No QR Code Data Found");
        }
    }

    private void verifyTicketWithBackend(String qrPayload, String eventSlug) {
        try {
            // 1. Build the exact JSON body your Fastify backend expects
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("qrPayload", qrPayload);
            jsonBody.put("eventSlug", eventSlug); // This will now be "hsm-developer-community-2026"

            // Log the outgoing payload to Android Studio's Logcat
            Log.d("API_REQUEST", "Sending: " + jsonBody.toString());

            RequestBody body = RequestBody.create(
                    jsonBody.toString(),
                    MediaType.get("application/json; charset=utf-8")
            );

            // 2. Create the POST Request
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .build();

            // 3. Send the API Call
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e("API_ERROR", "Network failure", e);
                    runOnUiThread(() -> showError("Network Error: Could not connect to server. Check API_URL."));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String responseData = response.body().string();

                    Log.d("API_RESPONSE", "Server returned: " + responseData);

                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);

                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);

                            if (response.isSuccessful()) {
                                // Check the status field from your backend
                                String status = jsonResponse.optString("status");

                                if ("valid".equals(status)) {
                                    showSuccess(jsonResponse);
                                } else {
                                    showError("Ticket Status: " + status);
                                }
                            } else {
                                // Handle backend errors (e.g., ticket not found, already checked in)
                                String errorMsg = jsonResponse.optString("error", "Unknown validation error");
                                showError("Scan Failed: " + errorMsg);
                            }
                        } catch (Exception e) {
                            Log.e("PARSE_ERROR", "Failed to parse JSON", e);
                            showError("Error parsing server response.");
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e("BUILD_ERROR", "Failed to build JSON request", e);
            showError("Error building request payload");
        }
    }

    private void showSuccess(JSONObject data) {
        tvScanStatus.setText("Scan Successful!");
        tvScanStatus.setTextColor(Color.parseColor("#4CAF50")); // Green

        attendeeInfoLayout.setVisibility(View.VISIBLE);
        tvAttendeeName.setText(data.optString("name", "N/A"));
        tvAttendeeEmail.setText(data.optString("email", "N/A"));
        tvTicketId.setText(data.optString("ticketId", "N/A"));
    }

    private void showError(String message) {
        progressBar.setVisibility(View.GONE);
        tvScanStatus.setText(message);
        tvScanStatus.setTextColor(Color.parseColor("#F44336")); // Red
        attendeeInfoLayout.setVisibility(View.GONE);
    }
}