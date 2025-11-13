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

    // Store model selection per user (chatId -> modelId)
    private val userModels = ConcurrentHashMap<Long, String>()

    // Store max output tokens per user (chatId -> maxTokens)
    private val userMaxTokens = ConcurrentHashMap<Long, Int>()

    // Store history compression threshold per user (chatId -> tokenThreshold)
    private val userHistoryThresholds = ConcurrentHashMap<Long, Int>()

    // Default temperature value
    private val defaultTemperature = 0.6

    // Default model
    private val defaultModel = "claude-sonnet-4-5-20250929"

    // Default max output tokens
    private val defaultMaxTokens = 4096

    // Default history compression threshold (in tokens)
    private val defaultHistoryThreshold = 8000

    // Token limits validation
    companion object {
        const val MIN_TOKENS = 1
        const val MAX_TOKENS = 10000

        // History compression threshold limits
        const val MIN_HISTORY_THRESHOLD = 500
        const val MAX_HISTORY_THRESHOLD = 50000
    }

    /**
     * Available temperature options with descriptions
     */
    enum class TemperatureOption(val value: Double, val description: String) {
        PRECISE(0.0, "Максимально точные и предсказуемые ответы. Идеально для фактических вопросов и технических задач."),
        BALANCED(0.5, "Баланс между креативностью и точностью. Подходит для большинства задач."),
        CREATIVE(1.0, "Более креативные и разнообразные ответы. Хорошо для brainstorming и творческих задач.")
    }

    /**
     * Available Claude models with pricing information
     */
    enum class ModelOption(
        val modelId: String,
        val displayName: String,
        val description: String,
        val inputPricePerMTok: Double,
        val outputPricePerMTok: Double,
        val contextWindow: Int,
        val maxOutput: Int
    ) {
        OPUS_4_1(
            "claude-opus-4-1-20250805",
            "Opus 4.1",
            "Самая мощная модель для сложных задач",
            15.0,
            75.0,
            200_000,
            2_000
        ),
        SONNET_4_5(
            "claude-sonnet-4-5-20250929",
            "Sonnet 4.5",
            "Лучший баланс качества и цены (рекомендуется)",
            3.0,
            15.0,
            200_000,
            2_000
        ),
        HAIKU_4_5(
            "claude-haiku-4-5-20251001",
            "Haiku 4.5",
            "Быстрая и экономичная модель",
            1.0,
            5.0,
            200_000,
            2_000
        );

        /**
         * Calculate cost for given tokens
         */
        fun calculateCost(inputTokens: Int, outputTokens: Int): Double {
            val inputCost = (inputTokens / 1_000_000.0) * inputPricePerMTok
            val outputCost = (outputTokens / 1_000_000.0) * outputPricePerMTok
            return inputCost + outputCost
        }

        companion object {
            fun fromModelId(modelId: String): ModelOption? {
                return entries.find { it.modelId == modelId }
            }
        }
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

    /**
     * Set model for a user
     */
    fun setModel(chatId: Long, modelId: String) {
        require(ModelOption.entries.any { it.modelId == modelId }) { "Invalid model ID" }
        userModels[chatId] = modelId
    }

    /**
     * Get model for a user (returns default if not set)
     */
    fun getModel(chatId: Long): String {
        return userModels.getOrDefault(chatId, defaultModel)
    }

    /**
     * Get model option by ID
     */
    fun getModelOption(modelId: String): ModelOption? {
        return ModelOption.fromModelId(modelId)
    }

    /**
     * Get all available model options
     */
    fun getAllModelOptions(): List<ModelOption> {
        return ModelOption.entries
    }

    /**
     * Reset model to default for a user
     */
    fun resetModel(chatId: Long) {
        userModels.remove(chatId)
    }

    /**
     * Set max output tokens for a user
     * @throws IllegalArgumentException if tokens are out of range
     */
    fun setMaxTokens(chatId: Long, maxTokens: Int) {
        require(maxTokens in MIN_TOKENS..MAX_TOKENS) {
            "Max tokens must be between $MIN_TOKENS and $MAX_TOKENS"
        }
        userMaxTokens[chatId] = maxTokens
    }

    /**
     * Get max output tokens for a user (returns default if not set)
     */
    fun getMaxTokens(chatId: Long): Int {
        return userMaxTokens.getOrDefault(chatId, defaultMaxTokens)
    }

    /**
     * Reset max tokens to default for a user
     */
    fun resetMaxTokens(chatId: Long) {
        userMaxTokens.remove(chatId)
    }

    /**
     * Validate if token value is in allowed range
     */
    fun isValidTokenValue(tokens: Int): Boolean {
        return tokens in MIN_TOKENS..MAX_TOKENS
    }

    /**
     * Set history compression threshold for a user (in tokens)
     * @throws IllegalArgumentException if threshold is out of range
     */
    fun setHistoryThreshold(chatId: Long, threshold: Int) {
        require(threshold in MIN_HISTORY_THRESHOLD..MAX_HISTORY_THRESHOLD) {
            "History threshold must be between $MIN_HISTORY_THRESHOLD and $MAX_HISTORY_THRESHOLD"
        }
        userHistoryThresholds[chatId] = threshold
    }

    /**
     * Get history compression threshold for a user (returns default if not set)
     */
    fun getHistoryThreshold(chatId: Long): Int {
        return userHistoryThresholds.getOrDefault(chatId, defaultHistoryThreshold)
    }

    /**
     * Reset history threshold to default for a user
     */
    fun resetHistoryThreshold(chatId: Long) {
        userHistoryThresholds.remove(chatId)
    }

    /**
     * Validate if history threshold value is in allowed range
     */
    fun isValidHistoryThreshold(threshold: Int): Boolean {
        return threshold in MIN_HISTORY_THRESHOLD..MAX_HISTORY_THRESHOLD
    }
}
