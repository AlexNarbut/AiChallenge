# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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
Currently, each message is independent. To add context, maintain a conversation history (Map of chatId to message list) and include previous messages in the `messages` array sent to Claude API.
