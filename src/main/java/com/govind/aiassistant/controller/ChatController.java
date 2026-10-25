
package com.govind.aiassistant.controller;

import com.govind.aiassistant.dto.ChatRequest;
import com.govind.aiassistant.dto.ChatResponse;
import com.govind.aiassistant.dto.StreamResponse;
import com.govind.aiassistant.service.ChatService;
import com.govind.aiassistant.service.DocumentIngestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".pdf", ".doc", ".docx", ".txt");

    private final ChatService chatService;
    private final DocumentIngestionService documentIngestionService;

    /**
     * Normal chat endpoint.
     * POST /api/v1/chat
     */
    @PostMapping("/chat")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request) {

        String response = chatService.chat(
                request.getSessionId(),
                request.getPrompt()
        );

        return new ChatResponse(response);
    }

    /**
     * Streaming chat endpoint.
     * POST /api/v1/chat/stream
     */
    @PostMapping(
            value = "/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<StreamResponse>> streamChat(
            @Valid @RequestBody ChatRequest request) {

        Flux<ServerSentEvent<StreamResponse>> tokenEvents =
                chatService.streamChat(
                        request.getSessionId(),
                        request.getPrompt()
                )
                .map(chunk ->
                        ServerSentEvent.<StreamResponse>builder()
                                .data(new StreamResponse("token", chunk))
                                .event("token")
                                .build()
                );

        ServerSentEvent<StreamResponse> doneEvent =
                ServerSentEvent.<StreamResponse>builder()
                        .data(new StreamResponse("done", "[DONE]"))
                        .event("done")
                        .build();

        return tokenEvents.concatWithValues(doneEvent);
    }

    /**
     * Upload a supported document and index its text in ChromaDB.
     * POST /api/v1/documents/upload
     *
     * Multipart form field: file
     */
    @PostMapping(
            value = "/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please upload a non-empty document.");
        }

        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            return ResponseEntity.badRequest()
                    .body("The uploaded file must have a filename.");
        }

        String lowercaseFilename = filename.toLowerCase(Locale.ROOT);

        boolean supported = SUPPORTED_EXTENSIONS.stream()
                .anyMatch(lowercaseFilename::endsWith);

        if (!supported) {
            return ResponseEntity.badRequest()
                    .body("Unsupported file format. "
                            + "Supported formats: PDF, DOC, DOCX, TXT.");
        }

        int chunksIndexed = documentIngestionService.ingestPdf(file);

        return ResponseEntity.ok(
                "Document indexed successfully. Filename: "
                        + filename
                        + ", chunks indexed: "
                        + chunksIndexed
        );
    }
}
