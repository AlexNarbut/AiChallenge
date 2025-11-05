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
    private val responseParser: ResponseParser
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    suspend fun sendMessage(userMessage: String): String {
        return sendMessageWithHistory(userMessage, mutableListOf())
    }

    suspend fun sendMessageWithHistory(userMessage: String, history: MutableList<Message>): String {
        return try {
            logger.info { "Sending message to Claude API with ${history.size} previous messages and format: ${config.responseFormat}" }

            // Get system prompt based on response format

            val responseFormatPrompt = when (config.responseFormat.lowercase()) {
                "json" -> formatPromptLoader.getPromptForFormat("json")
                "xml" -> formatPromptLoader.getPromptForFormat("xml")
                else -> ""
            }

            val systemPrompt = responseFormatPrompt

            // Add user message to history
            history.add(Message(role = "user", content = userMessage))

            val request = ClaudeRequest(
                model = config.model,
                maxTokens = config.maxTokens,
                messages = history.toList(),
                system = systemPrompt
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

                val assistantMessage = response.content.firstOrNull()?.text ?: "No response from Claude API"

                // Log the full raw response from Claude
                logger.info { "Raw Claude response:\n$assistantMessage" }

                // Add assistant response to history
                history.add(Message(role = "assistant", content = assistantMessage))

                // Parse response based on format
                val parsedMessage = responseParser.parseResponse(assistantMessage, config.responseFormat)

                // Log the parsed response
                logger.info { "Parsed response:\n$parsedMessage" }

                parsedMessage
            } else {
                val errorResponse: ClaudeErrorResponse = httpResponse.body()
                logger.error { "Claude API error: ${errorResponse.error.type} - ${errorResponse.error.message}" }

                // Remove the user message from history on error
                history.removeLastOrNull()

                "Sorry, Claude API returned an error: ${errorResponse.error.message}"
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
