package com.aiassist.bot

import com.aiassist.bot.config.ClaudeApiConfig
import com.aiassist.bot.config.TelegramBotConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(TelegramBotConfig::class, ClaudeApiConfig::class)
class AiAssistBotApplication

fun main(args: Array<String>) {
    runApplication<AiAssistBotApplication>(*args)
}
