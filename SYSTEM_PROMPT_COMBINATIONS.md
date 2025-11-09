# System Prompt Combinations - Справочная таблица

## 📋 Полная матрица комбинаций

**Дата:** 9 ноября 2024

---

## 🎯 Логика

Системный промпт формируется из двух компонентов:

```
System Prompt = Mode Prompt + "\n\n" + Format Prompt
```

Оба компонента опциональны и применяются независимо друг от друга.

---

## 📊 Таблица всех возможных комбинаций

### Normal Mode

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | - | - | (пусто) | text |
| json | - | JSON format | JSON format requirements | JSON ✅ |
| xml | - | XML format | XML format requirements | XML ✅ |

### Expert Mode

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | Expert | - | Expert system prompt | text |
| json | Expert | JSON format | Expert prompt + JSON format | JSON ✅ |
| xml | Expert | XML format | Expert prompt + XML format | XML ✅ |

### Reasoning Mode - Quick Answer

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | Quick Answer | - | Quick Answer prompt | text |
| json | Quick Answer | JSON format | Quick Answer + JSON format | JSON ✅ |
| xml | Quick Answer | XML format | Quick Answer + XML format | XML ✅ |

### Reasoning Mode - Step by Step

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | Step by Step | - | Step by Step prompt | text |
| json | Step by Step | JSON format | Step by Step + JSON format | JSON ✅ |
| xml | Step by Step | XML format | Step by Step + XML format | XML ✅ |

### Reasoning Mode - Prompt Engineer

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | Prompt Engineer | - | Prompt Engineer prompt | text |
| json | Prompt Engineer | JSON format | Prompt Engineer + JSON format | JSON ✅ |
| xml | Prompt Engineer | XML format | Prompt Engineer + XML format | XML ✅ |

### Reasoning Mode - Expert Panel

| Response Format | Mode Prompt | Format Prompt | Итоговый System Prompt | Парсинг |
|----------------|-------------|---------------|------------------------|---------|
| text | Expert Panel | - | Expert Panel prompt | text |
| json | Expert Panel | JSON format | Expert Panel + JSON format | JSON ✅ |
| xml | Expert Panel | XML format | Expert Panel + XML format | XML ✅ |

---

## 🔧 Конфигурация

### Response Format

Устанавливается через переменную окружения:

```bash
CLAUDE_RESPONSE_FORMAT=json   # JSON формат
CLAUDE_RESPONSE_FORMAT=xml    # XML формат
CLAUDE_RESPONSE_FORMAT=text   # Текстовый формат (по умолчанию)
```

### Режимы

Активируются командами в Telegram:

```
/normal              # Normal mode
/expert              # Expert mode
/reasoning           # Открыть меню reasoning
  /quick_answer      # Quick Answer reasoning
  /step_by_step      # Step by Step reasoning
  /prompt_engineer   # Prompt Engineer reasoning
  /expert_panel      # Expert Panel reasoning
```

---

## 📝 Примеры использования

### Пример 1: Reasoning + JSON

**Конфигурация:**
```bash
CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun
```

**Telegram диалог:**
```
User: /quick_answer
Bot: ⚡ Режим "Быстрый ответ" активирован

User: "Если все кошки - животные, а Мурка - кошка, то Мурка - животное?"

Bot:
📋 Вопрос: Если все кошки - животные...
📝 Ответ: [быстрый прямой ответ согласно Quick Answer промпту]
🔗 Источники: (нет)
📅 Дата: 2024-11-09T01:15:00Z
```

**System Prompt:**
```
[Quick Answer system prompt]

[JSON format requirements]
```

---

### Пример 2: Expert + XML

**Конфигурация:**
```bash
CLAUDE_RESPONSE_FORMAT=xml ./gradlew bootRun
```

**Telegram диалог:**
```
User: /expert
Bot: 👨‍⚕️ Expert Mode Activated!

User: "Мне 25 лет, вес 70кг..."

Bot:
<response>
  <question>Мне 25 лет...</question>
  <answer>[опрос от фитнес-тренера]</answer>
  <urls></urls>
  <date>2024-11-09T01:20:00Z</date>
</response>
```

**System Prompt:**
```
[Expert system prompt (fitness trainer)]

[XML format requirements]
```

---

### Пример 3: Normal + text

**Конфигурация:**
```bash
CLAUDE_RESPONSE_FORMAT=text ./gradlew bootRun
```

**Telegram диалог:**
```
User: "Привет! Как дела?"

Bot: Привет! У меня всё отлично, спасибо! Чем могу помочь?
```

**System Prompt:**
```
(пусто)
```

---

## ⚙️ Реализация

**Файл:** `src/main/kotlin/com/aiassist/bot/service/ClaudeApiClient.kt`

```kotlin
// 1. Определяем mode-специфичный промпт
val modePrompt = when {
    reasoningType != null -> formatPromptLoader.getPromptForFormat("reasoning_$reasoningType") ?: ""
    isExpertMode -> formatPromptLoader.getPromptForFormat("expert") ?: ""
    else -> ""
}

// 2. Определяем format-специфичный промпт
val formatPrompt = when (config.responseFormat.lowercase()) {
    "json" -> formatPromptLoader.getPromptForFormat("json") ?: ""
    "xml" -> formatPromptLoader.getPromptForFormat("xml") ?: ""
    else -> ""
}

// 3. Комбинируем промпты
val systemPrompt = if (modePrompt.isNotEmpty() && formatPrompt.isNotEmpty()) {
    "$modePrompt\n\n$formatPrompt"
} else {
    modePrompt + formatPrompt
}
```

---

## ✅ Ключевые моменты

1. **Независимость:** Response format применяется ВСЕГДА, независимо от режима
2. **Комбинирование:** Mode prompt и format prompt объединяются, а не замещают друг друга
3. **Парсинг:** Выполняется согласно `config.responseFormat` во всех режимах
4. **Гибкость:** Можно использовать reasoning режимы со структурированным выводом (JSON/XML)

---

## 🎓 Use Cases

### Use Case 1: Логические задачи с JSON для API

```bash
CLAUDE_RESPONSE_FORMAT=json
```

Reasoning режимы дают логически правильные ответы + JSON структура для автоматической обработки.

### Use Case 2: Фитнес планы с XML для базы данных

```bash
CLAUDE_RESPONSE_FORMAT=xml
```

Expert mode создает планы тренировок + XML для сохранения в базу.

### Use Case 3: Простое общение

```bash
CLAUDE_RESPONSE_FORMAT=text
```

Обычный разговор без структурирования.

---

**Статус:** ✅ АКТУАЛЬНО
**Версия:** 2.0 (с поддержкой комбинирования промптов)
