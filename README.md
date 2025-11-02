# AI Assist Telegram Bot

A Telegram bot built with Kotlin, Spring Boot, and Ktor that integrates with Claude API to provide AI-powered responses.

## Features

- Receives messages from Telegram users
- Processes messages through Claude API
- Returns AI-generated responses to users
- Built with Spring Boot for robust application management
- Uses Ktor client for efficient HTTP communication

## Prerequisites

- Java 17 or higher
- Gradle 8.x
- Telegram Bot Token (from [@BotFather](https://t.me/botfather))
- Claude API Key (from [Anthropic Console](https://console.anthropic.com/))

## Setup

1. **Clone the repository**

2. **Configure environment variables**

   Copy `.env.example` to `.env` and fill in your credentials:
   ```bash
   cp .env.example .env
   ```

   Edit `.env` with your actual values:
   - `TELEGRAM_BOT_TOKEN`: Your Telegram bot token from BotFather
   - `TELEGRAM_BOT_USERNAME`: Your bot's username
   - `CLAUDE_API_KEY`: Your Claude API key

3. **Build the project**
   ```bash
   ./gradlew build
   ```

4. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

   Or with environment variables:
   ```bash
   TELEGRAM_BOT_TOKEN=your-token CLAUDE_API_KEY=your-key ./gradlew bootRun
   ```

## Configuration

Configuration can be set in `src/main/resources/application.yml` or via environment variables:

- `TELEGRAM_BOT_TOKEN`: Telegram bot token
- `TELEGRAM_BOT_USERNAME`: Bot username
- `CLAUDE_API_KEY`: Claude API key

## Project Structure

```
src/main/kotlin/com/aiassist/bot/
├── AiAssistBotApplication.kt    # Main application entry point
├── config/
│   └── BotConfiguration.kt      # Configuration classes
├── model/
│   └── ClaudeModels.kt          # Data models for Claude API
└── service/
    ├── ClaudeApiClient.kt       # Claude API client
    └── TelegramBotService.kt    # Telegram bot service
```

## Usage

1. Start the bot
2. Open Telegram and find your bot by username
3. Send a message to the bot
4. The bot will forward your message to Claude API and return the response

## Development

Run tests:
```bash
./gradlew test
```

Clean build:
```bash
./gradlew clean build
```

## License

MIT
