package com.projects.eventvalidator;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private static final String COLOR_ACTIVE = "#2F6BFF";
    private static final String COLOR_ACTIVE_TEXT = "#2458D6";
    private static final String COLOR_ACTIVE_BG = "#EAF1FF";
    private static final String COLOR_INACTIVE_BORDER = "#DBE6F5";
    private static final String COLOR_INACTIVE_TEXT = "#88A0C8";
    private static final String COLOR_INACTIVE_BG = "#FFFFFF";
    private static final String COLOR_STATUS_IDLE = "#6A84B6";
    private static final String COLOR_STATUS_PROGRESS = "#2F6BFF";
    private static final String COLOR_STATUS_SUCCESS = "#2A9D8F";

    // View references
    private MaterialCardView cardConference, cardWorkshop;
    private ImageView iconConference, iconWorkshop;
    private TextView tvConference, tvWorkshop, tvScanDescription, tvScanStatus;

    // Track which event type is selected
    private String selectedEventType = "Conference";

    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() == null) {
                    // Update status if cancelled
                    updateStatus("Status: Scan cancelled", COLOR_STATUS_IDLE);
                    Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    // Update status if successful
                    updateStatus("Status: Scan successful!", COLOR_STATUS_SUCCESS);
                    String scannedData = result.getContents();
                    Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                    // Pass BOTH the QR data and the event type they selected
                    intent.putExtra("QR_DATA", scannedData);
                    intent.putExtra("EVENT_TYPE", selectedEventType);
                    startActivity(intent);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        cardConference = findViewById(R.id.cardConference);
        cardWorkshop = findViewById(R.id.cardWorkshop);
        iconConference = findViewById(R.id.iconConference);
        iconWorkshop = findViewById(R.id.iconWorkshop);
        tvConference = findViewById(R.id.tvConference);
        tvWorkshop = findViewById(R.id.tvWorkshop);
        tvScanDescription = findViewById(R.id.tvScanDescription);
        tvScanStatus = findViewById(R.id.tvScanStatus); // Initialize the status text

        Button btnOpenScanner = findViewById(R.id.btnOpenScanner);

        // Set Click Listeners for the cards
        cardConference.setOnClickListener(v -> selectEventType(true));
        cardWorkshop.setOnClickListener(v -> selectEventType(false));
        selectEventType(true);

//        btnOpenScanner.setOnClickListener(v -> {
//            // Update the main screen UI to show we are busy
//            tvScanStatus.setText("Status: Scanner active...");
//
//            ScanOptions options = new ScanOptions();
//            // Make the camera prompt text more active
//            options.setPrompt("Scanning... Hold camera steady over " + selectedEventType + " QR Code");
//            options.setBeepEnabled(true);
//            options.setOrientationLocked(true);
//            options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);
//
//            barcodeLauncher.launch(options);
//        });

        btnOpenScanner.setOnClickListener(v -> {
            // Update the main screen UI to show we are busy
            updateStatus("Status: Scanner active...", COLOR_STATUS_PROGRESS);

            ScanOptions options = new ScanOptions();
            options.setBeepEnabled(true);
            options.setOrientationLocked(true);

            // Pass the currently selected event type to the scanner screen
            options.addExtra("EVENT_TYPE", selectedEventType);

            // IMPORTANT: Tell the launcher to use our new Custom Activity
            options.setCaptureActivity(CustomScannerActivity.class);

            barcodeLauncher.launch(options);
        });
    }

    // Helper method to update UI colors based on selection
    private void selectEventType(boolean isConference) {
        if (isConference) {
            selectedEventType = "Conference";
            tvScanDescription.setText("Point your camera at the attendee's QR code for the Conference.");

            cardConference.setStrokeColor(Color.parseColor(COLOR_ACTIVE));
            cardConference.setStrokeWidth(4);
            cardConference.setCardBackgroundColor(Color.parseColor(COLOR_ACTIVE_BG));
            tvConference.setTextColor(Color.parseColor(COLOR_ACTIVE_TEXT));
            iconConference.setColorFilter(Color.parseColor(COLOR_ACTIVE));

            cardWorkshop.setStrokeColor(Color.parseColor(COLOR_INACTIVE_BORDER));
            cardWorkshop.setStrokeWidth(1);
            cardWorkshop.setCardBackgroundColor(Color.parseColor(COLOR_INACTIVE_BG));
            tvWorkshop.setTextColor(Color.parseColor(COLOR_INACTIVE_TEXT));
            iconWorkshop.setColorFilter(Color.parseColor(COLOR_INACTIVE_TEXT));

        } else {
            selectedEventType = "Workshop";
            tvScanDescription.setText("Point your camera at the attendee's QR code for the Workshop.");

            cardWorkshop.setStrokeColor(Color.parseColor(COLOR_ACTIVE));
            cardWorkshop.setStrokeWidth(4);
            cardWorkshop.setCardBackgroundColor(Color.parseColor(COLOR_ACTIVE_BG));
            tvWorkshop.setTextColor(Color.parseColor(COLOR_ACTIVE_TEXT));
            iconWorkshop.setColorFilter(Color.parseColor(COLOR_ACTIVE));

            cardConference.setStrokeColor(Color.parseColor(COLOR_INACTIVE_BORDER));
            cardConference.setStrokeWidth(1);
            cardConference.setCardBackgroundColor(Color.parseColor(COLOR_INACTIVE_BG));
            tvConference.setTextColor(Color.parseColor(COLOR_INACTIVE_TEXT));
            iconConference.setColorFilter(Color.parseColor(COLOR_INACTIVE_TEXT));
        }
    }

    private void updateStatus(String message, String colorHex) {
        tvScanStatus.setText(message);
        tvScanStatus.setTextColor(Color.parseColor(colorHex));
    }
}
