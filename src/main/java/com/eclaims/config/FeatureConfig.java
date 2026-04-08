package com.eclaims.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Feature Flags Configuration
 *
 * Controls which optional features are enabled/disabled.
 * All flags default to FALSE — features must be explicitly enabled.
 *
 * To enable OCR:
 *   1. Set eclaims.features.ocr-enabled=true in application.properties
 *   2. Set eclaims.features.google-vision-api-key=YOUR_KEY
 *   3. Restart the application
 *
 * To disable OCR:
 *   1. Set eclaims.features.ocr-enabled=false
 *   2. Restart — zero impact on existing functionality
 */
@Configuration
@ConfigurationProperties(prefix = "eclaims.features")
@Getter
@Setter
public class FeatureConfig {

    /**
     * Enable/disable Google Vision OCR receipt scanning.
     * When false: paperclip button works as normal (upload only).
     * When true:  paperclip button also shows "Scan Amount" option.
     */
    private boolean ocrEnabled = false;

    /**
     * Google Cloud Vision API key.
     * Get from: https://console.cloud.google.com → APIs & Services → Credentials
     * Free tier: 1000 requests/month
     */
    private String googleVisionApiKey = "";

    /**
     * System phase control.
     * phase=1 → Staff can only create, save, edit, download claims. Submit hidden.
     * phase=2 → Full workflow enabled (submit, approve, finance process).
     *
     * To move to Phase 2:
     *   1. Set eclaims.features.phase=2 in application.properties
     *   2. Restart the application
     */
    private int phase = 1;

    public boolean isPhase1() { return phase == 1; }
    public boolean isPhase2() { return phase >= 2; }
}
