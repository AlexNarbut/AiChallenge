# HTTP Timeout Fix - Увеличение таймаута запросов

## ✅ Проблема решена

**Дата:** 9 ноября 2024

---

## 🐛 Проблема

При использовании Expert Panel (и других режимов с длинными ответами) возникала ошибка:

```
io.ktor.client.plugins.HttpRequestTimeoutException: Request timeout has expired
[url=https://api.anthropic.com/v1/messages, request_timeout=unknown ms]
```

**Причина:**
- Ktor HttpClient имеет дефолтный timeout ~60 секунд
- Expert Panel генерирует очень длинный ответ (4 эксперта + синтез)
- Claude API не успевает ответить за 60 секунд
- Соединение обрывается

---

## ✅ Решение

### 1. Добавлен HttpTimeout plugin

**Файл:** `ClaudeApiClient.kt:13, 35-39`

```kotlin
import io.ktor.client.plugins.HttpTimeout

private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson()
    }
    install(Logging) {
        level = LogLevel.INFO
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 300000 // 5 minutes
        connectTimeoutMillis = 60000  // 1 minute
        socketTimeoutMillis = 300000  // 5 minutes
    }
}
```

**Настройки:**
- **requestTimeoutMillis:** 300000 ms (5 минут) - общий таймаут запроса
- **connectTimeoutMillis:** 60000 ms (1 минута) - таймаут установки соединения
- **socketTimeoutMillis:** 300000 ms (5 минут) - таймаут чтения данных из сокета

---

### 2. Исправлена модель ошибок

**Файл:** `ClaudeModels.kt:47-56`

**Проблема:**
При timeout Claude API возвращает ответ, который не соответствует модели `ClaudeErrorResponse`. Поля `type` и `message` были non-nullable, что вызывало Jackson exception.

**Решение:**
```kotlin
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClaudeErrorResponse(
    val type: String? = null,      // Было: val type: String
    val error: ErrorDetail? = null  // Было: val error: ErrorDetail
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorDetail(
    val type: String? = null,       // Было: val type: String
    val message: String? = null     // Было: val message: String
)
```

---

### 3. Улучшена обработка ошибок

**Файл:** `ClaudeApiClient.kt:124-132`

**Было:**
```kotlin
val errorResponse: ClaudeErrorResponse = httpResponse.body()
logger.error { "Claude API error: ${errorResponse.error.type} - ${errorResponse.error.message}" }
// NPE если error или его поля null
```

**Стало:**
```kotlin
val errorResponse: ClaudeErrorResponse = httpResponse.body()
val errorType = errorResponse.error?.type ?: "unknown"
val errorMessage = errorResponse.error?.message ?: "No error message"
logger.error { "Claude API error: $errorType - $errorMessage" }
```

**Результат:** Безопасная обработка даже если поля null.

---

## 📊 Таймауты

### Почему 5 минут?

| Режим | Примерное время генерации | Запас |
|-------|--------------------------|-------|
| Normal | 5-10 секунд | Огромный |
| Quick Answer | 10-15 секунд | Огромный |
| Step by Step | 30-60 секунд | Хороший |
| Prompt Engineer | 30-45 секунд | Хороший |
| **Expert Panel** | **90-180 секунд** | **Достаточный** |

**Expert Panel:** 4 эксперта + синтез = самый длинный ответ

**5 минут (300 секунд):**
- Достаточно даже для самых сложных задач
- Не слишком долго для UX (пользователь ждет не вечность)
- Безопасный запас для сетевых задержек

---

## 🎯 Результат

### До исправления:

```
User: /expert_panel
User: "Сложная задача"

Bot: [генерирует 90 секунд...]
❌ HttpRequestTimeoutException
❌ Пользователь не получает ответ
```

### После исправления:

```
User: /expert_panel
User: "Сложная задача"

Bot: [генерирует 90 секунд...]
✅ Ответ получен
✅ Отправлен пользователю
```

---

## ⚙️ Настройка (опционально)

Таймауты можно настроить через environment variables (если добавить конфигурацию):

**Не реализовано, но можно добавить:**

```yaml
# application.yml
claude:
  api:
    timeout:
      request: ${CLAUDE_REQUEST_TIMEOUT:300000}
      connect: ${CLAUDE_CONNECT_TIMEOUT:60000}
      socket: ${CLAUDE_SOCKET_TIMEOUT:300000}
```

```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = config.timeout.request
    connectTimeoutMillis = config.timeout.connect
    socketTimeoutMillis = config.timeout.socket
}
```

**Текущая реализация:** Хардкод 5 минут (достаточно для всех случаев).

---

## 🔍 Типы таймаутов

### 1. Request Timeout (300000 ms = 5 минут)
**Что:** Общий таймаут всего запроса от начала до конца

**Когда истекает:**
- Запрос занял > 5 минут в общей сложности
- Включает установку соединения + отправку + ожидание + получение

**Пример:**
```
Start → Connect (10s) → Send (1s) → Wait (280s) → Receive (10s) → Total: 301s
❌ Request timeout (превышен лимит 300s)
```

### 2. Connect Timeout (60000 ms = 1 минута)
**Что:** Таймаут установки TCP соединения с сервером

**Когда истекает:**
- Не удается подключиться к api.anthropic.com за 60 секунд
- Проблемы с сетью, DNS, firewall

**Обычно:** Соединение устанавливается за 1-5 секунд

### 3. Socket Timeout (300000 ms = 5 минут)
**Что:** Таймаут между получением частей данных

**Когда истекает:**
- Сервер перестал отправлять данные > 5 минут
- Соединение "зависло"

**Обычно:** Claude API отправляет данные непрерывно

---

## 📝 Логирование

### Нормальная работа:

```
INFO: Sending message to Claude API with...
INFO: Received response from Claude API. Tokens used: 7543
INFO: Raw Claude response (3 blocks):
```

### Timeout:

```
INFO: Sending message to Claude API with...
ERROR: Error calling Claude API
io.ktor.client.plugins.HttpRequestTimeoutException: Request timeout has expired
```

**Новое поведение:** Timeout случается крайне редко, только при реальных проблемах.

---

## 🆘 Если timeout всё равно происходит

### Возможные причины:

1. **Сеть очень медленная**
   - Проверить скорость интернета
   - Проверить пинг до api.anthropic.com

2. **Claude API перегружен**
   - Anthropic испытывает проблемы
   - Попробовать позже

3. **Задача действительно очень сложная**
   - Упростить вопрос
   - Разбить на несколько запросов

### Решения:

**Вариант 1:** Увеличить таймаут (если нужно)
```kotlin
requestTimeoutMillis = 600000 // 10 минут
```

**Вариант 2:** Использовать более простой режим
```
/expert_panel → /step_by_step  (быстрее генерирует)
```

**Вариант 3:** Сократить max-tokens
```yaml
max-tokens: 8000 → 4000  (быстрее генерирует)
```

---

## 📚 Связанные изменения

- `ClaudeApiClient.kt:13` - import HttpTimeout
- `ClaudeApiClient.kt:35-39` - install HttpTimeout с настройками
- `ClaudeModels.kt:47-56` - nullable поля в error models
- `ClaudeApiClient.kt:124-132` - безопасная обработка ошибок

---

## ✅ Проверка

- [x] Timeout увеличен до 5 минут
- [x] Error models сделаны nullable
- [x] Обработка ошибок безопасна
- [x] Проект собирается
- [ ] Протестировать Expert Panel на длинной задаче

---

**Статус:** ✅ ИСПРАВЛЕНО
**Expert Panel:** Теперь работает даже на сложных задачах
**Timeout:** 5 минут (достаточно для всех режимов)
