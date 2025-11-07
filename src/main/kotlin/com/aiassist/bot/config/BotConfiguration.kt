package com.aiassist.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramBotConfig(
    var token: String = "",
    var username: String = "",
    var maxMessageLength: Int = 4000 // Max characters before switching to PDF
)

@ConfigurationProperties(prefix = "claude.api")
data class ClaudeApiConfig(
    var key: String = "",
    var model: String = "",
    var maxTokens: Int = 1024,
    var apiUrl: String = "",
    var version: String = "",
    var responseFormat: String = "text", // Options: "text", "json", "xml"
    var expertPrompt: String = "expert_system_prompt" // Filename without .txt extension
)
