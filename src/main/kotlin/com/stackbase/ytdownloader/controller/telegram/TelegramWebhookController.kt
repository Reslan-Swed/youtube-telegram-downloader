package com.stackbase.ytdownloader.controller.telegram

import com.pengrad.telegrambot.BotUtils
import com.pengrad.telegrambot.model.Update
import com.stackbase.ytdownloader.service.TelegramBotService
import com.stackbase.ytdownloader.service.YtDlpService
import com.stackbase.ytdownloader.utils.YouTubeUrlShortener
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * @author Reslan Swed
 * Created on 2025-06-07
 */
@RestController
@RequestMapping("/telegram/v1")
class TelegramWebhookController(
    private val telegramBotService: TelegramBotService,
    private val ytDlpService: YtDlpService,
    @Value("\${public.download.url}") private val publicUrl: String,
    @Value("\${telegram.bot.file.max-size}") private val botFileSizeMbLimit: Int,
) {
    private val logger = LoggerFactory.getLogger(TelegramWebhookController::class.java)

    @PostMapping("/webhook")
    fun onUpdate(request: HttpServletRequest) {
        try {
            val json = request.reader.readText()
            val update: Update = BotUtils.parseUpdate(json)

            logger.debug("Received update: {}", json)
            val message = update.message()
            val callbackQuery = update.callbackQuery()

            when {
                message != null && message.text() != null -> {
                    val chatId = message.chat().id()
                    val text = message.text()
                    logger.info("Received message from chat {}: {}", chatId, text)

                    telegramBotService.handleCommand(chatId, text)
                }

                callbackQuery != null -> {
                    val chatId = callbackQuery.message().chat().id()
                    val messageId = callbackQuery.message().messageId()
                    val data = callbackQuery.data().split("|")
                    val callbackId = callbackQuery.id()

                    logger.info("Received callback from chat {}: {}", chatId, data)

                    val type = data[0]

                    val maxBotFileSize = botFileSizeMbLimit * 1024 * 1024
                    when (type) {
                        "format" -> {
                            // User selected format (audio/video)
                            val format = data[1]
                            val url = data[2]

                            logger.info("User selected format: {}, URL: {}", format, url)

                            // Show quality options based on format
                            if (format == "audio") {
                                telegramBotService.showAudioQualityOptions(chatId, url, messageId)
                            } else {
                                telegramBotService.showVideoResolutionOptions(chatId, url, messageId)
                            }
                        }

                        "audio" -> {
                            // User selected audio quality
                            val kbps = data[1].toInt()
                            val url = YouTubeUrlShortener.toFull(data[2])

                            logger.info("Starting audio download: {}kbps, URL: {}", kbps, url)
                            telegramBotService.answerCallback(callbackId, "🎶 Downloading started...")
                            telegramBotService.editMessageText(
                                chatId,
                                messageId,
                                "🎶 Downloading audio at ${kbps}kbps..."
                            )

                            val (errorMessage, file) = ytDlpService.downloadAudio(url, kbps, "$chatId")
                            if (file != null) {
                                logger.info("Audio download completed: {}, size: {} bytes", file.name, file.length())
                                telegramBotService.editMessageText(chatId, messageId, "✅ Audio downloaded.")
                                if (file.length() < maxBotFileSize) {
                                    telegramBotService.sendFile(chatId, file)
                                } else {
                                    val encodedFileName =
                                        URLEncoder.encode(file.name, StandardCharsets.UTF_8.toString())
                                    val downloadUrl = "$publicUrl/$chatId/$encodedFileName"
                                    telegramBotService.sendTextMessage(
                                        chatId,
                                        "File is too large for Telegram. Download it here: $downloadUrl"
                                    )
                                }
                            } else {
                                logger.error("Audio download failed: {}", errorMessage)
                                telegramBotService.editMessageText(
                                    chatId,
                                    messageId,
                                    "❌ $errorMessage"
                                )
                            }
                        }

                        "video" -> {
                            // User selected video resolution
                            val resolution = data[1].toInt()
                            val url = YouTubeUrlShortener.toFull(data[2])

                            logger.info("Starting video download: {}p, URL: {}", resolution, url)
                            telegramBotService.answerCallback(callbackId, "🎬 Downloading started...")
                            telegramBotService.editMessageText(
                                chatId,
                                messageId,
                                "🎬 Downloading video at ${resolution}p..."
                            )

                            val (errorMessage, file) = ytDlpService.downloadVideo(url, resolution, "$chatId")
                            if (file != null) {
                                logger.info("Video download completed: {}, size: {} bytes", file.name, file.length())
                                telegramBotService.editMessageText(chatId, messageId, "✅ Video downloaded.")
                                if (file.length() < maxBotFileSize) {
                                    telegramBotService.sendFile(chatId, file)
                                } else {
                                    val encodedFileName =
                                        URLEncoder.encode(file.name, StandardCharsets.UTF_8.toString())
                                    val downloadUrl = "$publicUrl/$chatId/$encodedFileName"
                                    telegramBotService.sendTextMessage(
                                        chatId,
                                        "File is too large for Telegram. Download it here: $downloadUrl"
                                    )
                                }
                            } else {
                                logger.error("Video download failed: {}", errorMessage)
                                telegramBotService.editMessageText(
                                    chatId,
                                    messageId,
                                    "❌ $errorMessage"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing Telegram update", e)
        }
    }
}
