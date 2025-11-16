package com.aiassist.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Represents a summary of compressed conversation history
 */
@Entity
@Table(name = "chat_summaries", indexes = [
    Index(name = "idx_summary_chat_id", columnList = "chat_id"),
    Index(name = "idx_summary_sequence", columnList = "chat_id,sequence_number")
])
data class ChatSummaryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long,

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    val summaryText: String,

    @Column(name = "messages_summarized", nullable = false)
    val messagesSummarized: Int, // How many messages were compressed into this summary

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "sequence_number", nullable = false)
    val sequenceNumber: Int = 0 // Order of summaries (0 = oldest, higher = newer)
)
