package com.aiassist.bot.service

import com.aiassist.bot.config.ClaudeApiConfig
import com.aiassist.bot.model.ClaudeRequest
import com.aiassist.bot.model.ClaudeResponse
import com.aiassist.bot.model.ClaudeErrorResponse
import com.aiassist.bot.model.ClaudeResponseWithMetrics
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
    ): ClaudeResponseWithMetrics {
        return sendMessageWithHistory(userMessage, mutableListOf(), chatId, isExpertMode, reasoningType)
    }

    suspend fun sendMessageWithHistory(
        userMessage: String,
        history: MutableList<Message>,
        chatId: Long,
        isExpertMode: Boolean = false,
        reasoningType: String? = null
    ): ClaudeResponseWithMetrics {
        val startTime = System.currentTimeMillis()
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

            // Get user's model selection
            val selectedModel = settingsManager.getModel(chatId)
            logger.info { "Using model: $selectedModel for chatId: $chatId" }

            // Get user's max tokens setting
            val maxTokens = settingsManager.getMaxTokens(chatId)
            logger.info { "Using max tokens: $maxTokens for chatId: $chatId" }

            val request = ClaudeRequest(
                model = selectedModel,
                maxTokens = maxTokens,
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
                val responseTime = System.currentTimeMillis() - startTime

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

                // Determine if we should parse structured formats
                // For JSON/XML formats, require minimum tokens to avoid incomplete responses
                val shouldParseStructuredFormat = when (config.responseFormat.lowercase()) {
                    "json", "xml" -> maxTokens >= 1000 // Minimum 1000 tokens for structured formats
                    else -> true
                }

                // Parse response based on configured format
                val parsedMessage = if (shouldParseStructuredFormat) {
                    responseParser.parseResponse(assistantMessage, config.responseFormat)
                } else {
                    logger.warn { "Skipping structured format parsing due to low token limit ($maxTokens). Minimum required: 1000" }
                    assistantMessage // Return raw response
                }

                // Log the parsed response
                logger.info { "Parsed response:\n$parsedMessage" }

                // Calculate cost based on model used
                val modelOption = settingsManager.getModelOption(selectedModel)
                val cost = modelOption?.calculateCost(response.usage.inputTokens, response.usage.outputTokens) ?: 0.0

                ClaudeResponseWithMetrics(
                    message = parsedMessage,
                    inputTokens = response.usage.inputTokens,
                    outputTokens = response.usage.outputTokens,
                    responseTimeMs = responseTime,
                    cost = cost,
                    modelUsed = response.model
                )
            } else {
                val errorResponse: ClaudeErrorResponse = httpResponse.body()
                val errorType = errorResponse.error?.type ?: "unknown"
                val errorMessage = errorResponse.error?.message ?: "No error message"
                val responseTime = System.currentTimeMillis() - startTime
                logger.error { "Claude API error: $errorType - $errorMessage" }

                // Remove the user message from history on error
                history.removeLastOrNull()

                ClaudeResponseWithMetrics(
                    message = "Sorry, Claude API returned an error: $errorMessage",
                    inputTokens = 0,
                    outputTokens = 0,
                    responseTimeMs = responseTime,
                    cost = 0.0,
                    modelUsed = settingsManager.getModel(chatId)
                )
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            logger.error(e) { "Error calling Claude API" }

            // Remove the user message from history on error
            history.removeLastOrNull()

            ClaudeResponseWithMetrics(
                message = "Sorry, I encountered an error processing your request: ${e.message}",
                inputTokens = 0,
                outputTokens = 0,
                responseTimeMs = responseTime,
                cost = 0.0,
                modelUsed = settingsManager.getModel(chatId)
            )
        }
    }

    /**
     * Generate a summary of conversation messages
     * Used for history compression
     */
    suspend fun generateSummary(messages: List<Message>, chatId: Long): String {
        return try {
            logger.info { "Generating summary for ${messages.size} messages for chatId: $chatId" }

            // Create a prompt for summarization
            val conversationText = messages.joinToString("\n") { message ->
                "${message.role.uppercase()}: ${message.content}"
            }

            val summaryPrompt = """
                Please provide a concise summary of the following conversation.
                Focus on key topics, decisions, and important information discussed.
                Keep the summary brief but informative (2-4 sentences).

                Conversation:
                $conversationText
            """.trimIndent()

            val summaryRequest = ClaudeRequest(
                model = settingsManager.getModel(chatId),
                maxTokens = 500, // Short summary
                messages = listOf(Message(role = "user", content = summaryPrompt)),
                temperature = 0.3 // Lower temperature for consistent summaries
            )

            val httpResponse = client.post(config.apiUrl) {
                contentType(ContentType.Application.Json)
                header("x-api-key", config.key)
                header("anthropic-version", config.version)
                setBody(summaryRequest)
            }

            if (httpResponse.status.value in 200..299) {
                val response: ClaudeResponse = httpResponse.body()
                val summary = response.content
                    .filter { it.type == "text" }
                    .joinToString("") { it.text }

                logger.info { "Generated summary (${summary.length} chars): ${summary.take(100)}..." }
                summary
            } else {
                logger.error { "Failed to generate summary: ${httpResponse.status}" }
                "Summary generation failed"
            }
        } catch (e: Exception) {
            logger.error(e) { "Error generating summary" }
            "Summary generation failed: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}
