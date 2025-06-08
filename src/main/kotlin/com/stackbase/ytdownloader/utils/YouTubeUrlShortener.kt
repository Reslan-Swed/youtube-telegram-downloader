package com.stackbase.ytdownloader.utils

import java.net.URI
import java.net.URISyntaxException

/**
 * @author Reslan Swed
 * Created on 2025-06-06
 */
object YouTubeUrlShortener {
    private const val YT_PREFIX = "https://www.youtube.com/watch?v="
    private const val YT_M_PREFIX = "https://m.youtube.com/watch?v="
    private const val YT_SHORT_PREFIX = "https://youtu.be/"

    /**
     * Extracts the video ID from a YouTube URL.
     * Supports various YouTube URL formats:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://m.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - https://youtube.com/shorts/VIDEO_ID
     * 
     * Also handles additional query parameters.
     */
    fun toShort(url: String): String {
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: ""
            val path = uri.path

            return when {
                // youtu.be short links
                host == "youtu.be" -> {
                    val pathParts = path.split("/").filter { it.isNotEmpty() }
                    pathParts.firstOrNull() ?: url
                }
                
                // youtube.com/shorts format
                host.contains("youtube.com") && path.startsWith("/shorts/") -> {
                    val pathParts = path.removePrefix("/shorts/").split("/")
                    pathParts.firstOrNull() ?: url
                }
                
                // Standard youtube.com watch URLs
                host.contains("youtube.com") && path == "/watch" -> {
                    // Parse query parameters
                    val query = uri.query ?: ""
                    val params = query.split("&")
                        .map { it.split("=", limit = 2) }
                        .filter { it.size == 2 }
                        .associate { it[0] to it[1] }
                    
                    params["v"] ?: url
                }
                
                // Already just an ID (fallback)
                else -> url
            }
        } catch (e: URISyntaxException) {
            return url
        }
    }

    fun toFull(shortUrl: String): String {
        if (shortUrl.startsWith("http")) {
            return shortUrl
        }
        return "$YT_PREFIX$shortUrl"
    }

    // Helper function to check if text is a YouTube URL
    fun isYoutubeUrl(text: String): Boolean {
        val youtubeRegex = Regex("^(https?://)?((www\\.)|(m\\.))?((youtube\\.com)|(youtu\\.be))/.+$")
        return youtubeRegex.matches(text)
    }
}