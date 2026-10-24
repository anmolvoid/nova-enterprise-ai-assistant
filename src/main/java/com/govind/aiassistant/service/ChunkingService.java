
package com.govind.aiassistant.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkingService {

    /**
     * Splits text into overlapping chunks.
     *
     * @param text      source text
     * @param chunkSize maximum characters per chunk
     * @param overlap   number of overlapping characters
     * @return list of non-empty text chunks
     */
    public List<String> chunkText(
            String text,
            int chunkSize,
            int overlap) {

        List<String> chunks = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return chunks;
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Chunk size must be greater than zero");
        }

        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "Overlap must be non-negative and smaller than chunk size");
        }

        int start = 0;

        while (start < text.length()) {

            int end = Math.min(
                    start + chunkSize,
                    text.length());

            String chunk = text.substring(start, end);

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            if (end == text.length()) {
                break;
            }

            start += chunkSize - overlap;
        }

        return chunks;
    }
}
