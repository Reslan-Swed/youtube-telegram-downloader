package com.stackbase.ytdownloader.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileFilter
import java.io.IOException
import kotlin.io.path.Path

private const val OUTPUT_FILE_TEMPLATE = "%(title)s.%(ext)s"

/**
 * @author Reslan Swed
 * Created on 2025-06-06
 */
@Service
class YtDlpService(
    @Value("\${public.download.dir}") private val defaultDownloadDir: String,
    @Value("\${yt-dlp.ffmpeg.path}") private val ffmpegPath: String?,
    @Value("\${public.download.file.max-size:500}") private val maxFileSizeMB: Int,
    @Value("\${yt-dlp.output:false}") private val enableOutput: Boolean,
    @Value("\${yt-dlp.cookies.path:cookies.txt}") private val cookiesPath: String?
) {
    private val logger = LoggerFactory.getLogger(YtDlpService::class.java)

    private fun <T> executeCommand(
        command: List<String>,
        onSuccess: (String) -> T,
        onFailure: (String) -> T,
        redirectOutput: Boolean = false
    ): T {
        try {
            logger.info("Executing command: {}", command.joinToString(" "))

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            // Capture the output
            val outputBuffer = StringBuilder()
            process.inputStream.bufferedReader().forEachLine { line ->
                if (redirectOutput) {
                    logger.debug(line)
                }
                outputBuffer.append(line).append("\n")
            }

            val exitCode = process.waitFor()
            val output = outputBuffer.toString().trim()

            return if (exitCode == 0) {
                logger.info("Command executed successfully with exit code 0")
                onSuccess(output)
            } else {
                logger.error("Command failed with exit code: {}", exitCode)
                onFailure(output)
            }
        } catch (e: IOException) {
            logger.error("IO exception while executing command", e)
            return onFailure("IO error: ${e.message}")
        } catch (e: InterruptedException) {
            logger.error("Command execution interrupted", e)
            Thread.currentThread().interrupt()
            return onFailure("Command interrupted: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error executing command", e)
            return onFailure("Unexpected error: ${e.message}")
        }
    }

    private fun failureHandler(output: String): Pair<String, File?> {
        logger.error("Download failed: {}", output)
        return "Download failed: Unknown error" to null
    }

    private fun successHandler(output: String, outputPath: File, vararg extensions: String): Pair<String, File?> {
        // Check for file size limit error patterns
        return when {
            output.contains("File is larger than max-filesize") ||
                    output.contains("--max-filesize") ||
                    output.contains("exceeds max-filesize") -> {
                logger.error("Download failed: file size exceeds the limit of {}MB", maxFileSizeMB)
                "File size exceeds the maximum limit of ${maxFileSizeMB}MB" to null
            }

            output.contains("This video is unavailable") -> {
                logger.error("Download failed: video is unavailable")
                "This video is unavailable" to null
            }

            output.contains("Private video") -> {
                logger.error("Download failed: video is private")
                "This video is private and cannot be accessed" to null
            }

            output.contains("has been removed") -> {
                logger.error("Download failed: video has been removed")
                "This video has been removed" to null
            }

            else -> {
                val downloadedFile = outputPath.listFiles(FileFilter { file ->
                    file.isFile && extensions.any { file.name.endsWith(it) }
                })?.maxByOrNull { it.lastModified() }

                if (downloadedFile != null) {
                    logger.info(
                        "Download complete: {}, size: {} bytes",
                        downloadedFile.absolutePath, downloadedFile.length()
                    )
                    "Download success" to downloadedFile
                } else {
                    logger.warn("Download completed but file not found in output directory")
                    "Download completed but file not found" to null
                }
            }
        }
    }

    fun downloadVideo(url: String, resolution: Int = 720, outputDir: String? = null): Pair<String, File?> {
        logger.info("Starting video download: url={}, resolution={}p, outputDir={}", url, resolution, outputDir)

        val outputPath = prepareFolder(outputDir)

        val cmd = constructCommand(url, "$outputPath")(resolution)

        return executeCommand(
            cmd, redirectOutput = enableOutput,
            onSuccess = { successHandler(it, outputPath, ".mp4", ".webm") },
            onFailure = this::failureHandler
        )
    }

    fun downloadAudio(url: String, audioKbps: Int = 192, outputDir: String? = null): Pair<String, File?> {
        logger.info("Starting audio download: url={}, quality={}kbps, outputDir={}", url, audioKbps, outputDir)

        val outputPath = prepareFolder(outputDir)

        val cmd = constructCommand(url, "$outputPath", forAudio = true)(audioKbps)

        return executeCommand(
            cmd, redirectOutput = enableOutput,
            onSuccess = { successHandler(it, outputPath, ".mp3") },
            onFailure = this::failureHandler
        )
    }

    private fun constructCommand(url: String, outputPath: String, forAudio: Boolean = false): (Int) -> List<String> =
        { quality ->
            mutableListOf(
                "yt-dlp",
                "--max-filesize", "${maxFileSizeMB}M",
                "--no-playlist",
                "--retries", "3",
                "--fragment-retries", "3",
                "-o", "$outputPath/$OUTPUT_FILE_TEMPLATE"
            ).also {
                if (forAudio) {
                    it.addAll(
                        listOf(
                            "-f", "bestaudio",
                            "--extract-audio",
                            "--audio-format", "mp3",
                            "--audio-quality", quality.toString()
                        )
                    )
                } else {
                    it.addAll(
                        listOf(
                            "-f", "bestvideo[height<=${quality}][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                            "--merge-output-format", "mp4",
                        )
                    )
                }
                if (ffmpegPath != null) {
                    it.add("--ffmpeg-location")
                    it.add(ffmpegPath)
                }
                if (cookiesPath != null && File(cookiesPath).exists()) {
                    it.add("--cookies")
                    it.add(cookiesPath)
                }
                it.add(url)
            }
        }

    private fun prepareFolder(outputPath: String?): File {
        val path =
            (if (outputPath != null) Path(defaultDownloadDir, outputPath).toFile() else File(defaultDownloadDir))
                .apply { mkdirs() }
        return path
    }
}
