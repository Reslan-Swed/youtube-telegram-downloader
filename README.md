# YouTube Telegram Downloader

A Spring Boot application that allows users to download YouTube videos and audio through a Telegram bot interface.

## Features

- 🎬 Download YouTube videos in various resolutions (360p, 480p, 720p, 1080p)
- 🎵 Download YouTube audio in different qualities (128kbps, 192kbps, 256kbps)
- 🤖 Interact through a Telegram bot interface
- 🔗 Generate download links for files too large for Telegram
- 📱 Simple and intuitive user experience

## Requirements

- Java 21 or higher
- Docker (for containerized deployment)
- A Telegram Bot Token (get one from [@BotFather](https://t.me/botfather))
- A publicly accessible URL for hosting the application (for webhook and file downloads)

## Quick Start with Docker

### Run the container

```bash
docker run -d \
  -p 8080:8080 \
  -e TELEGRAM_BOT_TOKEN=your_telegram_bot_token \
  -e PUBLIC_DOWNLOAD_URL=https://your-domain.com/public/download \
  -e YT_DLP_COOKIES_PATH=/app/cookies.txt \
  -v /path/to/downloads:/app/downloads \
  -v /path/to/logs:/app/logs \
  -v /path/to/cookies.txt:/app/cookies.txt \
  youtube-telegram-downloader
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TELEGRAM_BOT_TOKEN` | Your Telegram bot token | Required |
| `PUBLIC_DOWNLOAD_URL` | Public URL for accessing downloaded files | Required |
| `PUBLIC_DOWNLOAD_PATH` | Path where downloaded files are stored | `downloads` |
| `TELEGRAM_BOT_FILE_MAX_SIZE` | Maximum file size (MB) for Telegram | `50` |
| `PUBLIC_DOWNLOAD_FILE_MAX_SIZE` | Maximum file size (MB) for public downloads | `500` |
| `YT_DLP_FFMPEG_PATH` | Path to ffmpeg in the container | `/usr/bin/ffmpeg` |
| `YT_DLP_COOKIES_PATH` | Path to cookies file for yt-dlp | `cookies.txt` |
| `FILE_CLEANUP_MAX_AGE_HOURS` | Hours after which downloaded files are automatically deleted | `24` |
| `APP_PORT` | Application port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring profile to activate | `prod` |

## Building from Source

### Clone the repository

```bash
git clone https://github.com/yourusername/youtube-telegram-downloader.git
cd youtube-telegram-downloader
```

### Build the Docker image

```bash
docker build -t youtube-telegram-downloader .
```

## Usage

1. Start a chat with your Telegram bot
2. Send a YouTube URL or use the commands:
   - `/start` - Get a welcome message
   - `/help` - Show available commands
   - `/video <YouTube URL>` - Download a video
   - `/audio <YouTube URL>` - Download audio

## Deployment Options

### Railway

[![Deploy on Railway](https://railway.app/button.svg)](https://railway.app/template/youtube-telegram-downloader)

Set the required environment variables in the Railway dashboard.

### Local Server

For a local server with a public URL, you can use ngrok:

```bash
ngrok http 8080
```

Then set `PUBLIC_DOWNLOAD_URL` to your ngrok URL + `/public/download`.

## Logs

- In development mode: Logs appear in the console
- In production mode: Logs are written to `/app/logs/youtube-downloader.log`

## License

This project is licensed under the MIT License - see the LICENSE file for details.