package com.mcp.weather

import kotlinx.coroutines.runBlocking

/**
 * Manual test to verify weather API is working
 * Run this with: ./gradlew test --tests ManualWeatherTest
 */
fun main() = runBlocking {
    val apiKey = System.getenv("OPENWEATHER_API_KEY") ?: "c6dc29e33173e6e9924fb2b301fa5293"
    val weatherService = WeatherService(apiKey)

    println("=".repeat(60))
    println("🧪 Тестирование Weather MCP Server")
    println("=".repeat(60))

    val cities = listOf(
        Triple("Москва", 55.7558, 37.6173),
        Triple("Лондон", 51.5074, -0.1278),
        Triple("Нью-Йорк", 40.7128, -74.0060),
        Triple("Токио", 35.6762, 139.6503),
        Triple("Сидней", -33.8688, 151.2093)
    )

    cities.forEach { (name, lat, lon) ->
        println("\n📍 $name (${lat}, ${lon})")
        println("-".repeat(60))
        val result = weatherService.getWeatherByCoordinates(lat, lon)
        println(result)
    }

    println("\n" + "=".repeat(60))
    println("✅ Все запросы завершены!")
    println("=".repeat(60))

    weatherService.close()
}
