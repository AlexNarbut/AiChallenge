package com.aiassist.bot.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

@Service
class ResponseParser {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    /**
     * Parse response based on format and return a formatted string
     */
    fun parseResponse(response: String, format: String): String {
        return when (format.lowercase()) {
            "json" -> parseJsonResponse(response)
            "xml" -> parseXmlResponse(response)
            else -> response // Return as-is for text format
        }
    }

    /**
     * Parse JSON response and extract fields into a readable format
     */
    private fun parseJsonResponse(response: String): String {
        return try {
            logger.debug { "Parsing JSON response of length: ${response.length}" }

            // Strip markdown code blocks if present
            val cleanedResponse = response
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            logger.debug { "Cleaned response length: ${cleanedResponse.length}, first 100 chars: ${cleanedResponse.take(100)}" }

            val jsonNode = objectMapper.readTree(cleanedResponse)

            val question = jsonNode.get("question")?.asText() ?: ""
            val answer = jsonNode.get("answer")?.asText() ?: ""
            val urls = jsonNode.get("urls")?.map { it.asText() } ?: emptyList()
            val date = jsonNode.get("date")?.asText() ?: ""

            buildString {
                if (question.isNotEmpty()) {
                    appendLine("📝 Question: $question")
                    appendLine()
                }
                if (answer.isNotEmpty()) {
                    appendLine("💡 Answer:")
                    appendLine(answer)
                    appendLine()
                }
                if (urls.isNotEmpty()) {
                    appendLine("🔗 Sources:")
                    urls.forEach { url ->
                        appendLine("  • $url")
                    }
                    appendLine()
                }
                if (date.isNotEmpty()) {
                    appendLine("📅 Date: $date")
                }
            }.trim()
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse JSON response. Length: ${response.length}" }

            // Show truncated response if too long
            val preview = if (response.length > 500) {
                "${response.take(250)}\n\n... (${response.length - 500} characters omitted) ...\n\n${response.takeLast(250)}"
            } else {
                response
            }

            "❌ Failed to parse JSON response.\n\nError: ${e.message}\n\nRaw response preview:\n```\n$preview\n```"
        }
    }

    /**
     * Parse XML response and extract fields into a readable format
     */
    private fun parseXmlResponse(response: String): String {
        return try {
            // Strip markdown code blocks if present
            val cleanedResponse = response
                .trim()
                .removePrefix("```xml")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc: Document = dBuilder.parse(InputSource(StringReader(cleanedResponse)))
            doc.documentElement.normalize()

            val root = doc.documentElement

            val question = getElementText(root, "question")
            val answer = getElementText(root, "answer")
            val urls = getElementTextList(root, "urls", "url")
            val date = getElementText(root, "date")

            buildString {
                if (question.isNotEmpty()) {
                    appendLine("📝 Question: $question")
                    appendLine()
                }
                if (answer.isNotEmpty()) {
                    appendLine("💡 Answer:")
                    appendLine(answer)
                    appendLine()
                }
                if (urls.isNotEmpty()) {
                    appendLine("🔗 Sources:")
                    urls.forEach { url ->
                        appendLine("  • $url")
                    }
                    appendLine()
                }
                if (date.isNotEmpty()) {
                    appendLine("📅 Date: $date")
                }
            }.trim()
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse XML response. Length: ${response.length}" }

            // Show truncated response if too long
            val preview = if (response.length > 500) {
                "${response.take(250)}\n\n... (${response.length - 500} characters omitted) ...\n\n${response.takeLast(250)}"
            } else {
                response
            }

            "❌ Failed to parse XML response.\n\nError: ${e.message}\n\nRaw response preview:\n```\n$preview\n```"
        }
    }

    private fun getElementText(parent: Element, tagName: String): String {
        val nodeList = parent.getElementsByTagName(tagName)
        return if (nodeList.length > 0) {
            nodeList.item(0).textContent ?: ""
        } else {
            ""
        }
    }

    private fun getElementTextList(parent: Element, containerTag: String, itemTag: String): List<String> {
        val containerList = parent.getElementsByTagName(containerTag)
        if (containerList.length == 0) return emptyList()

        val container = containerList.item(0) as Element
        val itemList = container.getElementsByTagName(itemTag)

        return (0 until itemList.length).map { i ->
            itemList.item(i).textContent ?: ""
        }.filter { it.isNotEmpty() }
    }

    /**
     * Validate if response is valid JSON
     */
    fun isValidJson(response: String): Boolean {
        return try {
            objectMapper.readTree(response)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Validate if response is valid XML
     */
    fun isValidXml(response: String): Boolean {
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            dBuilder.parse(InputSource(StringReader(response)))
            true
        } catch (e: Exception) {
            false
        }
    }
}
