package com.stackbase.ytdownloader.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
    @GetMapping("/download/{chatId}/{filename}")
    fun downloadFile(@PathVariable chatId: Long, @PathVariable filename: String): ResponseEntity<FileSystemResource> {

        // Decode the URL-encoded filename
        val decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8.toString())

        println("Downloading file: $decodedFilename")
        val file = Path(downloadPath, chatId.toString(), decodedFilename).toFile()
        println("$file File exists: ${file.exists()}")

        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.name}\"")
            .body(FileSystemResource(file))
    }
}