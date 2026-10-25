package com.govind.aiassistant.repository;

import com.govind.aiassistant.entity.DocumentUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentUploadRepository
        extends JpaRepository<DocumentUpload, String> {

    Optional<DocumentUpload> findFirstByOrderByUploadedAtDesc();
}