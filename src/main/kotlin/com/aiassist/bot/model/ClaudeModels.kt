package com.aiassist.bot.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class ClaudeRequest(
    val model: String,
    @JsonProperty("max_tokens")
    val maxTokens: Int,
    val messages: List<Message>
)

data class Message(
    val role: String,
    val content: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @JsonProperty("stop_reason")
    val stopReason: String?,
    val usage: Usage
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContentBlock(
    val type: String,
    val text: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Usage(
    @JsonProperty("input_tokens")
    val inputTokens: Int,
    @JsonProperty("output_tokens")
    val outputTokens: Int
)

// Error response from Claude API
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClaudeErrorResponse(
    val type: String,
    val error: ErrorDetail
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorDetail(
    val type: String,
    val message: String
)
