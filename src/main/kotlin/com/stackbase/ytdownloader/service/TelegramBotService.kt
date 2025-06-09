package com.stackbase.ytdownloader.service

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.model.CallbackQuery
import com.pengrad.telegrambot.model.Message
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.request.SendDocument
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.EditMessageText
import com.stackbase.ytdownloader.utils.YouTubeUrlShortener
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val AUDIO = "audio"
private const val VIDEO = "video"
private const val FORMAT = "format"

/**
 * @author Reslan Swed
 * Created on 2025-06-07
 */
@Service
class TelegramBotService(
    @Value("\${telegram.bot.token}") private val botToken: String,
    private val ytDlpService: YtDlpService,
    @Value("\${public.download.url}") private val publicUrl: String,
    @Value("\${telegram.bot.file.max-size}") private val botFileSizeMbLimit: Int
) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)

    private val bot = TelegramBot(botToken)
    private val maxBotFileSize = botFileSizeMbLimit * 1024 * 1024
    private val formatUnit: (String) -> Pair<String, String> = {
        when (it) {
            AUDIO -> "quality" to "kbps"
            VIDEO -> "resolution" to "p"
            else -> "" to ""
        }
    }
    private val formatIcon: (String) -> String = {
        when (it) {
            AUDIO -> "🎵"
            VIDEO -> "🎬"
            else -> ""
        }
    }
    private val availableQuality: (String) -> List<String> = {
        when (it) {
            AUDIO -> listOf("128kbps", "192kbps", "256kbps")
            VIDEO -> listOf("360p", "480p", "720p", "1080p")
            else -> emptyList()
        }
    }

    fun handleCommand(message: Message) {
        val chatId = message.chat().id()
        val messageText = message.text()

        logger.info("Received message from chat {}: {}", chatId, messageText)

        when {
            messageText.startsWith("/start") -> handleStartCommand(chatId)

            messageText.startsWith("/help") -> handleHelpCommand(chatId)

            messageText.startsWith("/audio") -> {
                val url = messageText.removePrefix("/audio").trim()
                if (isYoutubeUrl(url)) {
                    logger.info("Processing audio command with URL: {}", url)
                    showQualityOptions(chatId, url, AUDIO)
                } else {
                    logger.warn("Invalid YouTube URL for audio: {}", url)
                    sendTextMessage(chatId, "❌ Invalid YouTube URL for audio.")
                }
            }

            messageText.startsWith("/video") -> {
                val url = messageText.removePrefix("/video").trim()
                if (isYoutubeUrl(url)) {
                    logger.info("Processing video command with URL: {}", url)
                    showQualityOptions(chatId, url, VIDEO)
                } else {
                    logger.warn("Invalid YouTube URL for video: {}", url)
                    sendTextMessage(chatId, "❌ Invalid YouTube URL for video.")
                }
            }

            else -> {
                if (YouTubeUrlShortener.isYoutubeUrl(messageText)) {
                    // User sent a YouTube URL - ask for format choice
                    logger.info("Detected YouTube URL: {}", messageText)
                    showFormatOptions(chatId, messageText)
                } else {
                    logger.info("Unknown input: {}", messageText)
                    sendTextMessage(
                        chatId,
                        "❓ Unknown input. Please send a valid YouTube URL or use /help for instructions."
                    )
                }
            }
        }
    }

    private fun handleStartCommand(chatId: Long) {
        logger.info("Sending start command response to chat {}", chatId)
        val text = """
        🎥 YouTube Downloader Bot — Download YouTube videos and audio files directly through Telegram!

        Features:

        🎵 Download audio in 128kbps, 192kbps, or 256kbps quality

        🎬 Download videos in 360p, 480p, 720p or 1080p resolution

        📥 Fast, reliable, and easy to use
        """.trimIndent()

        sendTextMessage(chatId, text)
    }

    private fun handleHelpCommand(chatId: Long) {
        logger.info("Sending help command response to chat {}", chatId)
        val text = """
        Hey there! 👋
        I’m your YouTube downloader bot — I can fetch audio and video from YouTube for you in different qualities. Here’s how you can use me:
        
        💡 Pro tip: Copy-paste a link directly or use the commands — easy and instant!
        
        🎵 Download Audio:
        
            Send the command: /audio <YouTube URL> 
            OR past a YouTube link then choose 'audio' format option
            
            I’ll show you a list of audio quality options (128kbps, 192kbps, 256kbps).
            
            Pick one — I’ll download and send it to you!
            
        🎬 Download Video:
        
            Send the command: /video <YouTube URL> 
            OR past a YouTube link then choose 'video' format option
            
            I’ll show you available video resolutions (360p, 480p, 720p, 1080p).
            
            Choose one — I’ll download and send it to you!
        
        ℹ️ Notes:
        
            ⚠️ Maximum file size I can send via Telegram is 50MB
            
            ⚠️ If a file exceeds the limit, it’ll be saved on the server’s downloads folder (if enabled)
            
            ⚠️ Only YouTube links are supported for now
        
        📜 Available Commands:
        
            /start — Show welcome message
            
            /help — Show this help guide
            
            /audio <YouTube URL> — Download audio
            
            /video <YouTube URL> — Download video
""".trimIndent()

        sendTextMessage(chatId, text)
    }

    fun handleCallback(callbackQuery: CallbackQuery) {
        val chatId = callbackQuery.message().chat().id()
        val messageId = callbackQuery.message().messageId()
        val data = callbackQuery.data().split("|")
        val callbackId = callbackQuery.id()
        logger.info("Received callback from chat {}: {}", chatId, data)

        val type = data[0]

        when (type) {
            FORMAT -> {
                // User selected format (audio/video)
                val format = data[1]
                val url = data[2]

                logger.info("User selected format: {}, URL: {}", format, url)

                // Show quality options based on format
                showQualityOptions(chatId, url, format, messageId)
            }

            AUDIO, VIDEO -> {
                // User selected audio quality/video resolution
                val quality = data[1].toInt()
                val url = YouTubeUrlShortener.toFull(data[2])
                val outputDir = "${uuidStringFromId(chatId.toString())}/${System.currentTimeMillis()}"
                val (_, unit) = formatUnit(type)
                val icon = formatIcon(type)
                val method = if (type == AUDIO) ytDlpService::downloadAudio else ytDlpService::downloadVideo

                logger.info("Starting {} download: {}{}, URL: {}", type, quality, unit, url)
                answerCallback(callbackId, "$icon Downloading started...")
                editMessageText(
                    chatId,
                    messageId,
                    "$icon Downloading $type at ${quality}$unit..."
                )
                val (errorMessage, file) = method(url, quality, outputDir)
                if (file != null) {
                    logger.info(
                        "{} download completed: {}, size: {} bytes",
                        type.replaceFirstChar { it.titlecase() },
                        file.name,
                        file.length()
                    )
                    editMessageText(
                        chatId,
                        messageId,
                        "✅ ${type.replaceFirstChar { it.titlecase() }} downloaded."
                    )
                    if (file.length() < maxBotFileSize) {
                        sendFile(chatId, file)
                    } else {
                        val encodedFileName =
                            URLEncoder.encode(file.name, StandardCharsets.UTF_8.toString())
                        val downloadUrl = "$publicUrl/$outputDir/$encodedFileName"
                        sendTextMessage(
                            chatId,
                            "File is too large for Telegram. Download it here: $downloadUrl"
                        )
                    }
                } else {
                    logger.error(
                        "{} download failed: {}",
                        type.replaceFirstChar { it.titlecase() },
                        errorMessage
                    )
                    editMessageText(chatId, messageId, "❌ $errorMessage")
                }
            }
        }
    }

    fun sendTextMessage(chatId: Long, text: String) {
        try {
            logger.debug("Sending text message to chat {}: {}", chatId, text)
            val response = bot.execute(SendMessage(chatId, text))
            if (!response.isOk) {
                logger.error("Failed to send message to chat {}: {}", chatId, response.description())
            }
        } catch (e: Exception) {
            logger.error("Error sending message to chat {}", chatId, e)
        }
    }

    fun editMessageText(chatId: Long, messageId: Int, text: String) {
        try {
            logger.debug("Editing message {} for chat {}: {}", messageId, chatId, text)
            val response = bot.execute(EditMessageText(chatId, messageId, text))
            if (!response.isOk) {
                logger.error("Failed to edit message {} for chat {}: {}", messageId, chatId, response.description())
            }
        } catch (e: Exception) {
            logger.error("Error editing message {} for chat {}", messageId, chatId, e)
        }
    }

    // Show format options (audio/video)
    private fun showFormatOptions(chatId: Long, url: String) {
        val shortUrl = YouTubeUrlShortener.toShort(url)

        val keyboard = InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("${formatIcon(AUDIO)} Audio").callbackData("$FORMAT|$AUDIO|$shortUrl"),
                InlineKeyboardButton("${formatIcon(VIDEO)} Video").callbackData("$FORMAT|$VIDEO|$shortUrl")
            )
        )

        bot.execute(
            SendMessage(chatId, "Choose download format:")
                .replyMarkup(keyboard)
        )
    }

    private fun showQualityOptions(chatId: Long, url: String, type: String, messageId: Int? = null) {
        val shortUrl = YouTubeUrlShortener.toShort(url)

        val availableQuality = availableQuality(type)
        val (label, unit) = formatUnit(type)
        val icon = formatIcon(type)

        val keyboard = InlineKeyboardMarkup(
            availableQuality.map { InlineKeyboardButton("$it$unit").callbackData("$type|$it|$shortUrl") }
                .toTypedArray()
        )

        val text = "$icon Choose $type $label:"
        val message = if (messageId != null) EditMessageText(chatId, messageId, text).replyMarkup(keyboard)
        else SendMessage(chatId, text).replyMarkup(keyboard)

        bot.execute(message)
    }

    private fun answerCallback(callbackQueryId: String, message: String = "") {
        bot.execute(AnswerCallbackQuery(callbackQueryId).text(message))
    }

    private fun removeInlineKeyboard(chatId: Long, messageId: Int) {
        bot.execute(EditMessageReplyMarkup(chatId, messageId).replyMarkup(InlineKeyboardMarkup()))
    }

    private fun sendFile(chatId: Long, file: File) {
        bot.execute(SendDocument(chatId, file))
    }

    private fun isYoutubeUrl(text: String): Boolean {
        return text.contains("youtube.com") || text.contains("youtu.be")
    }

    private fun uuidStringFromId(id: String): String {
        return UUID.nameUUIDFromBytes(id.toByteArray()).toString()
    }
}
