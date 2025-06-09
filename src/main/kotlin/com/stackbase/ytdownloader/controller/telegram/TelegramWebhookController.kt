package com.stackbase.ytdownloader.controller.telegram

import com.pengrad.telegrambot.BotUtils
import com.pengrad.telegrambot.model.Update
import com.stackbase.ytdownloader.service.TelegramBotService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @author Reslan Swed
 * Created on 2025-06-07
 */
@RestController
@RequestMapping("/telegram/v1")
class TelegramWebhookController(
    private val telegramBotService: TelegramBotService,
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
                message != null && message.text() != null -> telegramBotService.handleCommand(message)

                callbackQuery != null -> telegramBotService.handleCallback(callbackQuery)
            }
        } catch (e: Exception) {
            logger.error("Error processing Telegram update", e)
        }
    }
}
