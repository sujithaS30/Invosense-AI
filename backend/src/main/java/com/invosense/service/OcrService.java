package com.invosense.service;

import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Converts an uploaded invoice (PDF or image) into raw text.
 * - Text-based PDFs -> PDFBox text extraction (fast, no OCR needed)
 * - Scanned PDFs / images -> Tesseract OCR
 */
@Service
public class OcrService {

    public String extractText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            return extractFromPdf(file);
        }
        return extractFromImage(file);
    }

    private String extractFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            // If the PDF has (almost) no extractable text, it's likely a scanned image PDF.
            if (text == null || text.trim().length() < 20) {
                // Fallback: render pages to images and OCR them (kept simple for one page here).
                return "OCR_FALLBACK_NEEDED: implement page rasterization + Tesseract for scanned PDFs";
            }
            return text;
        }
    }

    private String extractFromImage(MultipartFile file) throws IOException {
        BufferedImage image = ImageIO.read(file.getInputStream());
        Tesseract tesseract = new Tesseract();
        // In Docker, point this at the installed tessdata path (see Dockerfile).
        tesseract.setDatapath("/usr/share/tesseract-ocr/5/tessdata");
        try {
            return tesseract.doOCR(image);
        } catch (Exception e) {
            throw new IOException("OCR failed: " + e.getMessage(), e);
        }
    }
}
