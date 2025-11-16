package com.aiassist.bot.entity

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Represents a single message in a conversation
 */
@Entity
@Table(name = "chat_messages", indexes = [
    Index(name = "idx_chat_id", columnList = "chat_id"),
    Index(name = "idx_chat_id_summarized", columnList = "chat_id,is_summarized")
])
data class ChatMessageEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,

    @Column(name = "chat_id", nullable = false)
    val chatId: Long,

    @Column(name = "role", nullable = false, length = 20)
    val role: String, // "user" or "assistant"

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "is_summarized", nullable = false)
    val isSummarized: Boolean = false // true if this message has been compressed into a summary
)
