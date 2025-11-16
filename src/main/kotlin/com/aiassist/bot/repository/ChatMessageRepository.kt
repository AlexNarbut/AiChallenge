package com.aiassist.bot.repository

import com.aiassist.bot.entity.ChatMessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, Long> {

    /**
     * Find all messages for a specific chat that haven't been summarized yet
     */
    fun findByChatIdAndIsSummarizedFalseOrderByCreatedAtAsc(chatId: Long): List<ChatMessageEntity>

    /**
     * Find recent N messages for a chat (not summarized)
     */
    fun findTopNByChatIdAndIsSummarizedFalseOrderByCreatedAtDesc(chatId: Long): List<ChatMessageEntity>

    /**
     * Count unsummarized messages for a chat
     */
    fun countByChatIdAndIsSummarizedFalse(chatId: Long): Long

    /**
     * Mark messages as summarized
     */
    @Modifying
    @Query("UPDATE ChatMessageEntity m SET m.isSummarized = true WHERE m.chatId = :chatId AND m.id IN :messageIds")
    fun markAsSummarized(chatId: Long, messageIds: List<Long>)

    /**
     * Delete all messages for a chat
     */
    fun deleteByChatId(chatId: Long)
}
