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
    private val claudeApiClient: ClaudeApiClient
) {
    // Store conversation history per chat
    private val conversationHistory = ConcurrentHashMap<Long, MutableList<Message>>()

    private val bot = bot {
        token = config.token

        dispatch {
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                val welcomeMessage = """
                    👋 Welcome to AI Assistant Bot!

                    I'm powered by Claude AI. Just send me a message and I'll respond!

                    Commands:
                    /start - Show this welcome message
                    /clear - Clear conversation history
                """.trimIndent()

                bot.sendMessage(chatId, welcomeMessage)
                logger.info { "Sent welcome message to user ${message.from?.username}" }
            }

            command("clear") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                conversationHistory.remove(userId)

                val clearMessage = "🧹 Conversation history cleared! Starting fresh."
                bot.sendMessage(chatId, clearMessage)
                logger.info { "Cleared conversation history for user ${message.from?.username}" }
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

                // Get response from Claude API with conversation history
                val response = runBlocking {
                    claudeApiClient.sendMessageWithHistory(userMessage, history)
                }

                logger.info { "Sending response to user: ${response.take(50)}..." }

                // Send response back to user
                bot.sendMessage(chatId, response)
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
