package com.eclaims.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class PdfPageExtractionService {

    public byte[] extractPages(byte[] originalPdf, List<Integer> pageNumbers) throws IOException {
        if (pageNumbers == null || pageNumbers.isEmpty()) {
            throw new IllegalArgumentException("No page numbers provided");
        }

        try (PDDocument source = Loader.loadPDF(originalPdf);
             PDDocument extracted = new PDDocument()) {

            int totalPages = source.getNumberOfPages();

            for (Integer pageNum : pageNumbers) {
                if (pageNum == null || pageNum < 1 || pageNum > totalPages) {
                    throw new IllegalArgumentException(
                            "Invalid page number: " + pageNum + " (document has " + totalPages + " pages)");
                }
                extracted.addPage(source.getPage(pageNum - 1));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            extracted.save(out);
            return out.toByteArray();
        }
    }
}