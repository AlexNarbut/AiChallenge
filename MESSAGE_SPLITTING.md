# Message Splitting - Разбиение длинных ответов на части

## ✅ Реализовано

**Дата:** 9 ноября 2024

---

## 🎯 Решение

Вместо генерации PDF для длинных ответов, теперь бот отправляет несколько последовательных сообщений.

---

## 💡 Преимущества

### Было (PDF):
❌ Нужно скачать и открыть файл
❌ Неудобно на мобильных устройствах
❌ Нельзя скопировать текст из уведомления
❌ Занимает место в памяти

### Стало (множественные сообщения):
✅ Читается прямо в чате
✅ Удобно на любом устройстве
✅ Можно скопировать любую часть
✅ История остается в чате
✅ Нумерация частей (Part 1/3, Part 2/3, ...)

---

## 🔧 Реализация

**Файл:** `TelegramBotService.kt:308-360`

### Алгоритм разбиения:

1. **Проверка длины:** Если `response.length > maxMessageLength`
2. **Умное разбиение:**
   - Приоритет 1: Разрыв на переводе строки `\n`
   - Приоритет 2: Разрыв на пробеле
   - Приоритет 3: Жесткий разрыв по длине
3. **Условие:** Точка разрыва должна быть > 50% от maxMessageLength
4. **Отправка:** Последовательная отправка всех частей
5. **Задержка:** 100ms между сообщениями (избежание rate limits)

### Код:

```kotlin
if (response.length > config.maxMessageLength) {
    // Split into chunks
    val chunks = mutableListOf<String>()
    var remainingText = response

    while (remainingText.isNotEmpty()) {
        if (remainingText.length <= config.maxMessageLength) {
            chunks.add(remainingText)
            break
        } else {
            // Find good breaking point (prefer newline, then space)
            var breakPoint = config.maxMessageLength
            val searchRange = remainingText.substring(0, config.maxMessageLength)

            val lastNewline = searchRange.lastIndexOf('\n')
            if (lastNewline > config.maxMessageLength / 2) {
                breakPoint = lastNewline + 1
            } else {
                val lastSpace = searchRange.lastIndexOf(' ')
                if (lastSpace > config.maxMessageLength / 2) {
                    breakPoint = lastSpace + 1
                }
            }

            chunks.add(remainingText.substring(0, breakPoint))
            remainingText = remainingText.substring(breakPoint)
        }
    }

    // Send all chunks with headers
    chunks.forEachIndexed { index, chunk ->
        val partHeader = if (chunks.size > 1) "📝 Part ${index + 1}/${chunks.size}\n\n" else ""
        bot.sendMessage(chatId, partHeader + chunk)

        if (index < chunks.size - 1) {
            Thread.sleep(100) // Small delay between messages
        }
    }
}
```

---

## 📊 Примеры

### Пример 1: Короткий ответ (< 4000 символов)

```
User: "Привет!"
Bot: "Привет! Чем могу помочь?"
```

Отправляется как одно сообщение.

---

### Пример 2: Длинный ответ (> 4000 символов)

```
User: /step_by_step
User: "Сложная задача требующая детального объяснения..."

Bot: [Отправляет 3 сообщения]

Сообщение 1:
📝 Part 1/3

📋 АНАЛИЗ ЗАДАЧИ:
[... первая часть ответа ...]

Сообщение 2:
📝 Part 2/3

[... вторая часть ответа ...]

Сообщение 3:
📝 Part 3/3

[... третья часть и заключение ...]
```

---

### Пример 3: Expert Panel с длинным анализом

```
User: /expert_panel
User: "Сложная междисциплинарная задача..."

Bot: [Отправляет 4 сообщения]

📝 Part 1/4
📋 ФОРМУЛИРОВКА ЗАДАЧИ:
[...]
👥 МНЕНИЯ ЭКСПЕРТОВ:
👨‍🔬 Математик-логик:
[...]

📝 Part 2/4
🧠 Когнитивный психолог:
[...]

📝 Part 3/4
💻 Программист-алгоритмист:
[...]

📝 Part 4/4
🎓 Философ-аналитик:
[...]
🎯 СИНТЕЗИРОВАННОЕ РЕШЕНИЕ:
[...]
```

---

## ⚙️ Конфигурация

**Параметр:** `telegram.max-message-length`

**Значение по умолчанию:** 4000 символов

**Настройка:**
```yaml
# application.yml
telegram:
  max-message-length: 4000
```

**Через переменную окружения:**
```bash
TELEGRAM_MAX_MESSAGE_LENGTH=3000 ./gradlew bootRun
```

---

## 🎯 Логика умного разбиения

### Приоритет точек разрыва:

1. **Перевод строки (`\n`)**
   - Самый естественный разрыв
   - Не ломает структуру текста
   - Используется если найден в последней половине чанка

2. **Пробел (` `)**
   - Разрыв между словами
   - Не ломает слова
   - Используется если нет переводов строки

3. **Жесткий разрыв**
   - По достижению maxMessageLength
   - Используется только если нет пробелов/переводов
   - Может разорвать слово

### Минимальная позиция разрыва:

Разрыв должен быть дальше чем `maxMessageLength / 2`, чтобы избежать:
- Слишком коротких сообщений
- Разрыва в начале чанка (где обычно заголовки)

---

## 📝 Формат заголовков частей

**Одна часть (весь ответ влез):**
```
[просто ответ без заголовка]
```

**Множество частей:**
```
📝 Part 1/3

[текст первой части]
```

Заголовок добавляется ТОЛЬКО если частей больше одной.

---

## 🚀 Преимущества для UX

1. **Читаемость:** Текст сразу виден в чате
2. **Копирование:** Легко скопировать нужную часть
3. **Навигация:** Нумерация помогает ориентироваться
4. **Уведомления:** Можно читать из уведомлений
5. **История:** Весь разговор сохраняется в чате

---

## 🔍 Отладка

**Логирование:**

```
INFO: Response length: 8543 characters
INFO: Response exceeds max length (4000), splitting into multiple messages
INFO: Sending response in 3 parts
INFO: Sent 3 message parts to user @username
```

Можно отслеживать:
- Длину ответа
- Количество частей
- Факт успешной отправки

---

## ⚠️ Важные детали

### Rate Limits

Telegram имеет ограничения на частоту отправки:
- Бот спит 100ms между сообщениями
- Это предотвращает блокировку за flood

### Порядок гарантирован

Сообщения отправляются последовательно:
```kotlin
chunks.forEachIndexed { index, chunk ->
    bot.sendMessage(chatId, partHeader + chunk)
    if (index < chunks.size - 1) {
        Thread.sleep(100)
    }
}
```

Пользователь получает части в правильном порядке.

---

## 📚 Связанные изменения

- `TelegramBotService.kt:308-360` - логика разбиения и отправки
- Удалена зависимость от PdfGenerator для длинных ответов
- PdfGenerator остается в коде, но больше не используется

---

**Статус:** ✅ РАБОТАЕТ
**UX:** 🌟🌟🌟🌟🌟 Значительное улучшение
**Тестирование:** Готово к проверке
