
package com.govind.aiassistant.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ChatRequest {

    @NotBlank(message = "Session Id cannot be empty")
    private String sessionId;

    @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
}
