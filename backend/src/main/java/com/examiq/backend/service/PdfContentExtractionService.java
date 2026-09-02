package com.examiq.backend.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * Extracts plain text from an uploaded PDF so subject-match confidence can be
 * computed from the paper's real content instead of only its title. Never
 * throws — any extraction failure (corrupt file, non-PDF, encrypted PDF)
 * degrades to an empty string so the upload flow always continues. Works
 * directly off the in-memory upload bytes so it can run before the file is
 * written to disk (a paper that turns out to be a clear subject mismatch is
 * never stored, matching the existing auto-reject behavior).
 */
@Service
public class PdfContentExtractionService {

    private static final int MAX_CHARACTERS = 20_000;

    public String extractText(byte[] fileBytes, String originalFileName) {
        if (fileBytes == null || fileBytes.length == 0 || !looksLikePdf(originalFileName)) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null) {
                return "";
            }
            return text.length() > MAX_CHARACTERS ? text.substring(0, MAX_CHARACTERS) : text;
        } catch (Exception e) {
            System.out.println("Warning: PDF text extraction failed for " + originalFileName + ": " + e.getMessage());
            return "";
        }
    }

    private boolean looksLikePdf(String fileName) {
        return fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }
}
