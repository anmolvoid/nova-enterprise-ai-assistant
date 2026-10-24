
package com.govind.aiassistant.service;

import com.govind.aiassistant.entity.ChatHistory;
import com.govind.aiassistant.repository.ChatHistoryRepository;

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
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final ChatHistoryRepository chatHistoryRepository;
    private final VectorStore vectorStore;

    public ChatService(
            ChatClient chatClient,
            ChatHistoryRepository chatHistoryRepository,
            VectorStore vectorStore) {

        this.chatClient = chatClient;
        this.chatHistoryRepository = chatHistoryRepository;
        this.vectorStore = vectorStore;
    }

    /**
     * Normal chat API.
     * Supports general questions and document-based questions.
     */
    public String chat(String sessionId, String prompt) {

        // Load previous conversation before saving the current question.
        String conversation = buildConversation(sessionId);

        // Retrieve relevant document context from ChromaDB.
        String context = retrieveContext(prompt);

        // Generate the answer.
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

        // Save both messages after generating the response.
        saveMessage(sessionId, "USER", prompt);
        saveMessage(sessionId, "AI", response);

        return response;
    }

    /**
     * Streaming chat API.
     * Streams the response and saves the completed answer.
     */
    public Flux<String> streamChat(String sessionId, String prompt) {

        // Capture the conversation before adding the current question.
        String conversation = buildConversation(sessionId);

        // Retrieve relevant document context.
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
                .doOnSubscribe(subscription -> saveMessage(sessionId, "USER", prompt))
                .doOnNext(completeResponse::append)
                .doOnComplete(() -> saveMessage(
                        sessionId,
                        "AI",
                        completeResponse.toString()));
    }

    /**
     * Search ChromaDB for relevant document chunks.
     * General chat continues when no relevant documents are found.
     */

    private String retrieveContext(String prompt) {
        try {
            log.info("Searching ChromaDB for prompt: {}", prompt);

            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(prompt)
                            .topK(4)
                            .build());

            if (documents == null || documents.isEmpty()) {
                log.warn("ChromaDB returned no documents for prompt: {}", prompt);
                return "No relevant document context was retrieved.";
            }

            StringBuilder context = new StringBuilder();

            for (Document document : documents) {
                String text = document.getText();

                if (text == null || text.isBlank()) {
                    continue;
                }

                Map<String, Object> metadata = document.getMetadata();

                String source = String.valueOf(
                        metadata.getOrDefault("source", "Unknown document"));

                String chunkIndex = String.valueOf(
                        metadata.getOrDefault("chunk_index", "Unknown"));

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

        } catch (Exception exception) {
            log.error("ChromaDB document retrieval failed.", exception);
            return "Document retrieval failed.";
        }
    }

    /**
     * Save a message in the chat history database.
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
     * Load the most recent 50 messages and arrange them
     * from oldest to newest.
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
                                1. Answer general questions normally using your
                                   general knowledge, even when no document context
                                   is available.
                                2. Do not require users to upload a document before
                                   answering a general question.
                                3. Be accurate, helpful, and professional.

                                Document question guidelines:
                                4. Use the supplied document context when it is
                                   relevant to the user's question.
                                5. For questions specifically about uploaded documents,
                                   do not invent facts that are absent from the context.
                                6. If the supplied context does not answer a
                                   document-specific question, clearly say that the
                                   available document context does not provide enough
                                   information.
                                7. Do not claim that a document contains information
                                   unless the supplied context supports that claim.
                                8. If no relevant document context was retrieved,
                                   answer general questions normally.

                                Conversation guidelines:
                                9. Use the conversation history when it is relevant.
                                10. Do not treat previous assistant messages as
                                    verified facts when they conflict with the
                                    current relevant document context.
                                11. Keep responses under 300 words by default.
                                12. Use headings and bullet points when helpful.
                                13. Ask for clarification when necessary.
                                14. Never identify yourself as ChatGPT, Claude,
                                    Gemini, or another assistant.
                                15. If asked who created you, say:
                                    "I am NOVA, an Enterprise AI Knowledge Assistant
                                    developed by Govind Kumar."

                SOURCE CITATION RULES:

                1. For questions about uploaded documents, base factual answers on the retrieved document context.
                2. Cite supporting claims using the source filename and chunk number provided in the context.
                3. Use this format: [Source: filename.pdf, Chunk: 2].
                4. Place citations immediately after the claims they support.
                5. Cite only sources present in the retrieved context.
                6. Never invent filenames, chunk numbers, or document contents.
                7. If the retrieved context does not contain sufficient information, say so clearly.
                8. For general questions unrelated to uploaded documents, answer normally without document citations.

                                """;
    }
}
