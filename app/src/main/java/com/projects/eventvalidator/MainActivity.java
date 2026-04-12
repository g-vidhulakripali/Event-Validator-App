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
                    tvScanStatus.setText("Status: Scan cancelled");
                    Toast.makeText(this, "Scan Cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    // Update status if successful
                    tvScanStatus.setText("Status: Scan successful!");
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
            tvScanStatus.setText("Status: Scanner active...");

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

            // Set Conference to Active (Purple)
            cardConference.setStrokeColor(Color.parseColor("#5D3FF5"));
            cardConference.setStrokeWidth(4);
            cardConference.setCardBackgroundColor(Color.parseColor("#F3F0FF"));
            tvConference.setTextColor(Color.parseColor("#5D3FF5"));
            iconConference.setColorFilter(Color.parseColor("#5D3FF5"));

            // Set Workshop to Inactive (Grey)
            cardWorkshop.setStrokeColor(Color.parseColor("#E0E0E0"));
            cardWorkshop.setStrokeWidth(2);
            cardWorkshop.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
            tvWorkshop.setTextColor(Color.parseColor("#888888"));
            iconWorkshop.setColorFilter(Color.parseColor("#888888"));

        } else {
            selectedEventType = "Workshop";
            tvScanDescription.setText("Point your camera at the attendee's QR code for the Workshop.");

            // Set Workshop to Active (Purple)
            cardWorkshop.setStrokeColor(Color.parseColor("#5D3FF5"));
            cardWorkshop.setStrokeWidth(4);
            cardWorkshop.setCardBackgroundColor(Color.parseColor("#F3F0FF"));
            tvWorkshop.setTextColor(Color.parseColor("#5D3FF5"));
            iconWorkshop.setColorFilter(Color.parseColor("#5D3FF5"));

            // Set Conference to Inactive (Grey)
            cardConference.setStrokeColor(Color.parseColor("#E0E0E0"));
            cardConference.setStrokeWidth(2);
            cardConference.setCardBackgroundColor(Color.parseColor("#FFFFFF"));
            tvConference.setTextColor(Color.parseColor("#888888"));
            iconConference.setColorFilter(Color.parseColor("#888888"));
        }
    }
}