package com.aiworkflow.designer.document.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Extracts plain text from uploads with hard resource bounds: PDF magic-byte
 * verification, a page cap, an extracted-character cap, temp-file buffering so
 * decompression cannot exhaust heap, and a wall-clock timeout on the whole
 * extraction.
 */
@Service
@Slf4j
public class DocumentParserService {

    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("txt", "md", "markdown", "text", "csv", "log");

    private static final int MAX_PAGES = 100;
    private static final int MAX_TEXT_CHARS = 500_000;
    private static final int MAX_TEXT_BYTES = 5 * 1024 * 1024; // 5MB for plain-text uploads
    private static final long EXTRACTION_TIMEOUT_SECONDS = 60;

    private final ExecutorService extractionPool = Executors.newFixedThreadPool(2);

    /**
     * Extracts plain text from an uploaded file. Supports PDF (via PDFBox)
     * and plain-text formats (txt, md, csv, log).
     */
    public String extractText(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String extension = extensionOf(filename);

        try {
            if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(file.getContentType())) {
                return extractPdfText(file, filename);
            }
            if (PLAIN_TEXT_EXTENSIONS.contains(extension) || isTextual(file.getContentType())) {
                byte[] bytes = file.getBytes();
                if (bytes.length > MAX_TEXT_BYTES) {
                    throw new IllegalArgumentException(
                            "Text file " + filename + " exceeds the 5MB text limit");
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                return truncate(text);
            }
            throw new IllegalArgumentException(
                    "Unsupported file type: " + filename + ". Supported: PDF, TXT, MD, CSV");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read document " + filename + ": " + e.getMessage(), e);
        }
    }

    private String extractPdfText(MultipartFile file, String filename) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read document " + filename, e);
        }

        // Verify the PDF signature before parsing — content-type is client-supplied
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new IllegalArgumentException(
                    "File " + filename + " claims to be a PDF but lacks the %PDF signature");
        }

        Callable<String> task = () -> {
            // Buffer to a temp file so a decompression bomb cannot exhaust heap
            try (PDDocument document = PDDocument.load(new java.io.ByteArrayInputStream(bytes),
                    MemoryUsageSetting.setupTempFileOnly())) {
                int pages = document.getNumberOfPages();
                if (pages > MAX_PAGES) {
                    throw new IllegalArgumentException(
                            "PDF " + filename + " has " + pages + " pages — maximum is " + MAX_PAGES);
                }
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);
                log.info("Extracted {} chars from PDF {} ({} pages)", text.length(), filename, pages);
                return truncate(text);
            }
        };

        Future<String> future = extractionPool.submit(task);
        try {
            return future.get(EXTRACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IllegalArgumentException(
                    "PDF extraction timed out after " + EXTRACTION_TIMEOUT_SECONDS + "s for " + filename);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PDF extraction interrupted for " + filename, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException iae) {
                throw iae;
            }
            throw new RuntimeException(
                    "Failed to extract text from " + filename + ": "
                            + (cause != null ? cause.getMessage() : e.getMessage()), cause);
        }
    }

    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_CHARS) {
            return text;
        }
        log.warn("Extracted text truncated from {} to {} chars", text.length(), MAX_TEXT_CHARS);
        return text.substring(0, MAX_TEXT_CHARS);
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private boolean isTextual(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/");
    }
}
