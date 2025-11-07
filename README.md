# AI Assist Telegram Bot

A Telegram bot built with Kotlin, Spring Boot, and Ktor that integrates with Claude API to provide AI-powered responses.

## Features

- Receives messages from Telegram users
- Processes messages through Claude API
- Returns AI-generated responses to users
- **Structured response formats**: Support for JSON and XML formatted responses
- Built with Spring Boot for robust application management
- Uses Ktor client for efficient HTTP communication
- Conversation history management per user

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
- `TELEGRAM_MAX_MESSAGE_LENGTH`: Maximum message length before switching to PDF (default: 4000)
- `CLAUDE_API_KEY`: Claude API key
- `CLAUDE_RESPONSE_FORMAT`: Response format - `text` (default), `json`, or `xml`

### Response Format Configuration

The bot supports three response formats:

**Text format (default)**:
```bash
./gradlew bootRun
```

**JSON format**:
```bash
CLAUDE_RESPONSE_FORMAT=json ./gradlew bootRun
```

**XML format**:
```bash
CLAUDE_RESPONSE_FORMAT=xml ./gradlew bootRun
```

#### System Prompts

System prompts for formatted responses are stored in `src/main/resources/`:
- `json_format_requirements.txt` - Instructions for JSON-formatted responses
- `xml_format_requirements.txt` - Instructions for XML-formatted responses

These files contain detailed formatting rules that are sent to Claude API as system prompts. You can customize these files to modify the structure or requirements of the formatted responses.

**Expected response structure:**
- `question`: User's original question
- `answer`: AI's detailed response
- `urls`: List of source URLs (can be empty)
- `date`: ISO 8601 timestamp

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
    ├── FormatPromptLoader.kt    # Loads format requirement prompts
    ├── PdfGenerator.kt          # Generates PDF documents
    ├── ResponseParser.kt        # Parses JSON/XML responses
    └── TelegramBotService.kt    # Telegram bot service

src/main/resources/
├── application.yml              # Application configuration
├── expert_system_prompt.txt     # System prompt for expert mode
├── json_format_requirements.txt # System prompt for JSON format
└── xml_format_requirements.txt  # System prompt for XML format
```

## Usage

1. Start the bot
2. Open Telegram and find your bot by username
3. Send `/start` to initialize the bot and verify Claude API connection
4. Send any message to the bot
5. The bot will forward your message to Claude API and return the response

### Commands

- `/start` - Initialize bot and verify Claude API connection
- `/expert` - Switch to expert mode (fitness trainer)
- `/normal` - Switch back to normal conversation mode
- `/clear` - Clear conversation history for your chat

### Expert Mode

The bot includes an **Expert Mode** feature that allows you to interact with specialized AI personas. Currently includes a fitness trainer expert that helps create personalized training programs.

**How to use Expert Mode:**

1. Send `/expert` to activate expert mode
2. The fitness trainer will guide you through a survey to create a personalized workout plan
3. Answer the questions one by one
4. Get a customized weekly training program
5. Send `/normal` to return to regular conversation mode

**Expert system prompts are stored in:**
- `src/main/resources/expert_system_prompt.txt` - Contains the expert persona definition and behavior rules

You can customize the expert prompt or add new expert modes by creating additional prompt files and modifying the configuration.

### Long Response Handling

When responses exceed the configured maximum message length (default: 4000 characters), the bot automatically generates and sends a PDF document instead of a text message.

**Features:**
- Automatic detection of long responses
- PDF generation with formatted content
- Proper titles based on mode (e.g., "Fitness Training Plan" for expert mode)
- Timestamp included in PDF
- Automatic cleanup of temporary files
- Fallback to truncated text if PDF generation fails

**Configuration:**
```bash
TELEGRAM_MAX_MESSAGE_LENGTH=4000 ./gradlew bootRun
```

This is particularly useful for expert mode responses like training plans that can be quite lengthy.

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
