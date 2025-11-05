package com.aiassist.bot.service

import mu.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.IOException

private val logger = KotlinLogging.logger {}

@Service
class FormatPromptLoader {

    private val prompts = mutableMapOf<String, String>()

    init {
        loadPrompts()
    }

    private fun loadPrompts() {
        try {
            prompts["json"] = loadPromptFromFile("json_format_requirements.txt")
            prompts["xml"] = loadPromptFromFile("xml_format_requirements.txt")
            logger.info { "Successfully loaded format prompts for: ${prompts.keys}" }
        } catch (e: Exception) {
            logger.error(e) { "Error loading format prompts" }
        }
    }

    private fun loadPromptFromFile(filename: String): String {
        return try {
            val resource = ClassPathResource(filename)
            resource.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            logger.error(e) { "Failed to load prompt file: $filename" }
            throw e
        }
    }

    fun getPromptForFormat(format: String): String? {
        return prompts[format.lowercase()]
    }

    fun hasPromptForFormat(format: String): Boolean {
        return prompts.containsKey(format.lowercase())
    }
}
