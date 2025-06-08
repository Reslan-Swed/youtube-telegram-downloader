package com.stackbase.ytdownloader.controller

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.FileInputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.Path

/**
 * @author Reslan Swed
 * Created on 2025-06-06
 */
@RestController
@RequestMapping("/public")
class PublicController(
    @Value("\${public.download.dir}") private val downloadPath: String
) {
    private val logger = LoggerFactory.getLogger(PublicController::class.java)

    @GetMapping("/download/{chatId}/{filename}")
    fun downloadFile(@PathVariable chatId: Long, @PathVariable filename: String): ResponseEntity<StreamingResponseBody> {
        // Decode the URL-encoded filename
        val decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8.toString())
        logger.info("Download request received for file: {}", decodedFilename)

        val file = Path(downloadPath, chatId.toString(), decodedFilename).toFile()
        logger.debug("File path: {}, exists: {}", file.absolutePath, file.exists())

        if (!file.exists()) {
            logger.warn("File not found: {}", file.absolutePath)
            return ResponseEntity.notFound().build()
        }

        val contentType = when {
            file.name.endsWith(".mp4") -> MediaType.parseMediaType("video/mp4")
            file.name.endsWith(".mp3") -> MediaType.parseMediaType("audio/mpeg")
            file.name.endsWith(".webm") -> MediaType.parseMediaType("video/webm")
            else -> MediaType.APPLICATION_OCTET_STREAM
        }

        val headers = HttpHeaders().apply {
            this.contentDisposition = ContentDisposition
                .builder("attachment")
                .filename(file.name, StandardCharsets.UTF_8)
                .build()
            this.contentType = contentType
            this.contentLength = file.length()
        }

        logger.info("Serving file for download: {}, size: {} bytes", file.name, file.length())

        // Create a streaming response that deletes the file after it's been sent
        val responseBody = StreamingResponseBody { outputStream: OutputStream ->
            try {
                FileInputStream(file).use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }

                // Delete the file after it's been sent
                if (file.delete()) {
                    logger.info("File deleted after download: {}", file.absolutePath)
                } else {
                    logger.warn("Failed to delete file after download: {}", file.absolutePath)
                }
            } catch (e: Exception) {
                logger.error("Error while streaming file: {}", e.message, e)
            }
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(responseBody)
    }
}