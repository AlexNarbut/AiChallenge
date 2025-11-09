# Troubleshooting - Диагностика проблем

## 🔍 Частые проблемы и решения

---

## 1. Ошибка парсинга JSON/XML

### Симптомы:
```
❌ Failed to parse JSON response.
Error: Unexpected character...
```

### Причины:

#### A. Claude добавляет текст до/после JSON
**Решение:** Проверить format prompts:
- `dataFormat/json_format_requirements.txt`
- `dataFormat/xml_format_requirements.txt`

Убедиться что там указано: "CRITICAL! First character MUST be {"

#### B. Невалидный JSON
**Диагностика:**
1. Проверить логи - там будет полный response
2. Проверить preview в сообщении об ошибке
3. Найти проблемное место (незакрытые кавычки, неэкранированные символы)

**Решение:**
- Добавить примеры экранирования в format prompts
- Увеличить `max-tokens` если ответ обрезается

#### C. Ответ разбит на несколько content blocks
**Статус:** ✅ Уже решено (ClaudeApiClient объединяет все blocks)

**Проверка:** В логах должно быть `Raw Claude response (N blocks):`

---

## 2. Длинные ответы не разбиваются на части

### Симптомы:
- Получаете один длинный ответ вместо нескольких частей
- Telegram обрезает сообщение

### Диагностика:

Проверить конфигурацию:
```yaml
telegram:
  max-message-length: 4000
```

Или переменную окружения:
```bash
echo $TELEGRAM_MAX_MESSAGE_LENGTH
```

### Решение:
Установить правильный лимит (Telegram max = 4096, рекомендуется 4000):
```bash
TELEGRAM_MAX_MESSAGE_LENGTH=4000 ./gradlew bootRun
```

---

## 3. Response Format не применяется

### Симптомы:
- Ожидаете JSON, получаете обычный текст
- Format prompt игнорируется

### Диагностика:

**Проверка 1: Конфигурация**
```bash
echo $CLAUDE_RESPONSE_FORMAT
```

Должно быть: `json`, `xml` или `text`

**Проверка 2: Логи**
```
INFO: Sending message to Claude API with... format: json
```

**Проверка 3: System prompt**
Логи должны показывать что format prompt добавлен.

### Решение:

Установить правильный формат:
```bash
CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun
```

---

## 4. History не очищается при смене режима

### Симптомы:
- Контекст от предыдущего режима попадает в новый
- Несовместимые ответы

### Диагностика:

Проверить код режима в `TelegramBotService.kt`:
```kotlin
command("режим") {
    val userId = message.chat.id

    // Должна быть эта строка:
    conversationHistory.remove(userId)
}
```

### Решение:
✅ Уже решено - все команды очищают историю
Документация: `HISTORY_CLEARING_VERIFICATION.md`

---

## 5. Content blocks не объединяются

### Симптомы:
- Получаете только начало ответа Claude
- Длинные ответы обрезаются

### Диагностика:

Проверить логи:
```
INFO: Raw Claude response (1 blocks):
```

Если blocks = 1, проблема не в этом.

Если blocks > 1, но ответ всё равно короткий:
```
INFO: Raw Claude response (3 blocks):
```

Проверить `ClaudeApiClient.kt:98-105`:
```kotlin
val assistantMessage = response.content
    .filter { it.type == "text" }
    .joinToString("") { it.text }
```

### Решение:
✅ Уже решено - все blocks объединяются
Документация: `CONTENT_BLOCKS_FIX.md`

---

## 6. Mode prompt и Format prompt не комбинируются

### Симптомы:
- В reasoning режиме не работает JSON format
- Expert mode игнорирует response format

### Диагностика:

Проверить логи при отправке запроса:
```
DEBUG: System prompt: [должно быть Mode Prompt + Format Prompt]
```

### Решение:
✅ Уже решено - промпты комбинируются
Документация: `RESPONSE_FORMAT_INTEGRATION_SUMMARY.md`

---

## 7. PDF генерируется вместо разбиения на части

### Симптомы:
- Длинные ответы приходят как PDF
- Ожидали несколько сообщений

### Диагностика:

Проверить `TelegramBotService.kt:308-360` - там должна быть логика разбиения, а не PDF.

Если там PDF, значит старая версия кода.

### Решение:
✅ Уже решено - PDF заменен на message splitting
Документация: `MESSAGE_SPLITTING.md`

---

## 8. Reasoning режимы не работают

### Симптомы:
- Команды `/quick_answer`, `/step_by_step` и т.д. не отвечают
- Получаете обычные ответы

### Диагностика:

**Проверка 1: Команды зарегистрированы?**
`TelegramBotService.kt` должен содержать:
```kotlin
command("quick_answer") { ... }
command("step_by_step") { ... }
command("prompt_engineer") { ... }
command("expert_panel") { ... }
```

**Проверка 2: Промпты загружены?**
```
INFO: Loading reasoning prompts...
```

**Проверка 3: Файлы существуют?**
```bash
ls src/main/resources/systemPrompts/reasoning_*.txt
```

### Решение:
Проверить что все файлы на месте:
- `reasoning_quick_answer_system_prompt.txt`
- `reasoning_step_by_step_system_prompt.txt`
- `reasoning_prompt_engineer_system_prompt.txt`
- `reasoning_expert_panel_system_prompt.txt`

Документация: `REASONING_MODE.md`

---

## 🔧 Общие рекомендации

### 1. Проверить логи

**Debug mode:**
```yaml
# application.yml
logging:
  level:
    com.aiassist.bot: DEBUG
```

**Важные логи:**
- `Sending message to Claude API with...` - параметры запроса
- `Raw Claude response (N blocks):` - ответ Claude
- `Parsed response:` - результат парсинга
- `Sending response in N parts` - разбиение на части

### 2. Проверить конфигурацию

```bash
# Environment variables
echo $TELEGRAM_BOT_TOKEN
echo $CLAUDE_API_KEY
echo $CLAUDE_RESPONSE_FORMAT
echo $TELEGRAM_MAX_MESSAGE_LENGTH
```

### 3. Проверить файлы

```bash
# System prompts
ls -la src/main/resources/systemPrompts/
ls -la src/main/resources/dataFormat/
```

### 4. Пересобрать проект

```bash
JAVA_HOME=/Users/alexnarbut/Library/Java/JavaVirtualMachines/corretto-17.0.7/Contents/Home ./gradlew clean build
```

---

## 📚 Документация по проблемам

- **Парсинг:** `PARSER_ERROR_HANDLING.md`
- **Длинные ответы:** `MESSAGE_SPLITTING.md`
- **Content blocks:** `CONTENT_BLOCKS_FIX.md`
- **Response format:** `RESPONSE_FORMAT_INTEGRATION_SUMMARY.md`
- **История:** `HISTORY_CLEARING_VERIFICATION.md`
- **Reasoning режимы:** `REASONING_MODE.md`, `QUICK_START_REASONING.md`

---

## 🆘 Если ничего не помогает

1. **Проверить версию проекта:**
   Убедиться что используется последняя версия (проверить LATEST_CHANGES_SUMMARY.md)

2. **Проверить зависимости:**
   ```bash
   ./gradlew dependencies
   ```

3. **Очистить кеш Gradle:**
   ```bash
   ./gradlew clean
   rm -rf ~/.gradle/caches/
   ```

4. **Проверить Java версию:**
   ```bash
   java -version  # Должно быть Java 17
   ```

5. **Создать issue:**
   Собрать логи и описать проблему детально

---

**Версия:** 2.1
**Последнее обновление:** 9 ноября 2024
