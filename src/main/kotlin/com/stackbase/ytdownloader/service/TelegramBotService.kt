package com.stackbase.ytdownloader.service

import com.pengrad.telegrambot.TelegramBot
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

/**
 * @author Reslan Swed
 * Created on 2025-06-07
 */
@Service
class TelegramBotService(
    @Value("\${telegram.bot.token}") private val botToken: String
) {
    private val logger = LoggerFactory.getLogger(TelegramBotService::class.java)
    val bot = TelegramBot(botToken)

    fun handleCommand(chatId: Long, messageText: String) {
        logger.info("Handling command for chat {}: {}", chatId, messageText)
        
        when {
            messageText.startsWith("/start") -> handleStartCommand(chatId)

            messageText.startsWith("/help") -> handleHelpCommand(chatId)

            messageText.startsWith("/audio") -> {
                val url = messageText.removePrefix("/audio").trim()
                if (isYoutubeUrl(url)) {
                    logger.info("Processing audio command with URL: {}", url)
                    showAudioQualityOptions(chatId, url)
                } else {
                    logger.warn("Invalid YouTube URL for audio: {}", url)
                    sendTextMessage(chatId, "❌ Invalid YouTube URL for audio.")
                }
            }

            messageText.startsWith("/video") -> {
                val url = messageText.removePrefix("/video").trim()
                if (isYoutubeUrl(url)) {
                    logger.info("Processing video command with URL: {}", url)
                    showVideoResolutionOptions(chatId, url)
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
    fun showFormatOptions(chatId: Long, url: String) {
        val shortUrl = YouTubeUrlShortener.toShort(url)

        val keyboard = InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("🎵 Audio").callbackData("format|audio|$shortUrl"),
                InlineKeyboardButton("🎬 Video").callbackData("format|video|$shortUrl")
            )
        )

        bot.execute(
            SendMessage(chatId, "Choose download format:")
                .replyMarkup(keyboard)
        )
    }

    // Show audio quality options
    fun showAudioQualityOptions(chatId: Long, url: String, messageId: Int? = null) {
        val shortUrl = YouTubeUrlShortener.toShort(url)

        val keyboard = InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("128kbps").callbackData("audio|128|$shortUrl"),
                InlineKeyboardButton("192kbps").callbackData("audio|192|$shortUrl"),
                InlineKeyboardButton("256kbps").callbackData("audio|256|$shortUrl")
            )
        )

        val text = "🎵 Choose audio quality:"
        val message = if(messageId != null) EditMessageText(chatId, messageId, text).replyMarkup(keyboard)
        else SendMessage(chatId, text).replyMarkup(keyboard)

        bot.execute(message)
    }

    // Show video resolution options
    fun showVideoResolutionOptions(chatId: Long, url: String, messageId: Int? = null) {
        val shortUrl = YouTubeUrlShortener.toShort(url)

        val keyboard = InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("360p").callbackData("video|360|$shortUrl"),
                InlineKeyboardButton("480p").callbackData("video|480|$shortUrl"),
                InlineKeyboardButton("720p").callbackData("video|720|$shortUrl"),
                InlineKeyboardButton("1080p").callbackData("video|1080|$shortUrl")
            )
        )

        val text = "🎬 Choose video resolution:"
        val message = if(messageId != null) EditMessageText(chatId, messageId, text).replyMarkup(keyboard)
        else SendMessage(chatId, text).replyMarkup(keyboard)

        bot.execute(message)
    }

    fun answerCallback(callbackQueryId: String, message: String = "") {
        bot.execute(AnswerCallbackQuery(callbackQueryId).text(message))
    }

    fun removeInlineKeyboard(chatId: Long, messageId: Int) {
        bot.execute(EditMessageReplyMarkup(chatId, messageId).replyMarkup(InlineKeyboardMarkup()))
    }

    fun sendFile(chatId: Long, file: File) {
        bot.execute(SendDocument(chatId, file))
    }

    fun isYoutubeUrl(text: String): Boolean {
        return text.contains("youtube.com") || text.contains("youtu.be")
    }
}
