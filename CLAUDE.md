# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## ⚠️ CRITICAL RULES

**Documentation Policy:**
- **NEVER** create new .md files under any circumstances
- **ONLY** two .md files are allowed in this repository: CLAUDE.md and README.md
- **DO NOT** create documentation files for changes, summaries, troubleshooting, or any other purpose
- **UPDATE** CLAUDE.md directly if important architectural information needs to be preserved
- **MAINTAIN** context through this file only, not through additional documentation files

## Project Overview

This is a Telegram bot built with Kotlin and Spring Boot that integrates with Claude API. Users send messages via Telegram, which are forwarded to Claude API, and the AI-generated responses are sent back to the user.

**Tech Stack:**
- Kotlin 1.9.21
- Spring Boot 3.2.0
- Ktor Client 2.3.7 (for HTTP requests to Claude API)
- kotlin-telegram-bot library for Telegram integration
- Java 17

## Build Commands

**Build the project:**
```bash
./gradlew build
```

**Run the application:**
```bash
./gradlew bootRun
```

**Run with environment variables:**
```bash
TELEGRAM_BOT_TOKEN=xxx CLAUDE_API_KEY=yyy ./gradlew bootRun
```

**Clean build:**
```bash
./gradlew clean build
```

**Run tests:**
```bash
./gradlew test
```

**Run a single test class:**
```bash
./gradlew test --tests "ClassName"
```

**Run a specific test method:**
```bash
./gradlew test --tests "ClassName.testMethodName"
```

## Architecture

### Package Structure

```
com.aiassist.bot/
├── config/          # Configuration classes for Telegram and Claude API
├── model/           # Data models for Claude API requests/responses
└── service/         # Business logic services
```

### Key Components

**TelegramBotService** (`service/TelegramBotService.kt`):
- Spring service that manages the Telegram bot lifecycle
- Uses `@PostConstruct` to start polling on application startup
- Uses `@PreDestroy` to stop polling on shutdown
- Receives messages from users and delegates to ClaudeApiClient
- Sends responses back to Telegram users

**ClaudeApiClient** (`service/ClaudeApiClient.kt`):
- Ktor HTTP client for communicating with Claude API
- Handles request/response serialization
- Error handling and logging for API calls
- Uses suspend functions for async operations

**Configuration** (`config/BotConfiguration.kt`):
- Two configuration classes: `TelegramBotConfig` and `ClaudeApiConfig`
- Both use `@ConfigurationProperties` to bind from `application.yml`
- Environment variables override default values

### Message Flow

1. User sends message to Telegram bot
2. `TelegramBotService` receives message in text dispatcher
3. Bot sends "typing" action to indicate processing
4. `ClaudeApiClient.sendMessage()` is called (using `runBlocking` for coroutine)
5. Claude API returns response
6. Response is sent back to user via Telegram

## Configuration

Required environment variables:
- `TELEGRAM_BOT_TOKEN`: Bot token from @BotFather
- `TELEGRAM_BOT_USERNAME`: Bot username
- `CLAUDE_API_KEY`: API key from Anthropic Console

Optional configuration in `application.yml`:
- `claude.api.model`: Claude model to use (default: claude-3-5-sonnet-20241022)
- `claude.api.max-tokens`: Maximum tokens in response (default: 1024)
- `claude.api.response-format`: Response format (default: text, options: text, json, xml)

## Development Notes

**Coroutines:**
The project uses Kotlin coroutines for async operations. The `ClaudeApiClient` uses suspend functions, which are called from `TelegramBotService` using `runBlocking`. In production, consider using proper coroutine scopes for better async handling.

**Error Handling:**
Errors from Claude API are caught and returned as user-friendly messages. Check logs for detailed error information.

**Logging:**
Uses kotlin-logging-jvm for structured logging. Log levels configured in `application.yml`. Debug level is enabled for `com.aiassist.bot` package.

**Ktor Client:**
The Ktor client is configured with:
- CIO engine for async I/O
- Jackson for JSON serialization
- Logging plugin for HTTP request/response logging

**Bot Polling:**
The bot uses long polling (not webhooks). This is simpler for development but consider webhooks for production deployments.

## Extending the Bot

**Adding new commands:**
Modify `TelegramBotService.kt` and add new dispatchers:
```kotlin
dispatch {
    command("start") {
        // Handle /start command
    }
    text {
        // Handle text messages
    }
}
```

**Modifying Claude API parameters:**
Edit `ClaudeApiConfig` and `application.yml` to add new parameters, then update `ClaudeRequest` model and usage in `ClaudeApiClient`.

**Adding conversation history:**
The bot maintains conversation history per user in a ConcurrentHashMap. Each user's messages are stored separately and sent to Claude API to maintain context across multiple interactions.

**Expert Mode:**
The bot supports switching between normal conversation mode and expert mode (specialized personas). Expert mode state is tracked per user and affects which system prompt is used.

## Response Format Feature

The bot supports structured response formats (JSON and XML) in addition to plain text responses.

**FormatPromptLoader** (`service/FormatPromptLoader.kt`):
- Service that loads format requirement prompts from text files
- Prompts are loaded from `json_format_requirements.txt` and `xml_format_requirements.txt`
- These prompts instruct Claude to respond in specific formats

**ResponseParser** (`service/ResponseParser.kt`):
- Parses structured responses (JSON/XML) and formats them for display
- Validates response format
- Extracts fields: question, answer, urls, date
- Handles parsing errors gracefully

**How it works:**
1. Configure response format via `CLAUDE_RESPONSE_FORMAT` environment variable (text/json/xml)
2. Format prompt is loaded and combined with mode-specific prompt (if any)
3. Combined system prompt is sent as a top-level `system` parameter in the Claude API request
4. Claude API receives the system parameter along with the messages array and responds in the specified format
5. ResponseParser parses the structured response and formats it for the user
6. User receives a nicely formatted message with the extracted information

**Integration with modes:**
Response format is ALWAYS applied, regardless of the bot mode:
- **Normal mode** → format prompt only
- **Expert mode** → expert prompt + format prompt
- **Reasoning mode** → reasoning prompt + format prompt

The prompts are combined with `\n\n` separator when both are present.

**Example - JSON format:**
```bash
CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun
```

**Example - XML format:**
```bash
CLAUDE_RESPONSE_FORMAT=xml ./gradlew bootRun
```

**Format requirement files:**
- `src/main/resources/dataFormat/json_format_requirements.txt`: Instructions for JSON responses
- `src/main/resources/dataFormat/xml_format_requirements.txt`: Instructions for XML responses

**Customizing format prompts:**
Edit the format requirement files to modify the structure or add/remove fields. The ResponseParser expects the following fields:
- `question`: User's original question
- `answer`: AI's detailed response
- `urls`: List of source URLs (can be empty)
- `date`: ISO 8601 timestamp

## Expert Mode Feature

The bot supports specialized expert modes where Claude takes on specific personas with detailed instructions and behaviors.

**Key Components:**

**Expert Mode Tracking** (`TelegramBotService.kt`):
- Maintains a ConcurrentHashMap tracking which users are in expert mode
- Switches between normal and expert mode via commands
- Clears conversation history when switching modes

**Commands:**
- `/expert` - Activate expert mode (currently: fitness trainer)
- `/normal` - Return to normal conversation mode

**How it works:**
1. User sends `/expert` command
2. Bot sets expert mode flag for that user and clears history
3. All subsequent messages use the expert system prompt instead of format prompts
4. Expert prompt is loaded from `expert_system_prompt.txt`
5. User sends `/normal` to exit expert mode

**Expert Prompt File:**
- `src/main/resources/systemPrompts/expert_system_prompt.txt` - Contains the fitness trainer persona with survey protocol and plan generation instructions

**Adding New Expert Modes:**
1. Create a new system prompt file in `src/main/resources/systemPrompts/`
2. Update `ClaudeApiConfig.expertPrompt` to reference the new file
3. Modify `FormatPromptLoader` to load additional expert prompts
4. Add command handlers in `TelegramBotService` to switch between expert types

**Note:** Expert mode and response format features are independent. Expert mode uses its own system prompt and bypasses response parsing.

## Long Response Handling (Message Splitting)

When responses exceed Telegram's message length limit, the bot automatically splits them into multiple sequential messages.

**Configuration** (`config/BotConfiguration.kt`):
- `maxMessageLength` in `TelegramBotConfig` (default: 4000 characters)
- Configurable via `TELEGRAM_MAX_MESSAGE_LENGTH` environment variable

**How it works:**
1. Bot receives response from Claude API
2. Checks if response length exceeds `maxMessageLength`
3. If yes: intelligently splits response into chunks
4. Sends chunks sequentially with part numbers (e.g., "Part 1/3")
5. Small delay (100ms) between messages to avoid rate limits

**Smart Splitting Algorithm** (`TelegramBotService.kt:308-360`):
- **Priority 1:** Break at newline character `\n` (preserves structure)
- **Priority 2:** Break at space (keeps words intact)
- **Priority 3:** Hard break at max length (only if no better option)
- Minimum break position: 50% of maxMessageLength (avoids tiny chunks)

**Example output:**
```
📝 Part 1/3

[First part of the response...]

📝 Part 2/3

[Second part of the response...]

📝 Part 3/3

[Final part of the response...]
```

**Advantages:**
- User can read directly in chat (no file downloads)
- Easy to copy specific parts
- Works perfectly on mobile devices
- Maintains chat history
- Numbered parts for easy navigation

This feature is especially useful for reasoning mode responses (Step by Step, Expert Panel) that often contain detailed explanations.

## Reasoning Mode Feature

The bot supports specialized reasoning modes for solving different types of logical problems with tailored AI personas.

**Architecture:**

The reasoning system has 4 specialized sub-modes, each with its own system prompt and approach:

1. **Basic Reasoning** (`/reasoning_basic`) - Fundamental logical reasoning and step-by-step problem solving
2. **Mathematical Reasoning** (`/reasoning_math`) - Mathematical, analytical, and quantitative problem solving
3. **Strategic Reasoning** (`/reasoning_strategic`) - Strategic planning, decision-making, and scenario analysis
4. **Creative Reasoning** (`/reasoning_creative`) - Creative problem solving and innovative thinking

**Key Components:**

**Reasoning Mode Tracking** (`TelegramBotService.kt`):
- Maintains a ConcurrentHashMap tracking active reasoning mode per user
- Each user can be in one reasoning sub-mode at a time
- History is cleared when switching between modes

**Commands:**
- `/reasoning` - Opens reasoning mode menu with all available sub-modes
- `/quick_answer` - Quick direct answers mode
- `/step_by_step` - Step-by-step solution mode
- `/prompt_engineer` - Prompt generation mode
- `/expert_panel` - Expert panel discussion mode
- `/normal` - Exit reasoning mode and return to normal conversation

**How it works:**
1. User sends `/reasoning` command to see available sub-modes
2. User selects a specific reasoning mode (e.g., `/step_by_step`)
3. Bot sets reasoning mode flag for that user and clears history
4. All subsequent messages use the specific reasoning system prompt
5. Reasoning prompts are loaded from dedicated files in `src/main/resources/`
6. User sends `/normal` or `/reasoning` to exit or switch modes

**System Prompt Files:**
- `src/main/resources/systemPrompts/reasoning_quick_answer_system_prompt.txt` - Quick direct answers
- `src/main/resources/systemPrompts/reasoning_step_by_step_system_prompt.txt` - Step-by-step solutions
- `src/main/resources/systemPrompts/reasoning_prompt_engineer_system_prompt.txt` - Prompt generation for LLMs
- `src/main/resources/systemPrompts/reasoning_expert_panel_system_prompt.txt` - Expert panel discussions

**Configuration** (`config/BotConfiguration.kt`):
- `reasoningPrompts` map in `ClaudeApiConfig` defines filename mappings
- Default mappings can be overridden in `application.yml`

**Customizing Reasoning Prompts:**
1. Edit the corresponding `.txt` file in `src/main/resources/systemPrompts/`
2. Define the persona, approach, and capabilities
3. Specify problem-solving frameworks and techniques
4. The prompt is loaded automatically on application startup

**Mode Priority** (`ClaudeApiClient.kt:50-67`):
When determining which system prompt to use, the bot follows this priority:
1. Reasoning mode (if active)
2. Expert mode (if active)
3. Response format mode (normal operation)

**Note:** Reasoning mode, like expert mode, returns raw responses without format parsing. This allows for flexible, conversational problem-solving.

## Temperature Settings Feature

The bot supports user-configurable temperature settings for Claude API, allowing users to control the creativity and randomness of responses.

**Key Components:**

**SettingsManager** (`service/SettingsManager.kt`):
- Service for managing user-specific settings
- Maintains a ConcurrentHashMap tracking temperature per user
- Provides three preset temperature options: PRECISE (0.0), BALANCED (0.5), CREATIVE (1.0)
- Default temperature is 1.0 (Creative)

**Temperature Parameter:**
- Added to `ClaudeRequest` model (`model/ClaudeModels.kt`)
- Sent to Claude API with each request
- Range: 0.0 to 1.0

**Commands:**
- `/settings` - Opens settings menu with current temperature and available options
- `/temp_0` - Set temperature to 0.0 (Precise mode)
- `/temp_05` - Set temperature to 0.5 (Balanced mode)
- `/temp_1` - Set temperature to 1.0 (Creative mode)

**How it works:**
1. User sends `/settings` command to see current temperature and available options
2. User selects desired temperature option (e.g., `/temp_0`)
3. SettingsManager stores the temperature setting for that user
4. All subsequent API requests use the user's temperature setting
5. Temperature persists across conversations until changed

**Temperature Options:**

1. **Precise (0.0)** - 🎯
   - Maximally deterministic and consistent responses
   - Ideal for factual questions and technical tasks
   - Claude will give the most likely/expected response

2. **Balanced (0.5)** - ⚖️
   - Balance between creativity and precision
   - Suitable for most general tasks
   - Good default for varied conversations

3. **Creative (1.0)** - 🎨
   - More creative and diverse responses
   - Good for brainstorming and creative tasks
   - Produces more varied and imaginative outputs

**Implementation Details:**
- Temperature is applied per-user and stored in memory
- Settings survive across different bot modes (normal, expert, reasoning)
- ClaudeApiClient retrieves user's temperature from SettingsManager before each request
- Temperature is logged for debugging purposes

## Model Selection Feature

The bot supports selecting different Claude models (Opus, Sonnet, Haiku) through the settings menu, allowing users to choose the best model for their needs based on performance and cost.

**Key Components:**

**SettingsManager** (`service/SettingsManager.kt`):
- Maintains a ConcurrentHashMap tracking selected model per user
- Provides three Claude model options with pricing information
- Default model is Sonnet 4.5 (claude-sonnet-4-5-20250929)

**Available Models (ModelOption enum):**

1. **Opus 4.1** (`claude-opus-4-1-20250805`)
   - Most powerful model for complex tasks
   - Pricing: $15/MTok input, $75/MTok output
   - Context: 200K tokens, Max output: 32K tokens

2. **Sonnet 4.5** (`claude-sonnet-4-5-20250929`) - **Recommended**
   - Best balance of quality and price
   - Pricing: $3/MTok input, $15/MTok output
   - Context: 200K tokens, Max output: 64K tokens

3. **Haiku 4.5** (`claude-haiku-4-5-20251001`)
   - Fast and economical model
   - Pricing: $1/MTok input, $5/MTok output
   - Context: 200K tokens, Max output: 64K tokens

**Commands:**
- `/settings` - Opens settings menu showing current model and temperature
- `/model_opus` - Switch to Opus 4.1 (clears chat history)
- `/model_sonnet` - Switch to Sonnet 4.5 (clears chat history)
- `/model_haiku` - Switch to Haiku 4.5 (clears chat history)

**How it works:**
1. User sends `/settings` command to see current model and available options
2. User selects desired model (e.g., `/model_sonnet`)
3. SettingsManager stores the model selection for that user
4. **Chat history is automatically cleared** when switching models
5. All subsequent API requests use the user's selected model
6. Model selection persists across conversations until changed

**Implementation Details:**
- Model selection is applied per-user and stored in memory
- Chat history is cleared when changing models to avoid context mismatch
- ClaudeApiClient retrieves user's model from SettingsManager before each request
- Model pricing information is used to calculate cost per request

## Response Metrics Feature

Every response from the bot includes detailed metrics showing performance and cost information.

**Metrics Displayed:**

Each response includes a footer with the following information:
- ⏱️ **Response Time**: Time taken to generate response (in seconds)
- 📥 **Input Tokens**: Number of tokens in the request (user message + conversation history)
- 📤 **Output Tokens**: Number of tokens in the response
- 💰 **Cost**: Calculated cost for this specific request in USD (based on model pricing)
- 🤖 **Model Used**: Name of the Claude model used (Opus 4.1, Sonnet 4.5, or Haiku 4.5)
- 📚 **History**: Current history size / threshold in tokens with percentage (NEW!)

**Example Metrics Footer:**
```
📊 Метрики:
⏱️ Время: 2.543s
📥 Токены (вход): 1523
📤 Токены (выход): 456
💰 Стоимость: 0.011340$
🤖 Модель: Sonnet 4.5
📚 История: 6500/8000 токенов (81%)
```

**History Display in Metrics:**
- Shows current history size vs compression threshold
- Percentage indicates how close to compression trigger
- Updates in real-time with each message
- Helps users monitor when compression will occur

**ClaudeResponseWithMetrics** (`model/ClaudeModels.kt`):
- New data class that wraps response message with metrics
- Contains: message, inputTokens, outputTokens, responseTimeMs, cost, modelUsed
- Returned by ClaudeApiClient instead of plain String

**Cost Calculation:**
- Cost is calculated using the formula: `(inputTokens / 1M * inputPrice) + (outputTokens / 1M * outputPrice)`
- Prices are stored in the ModelOption enum for each model
- Calculated in real-time for each request

**Implementation Details:**
- Response time is measured from start to end of API call in ClaudeApiClient
- Token counts are extracted from Claude API response (Usage object)
- Metrics are appended to the last message when splitting long responses
- All metrics are logged for debugging and monitoring purposes

## Token Limit Configuration Feature

The bot allows users to configure the maximum number of output tokens per response, providing control over response length and API costs.

**Key Components:**

**SettingsManager** (`service/SettingsManager.kt`):
- Maintains a ConcurrentHashMap tracking max tokens per user
- Default max tokens: 4096
- Token range validation: 1-10000 tokens
- Provides validation method `isValidTokenValue()`

**Commands:**
- `/settings` - Shows current max tokens setting
- `/set_tokens <number>` - Set maximum output tokens (range: 1-10000)

**How it works:**
1. User sends `/set_tokens <number>` command (e.g., `/set_tokens 2000`)
2. Bot validates the input:
   - Must be a number
   - Must be in range 1-10000
3. SettingsManager stores the token limit for that user
4. All subsequent API requests use the user's token limit
5. Token limit persists across conversations until changed

**Validation:**
- **Type check**: Value must be an integer
- **Range check**: Value must be between `SettingsManager.MIN_TOKENS` (1) and `SettingsManager.MAX_TOKENS` (10000)
- Error messages provide clear feedback for invalid inputs

**Example Usage:**
```
/set_tokens 2000  → Sets max tokens to 2000
/set_tokens abc   → Error: must be a number
/set_tokens 15000 → Error: out of range (max 10000)
```

**Token Estimation:**
The bot provides helpful approximations when setting tokens:
- 1 token ≈ 2 Russian characters
- 1 token ≈ 4 English characters

**Implementation Details:**
- Token limit is applied per-user and stored in memory
- Settings survive across different bot modes (normal, expert, reasoning)
- ClaudeApiClient retrieves user's max tokens from SettingsManager before each request
- Token limit is included in the `ClaudeRequest` model's `maxTokens` field
- Token limit is logged for debugging purposes

**Smart Format Handling:**
- If token limit is < 1000 and response format is JSON or XML, structured parsing is automatically disabled
- This prevents incomplete JSON/XML responses that would fail parsing
- Raw text response is returned instead when token limit is too low
- User receives a warning when setting tokens < 1000 about JSON/XML format being disabled

## Conversation History Management Feature

The bot implements intelligent conversation history management with automatic compression to optimize token usage and reduce API costs. **All conversation data is persisted to SQLite database**, ensuring history survives bot restarts.

**Strategy: Hybrid Sliding Window + Summaries**

The bot uses a sophisticated approach combining:
- **Sliding Window**: Keeps last N messages in full detail
- **Automatic Summarization**: Older messages are compressed into summaries
- **Multi-level Compression**: Multiple summaries can be merged into one

**Key Components:**

**ConversationHistoryManager** (`service/ConversationHistoryManager.kt`):
- Manages conversation history per user with SQLite persistence
- Automatically triggers compression when needed
- Stores messages and summaries in database tables
- Provides statistics and history display

**Database Tables:**
- `chat_messages` - Stores individual messages (user/assistant)
  - Fields: id, chat_id, role, content, created_at, is_summarized
- `chat_summaries` - Stores compression summaries
  - Fields: id, chat_id, summary_text, messages_summarized, created_at, sequence_number

**Repositories:**
- `ChatMessageRepository` - CRUD operations for messages
- `ChatSummaryRepository` - CRUD operations for summaries

**Configuration Parameters:**
- `DEFAULT_HISTORY_THRESHOLD` = 8000 tokens - Default compression trigger
- `MIN_HISTORY_THRESHOLD` = 500 tokens - Minimum allowed threshold
- `MAX_HISTORY_THRESHOLD` = 50000 tokens - Maximum allowed threshold
- `MAX_SUMMARIES` = 2 - Maximum summaries before merging
- `CHARS_PER_TOKEN` = 3 - Token estimation ratio (conservative)

**Token-Based Compression (New Approach):**

Instead of counting messages, compression is triggered by **token count**:

1. User sends messages normally
2. System estimates tokens: `message length / 3` (conservative)
3. When estimated tokens exceed threshold (e.g., 8000):
   - Compression automatically triggers
   - Keeps ~50% of threshold worth of recent messages
   - Rest are summarized via Claude API
4. Summary is stored (typically 2-4 sentences)
5. When summaries exceed 2:
   - All summaries are merged into one mega-summary
   - Prevents summary accumulation

**Why Token-Based is Better:**
- ✅ Handles long messages correctly (1 message can be 5000 tokens)
- ✅ Handles short messages correctly (100 messages can be 500 tokens)
- ✅ More predictable cost control
- ✅ Better alignment with Claude API pricing
- ✅ User-configurable threshold per chat

**History Structure:**
```
API Request Contains:
┌─────────────────────────────────────┐
│ [Summary 1] (as context message)    │ ← Compressed old messages
│ [Summary 2] (as context message)    │
├─────────────────────────────────────┤
│ [Recent Message 1] User: ...        │ ← Full detail
│ [Recent Message 2] Assistant: ...   │
│ ...                                 │
│ [Recent Message 20] Assistant: ...  │
└─────────────────────────────────────┘
```

**Commands:**

- `/history` - Display conversation history with summaries and recent messages
- `/history_stats` - Show statistics (total messages, summaries count, estimated tokens, threshold)
- `/set_history_threshold <tokens>` - Set compression threshold (500-50000 tokens)
- `/summarize` - Manually trigger history compression
- `/clear` - Clear all history and start fresh

**Summary Generation:**

Summaries are generated using Claude API with:
- Lower temperature (0.3) for consistency
- Fixed token limit (500 tokens)
- Focused prompt asking for key topics and decisions
- Automatic retry on failure

**Cost Savings Example:**

**Without Compression (100 messages):**
- Input tokens: ~50,000
- Cost per request: ~$0.15 (Sonnet 4.5)

**With Compression (100 messages):**
- Summary: ~500 tokens
- Recent messages: ~10,000 tokens
- Total input: ~10,500 tokens
- Cost per request: ~$0.03
- **Savings: 80%!**

**Implementation Details:**

- History is stored in-memory per user (ConcurrentHashMap)
- Summaries are injected as special context messages
- Compression is transparent to the user
- All mode changes (expert, reasoning) clear history
- Model changes also clear history to avoid context mismatch

**Message Flow:**
1. User sends message → Added to `historyManager` (may trigger compression)
2. Compression (if needed): Old messages → Claude API → Summary created
3. `historyManager.getMessagesForApi()` returns: [Summaries] + [Recent messages]
4. Messages sent to Claude API for response generation
5. Assistant response → Added to `historyManager` (may trigger compression)
6. User sees response with history metrics

**Important:** Each message is added to `historyManager` exactly once, ensuring summaries are created correctly and history doesn't duplicate.

**History Display Format:**

```
📚 История диалога

📝 Резюме предыдущих сообщений (2):
--- Резюме 1 ---
[Summary text...]

--- Резюме 2 ---
[Summary text...]

💬 Последние сообщения (20):
1. 👤 Вы: [message preview...]
2. 🤖 Ассистент: [response preview...]
...
```

**Statistics Display:**

```
📊 Статистика истории диалога

📈 Всего обработано сообщений: 45
📝 Количество резюме: 2
💬 Последних детальных сообщений: 20
🔢 Примерный размер (токены): 6500 / 8000 (81%)

⚙️ Порог сжатия: 8000 токенов

💡 Совет: Используйте /set_history_threshold для изменения порога сжатия.
```

**Recommended Threshold Values:**
- **1000 tokens** - Quick testing (minimal history, ~2-3 messages)
- **4000 tokens** - Economy mode (~ 10 messages, frequent compression)
- **8000 tokens** - Default balanced (~ 20 messages, good balance)
- **15000 tokens** - Large context (~ 40 messages, rare compression)
- **30000 tokens** - Maximum context (~ 80 messages, minimal compression)

**Benefits:**

1. **Persistent Storage**: All messages and summaries saved to SQLite database
2. **Survives Restarts**: History preserved even after bot restart or server reboot
3. **Cost Optimization**: Reduces token usage by up to 80%
4. **Context Preservation**: Important information retained in summaries
5. **No Token Limit Issues**: Prevents hitting Claude's 200K context limit
6. **Transparent**: Works automatically without user intervention
7. **Flexible**: Manual compression available via `/summarize` (forces compression even if below threshold)
8. **Informative**: Users can view history and statistics anytime

**Manual Compression (`/summarize`):**
- Forces compression regardless of token threshold
- Useful for creating summaries before important context switches
- Requires minimum 4 messages in history
- **Behavior:** Keeps only the last 2 messages, summarizes all previous messages via Claude API
- **Summary prompt:** Structured prompt focusing on key topics, facts, decisions, and technical details
- **Summary length:** 5-8 sentences (500 tokens max)
- Temperature: 0.3 for consistent summaries
- **JSON serialization:** Configured to exclude null values (Jackson NON_NULL policy)

## Database Persistence (SQLite)

The bot uses SQLite for persistent storage of all conversation history. This ensures data survives application restarts and provides a foundation for advanced features.

**Database Configuration** (`application.yml`):
```yaml
spring:
  datasource:
    url: jdbc:sqlite:${DB_PATH:./data/aiassist.db}
    driver-class-name: org.sqlite.JDBC

  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update  # Auto-creates tables on startup
    properties:
      hibernate:
        id:
          new_generator_mappings: false  # SQLite compatibility
```

**Important Note:**
- Entities use `@GeneratedValue(strategy = GenerationType.AUTO)` for SQLite compatibility
- Old generator mappings (`new_generator_mappings: false`) required for SQLite JDBC driver
- If you encounter "not implemented by SQLite JDBC driver" error, delete `./data/aiassist.db` and restart

**Database Location:**
- Default: `./data/aiassist.db` (relative to working directory)
- Configurable via `DB_PATH` environment variable
- Example: `DB_PATH=/var/lib/aiassist/bot.db ./gradlew bootRun`

**Database Schema:**

**Table: `chat_messages`**
- `id` (PRIMARY KEY) - Auto-incrementing message ID
- `chat_id` - Telegram chat ID (indexed)
- `role` - Message role ("user" or "assistant")
- `content` - Message text (TEXT column, unlimited length)
- `created_at` - Timestamp when message was created
- `is_summarized` - Boolean flag (false = recent, true = compressed into summary)

**Table: `chat_summaries`**
- `id` (PRIMARY KEY) - Auto-incrementing summary ID
- `chat_id` - Telegram chat ID (indexed)
- `summary_text` - Summary content (TEXT column)
- `messages_summarized` - Count of messages in this summary
- `created_at` - Timestamp when summary was created
- `sequence_number` - Order of summaries (0 = oldest)

**Key Features:**

1. **Automatic Schema Creation**:
   - Tables created automatically on first run (hibernate ddl-auto: update)
   - No manual database setup required

2. **Data Lifecycle**:
   - `/clear` command deletes ALL history from database for that chat
   - Messages marked as `is_summarized=true` are kept for audit purposes
   - Summaries persist until `/clear` or manual deletion

3. **Transaction Safety**:
   - All write operations use `@Transactional` annotation
   - Ensures data consistency during compression and merging

4. **Database Backup**:
   - Simply copy `./data/aiassist.db` file for backup
   - Stop bot before copying to ensure consistency
   - Restore by replacing file and restarting bot

5. **Database Inspection**:
```bash
# View database with sqlite3 CLI
sqlite3 ./data/aiassist.db

# Useful queries:
SELECT COUNT(*) FROM chat_messages WHERE chat_id = 343222972;
SELECT * FROM chat_summaries ORDER BY created_at DESC;
SELECT COUNT(*) FROM chat_messages WHERE is_summarized = 0;
```

**Environment Variables:**
- `DB_PATH` - Database file path (default: `./data/aiassist.db`)
- `DB_SHOW_SQL` - Show SQL queries in logs (default: `false`)

**Dependencies** (`build.gradle.kts`):
- `spring-boot-starter-data-jpa` - JPA/Hibernate support
- `sqlite-jdbc:3.44.1.0` - SQLite JDBC driver
- `hibernate-community-dialects:6.3.1.Final` - SQLite dialect for Hibernate

## Performance Monitoring and Optimization

The bot includes detailed performance logging to track API call durations and identify bottlenecks.

**Performance Metrics Logged:**

1. **Database Operations:**
   - `💾 Saved message to DB` - Time to save message to database
   - `📚 Loaded history for chat` - Time to load history from database
   - Shows: summary count, recent message count, total messages

2. **Claude API Calls:**
   - `📊 Request stats` - Input size (messages, characters, estimated tokens)
   - `⏱️ Claude API call took Xms` - **Actual API response time**
   - Response metrics (input/output tokens, cost, model)

**Example Log Output:**
```
📚 Loaded history: 0 summaries, 2 recent messages, 2 total API messages (15ms)
📊 Request stats: 3 messages, ~450 chars (~150 tokens), model: claude-sonnet-4-5-20250929, max_tokens: 4096
⏱️ Claude API call took 28547ms (28.547s)
Response: 1523 input tokens, 456 output tokens, cost: 0.011340$
```

**Typical Response Times:**

- **Fast (1-5s)**: Short messages, small context, Haiku model
- **Normal (5-15s)**: Medium messages, moderate context, Sonnet model
- **Slow (15-40s)**: Long messages, large context, Opus model or reasoning modes
- **Very Slow (40-60s)**: Maximum tokens (8000+), expert panel mode, large history

**Performance Factors:**

1. **Model Selection** (biggest impact):
   - Haiku 4.5: Fastest (~2-8s)
   - Sonnet 4.5: Balanced (~5-20s)
   - Opus 4.1: Slowest (~10-40s)

2. **Max Tokens Setting**:
   - 1000 tokens: Fast
   - 4000 tokens: Medium (default)
   - 8000+ tokens: Slow

3. **Context Size**:
   - Small (< 2000 tokens): Fast
   - Medium (2000-5000 tokens): Normal
   - Large (5000-10000 tokens): Slow

4. **Reasoning Modes**:
   - Normal mode: Fast
   - Expert Panel mode: Slowest (complex reasoning)

**Optimization Tips:**

1. **Use Haiku for simple queries**:
   ```
   /model_haiku
   ```

2. **Reduce max tokens for faster responses**:
   ```
   /set_tokens 2000
   ```

3. **Compress history regularly**:
   ```
   /summarize
   ```

4. **Clear old conversations**:
   ```
   /clear
   ```

5. **Monitor logs** to identify slow queries:
   ```bash
   grep "⏱️ Claude API" logs/app.log
   ```

**Network Issues:**

If API calls consistently take > 60s:
- Check internet connection
- Verify Claude API status: https://status.anthropic.com
- Check firewall/proxy settings
- Try different network

**Timeout Configuration:**

Current timeouts (`ClaudeApiClient.kt`):
- Request timeout: 300000ms (5 minutes)
- Connect timeout: 60000ms (1 minute)
- Socket timeout: 300000ms (5 minutes)

These are intentionally high to accommodate long responses.
