package com.projects.eventvalidator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GoogleSheetsService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;

    public GoogleSheetsService(OkHttpClient client) {
        this.client = client;
    }

    public AttendanceUpdateResult markAttendance(
            String spreadsheetId,
            String configuredRange,
            String clientEmail,
            String privateKeyPem,
            String eventType,
            String attendedValue,
            AttendeeRecord attendee
    ) throws Exception {
        if (isBlank(spreadsheetId) || isBlank(configuredRange) || isBlank(clientEmail) || isBlank(privateKeyPem)) {
            return AttendanceUpdateResult.failure("Missing Google Sheets configuration in .env.");
        }

        if (attendee == null || attendee.isEmpty()) {
            return AttendanceUpdateResult.failure("No attendee data available from the scan response.");
        }

        String accessToken = fetchAccessToken(clientEmail, privateKeyPem);
        SheetSnapshot snapshot = fetchSheetSnapshot(accessToken, spreadsheetId, configuredRange);
        int targetColumnIndex = findAttendanceColumn(snapshot.headerRow, eventType);

        if (targetColumnIndex < 0) {
            return AttendanceUpdateResult.failure("Could not find the Google Sheet column for " + eventType + ".");
        }

        int matchedRowIndex = findMatchingRow(snapshot.rows, attendee);
        if (matchedRowIndex < 0) {
            return AttendanceUpdateResult.failure("No matching attendee row found in Google Sheets.");
        }

        String normalizedAttendedValue = safeTrim(isBlank(attendedValue) ? "Attended" : attendedValue);
        String existingValue = getCellValue(snapshot.rows.get(matchedRowIndex), targetColumnIndex);
        if (normalizedAttendedValue.equalsIgnoreCase(safeTrim(existingValue))) {
            return AttendanceUpdateResult.success(eventType + " attendance was already marked.");
        }

        int sheetRowNumber = matchedRowIndex + 2;
        String cellRange = extractSheetName(configuredRange) + "!" + columnLabel(targetColumnIndex) + sheetRowNumber;
        updateCell(accessToken, spreadsheetId, cellRange, normalizedAttendedValue);

        return AttendanceUpdateResult.success(eventType + " attendance marked successfully.");
    }

    private String fetchAccessToken(String clientEmail, String privateKeyPem) throws Exception {
        String assertion = buildJwtAssertion(clientEmail, privateKeyPem);
        RequestBody requestBody = new FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("assertion", assertion)
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Google auth failed: " + responseBody);
            }

            JSONObject jsonObject = new JSONObject(responseBody);
            String token = jsonObject.optString("access_token");
            if (isBlank(token)) {
                throw new IOException("Google auth response did not return an access token.");
            }
            return token;
        }
    }

    private SheetSnapshot fetchSheetSnapshot(String accessToken, String spreadsheetId, String configuredRange) throws Exception {
        String encodedRange = URLEncoder.encode(configuredRange, StandardCharsets.UTF_8.name());
        Request request = new Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/" + encodedRange)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to read Google Sheets data: " + responseBody);
            }

            JSONObject jsonObject = new JSONObject(responseBody);
            JSONArray values = jsonObject.optJSONArray("values");
            if (values == null || values.length() == 0) {
                throw new IOException("Google Sheet is empty for the configured range.");
            }

            List<String> headerRow = jsonArrayToList(values.optJSONArray(0));
            List<List<String>> rows = new ArrayList<>();
            for (int index = 1; index < values.length(); index++) {
                rows.add(jsonArrayToList(values.optJSONArray(index)));
            }

            return new SheetSnapshot(headerRow, rows);
        }
    }

    private void updateCell(String accessToken, String spreadsheetId, String cellRange, String value) throws Exception {
        JSONObject requestJson = new JSONObject();
        requestJson.put("range", cellRange);
        requestJson.put("majorDimension", "ROWS");

        JSONArray rowArray = new JSONArray();
        rowArray.put(value);

        JSONArray valuesArray = new JSONArray();
        valuesArray.put(rowArray);
        requestJson.put("values", valuesArray);

        String encodedRange = URLEncoder.encode(cellRange, StandardCharsets.UTF_8.name());
        Request request = new Request.Builder()
                .url("https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/" + encodedRange + "?valueInputOption=USER_ENTERED")
                .addHeader("Authorization", "Bearer " + accessToken)
                .put(RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Failed to update Google Sheets: " + responseBody);
            }
        }
    }

    private String buildJwtAssertion(String clientEmail, String privateKeyPem) throws Exception {
        long issuedAt = System.currentTimeMillis() / 1000L;
        long expiresAt = issuedAt + 3600L;

        JSONObject header = new JSONObject();
        header.put("alg", "RS256");
        header.put("typ", "JWT");

        JSONObject claims = new JSONObject();
        claims.put("iss", clientEmail);
        claims.put("scope", SHEETS_SCOPE);
        claims.put("aud", TOKEN_URL);
        claims.put("iat", issuedAt);
        claims.put("exp", expiresAt);

        String encodedHeader = base64UrlEncode(header.toString().getBytes(StandardCharsets.UTF_8));
        String encodedClaims = base64UrlEncode(claims.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedClaims;
        String encodedSignature = base64UrlEncode(sign(signingInput, privateKeyPem));
        return signingInput + "." + encodedSignature;
    }

    private byte[] sign(String payload, String privateKeyPem) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(parsePrivateKey(privateKeyPem));
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String normalizedKey = privateKeyPem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] decoded = Base64.getDecoder().decode(normalizedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private int findAttendanceColumn(List<String> headerRow, String eventType) {
        List<String> candidateHeaders = new ArrayList<>();
        String normalizedEventType = normalize(eventType);
        candidateHeaders.add(normalizedEventType);
        candidateHeaders.add(normalizedEventType + " attended");
        candidateHeaders.add(normalizedEventType + " attendance");
        candidateHeaders.add("attended " + normalizedEventType);

        for (int index = 0; index < headerRow.size(); index++) {
            String normalizedHeader = normalize(headerRow.get(index));
            if (candidateHeaders.contains(normalizedHeader)) {
                return index;
            }
        }
        return -1;
    }

    private int findMatchingRow(List<List<String>> rows, AttendeeRecord attendee) {
        int bestIndex = -1;
        int bestScore = -1;

        for (int index = 0; index < rows.size(); index++) {
            int score = calculateMatchScore(rows.get(index), attendee);
            if (score > bestScore) {
                bestScore = score;
                bestIndex = index;
            }
        }

        return bestScore > 0 ? bestIndex : -1;
    }

    private int calculateMatchScore(List<String> row, AttendeeRecord attendee) {
        int score = 0;

        if (!isBlank(attendee.ticketId) && rowContainsExact(row, attendee.ticketId)) {
            score += 100;
        }
        if (!isBlank(attendee.email) && rowContainsExact(row, attendee.email)) {
            score += 80;
        }
        if (!isBlank(attendee.name) && rowContainsExact(row, attendee.name)) {
            score += 40;
        }
        if (!isBlank(attendee.name) && !isBlank(attendee.email)
                && rowContainsExact(row, attendee.name) && rowContainsExact(row, attendee.email)) {
            score += 25;
        }

        return score;
    }

    private boolean rowContainsExact(List<String> row, String value) {
        String normalizedExpected = normalize(value);
        for (String cell : row) {
            if (normalizedExpected.equals(normalize(cell))) {
                return true;
            }
        }
        return false;
    }

    private String extractSheetName(String configuredRange) {
        int separatorIndex = configuredRange.indexOf('!');
        return separatorIndex > 0 ? configuredRange.substring(0, separatorIndex) : configuredRange;
    }

    private String columnLabel(int zeroBasedColumnIndex) {
        StringBuilder builder = new StringBuilder();
        int column = zeroBasedColumnIndex + 1;
        while (column > 0) {
            int remainder = (column - 1) % 26;
            builder.insert(0, (char) ('A' + remainder));
            column = (column - 1) / 26;
        }
        return builder.toString();
    }

    private String getCellValue(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }

    private List<String> jsonArrayToList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }

        for (int index = 0; index < array.length(); index++) {
            values.add(array.optString(index, ""));
        }
        return values;
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalize(String value) {
        return safeTrim(value).toLowerCase(Locale.US).replaceAll("\\s+", " ");
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return safeTrim(value).isEmpty();
    }

    private static class SheetSnapshot {
        private final List<String> headerRow;
        private final List<List<String>> rows;

        private SheetSnapshot(List<String> headerRow, List<List<String>> rows) {
            this.headerRow = headerRow;
            this.rows = rows;
        }
    }

    public static class AttendeeRecord {
        public final String name;
        public final String email;
        public final String ticketId;

        public AttendeeRecord(String name, String email, String ticketId) {
            this.name = name;
            this.email = email;
            this.ticketId = ticketId;
        }

        public boolean isEmpty() {
            return isBlank(name) && isBlank(email) && isBlank(ticketId);
        }

        private boolean isBlank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    public static class AttendanceUpdateResult {
        public final boolean success;
        public final String message;

        private AttendanceUpdateResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static AttendanceUpdateResult success(String message) {
            return new AttendanceUpdateResult(true, message);
        }

        public static AttendanceUpdateResult failure(String message) {
            return new AttendanceUpdateResult(false, message);
        }
    }
}
