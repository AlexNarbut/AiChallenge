package com.aiassist.bot.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResponseParserTest {

    private val parser = ResponseParser()

    @Test
    fun `test JSON parsing with valid response`() {
        val jsonResponse = """
            {
              "question": "What is Kotlin?",
              "answer": "Kotlin is a modern programming language",
              "urls": ["https://kotlinlang.org"],
              "date": "2025-11-05T12:00:00Z"
            }
        """.trimIndent()

        val result = parser.parseResponse(jsonResponse, "json")

        assertTrue(result.contains("What is Kotlin?"))
        assertTrue(result.contains("Kotlin is a modern programming language"))
        assertTrue(result.contains("https://kotlinlang.org"))
        assertTrue(result.contains("2025-11-05T12:00:00Z"))
    }

    @Test
    fun `test XML parsing with valid response`() {
        val xmlResponse = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
              <question>What is Spring Boot?</question>
              <answer>Spring Boot is a framework</answer>
              <urls>
                <url>https://spring.io</url>
              </urls>
              <date>2025-11-05T12:00:00Z</date>
            </response>
        """.trimIndent()

        val result = parser.parseResponse(xmlResponse, "xml")

        assertTrue(result.contains("What is Spring Boot?"))
        assertTrue(result.contains("Spring Boot is a framework"))
        assertTrue(result.contains("https://spring.io"))
        assertTrue(result.contains("2025-11-05T12:00:00Z"))
    }

    @Test
    fun `test text format returns response as-is`() {
        val textResponse = "This is a plain text response"

        val result = parser.parseResponse(textResponse, "text")

        assertEquals(textResponse, result)
    }

    @Test
    fun `test invalid JSON handling`() {
        val invalidJson = "This is not valid JSON"

        val result = parser.parseResponse(invalidJson, "json")

        assertTrue(result.contains("Failed to parse JSON response"))
        assertTrue(result.contains(invalidJson))
    }

    @Test
    fun `test invalid XML handling`() {
        val invalidXml = "This is not valid XML"

        val result = parser.parseResponse(invalidXml, "xml")

        assertTrue(result.contains("Failed to parse XML response"))
        assertTrue(result.contains(invalidXml))
    }

    @Test
    fun `test isValidJson with valid JSON`() {
        val validJson = """{"key": "value"}"""
        assertTrue(parser.isValidJson(validJson))
    }

    @Test
    fun `test isValidJson with invalid JSON`() {
        val invalidJson = "not json"
        assertFalse(parser.isValidJson(invalidJson))
    }

    @Test
    fun `test isValidXml with valid XML`() {
        val validXml = """<?xml version="1.0"?><root><item>value</item></root>"""
        assertTrue(parser.isValidXml(validXml))
    }

    @Test
    fun `test isValidXml with invalid XML`() {
        val invalidXml = "not xml"
        assertFalse(parser.isValidXml(invalidXml))
    }
}
