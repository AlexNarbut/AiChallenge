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
