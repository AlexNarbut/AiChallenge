# Resources Structure

## Структура папки `src/main/resources/`

```
src/main/resources/
│
├── dataFormat/                          # Форматы ответов (JSON, XML)
│   ├── json_format_requirements.txt     # Промпт для JSON формата
│   └── xml_format_requirements.txt      # Промпт для XML формата
│
└── systemPrompts/                                   # Системные промпты для AI режимов
    ├── expert_system_prompt.txt                     # Expert mode (fitness trainer)
    ├── reasoning_expert_panel_system_prompt.txt     # 👥 Мнение экспертов
    ├── reasoning_prompt_engineer_system_prompt.txt  # 🎯 Составление промпта
    ├── reasoning_quick_answer_system_prompt.txt     # ⚡ Быстрый ответ
    └── reasoning_step_by_step_system_prompt.txt     # 📊 Пошаговый ответ
```

---

## Категории промптов

### 1. 📁 dataFormat/ - Форматирование ответов

**json_format_requirements.txt** (1.6 KB)
- Инструкции для форматирования ответов в JSON
- Используется когда `CLAUDE_RESPONSE_FORMAT=json`
- Структура: question, answer, urls, date

**xml_format_requirements.txt** (2.1 KB)
- Инструкции для форматирования ответов в XML
- Используется когда `CLAUDE_RESPONSE_FORMAT=xml`
- Структура: аналогична JSON

### 2. 🧠 systemPrompts/ - Системные промпты AI

Все системные промпты для различных режимов работы AI.

#### 👨‍⚕️ Expert Mode

**systemPrompts/expert_system_prompt.txt** (2.5 KB)
- Режим эксперта (fitness trainer)
- Активируется командой `/expert`
- Включает опросник и генерацию тренировочных планов

#### 🧠 Reasoning Mode (4 подрежима)

**systemPrompts/reasoning_quick_answer_system_prompt.txt** (1.7 KB)
- Режим: ⚡ Быстрый ответ
- Команда: `/reasoning_basic`
- Назначение: Прямые лаконичные ответы на логические задачи

**systemPrompts/reasoning_step_by_step_system_prompt.txt** (3.0 KB)
- Режим: 📊 Пошаговый ответ
- Команда: `/reasoning_math`
- Назначение: Детальное решение с объяснением каждого шага

**systemPrompts/reasoning_prompt_engineer_system_prompt.txt** (5.6 KB)
- Режим: 🎯 Составление промпта
- Команда: `/reasoning_strategic`
- Назначение: Генерация промптов для других LLM

**systemPrompts/reasoning_expert_panel_system_prompt.txt** (6.9 KB)
- Режим: 👥 Мнение экспертов
- Команда: `/reasoning_creative`
- Назначение: Панель из 4 экспертов + синтез решений

---

## Загрузка промптов

Все промпты загружаются при старте приложения через `FormatPromptLoader`:

```kotlin
// Data format prompts
prompts["json"] = loadPromptFromFile("dataFormat/json_format_requirements.txt")
prompts["xml"] = loadPromptFromFile("dataFormat/xml_format_requirements.txt")

// Expert mode prompt
prompts["expert"] = loadPromptFromFile("systemPrompts/expert_system_prompt.txt")

// Reasoning mode prompts
prompts["reasoning_basic"] = loadPromptFromFile("systemPrompts/reasoning_quick_answer_system_prompt.txt")
prompts["reasoning_mathematical"] = loadPromptFromFile("systemPrompts/reasoning_step_by_step_system_prompt.txt")
prompts["reasoning_strategic"] = loadPromptFromFile("systemPrompts/reasoning_prompt_engineer_system_prompt.txt")
prompts["reasoning_creative"] = loadPromptFromFile("systemPrompts/reasoning_expert_panel_system_prompt.txt")
```

---

## Приоритет использования промптов

При определении системного промпта используется следующий приоритет:

```
1. Reasoning Mode    (если активен reasoning режим)
   ↓
2. Expert Mode       (если активен expert режим)
   ↓
3. Response Format   (json/xml/text - обычный режим)
```

---

## Редактирование промптов

### Как изменить промпт:

1. Найдите нужный `.txt` файл в `src/main/resources/`
2. Отредактируйте содержимое
3. Перезапустите приложение
4. Промпт будет загружен заново

### Как добавить новый режим reasoning:

1. Создайте файл `reasoning_newmode.txt` в `src/main/resources/systemPrompts/`
2. Добавьте маппинг в `BotConfiguration.kt`:
   ```kotlin
   var reasoningPrompts: Map<String, String> = mapOf(
       // ... existing modes ...
       "newmode" to "reasoning_newmode"
   )
   ```
3. Добавьте команду в `TelegramBotService.kt`:
   ```kotlin
   command("reasoning_newmode") {
       // ... handler code ...
   }
   ```
4. Промпт будет автоматически загружен при старте

---

## Размеры файлов

| Файл | Размер | Категория |
|------|--------|-----------|
| dataFormat/json_format_requirements.txt | 1.6 KB | Data Format |
| dataFormat/xml_format_requirements.txt | 2.1 KB | Data Format |
| systemPrompts/expert_system_prompt.txt | 2.5 KB | Expert Mode |
| systemPrompts/reasoning_quick_answer_system_prompt.txt | 1.7 KB | Reasoning |
| systemPrompts/reasoning_step_by_step_system_prompt.txt | 3.0 KB | Reasoning |
| systemPrompts/reasoning_prompt_engineer_system_prompt.txt | 5.6 KB | Reasoning |
| systemPrompts/reasoning_expert_panel_system_prompt.txt | 6.9 KB | Reasoning |
| **ИТОГО** | **23.4 KB** | **2 папки, 7 файлов** |

---

## Лучшие практики

### ✅ DO:
- Храните промпты в `.txt` файлах
- Используйте понятные имена файлов
- Группируйте связанные промпты в подпапки
- Документируйте назначение каждого промпта
- Указывайте размер файлов в документации

### ❌ DON'T:
- Не храните чувствительные данные в промптах
- Не создавайте слишком большие промпты (>10 KB)
- Не дублируйте промпты
- Не забывайте обновлять загрузчик при добавлении новых файлов

---

## Примеры использования

### Смена формата ответа:
```bash
# JSON формат
CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun

# XML формат
CLAUDE_RESPONSE_FORMAT=xml ./gradlew bootRun
```

### Использование режимов через Telegram:
```
/expert              → использует expert_system_prompt.txt
/reasoning_basic     → использует reasoning_basic.txt
/reasoning_math      → использует reasoning_mathematical.txt
/reasoning_strategic → использует reasoning_strategic.txt
/reasoning_creative  → использует reasoning_creative.txt
```

---

## Обновление структуры

**Дата последнего изменения:** 8 ноября 2024

**Изменения:**
- ✅ Созданы 4 reasoning промпта
- ✅ Перемещены format файлы в `dataFormat/`
- ✅ Обновлён `FormatPromptLoader.kt`
- ✅ Обновлена документация

---

**Структура стабильна и готова к использованию.** ✅
