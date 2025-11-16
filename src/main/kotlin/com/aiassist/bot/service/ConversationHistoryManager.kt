package com.aiassist.bot.service

import com.aiassist.bot.entity.ChatMessageEntity
import com.aiassist.bot.entity.ChatSummaryEntity
import com.aiassist.bot.model.Message
import com.aiassist.bot.repository.ChatMessageRepository
import com.aiassist.bot.repository.ChatSummaryRepository
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Manages conversation history with automatic compression using summaries
 * All data is persisted to SQLite database
 *
 * Strategy: Token-based compression with sliding window + summaries
 * - Messages stored in database (chat_messages table)
 * - Summaries stored in database (chat_summaries table)
 * - Compression triggered when token count exceeds threshold
 * - Older messages marked as summarized, newer messages kept active
 */
@Service
class ConversationHistoryManager(
    private val settingsManager: SettingsManager,
    private val messageRepository: ChatMessageRepository,
    private val summaryRepository: ChatSummaryRepository
) {

    companion object {
        const val MAX_SUMMARIES = 2
        const val CHARS_PER_TOKEN = 3
    }

    /**
     * Statistics about conversation history
     */
    data class HistoryStats(
        val totalMessagesProcessed: Int,
        val summariesCount: Int,
        val recentMessagesCount: Int,
        val estimatedTokens: Int,
        val compressionThreshold: Int
    )

    /**
     * Add a message to conversation history (persisted to database)
     */
    @Transactional
    suspend fun addMessage(chatId: Long, message: Message, summaryGenerator: suspend (List<Message>) -> String) {
        val saveStart = System.currentTimeMillis()

        // Save message to database
        val entity = ChatMessageEntity(
            chatId = chatId,
            role = message.role,
            content = message.content
        )
        messageRepository.save(entity)

        val saveDuration = System.currentTimeMillis() - saveStart
        logger.debug { "💾 Saved message to DB for chat $chatId, role: ${message.role} (${saveDuration}ms)" }

        // Check if compression is needed
        val recentMessages = getRecentMessages(chatId)
        val estimatedTokens = estimateTokens(recentMessages)
        val threshold = settingsManager.getHistoryThreshold(chatId)

        if (estimatedTokens > threshold) {
            logger.info { "Compression triggered for chat $chatId. Tokens: $estimatedTokens, threshold: $threshold" }
            compressHistory(chatId, summaryGenerator, forceCompress = false)
        }
    }

    /**
     * Get recent unsummarized messages from database
     */
    fun getRecentMessages(chatId: Long): List<ChatMessageEntity> {
        return messageRepository.findByChatIdAndIsSummarizedFalseOrderByCreatedAtAsc(chatId)
    }

    /**
     * Estimate tokens for messages
     */
    private fun estimateTokens(messages: List<ChatMessageEntity>): Int {
        return messages.sumOf { it.content.length / CHARS_PER_TOKEN }
    }

    /**
     * Compress history: summarize old messages, keep recent ones
     */
    @Transactional
    suspend fun compressHistory(chatId: Long, summaryGenerator: suspend (List<Message>) -> String, forceCompress: Boolean) {
        val allMessages = getRecentMessages(chatId)
        val threshold = settingsManager.getHistoryThreshold(chatId)

        if (allMessages.isEmpty()) {
            logger.warn { "No messages to compress for chat $chatId" }
            return
        }

        // Determine which messages to keep vs summarize
        val messagesToKeep: List<ChatMessageEntity>
        val messagesToSummarize: List<ChatMessageEntity>

        if (forceCompress) {
            // Force: keep last 2, summarize rest
            val keepCount = minOf(2, allMessages.size)
            messagesToKeep = allMessages.takeLast(keepCount)
            messagesToSummarize = allMessages.dropLast(keepCount)
            logger.info { "Force compress: keeping ${messagesToKeep.size}, summarizing ${messagesToSummarize.size}" }
        } else {
            // Auto: use token-based logic
            val targetKeepTokens = threshold / 2
            val kept = mutableListOf<ChatMessageEntity>()
            var accumulatedTokens = 0

            // Take from end (most recent)
            for (msg in allMessages.reversed()) {
                val msgTokens = msg.content.length / CHARS_PER_TOKEN
                if (accumulatedTokens + msgTokens <= targetKeepTokens) {
                    kept.add(0, msg)
                    accumulatedTokens += msgTokens
                } else {
                    break
                }
            }

            // Ensure at least 2 messages kept
            if (kept.size < 2 && allMessages.size >= 2) {
                kept.clear()
                kept.addAll(allMessages.takeLast(2))
            }

            messagesToKeep = kept
            messagesToSummarize = allMessages.take(allMessages.size - kept.size)
            logger.info { "Auto compress: keeping ${messagesToKeep.size}, summarizing ${messagesToSummarize.size}" }
        }

        if (messagesToSummarize.isEmpty()) {
            logger.warn { "No messages to summarize for chat $chatId" }
            return
        }

        try {
            // Convert entities to Messages for API
            val messagesForApi = messagesToSummarize.map { Message(role = it.role, content = it.content) }

            // Generate summary via Claude API
            val summaryText = summaryGenerator(messagesForApi)
            logger.info { "Generated summary for chat $chatId: ${summaryText.take(100)}..." }

            // Get current summary count for sequence number
            val currentSummaryCount = summaryRepository.countByChatId(chatId).toInt()

            // Save summary to database
            val summaryEntity = ChatSummaryEntity(
                chatId = chatId,
                summaryText = summaryText,
                messagesSummarized = messagesToSummarize.size,
                sequenceNumber = currentSummaryCount
            )
            summaryRepository.save(summaryEntity)

            // Mark summarized messages as summarized
            val summarizedIds = messagesToSummarize.mapNotNull { it.id }
            if (summarizedIds.isNotEmpty()) {
                messageRepository.markAsSummarized(chatId, summarizedIds)
            }

            logger.info { "Compression complete for chat $chatId. Summary saved, ${summarizedIds.size} messages marked as summarized" }

            // Check if we need to merge summaries
            val summaryCount = summaryRepository.countByChatId(chatId)
            if (summaryCount > MAX_SUMMARIES) {
                mergeSummaries(chatId, summaryGenerator)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to compress history for chat $chatId" }
        }
    }

    /**
     * Merge multiple summaries into one mega-summary
     */
    @Transactional
    private suspend fun mergeSummaries(chatId: Long, summaryGenerator: suspend (List<Message>) -> String) {
        val summaries = summaryRepository.findByChatIdOrderBySequenceNumberAsc(chatId)

        if (summaries.size <= MAX_SUMMARIES) {
            return
        }

        logger.info { "Merging ${summaries.size} summaries for chat $chatId" }

        try {
            // Combine all summary texts
            val combinedText = summaries.joinToString("\n\n---\n\n") { it.summaryText }

            // Generate mega-summary
            val messages = listOf(
                Message(role = "user", content = "Please provide a concise summary of these conversation summaries:\n\n$combinedText")
            )
            val megaSummary = summaryGenerator(messages)

            // Delete old summaries
            summaries.forEach { summaryRepository.delete(it) }

            // Save new mega-summary
            val newSummary = ChatSummaryEntity(
                chatId = chatId,
                summaryText = megaSummary,
                messagesSummarized = summaries.sumOf { it.messagesSummarized },
                sequenceNumber = 0
            )
            summaryRepository.save(newSummary)

            logger.info { "Merged ${summaries.size} summaries into one for chat $chatId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to merge summaries for chat $chatId" }
        }
    }

    /**
     * Get messages for API request (summaries + recent messages)
     */
    fun getMessagesForApi(chatId: Long): List<Message> {
        val loadStart = System.currentTimeMillis()
        val messages = mutableListOf<Message>()

        // Add summaries as context
        val summaries = summaryRepository.findByChatIdOrderBySequenceNumberAsc(chatId)
        summaries.forEach { summary ->
            messages.add(Message(role = "user", content = "[CONTEXT SUMMARY]: ${summary.summaryText}"))
            messages.add(Message(role = "assistant", content = "Understood. I'll keep this context in mind."))
        }

        // Add recent unsummarized messages
        val recentMessages = getRecentMessages(chatId)
        recentMessages.forEach { msg ->
            messages.add(Message(role = msg.role, content = msg.content))
        }

        val loadDuration = System.currentTimeMillis() - loadStart
        logger.info { "📚 Loaded history for chat $chatId: ${summaries.size} summaries, ${recentMessages.size} recent messages, ${messages.size} total API messages (${loadDuration}ms)" }

        return messages
    }

    /**
     * Get statistics for a chat
     */
    fun getStats(chatId: Long): HistoryStats {
        val summaries = summaryRepository.findByChatIdOrderBySequenceNumberAsc(chatId)
        val recentMessages = getRecentMessages(chatId)
        val allMessagesCount = messageRepository.count()
        val estimatedTokens = estimateTokens(recentMessages) + summaries.sumOf { it.summaryText.length / CHARS_PER_TOKEN }
        val threshold = settingsManager.getHistoryThreshold(chatId)

        return HistoryStats(
            totalMessagesProcessed = allMessagesCount.toInt(),
            summariesCount = summaries.size,
            recentMessagesCount = recentMessages.size,
            estimatedTokens = estimatedTokens,
            compressionThreshold = threshold
        )
    }

    /**
     * Clear all history for a chat (database records deleted)
     */
    @Transactional
    fun clearHistory(chatId: Long) {
        messageRepository.deleteByChatId(chatId)
        summaryRepository.deleteByChatId(chatId)
        logger.info { "Cleared all history from database for chat $chatId" }
    }

    /**
     * Get history display for user
     */
    fun getHistoryDisplay(chatId: Long): String {
        val summaries = summaryRepository.findByChatIdOrderBySequenceNumberAsc(chatId)
        val recentMessages = getRecentMessages(chatId)

        return buildString {
            appendLine("📚 История диалога")
            appendLine()

            if (summaries.isNotEmpty()) {
                appendLine("📝 Резюме предыдущих сообщений (${summaries.size}):")
                appendLine()
                summaries.forEachIndexed { index, summary ->
                    appendLine("--- Резюме ${index + 1} ---")
                    val preview = summary.summaryText.take(500)
                    appendLine(if (summary.summaryText.length > 500) "$preview..." else preview)
                    appendLine()
                }
            }

            if (recentMessages.isNotEmpty()) {
                appendLine("💬 Последние сообщения (${recentMessages.size}):")
                appendLine()
                recentMessages.forEachIndexed { index, msg ->
                    val role = when (msg.role) {
                        "user" -> "👤 Вы"
                        "assistant" -> "🤖 Ассистент"
                        else -> msg.role
                    }
                    val preview = msg.content.take(200)
                    appendLine("${index + 1}. $role:")
                    appendLine(if (msg.content.length > 200) "$preview..." else preview)
                    appendLine()
                }
            }

            if (summaries.isEmpty() && recentMessages.isEmpty()) {
                appendLine("История пуста. Начните диалог!")
            }
        }
    }
}

/**
 * Force compression for a specific chat (manual trigger via /summarize)
 */
suspend fun ConversationHistoryManager.forceCompress(chatId: Long, summaryGenerator: suspend (List<Message>) -> String): Boolean {
    val recentMessages = this.getRecentMessages(chatId)

    if (recentMessages.size < 4) {
        logger.warn { "Not enough messages to compress for chat $chatId" }
        return false
    }

    this.compressHistory(chatId, summaryGenerator, forceCompress = true)
    return true
}
