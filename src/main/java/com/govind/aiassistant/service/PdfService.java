
package com.govind.aiassistant.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class PdfService {

    /**
     * Extracts text from a PDF file.
     *
     * @param filePath absolute or relative path to the PDF file
     * @return extracted text
     * @throws IOException if the file cannot be read
     */
    public String extractText(String filePath) throws IOException {

        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "PDF file path must not be empty");
        }

        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            throw new IOException(
                    "PDF file does not exist: " + filePath);
        }

        if (!file.canRead()) {
            throw new IOException(
                    "PDF file is not readable: " + filePath);
        }

        try (PDDocument document = Loader.loadPDF(file)) {

            if (document.isEncrypted()) {
                throw new IOException(
                        "Encrypted PDFs are not supported");
            }

            PDFTextStripper stripper = new PDFTextStripper();

            String extractedText = stripper.getText(document);

            if (extractedText == null || extractedText.isBlank()) {
                throw new IOException(
                        "No extractable text found in the PDF. "
                        + "It may be scanned and require OCR.");
            }

            return extractedText.trim();
        }
    }
}
