package com.govind.aiassistant.repository;

import com.govind.aiassistant.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository
        extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findBySessionIdOrderByIdAsc(
            String sessionId);

    List<ChatHistory> findTop50BySessionIdOrderByIdDesc(
            String sessionId);
}