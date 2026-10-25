package com.govind.aiassistant.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "document_uploads")
public class DocumentUpload {

    @Id
    private String documentId;

    private String filename;

    private Instant uploadedAt;

    private int chunkCount;

    protected DocumentUpload() {
    }

    public DocumentUpload(
            String documentId,
            String filename,
            Instant uploadedAt,
            int chunkCount) {
        this.documentId = documentId;
        this.filename = filename;
        this.uploadedAt = uploadedAt;
        this.chunkCount = chunkCount;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}