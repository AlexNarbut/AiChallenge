package com.aiassist.bot.service

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class PdfGenerator {

    /**
     * Generate a PDF file from text content
     * @param content The text content to include in the PDF
     * @param title Optional title for the PDF
     * @return File object pointing to the generated PDF
     */
    fun generatePdf(content: String, title: String = "AI Assistant Response"): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "response_${timestamp}.pdf"
        val tempFile = File.createTempFile("ai_assistant_", ".pdf")

        try {
            logger.info { "Generating PDF: $fileName" }

            val pdfWriter = PdfWriter(tempFile)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            // Add title
            val titleParagraph = Paragraph(title)
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
            document.add(titleParagraph)

            // Add timestamp
            val timestampText = Paragraph("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
            document.add(timestampText)

            // Add content - split by lines to preserve formatting
            content.lines().forEach { line ->
                val paragraph = Paragraph(line)
                    .setFontSize(12f)
                    .setMarginBottom(5f)
                document.add(paragraph)
            }

            document.close()

            logger.info { "PDF generated successfully: ${tempFile.absolutePath}" }
            return tempFile
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate PDF" }
            throw e
        }
    }

    /**
     * Generate a PDF as byte array (for in-memory operations)
     */
    fun generatePdfBytes(content: String, title: String = "AI Assistant Response"): ByteArray {
        val outputStream = ByteArrayOutputStream()

        try {
            val pdfWriter = PdfWriter(outputStream)
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            // Add title
            val titleParagraph = Paragraph(title)
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
            document.add(titleParagraph)

            // Add timestamp
            val timestampText = Paragraph("Generated: ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
                .setFontSize(10f)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20f)
            document.add(timestampText)

            // Add content
            content.lines().forEach { line ->
                val paragraph = Paragraph(line)
                    .setFontSize(12f)
                    .setMarginBottom(5f)
                document.add(paragraph)
            }

            document.close()

            return outputStream.toByteArray()
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate PDF bytes" }
            throw e
        }
    }
}
