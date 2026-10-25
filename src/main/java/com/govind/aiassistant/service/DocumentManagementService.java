package com.govind.aiassistant.service;

import com.govind.aiassistant.entity.DocumentUpload;
import com.govind.aiassistant.repository.DocumentUploadRepository;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentManagementService {

    private final DocumentUploadRepository documentUploadRepository;
    private final VectorStore vectorStore;

    public DocumentManagementService(
            DocumentUploadRepository documentUploadRepository,
            VectorStore vectorStore) {

        this.documentUploadRepository = documentUploadRepository;
        this.vectorStore = vectorStore;
    }

    /**
     * Returns all registered document uploads.
     */
    public List<DocumentUpload> getAllDocuments() {
        return documentUploadRepository.findAll();
    }

    /**
     * Returns metadata for one document.
     */
    public DocumentUpload getDocumentById(String documentId) {
        return documentUploadRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(
                        "Document not found: " + documentId));
    }

    /**
     * Deletes a document's ChromaDB vectors and H2 registry entry.
     */
    public void deleteDocument(String documentId) {

        DocumentUpload document = getDocumentById(documentId);

        // Delete only vectors belonging to this document.
        vectorStore.delete(
                "document_id == '" + document.getDocumentId() + "'");

        // Delete the H2 registry entry after vector deletion succeeds.
        documentUploadRepository.delete(document);
    }

    public static class DocumentNotFoundException
            extends RuntimeException {

        public DocumentNotFoundException(String message) {
            super(message);
        }
    }
}