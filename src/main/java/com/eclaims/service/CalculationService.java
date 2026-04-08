package com.eclaims.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * All claim calculation rules for IFC E-Claims.
 */
@Service
public class CalculationService {

    // ── CL01 Meal Weekday ──────────────────────────────────────
    // RM12 per hour, max cap RM36 (3 hours)
    public BigDecimal calcMealWeekday(BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal rate    = new BigDecimal("12.00");
        BigDecimal cap     = new BigDecimal("36.00");
        BigDecimal amount  = hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return amount.compareTo(cap) > 0 ? cap : amount;
    }

    // ── CL02 Meal Rest/Off/Holiday ─────────────────────────────
    // RM12 per hour, max cap RM96 (8 hours)
    public BigDecimal calcMealHoliday(BigDecimal hours) {
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal rate   = new BigDecimal("12.00");
        BigDecimal cap    = new BigDecimal("96.00");
        BigDecimal amount = hours.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return amount.compareTo(cap) > 0 ? cap : amount;
    }

    // Calculate hours from TimeFrom/TimeTo strings (HH:mm)
    public BigDecimal calcHours(String timeFrom, String timeTo) {
        try {
            if (timeFrom == null || timeTo == null) return BigDecimal.ZERO;
            String[] from = timeFrom.split(":");
            String[] to   = timeTo.split(":");
            int fromMins  = Integer.parseInt(from[0]) * 60 + Integer.parseInt(from[1]);
            int toMins    = Integer.parseInt(to[0])   * 60 + Integer.parseInt(to[1]);
            if (toMins < fromMins) toMins += 24 * 60; // overnight
            int diff = toMins - fromMins;
            return new BigDecimal(diff).divide(new BigDecimal(60), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ── CL04 Mileage ───────────────────────────────────────────
    // Car: first 200km × RM0.60, remainder × RM0.50
    // Motorcycle: all km × RM0.50
    public BigDecimal calcMileage(String vehicleType, Integer km) {
        if (km == null || km <= 0) return BigDecimal.ZERO;
        if ("Motorcycle".equalsIgnoreCase(vehicleType)) {
            return new BigDecimal(km).multiply(new BigDecimal("0.50"))
                .setScale(2, RoundingMode.HALF_UP);
        }
        // Car (default)
        if (km <= 200) {
            return new BigDecimal(km).multiply(new BigDecimal("0.60"))
                .setScale(2, RoundingMode.HALF_UP);
        } else {
            BigDecimal first  = new BigDecimal(200).multiply(new BigDecimal("0.60"));
            BigDecimal rest   = new BigDecimal(km - 200).multiply(new BigDecimal("0.50"));
            return first.add(rest).setScale(2, RoundingMode.HALF_UP);
        }
    }

    // ── CL12 Travel Allowance ─────────────────────────────────
    // Total = ClaimLimit × (Percentage_Allowance / 100)
    public BigDecimal calcTravel(BigDecimal claimLimit, String percentageAllowance) {
        if (claimLimit == null || percentageAllowance == null) return BigDecimal.ZERO;
        try {
            BigDecimal pct = new BigDecimal(percentageAllowance.trim());
            return claimLimit.multiply(pct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
