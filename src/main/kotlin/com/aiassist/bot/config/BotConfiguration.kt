package com.aiassist.bot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramBotConfig(
    var token: String = "",
    var username: String = ""
)

@ConfigurationProperties(prefix = "claude.api")
data class ClaudeApiConfig(
    var key: String = "",
    var model: String = "",
    var maxTokens: Int = 1024,
    var apiUrl: String = "",
    var version: String = ""
)
