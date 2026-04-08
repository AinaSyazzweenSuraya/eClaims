package com.eclaims.service;

import com.eclaims.config.FeatureConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class TessOcrService {
	/*
	 * 
	 * private final FeatureConfig featureConfig;
	 * 
	 *//**
		 * Extract total amount from a receipt using Tesseract OCR.
		 */
	/*
	 * public OcrService.OcrResult extractAmount(MultipartFile file) {
	 * 
	 * if (!featureConfig.isOcrEnabled()) { return OcrService.OcrResult.disabled();
	 * }
	 * 
	 * try { // Save MultipartFile to temporary file File tempFile =
	 * File.createTempFile("ocr-", ".tmp"); try (FileOutputStream fos = new
	 * FileOutputStream(tempFile)) { fos.write(file.getBytes()); }
	 * 
	 * // Initialize Tesseract Tesseract tesseract = new Tesseract();
	 * tesseract.setDatapath(featureConfig.getTessDataPath()); // e.g.,
	 * src/main/resources/tessdata tesseract.setLanguage("eng"); // or "eng+msa" for
	 * Malay + English
	 * 
	 * // Perform OCR String fullText = tesseract.doOCR(tempFile);
	 * log.debug("Tess4J OCR full text: {}", fullText);
	 * 
	 * // Parse total amount (reuses OcrService parsing logic) String amount =
	 * parseAmount(fullText);
	 * 
	 * if (amount != null) { log.info("Tess4J OCR extracted amount: RM {}", amount);
	 * return OcrService.OcrResult.success(amount); } else { return
	 * OcrService.OcrResult.
	 * error("Could not find total amount. Please enter manually."); }
	 * 
	 * } catch (TesseractException e) { log.error("Tess4J OCR failed: {}",
	 * e.getMessage()); return
	 * OcrService.OcrResult.error("OCR scan failed. Please enter amount manually.");
	 * } catch (Exception e) { log.error("OCR extraction error: {}",
	 * e.getMessage()); return
	 * OcrService.OcrResult.error("OCR scan failed. Please enter amount manually.");
	 * } }
	 * 
	 * // ── Parsing logic (same as OcrService) ─────────────────────────────
	 * private String parseAmount(String fullText) { if (fullText == null ||
	 * fullText.isBlank()) return null;
	 * 
	 * String[] lines = fullText.split("\n"); String[] totalKeywords = {
	 * "grand total", "total amount", "total payable", "amount due",
	 * "amount payable", "jumlah besar", "jumlah bayaran", "jumlah keseluruhan",
	 * "total:", "total ", "jumlah:" };
	 * 
	 * double bestAmount = -1; for (String line : lines) { String lower =
	 * line.toLowerCase().trim(); for (String keyword : totalKeywords) { if
	 * (lower.contains(keyword)) { Double amount = extractNumber(line); if (amount
	 * != null && amount > bestAmount) bestAmount = amount; } } }
	 * 
	 * if (bestAmount > 0) return String.format("%.2f", bestAmount);
	 * 
	 * // Fallback — largest number in text double largest = -1; for (String line :
	 * lines) { Double amount = extractNumber(line); if (amount != null && amount >
	 * largest && amount < 99999) largest = amount; } return largest > 0 ?
	 * String.format("%.2f", largest) : null; }
	 * 
	 * private Double extractNumber(String line) { java.util.regex.Pattern pattern =
	 * java.util.regex.Pattern.compile(
	 * "(?:RM|MYR)?\\s*(\\d{1,6}(?:[,.]\\d{3})*(?:[.,]\\d{2}))" );
	 * java.util.regex.Matcher matcher = pattern.matcher(line); Double last = null;
	 * while (matcher.find()) { String numStr = matcher.group(1).replace(",",
	 * "").trim(); try { last = Double.parseDouble(numStr); } catch
	 * (NumberFormatException ignored) {} } return last; }
	 */}