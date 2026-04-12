package com.projects.eventvalidator;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

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

    private TextView tvScanStatus, tvAttendeeName, tvAttendeeEmail, tvTicketId, tvRawQrData, tvSelectedEventType;
    private ProgressBar progressBar;
    private LinearLayout attendeeInfoLayout;
    private MaterialButton btnAcceptAttendance;

    private OkHttpClient client;
    private String selectedEventType;
    private GoogleSheetsService.AttendeeRecord verifiedAttendee;
    private boolean isSubmittingAttendance;

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
        tvSelectedEventType = findViewById(R.id.tvSelectedEventType);
        btnAcceptAttendance = findViewById(R.id.btnAcceptAttendance);

        client = new OkHttpClient();
        selectedEventType = getIntent().getStringExtra("EVENT_TYPE");
        if (selectedEventType == null || selectedEventType.trim().isEmpty()) {
            selectedEventType = "Conference";
        }

        tvSelectedEventType.setText("Selected Check-In: " + selectedEventType);
        btnAcceptAttendance.setOnClickListener(v -> acceptAttendance());

        // Get the scanned data from the camera
        String qrPayload = getIntent().getStringExtra("QR_DATA");

        if (qrPayload != null) {
            // Display the raw QR data for debugging
            tvRawQrData.setText(qrPayload);

            // Call the backend with the exact slug you requested
            verifyTicketWithBackend(qrPayload, BuildConfig.EVENT_SLUG);
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
                    .url(BuildConfig.SCAN_API_URL)
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
        verifiedAttendee = new GoogleSheetsService.AttendeeRecord(
                data.optString("name", ""),
                data.optString("email", ""),
                data.optString("ticketId", "")
        );

        tvScanStatus.setText("Scan Successful! Ready to accept " + selectedEventType + " attendance.");
        tvScanStatus.setTextColor(Color.parseColor("#4CAF50")); // Green

        attendeeInfoLayout.setVisibility(View.VISIBLE);
        tvAttendeeName.setText(fallbackValue(verifiedAttendee.name));
        tvAttendeeEmail.setText(fallbackValue(verifiedAttendee.email));
        tvTicketId.setText(fallbackValue(verifiedAttendee.ticketId));
        btnAcceptAttendance.setVisibility(View.VISIBLE);
        btnAcceptAttendance.setEnabled(true);
        btnAcceptAttendance.setText("Accept");
    }

    private void showError(String message) {
        verifiedAttendee = null;
        progressBar.setVisibility(View.GONE);
        tvScanStatus.setText(message);
        tvScanStatus.setTextColor(Color.parseColor("#F44336")); // Red
        attendeeInfoLayout.setVisibility(View.GONE);
        btnAcceptAttendance.setVisibility(View.GONE);
    }

    private void acceptAttendance() {
        if (verifiedAttendee == null || isSubmittingAttendance) {
            return;
        }

        if (!BuildConfig.GOOGLE_SHEETS_ENABLED) {
            showAcceptanceError("Google Sheets sync is disabled. Set GOOGLE_SHEETS_ENABLED=true in .env.");
            return;
        }

        isSubmittingAttendance = true;
        progressBar.setVisibility(View.VISIBLE);
        btnAcceptAttendance.setEnabled(false);
        tvScanStatus.setText("Marking " + selectedEventType + " attendance...");
        tvScanStatus.setTextColor(Color.parseColor("#333333"));

        new Thread(() -> {
            try {
                GoogleSheetsService sheetsService = new GoogleSheetsService(client);
                GoogleSheetsService.AttendanceUpdateResult result = sheetsService.markAttendance(
                        BuildConfig.GOOGLE_SHEETS_SPREADSHEET_ID,
                        BuildConfig.GOOGLE_SHEETS_RANGE,
                        BuildConfig.GOOGLE_SHEETS_CLIENT_EMAIL,
                        BuildConfig.GOOGLE_SHEETS_PRIVATE_KEY,
                        selectedEventType,
                        BuildConfig.GOOGLE_SHEETS_ATTENDED_VALUE,
                        verifiedAttendee
                );

                runOnUiThread(() -> {
                    isSubmittingAttendance = false;
                    progressBar.setVisibility(View.GONE);

                    if (result.success) {
                        tvScanStatus.setText(result.message);
                        tvScanStatus.setTextColor(Color.parseColor("#4CAF50"));
                        btnAcceptAttendance.setText("Accepted");
                        btnAcceptAttendance.setEnabled(false);
                    } else {
                        showAcceptanceError(result.message);
                    }
                });
            } catch (Exception exception) {
                Log.e("GOOGLE_SHEETS", "Attendance update failed", exception);
                runOnUiThread(() -> {
                    isSubmittingAttendance = false;
                    progressBar.setVisibility(View.GONE);
                    showAcceptanceError("Could not update Google Sheets. " + safeMessage(exception));
                });
            }
        }).start();
    }

    private void showAcceptanceError(String message) {
        tvScanStatus.setText(message);
        tvScanStatus.setTextColor(Color.parseColor("#F44336"));
        btnAcceptAttendance.setEnabled(true);
    }

    private String fallbackValue(String value) {
        return value == null || value.trim().isEmpty() ? "N/A" : value;
    }

    private String safeMessage(Exception exception) {
        String rawMessage = exception.getMessage();
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "Please try again.";
        }
        return rawMessage;
    }
}
