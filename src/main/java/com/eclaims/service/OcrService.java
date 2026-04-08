package com.eclaims.service;

import com.eclaims.config.FeatureConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

/**
 * OCR Service — Google Cloud Vision API Integration
 *
 * This service is completely isolated from all other business logic.
 * It only does one thing: read a receipt image and return the total amount.
 *
 * FEATURE FLAG: This service checks FeatureConfig.isOcrEnabled() before
 * doing anything. If disabled, it returns an error response immediately.
 *
 * Zero impact on existing claim flow when disabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final FeatureConfig featureConfig;

    private static final String VISION_API_URL =
        "https://vision.googleapis.com/v1/images:annotate";

    // ── Main entry point ──────────────────────────────────────
    public OcrResult extractAmount(MultipartFile file) {

        // Feature flag check — exit immediately if disabled
        if (!featureConfig.isOcrEnabled()) {
            return OcrResult.disabled();
        }
        String apiKey = featureConfig.getGoogleVisionApiKey();
        if (apiKey == null || apiKey.isBlank() || "YOUR_KEY".equals(apiKey)) {
            log.warn("OCR enabled but Google Vision API key not configured");
            return OcrResult.error("OCR API key not configured. Please contact Admin.");
        }

        try {
            // 1. Convert file to base64
            byte[] imageBytes = file.getBytes();
            String base64     = Base64.getEncoder().encodeToString(imageBytes);

            // Determine image type
            String mimeType   = file.getContentType();
            if (mimeType == null) mimeType = "image/jpeg";

            // 2. Build Google Vision API request body
            String requestBody = buildRequestBody(base64, mimeType);

            // 3. Call Google Vision API
            String responseJson = callVisionApi(requestBody);

            // 4. Parse the amount from response
            String amount = parseAmountFromResponse(responseJson);

            if (amount != null) {
                log.info("OCR extracted amount: RM {}", amount);
                return OcrResult.success(amount);
            } else {
                return OcrResult.error("Could not find a total amount in this receipt. Please enter manually.");
            }

        } catch (Exception e) {
            log.error("OCR extraction failed: {}", e.getMessage());
            return OcrResult.error("OCR scan failed. Please enter amount manually.");
        }
    }

    // ── Build Google Vision API request ──────────────────────
    private String buildRequestBody(String base64Image, String mimeType) {
        // Determine type from mime
        String type = "IMAGE";
        if (mimeType.contains("pdf")) type = "DOCUMENT";

        return """
            {
              "requests": [{
                "image": {
                  "content": "%s"
                },
                "features": [{
                  "type": "DOCUMENT_TEXT_DETECTION",
                  "maxResults": 1
                }]
              }]
            }
            """.formatted(base64Image);
    }

    // ── Call Google Vision REST API ───────────────────────────
    private String callVisionApi(String requestBody) throws Exception {
        String url = VISION_API_URL + "?key=" + featureConfig.getGoogleVisionApiKey();

        HttpClient client   = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Google Vision API error: HTTP {} — {}", response.statusCode(), response.body());
            throw new RuntimeException("Google Vision API returned HTTP " + response.statusCode());
        }

        return response.body();
    }

    // ── Parse amount from OCR text ────────────────────────────
    /**
     * Extracts the final total amount from OCR text.
     *
     * Strategy:
     * 1. Look for lines containing TOTAL / JUMLAH / GRAND TOTAL / AMOUNT DUE
     * 2. Extract the number from that line
     * 3. If multiple matches, take the LARGEST (most likely grand total)
     * 4. Fallback: scan all numbers and take the largest
     */
    private String parseAmountFromResponse(String responseJson) {
        try {
            // Extract full text from Google Vision response
            String fullText = extractTextFromJson(responseJson);
            if (fullText == null || fullText.isBlank()) return null;

            log.debug("OCR full text: {}", fullText);

            String[] lines = fullText.split("\n");

            // Priority keywords for total amount (English + Malay)
            String[] totalKeywords = {
                "grand total", "total amount", "total payable",
                "amount due", "amount payable", "jumlah besar",
                "jumlah bayaran", "jumlah keseluruhan",
                "total:", "total ", "jumlah:"
            };

            double bestAmount = -1;

            // Step 1: Look for total keyword lines
            for (String line : lines) {
                String lower = line.toLowerCase().trim();
                for (String keyword : totalKeywords) {
                    if (lower.contains(keyword)) {
                        Double amount = extractNumberFromLine(line);
                        if (amount != null && amount > bestAmount) {
                            bestAmount = amount;
                            log.debug("Found total keyword '{}' → amount: {}", keyword, amount);
                        }
                    }
                }
            }

            // Step 2: If found via keyword, return it
            if (bestAmount > 0) {
                return String.format("%.2f", bestAmount);
            }

            // Step 3: Fallback — scan all numbers, take largest
            // (last resort for simple receipts without labels)
            double largestNumber = -1;
            for (String line : lines) {
                Double amount = extractNumberFromLine(line);
                if (amount != null && amount > largestNumber && amount < 99999) {
                    largestNumber = amount;
                }
            }

            if (largestNumber > 0) {
                log.debug("Fallback: largest number found = {}", largestNumber);
                return String.format("%.2f", largestNumber);
            }

            return null;

        } catch (Exception e) {
            log.error("Error parsing OCR response: {}", e.getMessage());
            return null;
        }
    }

    // ── Extract text block from Google Vision JSON response ───
    private String extractTextFromJson(String json) {
        // Simple string extraction — no external JSON library needed
        // Google Vision response: "fullTextAnnotation": { "text": "..." }
        String marker = "\"text\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        // Unescape newlines
        return json.substring(start, end)
            .replace("\\n", "\n")
            .replace("\\t", " ")
            .replace("\\r", "");
    }

    // ── Extract a number from a line of text ─────────────────
    private Double extractNumberFromLine(String line) {
        // Match patterns like: 57.24 / 57,240.00 / RM57.24 / MYR 57.24
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:RM|MYR)?\\s*(\\d{1,6}(?:[,.]\\d{3})*(?:[.,]\\d{2}))"
        );
        java.util.regex.Matcher matcher = pattern.matcher(line);
        Double last = null;
        while (matcher.find()) {
            String numStr = matcher.group(1)
                .replace(",", "")   // remove thousand separators
                .trim();
            try {
                last = Double.parseDouble(numStr);
            } catch (NumberFormatException ignored) {}
        }
        return last;
    }

    // ── Result DTO ────────────────────────────────────────────
    public static class OcrResult {
        public boolean success;
        public boolean featureDisabled;
        public String  amount;
        public String  message;

        public static OcrResult success(String amount) {
            OcrResult r = new OcrResult();
            r.success = true;
            r.amount  = amount;
            r.message = "Amount extracted: RM " + amount;
            return r;
        }

        public static OcrResult error(String message) {
            OcrResult r = new OcrResult();
            r.success = false;
            r.message = message;
            return r;
        }

        public static OcrResult disabled() {
            OcrResult r = new OcrResult();
            r.success         = false;
            r.featureDisabled = true;
            r.message         = "OCR feature is not enabled";
            return r;
        }
    }
}
