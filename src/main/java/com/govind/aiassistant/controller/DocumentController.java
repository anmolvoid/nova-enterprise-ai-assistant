
package com.govind.aiassistant.controller;

import com.govind.aiassistant.entity.DocumentUpload;
import com.govind.aiassistant.service.DocumentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentManagementService documentManagementService;

    // Get all uploaded documents
    @GetMapping
    public ResponseEntity<List<DocumentUpload>> getAllDocuments() {
        return ResponseEntity.ok(
                documentManagementService.getAllDocuments()
        );
    }

    // Get a document by ID
    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentUpload> getDocumentById(
            @PathVariable String documentId) {

        try {
            return ResponseEntity.ok(
                    documentManagementService.getDocumentById(documentId)
            );
        } catch (DocumentManagementService.DocumentNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    // Delete a document and its indexed vectors
    @DeleteMapping("/{documentId}")
    public ResponseEntity<String> deleteDocument(
            @PathVariable String documentId) {

        try {
            documentManagementService.deleteDocument(documentId);

            return ResponseEntity.ok(
                    "Document and its indexed vectors deleted successfully."
            );
        } catch (DocumentManagementService.DocumentNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
