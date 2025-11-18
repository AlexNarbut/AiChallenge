package com.mcp.weather

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// Weather API models
@Serializable
data class WeatherResponse(
    val coord: Coord? = null,
    val weather: List<Weather>? = null,
    val main: Main? = null,
    val wind: Wind? = null,
    val name: String? = null,
    val cod: Int? = null,
    val message: String? = null
)

@Serializable
data class Coord(val lat: Double, val lon: Double)

@Serializable
data class Weather(
    val description: String,
    val main: String,
    val id: Int? = null,
    val icon: String? = null
)

@Serializable
data class Main(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val humidity: Int,
    val pressure: Int,
    @SerialName("temp_min") val tempMin: Double? = null,
    @SerialName("temp_max") val tempMax: Double? = null,
    @SerialName("sea_level") val seaLevel: Int? = null,
    @SerialName("grnd_level") val grndLevel: Int? = null
)

@Serializable
data class Wind(
    val speed: Double,
    val deg: Int? = null,
    val gust: Double? = null
)

// Weather Service
class WeatherService(private val apiKey: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getWeatherByCoordinates(lat: Double, lon: Double): String {
        return try {
            val response: WeatherResponse = client.get(
                "https://api.openweathermap.org/data/2.5/weather"
            ) {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("appid", apiKey)
                parameter("units", "metric")
                parameter("lang", "ru")
            }.body()

            // Check for API error response
            if (response.cod != null && response.cod != 200) {
                return "❌ Ошибка API (код ${response.cod}): ${response.message ?: "Неизвестная ошибка"}"
            }

            // Check if we have all required data
            if (response.main == null || response.coord == null || response.wind == null) {
                return "❌ Получены неполные данные от API"
            }

            buildString {
                appendLine("🌍 Местоположение: ${response.name ?: "Неизвестно"}")
                appendLine("📍 Координаты: ${response.coord.lat}, ${response.coord.lon}")
                appendLine("🌡️ Температура: ${response.main.temp}°C")
                appendLine("🤔 Ощущается как: ${response.main.feelsLike}°C")
                appendLine("☁️ Погода: ${response.weather?.firstOrNull()?.description ?: "Неизвестно"}")
                appendLine("💨 Ветер: ${response.wind.speed} м/с")
                appendLine("💧 Влажность: ${response.main.humidity}%")
                appendLine("🔽 Давление: ${response.main.pressure} гПа")
            }
        } catch (e: Exception) {
            "❌ Ошибка при получении погоды: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
}

/**
 * Starts the MCP Weather Server using stdio transport
 */
fun main() {
    val apiKey = System.getenv("OPENWEATHER_API_KEY")
        ?: throw IllegalStateException("OPENWEATHER_API_KEY is required. Set it in the environment variable.")

    val weatherService = WeatherService(apiKey)

    Runtime.getRuntime().addShutdownHook(Thread {
        System.err.println("🌤️ Shutting down Weather MCP Server...")
        weatherService.close()
    })

    System.err.println("🌤️ Weather MCP Server starting (stdio transport)...")

    // Create the MCP Server instance
    val server = Server(
        Implementation(
            name = "weather-mcp-server",
            version = "1.0.0"
        ),
        ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true)
            )
        )
    )

    // Register the weather tool
    server.addTool(
        name = "get_weather",
        description = "Получить текущую погоду по географическим координатам (широта и долгота)",
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("latitude") {
                    put("type", "number")
                    put("description", "Широта (например, 55.7558 для Москвы)")
                }
                putJsonObject("longitude") {
                    put("type", "number")
                    put("description", "Долгота (например, 37.6173 для Москвы)")
                }
            },
            required = listOf("latitude", "longitude")
        )
    ) { request ->
        val lat = request.arguments?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val lon = request.arguments?.get("longitude")?.jsonPrimitive?.doubleOrNull

        if (lat == null || lon == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("Required fields 'latitude' and 'longitude' must be valid numbers"))
            )
        }

        val weatherInfo = runBlocking {
            weatherService.getWeatherByCoordinates(lat, lon)
        }

        CallToolResult(content = listOf(TextContent(weatherInfo)))
    }

    // Create stdio transport
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    System.err.println("✅ Weather MCP Server ready (waiting for requests via stdio)")

    // Connect and run server until cancelled
    runBlocking {
        val job = launch {
            try {
                server.connect(transport)
                // This will suspend forever until the job is cancelled
                awaitCancellation()
            } catch (e: Exception) {
                System.err.println("🌤️ Server exception: ${e.message}")
            }
        }

        // Wait for the job to complete
        job.join()
    }

    System.err.println("🌤️ Server shutting down")
}
