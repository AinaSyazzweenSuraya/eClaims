package com.eclaims.controller;

import com.eclaims.config.FeatureConfig;
import com.eclaims.service.OcrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * OCR Controller — Handles receipt scanning requests
 *
 * This controller is completely separate from ClaimController.
 * It only handles OCR-related requests.
 *
 * Endpoint: POST /api/ocr/scan
 *
 * Feature flag is checked in OcrService — if disabled,
 * returns 200 with { featureDisabled: true } so UI can handle gracefully.
 */
@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {

    private final OcrService    ocrService;
    private final FeatureConfig featureConfig;

    /**
     * Scan a receipt image and extract the total amount.
     *
     * Request:  POST /api/ocr/scan  (multipart/form-data, field name = "file")
     * Response: { success: true, amount: "57.24", message: "Amount extracted: RM 57.24" }
     *       OR: { success: false, message: "Could not find amount..." }
     *       OR: { success: false, featureDisabled: true }
     */
    @PostMapping("/scan")
    public ResponseEntity<?> scanReceipt(@RequestParam("file") MultipartFile file) {

        // Basic validation
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false, "message", "No file provided"));
        }

        // Validate file type — only images and PDF
        String contentType = file.getContentType();
        if (contentType == null ||
            (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            return ResponseEntity.badRequest()
                .body(Map.of("success", false,
                    "message", "Only image files (JPG, PNG) and PDF are supported for OCR"));
        }

        log.info("OCR scan request: file={}, size={}, type={}",
            file.getOriginalFilename(), file.getSize(), contentType);

        OcrService.OcrResult result = ocrService.extractAmount(file);
        return ResponseEntity.ok(result);
    }

    /**
     * Check if OCR feature is enabled.
     * Called by JS on page load to show/hide the scan button.
     *
     * Response: { enabled: true/false }
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("enabled", featureConfig.isOcrEnabled()));
    }
}
