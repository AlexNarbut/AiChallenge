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
    var defaultTemperature: Double = 0.6, // Default temperature for Claude API (0.0-1.0)
    var expertPrompt: String = "expert_system_prompt", // Filename without .txt extension
    var reasoningPrompts: Map<String, String> = mapOf(
        "basic" to "reasoning_quick_answer_system_prompt",
        "mathematical" to "reasoning_step_by_step_system_prompt",
        "strategic" to "reasoning_prompt_engineer_system_prompt",
        "creative" to "reasoning_expert_panel_system_prompt"
    ) // Reasoning mode prompts - filenames without .txt extension
)
