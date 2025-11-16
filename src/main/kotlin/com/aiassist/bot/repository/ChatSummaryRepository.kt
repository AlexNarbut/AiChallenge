package com.aiassist.bot.repository

import com.aiassist.bot.entity.ChatSummaryEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatSummaryRepository : JpaRepository<ChatSummaryEntity, Long> {

    /**
     * Find all summaries for a specific chat, ordered by sequence
     */
    fun findByChatIdOrderBySequenceNumberAsc(chatId: Long): List<ChatSummaryEntity>

    /**
     * Count summaries for a chat
     */
    fun countByChatId(chatId: Long): Long

    /**
     * Delete all summaries for a chat
     */
    fun deleteByChatId(chatId: Long)
}
