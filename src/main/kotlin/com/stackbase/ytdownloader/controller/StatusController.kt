package com.stackbase.ytdownloader.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * @author Reslan Swed
 * Created on 2025-06-06
 */
@RestController
class StatusController {
    @GetMapping("/status")
    fun status(): Map<String, String> {
        return mapOf(
            "status" to "OK",
            "version" to "1.0.0"
        )
    }
}