package com.stackbase.ytdownloader.utils

/**
 * @author Reslan Swed
 * Created on 2025-06-06
 */
object YouTubeUrlShortener {
    private const val YT_PREFIX = "https://www.youtube.com/watch?v="
    private const val YT_M_PREFIX = "https://m.youtube.com/watch?v="

    fun toShort(url: String): String {
        return when {
            url.startsWith(YT_PREFIX) -> url.removePrefix(YT_PREFIX)
            url.startsWith(YT_M_PREFIX) -> url.removePrefix(YT_M_PREFIX)
            else -> url // fallback if it's already just an ID
        }
    }

    fun toFull(shortUrl: String): String {
        return "$YT_PREFIX$shortUrl"
    }

    // Helper function to check if text is a YouTube URL
    fun isYoutubeUrl(text: String): Boolean {
        val youtubeRegex = Regex("^(https?://)?((www\\.)|(m\\.))?(youtube\\.com|youtu\\.be)/.+$")
        return youtubeRegex.matches(text)
    }
}
