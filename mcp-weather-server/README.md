# MCP Weather Server

MCP сервер для получения погоды по координатам используя OpenWeatherMap API.

**Transport:** STDIO (stdin/stdout) - работает через Model Context Protocol Kotlin SDK v0.5.0

## Требования

- Java 17+
- OpenWeatherMap API ключ (получите бесплатно на https://openweathermap.org/api)

## Установка и сборка

```bash
# Сборка проекта и создание JAR файла
JAVA_HOME=/Users/alexnarbut/Library/Java/JavaVirtualMachines/corretto-17.0.7/Contents/Home ./gradlew clean build

# JAR будет создан в: build/libs/mcp-weather-server-1.0.0.jar
```

## Локальное тестирование

```bash
# Установите переменную окружения с API ключом
export OPENWEATHER_API_KEY="your-api-key-here"

# Запустите сервер вручную (для тестирования)
java -jar build/libs/mcp-weather-server-1.0.0.jar

# Сервер будет ожидать JSON-RPC команды через stdin
# Логи выводятся в stderr
```

**Примечание:** Для использования с Claude Desktop НЕ нужно запускать сервер вручную - Claude Desktop запустит его автоматически.

## Подключение к Claude Desktop

### macOS

1. Отредактируйте файл конфигурации Claude Desktop:

```bash
code ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

2. Добавьте следующую конфигурацию:

```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/alexnarbut/AiAssistSpring/mcp-weather-server/build/libs/mcp-weather-server-1.0.0.jar"
      ],
      "env": {
        "OPENWEATHER_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

3. Перезапустите Claude Desktop

4. Проверьте что сервер подключен - в интерфейсе должна появиться иконка 🔌 или упоминание MCP серверов

### Windows

1. Отредактируйте файл конфигурации:

```
%APPDATA%\Claude\claude_desktop_config.json
```

2. Используйте такую же конфигурацию, только пути должны быть Windows-style:

```json
{
  "mcpServers": {
    "weather": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\YourUser\\AiAssistSpring\\mcp-weather-server\\build\\libs\\mcp-weather-server-1.0.0.jar"
      ],
      "env": {
        "OPENWEATHER_API_KEY": "your-api-key-here"
      }
    }
  }
}
```

## Использование в Claude

После подключения вы можете спросить Claude:

- "Какая погода в Москве?" (координаты: 55.7558, 37.6173)
- "Get weather for latitude 51.5074 and longitude -0.1278" (Лондон)
- "Покажи погоду для координат 40.7128, -74.0060" (Нью-Йорк)

Claude автоматически увидит ваш tool `get_weather` и использует его для ответа.

## Доступные инструменты (Tools)

### `get_weather`

Получить текущую погоду по географическим координатам.

**Параметры:**
- `latitude` (number): Широта (например, 55.7558 для Москвы)
- `longitude` (number): Долгота (например, 37.6173 для Москвы)

**Пример ответа:**
```
🌍 Местоположение: Moscow
📍 Координаты: 55.7558, 37.6173
🌡️ Температура: 5.2°C
🤔 Ощущается как: 2.1°C
☁️ Погода: облачно с прояснениями
💨 Ветер: 3.5 м/с
💧 Влажность: 76%
🔽 Давление: 1013 гПа
```

## Отладка

### Проверка логов

Claude Desktop пишет логи MCP серверов в:

**macOS:**
```bash
tail -f ~/Library/Logs/Claude/mcp*.log
```

**Windows:**
```
%APPDATA%\Claude\Logs\mcp*.log
```

### Тестирование сервера вручную

Вы можете протестировать сервер отправляя JSON-RPC запросы через stdin:

```bash
export OPENWEATHER_API_KEY="your-key"
java -jar build/libs/mcp-weather-server-1.0.0.jar
```

Затем отправьте JSON-RPC команды (каждая команда на новой строке):

**Инициализация:**
```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0.0"}}}
```

**Список инструментов:**
```json
{"jsonrpc":"2.0","id":2,"method":"tools/list"}
```

**Вызов инструмента погоды (Москва):**
```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"get_weather","arguments":{"latitude":55.7558,"longitude":37.6173}}}
```

Логи будут выводиться в stderr (System.err), а ответы JSON-RPC в stdout (System.out).

## Технические детали

### Архитектура

- **Transport:** StdioServerTransport - коммуникация через stdin/stdout
- **Protocol:** MCP (Model Context Protocol) v2024-11-05
- **SDK:** io.modelcontextprotocol:kotlin-sdk:0.5.0
- **Weather API:** OpenWeatherMap API v2.5

### Структура проекта

```
mcp-weather-server/
├── build.gradle.kts          # Gradle конфигурация (Kotlin 2.1.10, MCP SDK 0.5.0)
├── settings.gradle.kts        # Настройки проекта
├── README.md                  # Эта документация
└── src/main/kotlin/com/mcp/weather/
    └── WeatherServer.kt       # Сервер MCP + Weather Service + Tool
```

### Как работает

1. Claude Desktop запускает JAR как subprocess
2. Сервер подключается через StdioServerTransport (stdin/stdout)
3. Claude Desktop отправляет JSON-RPC команды через stdin
4. Сервер обрабатывает команды используя MCP SDK
5. Ответы возвращаются через stdout в формате JSON-RPC
6. Логи выводятся в stderr (видны в логах Claude Desktop)

## Troubleshooting

### "OPENWEATHER_API_KEY environment variable is required"

Убедитесь что вы добавили API ключ в конфигурацию Claude Desktop в секции `env`.

### Сервер не появляется в Claude

1. Проверьте что путь к JAR файлу корректный
2. Убедитесь что JAR файл собран (`./gradlew jar`)
3. Перезапустите Claude Desktop полностью (Quit и запустите заново)
4. Проверьте логи Claude Desktop

### "BUILD FAILED" при сборке

Убедитесь что используете Java 17+:

```bash
java -version  # должна быть версия 17 или выше
```

## Расширение функционала

Вы можете добавить больше tools в сервер, например:
- Прогноз погоды на несколько дней
- Геокодирование (адрес → координаты)
- Поиск городов по названию
- Historical weather data

Для этого добавьте новые Tool определения в код и реализуйте соответствующие хэндлеры.
