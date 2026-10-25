
package com.govind.aiassistant.service;

import com.govind.aiassistant.entity.DocumentUpload;
import com.govind.aiassistant.repository.DocumentUploadRepository;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 150;

    private static final List<String> SUPPORTED_EXTENSIONS =
            List.of(".pdf", ".doc", ".docx", ".txt");

    private final PdfService pdfService;
    private final ChunkingService chunkingService;
    private final VectorStore vectorStore;
    private final DocumentUploadRepository documentUploadRepository;
    private final Tika tika = new Tika();

    public DocumentIngestionService(
            PdfService pdfService,
            ChunkingService chunkingService,
            VectorStore vectorStore,
            DocumentUploadRepository documentUploadRepository) {

        this.pdfService = pdfService;
        this.chunkingService = chunkingService;
        this.vectorStore = vectorStore;
        this.documentUploadRepository = documentUploadRepository;
    }

    /**
     * Extracts text from PDF, DOC, DOCX or TXT files,
     * splits it into chunks, indexes them in ChromaDB,
     * and registers the upload in H2.
     *
     * @return number of chunks indexed
     */
    public int ingestPdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload a non-empty document.");
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException(
                    "The uploaded file must have a filename.");
        }

        String filename = originalFilename.toLowerCase(Locale.ROOT);

        String extension = SUPPORTED_EXTENSIONS.stream()
                .filter(filename::endsWith)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported file format. "
                                + "Supported formats: PDF, DOC, DOCX, TXT."));

        String documentId = UUID.randomUUID().toString();
        Instant uploadedAt = Instant.now();
        String uploadedAtMetadata = uploadedAt.toString();

        Path tempPath = null;

        try {
            // Preserve the extension for format detection.
            tempPath = Files.createTempFile(
                    "nova-upload-", extension);

            File tempFile = tempPath.toFile();
            file.transferTo(tempFile);

            // Preserve the existing PDFBox implementation for PDFs.
            // Apache Tika extracts text from DOC, DOCX and TXT.
            String extractedText;

            if (".pdf".equals(extension)) {
                extractedText =
                        pdfService.extractText(tempFile.getAbsolutePath());
            } else {
                extractedText = tika.parseToString(tempFile);
            }

            if (extractedText == null || extractedText.isBlank()) {
                throw new IllegalArgumentException(
                        "The uploaded document contains no extractable text.");
            }

            // Preserve existing chunk size and overlap.
            List<String> chunks = chunkingService.chunkText(
                    extractedText,
                    CHUNK_SIZE,
                    CHUNK_OVERLAP);

            if (chunks == null || chunks.isEmpty()) {
                throw new IllegalArgumentException(
                        "No usable text chunks were found in the document.");
            }

            // Create Spring AI documents with existing metadata.
            List<Document> documents = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {

                Map<String, Object> metadata = Map.of(
                        "source", originalFilename,
                        "document_id", documentId,
                        "uploaded_at", uploadedAtMetadata,
                        "chunk_index", i
                );

                documents.add(
                        new Document(chunks.get(i), metadata));
            }

            // Index document chunks in ChromaDB.
            vectorStore.add(documents);

            // Register the successful upload in H2.
            DocumentUpload upload = new DocumentUpload(
                    documentId,
                    originalFilename,
                    uploadedAt,
                    documents.size()
            );

            documentUploadRepository.save(upload);

            return documents.size();

        } catch (IOException | TikaException exception) {
            throw new IllegalStateException(
                    "Failed to read or extract text from the uploaded document.",
                    exception);

        } finally {
            // Always attempt to delete the temporary file.
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException exception) {
                    System.err.println(
                            "Could not delete temporary file: " + tempPath);
                }
            }
        }
    }
}
