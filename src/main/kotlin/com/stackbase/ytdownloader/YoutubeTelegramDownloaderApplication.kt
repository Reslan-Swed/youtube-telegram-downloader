package com.stackbase.ytdownloader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File
import org.slf4j.LoggerFactory

@SpringBootApplication
@EnableScheduling
class YoutubeTelegramDownloaderApplication {
    companion object {
        private val logger = LoggerFactory.getLogger(YoutubeTelegramDownloaderApplication::class.java)
        
        init {
            // Ensure logs directory exists
            val logsDir = File("logs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
                logger.info("Created logs directory")
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<YoutubeTelegramDownloaderApplication>(*args)
}