package com.aiassist.bot.service

import com.aiassist.bot.config.TelegramBotConfig
import com.aiassist.bot.model.Message
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class TelegramBotService(
    private val config: TelegramBotConfig,
    private val claudeApiClient: ClaudeApiClient,
    private val pdfGenerator: PdfGenerator
) {
    // Store conversation history per chat
    private val conversationHistory = ConcurrentHashMap<Long, MutableList<Message>>()

    // Store expert mode state per chat
    private val expertMode = ConcurrentHashMap<Long, Boolean>()

    private val bot = bot {
        token = config.token

        dispatch {
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                logger.info { "Initializing bot for user ${message.from?.username}" }

                // Initialize conversation history for this user
                conversationHistory.getOrPut(userId) { mutableListOf() }

                // Verify Claude API client is working
                bot.sendChatAction(chatId, com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                val testResult = runBlocking {
                    try {
                        // Send a simple test message to verify API connectivity
                        claudeApiClient.sendMessage("Hello")
                        true
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to initialize Claude API client" }
                        false
                    }
                }

                val welcomeMessage = if (testResult) {
                    """
                    👋 Welcome to AI Assistant Bot!

                    I'm powered by Claude AI. Just send me a message and I'll respond!

                    Commands:
                    /start - Initialize bot and verify connection
                    /expert - Talk with fitness trainer expert
                    /normal - Switch back to normal mode
                    /clear - Clear conversation history

                    ✅ Claude API connection verified successfully!
                    """.trimIndent()
                } else {
                    """
                    👋 Welcome to AI Assistant Bot!

                    Commands:
                    /start - Initialize bot and verify connection
                    /expert - Talk with fitness trainer expert
                    /normal - Switch back to normal mode
                    /clear - Clear conversation history

                    ❌ Warning: Failed to connect to Claude API. Please check your configuration.
                    """.trimIndent()
                }

                bot.sendMessage(chatId, welcomeMessage)
                logger.info { "Bot initialized for user ${message.from?.username}, API test: ${if (testResult) "passed" else "failed"}" }
            }

            command("clear") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                conversationHistory.remove(userId)

                val clearMessage = "🧹 Conversation history cleared! Starting fresh."
                bot.sendMessage(chatId, clearMessage)
                logger.info { "Cleared conversation history for user ${message.from?.username}" }
            }

            command("expert") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = true
                conversationHistory.remove(userId) // Clear history when switching modes

                val expertMessage = """
                    👨‍⚕️ Expert Mode Activated!

                    You are now talking with a fitness trainer expert. The expert will guide you through creating a personalized training program.

                    Commands:
                    /normal - Switch back to normal mode
                    /clear - Clear conversation and restart
                """.trimIndent()

                bot.sendMessage(chatId, expertMessage)
                logger.info { "Activated expert mode for user ${message.from?.username}" }
            }

            command("normal") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                conversationHistory.remove(userId) // Clear history when switching modes

                val normalMessage = """
                    💬 Normal Mode Activated!

                    You are now in normal conversation mode with Claude AI.

                    Commands:
                    /expert - Switch to expert mode
                    /clear - Clear conversation history
                """.trimIndent()

                bot.sendMessage(chatId, normalMessage)
                logger.info { "Activated normal mode for user ${message.from?.username}" }
            }

            text {
                val userMessage = message.text ?: return@text
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                logger.info { "Received message from user ${message.from?.username}: $userMessage" }

                // Send "typing" action to show bot is processing
                bot.sendChatAction(chatId, com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Get or create conversation history for this chat
                val history = conversationHistory.getOrPut(userId) { mutableListOf() }

                // Check if expert mode is enabled for this user
                val isExpertMode = expertMode[userId] ?: false

                // Get response from Claude API with conversation history
                val response = runBlocking {
                    claudeApiClient.sendMessageWithHistory(userMessage, history, isExpertMode)
                }

                logger.info { "Response length: ${response.length} characters" }

                // Check if response is too long for Telegram message
                if (response.length > config.maxMessageLength) {
                    logger.info { "Response exceeds max length (${config.maxMessageLength}), generating PDF" }

                    try {
                        // Generate PDF
                        val pdfFile = pdfGenerator.generatePdf(
                            content = response,
                            title = if (isExpertMode) "Fitness Training Plan" else "AI Assistant Response"
                        )

                        // Send PDF file
                        bot.sendDocument(
                            chatId = chatId,
                            document = com.github.kotlintelegrambot.entities.TelegramFile.ByFile(pdfFile),
                            caption = "📄 Response was too long, here's a PDF document"
                        )

                        // Clean up temp file
                        pdfFile.delete()

                        logger.info { "PDF sent successfully to user ${message.from?.username}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to generate or send PDF" }
                        // Fallback: send truncated message
                        val truncatedResponse = response.take(config.maxMessageLength - 100) + "\n\n... (response truncated)"
                        bot.sendMessage(chatId, truncatedResponse)
                    }
                } else {
                    // Send normal text message
                    logger.info { "Sending response to user:\n$response" }
                    bot.sendMessage(chatId, response)
                }
            }
        }
    }

    @PostConstruct
    fun start() {
        logger.info { "Starting Telegram bot: ${config.username}" }
        bot.startPolling()
    }

    @PreDestroy
    fun stop() {
        logger.info { "Stopping Telegram bot" }
        bot.stopPolling()
        claudeApiClient.close()
    }
}
