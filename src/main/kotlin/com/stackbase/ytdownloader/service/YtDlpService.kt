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
    @Value("\${yt-dlp.output:false}") private val enableOutput: Boolean
) {
    private val logger = LoggerFactory.getLogger(YtDlpService::class.java)

    private fun <T> executeCommand(
        command: List<String>,
        onSuccess: () -> T,
        onFailure: () -> T,
        redirectOutput: Boolean = false
    ): T {
        try {
            logger.info("Executing command: {}", command.joinToString(" "))

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            if (redirectOutput) {
                process.inputStream.bufferedReader().forEachLine { logger.debug(it) }
            }

            val exitCode = process.waitFor()

            if (exitCode == 0) {
                logger.info("Command executed successfully with exit code 0")
                return onSuccess()
            } else {
                logger.error("Command failed with exit code: {}", exitCode)
            }
        } catch (e: IOException) {
            logger.error("IO exception while executing command", e)
        } catch (e: InterruptedException) {
            logger.error("Command execution interrupted", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            logger.error("Unexpected error executing command", e)
        }
        return onFailure()
    }

    fun downloadVideo(url: String, resolution: Int = 720, outputDir: String? = null): File? {
        logger.info("Starting video download: url={}, resolution={}p, outputDir={}", url, resolution, outputDir)
        
        val outputPath = if(outputDir != null) Path(defaultDownloadDir, outputDir).toFile() else File(defaultDownloadDir)
        outputPath.mkdirs()

        val cmd = mutableListOf(
            "yt-dlp",
            "-f", "bestvideo[height<=${resolution}]+bestaudio/best",
            "-o", "$outputPath/$OUTPUT_FILE_TEMPLATE"
        )

        if (ffmpegPath != null) {
            cmd.add("--ffmpeg-location")
            cmd.add(ffmpegPath)
        }

        cmd.add(url)

        return executeCommand(
            cmd, redirectOutput = enableOutput,
            onSuccess = {
                val downloadedFile = outputPath.listFiles(FileFilter { file ->
                    file.isFile && file.name.contains(".mp4")
                })?.maxByOrNull { it.lastModified() }
                
                if (downloadedFile != null) {
                    logger.info("Video download complete: {}, size: {} bytes", 
                        downloadedFile.absolutePath, downloadedFile.length())
                } else {
                    logger.warn("Video download completed but file not found in output directory")
                }
                
                downloadedFile
            },
            onFailure = {
                logger.error("Video download failed for URL: {}", url)
                null
            })
    }

    fun downloadAudio(url: String, audioKbps: Int = 192, outputDir: String? = null): File? {
        logger.info("Starting audio download: url={}, quality={}kbps, outputDir={}", url, audioKbps, outputDir)
        
        val outputPath = if(outputDir != null) Path(defaultDownloadDir, outputDir).toFile() else File(defaultDownloadDir)
        outputPath.mkdirs()

        val cmd = mutableListOf(
            "yt-dlp",
            "-f", "bestaudio",
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", audioKbps.toString(),
            "-o", "$outputPath/$OUTPUT_FILE_TEMPLATE"
        )

        if (ffmpegPath != null) {
            cmd.add("--ffmpeg-location")
            cmd.add(ffmpegPath)
        }

        cmd.add(url)

        return executeCommand(
            cmd, redirectOutput = enableOutput,
            onSuccess = {
                val downloadedFile = outputPath.listFiles(FileFilter { file ->
                    file.isFile && file.name.contains(".mp3")
                })?.maxByOrNull { it.lastModified() }
                
                if (downloadedFile != null) {
                    logger.info("Audio download complete: {}, size: {} bytes", 
                        downloadedFile.absolutePath, downloadedFile.length())
                } else {
                    logger.warn("Audio download completed but file not found in output directory")
                }
                
                downloadedFile
            },
            onFailure = {
                logger.error("Audio download failed for URL: {}", url)
                null
            })
    }
}
