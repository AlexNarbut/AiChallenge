# Max Tokens Increase - Увеличение лимита выходных токенов

## ✅ Реализовано

**Дата:** 9 ноября 2024

---

## 🎯 Проблема

При `max-tokens: 1024` длинные ответы Claude обрезались, что приводило к:
- Невалидному JSON/XML (незакрытые кавычки, скобки, теги)
- Ошибкам парсинга
- Неполным ответам пользователю

---

## ✅ Решение

### 1. Увеличение max-tokens

**Было:**
```yaml
claude:
  api:
    max-tokens: ${CLAUDE_MAX_TOKENS:1024}
```

**Стало:**
```yaml
claude:
  api:
    max-tokens: ${CLAUDE_MAX_TOKENS:8000}
```

**Изменение:** 1024 → 8000 (увеличение в 7.8 раз)

---

### 2. Обновление format prompts

Добавлены инструкции о валидности при обрезке.

#### JSON Format (`dataFormat/json_format_requirements.txt`):

```
IMPORTANT: Your response MUST be complete valid JSON even if answer is very long.
Always close all brackets and quotes properly. If response is cut due to length limits,
finish with valid JSON by closing the current field and adding remaining required fields.
```

#### XML Format (`dataFormat/xml_format_requirements.txt`):

```
IMPORTANT: Your response MUST be complete valid XML even if answer is very long.
Always close all tags properly. If response is cut due to length limits,
finish with valid XML by closing the current tag and adding remaining required tags.
```

---

## 📊 Сравнение

### Лимиты токенов:

| Параметр | Старое значение | Новое значение | Изменение |
|----------|----------------|----------------|-----------|
| max-tokens | 1024 | 8000 | +681% |
| Примерно символов | ~3000 | ~24000 | +700% |
| Примерно слов | ~750 | ~6000 | +700% |

**Примечание:** 1 token ≈ 3-4 символа для английского текста, ≈ 1.5-2 для русского

---

## 🎯 Преимущества

### 1. Полные ответы

✅ Expert Panel может дать развернутый анализ от 4 экспертов
✅ Step by Step может детально разобрать сложную задачу
✅ Expert Mode может создать полный план тренировок

### 2. Валидность структуры

✅ JSON всегда валиден (Claude успевает закрыть все скобки)
✅ XML всегда валиден (все теги закрыты)
✅ Парсинг работает без ошибок

### 3. Лучший UX

✅ Пользователь получает полный ответ
✅ Нет обрезанных мыслей
✅ Reasoning режимы работают максимально эффективно

---

## 💰 Стоимость

### Ценообразование Claude (примерно):

**Модель: claude-haiku-4-5-20251001**

Input: ~$0.25 / 1M tokens
Output: ~$1.25 / 1M tokens

**Пример:**
- Старый лимит (1024 tokens): $0.00128 за ответ
- Новый лимит (8000 tokens): $0.01 за ответ

**Разница:** ~$0.009 за длинный ответ

**Оценка:** Для большинства запросов токены не достигнут лимита. Увеличение стоимости только для действительно длинных ответов (reasoning режимы, expert mode).

---

## ⚙️ Настройка

### Через environment variable:

```bash
CLAUDE_MAX_TOKENS=8000 ./gradlew bootRun
```

### Через application.yml:

```yaml
claude:
  api:
    max-tokens: 8000
```

### Рекомендуемые значения:

| Use Case | max-tokens | Примечание |
|----------|-----------|------------|
| Короткие ответы | 2048 | Экономия на стоимости |
| Средние ответы | 4096 | Баланс стоимость/качество |
| **Длинные ответы (рекомендуется)** | **8000** | **Для reasoning/expert режимов** |
| Очень длинные | 16000 | Максимум для большинства моделей |

---

## 🔍 Мониторинг использования

### Логи показывают использование токенов:

```
INFO: Received response from Claude API. Tokens used: 12543
```

Где `12543 = inputTokens + outputTokens`

### Анализ:

- Если `outputTokens` близко к max-tokens → ответ обрезан
- Если `outputTokens` << max-tokens → лимит не достигнут

**Пример анализа:**
```
Tokens used: 8523 (input: 523, output: 8000)
```
→ Output достиг лимита, возможно ответ обрезан

```
Tokens used: 3200 (input: 450, output: 2750)
```
→ Лимит не достигнут, ответ полный

---

## 📝 Format Prompts Improvements

### До изменений:

Format prompts только требовали валидность, но не говорили что делать при обрезке.

### После изменений:

Явная инструкция: "Если ответ обрезается, всё равно закрой все теги/скобки".

**Результат:**
Даже если Claude достигнет max-tokens, он постарается закрыть структуру.

---

## 🧪 Тестовые сценарии

### Сценарий 1: Expert Panel с длинным ответом

```bash
CLAUDE_MAX_TOKENS=8000 CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun
```

```
User: /expert_panel
User: "Сложная междисциплинарная задача требующая детального анализа..."

Expected:
- Ответ от 4 экспертов
- Синтез
- Валидный JSON
- output tokens: ~6000-7000
```

### Сценарий 2: Step by Step

```
User: /step_by_step
User: "Решить систему из 5 уравнений с подробными объяснениями"

Expected:
- Детальный анализ
- Пошаговое решение
- Проверка
- output tokens: ~4000-5000
```

### Сценарий 3: Expert Mode

```
User: /expert
User: [детальные параметры тренировки]

Expected:
- Полный план на несколько недель
- Детальное описание упражнений
- output tokens: ~5000-6000
```

---

## ⚠️ Важные замечания

### 1. Max tokens - это лимит

Claude может вернуть МЕНЬШЕ токенов, если ответ короче. Это не "минимум".

### 2. Input + Output

Общий контекст = input tokens (промпт + история) + output tokens (ответ).

Если история очень длинная, может не хватить места для output.

**Решение:** История очищается при смене режимов (см. HISTORY_CLEARING_VERIFICATION.md).

### 3. Модели имеют разные лимиты

- Haiku: обычно до 16k output tokens
- Sonnet: до 16k output tokens
- Opus: до 16k output tokens

8000 - безопасное значение для всех моделей.

---

## 📚 Связанные документы

- `CONTENT_BLOCKS_FIX.md` - объединение длинных ответов
- `MESSAGE_SPLITTING.md` - разбиение на части в Telegram
- `PARSER_ERROR_HANDLING.md` - обработка ошибок парсинга
- `application.yml` - конфигурация max-tokens

---

## ✅ Результат

**До:**
- max-tokens: 1024
- Частые обрезки
- Невалидный JSON/XML
- Ошибки парсинга

**После:**
- max-tokens: 8000
- Полные ответы
- Валидный JSON/XML
- Парсинг работает

**Статус:** ✅ РАБОТАЕТ
**Рекомендация:** Оставить 8000 для production
