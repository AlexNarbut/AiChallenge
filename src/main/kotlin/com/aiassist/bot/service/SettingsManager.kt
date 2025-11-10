package com.aiassist.bot.service

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing user-specific settings
 */
@Service
class SettingsManager {
    // Store temperature settings per user (chatId -> temperature)
    private val userTemperatures = ConcurrentHashMap<Long, Double>()

    // Default temperature value
    private val defaultTemperature = 1.0

    /**
     * Available temperature options with descriptions
     */
    enum class TemperatureOption(val value: Double, val description: String) {
        PRECISE(0.0, "Максимально точные и предсказуемые ответы. Идеально для фактических вопросов и технических задач."),
        BALANCED(0.5, "Баланс между креативностью и точностью. Подходит для большинства задач."),
        CREATIVE(1.0, "Более креативные и разнообразные ответы. Хорошо для brainstorming и творческих задач.")
    }

    /**
     * Set temperature for a user
     */
    fun setTemperature(chatId: Long, temperature: Double) {
        require(temperature in 0.0..1.0) { "Temperature must be between 0.0 and 1.0" }
        userTemperatures[chatId] = temperature
    }

    /**
     * Get temperature for a user (returns default if not set)
     */
    fun getTemperature(chatId: Long): Double {
        return userTemperatures.getOrDefault(chatId, defaultTemperature)
    }

    /**
     * Reset temperature to default for a user
     */
    fun resetTemperature(chatId: Long) {
        userTemperatures.remove(chatId)
    }

    /**
     * Get temperature option by value
     */
    fun getTemperatureOption(temperature: Double): TemperatureOption? {
        return TemperatureOption.entries.find { it.value == temperature }
    }

    /**
     * Get all available temperature options
     */
    fun getAllTemperatureOptions(): List<TemperatureOption> {
        return TemperatureOption.entries
    }
}
