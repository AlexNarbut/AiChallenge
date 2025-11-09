# Parser Error Handling - Улучшенная обработка ошибок парсинга

## ✅ Реализовано

**Дата:** 9 ноября 2024

---

## 🎯 Проблема

Когда парсинг JSON/XML падал, пользователь получал весь raw response, который мог быть:
- Очень длинным (тысячи символов)
- Неудобным для чтения в Telegram
- Перегружал сообщение ненужной информацией

---

## ✅ Решение

### Улучшенные сообщения об ошибках

**Было:**
```
❌ Failed to parse JSON response. Raw response:
[весь response на 5000 символов]
```

**Стало:**
```
❌ Failed to parse JSON response.

Error: Unexpected character '{' at position 1250

Raw response preview:
```
[первые 250 символов]

... (4500 characters omitted) ...

[последние 250 символов]
```
```

---

## 🔧 Реализация

**Файл:** `ResponseParser.kt`

### JSON Error Handling (строки 76-87):

```kotlin
} catch (e: Exception) {
    logger.error(e) { "Failed to parse JSON response. Length: ${response.length}" }

    // Show truncated response if too long
    val preview = if (response.length > 500) {
        "${response.take(250)}\n\n... (${response.length - 500} characters omitted) ...\n\n${response.takeLast(250)}"
    } else {
        response
    }

    "❌ Failed to parse JSON response.\n\nError: ${e.message}\n\nRaw response preview:\n```\n$preview\n```"
}
```

### XML Error Handling (строки 136-147):

Аналогичная логика для XML.

---

## 📊 Примеры

### Пример 1: Короткий ответ с ошибкой (< 500 символов)

```
❌ Failed to parse JSON response.

Error: Unexpected end of JSON input

Raw response preview:
```
{"question": "Test", "answer": "incomplete
```
```

Показывается полностью (response.length <= 500).

---

### Пример 2: Длинный ответ с ошибкой (> 500 символов)

```
❌ Failed to parse JSON response.

Error: Unexpected character '}' at position 1250

Raw response preview:
```
{"question": "Very long question about complex topic requiring detailed explanation...", "answer": "This is the beginning of a very long answer that spans multiple paragraphs and discusses various aspects of the problem. The answer contains ...

... (4500 characters omitted) ...

... and this is the conclusion of the long answer with final thoughts and recommendations.", "urls": [], "date": "2024-11-09T01:30:00Z"}
```
```

Показываются:
- Первые 250 символов
- Информация о пропущенных символах
- Последние 250 символов

---

## 📝 Логирование

### Debug Logging

Добавлено детальное логирование (строки 36, 46):

```kotlin
logger.debug { "Parsing JSON response of length: ${response.length}" }
logger.debug { "Cleaned response length: ${cleanedResponse.length}, first 100 chars: ${cleanedResponse.take(100)}" }
```

### Error Logging

```kotlin
logger.error(e) { "Failed to parse JSON response. Length: ${response.length}" }
```

Логи содержат:
- Exception с stack trace
- Длину response
- Первые 100 символов cleaned response (в debug mode)

---

## 🎯 Преимущества

### Для пользователя:

✅ **Читаемость:** Сообщение об ошибке компактное
✅ **Полезность:** Видно начало и конец ответа
✅ **Понятность:** Указана конкретная ошибка
✅ **Telegram-friendly:** Не перегружает чат

### Для разработчика:

✅ **Отладка:** Детальные логи в консоли
✅ **Диагностика:** Видно где именно парсинг упал
✅ **Мониторинг:** Легко отследить проблемы

---

## 🔍 Когда возникают ошибки парсинга

### Частые причины:

1. **Claude не следует формату**
   - Добавляет текст до/после JSON/XML
   - Использует markdown обертки (```json)

2. **Невалидный JSON/XML**
   - Незакрытые кавычки
   - Неэкранированные спецсимволы
   - Неправильная вложенность

3. **Неполный ответ**
   - Превышен maxTokens
   - Обрыв соединения (редко)

4. **Неправильная кодировка**
   - Спецсимволы в тексте
   - Emoji в JSON без экранирования

---

## 🛠️ Диагностика проблем

### Шаг 1: Проверить логи

```
ERROR: Failed to parse JSON response. Length: 5234
```

Длина показывает что ответ получен полностью.

### Шаг 2: Проверить debug логи

```
DEBUG: Parsing JSON response of length: 5234
DEBUG: Cleaned response length: 5230, first 100 chars: {"question": "Test", "answer": "This is a very long answer with special chars & symbols...
```

Видим что очистка прошла (5234 → 5230 после удаления markdown).

### Шаг 3: Проверить preview в Telegram

Пользователь получит начало и конец response, можно увидеть где проблема.

---

## 📚 Связанные изменения

- `ResponseParser.kt:36` - debug logging для JSON
- `ResponseParser.kt:46` - debug logging после очистки
- `ResponseParser.kt:76-87` - улучшенная обработка ошибок JSON
- `ResponseParser.kt:136-147` - улучшенная обработка ошибок XML

---

## 💡 Рекомендации

### Для предотвращения ошибок:

1. **Проверить format prompts:**
   - `dataFormat/json_format_requirements.txt`
   - `dataFormat/xml_format_requirements.txt`

2. **Увеличить maxTokens если нужно:**
   ```yaml
   claude:
     api:
       max-tokens: 2048  # Увеличить если ответы обрезаются
   ```

3. **Мониторить логи:**
   - Частые ошибки парсинга = проблема с промптами
   - Редкие ошибки = специфические edge cases

---

**Статус:** ✅ РАБОТАЕТ
**Логирование:** 🔍 Детальное
**UX:** ⭐⭐⭐⭐⭐ Понятные сообщения об ошибках
