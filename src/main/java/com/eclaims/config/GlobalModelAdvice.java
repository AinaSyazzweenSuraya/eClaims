package com.eclaims.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * GlobalModelAdvice — injects global attributes into every Thymeleaf model.
 *
 * This is the cleanest way to make phase flag available in ALL templates
 * (especially layout.html fragments) without touching any existing controller.
 *
 * Zero impact on existing controllers — they don't need any changes.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final FeatureConfig featureConfig;

    /**
     * Makes 'phase1' available in every Thymeleaf template.
     * Usage in templates: th:if="${phase1}" to hide Phase 2 features.
     */
    @ModelAttribute("phase1")
    public boolean isPhase1() {
        return featureConfig.isPhase1();
    }

    /**
     * Makes 'ocrEnabled' available globally too (was previously per-controller).
     */
    @ModelAttribute("ocrEnabled")
    public boolean isOcrEnabled() {
        return featureConfig.isOcrEnabled();
    }
}
