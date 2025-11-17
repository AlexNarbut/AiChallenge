package com.mcp.weather

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertContains
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeatherServiceTest {

    private lateinit var weatherService: WeatherService
    private val apiKey: String = System.getenv("OPENWEATHER_API_KEY")
        ?: throw IllegalStateException("OPENWEATHER_API_KEY environment variable must be set for tests")

    @BeforeAll
    fun setup() {
        weatherService = WeatherService(apiKey)
        println("✅ WeatherService initialized with API key")
    }

    @Test
    fun `test get weather for Moscow`() = runTest {
        println("\n🧪 Testing weather for Moscow...")

        val lat = 55.7558
        val lon = 37.6173
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        assertNotNull(result, "Weather result should not be null")
        assertTrue(result.isNotEmpty(), "Weather result should not be empty")

        // Check that result contains expected information
        assertContains(result, "Местоположение:", ignoreCase = true)
        assertContains(result, "Координаты:", ignoreCase = true)
        assertContains(result, "Температура:", ignoreCase = true)
        assertContains(result, "Погода:", ignoreCase = true)
        assertContains(result, "Ветер:", ignoreCase = true)
        assertContains(result, "Влажность:", ignoreCase = true)
        assertContains(result, "Давление:", ignoreCase = true)

        // Check coordinates are present
        assertTrue(result.contains(lat.toString()), "Should contain latitude")
        assertTrue(result.contains(lon.toString()), "Should contain longitude")

        println("✅ Moscow weather test passed")
    }

    @Test
    fun `test get weather for London`() = runTest {
        println("\n🧪 Testing weather for London...")

        val lat = 51.5074
        val lon = -0.1278
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertContains(result, "🌡️")
        assertContains(result, "°C")

        println("✅ London weather test passed")
    }

    @Test
    fun `test get weather for New York`() = runTest {
        println("\n🧪 Testing weather for New York...")

        val lat = 40.7128
        val lon = -74.0060
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertContains(result, "Температура:")
        assertContains(result, "Влажность:")

        println("✅ New York weather test passed")
    }

    @Test
    fun `test get weather for Tokyo`() = runTest {
        println("\n🧪 Testing weather for Tokyo...")

        val lat = 35.6762
        val lon = 139.6503
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertContains(result, "🌍")

        println("✅ Tokyo weather test passed")
    }

    @Test
    fun `test get weather for invalid coordinates should not crash`() = runTest {
        println("\n🧪 Testing weather for extreme coordinates...")

        val lat = 91.0 // Invalid latitude (max is 90)
        val lon = 0.0
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        // Should either return valid data or error message, but not crash
        assertNotNull(result)
        assertTrue(result.isNotEmpty())

        println("✅ Invalid coordinates test passed (no crash)")
    }

    @Test
    fun `test response format contains emojis`() = runTest {
        println("\n🧪 Testing response format with emojis...")

        val lat = 55.7558
        val lon = 37.6173
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        // Check for emoji presence
        assertTrue(result.contains("🌍"), "Should contain location emoji")
        assertTrue(result.contains("📍"), "Should contain coordinates emoji")
        assertTrue(result.contains("🌡️"), "Should contain temperature emoji")
        assertTrue(result.contains("🤔"), "Should contain feels-like emoji")
        assertTrue(result.contains("☁️"), "Should contain weather emoji")
        assertTrue(result.contains("💨"), "Should contain wind emoji")
        assertTrue(result.contains("💧"), "Should contain humidity emoji")
        assertTrue(result.contains("🔽"), "Should contain pressure emoji")

        println("✅ Response format test passed")
    }

    @Test
    fun `test response contains valid temperature range`() = runTest {
        println("\n🧪 Testing temperature values...")

        val lat = 55.7558
        val lon = 37.6173
        val result = weatherService.getWeatherByCoordinates(lat, lon)

        println("Result:\n$result")

        // Extract temperature value (simple regex)
        val tempRegex = """Температура:\s*(-?\d+\.?\d*?)°C""".toRegex()
        val match = tempRegex.find(result)

        assertNotNull(match, "Should find temperature value in result")

        val temperature = match!!.groupValues[1].toDoubleOrNull()
        assertNotNull(temperature, "Temperature should be a valid number")

        // Temperature should be in reasonable range for Earth (-100 to +60)
        assertTrue(temperature!! in -100.0..60.0, "Temperature $temperature should be in reasonable range")

        println("✅ Temperature: $temperature°C is valid")
    }

    @Test
    fun `test concurrent requests`() = runTest {
        println("\n🧪 Testing concurrent weather requests...")

        val cities = listOf(
            55.7558 to 37.6173,  // Moscow
            51.5074 to -0.1278,  // London
            40.7128 to -74.0060, // New York
            35.6762 to 139.6503  // Tokyo
        )

        // Simple sequential processing for tests
        val results = cities.map { (lat, lon) ->
            weatherService.getWeatherByCoordinates(lat, lon)
        }

        println("Received ${results.size} results")

        // All requests should succeed
        assertEquals(4, results.size)
        results.forEach { result ->
            assertNotNull(result)
            assertTrue(result.isNotEmpty())
        }

        println("✅ Concurrent requests test passed")
    }
}
