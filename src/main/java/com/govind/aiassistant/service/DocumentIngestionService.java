
package com.govind.aiassistant.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 150;

    private final PdfService pdfService;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;

    public DocumentIngestionService(
            PdfService pdfService,
            ChunkingService chunkingService,
            VectorStore vectorStore) {

        this.pdfService = pdfService;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
    }

    /**
     * Extract PDF text, split it into chunks,
     * and store the chunks in ChromaDB.
     *
     * @return number of chunks indexed
     */
    public int ingestPdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload a non-empty PDF file.");
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null
                || !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException(
                    "Only PDF files are supported.");
        }

        Path tempPath = null;

        try {
            // Create a temporary file for PDFBox.
            tempPath = Files.createTempFile(
                    "nova-upload-", ".pdf");

            File tempFile = tempPath.toFile();

            file.transferTo(tempFile);

            // Extract text from the uploaded PDF.
            String extractedText =
                    pdfService.extractText(tempFile.getAbsolutePath());

            // Split extracted text into overlapping chunks.
            List<String> chunks = chunkingService.chunkText(
                    extractedText,
                    CHUNK_SIZE,
                    CHUNK_OVERLAP);

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException(
                        "No usable text chunks were found in the PDF.");
            }

            // Convert chunks into Spring AI documents.
            List<Document> documents = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {

                Map<String, Object> metadata = Map.of(
                        "source", originalFilename,
                        "chunk_index", i
                );

                documents.add(
                        new Document(chunks.get(i), metadata));
            }

            // Spring AI generates embeddings and stores
            // documents through the configured VectorStore.
            vectorStore.add(documents);

            return documents.size();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read or process the uploaded PDF.",
                    exception);

        } finally {
            // Always clean up the temporary file.
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException exception) {
                    // Report cleanup failure without hiding
                    // an ingestion exception.
                    System.err.println(
                            "Could not delete temporary PDF: "
                                    + tempPath);
                }
            }
        }
    }
}
