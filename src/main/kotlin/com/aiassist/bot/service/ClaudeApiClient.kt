package com.aiassist.bot.service

import com.aiassist.bot.config.ClaudeApiConfig
import com.aiassist.bot.model.ClaudeRequest
import com.aiassist.bot.model.ClaudeResponse
import com.aiassist.bot.model.ClaudeErrorResponse
import com.aiassist.bot.model.Message
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import mu.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ClaudeApiClient(
    private val config: ClaudeApiConfig,
    private val formatPromptLoader: FormatPromptLoader,
    private val responseParser: ResponseParser,
    private val settingsManager: SettingsManager
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300000 // 5 minutes
            connectTimeoutMillis = 60000  // 1 minute
            socketTimeoutMillis = 300000  // 5 minutes
        }
    }

    suspend fun sendMessage(
        userMessage: String,
        chatId: Long,
        isExpertMode: Boolean = false,
        reasoningType: String? = null
    ): String {
        return sendMessageWithHistory(userMessage, mutableListOf(), chatId, isExpertMode, reasoningType)
    }

    suspend fun sendMessageWithHistory(
        userMessage: String,
        history: MutableList<Message>,
        chatId: Long,
        isExpertMode: Boolean = false,
        reasoningType: String? = null
    ): String {
        return try {
            logger.info { "Sending message to Claude API with ${history.size} previous messages, format: ${config.responseFormat}, expert mode: $isExpertMode, reasoning type: $reasoningType" }

            // Determine system prompt: mode-specific prompt + format requirements
            val modePrompt = when {
                // Priority 1: Reasoning mode
                reasoningType != null -> {
                    formatPromptLoader.getPromptForFormat("reasoning_$reasoningType") ?: ""
                }
                // Priority 2: Expert mode
                isExpertMode -> {
                    formatPromptLoader.getPromptForFormat("expert") ?: ""
                }
                // Priority 3: Normal mode - no mode-specific prompt
                else -> ""
            }

            // Add format requirements (JSON/XML) if configured
            val formatPrompt = when (config.responseFormat.lowercase()) {
                "json" -> formatPromptLoader.getPromptForFormat("json") ?: ""
                "xml" -> formatPromptLoader.getPromptForFormat("xml") ?: ""
                else -> ""
            }

            // Combine mode prompt with format prompt
            val systemPrompt = if (modePrompt.isNotEmpty() && formatPrompt.isNotEmpty()) {
                "$formatPrompt\n\n$modePrompt"
            } else {
                formatPrompt + modePrompt
            }

            // Add user message to history
            history.add(Message(role = "user", content = userMessage))

            // Get user's temperature setting
            val temperature = settingsManager.getTemperature(chatId)
            logger.info { "Using temperature: $temperature for chatId: $chatId" }

            val request = ClaudeRequest(
                model = config.model,
                maxTokens = config.maxTokens,
                messages = history.toList(),
                system = systemPrompt,
                temperature = temperature
            )

            val httpResponse = client.post(config.apiUrl) {
                contentType(ContentType.Application.Json)
                header("x-api-key", config.key)
                header("anthropic-version", config.version)
                setBody(request)
            }

            if (httpResponse.status.value in 200..299) {
                val response: ClaudeResponse = httpResponse.body()
                logger.info { "Received response from Claude API. Tokens used: ${response.usage.inputTokens + response.usage.outputTokens}" }

                // Combine all content blocks into one message
                val assistantMessage = response.content
                    .filter { it.type == "text" }
                    .joinToString("") { it.text }
                    .takeIf { it.isNotEmpty() } ?: "No response from Claude API"

                // Log the full raw response from Claude
                logger.info { "Raw Claude response (${response.content.size} blocks):\n$assistantMessage" }

                // Add assistant response to history
                history.add(Message(role = "assistant", content = assistantMessage))

                // Parse response based on configured format (always applied)
                val parsedMessage = responseParser.parseResponse(assistantMessage, config.responseFormat)

                // Log the parsed response
                logger.info { "Parsed response:\n$parsedMessage" }

                parsedMessage
            } else {
                val errorResponse: ClaudeErrorResponse = httpResponse.body()
                val errorType = errorResponse.error?.type ?: "unknown"
                val errorMessage = errorResponse.error?.message ?: "No error message"
                logger.error { "Claude API error: $errorType - $errorMessage" }

                // Remove the user message from history on error
                history.removeLastOrNull()

                "Sorry, Claude API returned an error: $errorMessage"
            }
        } catch (e: Exception) {
            logger.error(e) { "Error calling Claude API" }

            // Remove the user message from history on error
            history.removeLastOrNull()

            "Sorry, I encountered an error processing your request: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}
