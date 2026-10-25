package com.govind.aiassistant.service;

import com.govind.aiassistant.entity.ChatHistory;
import com.govind.aiassistant.entity.DocumentUpload;
import com.govind.aiassistant.repository.ChatHistoryRepository;
import com.govind.aiassistant.repository.DocumentUploadRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final Logger log =
            LoggerFactory.getLogger(ChatService.class);

    private static final int DEFAULT_TOP_K = 4;

    private final ChatClient chatClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final VectorStore vectorStore;
    private final DocumentUploadRepository documentUploadRepository;

    public ChatService(
            ChatClient chatClient,
            ChatHistoryRepository chatHistoryRepository,
            VectorStore vectorStore,
            DocumentUploadRepository documentUploadRepository) {

        this.chatClient = chatClient;
        this.chatHistoryRepository = chatHistoryRepository;
        this.vectorStore = vectorStore;
        this.documentUploadRepository = documentUploadRepository;
    }

    /**
     * Normal chat API.
     */
    public String chat(String sessionId, String prompt) {

        String conversation = buildConversation(sessionId);
        String context = retrieveContext(prompt);

        String response = chatClient
                .prompt()
                .system(getSystemPrompt())
                .user("""
                        Conversation history:
                        %s

                        Relevant document context:
                        %s

                        Current user question:
                        %s
                        """.formatted(conversation, context, prompt))
                .call()
                .content();

        saveMessage(sessionId, "USER", prompt);
        saveMessage(sessionId, "AI", response);

        return response;
    }

    /**
     * Streaming chat API.
     */
    public Flux<String> streamChat(String sessionId, String prompt) {

        String conversation = buildConversation(sessionId);
        String context = retrieveContext(prompt);

        StringBuilder completeResponse = new StringBuilder();

        return chatClient
                .prompt()
                .system(getSystemPrompt())
                .user("""
                        Conversation history:
                        %s

                        Relevant document context:
                        %s

                        Current user question:
                        %s
                        """.formatted(conversation, context, prompt))
                .stream()
                .content()
                .doOnSubscribe(
                        subscription -> saveMessage(
                                sessionId, "USER", prompt))
                .doOnNext(completeResponse::append)
                .doOnComplete(() -> saveMessage(
                        sessionId,
                        "AI",
                        completeResponse.toString()));
    }

    /**
     * Retrieves document context.
     *
     * Latest-document questions use H2 to determine upload order.
     * Other questions continue to use normal similarity search.
     */
    private String retrieveContext(String prompt) {

        try {
            log.info("Searching ChromaDB for prompt: {}", prompt);

            if (isLatestDocumentQuestion(prompt)) {
                return retrieveLatestDocumentContext(prompt);
            }

            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(prompt)
                            .topK(DEFAULT_TOP_K)
                            .build());

            if (documents == null || documents.isEmpty()) {
                log.warn(
                        "ChromaDB returned no documents for prompt: {}",
                        prompt);

                return "No relevant document context was retrieved.";
            }

            return formatContext(documents);

        } catch (Exception exception) {
            log.error("Document retrieval failed.", exception);
            return "Document retrieval failed.";
        }
    }

    /**
     * Retrieves the latest uploaded document using its H2 registry entry.
     */
    private String retrieveLatestDocumentContext(String prompt) {

        DocumentUpload latestUpload = documentUploadRepository
                .findFirstByOrderByUploadedAtDesc()
                .orElse(null);

        if (latestUpload == null) {
            log.warn("No uploaded documents are registered in H2.");

            return "No uploaded documents are registered yet.";
        }

        String documentId = latestUpload.getDocumentId();
        int chunkCount = latestUpload.getChunkCount();

        if (documentId == null
                || documentId.isBlank()
                || chunkCount <= 0) {

            log.warn(
                    "Latest upload has invalid metadata: filename={}",
                    latestUpload.getFilename());

            return "The latest upload has incomplete registry metadata.";
        }

        log.info(
                "Latest upload selected from H2: filename={}, "
                        + "documentId={}, uploadedAt={}",
                latestUpload.getFilename(),
                documentId,
                latestUpload.getUploadedAt());

        /*
         * Filter ChromaDB by the exact document ID recorded in H2.
         * The ID is generated by UUID.randomUUID() during ingestion.
         */
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(prompt)
                        .topK(chunkCount)
                        .filterExpression(
                                "document_id == '" + documentId + "'")
                        .build());

        if (documents == null || documents.isEmpty()) {
            log.warn(
                    "No ChromaDB chunks found for document ID {}",
                    documentId);

            return "The latest uploaded document is registered in H2, "
                    + "but its chunks could not be retrieved from ChromaDB.";
        }

        // Put retrieved chunks in their original document order.
        documents.sort((left, right) ->
                Integer.compare(
                        getChunkIndex(left),
                        getChunkIndex(right)));

        log.info(
                "Retrieved {} chunks for latest upload {}",
                documents.size(),
                latestUpload.getFilename());

        return formatContext(documents);
    }

    /**
     * Reads the chunk index from ChromaDB metadata.
     */
    private int getChunkIndex(Document document) {

        Object index = document.getMetadata().get("chunk_index");

        if (index instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(index));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * Detects questions about the newest or most recently uploaded document.
     */
    private boolean isLatestDocumentQuestion(String prompt) {

        if (prompt == null || prompt.isBlank()) {
            return false;
        }

        String normalized =
                prompt.toLowerCase(java.util.Locale.ROOT);

        return normalized.contains("most recently uploaded")
                || normalized.contains("latest uploaded")
                || normalized.contains("newest uploaded")
                || normalized.contains("latest document")
                || normalized.contains("newest document")
                || normalized.contains("most recent document")
                || normalized.contains("uploaded just now")
                || normalized.contains("uploaded last")
                || normalized.contains("most recent upload")
                || normalized.contains("newest upload")
                || normalized.contains("latest upload");
    }

    /**
     * Formats retrieved chunks with source citations.
     */
    private String formatContext(List<Document> documents) {

        StringBuilder context = new StringBuilder();

        for (Document document : documents) {

            String text = document.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            Map<String, Object> metadata = document.getMetadata();

            String source = String.valueOf(
                    metadata.getOrDefault(
                            "source",
                            "Unknown document"));

            String chunkIndex = String.valueOf(
                    metadata.getOrDefault(
                            "chunk_index",
                            "Unknown"));

            context.append("[Source: ")
                    .append(source)
                    .append(", Chunk: ")
                    .append(chunkIndex)
                    .append("]\n");

            context.append(text)
                    .append("\n\n---\n\n");

            log.info(
                    "Retrieved source={}, chunk={}",
                    source,
                    chunkIndex);
        }

        if (context.isEmpty()) {
            log.warn("Retrieved documents contained no usable text.");

            return "No relevant document context was retrieved.";
        }

        return context.toString();
    }

    /**
     * Saves a message in the chat history database.
     */
    private void saveMessage(
            String sessionId,
            String role,
            String message) {

        ChatHistory chatHistory = new ChatHistory();

        chatHistory.setSessionId(sessionId);
        chatHistory.setRole(role);
        chatHistory.setMessage(message);

        chatHistoryRepository.save(chatHistory);
    }

    /**
     * Loads the latest 50 messages and arranges them from oldest to newest.
     */
    private String buildConversation(String sessionId) {

        List<ChatHistory> history = chatHistoryRepository
                .findTop50BySessionIdOrderByIdDesc(sessionId);

        Collections.reverse(history);

        StringBuilder conversation = new StringBuilder();

        for (ChatHistory message : history) {
            conversation
                    .append(message.getRole())
                    .append(": ")
                    .append(message.getMessage())
                    .append("\n");
        }

        return conversation.toString();
    }

    /**
     * Central NOVA system prompt.
     */
    private String getSystemPrompt() {

        return """
                You are NOVA, an Enterprise AI Knowledge Assistant
                developed by Govind Kumar.

                Your capabilities:
                - Answer general knowledge and technical questions.
                - Explain concepts and provide practical examples.
                - Answer questions using relevant uploaded documents.
                - Maintain conversation context using the supplied history.

                General question guidelines:
                1. Answer general questions normally using your general
                   knowledge, even when no document context is available.
                2. Do not require users to upload a document before
                   answering a general question.
                3. Be accurate, helpful, and professional.

                Document question guidelines:
                4. Use the supplied document context when relevant.
                5. For questions specifically about uploaded documents,
                   do not invent facts absent from the context.
                6. If the supplied context does not answer a document-specific
                   question, clearly say that the available context is
                   insufficient.
                7. Do not claim a document contains information unless
                   the supplied context supports that claim.
                8. If no relevant document context was retrieved,
                   answer general questions normally.
                9. When asked about the most recently uploaded document,
                   use the context supplied for that request.
                   Do not infer upload order from filenames.
                10. Do not claim upload order based on text inside a PDF.
                    The application selects the latest upload using H2
                    registry metadata.

                Conversation guidelines:
                11. Use conversation history when relevant.
                12. Do not treat previous assistant messages as verified
                    facts when they conflict with current document context.
                13. Keep responses under 300 words by default.
                14. Use headings and bullet points when helpful.
                15. Ask for clarification when necessary.
                16. Never identify yourself as ChatGPT, Claude, Gemini,
                    or another assistant.
                17. If asked who created you, say:
                    "I am NOVA, an Enterprise AI Knowledge Assistant
                    developed by Govind Kumar."

                SOURCE CITATION RULES:
                1. Base factual answers about uploaded documents on
                   the retrieved document context.
                2. Cite supporting claims using the source filename
                   and chunk number provided in the context.
                3. Use this format: [Source: filename.pdf, Chunk: 2].
                4. Place citations immediately after the claims they support.
                5. Cite only sources present in the retrieved context.
                6. Never invent filenames, chunk numbers, or document contents.
                7. If context is insufficient, say so clearly.
                8. For general questions unrelated to uploaded documents,
                   answer normally without document citations.
                """;
    }
}