package com.aiassist.bot.service

import com.aiassist.bot.config.TelegramBotConfig
import com.aiassist.bot.model.Message
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.ChatId
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class TelegramBotService(
    private val config: TelegramBotConfig,
    private val claudeApiClient: ClaudeApiClient,
    private val pdfGenerator: PdfGenerator,
    private val settingsManager: SettingsManager
) {
    // Store conversation history per chat
    private val conversationHistory = ConcurrentHashMap<Long, MutableList<Message>>()

    // Store expert mode state per chat
    private val expertMode = ConcurrentHashMap<Long, Boolean>()

    // Store reasoning mode state per chat (null = not in reasoning mode, value = reasoning type)
    private val reasoningMode = ConcurrentHashMap<Long, String?>()

    private val bot = bot {
        token = config.token

        dispatch {
            command("start") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                logger.info { "Initializing bot for user ${message.from?.username}" }

                // Initialize conversation history for this user
                conversationHistory.getOrPut(userId) { mutableListOf() }

                // Verify Claude API client is working
                bot.sendChatAction(chatId, com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                val testResult = runBlocking {
                    try {
                        // Send a simple test message to verify API connectivity
                        claudeApiClient.sendMessage("Hello", userId)
                        true
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to initialize Claude API client" }
                        false
                    }
                }

                val welcomeMessage = if (testResult) {
                    """
                    👋 Добро пожаловать в AI Assistant Bot!

                    Я работаю на базе Claude AI. Просто отправьте мне сообщение, и я отвечу!

                    Команды:
                    /start - Инициализация бота и проверка соединения
                    /expert - Беседа с экспертом-фитнес тренером
                    /reasoning - Решение логических задач со специализированными режимами
                    /normal - Вернуться в обычный режим
                    /settings - ⚙️ Настройки (температура модели)
                    /clear - Очистить историю разговора

                    ✅ Соединение с Claude API успешно проверено!
                    """.trimIndent()
                } else {
                    """
                    👋 Добро пожаловать в AI Assistant Bot!

                    Команды:
                    /start - Инициализация бота и проверка соединения
                    /expert - Беседа с экспертом-фитнес тренером
                    /reasoning - Решение логических задач со специализированными режимами
                    /normal - Вернуться в обычный режим
                    /settings - ⚙️ Настройки (температура модели)
                    /clear - Очистить историю разговора

                    ❌ Внимание: Не удалось подключиться к Claude API. Проверьте конфигурацию.
                    """.trimIndent()
                }

                bot.sendMessage(chatId, welcomeMessage)
                logger.info { "Bot initialized for user ${message.from?.username}, API test: ${if (testResult) "passed" else "failed"}" }
            }

            command("clear") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                conversationHistory.remove(userId)

                val clearMessage = "🧹 История разговора очищена! Начинаем с чистого листа."
                bot.sendMessage(chatId, clearMessage)
                logger.info { "Cleared conversation history for user ${message.from?.username}" }
            }

            command("expert") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = true
                conversationHistory.remove(userId) // Clear history when switching modes

                val expertMessage = """
                    👨‍⚕️ Режим эксперта активирован!

                    Теперь вы общаетесь с экспертом-фитнес тренером. Эксперт поможет вам создать персональную программу тренировок.

                    Команды:
                    /normal - Вернуться в обычный режим
                    /clear - Очистить разговор и начать заново
                """.trimIndent()

                bot.sendMessage(chatId, expertMessage)
                logger.info { "Activated expert mode for user ${message.from?.username}" }
            }

            command("normal") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode.remove(userId)
                conversationHistory.remove(userId) // Clear history when switching modes

                val normalMessage = """
                    💬 Обычный режим активирован!

                    Теперь вы в обычном режиме разговора с Claude AI.

                    Команды:
                    /expert - Переключиться в режим эксперта
                    /reasoning - Войти в режим рассуждений
                    /clear - Очистить историю разговора
                """.trimIndent()

                bot.sendMessage(chatId, normalMessage)
                logger.info { "Activated normal mode for user ${message.from?.username}" }
            }

            command("reasoning") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode.remove(userId)
                conversationHistory.remove(userId)

                val reasoningMessage = """
                    🧠 Reasoning Mode

                    Выберите режим решения логической задачи:

                    /quick_answer - ⚡ Быстрый ответ (прямо и по делу)
                    /step_by_step - 📊 Пошаговый ответ (детальное объяснение)
                    /prompt_engineer - 🎯 Составление промпта (для другой LLM)
                    /expert_panel - 👥 Мнение экспертов (4 специалиста)

                    /normal - Вернуться в обычный режим
                """.trimIndent()

                bot.sendMessage(chatId, reasoningMessage)
                logger.info { "Opened reasoning mode menu for user ${message.from?.username}" }
            }

            command("settings") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                val currentTemp = settingsManager.getTemperature(userId)
                val currentOption = settingsManager.getTemperatureOption(currentTemp)
                val currentTempStr = currentOption?.let { "Текущая: ${it.name} (${it.value})" } ?: "Текущая: $currentTemp"

                val settingsMessage = """
                    ⚙️ Настройки

                    $currentTempStr

                    Выберите температуру модели:

                    /temp_0 - 🎯 Точная (0.0)
                    Максимально точные и предсказуемые ответы. Идеально для фактических вопросов и технических задач.

                    /temp_05 - ⚖️ Сбалансированная (0.5)
                    Баланс между креативностью и точностью. Подходит для большинства задач.

                    /temp_1 - 🎨 Креативная (1.0)
                    Более креативные и разнообразные ответы. Хорошо для brainstorming и творческих задач.
                """.trimIndent()

                bot.sendMessage(chatId, settingsMessage)
                logger.info { "Opened settings menu for user ${message.from?.username}" }
            }

            command("temp_0") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                settingsManager.setTemperature(userId, 0.0)

                val confirmMessage = """
                    ✅ Температура установлена: Точная (0.0)

                    Теперь ответы будут максимально точными и предсказуемыми.
                """.trimIndent()

                bot.sendMessage(chatId, confirmMessage)
                logger.info { "Set temperature to 0.0 for user ${message.from?.username}" }
            }

            command("temp_05") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                settingsManager.setTemperature(userId, 0.5)

                val confirmMessage = """
                    ✅ Температура установлена: Сбалансированная (0.5)

                    Теперь ответы будут сбалансированы между креативностью и точностью.
                """.trimIndent()

                bot.sendMessage(chatId, confirmMessage)
                logger.info { "Set temperature to 0.5 for user ${message.from?.username}" }
            }

            command("temp_1") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                settingsManager.setTemperature(userId, 1.0)

                val confirmMessage = """
                    ✅ Температура установлена: Креативная (1.0)

                    Теперь ответы будут более креативными и разнообразными.
                """.trimIndent()

                bot.sendMessage(chatId, confirmMessage)
                logger.info { "Set temperature to 1.0 for user ${message.from?.username}" }
            }

            command("quick_answer") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode[userId] = "basic"
                conversationHistory.remove(userId)

                val responseMessage = """
                    ⚡ Режим "Быстрый ответ" активирован

                    Этот режим дает прямые и лаконичные ответы на логические задачи без избыточных рассуждений.

                    Задайте вашу логическую задачу, и я дам точный ответ.

                    Команды:
                    /reasoning - Назад в меню режимов
                    /normal - Вернуться в обычный режим
                """.trimIndent()

                bot.sendMessage(chatId, responseMessage)
                logger.info { "Activated basic reasoning mode for user ${message.from?.username}" }
            }

            command("step_by_step") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode[userId] = "mathematical"
                conversationHistory.remove(userId)

                val responseMessage = """
                    📊 Режим "Пошаговый ответ" активирован

                    Этот режим решает задачи пошагово с детальными рассуждениями, объясняя логику каждого этапа.

                    Задайте вашу логическую задачу, и я разберу её по шагам.

                    Команды:
                    /reasoning - Назад в меню режимов
                    /normal - Вернуться в обычный режим
                """.trimIndent()

                bot.sendMessage(chatId, responseMessage)
                logger.info { "Activated mathematical reasoning mode for user ${message.from?.username}" }
            }

            command("prompt_engineer") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode[userId] = "strategic"
                conversationHistory.remove(userId)

                val responseMessage = """
                    🎯 Режим "Составление промпта" активирован

                    Этот режим создает оптимальные промпты для других языковых моделей, которые помогут решить вашу задачу.

                    Задайте вашу логическую задачу, и я создам для неё идеальный промпт.

                    Команды:
                    /reasoning - Назад в меню режимов
                    /normal - Вернуться в обычный режим
                """.trimIndent()

                bot.sendMessage(chatId, responseMessage)
                logger.info { "Activated strategic reasoning mode for user ${message.from?.username}" }
            }

            command("expert_panel") {
                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                expertMode[userId] = false
                reasoningMode[userId] = "creative"
                conversationHistory.remove(userId)

                val responseMessage = """
                    👥 Режим "Мнение экспертов" активирован

                    Панель из 4 экспертов решит вашу задачу:
                    • Математик-логик
                    • Когнитивный психолог
                    • Программист-алгоритмист
                    • Философ-аналитик

                    Задайте вашу логическую задачу, и эксперты выскажут свои мнения.

                    Команды:
                    /reasoning - Назад в меню режимов
                    /normal - Вернуться в обычный режим
                """.trimIndent()

                bot.sendMessage(chatId, responseMessage)
                logger.info { "Activated creative reasoning mode for user ${message.from?.username}" }
            }

            text {
                val userMessage = message.text ?: return@text

                // Skip command messages - they should be handled by command handlers only
                if (userMessage.startsWith("/")) {
                    return@text
                }

                val chatId = ChatId.fromId(message.chat.id)
                val userId = message.chat.id

                logger.info { "Received message from user ${message.from?.username}: $userMessage" }

                // Send "typing" action to show bot is processing
                bot.sendChatAction(chatId, com.github.kotlintelegrambot.entities.ChatAction.TYPING)

                // Get or create conversation history for this chat
                val history = conversationHistory.getOrPut(userId) { mutableListOf() }

                // Check if expert mode is enabled for this user
                val isExpertMode = expertMode[userId] ?: false

                // Check if reasoning mode is enabled for this user
                val reasoningType = reasoningMode[userId]

                // Get response from Claude API with conversation history
                val response = runBlocking {
                    claudeApiClient.sendMessageWithHistory(userMessage, history, userId, isExpertMode, reasoningType)
                }

                logger.info { "Response length: ${response.length} characters" }

                // Check if response is too long for Telegram message
                if (response.length > config.maxMessageLength) {
                    logger.info { "Response exceeds max length (${config.maxMessageLength}), splitting into multiple messages" }

                    // Split response into chunks
                    val chunks = mutableListOf<String>()
                    var remainingText = response

                    while (remainingText.isNotEmpty()) {
                        if (remainingText.length <= config.maxMessageLength) {
                            // Last chunk
                            chunks.add(remainingText)
                            break
                        } else {
                            // Find a good breaking point (prefer newline, then space)
                            var breakPoint = config.maxMessageLength
                            val searchRange = remainingText.substring(0, config.maxMessageLength)

                            // Try to break at last newline
                            val lastNewline = searchRange.lastIndexOf('\n')
                            if (lastNewline > config.maxMessageLength / 2) {
                                breakPoint = lastNewline + 1
                            } else {
                                // Try to break at last space
                                val lastSpace = searchRange.lastIndexOf(' ')
                                if (lastSpace > config.maxMessageLength / 2) {
                                    breakPoint = lastSpace + 1
                                }
                            }

                            chunks.add(remainingText.substring(0, breakPoint))
                            remainingText = remainingText.substring(breakPoint)
                        }
                    }

                    logger.info { "Sending response in ${chunks.size} parts" }

                    // Send all chunks
                    chunks.forEachIndexed { index, chunk ->
                        val partHeader = if (chunks.size > 1) "📝 Part ${index + 1}/${chunks.size}\n\n" else ""
                        bot.sendMessage(chatId, partHeader + chunk)

                        // Small delay between messages to avoid rate limits
                        if (index < chunks.size - 1) {
                            Thread.sleep(100)
                        }
                    }

                    logger.info { "Sent ${chunks.size} message parts to user ${message.from?.username}" }
                } else {
                    // Send normal text message
                    logger.info { "Sending response to user:\n$response" }
                    bot.sendMessage(chatId, response)
                }
            }
        }
    }

    @PostConstruct
    fun start() {
        logger.info { "Starting Telegram bot: ${config.username}" }
        bot.startPolling()
    }

    @PreDestroy
    fun stop() {
        logger.info { "Stopping Telegram bot" }
        bot.stopPolling()
        claudeApiClient.close()
    }
}
