package com.stackbase.ytdownloader.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant

/**
 * Service to clean up old downloaded files that weren't deleted after download
 */
@Service
@EnableScheduling
class FileCleanupService(
    @Value("\${public.download.dir}") private val downloadDir: String,
    @Value("\${file.cleanup.max-age-hours:24}") private val maxAgeHours: Long
) {
    private val logger = LoggerFactory.getLogger(FileCleanupService::class.java)

    /**
     * Scheduled task that runs every hour to clean up old files
     */
    @Scheduled(fixedRate = 3600000) // Run every hour (3600000 ms)
    fun cleanupOldFiles() {
        logger.info("Starting scheduled file cleanup")
        val downloadDirectory = File(downloadDir)

        if (!downloadDirectory.exists() || !downloadDirectory.isDirectory) {
            logger.warn("Download directory does not exist: {}", downloadDirectory.absolutePath)
            return
        }

        // Process each chat ID directory
        val deletedCount = downloadDirectory.listFiles()?.filter { it.isDirectory }?.forEach {
            deleteDirectory(it)
        }

        logger.info("File cleanup completed. Deleted {} files", deletedCount)
    }

    private fun deleteDirectory(directory: File): Int {
        var deletedCount = 0
        val now = Instant.now()

        directory.listFiles()?.forEach { file ->
            try {
                deletedCount += if (file.isFile) {
                    val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                    val fileAge = Duration.between(attrs.creationTime().toInstant(), now)

                    // Delete files older than the configured max age
                    if (fileAge.toHours() > maxAgeHours) {
                        if (file.delete()) {
                            logger.info("Deleted old file: {} (age: {} hours)", file.absolutePath, fileAge.toHours())
                            1
                        } else {
                            logger.warn("Failed to delete old file: {}", file.absolutePath)
                            0
                        }
                    } else 0
                } else deleteDirectory(file)
            } catch (e: Exception) {
                logger.error("Error processing file {}: {}", file.absolutePath, e.message, e)
            }
        }

        // Delete empty chat directories
        if (directory.listFiles()?.isEmpty() == true && directory.delete()) {
            logger.info("Deleted empty directory: {}", directory.absolutePath)
        }

        return deletedCount
    }
}